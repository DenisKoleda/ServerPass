package com.denis.serverpass.crypto;

import com.denis.serverpass.crypto.PasswordHasher.HashRecord;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {
    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void generatedHashValidatesOnlyMatchingPassword() {
        String password = "secret-" + UUID.randomUUID();
        HashRecord record = hasher.hash(password, PasswordHasher.DEFAULT_ALGORITHM, PasswordHasher.DEFAULT_ITERATIONS);

        assertTrue(hasher.verify(password, record));
        assertFalse(hasher.verify(password + "-wrong", record));
    }

    @Test
    void emptyPasswordIsRejectedForNewHash() {
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("", PasswordHasher.DEFAULT_ALGORITHM, PasswordHasher.DEFAULT_ITERATIONS));
    }

    @Test
    void twoHashesForSamePasswordUseDifferentSalts() {
        String password = "secret-" + UUID.randomUUID();
        HashRecord first = hasher.hash(password, PasswordHasher.DEFAULT_ALGORITHM, PasswordHasher.DEFAULT_ITERATIONS);
        HashRecord second = hasher.hash(password, PasswordHasher.DEFAULT_ALGORITHM, PasswordHasher.DEFAULT_ITERATIONS);

        assertNotEquals(first.salt(), second.salt());
        assertNotEquals(first.hash(), second.hash());
        assertTrue(hasher.verify(password, first));
        assertTrue(hasher.verify(password, second));
    }

    @Test
    void storedSaltAndHashDoNotContainPlainPasswordMarker() {
        String password = "plain-marker-" + UUID.randomUUID();
        HashRecord record = hasher.hash(password, PasswordHasher.DEFAULT_ALGORITHM, PasswordHasher.DEFAULT_ITERATIONS);

        assertFalse(record.salt().contains(password));
        assertFalse(record.hash().contains(password));
    }
}
