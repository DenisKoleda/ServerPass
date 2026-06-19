package com.denis.serverpass.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public final class AuthSession {
    private final UUID playerId;
    private final String playerName;
    private final Instant startedAt;
    private final Instant deadline;
    private int attempts;
    private boolean authenticated;

    public AuthSession(UUID playerId, String playerName, Instant startedAt, Duration timeout) {
        this.playerId = playerId;
        this.playerName = playerName;
        this.startedAt = startedAt;
        this.deadline = startedAt.plus(timeout);
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public int attempts() {
        return attempts;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public void authenticate() {
        authenticated = true;
    }

    public int recordFailure() {
        attempts++;
        return attempts;
    }

    public boolean isExpired(Instant now) {
        return !authenticated && !now.isBefore(deadline);
    }

    public long remainingSeconds(Instant now) {
        if (!now.isBefore(deadline)) {
            return 0L;
        }
        return Math.max(0L, Duration.between(now, deadline).toSeconds());
    }
}
