package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * InEventList - Tracks confirmed participants for an event
 * 
 * Represents users who have accepted their invitation and are confirmed
 * to participate in the event. This is the final list of attendees.
 * 
 * Lifecycle: ResponsePendingList (accepted) → InEventList → Event attendance
 * 
 * Firestore structure:
 * in_event_lists/{eventId}
 *   ├─ eventId: "event123"
 *   ├─ entrantIds: ["user1", "user2", ...]
 *   ├─ geolocationData: { ... }
 *   ├─ enrolledTimestamps: {
 *   │    "user1": 1234567890,
 *   │    "user2": 1234567891
 *   │  }
 *   └─ checkInStatus: {
 *        "user1": true,
 *        "user2": false
 *      }
 */
public class InEventList implements Parcelable {
    
    private String eventId;
    private List<String> entrantIds;
    private Map<String, Geolocation> geolocationData; // entrantId -> Geolocation
    private Map<String, Long> enrolledTimestamps; // entrantId -> enrollment timestamp
    private Map<String, Boolean> checkInStatus; // entrantId -> checked in status
    
    /**
     * Default constructor required for Firebase deserialization
     */
    public InEventList() {
        this.entrantIds = new ArrayList<>();
        this.geolocationData = new HashMap<>();
        this.enrolledTimestamps = new HashMap<>();
        this.checkInStatus = new HashMap<>();
    }
    
