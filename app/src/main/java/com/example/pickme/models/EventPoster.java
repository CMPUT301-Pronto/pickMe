package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * EventPoster - Event promotional image metadata
 *
 * Stores information about event poster images uploaded to Firebase Storage.
 * References the event and tracks who uploaded it and when.
 *
 * Firestore structure:
 * event_posters/{posterId}
 *   ├─ posterId: "poster123"
 *   ├─ eventId: "event456"
 *   ├─ imageUrl: "https://storage.googleapis.com/..."
 *   ├─ uploadTimestamp: 1234567890
 *   └─ uploadedBy: "user789"
 */
public class EventPoster implements Parcelable {

    private String posterId;
    private String eventId;
    private String imageUrl; // Firebase Storage download URL
    private long uploadTimestamp; // Unix timestamp in milliseconds
    private String uploadedBy; // User ID of uploader

    /**
     * Default constructor required for Firebase deserialization
     */
    public EventPoster() {
    }

    /**
     * Constructor with required fields
     * Upload timestamp is set to current time
     *
     * @param posterId Unique poster identifier
     * @param eventId Event this poster belongs to
     * @param imageUrl Firebase Storage URL
     * @param uploadedBy User ID of uploader
     */
    public EventPoster(String posterId, String eventId, String imageUrl, String uploadedBy) {
        this.posterId = posterId;
        this.eventId = eventId;
        this.imageUrl = imageUrl;
        this.uploadedBy = uploadedBy;
        this.uploadTimestamp = System.currentTimeMillis();
    }

    /**
     * Constructor with all fields
     *
     * @param posterId Unique poster identifier
     * @param eventId Event this poster belongs to
     * @param imageUrl Firebase Storage URL
     * @param uploadTimestamp Upload time
     * @param uploadedBy User ID of uploader
     */
    public EventPoster(String posterId, String eventId, String imageUrl, long uploadTimestamp, String uploadedBy) {
        this.posterId = posterId;
        this.eventId = eventId;
        this.imageUrl = imageUrl;
        this.uploadTimestamp = uploadTimestamp;
        this.uploadedBy = uploadedBy;
    }

    // Getters and Setters

    public String getPosterId() {
        return posterId;
    }

    public void setPosterId(String posterId) {
        this.posterId = posterId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public long getUploadTimestamp() {
        return uploadTimestamp;
    }

    public void setUploadTimestamp(long uploadTimestamp) {
        this.uploadTimestamp = uploadTimestamp;
    }

    public String getUploadedBy() {
        return uploadedBy;
    }

    public void setUploadedBy(String uploadedBy) {
        this.uploadedBy = uploadedBy;
    }

    // Helper Methods

    /**
     * Check if poster has valid data
     *
     * @return true if all required fields are set
     */
    public boolean isValid() {
        return posterId != null && !posterId.isEmpty()
                && eventId != null && !eventId.isEmpty()
                && imageUrl != null && !imageUrl.isEmpty();
    }

    /**
     * Convert EventPoster to Map for Firestore
     *
     * @return Map representation of EventPoster
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("posterId", posterId);
        map.put("eventId", eventId);
        map.put("imageUrl", imageUrl);
        map.put("uploadTimestamp", uploadTimestamp);
        map.put("uploadedBy", uploadedBy);
        return map;
    }

    @Override
    public String toString() {
        return "EventPoster{" +
                "posterId='" + posterId + '\'' +
                ", eventId='" + eventId + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", uploadTimestamp=" + uploadTimestamp +
                ", uploadedBy='" + uploadedBy + '\'' +
                '}';
    }

    // Parcelable implementation

    protected EventPoster(Parcel in) {
        posterId = in.readString();
        eventId = in.readString();
        imageUrl = in.readString();
        uploadTimestamp = in.readLong();
        uploadedBy = in.readString();
    }

    public static final Creator<EventPoster> CREATOR = new Creator<EventPoster>() {
        @Override
        public EventPoster createFromParcel(Parcel in) {
            return new EventPoster(in);
        }

        @Override
        public EventPoster[] newArray(int size) {
            return new EventPoster[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(posterId);
        dest.writeString(eventId);
        dest.writeString(imageUrl);
        dest.writeLong(uploadTimestamp);
        dest.writeString(uploadedBy);
    }
}

