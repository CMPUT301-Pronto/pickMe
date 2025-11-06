package com.example.pickme.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.models.Event;
import com.example.pickme.models.NotificationLog;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.ProfileRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.RemoteMessage;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NotificationService - Handles all FCM notifications
 *
 * Manages notification sending with:
 * - User preference checking (notificationEnabled)
 * - Notification logging for admin review (US 03.08.01)
 * - Firebase Cloud Messaging integration
 * - Batch sending support
 *
 * Notification Types:
 * - lottery_win: Selected in lottery
 * - lottery_loss: Not selected
 * - organizer_message: Custom broadcast
 * - replacement_draw: Selected as replacement
 *
 * Related User Stories: US 01.04.01, US 01.04.02, US 01.04.03, US 02.05.01,
 *                       US 02.07.01-03, US 03.08.01
 */
public class NotificationService {

    private static final String TAG = "NotificationService";
    private static final String COLLECTION_NOTIFICATION_LOGS = "notification_logs";
    private static final String COLLECTION_EVENTS = "events";
    private static final String SUBCOLLECTION_WAITING_LIST = "waitingList";
    private static final String SUBCOLLECTION_RESPONSE_PENDING = "responsePendingList";
    private static final String SUBCOLLECTION_IN_EVENT = "inEventList";

    private static NotificationService instance;
    private FirebaseFirestore db;
    private FirebaseMessaging fcm;
    private ProfileRepository profileRepository;

    private NotificationService() {
        this.db = FirebaseManager.getFirestore();
        this.fcm = FirebaseManager.getMessaging();
        this.profileRepository = new ProfileRepository();
    }

    public static synchronized NotificationService getInstance() {
        if (instance == null) {
            instance = new NotificationService();
        }
        return instance;
    }

    /**
     * Send lottery win notification to selected entrants
     *
     * @param entrantIds List of winner IDs
     * @param event Event object
     * @param listener Callback
     */
    public void sendLotteryWinNotification(@NonNull List<String> entrantIds,
                                          @NonNull Event event,
                                          @NonNull OnNotificationSentListener listener) {
        String message = "Congratulations! You've been selected for " + event.getName() +
                        ". Please respond to confirm your participation.";

        sendNotifications(entrantIds, message, "lottery_win", event, listener);
    }

    /**
     * Send lottery loss notification to non-selected entrants
     *
     * @param entrantIds List of loser IDs
     * @param event Event object
     * @param listener Callback
     */
    public void sendLotteryLossNotification(@NonNull List<String> entrantIds,
                                           @NonNull Event event,
                                           @NonNull OnNotificationSentListener listener) {
        String message = "Unfortunately, you weren't selected for " + event.getName() +
                        ", but you may have another chance if spots become available.";

        sendNotifications(entrantIds, message, "lottery_loss", event, listener);
    }

    /**
     * Send replacement draw notification
     *
     * @param entrantIds List of replacement winners
     * @param event Event object
     * @param listener Callback
     */
    public void sendReplacementDrawNotification(@NonNull List<String> entrantIds,
                                               @NonNull Event event,
                                               @NonNull OnNotificationSentListener listener) {
        String message = "Good news! You've been selected as a replacement for " + event.getName() +
                        ". Please respond to confirm your participation.";

        sendNotifications(entrantIds, message, "replacement_draw", event, listener);
    }

    /**
     * Send custom organizer message to specific entrants
     *
     * @param entrantIds List of recipient IDs
     * @param message Custom message content
     * @param event Event object
     * @param listener Callback
     */
    public void sendOrganizerMessage(@NonNull List<String> entrantIds,
                                    @NonNull String message,
                                    @NonNull Event event,
                                    @NonNull OnNotificationSentListener listener) {
        sendNotifications(entrantIds, message, "organizer_message", event, listener);
    }

