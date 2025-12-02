package com.example.pickme.utils;

import android.util.Log;

import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ProfileRepository;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AdminUtils - Helper methods for admin operations
 *
 * Provides cascade delete functionality for organizers and their events.
 *
 * Related User Stories: US 03.07.01 (Admin can remove events), US 03.07.02 (Admin can remove profiles)
 */
public class AdminUtils {

    private static final String TAG = "AdminUtils";

    /**
     * Delete an organizer and all their events
     *
     * This method:
     * 1. Fetches all events created by the organizer
     * 2. Deletes each event (including subcollections like waitingList, etc.)
     * 3. Deletes the organizer's profile
     *
     * @param userId The organizer's user ID to delete
     * @param listener Callback for completion/error
     */
    public static void deleteOrganizerWithEvents(String userId, OnDeleteCompleteListener listener) {
        EventRepository eventRepository = new EventRepository();
        ProfileRepository profileRepository = new ProfileRepository();

        Log.d(TAG, "Starting cascade delete for organizer: " + userId);

        // First, get all events created by this organizer
        eventRepository.getEventsByOrganizer(userId, new EventRepository.OnEventsLoadedListener() {
            @Override
            public void onEventsLoaded(List<Event> events) {
                if (events == null || events.isEmpty()) {
                    // No events to delete, just delete the profile
                    Log.d(TAG, "No events found for organizer, deleting profile only");
                    deleteProfile(profileRepository, userId, listener);
                    return;
                }

                Log.d(TAG, "Found " + events.size() + " events to delete for organizer: " + userId);

                // Delete all events, then delete profile
                deleteEventsRecursively(eventRepository, profileRepository, events, 0, userId, listener);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to get organizer's events", e);
                // Try to delete profile anyway
                deleteProfile(profileRepository, userId, listener);
            }
        });
    }

    /**
     * Recursively delete events one by one
     */
    private static void deleteEventsRecursively(EventRepository eventRepository,
                                                ProfileRepository profileRepository,
                                                List<Event> events,
                                                int index,
                                                String userId,
                                                OnDeleteCompleteListener listener) {
        if (index >= events.size()) {
            // All events deleted, now delete the profile
            Log.d(TAG, "All events deleted, now deleting profile");
            deleteProfile(profileRepository, userId, listener);
            return;
        }

        Event event = events.get(index);
        String eventId = event.getEventId();

        Log.d(TAG, "Deleting event " + (index + 1) + "/" + events.size() + ": " + eventId);

        eventRepository.deleteEvent(eventId,
                id -> {
                    Log.d(TAG, "Successfully deleted event: " + eventId);
                    // Delete next event
                    deleteEventsRecursively(eventRepository, profileRepository, events, index + 1, userId, listener);
                },
                e -> {
                    Log.e(TAG, "Failed to delete event: " + eventId, e);
                    // Continue with next event anyway
                    deleteEventsRecursively(eventRepository, profileRepository, events, index + 1, userId, listener);
                });
    }

    /**
     * Delete the profile after events are deleted
     */
    private static void deleteProfile(ProfileRepository profileRepository,
                                      String userId,
                                      OnDeleteCompleteListener listener) {
        profileRepository.deleteProfile(userId,
                uid -> {
                    Log.d(TAG, "Successfully deleted organizer profile: " + userId);
                    if (listener != null) {
                        listener.onDeleteComplete(userId);
                    }
                },
                e -> {
                    Log.e(TAG, "Failed to delete organizer profile: " + userId, e);
                    if (listener != null) {
                        listener.onError(e);
                    }
                });
    }

    /**
     * Delete a user profile with cascade delete if they're an organizer
     * Checks role first and deletes events only if organizer
     *
     * @param userId User ID to delete
     * @param listener Callback for completion/error
     */
    public static void deleteUserWithCascade(String userId, OnDeleteCompleteListener listener) {
        ProfileRepository profileRepository = new ProfileRepository();

        // First check if user is an organizer
        profileRepository.getProfile(userId, new ProfileRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(com.example.pickme.models.Profile profile) {
                if (profile == null) {
                    if (listener != null) {
                        listener.onError(new Exception("Profile not found"));
                    }
                    return;
                }

                // If organizer, delete events first
                if (com.example.pickme.models.Profile.ROLE_ORGANIZER.equals(profile.getRole())) {
                    Log.d(TAG, "User is organizer, performing cascade delete");
                    deleteOrganizerWithEvents(userId, listener);
                } else {
                    // Not an organizer, just delete profile
                    Log.d(TAG, "User is not organizer, deleting profile only");
                    deleteProfile(profileRepository, userId, listener);
                }
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to get profile for cascade delete check", e);
                if (listener != null) {
                    listener.onError(e);
                }
            }
        });
    }

    /**
     * Callback interface for delete operations
     */
    public interface OnDeleteCompleteListener {
        void onDeleteComplete(String userId);
        void onError(Exception e);
    }
}