package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Event - Represents an event in the lottery system
 *
 * Core entity for the event lottery application.
 * Contains all event details, registration settings, and lottery configuration.
 *
 * Firestore structure:
 * events/{eventId}
 *   ├─ name: "Concert Night"
 *   ├─ description: "Amazing concert..."
 *   ├─ organizerId: "user123"
 *   ├─ eventDates: [timestamp1, timestamp2]
 *   ├─ location: "Edmonton Convention Centre"
 *   ├─ registrationStartDate: timestamp
 *   ├─ registrationEndDate: timestamp
 *   ├─ price: 50.0
 *   ├─ capacity: 100
 *   ├─ waitingListLimit: 200
 *   ├─ geolocationRequired: true
 *   ├─ qrCodeId: "qr123"
 *   ├─ posterImageUrl: "https://..."
 *   └─ status: "OPEN"
 */
public class Event implements Parcelable {

    private String eventId;
    private String name;
    private String description;
    private String organizerId;
    private List<Long> eventDates; // List of event date timestamps
    private String location;
    private long registrationStartDate; // Timestamp
    private long registrationEndDate; // Timestamp
    private double price;
    private int capacity; // Maximum number of participants
    private int waitingListLimit; // Maximum number on waiting list (-1 for unlimited)
    private boolean geolocationRequired;
    private String qrCodeId;
    private String posterImageUrl;
    private String status; // Stored as String for Firebase, use EventStatus enum
    private long invitationDeadlineMillis;
    private String eventType = "General";
    /**
     * Default constructor required for Firebase deserialization
     */
    public Event() {
        this.eventDates = new ArrayList<>();
        this.status = EventStatus.DRAFT.name();
    }

    /**
     * Constructor with required fields
     *
     * @param eventId Unique event identifier
     * @param name Event name
     * @param description Event description
     * @param organizerId User ID of event organizer
     */
    public Event(String eventId, String name, String description, String organizerId) {
        this.eventId = eventId;
        this.name = name;
        this.description = description;
        this.organizerId = organizerId;
        this.eventDates = new ArrayList<>();
        this.status = EventStatus.DRAFT.name();
        this.waitingListLimit = -1; // Unlimited by default
        this.price = 0.0;
    }

    // Getters and Setters
    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public void setOrganizerId(String organizerId) {
        this.organizerId = organizerId;
    }

    public List<Long> getEventDates() {
        return eventDates;
    }

