package com.example.pickme.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;

public final class NotificationBadge {
    private static final String PREFS = "pickme_badges";
    private static final String KEY_INVITES = "pending_invites_count";
    public static final String ACTION_BADGE_CHANGED = "com.example.pickme.BADGE_CHANGED";
    public static final String EXTRA_INVITES = "invites";

    private NotificationBadge() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static int getPendingInvites(Context ctx) {
        return prefs(ctx).getInt(KEY_INVITES, 0);
    }

    public static void setPendingInvites(Context ctx, int value) {
        prefs(ctx).edit().putInt(KEY_INVITES, value).apply();
        broadcast(ctx, value);
    }

    public static void incrementPendingInvites(Context ctx) {
        int v = getPendingInvites(ctx) + 1;
        setPendingInvites(ctx, v);
    }

    public static void clearPendingInvites(Context ctx) {
        setPendingInvites(ctx, 0);
    }

    private static void broadcast(Context ctx, int value) {
        Intent i = new Intent(ACTION_BADGE_CHANGED);
        i.putExtra(EXTRA_INVITES, value);
        ctx.sendBroadcast(i);
    }
}