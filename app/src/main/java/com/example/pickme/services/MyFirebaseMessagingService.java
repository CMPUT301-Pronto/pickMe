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

public class MyFirebaseMessagingService extends FirebaseMessagingService {
    public static final String CH_INVITES = "invites";

    @Override public void onNewToken(@NonNull String token) {
        String userId = DeviceAuthenticator.getInstance(getApplicationContext()).getStoredUserId();
        if (userId != null && !userId.isEmpty()) {
            new com.example.pickme.repositories.ProfileRepository().setFcmToken(userId, token);
        }
    }

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
                .setSmallIcon(R.drawable.ic_stat_notification)
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

        // Badge
        NotificationBadge.incrementPendingInvites(this);
    }

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