package com.example.pickme.ui.events;

import com.example.pickme.utils.Constants;

/**
 * CancelledEntrantsFragment - Display cancelled entrants
 *
 * Shows entrants who declined their invitation or were removed from the event.
 * Useful for organizers to track who opted out and potentially draw replacements.
 *
 * Does not show join time or status as these entrants are no longer participating.
 *
 * This fragment extends BaseEntrantListFragment for lifecycle-aware data loading.
 *
 * Related User Stories: US 02.06.03
 */
public class CancelledEntrantsFragment extends BaseEntrantListFragment {

    private static final String TAG = "CancelledFragment";

    /**
     * Factory method to create new instance
     *
     * @param eventId Event ID to display cancelled entrants for
     * @return CancelledEntrantsFragment instance
     */
    public static CancelledEntrantsFragment newInstance(String eventId) {
        CancelledEntrantsFragment fragment = new CancelledEntrantsFragment();
        return (CancelledEntrantsFragment) BaseEntrantListFragment.newInstance(eventId, fragment);
    }

    @Override
    protected String getSubcollectionName() {
        return Constants.SUBCOLLECTION_CANCELLED;
    }

    @Override
    protected boolean showJoinTime() {
        return false; // Don't show join time for cancelled list
    }

    @Override
    protected boolean showStatus() {
        return false; // Don't show status - all are cancelled
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }
}

