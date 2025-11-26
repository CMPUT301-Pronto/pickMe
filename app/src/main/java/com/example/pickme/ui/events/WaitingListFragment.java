package com.example.pickme.ui.events;

import com.example.pickme.utils.Constants;

/**
 * WaitingListFragment - Display waiting list entrants
 *
 * Shows all entrants who joined the waiting list for an event.
 * Displays join timestamps to show when each entrant registered.
 *
 * This fragment extends BaseEntrantListFragment which provides:
 * - Lifecycle-aware profile loading
 * - Thread-safe data handling
 * - Common UI state management
 *
 * Related User Stories: US 02.02.01, US 02.02.02
 */
public class WaitingListFragment extends BaseEntrantListFragment {

    private static final String TAG = "WaitingListFragment";

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
}



