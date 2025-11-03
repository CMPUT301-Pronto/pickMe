package com.example.pickme.models;

import java.util.HashMap;
import java.util.Map;

/**
 * User - Model class representing a user in the event lottery system
 *
 * This is a Plain Old Java Object (POJO) that represents user data.
 * Firestore automatically converts between this class and Firestore documents.
 *
 * Fields:
 * - userId: Unique identifier (Firebase Auth UID)
 * - name: User's display name
 * - email: User's email (optional)
 * - profileImageUrl: URL to profile image in Firebase Storage
 * - phoneNumber: User's phone number (optional)
 * - deviceId: Device identifier for device-based authentication
 * - createdAt: Timestamp of account creation
 * - role: User role (entrant, organizer, admin)
 *
 * Firestore Structure:
 * users/{userId}
 *   ├─ name: "John Doe"
 *   ├─ email: "john@example.com"
 *   ├─ profileImageUrl: "https://..."
 *   ├─ phoneNumber: "+1234567890"
 *   ├─ deviceId: "device123"
 *   ├─ createdAt: 1234567890
 *   └─ role: "entrant"
 */
public class User {

    // User roles
    public static final String ROLE_ENTRANT = "entrant";
    public static final String ROLE_ORGANIZER = "organizer";
    public static final String ROLE_ADMIN = "admin";

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
     * Constructor with all fields
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
     * Minimal constructor for new users
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

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String passwordSalt) { this.passwordSalt = passwordSalt; }

    public String getPasswordAlgo() { return passwordAlgo; }
    public void setPasswordAlgo(String passwordAlgo) { this.passwordAlgo = passwordAlgo; }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

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
