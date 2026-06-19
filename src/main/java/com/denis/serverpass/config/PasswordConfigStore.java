package com.denis.serverpass.config;

import com.denis.serverpass.crypto.PasswordHasher;
import com.denis.serverpass.crypto.PasswordHasher.HashRecord;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public final class PasswordConfigStore {
    private final JavaPlugin plugin;
    private final PasswordHasher hasher;

    public PasswordConfigStore(JavaPlugin plugin, PasswordHasher hasher) {
        this.plugin = plugin;
        this.hasher = hasher;
    }

    public void reload() {
        plugin.reloadConfig();
    }

    public boolean isConfigured() {
        return !salt().isBlank() && !hash().isBlank();
    }

    public boolean verify(String password) {
        if (!isConfigured()) {
            return false;
        }
        return hasher.verify(password, currentRecord());
    }

    public void setPassword(String password) {
        HashRecord record = hasher.hash(password, algorithmForNewHash(), iterations());
        FileConfiguration config = config();
        config.set("password.algorithm", record.algorithm());
        config.set("password.iterations", record.iterations());
        config.set("password.salt", record.salt());
        config.set("password.hash", record.hash());
        plugin.saveConfig();
    }

    public boolean authEnabled() {
        return config().getBoolean("auth.enabled", true);
    }

    public boolean requireEveryJoin() {
        return config().getBoolean("auth.requireEveryJoin", true);
    }

    public int timeoutSeconds() {
        return Math.max(5, config().getInt("auth.timeoutSeconds", 60));
    }

    public int maxAttempts() {
        return Math.max(1, config().getInt("auth.maxAttempts", 3));
    }

    public String bypassPermission() {
        return config().getString("auth.bypassPermission", "serverpass.bypass");
    }

    public boolean allowOpsBypass() {
        return config().getBoolean("auth.allowOpsBypass", false);
    }

    public boolean allowLookAround() {
        return config().getBoolean("lock.allowLookAround", true);
    }

    public boolean blockMovement() {
        return config().getBoolean("lock.blockMovement", true);
    }

    public boolean blockBlockBreak() {
        return config().getBoolean("lock.blockBlockBreak", true);
    }

    public boolean blockBlockPlace() {
        return config().getBoolean("lock.blockBlockPlace", true);
    }

    public boolean blockInteract() {
        return config().getBoolean("lock.blockInteract", true);
    }

    public boolean blockInventory() {
        return config().getBoolean("lock.blockInventory", true);
    }

    public boolean blockItemDrop() {
        return config().getBoolean("lock.blockItemDrop", true);
    }

    public boolean blockItemPickup() {
        return config().getBoolean("lock.blockItemPickup", true);
    }

    public boolean blockChat() {
        return config().getBoolean("lock.blockChat", true);
    }

    public boolean blockCommandsExceptLogin() {
        return config().getBoolean("lock.blockCommandsExceptLogin", true);
    }

    public boolean blockDamage() {
        return config().getBoolean("lock.blockDamage", true);
    }

    public boolean protectFromDamage() {
        return config().getBoolean("lock.protectFromDamage", true);
    }

    public boolean auditEnabled() {
        return config().getBoolean("audit.enabled", true);
    }

    public String auditFile() {
        return config().getString("audit.file", "audit.log");
    }

    public HashRecord currentRecord() {
        return new HashRecord(algorithm(), iterations(), salt(), hash());
    }

    private String algorithmForNewHash() {
        String configured = algorithm();
        return hasher.supports(configured) ? configured : PasswordHasher.DEFAULT_ALGORITHM;
    }

    private String algorithm() {
        return config().getString("password.algorithm", PasswordHasher.DEFAULT_ALGORITHM);
    }

    private int iterations() {
        return Math.max(10_000, config().getInt("password.iterations", PasswordHasher.DEFAULT_ITERATIONS));
    }

    private String salt() {
        return config().getString("password.salt", "").trim();
    }

    private String hash() {
        return config().getString("password.hash", "").trim();
    }

    private FileConfiguration config() {
        return plugin.getConfig();
    }
}
