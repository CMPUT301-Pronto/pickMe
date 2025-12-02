package com.example.pickme.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;

import com.example.pickme.MainActivity;
import com.example.pickme.R;
import com.example.pickme.models.Profile;
import com.example.pickme.utils.Constants;
import com.example.pickme.utils.NotificationBadge;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * MyFirebaseMessagingService - Handles incoming FCM push notifications
 *
 * FIXED: Now uses proper Constants for actions and extras so MainActivity can handle them correctly
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    public static final String CHANNEL_LOTTERY = "lottery";
    public static final String CHANNEL_ORGANIZER = "organizer_messages";
    public static final String CHANNEL_SYSTEM = "system";

    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "New FCM token received");
        String userId = DeviceAuthenticator.getInstance(getApplicationContext()).getStoredUserId();
        if (userId != null && !userId.isEmpty()) {
            new com.example.pickme.repositories.ProfileRepository().setFcmToken(userId, token);
            Log.d(TAG, "FCM token saved for user: " + userId);
        }
    }

    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        Map<String, String> data = msg.getData();
        if (data == null || data.isEmpty()) return;

        Log.d(TAG, "FCM message received: " + data.get("type"));

        Profile profile = DeviceAuthenticator.getInstance(getApplicationContext()).getCachedProfile();
        if (profile != null && !profile.isNotificationEnabled()) {
            Log.d(TAG, "Notifications disabled - skipping");
            return;
        }

        String type = data.get("type");
        String eventId = data.get("eventId");
        String eventName = data.get("eventName");
        String message = data.get("message");

        if (type == null || eventName == null) return;

        createNotificationChannels();

        switch (type) {
            case "lottery_win":
                showLotteryWinNotification(eventId, eventName, message, data);
                break;
            case "lottery_loss":
                showLotteryLossNotification(eventId, eventName, message);
                break;
            case "replacement_draw":
                showReplacementNotification(eventId, eventName, message, data);
                break;
            case "organizer_message":
                showOrganizerMessageNotification(eventId, eventName, message);
                break;
            case "entrant_cancelled":
                showCancellationNotification(eventId, eventName, message);
                break;
            default:
                showGenericNotification(eventName, message);
        }
    }

    private void showLotteryWinNotification(String eventId, String eventName, String message, Map<String, String> data) {
        String deadline = data.get("invitationDeadline");

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Constants.ACTION_OPEN_INVITATION);
        intent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
        intent.putExtra(Constants.EXTRA_DEADLINE, deadline);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_LOTTERY)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("🎉 You've been selected!")
                .setContentText(eventName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message != null ? message : "Congratulations! You've been selected for " + eventName + ". Tap to respond."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(sound)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500});

        showNotification(builder.build());
        NotificationBadge.incrementPendingInvites(this);
    }

    private void showLotteryLossNotification(String eventId, String eventName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Constants.ACTION_OPEN_NOTIFICATIONS);
        intent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_LOTTERY)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("Lottery Results")
                .setContentText(eventName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message != null ? message : "You weren't selected for " + eventName + " this time."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        showNotification(builder.build());
    }

    private void showReplacementNotification(String eventId, String eventName, String message, Map<String, String> data) {
        String deadline = data.get("invitationDeadline");

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Constants.ACTION_OPEN_INVITATION);
        intent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
        intent.putExtra(Constants.EXTRA_DEADLINE, deadline);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_LOTTERY)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("🎊 Good News - Replacement Selected!")
                .setContentText(eventName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message != null ? message : "You've been selected as a replacement for " + eventName + "."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(sound)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500});

        showNotification(builder.build());
        NotificationBadge.incrementPendingInvites(this);
    }

    private void showOrganizerMessageNotification(String eventId, String eventName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Constants.ACTION_OPEN_NOTIFICATIONS);
        intent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ORGANIZER)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("Message from Organizer")
                .setContentText(eventName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message != null ? message : "You have a new message about " + eventName))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        showNotification(builder.build());
    }

    private void showCancellationNotification(String eventId, String eventName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Constants.ACTION_OPEN_NOTIFICATIONS);
        intent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_SYSTEM)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("Selection Cancelled")
                .setContentText(eventName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message != null ? message : "Your selection for " + eventName + " has been cancelled."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        showNotification(builder.build());
    }

    private void showGenericNotification(String eventName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction(Constants.ACTION_OPEN_NOTIFICATIONS);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_SYSTEM)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(eventName)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        showNotification(builder.build());
    }

    private void showNotification(android.app.Notification notification) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        nm.notify(notificationId, notification);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            NotificationChannel lotteryChannel = new NotificationChannel(
                    CHANNEL_LOTTERY, "Lottery & Invitations", NotificationManager.IMPORTANCE_HIGH);
            lotteryChannel.enableVibration(true);
            nm.createNotificationChannel(lotteryChannel);

            NotificationChannel organizerChannel = new NotificationChannel(
                    CHANNEL_ORGANIZER, "Organizer Messages", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(organizerChannel);

            NotificationChannel systemChannel = new NotificationChannel(
                    CHANNEL_SYSTEM, "System Notifications", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(systemChannel);
        }
    }
}