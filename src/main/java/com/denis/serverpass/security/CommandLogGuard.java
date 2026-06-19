package com.denis.serverpass.security;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;

public final class CommandLogGuard {
    private CommandLogGuard() {
    }

    public static boolean disablePlayerCommandLogging(JavaPlugin plugin) {
        boolean runtimeDisabled = disableRuntimeFlag(plugin);
        boolean persisted = persistSpigotSetting(plugin);
        if (runtimeDisabled) {
            plugin.getLogger().info("Disabled Spigot player command logging for ServerPass password safety.");
        } else {
            plugin.getLogger().severe("Could not disable Spigot player command logging. Password commands may be written to server logs.");
        }
        return runtimeDisabled && persisted;
    }

    public static boolean isRuntimeCommandLoggingDisabled() {
        try {
            Class<?> spigotConfig = Class.forName("org.spigotmc.SpigotConfig");
            Field field = spigotConfig.getDeclaredField("logCommands");
            field.setAccessible(true);
            return !field.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }

    public static boolean isPersistedCommandLoggingDisabled(JavaPlugin plugin) {
        try {
            Path serverRoot = plugin.getServer().getWorldContainer().toPath();
            File spigotFile = serverRoot.resolve("spigot.yml").toFile();
            if (!spigotFile.exists()) {
                return false;
            }
            YamlConfiguration spigot = YamlConfiguration.loadConfiguration(spigotFile);
            return !spigot.getBoolean("commands.log", true);
        } catch (Exception ex) {
            return false;
        }
    }

    private static boolean disableRuntimeFlag(JavaPlugin plugin) {
        try {
            Class<?> spigotConfig = Class.forName("org.spigotmc.SpigotConfig");
            Field field = spigotConfig.getDeclaredField("logCommands");
            field.setAccessible(true);
            field.setBoolean(null, false);
            return !field.getBoolean(null);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            plugin.getLogger().warning("Spigot command log runtime guard failed: " + ex.getClass().getSimpleName());
            return false;
        }
    }

    private static boolean persistSpigotSetting(JavaPlugin plugin) {
        try {
            Path serverRoot = plugin.getServer().getWorldContainer().toPath();
            File spigotFile = serverRoot.resolve("spigot.yml").toFile();
            if (!spigotFile.exists()) {
                return false;
            }
            YamlConfiguration spigot = YamlConfiguration.loadConfiguration(spigotFile);
            if (!spigot.getBoolean("commands.log", true)) {
                return true;
            }
            spigot.set("commands.log", false);
            spigot.save(spigotFile);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Could not persist spigot.yml commands.log=false: " + ex.getClass().getSimpleName());
            return false;
        }
    }
}
