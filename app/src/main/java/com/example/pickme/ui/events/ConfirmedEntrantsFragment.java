package com.example.pickme.ui.events;

import com.example.pickme.utils.Constants;

/**
 * ConfirmedEntrantsFragment - Display confirmed entrants (inEventList)
 *
 * Shows entrants who have accepted their invitation and are confirmed
 * to attend the event. These are the final participants.
 *
 * Does not show join time or status as these entrants have completed
 * the registration process.
 *
 * This fragment extends BaseEntrantListFragment for lifecycle-aware data loading.
 *
 * Related User Stories: US 02.06.02, US 02.06.04
 */
public class ConfirmedEntrantsFragment extends BaseEntrantListFragment {

    private static final String TAG = "ConfirmedFragment";

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
}

