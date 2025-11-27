package com.example.pickme.ui.events;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.pickme.models.Event;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.services.NotificationService;
import com.example.pickme.utils.Constants;

/**
 * SelectedEntrantsFragment - Display selected entrants (responsePendingList)
 *
 * Shows entrants who have been selected in the lottery draw and are awaiting
 * response (pending acceptance/decline of invitation).
 *
 * Features:
 * - Displays status indicators to show whether entrants have:
 *   - Not yet responded
 *   - Accepted invitation
 *   - Declined invitation
 * - Allows organizer to cancel entrants who didn't complete registration
 * - Sends notification to cancelled entrants
 * - Supports drawing replacements after cancellation
 *
 * This fragment extends BaseEntrantListFragment for lifecycle-aware data loading.
 *
 * Related User Stories: US 02.06.01, US 02.06.02
 */
public class SelectedEntrantsFragment extends BaseEntrantListFragment
        implements EntrantAdapter.OnEntrantActionListener {

    private static final String TAG = "SelectedFragment";

    private NotificationService notificationService;
    private Event currentEvent;

    /**
     * Factory method to create new instance
     *
     * @param eventId Event ID to display selected entrants for
     * @return SelectedEntrantsFragment instance
     */
    public static SelectedEntrantsFragment newInstance(String eventId) {
        SelectedEntrantsFragment fragment = new SelectedEntrantsFragment();
        return (SelectedEntrantsFragment) BaseEntrantListFragment.newInstance(eventId, fragment);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationService = NotificationService.getInstance();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Load the event for notification purposes
        loadEvent();
    }

    /**
     * Load the event object for use in notifications
     */
    private void loadEvent() {
        if (eventId != null) {
            eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
                @Override
                public void onEventLoaded(Event event) {
                    currentEvent = event;
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to load event", e);
                }
            });
        }
    }

    @Override
    protected String getSubcollectionName() {
        return Constants.SUBCOLLECTION_RESPONSE_PENDING;
    }

    @Override
    protected boolean showJoinTime() {
        return false; // Don't show join time for selected list
    }

    @Override
    protected boolean showStatus() {
        return true; // Show response status (pending/accepted/declined)
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    /**
     * Enable cancel option in the adapter menu
     */
    @Override
    protected boolean showCancelOption() {
        return true;
    }

    /**
     * Return this fragment as the action listener
     */
    @Override
    protected EntrantAdapter.OnEntrantActionListener getActionListener() {
        return this;
    }

    // ==================== OnEntrantActionListener Implementation ====================

    /**
     * Handle cancel entrant action from adapter
     * Shows confirmation dialog before proceeding
     *
     * @param profile The profile of the entrant to cancel
     */
    @Override
    public void onCancelEntrant(Profile profile) {
        if (getContext() == null || profile == null) return;

        String entrantName = profile.getName();
        if (entrantName == null || entrantName.trim().isEmpty()) {
            entrantName = "this entrant";
        }

        // Show confirmation dialog
        new AlertDialog.Builder(getContext())
                .setTitle("Cancel Entrant")
                .setMessage("Are you sure you want to cancel " + entrantName +
                        "?\n\nThis will:\n• Remove them from the selected list\n• Move them to the cancelled list\n• Send them a notification")
                .setPositiveButton("Cancel Entrant", (dialog, which) -> {
                    performCancellation(profile);
                })
                .setNegativeButton("Keep", (dialog, which) -> {
                    dialog.dismiss();
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Perform the actual cancellation after confirmation
     *
     * @param profile The profile of the entrant to cancel
     */
    private void performCancellation(Profile profile) {
        if (eventId == null || profile.getUserId() == null) {
            Toast.makeText(getContext(), "Error: Missing required data", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show loading indicator
        showLoading(true);

        // Perform cancellation in repository
        eventRepository.cancelSelectedEntrant(
                eventId,
                profile.getUserId(),
                "Did not complete registration", // Default reason
                new EventRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String id) {
                        Log.d(TAG, "Entrant cancelled successfully: " + id);

                        // Send notification to the cancelled entrant
                        sendCancellationNotification(profile);

                        // Refresh the list
                        if (isAdded()) {
                            refresh(); // Use the parent's refresh method
                            Toast.makeText(getContext(),
                                    "Entrant cancelled successfully",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new EventRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Failed to cancel entrant", e);
                        if (isAdded()) {
                            showLoading(false);
                            Toast.makeText(getContext(),
                                    "Failed to cancel entrant: " + e.getMessage(),
                                    Toast.LENGTH_LONG).show();
                        }
                    }
                }
        );
    }

    /**
     * Send cancellation notification to the entrant
     *
     * @param profile The profile of the cancelled entrant
     */
    private void sendCancellationNotification(Profile profile) {
        if (currentEvent == null) {
            Log.w(TAG, "Current event is null, cannot send notification");
            // Still refresh the list even if notification fails
            return;
        }

        notificationService.sendCancellationNotification(
                profile.getUserId(),
                currentEvent,
                "Did not complete registration",
                new NotificationService.OnNotificationSentListener() {
                    @Override
                    public void onNotificationSent(int sentCount) {
                        Log.d(TAG, "Cancellation notification sent");
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Failed to send cancellation notification", e);
                        // Don't show error to user - the cancellation succeeded
                    }
                }
        );
    }
}