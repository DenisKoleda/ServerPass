package com.denis.serverpass.audit;

import com.denis.serverpass.config.PasswordConfigStore;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

public final class AuditService {
    private final JavaPlugin plugin;
    private final PasswordConfigStore config;

    public AuditService(JavaPlugin plugin, PasswordConfigStore config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void record(String player, String result, String reason) {
        if (!config.auditEnabled()) {
            return;
        }
        String line = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now())
            + "\tplayer=" + sanitize(player)
            + "\tresult=" + sanitize(result)
            + "\treason=" + sanitize(reason)
            + System.lineSeparator();
        try {
            Path auditFile = plugin.getDataFolder().toPath().resolve(config.auditFile()).normalize();
            Files.createDirectories(auditFile.getParent());
            Files.writeString(auditFile, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not write ServerPass audit log: " + ex.getClass().getSimpleName());
        }
    }

    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replace('\t', '_').replace('\r', '_').replace('\n', '_');
    }
}
