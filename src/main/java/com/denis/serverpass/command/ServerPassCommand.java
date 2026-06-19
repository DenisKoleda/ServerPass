package com.denis.serverpass.command;

import com.denis.serverpass.ServerPassPlugin;
import com.denis.serverpass.audit.AuditService;
import com.denis.serverpass.auth.AuthSessionManager;
import com.denis.serverpass.config.PasswordConfigStore;
import com.denis.serverpass.message.MessageService;
import com.denis.serverpass.selftest.SelfTestService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ServerPassCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("set", "reload", "status", "logout", "forceauth", "selftest", "help");

    private final ServerPassPlugin plugin;
    private final PasswordConfigStore passwordStore;
    private final MessageService messages;
    private final AuditService auditService;
    private final AuthSessionManager sessionManager;
    private final SelfTestService selfTestService;

    public ServerPassCommand(
        ServerPassPlugin plugin,
        PasswordConfigStore passwordStore,
        MessageService messages,
        AuditService auditService,
        AuthSessionManager sessionManager,
        SelfTestService selfTestService
    ) {
        this.plugin = plugin;
        this.passwordStore = passwordStore;
        this.messages = messages;
        this.auditService = auditService;
        this.sessionManager = sessionManager;
        this.selfTestService = selfTestService;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String subcommand = args.length == 0 ? "help" : args[0].toLowerCase(Locale.ROOT);
        return switch (subcommand) {
            case "set" -> set(sender, joinTail(args, 1));
            case "reload" -> reload(sender);
            case "status" -> status(sender);
            case "logout" -> logout(sender, args);
            case "forceauth" -> forceauth(sender, args);
            case "selftest" -> selftest(sender, args);
            case "help" -> help(sender);
            default -> help(sender);
        };
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("logout") || args[0].equalsIgnoreCase("forceauth"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("selftest")) {
            return filter(List.of("keep"), args[1]);
        }
        return List.of();
    }

    public boolean handleInterceptedSet(Player player, String password) {
        return set(player, password);
    }

    private boolean set(CommandSender sender, String password) {
        if (!can(sender, "serverpass.set")) {
            messages.send(sender, "noPermission");
            return true;
        }
        if (password == null || password.isEmpty()) {
            messages.send(sender, "passwordRequired");
            return true;
        }
        try {
            passwordStore.setPassword(password);
        } catch (IllegalArgumentException ex) {
            messages.send(sender, "passwordRequired");
            return true;
        }
        auditService.record(sender.getName(), "success", "password_set");
        if (sender instanceof Player player) {
            sessionManager.authenticate(player);
        }
        messages.send(sender, "passwordUpdated");
        return true;
    }

    private boolean reload(CommandSender sender) {
        if (!can(sender, "serverpass.reload")) {
            messages.send(sender, "noPermission");
            return true;
        }
        plugin.reloadServerPass();
        auditService.record(sender.getName(), "success", "reload");
        messages.send(sender, "reloaded");
        return true;
    }

    private boolean status(CommandSender sender) {
        if (!can(sender, "serverpass.admin")) {
            messages.send(sender, "noPermission");
            return true;
        }
        sender.sendMessage(messages.raw("statusHeader"));
        sender.sendMessage(Component.text("enabled: " + passwordStore.authEnabled(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("configured: " + passwordStore.isConfigured(), passwordStore.isConfigured() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("timeoutSeconds: " + passwordStore.timeoutSeconds(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("maxAttempts: " + passwordStore.maxAttempts(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("sessions locked/authenticated: " + sessionManager.lockedCount() + "/" + sessionManager.authenticatedCount(), NamedTextColor.GRAY));
        return true;
    }

    private boolean logout(CommandSender sender, String[] args) {
        if (!can(sender, "serverpass.forceauth")) {
            messages.send(sender, "noPermission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /serverpass logout <player>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "playerNotFound", Map.of("player", args[1]));
            return true;
        }
        sessionManager.clear(target.getUniqueId());
        if (passwordStore.authEnabled() && !sessionManager.hasBypass(target)) {
            sessionManager.requireAuthentication(target, java.time.Instant.now());
            plugin.prompt(target, true);
        }
        auditService.record(target.getName(), "logout", "admin");
        messages.send(sender, "playerLoggedOut", Map.of("player", target.getName()));
        return true;
    }

    private boolean forceauth(CommandSender sender, String[] args) {
        if (!can(sender, "serverpass.forceauth")) {
            messages.send(sender, "noPermission");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Component.text("Usage: /serverpass forceauth <player>", NamedTextColor.RED));
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(sender, "playerNotFound", Map.of("player", args[1]));
            return true;
        }
        sessionManager.authenticate(target);
        auditService.record(target.getName(), "success", "forceauth");
        messages.send(sender, "playerForceAuthed", Map.of("player", target.getName()));
        return true;
    }

    private boolean selftest(CommandSender sender, String[] args) {
        if (!can(sender, "serverpass.selftest")) {
            messages.send(sender, "noPermission");
            return true;
        }
        boolean keep = args.length >= 2 && args[1].equalsIgnoreCase("keep");
        selfTestService.run(sender, keep);
        return true;
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage(messages.raw("helpHeader"));
        sender.sendMessage(Component.text("/login <password> - авторизовать текущую сессию", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/serverpass set <password> - задать общий пароль", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/serverpass reload - перезагрузить config/messages", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/serverpass status - состояние без salt/hash", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/serverpass logout <player> - снова заблокировать игрока", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/serverpass forceauth <player> - авторизовать игрока вручную", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/serverpass selftest [keep] - встроенная проверка", NamedTextColor.GRAY));
        return true;
    }

    private boolean can(CommandSender sender, String permission) {
        return !(sender instanceof Player) || sender.isOp() || sender.hasPermission(permission) || sender.hasPermission("serverpass.admin");
    }

    private String joinTail(String[] args, int start) {
        if (args.length <= start) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int index = start; index < args.length; index++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[index]);
        }
        return builder.toString();
    }

    private List<String> filter(List<String> values, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return new ArrayList<>(values);
        }
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(normalized)).toList();
    }
}
