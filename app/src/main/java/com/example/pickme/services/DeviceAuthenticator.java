package com.example.pickme.services;

import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.models.Profile;
import com.example.pickme.repositories.ProfileRepository;
import com.google.firebase.installations.FirebaseInstallations;

/**
 * JAVADOCS LLM GENERATED
 *
 * Manages device-based authentication and user initialization.
 *
 * <p><b>Role / Pattern:</b> Singleton service responsible for implementing
 *  account creation by using a unique device identifier
 * (Firebase Installation ID or Android ID fallback) to bind a {@link Profile}
 * to a specific installation.</p>
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Retrieve and cache the device identifier.</li>
 *   <li>Check whether a corresponding {@link Profile} already exists in Firestore.</li>
 *   <li>Create a default profile for first-time users (role = entrant).</li>
 *   <li>Cache and persist user state in {@link SharedPreferences}.</li>
 * </ul>
 * </p>
 *
 * <p><b>Storage:</b> Authentication metadata is stored in {@code PickMePrefs} under:
 * <ul>
 *   <li>{@code device_id} – Firebase Installation ID or Android ID fallback.</li>
 *   <li>{@code user_id} – the Firestore document ID of the user profile.</li>
 *   <li>{@code is_first_launch} – flag for first-launch onboarding logic.</li>
 * </ul>
 * </p>
 *
 * <p><b>Typical Flow (on first launch):</b>
 * <ol>
 *   <li>Obtain device ID via Firebase Installations.</li>
 *   <li>Check if a profile exists for that ID.</li>
 *   <li>If not found, create a new default entrant profile.</li>
 *   <li>Persist device/user IDs and return the initialized {@link Profile}.</li>
 * </ol>
 * </p>
 *
 * <p><b>Related User Story:</b> US 01.07.01 – Device-based authentication.</p>
 */
public class DeviceAuthenticator {

    private static final String TAG = "DeviceAuthenticator";
    private static final String PREFS_NAME = "PickMePrefs";
    private static final String KEY_DEVICE_ID = "device_id";
    private static final String KEY_FIRST_LAUNCH = "is_first_launch";
    private static final String KEY_USER_ID = "user_id";

    private static DeviceAuthenticator instance;
    private Context context;
    private SharedPreferences prefs;
    private ProfileRepository profileRepository;
    private String cachedDeviceId;
    private Profile cachedProfile;

    private DeviceAuthenticator(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        this.profileRepository = new ProfileRepository();
    }

    /**
     * Returns the singleton instance of the authenticator.
     *
     * @param context an application or activity context.
     * @return global {@link DeviceAuthenticator} instance.
     */
    public static synchronized DeviceAuthenticator getInstance(Context context) {
        if (instance == null) {
            instance = new DeviceAuthenticator(context);
        }
        return instance;
    }

