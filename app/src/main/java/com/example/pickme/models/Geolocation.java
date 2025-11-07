package com.example.pickme.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

/**
 * Geolocation - Represents geographic coordinates
 *
 * Stores latitude and longitude coordinates with timestamp.
 * Used for tracking entrant locations when joining events (if required).
 *
 * Firebase compatible with empty constructor and getters/setters.
 */
public class Geolocation implements Parcelable {

    private double latitude;
    private double longitude;
    private long timestamp; // Unix timestamp in milliseconds

    /**
     * Default constructor required for Firebase deserialization
     */
    public Geolocation() {
    }

    /**
     * Constructor with coordinates
     * Timestamp is set to current time
     *
     * @param latitude Latitude coordinate (-90 to 90)
     * @param longitude Longitude coordinate (-180 to 180)
     */
    public Geolocation(double latitude, double longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Constructor with all fields
     *
     * @param latitude Latitude coordinate (-90 to 90)
     * @param longitude Longitude coordinate (-180 to 180)
     * @param timestamp Unix timestamp in milliseconds
     */
    public Geolocation(double latitude, double longitude, long timestamp) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
    }

    // Getters and Setters

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Check if coordinates are valid
     *
     * @return true if latitude and longitude are within valid ranges
     */
    public boolean isValid() {
        return latitude >= -90 && latitude <= 90
                && longitude >= -180 && longitude <= 180;
    }

    /**
     * Calculate distance to another location using Haversine formula
     *
     * @param other Another Geolocation
     * @return Distance in kilometers
     */
    public double distanceTo(Geolocation other) {
        if (other == null || !isValid() || !other.isValid()) {
            return -1;
        }

        final int EARTH_RADIUS_KM = 6371;

        double lat1Rad = Math.toRadians(latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - latitude);
        double deltaLon = Math.toRadians(other.longitude - longitude);

        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Convert Geolocation to Map for Firestore
     *
     * @return Map representation of Geolocation
     */
    public java.util.Map<String, Object> toMap() {
        java.util.Map<String, Object> map = new java.util.HashMap<>();
        map.put("latitude", latitude);
        map.put("longitude", longitude);
        map.put("timestamp", timestamp);
        return map;
    }

    @Override
    public String toString() {
        return "Geolocation{" +
                "latitude=" + latitude +
                ", longitude=" + longitude +
                ", timestamp=" + timestamp +
                '}';
    }

    // Parcelable implementation

    protected Geolocation(Parcel in) {
        latitude = in.readDouble();
        longitude = in.readDouble();
        timestamp = in.readLong();
    }

    public static final Creator<Geolocation> CREATOR = new Creator<Geolocation>() {
        @Override
        public Geolocation createFromParcel(Parcel in) {
            return new Geolocation(in);
        }

        @Override
        public Geolocation[] newArray(int size) {
            return new Geolocation[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeDouble(latitude);
        dest.writeDouble(longitude);
        dest.writeLong(timestamp);
    }
}

