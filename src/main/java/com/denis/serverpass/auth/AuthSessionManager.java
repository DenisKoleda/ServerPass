package com.denis.serverpass.auth;

import com.denis.serverpass.config.PasswordConfigStore;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class AuthSessionManager {
    private final PasswordConfigStore config;
    private final Map<UUID, AuthSession> sessions = new ConcurrentHashMap<>();

    public AuthSessionManager(PasswordConfigStore config) {
        this.config = config;
    }

    public AuthSession requireAuthentication(Player player, Instant now) {
        return sessions.compute(player.getUniqueId(), (uuid, existing) -> {
            if (existing != null && !existing.authenticated()) {
                return existing;
            }
            return new AuthSession(uuid, player.getName(), now, Duration.ofSeconds(config.timeoutSeconds()));
        });
    }

    public void authenticate(Player player) {
        AuthSession session = sessions.computeIfAbsent(
            player.getUniqueId(),
            uuid -> new AuthSession(uuid, player.getName(), Instant.now(), Duration.ofSeconds(config.timeoutSeconds()))
        );
        session.authenticate();
    }

    public int recordFailure(Player player) {
        return requireAuthentication(player, Instant.now()).recordFailure();
    }

    public boolean isLocked(Player player) {
        if (!config.authEnabled()) {
            return false;
        }
        if (hasBypass(player)) {
            return false;
        }
        Optional<AuthSession> session = session(player);
        return session.map(value -> !value.authenticated()).orElse(config.requireEveryJoin() || config.isConfigured());
    }

    public boolean hasBypass(Player player) {
        String permission = config.bypassPermission();
        if (permission != null && !permission.isBlank() && player.hasPermission(permission)) {
            return true;
        }
        return config.allowOpsBypass() && player.isOp();
    }

    public boolean canConfigurePassword(CommandSender sender) {
        return !(sender instanceof Player) || sender.isOp() || sender.hasPermission("serverpass.set") || sender.hasPermission("serverpass.admin");
    }

    public Optional<AuthSession> session(Player player) {
        return Optional.ofNullable(sessions.get(player.getUniqueId()));
    }

    public void clear(UUID playerId) {
        sessions.remove(playerId);
    }

    public void clearAll() {
        sessions.clear();
    }

    public int authenticatedCount() {
        return (int) sessions.values().stream().filter(AuthSession::authenticated).count();
    }

    public int lockedCount() {
        return (int) sessions.values().stream().filter(session -> !session.authenticated()).count();
    }

    public boolean runSelfTest() {
        UUID playerId = UUID.randomUUID();
        Instant now = Instant.now();
        AuthSession session = new AuthSession(playerId, "selftest", now, Duration.ofSeconds(2));
        sessions.put(playerId, session);
        boolean locked = !session.authenticated();
        boolean failureCounted = session.recordFailure() == 1;
        session.authenticate();
        boolean unlocked = session.authenticated();
        sessions.remove(playerId);
        return locked && failureCounted && unlocked && !sessions.containsKey(playerId);
    }
}