    /**
     * Get device ID (Firebase Installation ID)
     *
     * Uses Firebase Installations API which provides:
     * - Unique ID per app installation
     * - Persists across app restarts
     * - Can be reset if needed
     *
     * @return Device ID
     */
    public void getDeviceId(@NonNull OnDeviceIdLoadedListener listener) {
        // Check cache first
        if (cachedDeviceId != null) {
            listener.onDeviceIdLoaded(cachedDeviceId);
            return;
        }

        // Check SharedPreferences
        String savedDeviceId = prefs.getString(KEY_DEVICE_ID, null);
        if (savedDeviceId != null) {
            cachedDeviceId = savedDeviceId;
            listener.onDeviceIdLoaded(savedDeviceId);
            return;
        }

        // Get from Firebase Installations
        FirebaseInstallations.getInstance().getId()
                .addOnSuccessListener(installationId -> {
                    Log.d(TAG, "Firebase Installation ID retrieved");

                    // Save to SharedPreferences
                    prefs.edit().putString(KEY_DEVICE_ID, installationId).apply();
                    cachedDeviceId = installationId;

                    listener.onDeviceIdLoaded(installationId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get Firebase Installation ID, using Android ID fallback", e);

                    // Fallback to Android ID
                    String androidId = Settings.Secure.getString(
                            context.getContentResolver(),
                            Settings.Secure.ANDROID_ID
                    );

                    prefs.edit().putString(KEY_DEVICE_ID, androidId).apply();
                    cachedDeviceId = androidId;

                    listener.onDeviceIdLoaded(androidId);
                });
    }


    /**
     * Initializes or loads the user’s profile.
     *
     * <p>This should be called once during app startup (e.g. in
     * {@code Application.onCreate()}). The method will:
     * <ol>
     *   <li>Obtain the device ID.</li>
     *   <li>Check Firestore for an existing profile.</li>
     *   <li>Create a new default entrant profile if none exists.</li>
     *   <li>Cache the profile locally and return it via callback.</li>
     * </ol>
     * </p>
     *
     * @param listener callback that receives the initialized profile and whether it is new.
     */
    public void initializeUser(@NonNull OnUserInitializedListener listener) {
        Log.d(TAG, "Initializing user");

        getDeviceId(deviceId -> {
            profileRepository.profileExists(deviceId, new ProfileRepository.OnProfileExistsListener() {
                @Override
                public void onCheckComplete(boolean exists) {
                    if (exists) {
                        // Load existing profile normally
                        profileRepository.getProfile(deviceId, new ProfileRepository.OnProfileLoadedListener() {
                            @Override public void onProfileLoaded(Profile profile) {
                                Log.d(TAG, "Existing user loaded: " + profile.getName());
                                cachedProfile = profile;
                                prefs.edit()
                                        .putString(KEY_USER_ID, deviceId)
                                        .putBoolean(KEY_FIRST_LAUNCH, false)
                                        .apply();
                                listener.onUserInitialized(profile, /*isNewUser=*/false);
                            }
                            @Override public void onError(Exception e) {
                                Log.e(TAG, "Failed to load profile", e);
                                listener.onError(e);
                            }
                        });
                    } else {

                        // profile does not exist yet
                        cachedProfile = null;
                        prefs.edit()
                                .putString(KEY_USER_ID, deviceId)
                                .putBoolean(KEY_FIRST_LAUNCH, true)
                                .apply();

                        // pass a  placeholder profile object if you want,
                        // NOT saved to Firestore.
                        Profile placeholder = new Profile(deviceId, null);
                        listener.onUserInitialized(placeholder, /*isNewUser=*/true);
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to check profile existence", e);
                    listener.onError(e);
                }
            });
        });
    }
    /**
     * Get user role (entrant, organizer, admin)
     *
     * @return Role string
     */
    public String getUserRole() {
        if (cachedProfile != null) {
            return cachedProfile.getRole();
        }
        return Profile.ROLE_ENTRANT; // Default
    }

    /**
     * Get cached profile
     *
     * @return Cached Profile or null
     */
    public Profile getCachedProfile() {
        return cachedProfile;
    }

    /**
     * Check if this is first app launch
     *
     * @return true if first launch
     */
    public boolean isFirstLaunch() {
        return prefs.getBoolean(KEY_FIRST_LAUNCH, true);
    }

    /**
     * Get stored user ID
     *
     * @return User ID or null
     */
    public String getStoredUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    /**
     * Update cached profile
     *
     * @param profile Updated profile
     */
    public void updateCachedProfile(Profile profile) {
        this.cachedProfile = profile;
    }

    /**
     * Clear authentication data (logout/reset)
     */
    public void clearAuthData() {
        prefs.edit().clear().apply();
        cachedDeviceId = null;
        cachedProfile = null;
        Log.d(TAG, "Authentication data cleared");
    }

    /**
     * Callback for device ID retrieval
     */
    public interface OnDeviceIdLoadedListener {
        void onDeviceIdLoaded(String deviceId);
    }

    /**
     * Callback for user initialization
     */
    public interface OnUserInitializedListener {
        void onUserInitialized(Profile profile, boolean isNewUser);
        void onError(Exception e);
    }
}

