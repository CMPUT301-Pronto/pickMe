package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * WaitingList - Tracks initial interested entrants for an event
 *
 * Represents the pool of users who have expressed interest in an event
 * by joining the waiting list. This is the first stage before lottery selection.
 *
 * Lifecycle: User joins → WaitingList → Lottery → ResponsePendingList → InEventList
 *
 * Firestore structure:
 * waiting_lists/{eventId}
 *   ├─ eventId: "event123"
 *   ├─ entrantIds: ["user1", "user2", ...]
 *   ├─ geolocationData: {
 *   │    "user1": { latitude: 53.5, longitude: -113.5, timestamp: 123... },
 *   │    "user2": { ... }
 *   │  }
 *   └─ entrantTimestamps: {
 *        "user1": 1234567890,
 *        "user2": 1234567891
 *      }
 */
public class WaitingList implements Parcelable {

    private String eventId;
    private List<String> entrantIds;
    private Map<String, Geolocation> geolocationData; // entrantId -> Geolocation
    private Map<String, Long> entrantTimestamps; // entrantId -> join timestamp

    /**
     * Default constructor required for Firebase deserialization
     */
    public WaitingList() {
        this.entrantIds = new ArrayList<>();
        this.geolocationData = new HashMap<>();
        this.entrantTimestamps = new HashMap<>();
    }

    /**
     * Constructor with event ID
     *
     * @param eventId Event this waiting list belongs to
     */
    public WaitingList(String eventId) {
        this.eventId = eventId;
        this.entrantIds = new ArrayList<>();
        this.geolocationData = new HashMap<>();
        this.entrantTimestamps = new HashMap<>();
    }

    // Getters and Setters

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public List<String> getEntrantIds() {
        return entrantIds;
    }

    public void setEntrantIds(List<String> entrantIds) {
        this.entrantIds = entrantIds;
    }

    public Map<String, Geolocation> getGeolocationData() {
        return geolocationData;
    }

    public void setGeolocationData(Map<String, Geolocation> geolocationData) {
        this.geolocationData = geolocationData;
    }

    public Map<String, Long> getEntrantTimestamps() {
        return entrantTimestamps;
    }

    public void setEntrantTimestamps(Map<String, Long> entrantTimestamps) {
        this.entrantTimestamps = entrantTimestamps;
    }

    // Collection Management Methods

    /**
     * Add entrant to waiting list
     * Prevents duplicate entries
     *
     * @param entrantId User ID to add
     * @param location Optional geolocation (can be null)
     * @return true if added, false if already exists
     */
    public boolean addEntrant(String entrantId, Geolocation location) {
        if (entrantId == null || entrantId.isEmpty()) {
            return false;
        }

        // Check for duplicates
        if (containsEntrant(entrantId)) {
            return false;
        }

        entrantIds.add(entrantId);
        entrantTimestamps.put(entrantId, System.currentTimeMillis());

        if (location != null) {
            geolocationData.put(entrantId, location);
        }

        return true;
    }

    /**
     * Remove entrant from waiting list
     *
     * @param entrantId User ID to remove
     * @return true if removed, false if not found
     */
    public boolean removeEntrant(String entrantId) {
        if (entrantId == null) {
            return false;
        }

        boolean removed = entrantIds.remove(entrantId);
        geolocationData.remove(entrantId);
        entrantTimestamps.remove(entrantId);

        return removed;
    }

    /**
     * Check if entrant is in waiting list
     *
     * @param entrantId User ID to check
     * @return true if entrant is in list
     */
    public boolean containsEntrant(String entrantId) {
        return entrantIds != null && entrantIds.contains(entrantId);
    }

    /**
     * Get number of entrants in waiting list
     *
     * @return Count of entrants
     */
    public int getEntrantCount() {
        return entrantIds != null ? entrantIds.size() : 0;
    }

    /**
     * Get available spots on waiting list
     *
     * @param limit Maximum waiting list size (-1 for unlimited)
     * @return Number of available spots, Integer.MAX_VALUE if unlimited
     */
    public int getAvailableSpots(int limit) {
        if (limit == -1) {
            return Integer.MAX_VALUE; // Unlimited
        }
        return Math.max(0, limit - getEntrantCount());
    }

