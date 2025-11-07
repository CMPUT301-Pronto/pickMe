package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * EventHistoryItem - Tracks a user's participation in an event
 *
 * Used in Profile.java to maintain user's event history.
 * Records the status of user's involvement with each event.
 *
 * Status values:
 * - "waiting" - User joined waiting list
 * - "selected" - User selected in lottery
 * - "enrolled" - User confirmed participation
 * - "cancelled" - User cancelled their participation
 * - "not_selected" - User not selected in lottery
 */
public class EventHistoryItem implements Parcelable {

    private String eventId;
    private String eventName;
    private long joinedTimestamp;
    private String status; // waiting, selected, enrolled, cancelled, not_selected

    /**
     * Default constructor required for Firebase deserialization
     */
    public EventHistoryItem() {
    }

    /**
     * Constructor with all fields
     *
     * @param eventId Event identifier
     * @param eventName Name of the event
     * @param joinedTimestamp When user joined/interacted with event
     * @param status Current status of participation
     */
    public EventHistoryItem(String eventId, String eventName, long joinedTimestamp, String status) {
        this.eventId = eventId;
        this.eventName = eventName;
        this.joinedTimestamp = joinedTimestamp;
        this.status = status;
    }

    // Getters and Setters

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEventName() {
        return eventName;
    }

    public void setEventName(String eventName) {
        this.eventName = eventName;
    }

    public long getJoinedTimestamp() {
        return joinedTimestamp;
    }

    public void setJoinedTimestamp(long joinedTimestamp) {
        this.joinedTimestamp = joinedTimestamp;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "EventHistoryItem{" +
                "eventId='" + eventId + '\'' +
                ", eventName='" + eventName + '\'' +
                ", joinedTimestamp=" + joinedTimestamp +
                ", status='" + status + '\'' +
                '}';
    }

    // Parcelable implementation

    protected EventHistoryItem(Parcel in) {
        eventId = in.readString();
        eventName = in.readString();
        joinedTimestamp = in.readLong();
        status = in.readString();
    }

    public static final Creator<EventHistoryItem> CREATOR = new Creator<EventHistoryItem>() {
        @Override
        public EventHistoryItem createFromParcel(Parcel in) {
            return new EventHistoryItem(in);
        }

        @Override
        public EventHistoryItem[] newArray(int size) {
            return new EventHistoryItem[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(eventId);
        dest.writeString(eventName);
        dest.writeLong(joinedTimestamp);
        dest.writeString(status);
    }
}

