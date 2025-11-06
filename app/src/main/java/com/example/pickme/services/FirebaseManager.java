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
 * FirebaseManager - Singleton class for managing Firebase services
 *
 * This class provides centralized access to all Firebase services used in the app:
 * - Firestore: NoSQL database for storing events, users, and lottery data
 * - Storage: Cloud storage for images (profile pictures, event posters)
 * - Cloud Messaging (FCM): Push notifications for event updates
 * - Authentication: Device-based authentication
 *
 * Singleton Pattern ensures:
 * - Only one instance exists throughout the app lifecycle
 * - Consistent Firebase configuration across all components
 * - Efficient resource management
 *
 * Usage:
 *   FirebaseFirestore db = FirebaseManager.getFirestore();
 *   StorageReference storage = FirebaseManager.getStorageReference();
 */
public class FirebaseManager {

    private static final String TAG = "FirebaseManager";

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
     * Initialize Firestore with optimal settings
     * - Enables offline persistence for better user experience
     * - Configures cache size for offline data
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
        storage = FirebaseStorage.getInstance();
        Log.d(TAG, "Firebase Storage initialized");
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
     * Initialize Firebase Cloud Messaging
     * Used for push notifications (event updates, lottery results)
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
    /** Try to store the FCM token if we already know the userId. Safe to call anytime. */
    private void storeFcmTokenIfPossible(@NonNull String token) {
        Context context = FirebaseApp.getInstance().getApplicationContext();
        String userId = DeviceAuthenticator.getInstance(context).getStoredUserId();

        if (userId == null || userId.isEmpty()) {
            Log.d(TAG, "No userId yet; deferring FCM token store");
            return;
        }

        new com.example.pickme.repositories.ProfileRepository().setFcmToken(userId, token);
    }
    public static void refreshAndStoreFcmToken() {
        getInstance().messaging.getToken().addOnSuccessListener(token -> {
            Log.d(TAG, "Refreshed FCM token: " + token);
            getInstance().storeFcmTokenIfPossible(token);
        });
    }

        /**
         * Get Firestore instance for database operations
         *
         * @return FirebaseFirestore instance
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
     * Disable Firestore network (useful for testing offline scenarios)
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
     * Setup connection state monitoring
     * Monitors .info/connected path in Firestore to detect connection changes
     *
     * @param listener Callback for connection state changes
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
