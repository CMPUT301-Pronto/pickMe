package com.example.pickme.utils;

import android.util.Base64;
import java.security.SecureRandom;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKeyFactory;

public final class PasswordUtil {
    private static final String ALGO = "PBKDF2WithHmacSHA256";
    private static final int ITER = 120_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private PasswordUtil() {}

    public static String generateSaltB64() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    public static String hashToB64(char[] password, String saltB64) {
        try {
            byte[] salt = Base64.decode(saltB64, Base64.NO_WRAP);
            PBEKeySpec spec = new PBEKeySpec(password, salt, ITER, KEY_BITS);
            byte[] hash = SecretKeyFactory.getInstance(ALGO).generateSecret(spec).getEncoded();
            spec.clearPassword();
            return Base64.encodeToString(hash, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new RuntimeException("Password hashing failed", e);
        }
    }

    public static boolean verify(char[] password, String saltB64, String expectedHashB64) {
        String got = hashToB64(password, saltB64);
        if (got.length() != expectedHashB64.length()) return false;
        int r = 0;
        for (int i = 0; i < got.length(); i++) r |= got.charAt(i) ^ expectedHashB64.charAt(i);
        return r == 0;
    }

    public static String algorithmName() { return ALGO; }
}