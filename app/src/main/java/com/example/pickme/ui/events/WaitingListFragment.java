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
 * WaitingListFragment - Display waiting list entrants
 *
 * Shows all entrants who joined the waiting list for an event.
 * Displays join timestamps to show when each entrant registered.
 *
 * Features:
 * - Displays join timestamps for each entrant
 * - Allows organizer to cancel/remove entrants from waiting list
 * - Sends notification to cancelled entrants
 *
 * This fragment extends BaseEntrantListFragment which provides:
 * - Lifecycle-aware profile loading
 * - Thread-safe data handling
 * - Common UI state management
 *
 * Related User Stories: US 02.02.01, US 02.02.02, US 02.06.02
 */
public class WaitingListFragment extends BaseEntrantListFragment
        implements EntrantAdapter.OnEntrantActionListener {

    private static final String TAG = "WaitingListFragment";

    private NotificationService notificationService;
    private Event currentEvent;

    /**
     * Factory method to create new instance
     *
     * @param eventId Event ID to display waiting list for
     * @return WaitingListFragment instance
     */
    public static WaitingListFragment newInstance(String eventId) {
        WaitingListFragment fragment = new WaitingListFragment();
        return (WaitingListFragment) BaseEntrantListFragment.newInstance(eventId, fragment);
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
        return Constants.SUBCOLLECTION_WAITING_LIST;
    }

    @Override
    protected boolean showJoinTime() {
        return true; // Show when entrants joined the waiting list
    }

    @Override
    protected boolean showStatus() {
        return false; // Waiting list doesn't have status
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
                .setTitle("Remove from Waiting List")
                .setMessage("Are you sure you want to remove " + entrantName +
                        " from the waiting list?\n\nThis will:\n• Remove them from the waiting list\n• Move them to the cancelled list\n• Send them a notification")
                .setPositiveButton("Remove", (dialog, which) -> {
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

        eventRepository.cancelWaitingListEntrant(
                eventId,
                profile.getUserId(),
                "Removed by organizer",
                new EventRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String id) {
                        Log.d(TAG, "Entrant removed from waiting list: " + id);

                        sendCancellationNotification(profile);

                        if (isAdded()) {
                            refresh();
                            Toast.makeText(getContext(),
                                    "Entrant removed from waiting list",
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                new EventRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        Log.e(TAG, "Failed to remove entrant", e);
                        if (isAdded()) {
                            showLoading(false);
                            Toast.makeText(getContext(),
                                    "Failed to remove entrant: " + e.getMessage(),
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
                "Removed from waiting list by organizer",
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