    /**
     * Get all entrant IDs
     *
     * @return List of entrant IDs (copy to prevent modification)
     */
    public List<String> getAllEntrants() {
        return new ArrayList<>(entrantIds);
    }

    /**
     * Get entrants who provided location data
     *
     * @return List of entrant IDs with geolocation
     */
    public List<String> getEntrantsWithLocation() {
        return new ArrayList<>(geolocationData.keySet());
    }

    /**
     * Get location for specific entrant
     *
     * @param entrantId User ID
     * @return Geolocation or null if not available
     */
    public Geolocation getEntrantLocation(String entrantId) {
        return geolocationData != null ? geolocationData.get(entrantId) : null;
    }

    /**
     * Get join timestamp for specific entrant
     *
     * @param entrantId User ID
     * @return Timestamp or null if not found
     */
    public Long getEntrantJoinTime(String entrantId) {
        return entrantTimestamps != null ? entrantTimestamps.get(entrantId) : null;
    }

    /**
     * Check if waiting list has space
     *
     * @param limit Maximum waiting list size (-1 for unlimited)
     * @return true if there is space
     */
    public boolean hasSpace(int limit) {
        if (limit == -1) {
            return true;
        }
        return getEntrantCount() < limit;
    }

    /**
     * Clear all entrants from waiting list
     */
    public void clear() {
        entrantIds.clear();
        geolocationData.clear();
        entrantTimestamps.clear();
    }

    /**
     * Convert WaitingList to Map for Firestore
     *
     * @return Map representation of WaitingList
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("entrantIds", entrantIds);

        // Convert geolocation data
        Map<String, Object> geoMap = new HashMap<>();
        if (geolocationData != null) {
            for (Map.Entry<String, Geolocation> entry : geolocationData.entrySet()) {
                geoMap.put(entry.getKey(), entry.getValue().toMap());
            }
        }
        map.put("geolocationData", geoMap);
        map.put("entrantTimestamps", entrantTimestamps);

        return map;
    }

    /**
     * Helper method to convert Geolocation to Map
     */
    private Map<String, Object> geolocationToMap(Geolocation geo) {
        Map<String, Object> map = new HashMap<>();
        map.put("latitude", geo.getLatitude());
        map.put("longitude", geo.getLongitude());
        map.put("timestamp", geo.getTimestamp());
        return map;
    }

    @Override
    public String toString() {
        return "WaitingList{" +
                "eventId='" + eventId + '\'' +
                ", entrantCount=" + getEntrantCount() +
                ", withLocation=" + getEntrantsWithLocation().size() +
                '}';
    }

    // Parcelable implementation

    protected WaitingList(Parcel in) {
        eventId = in.readString();
        entrantIds = new ArrayList<>();
        in.readList(entrantIds, String.class.getClassLoader());

        // Read geolocation data
        int geoSize = in.readInt();
        geolocationData = new HashMap<>();
        for (int i = 0; i < geoSize; i++) {
            String key = in.readString();
            Geolocation value = in.readParcelable(Geolocation.class.getClassLoader());
            geolocationData.put(key, value);
        }

        // Read timestamps
        int timeSize = in.readInt();
        entrantTimestamps = new HashMap<>();
        for (int i = 0; i < timeSize; i++) {
            String key = in.readString();
            Long value = in.readLong();
            entrantTimestamps.put(key, value);
        }
    }

    public static final Creator<WaitingList> CREATOR = new Creator<WaitingList>() {
        @Override
        public WaitingList createFromParcel(Parcel in) {
            return new WaitingList(in);
        }

        @Override
        public WaitingList[] newArray(int size) {
            return new WaitingList[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(eventId);
        dest.writeList(entrantIds);

        // Write geolocation data
        dest.writeInt(geolocationData.size());
        for (Map.Entry<String, Geolocation> entry : geolocationData.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeParcelable(entry.getValue(), flags);
        }

        // Write timestamps
        dest.writeInt(entrantTimestamps.size());
        for (Map.Entry<String, Long> entry : entrantTimestamps.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeLong(entry.getValue());
        }
    }
}

