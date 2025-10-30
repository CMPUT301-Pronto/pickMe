package com.example.pickme.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * PermissionUtil - Utility class for handling runtime permissions
 *
 * Android 6.0+ (API 23+) requires runtime permission requests for dangerous permissions.
 * This utility simplifies permission checking and requesting.
 *
 * Required permissions for this app:
 * - CAMERA: For QR code scanning
 * - ACCESS_FINE_LOCATION / ACCESS_COARSE_LOCATION: For geolocation features
 * - POST_NOTIFICATIONS: For push notifications (Android 13+)
 */
public class PermissionUtil {

    // Permission request codes - used to identify which permission was requested
    public static final int REQUEST_CAMERA = 100;
    public static final int REQUEST_LOCATION = 101;
    public static final int REQUEST_NOTIFICATION = 102;

    /**
     * Check if camera permission is granted
     *
     * @param context Application or Activity context
     * @return true if permission granted, false otherwise
     */
    public static boolean hasCameraPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request camera permission
     *
     * @param activity Activity to show permission dialog
     */
    public static void requestCameraPermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{Manifest.permission.CAMERA},
                REQUEST_CAMERA
        );
    }

    /**
     * Check if location permissions are granted
     * Checks both FINE and COARSE location
     *
     * @param context Application or Activity context
     * @return true if either permission granted, false otherwise
     */
    public static boolean hasLocationPermission(Context context) {
        boolean hasFineLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean hasCoarseLocation = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        return hasFineLocation || hasCoarseLocation;
    }

    /**
     * Request location permissions
     * Requests both FINE and COARSE location for maximum compatibility
     *
     * @param activity Activity to show permission dialog
     */
    public static void requestLocationPermission(Activity activity) {
        ActivityCompat.requestPermissions(
                activity,
                new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                },
                REQUEST_LOCATION
        );
    }

    /**
     * Check if notification permission is granted
     * Only applies to Android 13+ (API 33+)
     *
     * @param context Application or Activity context
     * @return true if permission granted or not required (Android 12-), false otherwise
     */
    public static boolean hasNotificationPermission(Context context) {
        // Notification permission only required on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED;
        }
        // On older Android versions, notification permission is granted by default
        return true;
    }

    /**
     * Request notification permission
     * Only requests on Android 13+ (API 33+)
     *
     * @param activity Activity to show permission dialog
     */
    public static void requestNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQUEST_NOTIFICATION
            );
        }
    }

    /**
     * Check if user permanently denied a permission
     * (User selected "Don't ask again" checkbox)
     *
     * @param activity Activity context
     * @param permission Permission to check (e.g., Manifest.permission.CAMERA)
     * @return true if permanently denied, false otherwise
     */
    public static boolean isPermissionPermanentlyDenied(Activity activity, String permission) {
        return !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                && ContextCompat.checkSelfPermission(activity, permission)
                != PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Check if all required app permissions are granted
     *
     * @param context Application or Activity context
     * @return true if all permissions granted, false otherwise
     */
    public static boolean hasAllRequiredPermissions(Context context) {
        return hasCameraPermission(context)
                && hasLocationPermission(context)
                && hasNotificationPermission(context);
    }
}

