package com.example.pickme.services;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;


/**
 * JAVADOCS LLM GENERATED
 *
 * Centralized access point for Firebase services (Firestore, Storage, Auth, FCM).
 *
 * <p><b>Role / Pattern:</b> Application-scoped Singleton that encapsulates SDK
 * initialization and exposes a minimal, cohesive API for other layers. Simplifies
 * dependency wiring and keeps Firebase usage consistent across the app.</p>
 *
 * <p><b>Lifecycle:</b> Lazily initialized on first call to {@link #getInstance()}.
 * You should ensure {@code FirebaseApp} is initialized (e.g., via
 * {@code PickMeApplication}) before first use.</p>
 *
 * <p><b>Thread-safety:</b> Lazy init is guarded by {@code synchronized} in {@link #getInstance()}.
 * Thereafter, Firebase clients are thread-safe as per SDK guarantees.</p>
 *
 * <p><b>Offline:</b> Firestore is configured with persistence enabled and an unlimited local cache.
 * Adjust cache policy for production if necessary.</p>
 *
 * <p><b>FCM tokens:</b> On startup, an FCM token is fetched and conditionally stored if a userId
 * is already known. You can trigger a fresh fetch via {@link #refreshAndStoreFcmToken()} once
 * identity is available.</p>
 *
 * <p><b>Outstanding issues / TODOs:</b>
 * <ul>
 *   <li>Consider injecting repositories (for token storage) to avoid direct new-calls.</li>
 *   <li>Evaluate Firestore cache size policy (unlimited may grow without bound).</li>
 *   <li>Replace {@link #monitorConnectionState(ConnectionStateListener)} stub with a proper
 *       connectivity strategy (e.g., Android {@code ConnectivityManager} or retry policies).</li>
 * </ul>
 * </p>
 *
 * <p><b>Typical usage:</b></p>
 * <pre>{@code
 * FirebaseFirestore db = FirebaseManager.getFirestore();
 * StorageReference storage = FirebaseManager.getStorageReference();
 * FirebaseAuth auth = FirebaseManager.getAuth();
 * FirebaseMessaging fcm = FirebaseManager.getMessaging();
 * }</pre>
 */
public class FirebaseManager {

    private static final String TAG = "FirebaseManager";
    // Explicit storage bucket (set to the bucket you created in Firebase)
    // If you later change buckets, update this constant or move to configuration.
    private static final String FIREBASE_STORAGE_BUCKET = "gs://pronto-project-8503b.firebasestorage.app";

    // Singleton instance - only one FirebaseManager exists in the app
    private static FirebaseManager instance;

    // Firebase service instances
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private FirebaseAuth auth;
    private FirebaseMessaging messaging;

    // Connection state tracking
    private boolean isConnected = true;

    /**
     * Private constructor - prevents direct instantiation
     * Initializes all Firebase services with appropriate settings
     */
    private FirebaseManager() {
        try {
            initializeFirestore();
            initializeStorage();
            initializeAuth();
            initializeMessaging();
            Log.d(TAG, "Firebase services initialized successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase services", e);
        }
    }

