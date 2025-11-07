package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ResponsePendingList - Tracks entrants selected in lottery awaiting response
 *
 * Represents users who have been selected in the lottery and are waiting to
 * accept or decline their invitation to participate in the event.
 *
 * Lifecycle: WaitingList → Lottery Selection → ResponsePendingList → InEventList (accepted) or back to WaitingList (declined)
 *
 * Firestore structure:
 * response_pending_lists/{eventId}
 *   ├─ eventId: "event123"
 *   ├─ entrantIds: ["user1", "user2", ...]
 *   ├─ geolocationData: { ... }
 *   ├─ selectedTimestamps: {
 *   │    "user1": 1234567890,
 *   │    "user2": 1234567891
 *   │  }
 *   └─ responseDeadline: 1234567890
 */
public class ResponsePendingList implements Parcelable {

    private String eventId;
    private List<String> entrantIds;
    private Map<String, Geolocation> geolocationData; // entrantId -> Geolocation
    private Map<String, Long> selectedTimestamps; // entrantId -> selection timestamp
    private long responseDeadline; // Deadline for responses (Unix timestamp)

    /**
     * Default constructor required for Firebase deserialization
     */
    public ResponsePendingList() {
        this.entrantIds = new ArrayList<>();
        this.geolocationData = new HashMap<>();
        this.selectedTimestamps = new HashMap<>();
    }

    /**
     * Constructor with event ID
     *
     * @param eventId Event this response pending list belongs to
     */
    public ResponsePendingList(String eventId) {
        this.eventId = eventId;
        this.entrantIds = new ArrayList<>();
        this.geolocationData = new HashMap<>();
        this.selectedTimestamps = new HashMap<>();
    }

    /**
     * Constructor with event ID and deadline
     *
     * @param eventId Event ID
     * @param responseDeadline Response deadline timestamp
     */
    public ResponsePendingList(String eventId, long responseDeadline) {
        this.eventId = eventId;
        this.responseDeadline = responseDeadline;
        this.entrantIds = new ArrayList<>();
        this.geolocationData = new HashMap<>();
        this.selectedTimestamps = new HashMap<>();
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

    public Map<String, Long> getSelectedTimestamps() {
        return selectedTimestamps;
    }

    public void setSelectedTimestamps(Map<String, Long> selectedTimestamps) {
        this.selectedTimestamps = selectedTimestamps;
    }

    public long getResponseDeadline() {
        return responseDeadline;
    }

    public void setResponseDeadline(long responseDeadline) {
        this.responseDeadline = responseDeadline;
    }

    // Collection Management Methods

    /**
     * Add entrant to response pending list
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
        selectedTimestamps.put(entrantId, System.currentTimeMillis());

        if (location != null) {
            geolocationData.put(entrantId, location);
        }

        return true;
    }

    /**
     * Remove entrant from response pending list
     * Used when entrant accepts/declines invitation
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
        selectedTimestamps.remove(entrantId);

        return removed;
    }

    /**
     * Check if entrant is in response pending list
     *
     * @param entrantId User ID to check
     * @return true if entrant is in list
     */
    public boolean containsEntrant(String entrantId) {
        return entrantIds != null && entrantIds.contains(entrantId);
    }

    /**
     * Get number of entrants awaiting response
     *
     * @return Count of entrants
     */
    public int getEntrantCount() {
        return entrantIds != null ? entrantIds.size() : 0;
    }

    /**
     * Get available spots for more selections
     *
     * @param capacity Event capacity
     * @return Number of spots that can still be filled
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
     * Get selection timestamp for specific entrant
     *
     * @param entrantId User ID
     * @return Timestamp or null if not found
     */
    public Long getEntrantSelectionTime(String entrantId) {
        return selectedTimestamps != null ? selectedTimestamps.get(entrantId) : null;
    }

    /**
     * Check if response deadline has passed
     *
     * @return true if current time is past deadline
     */
    public boolean isDeadlinePassed() {
        return System.currentTimeMillis() > responseDeadline;
    }

    /**
     * Get time remaining until deadline
     *
     * @return Milliseconds until deadline, 0 if passed
     */
    public long getTimeUntilDeadline() {
        long remaining = responseDeadline - System.currentTimeMillis();
        return Math.max(0, remaining);
    }

    /**
     * Clear all entrants from list
     */
    public void clear() {
        entrantIds.clear();
        geolocationData.clear();
        selectedTimestamps.clear();
    }

    /**
     * Convert ResponsePendingList to Map for Firestore
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
        map.put("selectedTimestamps", selectedTimestamps);
        map.put("responseDeadline", responseDeadline);

        return map;
    }

    @Override
    public String toString() {
        return "ResponsePendingList{" +
                "eventId='" + eventId + '\'' +
                ", entrantCount=" + getEntrantCount() +
                ", deadlinePassed=" + isDeadlinePassed() +
                '}';
    }

    // Parcelable implementation

    protected ResponsePendingList(Parcel in) {
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
        selectedTimestamps = new HashMap<>();
        for (int i = 0; i < timeSize; i++) {
            String key = in.readString();
            Long value = in.readLong();
            selectedTimestamps.put(key, value);
        }

        responseDeadline = in.readLong();
    }

    public static final Creator<ResponsePendingList> CREATOR = new Creator<ResponsePendingList>() {
        @Override
        public ResponsePendingList createFromParcel(Parcel in) {
            return new ResponsePendingList(in);
        }

        @Override
        public ResponsePendingList[] newArray(int size) {
            return new ResponsePendingList[size];
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
        dest.writeInt(selectedTimestamps.size());
        for (Map.Entry<String, Long> entry : selectedTimestamps.entrySet()) {
            dest.writeString(entry.getKey());
            dest.writeLong(entry.getValue());
        }

        dest.writeLong(responseDeadline);
    }
}

