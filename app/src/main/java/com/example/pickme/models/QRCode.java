package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * QRCode - QR code data for event check-in and registration
 *
 * Stores QR code information for events.
 * The encoded data typically contains the event ID and can be scanned
 * by entrants to join the event waiting list.
 *
 * Firestore structure:
 * qr_codes/{qrCodeId}
 *   ├─ qrCodeId: "qr123"
 *   ├─ eventId: "event456"
 *   ├─ encodedData: "EVENT:event456:HASH:abc123"
 *   └─ generatedTimestamp: 1234567890
 */
public class QRCode implements Parcelable {

    private String qrCodeId;
    private String eventId;
    private String encodedData; // Data encoded in the QR code
    private long generatedTimestamp; // Unix timestamp in milliseconds

    /**
     * Default constructor required for Firebase deserialization
     */
    public QRCode() {
    }

    /**
     * Constructor with required fields
     * Generated timestamp is set to current time
     *
     * @param qrCodeId Unique QR code identifier
     * @param eventId Event this QR code is for
     * @param encodedData Data encoded in QR code
     */
    public QRCode(String qrCodeId, String eventId, String encodedData) {
        this.qrCodeId = qrCodeId;
        this.eventId = eventId;
        this.encodedData = encodedData;
        this.generatedTimestamp = System.currentTimeMillis();
    }

    /**
     * Constructor with all fields
     *
     * @param qrCodeId Unique QR code identifier
     * @param eventId Event this QR code is for
     * @param encodedData Data encoded in QR code
     * @param generatedTimestamp When QR code was generated
     */
    public QRCode(String qrCodeId, String eventId, String encodedData, long generatedTimestamp) {
        this.qrCodeId = qrCodeId;
        this.eventId = eventId;
        this.encodedData = encodedData;
        this.generatedTimestamp = generatedTimestamp;
    }

    // Getters and Setters

    public String getQrCodeId() {
        return qrCodeId;
    }

    public void setQrCodeId(String qrCodeId) {
        this.qrCodeId = qrCodeId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getEncodedData() {
        return encodedData;
    }

    public void setEncodedData(String encodedData) {
        this.encodedData = encodedData;
    }

    public long getGeneratedTimestamp() {
        return generatedTimestamp;
    }

    public void setGeneratedTimestamp(long generatedTimestamp) {
        this.generatedTimestamp = generatedTimestamp;
    }

    // Helper Methods

    /**
     * Check if QR code has valid data
     *
     * @return true if all required fields are set
     */
    public boolean isValid() {
        return qrCodeId != null && !qrCodeId.isEmpty()
                && eventId != null && !eventId.isEmpty()
                && encodedData != null && !encodedData.isEmpty();
    }

    /**
     * Generate encoded data string for QR code
     * Format: EVENT:{eventId}:TIMESTAMP:{timestamp}:HASH:{hash}
     *
     * @param eventId Event identifier
     * @param hash Security hash
     * @return Encoded data string
     */
    public static String generateEncodedData(String eventId, String hash) {
        long timestamp = System.currentTimeMillis();
        return String.format("EVENT:%s:TIMESTAMP:%d:HASH:%s", eventId, timestamp, hash);
    }

    /**
     * Generate simple encoded data with just event ID
     *
     * @param eventId Event identifier
     * @return Simple encoded data string
     */
    public static String generateSimpleEncodedData(String eventId) {
        return "EVENT:" + eventId;
    }

    /**
     * Extract event ID from encoded data
     * Assumes format: EVENT:{eventId}:...
     *
     * @param encodedData Encoded QR code data
     * @return Event ID or null if invalid format
     */
    public static String extractEventId(String encodedData) {
        if (encodedData == null || !encodedData.startsWith("EVENT:")) {
            return null;
        }

        String[] parts = encodedData.split(":");
        if (parts.length >= 2) {
            return parts[1];
        }
        return null;
    }

    /**
     * Convert QRCode to Map for Firestore
     *
     * @return Map representation of QRCode
     */
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("qrCodeId", qrCodeId);
        map.put("eventId", eventId);
        map.put("encodedData", encodedData);
        map.put("generatedTimestamp", generatedTimestamp);
        return map;
    }

    @Override
    public String toString() {
        return "QRCode{" +
                "qrCodeId='" + qrCodeId + '\'' +
                ", eventId='" + eventId + '\'' +
                ", encodedData='" + encodedData + '\'' +
                ", generatedTimestamp=" + generatedTimestamp +
                '}';
    }

    // Parcelable implementation

    protected QRCode(Parcel in) {
        qrCodeId = in.readString();
        eventId = in.readString();
        encodedData = in.readString();
        generatedTimestamp = in.readLong();
    }

    public static final Creator<QRCode> CREATOR = new Creator<QRCode>() {
        @Override
        public QRCode createFromParcel(Parcel in) {
            return new QRCode(in);
        }

        @Override
        public QRCode[] newArray(int size) {
            return new QRCode[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(qrCodeId);
        dest.writeString(eventId);
        dest.writeString(encodedData);
        dest.writeLong(generatedTimestamp);
    }
}