    /**
     * Constructor with event ID
     * 
     * @param eventId Event this confirmed list belongs to
     */
    public InEventList(String eventId) {
        this.eventId = eventId;
        this.entrantIds = new ArrayList<>();
        this.geolocationData = new HashMap<>();
        this.enrolledTimestamps = new HashMap<>();
        this.checkInStatus = new HashMap<>();
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
    
    public Map<String, Long> getEnrolledTimestamps() {
        return enrolledTimestamps;
    }
    
    public void setEnrolledTimestamps(Map<String, Long> enrolledTimestamps) {
        this.enrolledTimestamps = enrolledTimestamps;
    }
    
    public Map<String, Boolean> getCheckInStatus() {
        return checkInStatus;
    }
    
    public void setCheckInStatus(Map<String, Boolean> checkInStatus) {
        this.checkInStatus = checkInStatus;
    }
    
    // Collection Management Methods
    
    /**
     * Add entrant to confirmed participant list
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
        enrolledTimestamps.put(entrantId, System.currentTimeMillis());
        checkInStatus.put(entrantId, false); // Not checked in initially
        
        if (location != null) {
            geolocationData.put(entrantId, location);
        }
        
        return true;
    }
    
    /**
     * Remove entrant from participant list
     * Used if participant cancels
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
        enrolledTimestamps.remove(entrantId);
        checkInStatus.remove(entrantId);
        
        return removed;
    }
    
    /**
     * Check if entrant is in participant list
     * 
     * @param entrantId User ID to check
     * @return true if entrant is in list
     */
    public boolean containsEntrant(String entrantId) {
        return entrantIds != null && entrantIds.contains(entrantId);
    }
    
    /**
     * Get number of confirmed participants
     * 
     * @return Count of participants
     */
    public int getEntrantCount() {
        return entrantIds != null ? entrantIds.size() : 0;
    }
    
    /**
     * Get available spots
     * 
     * @param capacity Event capacity
     * @return Number of spots still available
     */
    public int getAvailableSpots(int capacity) {
        return Math.max(0, capacity - getEntrantCount());
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
     * Get enrollment timestamp for specific entrant
     * 
     * @param entrantId User ID
     * @return Timestamp or null if not found
     */
    public Long getEntrantEnrollmentTime(String entrantId) {
        return enrolledTimestamps != null ? enrolledTimestamps.get(entrantId) : null;
    }
    
    /**
     * Mark entrant as checked in
     * 
     * @param entrantId User ID to check in
     * @return true if successful, false if not in list
     */
    public boolean checkInEntrant(String entrantId) {
        if (!containsEntrant(entrantId)) {
            return false;
        }
        checkInStatus.put(entrantId, true);
        return true;
    }
    
    /**
     * Check if entrant has checked in
     * 
     * @param entrantId User ID
     * @return true if checked in, false otherwise
     */
    public boolean isCheckedIn(String entrantId) {
        Boolean status = checkInStatus.get(entrantId);
        return status != null && status;
    }
    
    /**
     * Get count of checked in entrants
     * 
     * @return Number of participants who have checked in
     */
    public int getCheckedInCount() {
        int count = 0;
        if (checkInStatus != null) {
            for (Boolean status : checkInStatus.values()) {
                if (status != null && status) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * Get list of checked in entrants
     * 
     * @return List of entrant IDs who have checked in
     */
    public List<String> getCheckedInEntrants() {
        List<String> checkedIn = new ArrayList<>();
        if (checkInStatus != null) {
            for (Map.Entry<String, Boolean> entry : checkInStatus.entrySet()) {
                if (entry.getValue() != null && entry.getValue()) {
                    checkedIn.add(entry.getKey());
                }
            }
        }
        return checkedIn;
    }
    
    /**
     * Check if event is at capacity
     * 
     * @param capacity Event capacity
     * @return true if at capacity
     */
    public boolean isAtCapacity(int capacity) {
        return getEntrantCount() >= capacity;
    }
    
    /**
     * Clear all entrants from list
     */
    public void clear() {
        entrantIds.clear();
        geolocationData.clear();
        enrolledTimestamps.clear();
        checkInStatus.clear();
    }
    
    /**
     * Convert InEventList to Map for Firestore
     * 
     * @return Map representation
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("entrantIds", entrantIds);
        
        // Convert geolocation data
        Map<String, Object> geoMap = new HashMap<>();
        if (geolocationData != null) {
            for (Map.Entry<String, Geolocation> entry : geolocationData.entrySet()) {
                Map<String, Object> locMap = new HashMap<>();
                locMap.put("latitude", entry.getValue().getLatitude());
                locMap.put("longitude", entry.getValue().getLongitude());
                locMap.put("timestamp", entry.getValue().getTimestamp());
                geoMap.put(entry.getKey(), locMap);
            }
        }
        map.put("geolocationData", geoMap);
        map.put("enrolledTimestamps", enrolledTimestamps);
        map.put("checkInStatus", checkInStatus);
        
        return map;
    }
    
    @Override
    public String toString() {
        return "InEventList{" +
                "eventId='" + eventId + '\'' +
                ", totalParticipants=" + getEntrantCount() +
                ", checkedIn=" + getCheckedInCount() +
                '}';
    }
    
    // Parcelable implementation
    
    protected InEventList(Parcel in) {
        eventId = in.readString();

        // Read entrantIds list (avoiding deprecated readList)
        int entrantIdsSize = in.readInt();
        entrantIds = new ArrayList<>(entrantIdsSize);
        for (int i = 0; i < entrantIdsSize; i++) {
            entrantIds.add(in.readString());
        }

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
        enrolledTimestamps = new HashMap<>();
        for (int i = 0; i < timeSize; i++) {
            String key = in.readString();
            Long value = in.readLong();
            enrolledTimestamps.put(key, value);
        }
        
        // Read check-in status
        int statusSize = in.readInt();
        checkInStatus = new HashMap<>();
        for (int i = 0; i < statusSize; i++) {
            String key = in.readString();
            Boolean value = in.readByte() != 0;
            checkInStatus.put(key, value);
        }
    }
    
    public static final Creator<InEventList> CREATOR = new Creator<InEventList>() {
        @Override
        public InEventList createFromParcel(Parcel in) {
            return new InEventList(in);
        }
        
        @Override
        public InEventList[] newArray(int size) {
            return new InEventList[size];
        }
    };
    
    @Override
    public int describeContents() {
        return 0;
    }
    
    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(eventId);

        // Write entrantIds list (avoiding deprecated writeList)
        dest.writeInt(entrantIds.size());
        for (String entrantId : entrantIds) {
            dest.writeString(entrantId);
        }

        // Write geolocation data
        dest.writeInt(geolocationData.size());
        for (Map.Entry<String, Geolocation> entry : geolocationData.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeParcelable(entry.getValue(), flags);
        }
        
        // Write timestamps
        dest.writeInt(enrolledTimestamps.size());
        for (Map.Entry<String, Long> entry : enrolledTimestamps.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeLong(entry.getValue());
        }
        
        // Write check-in status
        dest.writeInt(checkInStatus.size());
        for (Map.Entry<String, Boolean> entry : checkInStatus.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeByte((byte) (entry.getValue() ? 1 : 0));
        }
    }
}

