package com.securevoting.crypto;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-256 proof-of-work miner. Brute-forces a nonce such that the hex hash of
 * (index|prevHash|timestamp|encryptedData|nonce) begins with `difficulty` zero chars.
 */
public final class ProofOfWork {

    private static final long MAX_ITERATIONS = 10_000_000L;

    private ProofOfWork() { }

    public record MinedBlock(long nonce, String hash, long timestamp) { }

    /** Mines a block. The timestamp is captured at the start and kept stable across nonce attempts. */
    public static MinedBlock mine(long blockIndex,
                                  String prevHash,
                                  String encryptedData,
                                  int difficulty) {
        if (difficulty < 1 || difficulty > 12) {
            throw new IllegalArgumentException("Difficulty must be 1..12 (got " + difficulty + ").");
        }
        String target = "0".repeat(difficulty);
        long timestamp = System.currentTimeMillis();
        for (long nonce = 0; nonce < MAX_ITERATIONS; nonce++) {
            String hash = sha256Hex(serialize(blockIndex, prevHash, timestamp, encryptedData, nonce));
            if (hash.startsWith(target)) {
                return new MinedBlock(nonce, hash, timestamp);
            }
        }
        throw new IllegalStateException(
                "Could not mine block " + blockIndex + " within " + MAX_ITERATIONS + " iterations. Lower difficulty.");
    }

    /** Recomputes a block's hash for validation. */
    public static String hashOf(long blockIndex,
                                String prevHash,
                                long timestamp,
                                String encryptedData,
                                long nonce) {
        return sha256Hex(serialize(blockIndex, prevHash, timestamp, encryptedData, nonce));
    }

    private static String serialize(long blockIndex, String prevHash, long timestamp,
                                    String encryptedData, long nonce) {
        return blockIndex + "|" + prevHash + "|" + timestamp + "|" + encryptedData + "|" + nonce;
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}