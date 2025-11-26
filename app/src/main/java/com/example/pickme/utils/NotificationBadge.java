package com.example.pickme.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;

/**
 * JAVADOCS LLM GENERATED
 *
 * Utility class for managing the in-app "pending invites" badge count.
 *
 * <p><b>Role / Pattern:</b> Stateless utility using {@link SharedPreferences} for persistence
 * and {@link Intent} broadcasts for notifying the UI of badge count changes.
 * This supports a reactive pattern without requiring a centralized service.</p>
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Store and retrieve the number of pending invitations.</li>
 *   <li>Increment or clear the badge count when new invites arrive or are handled.</li>
 *   <li>Broadcast updates so UI components (e.g. toolbar badge) can refresh in real time.</li>
 * </ul>
 * </p>
 *
 * <p><b>Usage Example:</b>
 * <pre>{@code
 * int pending = NotificationBadge.getPendingInvites(context);
 * NotificationBadge.incrementPendingInvites(context);
 * NotificationBadge.clearPendingInvites(context);
 * }</pre>
 * </p>
 *
 * <p><b>Broadcast Contract:</b>
 * <ul>
 *   <li>Action: {@link #ACTION_BADGE_CHANGED}</li>
 *   <li>Extra: {@link #EXTRA_INVITES} (int)</li>
 * </ul>
 * Example receiver:
 * <pre>{@code
 * <receiver android:name=".receivers.BadgeReceiver">
 *   <intent-filter>
 *     <action android:name="com.example.pickme.BADGE_CHANGED" />
 *   </intent-filter>
 * </receiver>
 * }</pre>
 * </p>
 *
 * <p><b>Storage:</b> Shared in {@code SharedPreferences} file {@code pickme_badges}.</p>
 */
public final class NotificationBadge {
    /** SharedPreferences file name. */
    private static final String PREFS = "pickme_badges";

    /** Key for pending invite count. */
    private static final String KEY_INVITES = "pending_invites_count";

    /** Broadcast action sent when badge count changes. */
    public static final String ACTION_BADGE_CHANGED = "com.example.pickme.BADGE_CHANGED";

    /** Intent extra containing the new invite count. */
    public static final String EXTRA_INVITES = "invites";

    /** Private constructor to prevent instantiation (static utility). */
    private NotificationBadge() {}

    /**
     * Retrieves the shared preferences instance for badge data.
     *
     * @param ctx app or activity context.
     * @return SharedPreferences for badge storage.
     */
    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Gets the number of pending invites currently stored.
     *
     * @param ctx app context.
     * @return current pending invite count (0 if none).
     */
    public static int getPendingInvites(Context ctx) {
        return prefs(ctx).getInt(KEY_INVITES, 0);
    }
    /**
     * Sets the pending invite count to a specific value.
     * Also broadcasts {@link #ACTION_BADGE_CHANGED} to update any UI observers.
     *
     * @param ctx   app context.
     * @param value new badge count.
     */
    public static void setPendingInvites(Context ctx, int value) {
        prefs(ctx).edit().putInt(KEY_INVITES, value).apply();
        broadcast(ctx, value);
    }

    /**
     * Increments the pending invite badge count by one
     * and notifies listeners.
     *
     * @param ctx app context.
     */
    public static void incrementPendingInvites(Context ctx) {
        int v = getPendingInvites(ctx) + 1;
        setPendingInvites(ctx, v);
    }
    /**
     * Clears the badge count (sets to zero) and notifies listeners.
     *
     * @param ctx app context.
     */
    public static void clearPendingInvites(Context ctx) {
        setPendingInvites(ctx, 0);
    }

    /**
     * Sends a broadcast intent notifying that the badge count has changed.
     *
     * @param ctx   app context.
     * @param value new badge count.
     */
    private static void broadcast(Context ctx, int value) {
        Intent i = new Intent(ACTION_BADGE_CHANGED);
        i.putExtra(EXTRA_INVITES, value);
        ctx.sendBroadcast(i);
    }
}