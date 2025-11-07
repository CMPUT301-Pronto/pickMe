package com.example.pickme;

import android.app.Application;
import android.util.Log;

import com.example.pickme.services.FirebaseManager;
import com.google.firebase.FirebaseApp;

/**
 * Application entry point for the PickMe app.
 *
 * <p><b>Role / Pattern:</b> Custom {@link Application} used to perform one-time,
 * process-wide initialization before any Activity/Service/Receiver is created.
 * Centralizes bootstrapping of Firebase and app-scoped singletons.</p>
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Ensures Firebase is initialized early.</li>
 *   <li>Bootstraps {@link FirebaseManager} (Firestore/Storage/Auth/FCM).</li>
 *   <li>Kicks off anonymous authentication to establish a device identity.</li>
 *   <li>Provides a lightweight app-context accessor via {@link #getInstance()}.</li>
 * </ul>
 * </p>
 *
 * <p><b>Configuration:</b> Declare in {@code AndroidManifest.xml}:
 * <pre>{@code
 * <application
 *     android:name=".PickMeApplication"
 *     ... >
 *     ...
 * </application>
 * }</pre>
 * </p>
 *
 * <p><b>Lifecycle:</b> {@link #onCreate()} is invoked once per app process. Avoid heavy
 * synchronous work here; prefer async where possible to keep startup snappy.</p>
 *
 * <p><b>Outstanding issues / TODOs:</b>
 * <ul>
 *   <li>Persist the anonymous UID (or create a profile) on first sign-in.</li>
 *   <li>Consider adding crash/reporting and strict-mode configuration here.</li>
 * </ul>
 * </p>
 */
public class PickMeApplication extends Application {

    /** Process-wide application instance (initialized in {@link #onCreate()}). */
    private static PickMeApplication instance;
    /**
     * Returns the current {@link PickMeApplication} instance.
     * Useful for places where a Context is needed and an Activity is not available.
     *
     * @return the singleton application instance, or {@code null} before {@link #onCreate()}.
     */
    public static PickMeApplication getInstance() {
        return instance;
    }

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
        instance = this;
        Log.d(TAG, "Application starting - initializing Firebase");

        // 1. Initialize Firebase
        // FirebaseApp.initializeApp() is called automatically by the Firebase SDK
        // when google-services.json is present, but we can call it explicitly for clarity
        initializeFirebase();

        // 2. Initialize FirebaseManager singleton
        // This ensures all Firebase services are ready before any Activity uses them
        initializeFirebaseManager();

        // 3. Setup anonymous authentication for device-based user tracking
        setupAuthentication();

        Log.d(TAG, "Application initialization complete");
    }

    /**
     * Ensures {@link FirebaseApp} has been initialized. The Firebase plugin will usually
     * auto-init when {@code google-services.json} is present, but calling explicitly adds
     * safety and logging.
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
     * Triggers creation of the {@link FirebaseManager} singleton and underlying Firebase clients
     * (Firestore, Storage, Auth, FCM). Any failures are logged.
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
     * Starts/ensures an anonymous Firebase Authentication session so the device has
     * a stable identity before user profile creation. If already signed in, logs the UID.
     *
     * <p><b>Note:</b> Persisting or acting upon the UID is left as a TODO.</p>
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
    /**
     * Called when the app process is about to terminate in emulated environments.
     * <b>Not</b> guaranteed on real devices—avoid critical cleanup here.
     */
    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, "Application terminating");
    }


    /**
     * Called when the overall system is running low on memory.
     * Release caches and trim non-essential resources here.
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

