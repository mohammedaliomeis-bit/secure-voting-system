package com.securevoting.util;

import java.security.SecureRandom;

public final class CodeGenerator {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] ALPHANUM = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray(); // no 0/O/1/I/L

    private CodeGenerator() { }

    /** Numeric OTP of the requested length (e.g. 6). Zero-padded. */
    public static String numericOtp(int length) {
        if (length < 4 || length > 10) throw new IllegalArgumentException("length out of range");
        long max = (long) Math.pow(10, length);
        long n = (RNG.nextLong() & Long.MAX_VALUE) % max;
        return String.format("%0" + length + "d", n);
    }

    /** Election code in the form ELX-XXXXX (5 chars from ALPHANUM). */
    public static String electionCode() {
        StringBuilder sb = new StringBuilder("ELX-");
        for (int i = 0; i < 5; i++) sb.append(ALPHANUM[RNG.nextInt(ALPHANUM.length)]);
        return sb.toString();
    }

    /** 16-digit numeric receipt shown to voter once. */
    public static String receipt16() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) sb.append(RNG.nextInt(10));
        return sb.toString();
    }

    /** Base64 random salt of the requested byte length. */
    public static String base64Salt(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RNG.nextBytes(bytes);
        return java.util.Base64.getEncoder().encodeToString(bytes);
    }
}