    public void setEventDates(List<Long> eventDates) {
        this.eventDates = eventDates;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public long getRegistrationStartDate() {
        return registrationStartDate;
    }

    public void setRegistrationStartDate(long registrationStartDate) {
        this.registrationStartDate = registrationStartDate;
    }

    public long getRegistrationEndDate() {
        return registrationEndDate;
    }

    public void setRegistrationEndDate(long registrationEndDate) {
        this.registrationEndDate = registrationEndDate;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getWaitingListLimit() {
        return waitingListLimit;
    }

    public void setWaitingListLimit(int waitingListLimit) {
        this.waitingListLimit = waitingListLimit;
    }

    public boolean isGeolocationRequired() {
        return geolocationRequired;
    }

    public void setGeolocationRequired(boolean geolocationRequired) {
        this.geolocationRequired = geolocationRequired;
    }

    public String getQrCodeId() {
        return qrCodeId;
    }

    public void setQrCodeId(String qrCodeId) {
        this.qrCodeId = qrCodeId;
    }

    public String getPosterImageUrl() {
        return posterImageUrl;
    }

    public void setPosterImageUrl(String posterImageUrl) {
        this.posterImageUrl = posterImageUrl;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Set status using EventStatus enum (helper method)
     *
     * @param status EventStatus enum value
     */
    public void setStatusEnum(EventStatus status) {
        this.status = status.name();
    }

    /**
     * Get status as EventStatus enum
     *
     * @return EventStatus enum value
     */
    public EventStatus getStatusEnum() {
        try {
            return EventStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            return EventStatus.DRAFT;
        }
    }

    // Validation and Helper Methods

    /**
     * Check if registration is currently open
     *
     * @return true if current time is within registration period and event is OPEN
     */
    public boolean isRegistrationOpen() {
        long currentTime = System.currentTimeMillis();
        return EventStatus.OPEN.name().equals(status)
                && currentTime >= registrationStartDate
                && currentTime <= registrationEndDate;
    }

    /**
     * Check if event has reached capacity
     *
     * @param currentEnrollment Current number of enrolled participants
     * @return true if event is at capacity
     */
    public boolean hasReachedCapacity(int currentEnrollment) {
        return currentEnrollment >= capacity;
    }

    /**
     * Check if waiting list has space
     *
     * @param currentWaitingListSize Current waiting list size
     * @return true if waiting list has space
     */
    public boolean hasWaitingListSpace(int currentWaitingListSize) {
        if (waitingListLimit == -1) {
            return true; // Unlimited
        }
        return currentWaitingListSize < waitingListLimit;
    }

    /**
     * Check if event is free
     *
     * @return true if price is 0
     */
    public boolean isFree() {
        return price == 0.0;
    }

    /**
     * Check if event is in draft mode
     *
     * @return true if status is DRAFT
     */
    public boolean isDraft() {
        return EventStatus.DRAFT.name().equals(status);
    }

    /**
     * Check if event is cancelled
     *
     * @return true if status is CANCELLED
     */
    public boolean isCancelled() {
        return EventStatus.CANCELLED.name().equals(status);
    }

    /**
     * Check if event is completed
     *
     * @return true if status is COMPLETED
     */
    public boolean isCompleted() {
        return EventStatus.COMPLETED.name().equals(status);
    }

    /**
     * Get number of available spots
     *
     * @param currentEnrollment Current enrollment count
     * @return Number of available spots, 0 if at capacity
     */
    public int getAvailableSpots(int currentEnrollment) {
        return Math.max(0, capacity - currentEnrollment);
    }

    /**
     * Convert Event to Map for Firestore
     *
     * @return Map representation of Event
     */

    public long getInvitationDeadlineMillis() { return invitationDeadlineMillis; }
    public void setInvitationDeadlineMillis(long v) { this.invitationDeadlineMillis = v; }
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("eventId", eventId);
        map.put("name", name);
        map.put("description", description);
        map.put("organizerId", organizerId);
        map.put("eventDates", eventDates);
        map.put("location", location);
        map.put("registrationStartDate", registrationStartDate);
        map.put("registrationEndDate", registrationEndDate);
        map.put("price", price);
        map.put("capacity", capacity);
        map.put("waitingListLimit", waitingListLimit);
        map.put("geolocationRequired", geolocationRequired);
        map.put("qrCodeId", qrCodeId);
        map.put("posterImageUrl", posterImageUrl);
        map.put("status", status);
        map.put("eventType", eventType);
        return map;
    }

    @Override
    public String toString() {
        return "Event{" +
                "eventId='" + eventId + '\'' +
                ", name='" + name + '\'' +
                ", organizerId='" + organizerId + '\'' +
                ", location='" + location + '\'' +
                ", capacity=" + capacity +
                ", status='" + status + '\'' +
                '}';
    }

    // Parcelable implementation

    protected Event(Parcel in) {
        eventId = in.readString();
        name = in.readString();
        description = in.readString();
        organizerId = in.readString();
        eventType = in.readString();
        // Read List<Long> for event dates (avoiding deprecated readList)
        int eventDatesSize = in.readInt();
        eventDates = new ArrayList<>(eventDatesSize);
        for (int i = 0; i < eventDatesSize; i++) {
            eventDates.add(in.readLong());
        }

        location = in.readString();
        registrationStartDate = in.readLong();
        registrationEndDate = in.readLong();
        price = in.readDouble();
        capacity = in.readInt();
        waitingListLimit = in.readInt();
        geolocationRequired = in.readByte() != 0;
        qrCodeId = in.readString();
        posterImageUrl = in.readString();
        status = in.readString();
    }

    public static final Creator<Event> CREATOR = new Creator<Event>() {
        @Override
        public Event createFromParcel(Parcel in) {
            return new Event(in);
        }

        @Override
        public Event[] newArray(int size) {
            return new Event[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(eventId);
        dest.writeString(name);
        dest.writeString(description);
        dest.writeString(organizerId);

        // Write List<Long> for event dates (avoiding deprecated writeList)
        dest.writeInt(eventDates.size());
        for (Long date : eventDates) {
            dest.writeLong(date);
        }

        dest.writeString(location);
        dest.writeLong(registrationStartDate);
        dest.writeLong(registrationEndDate);
        dest.writeDouble(price);
        dest.writeInt(capacity);
        dest.writeInt(waitingListLimit);
        dest.writeByte((byte) (geolocationRequired ? 1 : 0));
        dest.writeString(qrCodeId);
        dest.writeString(posterImageUrl);
        dest.writeString(status);
        dest.writeString(eventType);
    }
}

