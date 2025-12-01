package com.example.pickme.repositories;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.models.EventHistoryItem;
import com.example.pickme.models.Profile;
import com.example.pickme.services.FirebaseManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ProfileRepository - Repository for Profile operations
 *
 * Handles all Firestore operations related to user profiles including:
 * - Profile creation, retrieval, update, deletion
 * - Device-based authentication (no username/password)
 * - Event history management
 * - Notification preferences
 * - Cascade deletion (removes from waiting lists)
 *
 * Firestore Structure:
 * profiles/{userId}
 *   ├─ userId (device ID or Firebase Installation ID)
 *   ├─ name
 *   ├─ email (optional)
 *   ├─ phoneNumber (optional)
 *   ├─ notificationEnabled
 *   ├─ eventHistory: [ {...}, {...} ]
 *   └─ profileImageUrl
 *
 * Device-based authentication:
 * - Uses Android ID or Firebase Installation ID as unique identifier
 * - No username/password required (US 01.07.01)
 * - Profile auto-created on first app launch
 *
 * Related User Stories: US 01.02.01, US 01.02.02, US 01.02.03, US 01.02.04,
 *                       US 03.02.01, US 03.05.01
 */
public class ProfileRepository extends BaseRepository {

    private static final String TAG = "ProfileRepository";
    private static final String COLLECTION_PROFILES = "profiles";
    private static final String COLLECTION_EVENTS = "events";
    private static final String SUBCOLLECTION_WAITING_LIST = "waitingList";
    private static final String SUBCOLLECTION_RESPONSE_PENDING = "responsePendingList";
    private static final String SUBCOLLECTION_IN_EVENT = "inEventList";

    private FirebaseFirestore db;

    /**
     * Constructor - initializes repository for "profiles" collection
     */
    public ProfileRepository() {
        super(COLLECTION_PROFILES);
        this.db = FirebaseManager.getFirestore();
    }

    // ==================== Profile CRUD Operations ====================

    /**
     * Create a new profile
     * Used on first app launch with device-based authentication
     *
     * @param profile Profile object to create
     * @param onSuccess Success callback with user ID
     * @param onFailure Failure callback with exception
     */
    public void createProfile(@NonNull Profile profile,
                             @NonNull OnSuccessListener onSuccess,
                             @NonNull OnFailureListener onFailure) {
        if (profile.getUserId() == null || profile.getUserId().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("Profile must have a userId"));
            return;
        }

