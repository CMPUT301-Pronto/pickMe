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
import com.example.pickme.utils.NotificationBadge;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

/**
 * MyFirebaseMessagingService - Handles incoming FCM push notifications
 *
 * Receives data messages from Firebase Cloud Messaging and displays them as Android notifications.
 * Respects user notification preferences and routes different notification types appropriately.
 *
 * Notification Types Handled:
 * - lottery_win: User selected in lottery draw
 * - lottery_loss: User not selected in lottery draw
 * - replacement_draw: User selected as replacement
 * - organizer_message: Custom message from organizer
 * - entrant_cancelled: User cancelled by organizer
 *
 * Related User Stories:
 * - US 01.05.01: Receive notification when chosen (lottery win)
 * - US 01.05.02: Receive notification when not chosen (lottery loss)
 * - US 01.02.02: Opt out of notifications (preference check)
 * - US 02.07.01-03: Organizer notifications
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";

    // Notification channels
    public static final String CHANNEL_LOTTERY = "lottery";
    public static final String CHANNEL_ORGANIZER = "organizer_messages";
    public static final String CHANNEL_SYSTEM = "system";

    /**
     * Called when Firebase issues a new FCM token for this device.
     * Persists the token to user profile for server-side messaging.
     */
    @Override
    public void onNewToken(@NonNull String token) {
        Log.d(TAG, "New FCM token received");
        String userId = DeviceAuthenticator.getInstance(getApplicationContext()).getStoredUserId();
        if (userId != null && !userId.isEmpty()) {
            new com.example.pickme.repositories.ProfileRepository().setFcmToken(userId, token);
            Log.d(TAG, "FCM token saved for user: " + userId);
        } else {
            Log.w(TAG, "Cannot save FCM token - user not authenticated");
        }
    }

    /**
     * Handles incoming FCM data messages.
     * Checks user preferences, creates appropriate notification channels,
     * and displays notifications based on type.
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage msg) {
        Map<String, String> data = msg.getData();
        if (data == null || data.isEmpty()) {
            Log.w(TAG, "Received empty FCM message");
            return;
        }

        Log.d(TAG, "FCM message received: " + data.get("type"));

        // Check user notification preference
        Profile profile = DeviceAuthenticator.getInstance(getApplicationContext()).getCachedProfile();
        if (profile != null && !profile.isNotificationEnabled()) {
            Log.d(TAG, "Notifications disabled for user - skipping");
            return;
        }

        // Extract common fields
        String type = data.get("type");
        String eventId = data.get("eventId");
        String eventName = data.get("eventName");
        String message = data.get("message");

        if (type == null || eventName == null) {
            Log.w(TAG, "Missing required fields in FCM message");
            return;
        }

        // Create notification channels
        createNotificationChannels();

        // Handle different notification types
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
                Log.w(TAG, "Unknown notification type: " + type);
                showGenericNotification(eventName, message);
        }
    }

    /**
     * Show notification for lottery win (user selected)
     */
    private void showLotteryWinNotification(String eventId, String eventName, String message, Map<String, String> data) {
        String deadline = data.get("invitationDeadline");

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("com.example.pickme.ACTION_OPEN_INVITATION");
        intent.putExtra("eventId", eventId);
        intent.putExtra("deadline", deadline);
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

    /**
     * Show notification for lottery loss (user not selected)
     */
    private void showLotteryLossNotification(String eventId, String eventName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("eventId", eventId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_LOTTERY)
                .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle("Lottery Results")
                .setContentText(eventName)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(message != null ? message : "You weren't selected for " + eventName + " this time, but you may have another chance if spots open up."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent);

        showNotification(builder.build());
    }

    /**
     * Show notification for replacement draw (second chance)
     */
    private void showReplacementNotification(String eventId, String eventName, String message, Map<String, String> data) {
        String deadline = data.get("invitationDeadline");

        Intent intent = new Intent(this, MainActivity.class);
        intent.setAction("com.example.pickme.ACTION_OPEN_INVITATION");
        intent.putExtra("eventId", eventId);
        intent.putExtra("deadline", deadline);
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
                        .bigText(message != null ? message : "Good news! You've been selected as a replacement for " + eventName + ". Tap to respond."))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setSound(sound)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setVibrate(new long[]{0, 500, 200, 500});

        showNotification(builder.build());
        NotificationBadge.incrementPendingInvites(this);
    }

    /**
     * Show notification for organizer message
     */
    private void showOrganizerMessageNotification(String eventId, String eventName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("eventId", eventId);
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

    /**
     * Show notification for entrant cancellation
     */
    private void showCancellationNotification(String eventId, String eventName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra("eventId", eventId);
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

    /**
     * Show generic notification for unknown types
     */
    private void showGenericNotification(String eventName, String message) {
        Intent intent = new Intent(this, MainActivity.class);
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

    /**
     * Show notification using NotificationManager
     */
    private void showNotification(android.app.Notification notification) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        int notificationId = (int) (System.currentTimeMillis() & 0x7fffffff);
        nm.notify(notificationId, notification);
        Log.d(TAG, "Notification shown with ID: " + notificationId);
    }

    /**
     * Create notification channels for Android O+
     * Separate channels for different notification types
     */
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);

            // Lottery channel (high priority - invitations)
            NotificationChannel lotteryChannel = new NotificationChannel(
                    CHANNEL_LOTTERY,
                    "Lottery & Invitations",
                    NotificationManager.IMPORTANCE_HIGH
            );
            lotteryChannel.setDescription("Notifications when you're selected in a lottery or as a replacement");
            lotteryChannel.enableVibration(true);
            lotteryChannel.setVibrationPattern(new long[]{0, 500, 200, 500});
            nm.createNotificationChannel(lotteryChannel);

            // Organizer messages channel (default priority)
            NotificationChannel organizerChannel = new NotificationChannel(
                    CHANNEL_ORGANIZER,
                    "Organizer Messages",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            organizerChannel.setDescription("Messages from event organizers");
            nm.createNotificationChannel(organizerChannel);

            // System channel (default priority)
            NotificationChannel systemChannel = new NotificationChannel(
                    CHANNEL_SYSTEM,
                    "System Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            systemChannel.setDescription("System notifications and updates");
            nm.createNotificationChannel(systemChannel);

            Log.d(TAG, "Notification channels created");
        }
    }
}

