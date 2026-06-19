package com.denis.serverpass.auth;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthSessionTest {
    @Test
    void sessionStartsLockedThenUnlocks() {
        AuthSession session = new AuthSession(UUID.randomUUID(), "player", Instant.now(), Duration.ofSeconds(60));

        assertFalse(session.authenticated());
        assertEquals(1, session.recordFailure());
        session.authenticate();
        assertTrue(session.authenticated());
    }

    @Test
    void timeoutExpiresUnauthenticatedSession() {
        Instant start = Instant.parse("2026-06-19T00:00:00Z");
        AuthSession session = new AuthSession(UUID.randomUUID(), "player", start, Duration.ofSeconds(60));

        assertFalse(session.isExpired(start.plusSeconds(59)));
        assertTrue(session.isExpired(start.plusSeconds(60)));
    }
}