        db.collection(COLLECTION_PROFILES)
                .document(profile.getUserId())
                .set(profile.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile created successfully: " + profile.getUserId());
                    onSuccess.onSuccess(profile.getUserId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create profile", e);
                    onFailure.onFailure(e);
                });
    }

    /**
     * Update specific fields of a profile
     *
     * @param userId User ID to update
     * @param updates Map of field names to new values
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void updateProfile(@NonNull String userId,
                             @NonNull Map<String, Object> updates,
                             @NonNull OnSuccessListener onSuccess,
                             @NonNull OnFailureListener onFailure) {
        db.collection(COLLECTION_PROFILES)
                .document(userId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile updated successfully: " + userId);
                    onSuccess.onSuccess(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update profile: " + userId, e);
                    onFailure.onFailure(e);
                });
    }

    /**
     * Delete a profile with cascade deletion
     * Removes user from all event waiting lists, response pending lists, and in-event lists
     *
     * @param userId User ID to delete
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void deleteProfile(@NonNull String userId,
                             @NonNull OnSuccessListener onSuccess,
                             @NonNull OnFailureListener onFailure) {
        // First, remove user from all event subcollections
        cascadeDeleteFromEvents(userId, new OnCascadeCompleteListener() {
            @Override
            public void onComplete() {
                // Then delete the profile document
                db.collection(COLLECTION_PROFILES)
                        .document(userId)
                        .delete()
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Profile deleted successfully: " + userId);
                            onSuccess.onSuccess(userId);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to delete profile: " + userId, e);
                            onFailure.onFailure(e);
                        });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Cascade deletion failed, aborting profile deletion", e);
                onFailure.onFailure(e);
            }
        });
    }

    /**
     * Cascade delete user from all event subcollections
     * Removes user entries from waiting lists, response pending lists, and in-event lists
     *
     * @param userId User ID to remove
     * @param listener Callback when cascade deletion completes
     */
    private void cascadeDeleteFromEvents(@NonNull String userId,
                                        @NonNull OnCascadeCompleteListener listener) {
        // Get all events
        db.collection(COLLECTION_EVENTS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    WriteBatch batch = db.batch();
                    int deleteCountValue = 0;

                    // For each event, delete user from subcollections
                    for (DocumentSnapshot eventDoc : querySnapshot.getDocuments()) {
                        String eventId = eventDoc.getId();

                        // Delete from waiting list
                        batch.delete(db.collection(COLLECTION_EVENTS)
                                .document(eventId)
                                .collection(SUBCOLLECTION_WAITING_LIST)
                                .document(userId));

                        // Delete from response pending list
                        batch.delete(db.collection(COLLECTION_EVENTS)
                                .document(eventId)
                                .collection(SUBCOLLECTION_RESPONSE_PENDING)
                                .document(userId));

                        // Delete from in-event list
                        batch.delete(db.collection(COLLECTION_EVENTS)
                                .document(eventId)
                                .collection(SUBCOLLECTION_IN_EVENT)
                                .document(userId));

                        deleteCountValue += 3;
                    }

                    final int deleteCount = deleteCountValue;

                    // Commit batch deletion
                    if (deleteCount > 0) {
                        batch.commit()
                                .addOnSuccessListener(aVoid -> {
                                    Log.d(TAG, "Cascade deletion completed for user: " + userId +
                                              " (" + deleteCount + " potential deletions)");
                                    listener.onComplete();
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Cascade deletion batch failed", e);
                                    listener.onError(e);
                                });
                    } else {
                        listener.onComplete();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get events for cascade deletion", e);
                    listener.onError(e);
                });
    }
    public void setFcmToken(@NonNull String userId, @NonNull String token) {
        Log.d(TAG, "Saving FCM token for user: " + userId);
        db.collection(COLLECTION_PROFILES)
                .document(userId)
                .update("fcmToken", token)
                .addOnSuccessListener(v -> {
                    Log.d(TAG, "FCM token saved successfully for user: " + userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to store FCM token for user: " + userId, e);
                });
    }

    /**
     * Get a profile by user ID
     *
     * @param userId User ID to retrieve
     * @param listener Callback with Profile object
     */
    public void getProfile(@NonNull String userId,
                          @NonNull OnProfileLoadedListener listener) {
        db.collection(COLLECTION_PROFILES)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Profile profile = documentSnapshot.toObject(Profile.class);
                        Log.d(TAG, "Profile retrieved: " + userId);
                        listener.onProfileLoaded(profile);
                    } else {
                        Log.w(TAG, "Profile not found: " + userId);
                        listener.onError(new Exception("Profile not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get profile: " + userId, e);
                    listener.onError(e);
                });
    }

    /**
     * Get all profiles (for admin purposes)
     *
     * @param listener Callback with list of all profiles
     */
    public void getAllProfiles(@NonNull OnProfilesLoadedListener listener) {
        db.collection(COLLECTION_PROFILES)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Profile> profiles = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Profile profile = doc.toObject(Profile.class);
                        if (profile != null) {
                            profiles.add(profile);
                        }
                    }
                    Log.d(TAG, "Retrieved all profiles: " + profiles.size());
                    listener.onProfilesLoaded(profiles);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get all profiles", e);
                    listener.onError(e);
                });
    }

    // ==================== Event History Management ====================

    /**
     * Add event to user's event history
     * Appends EventHistoryItem to the eventHistory array
     *
     * @param userId User ID
     * @param item EventHistoryItem to add
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void addEventToHistory(@NonNull String userId,
                                  @NonNull EventHistoryItem item,
                                  @NonNull OnSuccessListener onSuccess,
                                  @NonNull OnFailureListener onFailure) {
        // Convert EventHistoryItem to Map
        Map<String, Object> itemMap = new HashMap<>();
        itemMap.put("eventId", item.getEventId());
        itemMap.put("eventName", item.getEventName());
        itemMap.put("joinedTimestamp", item.getJoinedTimestamp());
        itemMap.put("status", item.getStatus());

        // Use FieldValue.arrayUnion to append to array
        db.collection(COLLECTION_PROFILES)
                .document(userId)
                .update("eventHistory", FieldValue.arrayUnion(itemMap))
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event added to history for user: " + userId);
                    onSuccess.onSuccess(userId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add event to history", e);
                    onFailure.onFailure(e);
                });
    }

    /**
     * Update event history item status
     * Finds matching event in history and updates its status
     *
     * @param userId User ID
     * @param eventId Event ID to update
     * @param newStatus New status value
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void updateEventHistoryStatus(@NonNull String userId,
                                        @NonNull String eventId,
                                        @NonNull String newStatus,
                                        @NonNull OnSuccessListener onSuccess,
                                        @NonNull OnFailureListener onFailure) {
        // First get the profile
        getProfile(userId, new OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(Profile profile) {
                List<EventHistoryItem> history = profile.getEventHistory();

                // Find and update the matching event
                boolean found = false;
                if (history != null) {
                    for (EventHistoryItem item : history) {
                        if (eventId.equals(item.getEventId())) {
                            item.setStatus(newStatus);
                            found = true;
                            break;
                        }
                    }
                }

                if (found) {
                    // Update the entire eventHistory array
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("eventHistory", convertHistoryToMapList(history));

                    updateProfile(userId, updates, onSuccess, onFailure);
                } else {
                    onFailure.onFailure(new Exception("Event not found in history"));
                }
            }

            @Override
            public void onError(Exception e) {
                onFailure.onFailure(e);
            }
        });
    }

    /**
     * Convert List<EventHistoryItem> to List<Map> for Firestore
     */
    private List<Map<String, Object>> convertHistoryToMapList(List<EventHistoryItem> history) {
        List<Map<String, Object>> mapList = new ArrayList<>();
        for (EventHistoryItem item : history) {
            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("eventId", item.getEventId());
            itemMap.put("eventName", item.getEventName());
            itemMap.put("joinedTimestamp", item.getJoinedTimestamp());
            itemMap.put("status", item.getStatus());
            mapList.add(itemMap);
        }
        return mapList;
    }

    // ==================== Notification Preferences ====================

    /**
     * Update notification preference for user
     *
     * @param userId User ID
     * @param enabled true to enable notifications, false to disable
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void updateNotificationPreference(@NonNull String userId,
                                            boolean enabled,
                                            @NonNull OnSuccessListener onSuccess,
                                            @NonNull OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationEnabled", enabled);

        updateProfile(userId, updates, onSuccess, onFailure);
    }

    /**
     * Check if profile exists
     *
     * @param userId User ID to check
     * @param listener Callback with boolean result
     */
    public void profileExists(@NonNull String userId,
                             @NonNull OnProfileExistsListener listener) {
        db.collection(COLLECTION_PROFILES)
                .document(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean exists = documentSnapshot.exists();
                    Log.d(TAG, "Profile exists check for " + userId + ": " + exists);
                    listener.onCheckComplete(exists);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check profile existence", e);
                    listener.onError(e);
                });
    }

    // ==================== Listener Interfaces ====================

    /**
     * Callback for successful operations
     */
    public interface OnSuccessListener {
        void onSuccess(String userId);
    }

    /**
     * Callback for failed operations
     */
    public interface OnFailureListener {
        void onFailure(Exception e);
    }

    /**
     * Callback for loading a single profile
     */
    public interface OnProfileLoadedListener {
        void onProfileLoaded(Profile profile);
        void onError(Exception e);
    }

    /**
     * Callback for loading multiple profiles
     */
    public interface OnProfilesLoadedListener {
        void onProfilesLoaded(List<Profile> profiles);
        void onError(Exception e);
    }

    /**
     * Callback for cascade deletion completion
     */
    private interface OnCascadeCompleteListener {
        void onComplete();
        void onError(Exception e);
    }

    /**
     * Callback for profile existence check
     */
    public interface OnProfileExistsListener {
        void onCheckComplete(boolean exists);
        void onError(Exception e);
    }
}

