package com.example.pickme.models;

import java.util.HashMap;
import java.util.Map;
/**
 * JAVADOCS LLM GENERATED
 *
 * Firestore DTO for application users (authentication identity + profile snapshot).
 * <p>
 * <b>Role / Pattern:</b> Plain Old Java Object (POJO) that maps 1:1 to a Firestore document.
 * Prefer using {@link Profile} for UI/state; use {@code User} to represent identity + creation metadata.
 * </p>
 *
 * <p>
 * <b>Security:</b> Contains optional password hash metadata for custom auth flows (hash, salt, algo).
 * Prefer Firebase Authentication where possible; do not store plaintext passwords.
 * </p>
 *
 * <p>
 * <b>Outstanding issues / TODOs:</b>
 * <ul>
 *   <li>Consolidate identity (User) and UI state (Profile) if duplication causes drift.</li>
 *   <li>Verify that hash/salt/algo align with server-side auth policy and rotation strategy.</li>
 * </ul>
 * </p>
 *
 * <p><b>Firestore structure:</b></p>
 * <pre>
 * users/{userId}
 *   ├─ name
 *   ├─ email
 *   ├─ profileImageUrl
 *   ├─ phoneNumber
 *   ├─ deviceId
 *   ├─ createdAt
 *   ├─ role (entrant/organizer/admin)
 *   ├─ passwordHash (optional)
 *   ├─ passwordSalt (optional)
 *   └─ passwordAlgo (optional)
 * </pre>
 */
public class User {

    // User roles
    public static final String ROLE_ENTRANT = "entrant";
    public static final String ROLE_ORGANIZER = "organizer";
    public static final String ROLE_ADMIN = "admin";

    // Role constants
    private String userId;
    private String name;
    private String email;
    private String profileImageUrl;
    private String phoneNumber;
    private String deviceId;
    private long createdAt;
    private String role;

    // password security
    private String passwordHash; // base64-encoded PBKDF hash
    private String passwordSalt; // base64-encoded random salt
    private String passwordAlgo;


    /**
     * Default constructor required for Firestore deserialization
     * Firestore uses reflection to create objects from documents
     */
    public User() {
        // Default constructor required for calls to DataSnapshot.getValue(User.class)
    }

    /**
     * Full constructor for Firestore deserialization or manual creation.
     *
     * @param userId       Firebase Auth UID (or unique id)
     * @param name         Display name
     * @param email        Email address (optional)
     * @param profileImageUrl Profile image URL
     * @param phoneNumber  Phone number (optional)
     * @param deviceId     Device identifier (device-based auth)
     * @param createdAt    Epoch millis of account creation
     * @param role         Role string (entrant/organizer/admin)
     * @param passwordHash Base64-encoded hash (custom auth only)
     * @param passwordSalt Base64-encoded salt (custom auth only)
     * @param passwordAlgo Hash algorithm identifier
     */
    public User(String userId, String name, String email, String profileImageUrl,
                String phoneNumber, String deviceId, long createdAt, String role, String passwordHash, String passwordSalt, String passwordAlgo) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.passwordSalt = passwordSalt;
        this.passwordAlgo = passwordAlgo;
        this.profileImageUrl = profileImageUrl;
        this.phoneNumber = phoneNumber;
        this.deviceId = deviceId;
        this.createdAt = createdAt;
        this.role = role;
    }
    /**
     * Convenience constructor for minimal user creation.
     *
     * @param userId  Unique identifier
     * @param name    Display name
     * @param deviceId Device identifier (device-based auth)
     */
    public User(String userId, String name, String deviceId) {
        this.userId = userId;
        this.name = name;
        this.deviceId = deviceId;
        this.createdAt = System.currentTimeMillis();
        this.role = ROLE_ENTRANT;  // Default role
    }

    // Getters and Setters
    // Required by Firestore for serialization/deserialization

    /** @return Unique user identifier. */
    public String getUserId() { return userId; }

    /** @param userId Unique user identifier to set. */
    public void setUserId(String userId) { this.userId = userId; }

    /** @return Display name. */
    public String getName() { return name; }

    /** @param name Display name to set. */
    public void setName(String name) { this.name = name; }

    /** @return Email address or null if not set. */
    public String getEmail() { return email; }

    /** @param email Email address to set. */
    public void setEmail(String email) { this.email = email; }

    /** @return Base64-encoded password hash (custom auth only). */
    public String getPasswordHash() { return passwordHash; }

    /** @param passwordHash Base64-encoded password hash (custom auth only). */
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    /** @return Base64-encoded password salt (custom auth only). */
    public String getPasswordSalt() { return passwordSalt; }

    /** @param passwordSalt Base64-encoded password salt (custom auth only). */
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }

    /** @return Hash algorithm identifier, e.g., "PBKDF2WithHmacSHA256". */
    public String getPasswordAlgo() { return passwordAlgo; }

    /** @param passwordAlgo Hash algorithm identifier to set. */
    public void setPasswordAlgo(String passwordAlgo) { this.passwordAlgo = passwordAlgo; }

    /** @return Profile image URL or null if not set. */
    public String getProfileImageUrl() { return profileImageUrl; }

    /** @param profileImageUrl Profile image URL to set. */
    public void setProfileImageUrl(String profileImageUrl) { this.profileImageUrl = profileImageUrl; }

    /** @return Phone number or null if not set. */
    public String getPhoneNumber() { return phoneNumber; }

    /** @param phoneNumber Phone number to set. */
    public void setPhoneNumber(String phoneNumber) { this.phoneNumber = phoneNumber; }

    /** @return Device identifier (device-based auth). */
    public String getDeviceId() { return deviceId; }

    /** @param deviceId Device identifier to set. */
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }

    /** @return Epoch millis of account creation. */
    public long getCreatedAt() { return createdAt; }

    /** @param createdAt Epoch millis of account creation. */
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    /** @return Role string (entrant/organizer/admin). */
    public String getRole() { return role; }

    /** @param role Role string to set (entrant/organizer/admin). */
    public void setRole(String role) { this.role = role; }

    /**
     * Convert User object to Map for Firestore
     * Useful when you need more control over what gets saved
     *
     * @return Map representation of User
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("name", name);
        map.put("email", email);
        map.put("profileImageUrl", profileImageUrl);
        map.put("phoneNumber", phoneNumber);
        map.put("deviceId", deviceId);
        map.put("createdAt", createdAt);
        map.put("role", role);
        // security fields
        map.put("passwordHash", passwordHash);
        map.put("passwordSalt", passwordSalt);
        map.put("passwordAlgo", passwordAlgo);
        return map;
    }

    /**
     * Check if user is an organizer
     *
     * @return true if user has organizer role
     */
    public boolean isOrganizer() {
        return ROLE_ORGANIZER.equals(role);
    }

    /**
     * Check if user is an admin
     *
     * @return true if user has admin role
     */
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /**
     * Check if user profile is complete
     *
     * @return true if all required fields are filled
     */
    public boolean isProfileComplete() {
        return name != null && !name.isEmpty()
                && deviceId != null && !deviceId.isEmpty();
    }

    @Override
    public String toString() {
        return "User{" +
                "userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", role='" + role + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
