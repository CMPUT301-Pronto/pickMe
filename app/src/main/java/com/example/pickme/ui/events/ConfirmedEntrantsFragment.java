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
 * ConfirmedEntrantsFragment - Display confirmed entrants (inEventList)
 *
 * Shows entrants who have accepted their invitation and are confirmed
 * to attend the event. These are the final participants.
 *
 * Features:
 * - Displays confirmed attendees
 * - Allows organizer to cancel/remove confirmed entrants
 * - Sends notification to cancelled entrants
 * - Supports drawing replacements after cancellation
 *
 * This fragment extends BaseEntrantListFragment for lifecycle-aware data loading.
 *
 * Related User Stories: US 02.06.02, US 02.06.04
 */
public class ConfirmedEntrantsFragment extends BaseEntrantListFragment
        implements EntrantAdapter.OnEntrantActionListener {

    private static final String TAG = "ConfirmedFragment";

    private NotificationService notificationService;
    private Event currentEvent;

    /**
     * Factory method to create new instance
     *
     * @param eventId Event ID to display confirmed entrants for
     * @return ConfirmedEntrantsFragment instance
     */
    public static ConfirmedEntrantsFragment newInstance(String eventId) {
        ConfirmedEntrantsFragment fragment = new ConfirmedEntrantsFragment();
        return (ConfirmedEntrantsFragment) BaseEntrantListFragment.newInstance(eventId, fragment);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        notificationService = NotificationService.getInstance();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
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
        return Constants.SUBCOLLECTION_IN_EVENT;
    }

    @Override
    protected boolean showJoinTime() {
        return false; // Don't show join time for confirmed list
    }

    @Override
    protected boolean showStatus() {
        return false; // Don't show status - all are confirmed
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

        // Show confirmation dialog - more serious warning since they're confirmed
        new AlertDialog.Builder(getContext())
                .setTitle("Cancel Confirmed Entrant")
                .setMessage("Are you sure you want to cancel " + entrantName +
                        "'s confirmed spot?\n\nThis will:\n• Remove them from the event\n• Move them to the cancelled list\n• Send them a notification\n• Free up a spot for replacement draw")
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

        showLoading(true);

        eventRepository.cancelConfirmedEntrant(
                eventId,
                profile.getUserId(),
                "Cancelled by organizer",
                new EventRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String id) {
                        Log.d(TAG, "Confirmed entrant cancelled: " + id);

                        sendCancellationNotification(profile);

                        if (isAdded()) {
                            refresh();
                            Toast.makeText(getContext(),
                                    "Entrant removed from confirmed list",
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
     */
    private void sendCancellationNotification(Profile profile) {
        if (currentEvent == null) {
            Log.w(TAG, "Current event is null, cannot send notification");
            return;
        }

        notificationService.sendCancellationNotification(
                profile.getUserId(),
                currentEvent,
                "Your confirmed spot has been cancelled by the organizer",
                new NotificationService.OnNotificationSentListener() {
                    @Override
                    public void onNotificationSent(int sentCount) {
                        Log.d(TAG, "Cancellation notification sent");
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Failed to send cancellation notification", e);
                    }
                }
        );
    }
}