package com.denis.serverpass.crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.Set;

public final class PasswordHasher {
    public static final String DEFAULT_ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int DEFAULT_ITERATIONS = 210_000;
    private static final int SALT_BYTES = 32;
    private static final int KEY_BITS = 256;
    private static final Set<String> SUPPORTED_ALGORITHMS = Set.of("PBKDF2WithHmacSHA256", "PBKDF2WithHmacSHA512");

    private final SecureRandom secureRandom = new SecureRandom();

    public HashRecord hash(String password, String algorithm, int iterations) {
        requirePassword(password);
        String normalizedAlgorithm = normalizeAlgorithm(algorithm);
        int normalizedIterations = normalizeIterations(iterations);
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        byte[] hash = derive(password, normalizedAlgorithm, normalizedIterations, salt);
        return new HashRecord(
            normalizedAlgorithm,
            normalizedIterations,
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash)
        );
    }

    public boolean verify(String password, HashRecord record) {
        if (password == null || password.isEmpty() || record == null || record.salt().isBlank() || record.hash().isBlank()) {
            return false;
        }
        String normalizedAlgorithm = normalizeAlgorithm(record.algorithm());
        int normalizedIterations = normalizeIterations(record.iterations());
        byte[] salt = Base64.getDecoder().decode(record.salt());
        byte[] expected = Base64.getDecoder().decode(record.hash());
        byte[] actual = derive(password, normalizedAlgorithm, normalizedIterations, salt);
        try {
            return MessageDigest.isEqual(expected, actual);
        } finally {
            Arrays.fill(actual, (byte) 0);
        }
    }

    public boolean supports(String algorithm) {
        return SUPPORTED_ALGORITHMS.contains(normalizeAlgorithm(algorithm));
    }

    private byte[] derive(String password, String algorithm, int iterations, byte[] salt) {
        char[] chars = password.toCharArray();
        PBEKeySpec spec = new PBEKeySpec(chars, salt, iterations, KEY_BITS);
        try {
            return SecretKeyFactory.getInstance(algorithm).generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException ex) {
            throw new IllegalStateException("Password hash specification is invalid", ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Password hash algorithm is unavailable: " + algorithm, ex);
        } finally {
            spec.clearPassword();
            Arrays.fill(chars, '\0');
        }
    }

    private void requirePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password must not be empty");
        }
    }

    private String normalizeAlgorithm(String algorithm) {
        if (algorithm == null || algorithm.isBlank()) {
            return DEFAULT_ALGORITHM;
        }
        String trimmed = algorithm.trim();
        for (String supported : SUPPORTED_ALGORITHMS) {
            if (supported.toLowerCase(Locale.ROOT).equals(trimmed.toLowerCase(Locale.ROOT))) {
                return supported;
            }
        }
        throw new IllegalArgumentException("Unsupported password hash algorithm: " + trimmed);
    }

    private int normalizeIterations(int iterations) {
        return Math.max(10_000, iterations);
    }

    public record HashRecord(String algorithm, int iterations, String salt, String hash) {
    }
}
