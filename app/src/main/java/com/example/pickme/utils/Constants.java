package com.example.pickme.utils;

/**
 * Constants - Application-wide constant values
 *
 * Centralized location for all constant strings, URIs, and configuration values
 * used throughout the application. This prevents duplication and makes maintenance easier.
 *
 * UPDATED: Added ACTION_OPEN_NOTIFICATIONS, ACTION_OPEN_EVENT, and EVENT_TYPES
 */
public final class Constants {

    // Prevent instantiation
    private Constants() {
        throw new AssertionError("Cannot instantiate Constants class");
    }

    // ==================== Deep Links ====================

    /**
     * Deep link scheme for event lottery QR codes
     * Format: eventlottery://event/{eventId}
     */
    public static final String DEEP_LINK_SCHEME = "eventlottery";

    /**
     * Deep link host for event links
     */
    public static final String DEEP_LINK_HOST_EVENT = "event";

    /**
     * Full deep link prefix for event QR codes
     * Format: eventlottery://event/
     */
    public static final String DEEP_LINK_PREFIX_EVENT = DEEP_LINK_SCHEME + "://" + DEEP_LINK_HOST_EVENT + "/";

    // ==================== Intent Extras ====================

    /**
     * Intent extra key for event ID
     */
    public static final String EXTRA_EVENT_ID = "event_id";

    /**
     * Intent extra key for user ID
     */
    public static final String EXTRA_USER_ID = "user_id";

    /**
     * Intent extra key for invitation ID
     */
    public static final String EXTRA_INVITATION_ID = "invitationId";

    /**
     * Intent extra key for invitation deadline
     */
    public static final String EXTRA_DEADLINE = "deadline";

    // ==================== Intent Actions ====================

    /**
     * Custom action for opening invitation dialog (lottery win)
     */
    public static final String ACTION_OPEN_INVITATION = "com.example.pickme.ACTION_OPEN_INVITATION";

    /**
     * Custom action for opening notifications/invitations screen
     */
    public static final String ACTION_OPEN_NOTIFICATIONS = "com.example.pickme.ACTION_OPEN_NOTIFICATIONS";

    /**
     * Custom action for opening event details
     */
    public static final String ACTION_OPEN_EVENT = "com.example.pickme.ACTION_OPEN_EVENT";

    // ==================== File Provider ====================

    /**
     * File provider authority suffix
     * Full authority: {packageName}.fileprovider
     */
    public static final String FILE_PROVIDER_AUTHORITY_SUFFIX = ".fileprovider";

    // ==================== Fragment Arguments ====================

    /**
     * Fragment argument key for event ID
     */
    public static final String ARG_EVENT_ID = "event_id";

    /**
     * Fragment argument key for tab type
     */
    public static final String ARG_TAB_TYPE = "tab_type";

    // ==================== Subcollection Names ====================

    /**
     * Firestore subcollection name for waiting list
     */
    public static final String SUBCOLLECTION_WAITING_LIST = "waitingList";

    /**
     * Firestore subcollection name for response pending list (selected entrants)
     */
    public static final String SUBCOLLECTION_RESPONSE_PENDING = "responsePendingList";

    /**
     * Firestore subcollection name for in-event list (confirmed entrants)
     */
    public static final String SUBCOLLECTION_IN_EVENT = "inEventList";

    /**
     * Firestore subcollection name for cancelled list
     */
    public static final String SUBCOLLECTION_CANCELLED = "cancelledList";

    // ==================== Event Types ====================

    /**
     * "All" option for event type filter (shows all events)
     */
    public static final String EVENT_TYPE_ALL = "All";

    /**
     * Event types for filtering in Browse Events
     */
    public static final String[] EVENT_TYPES = {
            "All",
            "General",
            "Music",
            "Sports",
            "Food & Drink",
            "Arts & Culture",
            "Technology",
            "Business",
            "Education",
            "Community",
            "Other"
    };
}