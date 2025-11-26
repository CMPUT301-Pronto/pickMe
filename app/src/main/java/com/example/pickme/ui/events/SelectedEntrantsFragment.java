package com.example.pickme.ui.events;

import com.example.pickme.utils.Constants;

/**
 * SelectedEntrantsFragment - Display selected entrants (responsePendingList)
 *
 * Shows entrants who have been selected in the lottery draw and are awaiting
 * response (pending acceptance/decline of invitation).
 *
 * Displays status indicators to show whether entrants have:
 * - Not yet responded
 * - Accepted invitation
 * - Declined invitation
 *
 * This fragment extends BaseEntrantListFragment for lifecycle-aware data loading.
 *
 * Related User Stories: US 02.06.01
 */
public class SelectedEntrantsFragment extends BaseEntrantListFragment {

    private static final String TAG = "SelectedFragment";

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
}