    /**
     * Send message to all waiting list entrants
     *
     * @param eventId Event ID
     * @param message Message content
     * @param listener Callback
     */
    public void sendToAllWaitingList(@NonNull String eventId,
                                     @NonNull String message,
                                     @NonNull OnNotificationSentListener listener) {
        getEntrantIdsFromSubcollection(eventId, SUBCOLLECTION_WAITING_LIST, new OnEntrantIdsLoadedListener() {
            @Override
            public void onEntrantIdsLoaded(List<String> entrantIds, Event event) {
                sendNotifications(entrantIds, message, "organizer_message", event, listener);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    /**
     * Send message to all selected entrants (response pending)
     *
     * @param eventId Event ID
     * @param message Message content
     * @param listener Callback
     */
    public void sendToAllSelected(@NonNull String eventId,
                                  @NonNull String message,
                                  @NonNull OnNotificationSentListener listener) {
        getEntrantIdsFromSubcollection(eventId, SUBCOLLECTION_RESPONSE_PENDING, new OnEntrantIdsLoadedListener() {
            @Override
            public void onEntrantIdsLoaded(List<String> entrantIds, Event event) {
                sendNotifications(entrantIds, message, "organizer_message", event, listener);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    /**
     * Send message to all confirmed participants
     *
     * @param eventId Event ID
     * @param message Message content
     * @param listener Callback
     */
    public void sendToAllConfirmed(@NonNull String eventId,
                                   @NonNull String message,
                                   @NonNull OnNotificationSentListener listener) {
        getEntrantIdsFromSubcollection(eventId, SUBCOLLECTION_IN_EVENT, new OnEntrantIdsLoadedListener() {
            @Override
            public void onEntrantIdsLoaded(List<String> entrantIds, Event event) {
                sendNotifications(entrantIds, message, "organizer_message", event, listener);
            }

            @Override
            public void onError(Exception e) {
                listener.onError(e);
            }
        });
    }

    /**
     * Core notification sending logic with preference checking and logging
     */
    private void sendNotifications(@NonNull List<String> entrantIds,
                                   @NonNull String message,
                                   @NonNull String notificationType,
                                   @NonNull Event event,
                                   @NonNull OnNotificationSentListener listener) {
        if (entrantIds.isEmpty()) {
            listener.onNotificationSent(0);
            return;
        }

        Log.d(TAG, "Sending " + notificationType + " to " + entrantIds.size() + " entrants");

        // Filter recipients based on notification preferences
        filterEnabledRecipients(entrantIds, new OnRecipientsFilteredListener() {
            @Override
            public void onRecipientsFiltered(List<String> enabledRecipients) {
                if (enabledRecipients.isEmpty()) {
                    Log.w(TAG, "No recipients have notifications enabled");
                    listener.onNotificationSent(0);
                    return;
                }

                // Create notification log
                NotificationLog notificationLog = new NotificationLog(
                        db.collection(COLLECTION_NOTIFICATION_LOGS).document().getId(),
                        System.currentTimeMillis(),
                        event.getOrganizerId(),
                        enabledRecipients,
                        message,
                        notificationType,
                        event.getEventId()
                );

                // Save log to Firestore
                db.collection(COLLECTION_NOTIFICATION_LOGS)
                        .document(notificationLog.getNotificationId())
                        .set(notificationLog.toMap())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Notification log created: " + notificationLog.getNotificationId());

                            // Send FCM messages
                            sendFCMMessages(enabledRecipients, message, event, notificationType, listener);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Failed to create notification log", e);
                            // Still try to send notifications even if logging fails
                            sendFCMMessages(enabledRecipients, message, event, notificationType, listener);
                        });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to filter recipients", e);
                listener.onError(e);
            }
        });
    }

    /**
     * Filter recipients based on notificationEnabled preference
     */
    private void filterEnabledRecipients(@NonNull List<String> entrantIds,
                                         @NonNull OnRecipientsFilteredListener listener) {
        List<String> enabledRecipients = new ArrayList<>();
        int[] processedCount = {0};

        for (String entrantId : entrantIds) {
            profileRepository.getProfile(entrantId, new ProfileRepository.OnProfileLoadedListener() {
                @Override
                public void onProfileLoaded(Profile profile) {
                    synchronized (processedCount) {
                        if (profile.isNotificationEnabled()) {
                            enabledRecipients.add(entrantId);
                        }

                        processedCount[0]++;
                        if (processedCount[0] == entrantIds.size()) {
                            listener.onRecipientsFiltered(enabledRecipients);
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    synchronized (processedCount) {
                        Log.w(TAG, "Failed to get profile for: " + entrantId, e);
                        processedCount[0]++;
                        if (processedCount[0] == entrantIds.size()) {
                            listener.onRecipientsFiltered(enabledRecipients);
                        }
                    }
                }
            });
        }
    }

    /**
     * Send FCM messages to recipients
     *
     * Note: In production, use FCM HTTP API or Admin SDK for batch sending
     * This implementation shows the structure for notification payloads
     */


    /**
     * Get entrant IDs from event subcollection
     */
    private void getEntrantIdsFromSubcollection(@NonNull String eventId,
                                               @NonNull String subcollection,
                                               @NonNull OnEntrantIdsLoadedListener listener) {
        // First get the event
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .get()
                .addOnSuccessListener(eventDoc -> {
                    if (!eventDoc.exists()) {
                        listener.onError(new Exception("Event not found"));
                        return;
                    }

                    Event event = eventDoc.toObject(Event.class);

                    // Then get entrants from subcollection
                    db.collection(COLLECTION_EVENTS)
                            .document(eventId)
                            .collection(subcollection)
                            .get()
                            .addOnSuccessListener(querySnapshot -> {
                                List<String> entrantIds = new ArrayList<>();
                                for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                    entrantIds.add(doc.getId());
                                }

                                Log.d(TAG, "Retrieved " + entrantIds.size() + " entrants from " + subcollection);
                                listener.onEntrantIdsLoaded(entrantIds, event);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to get entrants from " + subcollection, e);
                                listener.onError(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get event", e);
                    listener.onError(e);
                });
    }

    /**
     * Callback for notification sent
     */
    public interface OnNotificationSentListener {
        void onNotificationSent(int sentCount);
        void onError(Exception e);
    }

    /**
     * Internal callback for recipient filtering
     */
    private interface OnRecipientsFilteredListener {
        void onRecipientsFiltered(List<String> enabledRecipients);
        void onError(Exception e);
    }

    /**
     * Internal callback for loading entrant IDs
     */
    private interface OnEntrantIdsLoadedListener {
        void onEntrantIdsLoaded(List<String> entrantIds, Event event);
        void onError(Exception e);
    }

    private void sendFCMMessages(@NonNull List<String> recipientIds,
                                 @NonNull String messageBody,
                                 @NonNull Event event,
                                 @NonNull String notificationType,
                                 @NonNull OnNotificationSentListener listener) {

        // Data payload consumed on-device by MyFirebaseMessagingService
        Map<String, Object> data = new HashMap<>();
        data.put("type", notificationType);
        data.put("eventId", event.getEventId());
        data.put("eventName", event.getName());
        data.put("message", messageBody);
        // include invitation info if you have it (invitationId/deadline)
        if (event.getInvitationDeadlineMillis() != 0L) {
            data.put("invitationDeadline", String.valueOf(event.getInvitationDeadlineMillis()));
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("userIds", recipientIds);
        payload.put("data", data);

        FirebaseFunctions.getInstance()
                .getHttpsCallable("sendBatchNotifications")
                .call(payload)
                .addOnSuccessListener((HttpsCallableResult r) -> {
                    Object res = r.getData();
                    // optional: parse {sent:n}
                    listener.onNotificationSent(recipientIds.size());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "sendBatchNotifications failed", e);
                    listener.onError(e);
                });
    }
}

