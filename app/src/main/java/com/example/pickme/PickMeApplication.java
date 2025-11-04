package com.example.pickme;

import android.app.Application;
import android.util.Log;

import com.example.pickme.services.FirebaseManager;
import com.google.firebase.FirebaseApp;

/**
 * PickMeApplication - Custom Application class
 *
 * This class extends Application and is instantiated before any other component
 * (Activity, Service, etc.) in the app. Perfect for one-time initialization tasks.
 *
 * Responsibilities:
 * - Initialize Firebase when the app starts
 * - Setup FirebaseManager singleton
 * - Perform anonymous authentication for device-based user tracking
 * - Configure app-wide settings
 *
 * Configuration:
 * - Must be declared in AndroidManifest.xml with android:name=".PickMeApplication"
 * - Runs once per app process, not per activity
 *
 * Lifecycle:
 * onCreate() → called when app process starts (before any Activity)
 */
public class PickMeApplication extends Application {

    private static final String TAG = "PickMeApplication";

    /**
     * Called when the application is starting, before any activity, service,
     * or receiver objects (excluding content providers) have been created.
     *
     * This is the perfect place to initialize Firebase and other app-wide services.
     */
    @Override
    public void onCreate() {
        super.onCreate();

        Log.d(TAG, "Application starting - initializing Firebase");

        // Initialize Firebase
        // FirebaseApp.initializeApp() is called automatically by the Firebase SDK
        // when google-services.json is present, but we can call it explicitly for clarity
        initializeFirebase();

        // Initialize FirebaseManager singleton
        // This ensures all Firebase services are ready before any Activity uses them
        initializeFirebaseManager();

        // Setup anonymous authentication for device-based user tracking
        setupAuthentication();

        Log.d(TAG, "Application initialization complete");
    }

    /**
     * Initialize Firebase
     * The Firebase SDK auto-initializes using google-services.json configuration,
     * but explicit initialization provides better control and error handling
     */
    private void initializeFirebase() {
        try {
            // FirebaseApp is automatically initialized, but we can verify it here
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this);
                Log.d(TAG, "Firebase explicitly initialized");
            } else {
                Log.d(TAG, "Firebase already initialized");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);
        }
    }

    /**
     * Initialize FirebaseManager singleton
     * This creates the FirebaseManager instance and initializes all Firebase services
     * (Firestore, Storage, Auth, Cloud Messaging)
     */
    private void initializeFirebaseManager() {
        try {
            // Access singleton instance - this triggers initialization
            FirebaseManager.getInstance();
            Log.d(TAG, "FirebaseManager initialized");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing FirebaseManager", e);
        }
    }

    /**
     * Setup device-based authentication
     * Signs in the user anonymously if not already authenticated
     * This provides a unique user ID for each device without requiring user accounts
     */
    private void setupAuthentication() {
        // Check if user is already authenticated
        if (!FirebaseManager.isUserAuthenticated()) {
            Log.d(TAG, "User not authenticated - signing in anonymously");

            // Sign in anonymously
            FirebaseManager.signInAnonymously(new FirebaseManager.AuthCallback() {
                @Override
                public void onSuccess(String userId) {
                    Log.d(TAG, "Anonymous authentication successful. User ID: " + userId);
                    // TODO: Store user ID in SharedPreferences or create user profile in Firestore
                }

                @Override
                public void onFailure(Exception e) {
                    Log.e(TAG, "Anonymous authentication failed", e);
                    // TODO: Handle authentication failure (show error, retry, etc.)
                }
            });
        } else {
            String userId = FirebaseManager.getCurrentUserId();
            Log.d(TAG, "User already authenticated. User ID: " + userId);
        }
    }

    /**
     * Called when the application is stopping.
     * This is called when the device is running low on memory and may kill the app process.
     *
     * Note: This is NOT guaranteed to be called - don't rely on it for critical cleanup.
     * Use onPause() or onStop() in Activities for important cleanup tasks.
     */
    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "Application terminating");
    }

    /**
     * Called when the overall system is running low on memory.
     * This is a good opportunity to release caches or other unnecessary resources.
     *
     * @paramlevel The context of the trim (TRIM_MEMORY_* constants)
     */
    @Override
    public void onLowMemory() {
        super.onLowMemory();
        Log.w(TAG, "Low memory warning - consider releasing caches");
        // TODO: Clear image caches, release unnecessary resources
    }

    /**
     * Called when the operating system has determined that it is a good time
     * for a process to trim unneeded memory from its process.
     *
     * @param level The context of the trim (TRIM_MEMORY_* constants)
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);

        // Different trim levels indicate different severity of memory pressure
        switch (level) {
            case TRIM_MEMORY_RUNNING_LOW:
                Log.w(TAG, "Memory trim: Running low");
                break;
            case TRIM_MEMORY_RUNNING_CRITICAL:
                Log.w(TAG, "Memory trim: Running critical");
                break;
            case TRIM_MEMORY_UI_HIDDEN:
                Log.d(TAG, "Memory trim: UI hidden - good time to release UI caches");
                break;
            default:
                Log.d(TAG, "Memory trim level: " + level);
        }

        // TODO: Release memory based on trim level
    }
}

