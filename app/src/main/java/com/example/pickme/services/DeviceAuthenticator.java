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
 * DeviceAuthenticator - Device-based authentication service
 *
 * Implements no-username/password authentication (US 01.07.01).
 * Uses Firebase Installation ID as unique device identifier.
 *
 * On first app launch:
 * 1. Get device ID (Firebase Installation ID)
 * 2. Check if Profile exists
 * 3. If not, create default profile
 * 4. Store device ID in SharedPreferences
 *
 * User roles:
 * - entrant: Default for all new users
 * - organizer: Can create events
 * - admin: Full access
 *
 * Related User Stories: US 01.07.01
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
     * Initialize user on app startup
     *
     * Process:
     * 1. Get device ID
     * 2. Check if profile exists
     * 3. If new user, create default profile
     * 4. Return Profile object
     *
     * Should be called in Application.onCreate()
     *
     * @param listener Callback with Profile
     */
    public void initializeUser(@NonNull OnUserInitializedListener listener) {
        Log.d(TAG, "Initializing user");

        getDeviceId(new OnDeviceIdLoadedListener() {
            @Override
            public void onDeviceIdLoaded(String deviceId) {
                // Check if profile exists
                profileRepository.profileExists(deviceId, new ProfileRepository.OnProfileExistsListener() {
                    @Override
                    public void onCheckComplete(boolean exists) {
                        if (exists) {
                            // Load existing profile
                            profileRepository.getProfile(deviceId, new ProfileRepository.OnProfileLoadedListener() {
                                @Override
                                public void onProfileLoaded(Profile profile) {
                                    Log.d(TAG, "Existing user loaded: " + profile.getName());
                                    cachedProfile = profile;
                                    prefs.edit()
                                            .putString(KEY_USER_ID, deviceId)
                                            .putBoolean(KEY_FIRST_LAUNCH, false)
                                            .apply();
                                    listener.onUserInitialized(profile, false);
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.e(TAG, "Failed to load profile", e);
                                    listener.onError(e);
                                }
                            });
                        } else {
                            // Create new profile for first-time user
                            Profile newProfile = new Profile(deviceId, "User" + deviceId.substring(0, 8));
                            newProfile.setRole(Profile.ROLE_ENTRANT); // Default role

                            profileRepository.createProfile(newProfile, new ProfileRepository.OnSuccessListener() {
                                @Override
                                public void onSuccess(String userId) {
                                    Log.d(TAG, "New user profile created: " + userId);
                                    cachedProfile = newProfile;
                                    prefs.edit()
                                            .putString(KEY_USER_ID, userId)
                                            .putBoolean(KEY_FIRST_LAUNCH, true)
                                            .apply();
                                    listener.onUserInitialized(newProfile, true);
                                }
                            }, new ProfileRepository.OnFailureListener() {
                                @Override
                                public void onFailure(Exception e) {
                                    Log.e(TAG, "Failed to create profile", e);
                                    listener.onError(e);
                                }
                            });
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Failed to check profile existence", e);
                        listener.onError(e);
                    }
                });
            }
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

