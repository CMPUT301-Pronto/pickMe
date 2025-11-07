package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NotificationLog - Records all notifications sent through the system
 *
 * Used for admin review and audit trail (US 03.08.01).
 * Tracks who sent what message to whom and when.
 *
 * Firestore structure:
 * notification_logs/{notificationId}
 *   ├─ notificationId
 *   ├─ timestamp
 *   ├─ senderId (organizer or system)
 *   ├─ recipientIds: ["user1", "user2", ...]
 *   ├─ messageContent
 *   ├─ notificationType (lottery_win, lottery_loss, organizer_message, etc.)
 *   └─ eventId
 */
public class NotificationLog implements Parcelable {

    private String notificationId;
    private long timestamp;
    private String senderId;
    private List<String> recipientIds;
    private String messageContent;
    private String notificationType; // lottery_win, lottery_loss, organizer_message, replacement_draw
    private String eventId;

    /**
     * Default constructor for Firebase
     */
    public NotificationLog() {
        this.recipientIds = new ArrayList<>();
    }

    /**
     * Constructor with all fields
     */
    public NotificationLog(String notificationId, long timestamp, String senderId,
                          List<String> recipientIds, String messageContent,
                          String notificationType, String eventId) {
        this.notificationId = notificationId;
        this.timestamp = timestamp;
        this.senderId = senderId;
        this.recipientIds = recipientIds != null ? recipientIds : new ArrayList<>();
        this.messageContent = messageContent;
        this.notificationType = notificationType;
        this.eventId = eventId;
    }

    // Getters and Setters

    public String getNotificationId() {
        return notificationId;
    }

    public void setNotificationId(String notificationId) {
        this.notificationId = notificationId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public List<String> getRecipientIds() {
        return recipientIds;
    }

    public void setRecipientIds(List<String> recipientIds) {
        this.recipientIds = recipientIds;
    }

    public String getMessageContent() {
        return messageContent;
    }

    public void setMessageContent(String messageContent) {
        this.messageContent = messageContent;
    }

    public String getNotificationType() {
        return notificationType;
    }

    public void setNotificationType(String notificationType) {
        this.notificationType = notificationType;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Convert to Map for Firestore
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("notificationId", notificationId);
        map.put("timestamp", timestamp);
        map.put("senderId", senderId);
        map.put("recipientIds", recipientIds);
        map.put("messageContent", messageContent);
        map.put("notificationType", notificationType);
        map.put("eventId", eventId);
        return map;
    }

    @Override
    public String toString() {
        return "NotificationLog{" +
                "notificationId='" + notificationId + '\'' +
                ", timestamp=" + timestamp +
                ", notificationType='" + notificationType + '\'' +
                ", recipientCount=" + (recipientIds != null ? recipientIds.size() : 0) +
                '}';
    }

    // Parcelable implementation

    protected NotificationLog(Parcel in) {
        notificationId = in.readString();
        timestamp = in.readLong();
        senderId = in.readString();
        recipientIds = new ArrayList<>();
        in.readList(recipientIds, String.class.getClassLoader());
        messageContent = in.readString();
        notificationType = in.readString();
        eventId = in.readString();
    }

    public static final Creator<NotificationLog> CREATOR = new Creator<NotificationLog>() {
        @Override
        public NotificationLog createFromParcel(Parcel in) {
            return new NotificationLog(in);
        }

        @Override
        public NotificationLog[] newArray(int size) {
            return new NotificationLog[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(notificationId);
        dest.writeLong(timestamp);
        dest.writeString(senderId);
        dest.writeList(recipientIds);
        dest.writeString(messageContent);
        dest.writeString(notificationType);
        dest.writeString(eventId);
    }
}

