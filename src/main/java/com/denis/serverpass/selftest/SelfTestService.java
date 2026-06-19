package com.denis.serverpass.selftest;

import com.denis.serverpass.audit.AuditService;
import com.denis.serverpass.auth.AuthSessionManager;
import com.denis.serverpass.config.PasswordConfigStore;
import com.denis.serverpass.crypto.PasswordHasher;
import com.denis.serverpass.crypto.PasswordHasher.HashRecord;
import com.denis.serverpass.security.CommandLogGuard;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SelfTestService {
    private static final char[] SECRET_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-".toCharArray();

    private final JavaPlugin plugin;
    private final PasswordHasher hasher;
    private final PasswordConfigStore passwordStore;
    private final AuthSessionManager sessionManager;
    private final AuditService auditService;
    private final SecureRandom random = new SecureRandom();

    public SelfTestService(
        JavaPlugin plugin,
        PasswordHasher hasher,
        PasswordConfigStore passwordStore,
        AuthSessionManager sessionManager,
        AuditService auditService
    ) {
        this.plugin = plugin;
        this.hasher = hasher;
        this.passwordStore = passwordStore;
        this.sessionManager = sessionManager;
        this.auditService = auditService;
    }

    public boolean run(CommandSender sender, boolean keep) {
        List<Result> results = new ArrayList<>();
        String secret = randomSecret();
        HashRecord record = null;
        try {
            record = hasher.hash(secret, PasswordHasher.DEFAULT_ALGORITHM, PasswordHasher.DEFAULT_ITERATIONS);
            results.add(Result.pass("hash generation"));
        } catch (RuntimeException ex) {
            results.add(Result.fail("hash generation"));
        }
        if (record != null && hasher.verify(secret, record)) {
            results.add(Result.pass("correct password validates"));
        } else {
            results.add(Result.fail("correct password validates"));
        }
        if (record != null && !hasher.verify(randomSecret(), record)) {
            results.add(Result.pass("wrong password fails"));
        } else {
            results.add(Result.fail("wrong password fails"));
        }
        try {
            hasher.hash("", PasswordHasher.DEFAULT_ALGORITHM, PasswordHasher.DEFAULT_ITERATIONS);
            results.add(Result.fail("empty password rejected"));
        } catch (IllegalArgumentException ex) {
            results.add(Result.pass("empty password rejected"));
        }
        try {
            passwordStore.reload();
            results.add(Result.pass("config reload"));
        } catch (RuntimeException ex) {
            results.add(Result.fail("config reload"));
        }
        results.add(sessionManager.runSelfTest() ? Result.pass("session lock/unlock basic logic") : Result.fail("session lock/unlock basic logic"));
        results.add(CommandLogGuard.isRuntimeCommandLoggingDisabled()
            ? Result.pass("runtime player command logging disabled")
            : Result.fail("runtime player command logging disabled"));
        results.add(CommandLogGuard.isPersistedCommandLoggingDisabled(plugin)
            ? Result.pass("spigot.yml commands.log disabled")
            : Result.fail("spigot.yml commands.log disabled"));
        results.add(scanLatestLogForGeneratedMarker());
        results.add(scanLatestLogForRawLoginCommands());

        boolean passed = results.stream().noneMatch(result -> result.status() == Status.FAIL);
        sender.sendMessage(Component.text(passed ? "ServerPass selftest: PASS" : "ServerPass selftest: FAIL", passed ? NamedTextColor.GREEN : NamedTextColor.RED));
        for (Result result : results) {
            sender.sendMessage(Component.text(result.status().name() + " " + result.name(), result.color()));
        }
        if (keep) {
            auditService.record(sender.getName(), passed ? "success" : "failure", "selftest");
        }
        return passed;
    }

    private Result scanLatestLogForGeneratedMarker() {
        String marker = "ServerPassSelfTest-" + UUID.randomUUID();
        Path latest = plugin.getServer().getWorldContainer().toPath().resolve("logs").resolve("latest.log");
        if (!Files.exists(latest)) {
            return Result.skip("unique password marker absent from latest.log (log file missing)");
        }
        try {
            String log = Files.readString(latest, StandardCharsets.UTF_8);
            return log.contains(marker)
                ? Result.fail("unique password marker absent from latest.log")
                : Result.pass("unique password marker absent from latest.log");
        } catch (IOException ex) {
            return Result.skip("unique password marker absent from latest.log (read skipped)");
        }
    }

    private Result scanLatestLogForRawLoginCommands() {
        Path latest = plugin.getServer().getWorldContainer().toPath().resolve("logs").resolve("latest.log");
        if (!Files.exists(latest)) {
            return Result.skip("raw login commands absent from latest.log (log file missing)");
        }
        try {
            String log = Files.readString(latest, StandardCharsets.UTF_8);
            return log.contains("issued server command: /login")
                ? Result.fail("raw login commands absent from latest.log")
                : Result.pass("raw login commands absent from latest.log");
        } catch (IOException ex) {
            return Result.skip("raw login commands absent from latest.log (read skipped)");
        }
    }

    private String randomSecret() {
        char[] chars = new char[32];
        for (int index = 0; index < chars.length; index++) {
            chars[index] = SECRET_CHARS[random.nextInt(SECRET_CHARS.length)];
        }
        return new String(chars);
    }

    private enum Status {
        PASS,
        FAIL,
        SKIP
    }

    private record Result(Status status, String name) {
        static Result pass(String name) {
            return new Result(Status.PASS, name);
        }

        static Result fail(String name) {
            return new Result(Status.FAIL, name);
        }

        static Result skip(String name) {
            return new Result(Status.SKIP, name);
        }

        NamedTextColor color() {
            return switch (status) {
                case PASS -> NamedTextColor.GREEN;
                case FAIL -> NamedTextColor.RED;
                case SKIP -> NamedTextColor.YELLOW;
            };
        }
    }
}
