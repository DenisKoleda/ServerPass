package com.denis.serverpass;

import com.denis.serverpass.audit.AuditService;
import com.denis.serverpass.auth.AuthSession;
import com.denis.serverpass.auth.AuthSessionManager;
import com.denis.serverpass.command.ServerPassCommand;
import com.denis.serverpass.config.PasswordConfigStore;
import com.denis.serverpass.crypto.PasswordHasher;
import com.denis.serverpass.listener.LockdownListener;
import com.denis.serverpass.listener.LoginCommandInterceptor;
import com.denis.serverpass.message.MessageService;
import com.denis.serverpass.security.CommandLogGuard;
import com.denis.serverpass.selftest.SelfTestService;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.Map;

public final class ServerPassPlugin extends JavaPlugin {
    private PasswordHasher passwordHasher;
    private PasswordConfigStore passwordStore;
    private MessageService messages;
    private AuditService auditService;
    private AuthSessionManager sessionManager;
    private SelfTestService selfTestService;
    private LoginCommandInterceptor loginCommandInterceptor;
    private BukkitTask sessionTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResourceIfMissing("messages.yml");

        passwordHasher = new PasswordHasher();
        passwordStore = new PasswordConfigStore(this, passwordHasher);
        messages = new MessageService(this);
        auditService = new AuditService(this, passwordStore);
        sessionManager = new AuthSessionManager(passwordStore);
        selfTestService = new SelfTestService(this, passwordHasher, passwordStore, sessionManager, auditService);
        CommandLogGuard.disablePlayerCommandLogging(this);

        ServerPassCommand serverPassCommand = new ServerPassCommand(this, passwordStore, messages, auditService, sessionManager, selfTestService);
        loginCommandInterceptor = new LoginCommandInterceptor(passwordStore, messages, auditService, sessionManager, serverPassCommand);

        registerCommands(serverPassCommand);
        registerListeners();
        sessionTask = Bukkit.getScheduler().runTaskTimer(this, this::tickLockedSessions, 20L, 20L);

        getLogger().info("ServerPass enabled; configured=" + passwordStore.isConfigured());
    }

    @Override
    public void onDisable() {
        if (sessionTask != null) {
            sessionTask.cancel();
        }
        if (sessionManager != null) {
            sessionManager.clearAll();
        }
    }

    public void reloadServerPass() {
        reloadConfig();
        passwordStore.reload();
        messages.reload();
    }

    public void prompt(Player player, boolean title) {
        if (title) {
            messages.showLoginTitle(player);
        }
        long seconds = sessionManager.session(player)
            .map(session -> Math.max(0L, session.remainingSeconds(Instant.now())))
            .orElse((long) passwordStore.timeoutSeconds());
        messages.sendActionbar(player, "actionbar", Map.of("seconds", Long.toString(seconds)));
    }

    private void registerCommands(ServerPassCommand serverPassCommand) {
        PluginCommand serverpass = getCommand("serverpass");
        if (serverpass == null) {
            throw new IllegalStateException("Command /serverpass is missing from plugin.yml");
        }
        serverpass.setExecutor(serverPassCommand);
        serverpass.setTabCompleter(serverPassCommand);

        PluginCommand login = getCommand("login");
        if (login == null) {
            throw new IllegalStateException("Command /login is missing from plugin.yml");
        }
        login.setExecutor(loginCommandInterceptor);
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(loginCommandInterceptor, this);
        pluginManager.registerEvents(new LockdownListener(this, passwordStore, messages, auditService, sessionManager), this);
    }

    private void tickLockedSessions() {
        Instant now = Instant.now();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!sessionManager.isLocked(player)) {
                continue;
            }
            AuthSession session = sessionManager.requireAuthentication(player, now);
            if (passwordStore.isConfigured() && session.isExpired(now)) {
                auditService.record(player.getName(), "kick", "timeout");
                sessionManager.clear(player.getUniqueId());
                player.kick(messages.raw("timeoutKick"));
                continue;
            }
            if (passwordStore.isConfigured()) {
                prompt(player, false);
            } else if (sessionManager.canConfigurePassword(player)) {
                messages.sendActionbar(player, "setupAdmin", Map.of());
            }
        }
    }

    private void saveResourceIfMissing(String resourceName) {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            throw new IllegalStateException("Could not create plugin data folder");
        }
        if (!getDataFolder().toPath().resolve(resourceName).toFile().exists()) {
            saveResource(resourceName, false);
        }
    }
}
