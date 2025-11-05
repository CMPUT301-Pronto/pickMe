package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Profile - User profile information
 *
 * Represents a user's profile in the event lottery system.
 * Uses device ID as the primary identifier for device-based authentication.
 *
 * Firestore structure:
 * profiles/{userId}
 *   ├─ userId: "device_abc123"
 *   ├─ name: "John Doe"
 *   ├─ email: "john@example.com"
 *   ├─ phoneNumber: "+1234567890" (optional)
 *   ├─ notificationEnabled: true
 *   ├─ eventHistory: [...]
 *   └─ profileImageUrl: "https://..."
 */
public class Profile implements Parcelable {

    // Role constants
    public static final String ROLE_ENTRANT = "entrant";
    public static final String ROLE_ORGANIZER = "organizer";
    public static final String ROLE_ADMIN = "admin";

    private String userId; // Device ID or Firebase Auth UID
    private String name;
    private String phoneNumber;
    private String email;
    private String passwordHash;
    private String passwordSalt;
    private String passwordAlgo;
    private boolean notificationEnabled;
    private List<EventHistoryItem> eventHistory;
    private String profileImageUrl;
    private String role; // entrant, organizer, admin

    /**
     * Default constructor required for Firebase deserialization
     */
    public Profile() {
        this.eventHistory = new ArrayList<>();
        this.notificationEnabled = true; // Enabled by default
        this.role = ROLE_ENTRANT; // Default role
    }

    /**
     * Constructor with required fields
     *
     * @param userId User identifier (device ID)
     * @param name User's display name
     */
    public Profile(String userId, String name) {
        this.userId = userId;
        this.name = name;
        this.eventHistory = new ArrayList<>();
        this.notificationEnabled = true;
        this.role = ROLE_ENTRANT;
    }

    /**
     * Constructor with common fields
     *
     * @param userId User identifier
     * @param name Display name
     * @param email Email address
     */
    public Profile(String userId, String name, String email) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.eventHistory = new ArrayList<>();
        this.notificationEnabled = true;
        this.role = ROLE_ENTRANT;
    }

    // Getters and Setters

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
    public void setPasswordHash(String v) { this.passwordHash = v; }

    public String getPasswordSalt() { return passwordSalt; }
    public void setPasswordSalt(String v) { this.passwordSalt = v; }

    public String getPasswordAlgo() { return passwordAlgo; }
    public void setPasswordAlgo(String v) { this.passwordAlgo = v; }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }

    public void setNotificationEnabled(boolean notificationEnabled) {
        this.notificationEnabled = notificationEnabled;
    }

    public List<EventHistoryItem> getEventHistory() {
        return eventHistory;
    }

    public void setEventHistory(List<EventHistoryItem> eventHistory) {
        this.eventHistory = eventHistory;
    }

    public String getProfileImageUrl() {
        return profileImageUrl;
    }

    public void setProfileImageUrl(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }

    public String getRole() {
        return role != null ? role : ROLE_ENTRANT;
    }

    public void setRole(String role) {
        this.role = role;
    }

    // Helper Methods

    /**
     * Check if profile has complete basic information
     *
     * @return true if name and userId are set
     */
    public boolean isProfileComplete() {
        return userId != null && !userId.isEmpty()
                && name != null && !name.isEmpty();
    }

    /**
     * Check if user has contact information
     *
     * @return true if email or phone number is set
     */
    public boolean hasContactInfo() {
        return (email != null && !email.isEmpty())
                || (phoneNumber != null && !phoneNumber.isEmpty());
    }

    /**
     * Add event to history
     *
     * @param historyItem Event history item to add
     */
    public void addEventHistory(EventHistoryItem historyItem) {
        if (eventHistory == null) {
            eventHistory = new ArrayList<>();
        }
        eventHistory.add(historyItem);
    }

    /**
     * Get number of events user has participated in
     *
     * @return Count of event history items
     */
    public int getEventCount() {
        return eventHistory != null ? eventHistory.size() : 0;
    }

    /**
     * Check if user has participated in a specific event
     *
     * @param eventId Event identifier
     * @return true if event is in history
     */
    public boolean hasEventInHistory(String eventId) {
        if (eventHistory == null || eventId == null) {
            return false;
        }
        for (EventHistoryItem item : eventHistory) {
            if (eventId.equals(item.getEventId())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if user is an organizer
     */
    public boolean isOrganizer() {
        return ROLE_ORGANIZER.equals(role) || ROLE_ADMIN.equals(role);
    }

    /**
     * Check if user is an admin
     */
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    /**
     * Convert Profile to Map for Firestore
     *
     * @return Map representation of Profile
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("userId", userId);
        map.put("name", name);
        map.put("email", email);
        map.put("passwordHash", passwordHash);
        map.put("passwordSalt", passwordSalt);
        map.put("passwordAlgo", passwordAlgo);
        map.put("phoneNumber", phoneNumber);
        map.put("notificationEnabled", notificationEnabled);
        map.put("role", role);

        // Convert eventHistory to list of maps
        if (eventHistory != null) {
            List<Map<String, Object>> historyMaps = new ArrayList<>();
            for (EventHistoryItem item : eventHistory) {
                Map<String, Object> itemMap = new HashMap<>();
                itemMap.put("eventId", item.getEventId());
                itemMap.put("eventName", item.getEventName());
                itemMap.put("joinedTimestamp", item.getJoinedTimestamp());
                itemMap.put("status", item.getStatus());
                historyMaps.add(itemMap);
            }
            map.put("eventHistory", historyMaps);
        }

        map.put("profileImageUrl", profileImageUrl);
        return map;
    }

    @Override
    public String toString() {
        return "Profile{" +
                "userId='" + userId + '\'' +
                ", name='" + name + '\'' +
                ", email='" + email + '\'' +
                ", notificationEnabled=" + notificationEnabled +
                ", eventCount=" + getEventCount() +
                ", phoneNumber='" + phoneNumber + '\'' +
                '}';
    }

    // Parcelable implementation

    protected Profile(Parcel in) {
        userId = in.readString();
        name = in.readString();
        email = in.readString();
        phoneNumber = in.readString();
        notificationEnabled = in.readByte() != 0;
        eventHistory = new ArrayList<>();
        in.readList(eventHistory, EventHistoryItem.class.getClassLoader());
        profileImageUrl = in.readString();
        role = in.readString();
    }

    public static final Creator<Profile> CREATOR = new Creator<Profile>() {
        @Override
        public Profile createFromParcel(Parcel in) {
            return new Profile(in);
        }

        @Override
        public Profile[] newArray(int size) {
            return new Profile[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(userId);
        dest.writeString(name);
        dest.writeString(email);
        dest.writeString(phoneNumber);
        dest.writeByte((byte) (notificationEnabled ? 1 : 0));
        dest.writeList(eventHistory);
        dest.writeString(profileImageUrl);
        dest.writeString(role);
    }
}