    /**
     * Get singleton instance of FirebaseManager
     * Thread-safe lazy initialization
     *
     * @return FirebaseManager singleton instance
     */
    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    /**
     * Configures Firestore:
     * <ul>
     *   <li>Offline persistence enabled</li>
     *   <li>Unlimited cache</li>
     * </ul>
     * Adjust these settings if you need stronger bounds on disk usage.
     */
    private void initializeFirestore() {
        firestore = FirebaseFirestore.getInstance();

        // Configure Firestore settings
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)  // Enable offline data persistence
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)  // Allow unlimited cache
                .build();

        firestore.setFirestoreSettings(settings);

        Log.d(TAG, "Firestore initialized with offline persistence enabled");
    }

    /**
     * Initialize Firebase Storage
     * Used for storing and retrieving images (profile pictures, event posters)
     */
    private void initializeStorage() {
        try {
            // Try to initialize storage with explicit bucket if provided
            if (FIREBASE_STORAGE_BUCKET != null && !FIREBASE_STORAGE_BUCKET.isEmpty()) {
                storage = FirebaseStorage.getInstance(FIREBASE_STORAGE_BUCKET);
                Log.d(TAG, "Firebase Storage initialized with bucket: " + FIREBASE_STORAGE_BUCKET);
            } else {
                storage = FirebaseStorage.getInstance();
                Log.d(TAG, "Firebase Storage initialized with default bucket");
            }
        } catch (Exception e) {
            // Fallback to default instance if explicit init fails
            Log.w(TAG, "Failed to initialize storage with explicit bucket, falling back to default", e);
            storage = FirebaseStorage.getInstance();
            Log.d(TAG, "Firebase Storage initialized (fallback)");
        }
    }

    /**
     * Initialize Firebase Authentication
     * Used for device-based anonymous authentication
     */
    private void initializeAuth() {
        auth = FirebaseAuth.getInstance();
        Log.d(TAG, "Firebase Auth initialized");
    }

    /**
     * Initializes Firebase Cloud Messaging client and attempts to fetch the current FCM token.
     * If a userId is already known, the token is stored immediately via {@code ProfileRepository}.
     * Use {@link #refreshAndStoreFcmToken()} after login/first-run if userId was not yet available.
     */
    private void initializeMessaging() {
        messaging = FirebaseMessaging.getInstance();

        // Get FCM token for this device
        messaging.getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                String token = task.getResult();
                Log.d(TAG, "FCM Token: " + token);
                storeFcmTokenIfPossible(token);
            } else {
                Log.w(TAG, "Failed to get FCM token", task.getException());
            }
        });

        Log.d(TAG, "Firebase Cloud Messaging initialized");
    }

    /**
     * Attempts to persist the FCM token if a stored userId is already available.
     * Safe to call at any time; does nothing if userId is not yet known.
     *
     * @param token current FCM registration token
     */
    private void storeFcmTokenIfPossible(@NonNull String token) {
        Context context = FirebaseApp.getInstance().getApplicationContext();
        String userId = DeviceAuthenticator.getInstance(context).getStoredUserId();

        if (userId == null || userId.isEmpty()) {
            Log.d(TAG, "No userId yet; deferring FCM token store");
            return;
        }

        Log.d(TAG, "Storing FCM token for user: " + userId);
        new com.example.pickme.repositories.ProfileRepository().setFcmToken(userId, token);
    }

    /**
     * Forces a fresh token fetch and attempts to store it if a userId is known.
     * Useful after sign-in or profile creation.
     */
    public static void refreshAndStoreFcmToken() {
        Log.d(TAG, "Refreshing FCM token...");
        getInstance().messaging.getToken().addOnSuccessListener(token -> {
            Log.d(TAG, "Refreshed FCM token: " + token);
            getInstance().storeFcmTokenIfPossible(token);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Failed to refresh FCM token", e);
        });
    }

    /**
     * Get Firestore instance for database operations
     *
     * @return App wide FirebaseFirestore instance
     */
    public static FirebaseFirestore getFirestore() {
        return getInstance().firestore;
    }

    /**
     * Get Storage reference for file operations
     *
     * @return StorageReference to root of Firebase Storage
     */
    public static StorageReference getStorageReference() {
        return getInstance().storage.getReference();
    }

    /**
     * Get Storage reference for a specific path
     *
     * @param path Path to the storage location (e.g., "profile_images/user123.jpg")
     * @return StorageReference to the specified path
     */
    public static StorageReference getStorageReference(@NonNull String path) {
        return getInstance().storage.getReference(path);
    }

    /**
     * Get Firebase Authentication instance
     *
     * @return FirebaseAuth instance
     */
    public static FirebaseAuth getAuth() {
        return getInstance().auth;
    }

    /**
     * Get Firebase Cloud Messaging instance
     *
     * @return FirebaseMessaging instance
     */
    public static FirebaseMessaging getMessaging() {
        return getInstance().messaging;
    }

    /**
     * Check if device is connected to Firebase
     * This monitors the network connection state
     *
     * @return true if connected, false if offline
     */
    public static boolean isConnected() {
        return getInstance().isConnected;
    }

    /**
     * Enable Firestore network (useful after disabling for testing)
     */
    public static void enableNetwork() {
        getInstance().firestore.enableNetwork()
                .addOnSuccessListener(aVoid -> {
                    getInstance().isConnected = true;
                    Log.d(TAG, "Firestore network enabled");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to enable Firestore network", e);
                });
    }

    /**
     * Disables Firestore network access (useful to simulate offline).
     * Updates {@link #isConnected} and logs outcome.
     */
    public static void disableNetwork() {
        getInstance().firestore.disableNetwork()
                .addOnSuccessListener(aVoid -> {
                    getInstance().isConnected = false;
                    Log.d(TAG, "Firestore network disabled");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to disable Firestore network", e);
                });
    }

    /**
     * Registers a best-effort listener for connectivity changes.
     * <p><b>Note:</b> Firestore has no official <code>.info/connected</code> like RTDB.
     * This is a placeholder pattern—consider OS network callbacks or retry strategies instead.</p>
     *
     * @param listener callback invoked when connectivity is believed to change
     */
    public static void monitorConnectionState(ConnectionStateListener listener) {
        // Firestore doesn't have a built-in connection listener like Realtime Database
        // Instead, we can monitor failed operations or use network callbacks
        // For now, we'll provide a basic implementation

        getInstance().firestore.collection(".info")
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        getInstance().isConnected = false;
                        listener.onConnectionChanged(false);
                        Log.w(TAG, "Connection lost", error);
                    } else {
                        getInstance().isConnected = true;
                        listener.onConnectionChanged(true);
                        Log.d(TAG, "Connected to Firestore");
                    }
                });
    }

    /**
     * Interface for connection state change callbacks
     */
    public interface ConnectionStateListener {
        void onConnectionChanged(boolean isConnected);
    }

    /**
     * Sign in anonymously for device-based authentication
     * This creates a unique Firebase Auth user for each device
     *
     * @param callback Callback for authentication result
     */
    public static void signInAnonymously(AuthCallback callback) {
        getInstance().auth.signInAnonymously()
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser() != null ? authResult.getUser().getUid() : "unknown";
                    Log.d(TAG, "Anonymous sign-in successful. UID: " + uid);
                    callback.onSuccess(uid);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Anonymous sign-in failed", e);
                    callback.onFailure(e);
                });
    }

    /**
     * Get current authenticated user ID
     *
     * @return User ID if authenticated, null otherwise
     */
    public static String getCurrentUserId() {
        return getInstance().auth.getCurrentUser() != null
                ? getInstance().auth.getCurrentUser().getUid()
                : null;
    }

    /**
     * Check if user is authenticated
     *
     * @return true if user is signed in, false otherwise
     */
    public static boolean isUserAuthenticated() {
        return getInstance().auth.getCurrentUser() != null;
    }

    /**
     * Callback interface for authentication operations
     */
    public interface AuthCallback {
        void onSuccess(String userId);
        void onFailure(Exception e);
    }
}
