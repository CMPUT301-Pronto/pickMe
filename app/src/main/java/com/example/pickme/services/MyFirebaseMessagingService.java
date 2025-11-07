package com.example.pickme.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.pickme.MainActivity;
import com.example.pickme.R;
import com.example.pickme.models.Profile;
import com.example.pickme.utils.NotificationBadge;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;
/**
 * JAVADOCS LLM GENERATED
 *
 * FCM entry point for the app. Receives data messages and surfaces them
 * as Android notifications, respecting the user's notification preferences.
 *
 * <p><b>Role / Pattern:</b> Android Service (bound to the app process) that
 * handles Firebase Cloud Messaging callbacks. It decodes data payloads,
 * routes deep links into the app (invitation dialog), and manages the
 * notifications channel on Android O+.</p>
 *
 * <p><b>Behavior:</b>
 * <ul>
 *   <li>Persists refreshed FCM tokens via {@code ProfileRepository} in {@link #onNewToken(String)}.</li>
 *   <li>Builds a high-priority notification for invitation events in {@link #onMessageReceived(RemoteMessage)}.</li>
 *   <li>Increments an in-app badge counter through {@link NotificationBadge}.</li>
 * </ul>
 * </p>
 *
 * <p><b>Data contract (expected message keys):</b>
 * {@code eventId}, {@code eventName}, {@code invitationId}, {@code invitationDeadline}.
 * The device respects {@code Profile.notificationEnabled} before showing UI.</p>
 *
 * <p><b>Outstanding / Notes:</b>
 * <ul>
 *   <li>Assumes a small icon resource {@code R.drawable.ic_stat_notification} exists.</li>
 *   <li>Relies on {@code DeviceAuthenticator} to provide the cached {@link Profile} and userId.</li>
 *   <li>Notification tap path deep-links into {@link MainActivity} with
 *       action {@code ACTION_OPEN_INVITATION} where the UI presents the dialog.</li>
 * </ul>
 * </p>
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    /** Notification channel id for invitation-related pushes. */
    public static final String CH_INVITES = "invites";
    /**
     * Called when Firebase rotates or issues a new registration token for this device.
     * Persists the token if we already know the authenticated userId on-device.
     *
     * @param token the new FCM registration token.
     */
    @Override public void onNewToken(@NonNull String token) {
        String userId = DeviceAuthenticator.getInstance(getApplicationContext()).getStoredUserId();
        if (userId != null && !userId.isEmpty()) {
            new com.example.pickme.repositories.ProfileRepository().setFcmToken(userId, token);
        }
    }
    /**
     * Handles incoming FCM data messages.
     * <ol>
     *   <li>Checks the user's {@code notificationEnabled} preference.</li>
     *   <li>Extracts event and invitation metadata from the payload.</li>
     *   <li>Creates the invites channel (O+) and shows a high-priority notification.</li>
     *   <li>Deep-links the tap action into {@link MainActivity} which opens the invite dialog.</li>
     * </ol>
     *
     * @param msg the received {@link RemoteMessage} (data message expected).
     */
    @Override public void onMessageReceived(@NonNull RemoteMessage msg) {
        Map<String,String> d = msg.getData();
        if (d == null || d.isEmpty()) return;

        // Respect user setting
        Profile p = DeviceAuthenticator.getInstance(getApplicationContext()).getCachedProfile();
        if (p != null && !p.isNotificationEnabled()) return;

        String eventId   = d.get("eventId");
        String eventName = d.get("eventName");
        String invId     = d.get("invitationId");
        String deadline  = d.get("invitationDeadline");

        // Deep-link to open the invitation flow in the main activity
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("com.example.pickme.ACTION_OPEN_INVITATION");
        intent.putExtra("eventId", eventId);
        intent.putExtra("invitationId", invId);
        intent.putExtra("deadline", deadline);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pi = PendingIntent.getActivity(
                this, 1001, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        createChannel();
        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(this, CH_INVITES)
                .setSmallIcon(R.drawable.ic_stat_notification) // require small icon
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.invite_notification_body, eventName))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(getString(R.string.invite_notification_body, eventName)))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(sound)
                .setAutoCancel(true)
                .setContentIntent(pi);

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify((int)(System.currentTimeMillis() & 0x7fffffff), nb.build());

        // Update in app Badge for pending invitations
        NotificationBadge.incrementPendingInvites(this);
    }

    /**
     * Creates the notification channel used for invitation notifications on Android O+.
     * Safe to call repeatedly; the platform ignores duplicates.
     */
    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                    CH_INVITES,
                    getString(R.string.channel_invites_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            ch.setDescription(getString(R.string.channel_invites_desc));
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }
}