package com.example.pickme.utils;

import android.util.Base64;
import java.security.SecureRandom;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.SecretKeyFactory;
/**
 * JAVADOCS LLM GENERATED
 *
 * Utility class for secure password hashing and verification.
 *
 * <p><b>Role / Pattern:</b> Static utility class (non-instantiable)
 * that provides secure, deterministic password hashing functions
 * for authentication and profile management.</p>
 *
 * <p><b>Algorithm:</b> PBKDF2 with HMAC-SHA256, using 120,000 iterations,
 * 256-bit derived keys, and 128-bit random salts. All encoded
 * outputs are Base64 strings for compact Firestore storage.</p>
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Generate cryptographically secure random salts.</li>
 *   <li>Hash passwords using PBKDF2WithHmacSHA256.</li>
 *   <li>Verify user-supplied passwords in constant time to prevent timing attacks.</li>
 *   <li>Expose algorithm metadata for storage or interoperability.</li>
 * </ul>
 * </p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>{@code
 * String salt = PasswordUtil.generateSaltB64();
 * String hash = PasswordUtil.hashToB64("secret".toCharArray(), salt);
 * boolean ok = PasswordUtil.verify("secret".toCharArray(), salt, hash);
 * }</pre>
 *
 * <p><b>Design Notes:</b>
 * <ul>
 *   <li>Stateless and thread-safe.</li>
 *   <li>Uses {@link SecureRandom} for cryptographically strong salt generation.</li>
 *   <li>Implements constant-time string comparison to avoid timing side-channel leaks.</li>
 *   <li>Throws {@link RuntimeException} on unrecoverable cryptographic failures.</li>
 * </ul>
 * </p>
 *
 * <p><b>Related Classes:</b>
 * <ul>
 *   <li>{@link com.example.pickme.models.User} — stores hash, salt, and algorithm.</li>
 *   <li>{@link com.example.pickme.models.Profile} — may reuse password fields for local authentication.</li>
 * </ul>
 * </p>
 */
public final class PasswordUtil {
    /** Password-Based Key Derivation Function (PBKDF2) algorithm identifier. */
    private static final String ALGO = "PBKDF2WithHmacSHA256";
    /** Number of PBKDF2 iterations (increases brute-force cost). */
    private static final int ITER = 120_000;
    /** Derived key length in bits. */
    private static final int KEY_BITS = 256;
    /** Salt length in bytes. */
    private static final int SALT_BYTES = 16;

    // private constructor to prevent instantiation
    private PasswordUtil() {}
    /**
     * Generates a new cryptographically secure random salt encoded in Base64.
     *
     * @return Base64-encoded salt string (no line wraps).
     */
    public static String generateSaltB64() {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return Base64.encodeToString(salt, Base64.NO_WRAP);
    }

    /**
     * Derives a Base64-encoded PBKDF2 hash from a password and salt.
     *
     * @param password  user-supplied password as a char array.
     * @param saltB64   Base64-encoded salt string.
     * @return Base64-encoded hash string suitable for storage.
     * @throws RuntimeException if cryptographic operations fail.
     */
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

    /**
     * Verifies a password by hashing it with the stored salt and comparing
     * to the expected hash using a constant-time equality check.
     *
     * @param password         password to verify (as char array).
     * @param saltB64          Base64-encoded salt.
     * @param expectedHashB64  stored Base64-encoded hash to compare against.
     * @return {@code true} if the password matches; {@code false} otherwise.
     */
    public static boolean verify(char[] password, String saltB64, String expectedHashB64) {
        String got = hashToB64(password, saltB64);
        if (got.length() != expectedHashB64.length()) return false;
        int r = 0;
        for (int i = 0; i < got.length(); i++) r |= got.charAt(i) ^ expectedHashB64.charAt(i);
        return r == 0;
    }

    /**
     * Returns the canonical algorithm name used for password hashing.
     *
     * @return PBKDF2 algorithm identifier.
     */
    public static String algorithmName() { return ALGO; }
}