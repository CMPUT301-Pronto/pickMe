package com.example.pickme.services;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.pickme.models.Geolocation;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;

/**
 * GeolocationService - Handles device location capture
 *
 * OPTIONAL FEATURE: When event requires geolocation:
 * - Captures device location when joining waiting list
 * - Stores lat/long with entrant's entry
 * - Organizer can view locations on map (US 02.02.02)
 *
 * Uses Google Play Services Location API with:
 * - FusedLocationProviderClient for efficient location access
 * - Last known location for quick retrieval
 * - Timeout after 5 seconds if unavailable
 * - Graceful handling of permission denials
 *
 * Related User Stories: US 02.02.02, US 02.02.03
 */
public class GeolocationService {

    private static final String TAG = "GeolocationService";
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;
    private static final long LOCATION_TIMEOUT_MS = 5000; // 5 seconds

    private FusedLocationProviderClient fusedLocationClient;

    public GeolocationService(Context context) {
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
    }

    /**
     * Request location permission from user
     *
     * @param activity Activity to show permission dialog
     * @param listener Callback for permission result
     */
    public void requestLocationPermission(@NonNull Activity activity,
                                         @NonNull OnPermissionResultListener listener) {
        if (hasLocationPermission(activity)) {
            Log.d(TAG, "Location permission already granted");
            listener.onPermissionResult(true);
            return;
        }

        Log.d(TAG, "Requesting location permission");

        // Request both fine and coarse location
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                LOCATION_PERMISSION_REQUEST_CODE
        );

        // Note: Result will be received in Activity.onRequestPermissionsResult()
        // Listener should be stored in Activity and called from there
    }

    /**
     * Get current device location
     *
     * Process:
     * 1. Check permission
     * 2. Request last known location (fast)
     * 3. If null, request current location update
     * 4. Timeout after 5 seconds
     * 5. Return Geolocation object or null
     *
     * @param context Android context
     * @param listener Callback with Geolocation
     */
    public void getCurrentLocation(@NonNull Context context,
                                   @NonNull OnLocationReceivedListener listener) {
        if (!hasLocationPermission(context)) {
            Log.w(TAG, "Location permission not granted");
            listener.onLocationReceived(null);
            return;
        }

        Log.d(TAG, "Getting current location");

        // Try to get last known location first (fastest)
        try {
            fusedLocationClient.getLastLocation()
                    .addOnSuccessListener(location -> {
                        if (location != null) {
                            Log.d(TAG, "Last known location retrieved");
                            Geolocation geolocation = new Geolocation(
                                    location.getLatitude(),
                                    location.getLongitude()
                            );
                            listener.onLocationReceived(geolocation);
                        } else {
                            // Last location not available, request current location
                            Log.d(TAG, "Last location null, requesting current location");
                            requestCurrentLocationUpdate(listener);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to get last location", e);
                        // Try current location as fallback
                        requestCurrentLocationUpdate(listener);
                    });
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception getting location", e);
            listener.onLocationReceived(null);
        }
    }

    /**
     * Request current location update with timeout
     */
    private void requestCurrentLocationUpdate(@NonNull OnLocationReceivedListener listener) {
        try {
            // Create cancellation token for timeout
            CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();

            // Set timeout
            new android.os.Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.w(TAG, "Location request timed out after " + LOCATION_TIMEOUT_MS + "ms");
                cancellationTokenSource.cancel();
                listener.onLocationReceived(null);
            }, LOCATION_TIMEOUT_MS);

            // Request current location
            fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.getToken()
            ).addOnSuccessListener(location -> {
                if (location != null) {
                    Log.d(TAG, "Current location retrieved");
                    Geolocation geolocation = new Geolocation(
                            location.getLatitude(),
                            location.getLongitude()
                    );
                    listener.onLocationReceived(geolocation);
                } else {
                    Log.w(TAG, "Current location is null");
                    listener.onLocationReceived(null);
                }
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Failed to get current location", e);
                listener.onLocationReceived(null);
            });

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception requesting current location", e);
            listener.onLocationReceived(null);
        }
    }

    /**
     * Check if location permission is granted
     *
     * @param context Android context
     * @return true if permission granted
     */
    public boolean hasLocationPermission(@NonNull Context context) {
        return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if location permission was permanently denied
     *
     * @param activity Activity to check
     * @return true if permanently denied
     */
    public boolean isLocationPermissionPermanentlyDenied(@NonNull Activity activity) {
        return !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) && !hasLocationPermission(activity);
    }

    /**
     * Handle permission result from Activity.onRequestPermissionsResult()
     * Call this from your Activity's onRequestPermissionsResult method
     *
     * @param requestCode Request code from callback
     * @param permissions Permissions array from callback
     * @param grantResults Grant results array from callback
     * @param listener Callback with result
     */
    public void handlePermissionResult(int requestCode,
                                      @NonNull String[] permissions,
                                      @NonNull int[] grantResults,
                                      @NonNull OnPermissionResultListener listener) {
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            Log.d(TAG, "Location permission result: " + (granted ? "granted" : "denied"));
            listener.onPermissionResult(granted);
        }
    }

    /**
     * Create location request for continuous updates (if needed in future)
     *
     * @return LocationRequest configured for high accuracy
     */
    public LocationRequest createLocationRequest() {
        return new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
                .setMinUpdateIntervalMillis(5000)
                .build();
    }

    /**
     * Callback for permission request result
     */
    public interface OnPermissionResultListener {
        void onPermissionResult(boolean granted);
    }

    /**
     * Callback for location retrieval
     */
    public interface OnLocationReceivedListener {
        void onLocationReceived(Geolocation location);
    }
}

