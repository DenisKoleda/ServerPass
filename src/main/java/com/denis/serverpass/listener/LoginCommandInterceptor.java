package com.denis.serverpass.listener;

import com.denis.serverpass.audit.AuditService;
import com.denis.serverpass.auth.AuthSessionManager;
import com.denis.serverpass.command.ServerPassCommand;
import com.denis.serverpass.config.PasswordConfigStore;
import com.denis.serverpass.message.MessageService;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;

public final class LoginCommandInterceptor implements Listener, CommandExecutor {
    private final PasswordConfigStore passwordStore;
    private final MessageService messages;
    private final AuditService auditService;
    private final AuthSessionManager sessionManager;
    private final ServerPassCommand serverPassCommand;

    public LoginCommandInterceptor(
        PasswordConfigStore passwordStore,
        MessageService messages,
        AuditService auditService,
        AuthSessionManager sessionManager,
        ServerPassCommand serverPassCommand
    ) {
        this.passwordStore = passwordStore;
        this.messages = messages;
        this.auditService = auditService;
        this.sessionManager = sessionManager;
        this.serverPassCommand = serverPassCommand;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        ParsedCommand parsed = parse(event.getMessage());
        if (parsed == null) {
            return;
        }
        Player player = event.getPlayer();
        if (parsed.command().equals("login")) {
            event.setCancelled(true);
            handleLogin(player, parsed.remainder());
            return;
        }
        if (parsed.command().equals("serverpass") && parsed.firstArgument().equals("set")) {
            event.setCancelled(true);
            serverPassCommand.handleInterceptedSet(player, parsed.afterFirstArgument());
            return;
        }
        if (sessionManager.isLocked(player) && passwordStore.blockCommandsExceptLogin()) {
            event.setCancelled(true);
            if (passwordStore.isConfigured()) {
                messages.send(player, "loginPrompt");
            } else if (sessionManager.canConfigurePassword(player)) {
                messages.send(player, "setupAdmin");
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "onlyPlayers");
            return true;
        }
        handleLogin(player, String.join(" ", args));
        return true;
    }

    public void handleLogin(Player player, String password) {
        if (!passwordStore.authEnabled()) {
            sessionManager.authenticate(player);
            messages.send(player, "success");
            return;
        }
        if (!passwordStore.isConfigured()) {
            if (sessionManager.canConfigurePassword(player)) {
                messages.send(player, "setupAdmin");
            } else {
                auditService.record(player.getName(), "kick", "not_configured");
                player.kick(messages.raw("notConfiguredKick"));
            }
            return;
        }
        if (password == null || password.isEmpty()) {
            messages.send(player, "loginPrompt");
            return;
        }
        boolean valid;
        try {
            valid = passwordStore.verify(password);
        } catch (RuntimeException ex) {
            auditService.record(player.getName(), "failure", "invalid_password_config");
            player.kick(messages.raw("notConfiguredKick"));
            return;
        }
        if (valid) {
            sessionManager.authenticate(player);
            auditService.record(player.getName(), "success", "login");
            messages.send(player, "success");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
            return;
        }
        int attempts = sessionManager.recordFailure(player);
        int remaining = passwordStore.maxAttempts() - attempts;
        auditService.record(player.getName(), "failure", "wrong_password");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
        if (remaining <= 0) {
            auditService.record(player.getName(), "kick", "max_attempts");
            sessionManager.clear(player.getUniqueId());
            player.kick(messages.raw("attemptsKick"));
            return;
        }
        messages.send(player, "attemptsLeft", Map.of("attempts", Integer.toString(remaining)));
    }

    private ParsedCommand parse(String message) {
        if (message == null || !message.startsWith("/")) {
            return null;
        }
        String withoutSlash = message.substring(1).trim();
        if (withoutSlash.isEmpty()) {
            return null;
        }
        int split = withoutSlash.indexOf(' ');
        String command = split >= 0 ? withoutSlash.substring(0, split) : withoutSlash;
        String remainder = split >= 0 ? withoutSlash.substring(split + 1).trim() : "";
        int namespace = command.indexOf(':');
        if (namespace >= 0 && namespace < command.length() - 1) {
            command = command.substring(namespace + 1);
        }
        return new ParsedCommand(command.toLowerCase(Locale.ROOT), remainder);
    }

    private record ParsedCommand(String command, String remainder) {
        String firstArgument() {
            if (remainder.isBlank()) {
                return "";
            }
            int split = remainder.indexOf(' ');
            return (split >= 0 ? remainder.substring(0, split) : remainder).toLowerCase(Locale.ROOT);
        }

        String afterFirstArgument() {
            int split = remainder.indexOf(' ');
            return split >= 0 ? remainder.substring(split + 1).trim() : "";
        }
    }
}
