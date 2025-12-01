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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JAVADOCS LLM GENERATED
 *
 * Centralized service for preparing and dispatching push notifications via
 * Firebase Cloud Messaging (FCM), with user-preference checks and audit logging.
 *
 * <p><b>Role / Pattern:</b> Process-wide singleton (Service Object) that
 * orchestrates notification fan-out to entrants for lottery outcomes or
 * organizer messages. It encapsulates:
 * <ul>
 *   <li>Preference filtering (respects {@code Profile.notificationEnabled}).</li>
 *   <li>Audit logging to Firestore (US 03.08.01).</li>
 *   <li>Delegation to a Cloud Function for batch sends (scalable dispatch).</li>
 * </ul>
 * </p>
 *
 * <p><b>Supported Types:</b> {@code lottery_win}, {@code lottery_loss},
 * {@code replacement_draw}, {@code organizer_message}.</p>
 *
 * <p><b>Data Flow:</b>
 * <ol>
 *   <li>Caller provides recipient userIds and an {@link Event}.</li>
 *   <li>Service filters recipients by profile preferences.</li>
 *   <li>Creates a {@link NotificationLog} document in Firestore.</li>
 *   <li>Invokes HTTPS callable Cloud Function {@code sendBatchNotifications} with
 *       userIds and a data payload. Device side handling is performed by
 *       {@code MyFirebaseMessagingService}.</li>
 * </ol>
 * </p>
 *
 * <p><b>Outstanding / Notes:</b>
 * <ul>
 *   <li>Relies on a deployed Cloud Function named {@code sendBatchNotifications} that
 *       accepts {@code { userIds: string[], data: object }} and returns success metadata.</li>
 *   <li>{@link Event} optionally exposes an invitation deadline via
 *       {@code getInvitationDeadlineMillis()}—ensure the model is populated when needed.</li>
 *   <li>If audit log creation fails, dispatch still proceeds (best-effort logging).</li>
 * </ul>
 * </p>
 */
public class NotificationService {

    private static final String TAG = "NotificationService";
    // Firestore collections / subcollections
    private static final String COLLECTION_NOTIFICATION_LOGS = "notification_logs";
    private static final String COLLECTION_EVENTS = "events";
    private static final String SUBCOLLECTION_WAITING_LIST = "waitingList";
    private static final String SUBCOLLECTION_RESPONSE_PENDING = "responsePendingList";
    private static final String SUBCOLLECTION_IN_EVENT = "inEventList";
    private static final String SUBCOLLECTION_CANCELLED = "cancelledList";

    private static NotificationService instance;
    private FirebaseFirestore db;
    private FirebaseMessaging fcm;
    private ProfileRepository profileRepository;

    private NotificationService() {
        this.db = FirebaseManager.getFirestore();
        this.fcm = FirebaseManager.getMessaging();
        this.profileRepository = new ProfileRepository();
    }
    /**
     * Private constructor; use {@link #getInstance()}.
     * Wires Firebase clients via {@link FirebaseManager}.
     */
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
     * Send message to all cancelled entrants
     * Related User Stories: US 02.07.03
     *
     * @param eventId Event ID
     * @param message Message content
     * @param listener Callback
     */
    public void sendToAllCancelled(@NonNull String eventId,
                                   @NonNull String message,
                                   @NonNull OnNotificationSentListener listener) {
        getEntrantIdsFromSubcollection(eventId, SUBCOLLECTION_CANCELLED, new OnEntrantIdsLoadedListener() {
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
     *
     *      Orchestrates preference filtering, audit logging, and invocation of the Cloud Function
     *      to fan-out FCM messages to recipients.
     *      @param entrantIds       candidate recipients (unfiltered).
     *      @param message          user-visible message body.
     *      @param notificationType semantic type (e.g., {@code lottery_win}).
     *      @param event            event context used in payload and logging.
     *      @param listener         completion/error callback.
     *
     */
    private void sendNotifications(@NonNull List<String> entrantIds,
                                   @NonNull String message,
                                   @NonNull String notificationType,
                                   @NonNull Event event,
                                   @NonNull OnNotificationSentListener listener) {
        Log.d(TAG, "sendNotifications called with " + entrantIds.size() + " entrant IDs for event: " + event.getName());

        if (entrantIds.isEmpty()) {
            Log.w(TAG, "No entrants provided to sendNotifications");
            listener.onNotificationSent(0);
            return;
        }

        Log.d(TAG, "Sending " + notificationType + " to " + entrantIds.size() + " entrants");
        Log.d(TAG, "Entrant IDs: " + entrantIds.toString());

        // Filter recipients based on notification preferences
        filterEnabledRecipients(entrantIds, new OnRecipientsFilteredListener() {
            @Override
            public void onRecipientsFiltered(List<String> enabledRecipients) {
                Log.d(TAG, "After preference filtering: " + enabledRecipients.size() + " recipients enabled");

                if (enabledRecipients.isEmpty()) {
                    Log.w(TAG, "No recipients have notifications enabled (all " + entrantIds.size() + " opted out)");
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

                Log.d(TAG, "Creating notification log: " + notificationLog.getNotificationId());

                // Save log to Firestore
                db.collection(COLLECTION_NOTIFICATION_LOGS)
                        .document(notificationLog.getNotificationId())
                        .set(notificationLog.toMap())
                        .addOnSuccessListener(aVoid -> {
                            Log.d(TAG, "Notification log created successfully");

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
     * Filters a set of candidate userIds to those with notifications enabled,
     * by loading each user's {@link Profile} and checking {@code notificationEnabled}.
     *
     * @param entrantIds candidate userIds.
     * @param listener   callback with filtered list or error.
     */
    private void filterEnabledRecipients(@NonNull List<String> entrantIds,
                                         @NonNull OnRecipientsFilteredListener listener) {
        Log.d(TAG, "Filtering " + entrantIds.size() + " recipients by notification preferences");

        List<String> enabledRecipients = new ArrayList<>();
        int[] processedCount = {0};
        int[] skippedCount = {0};

        for (String entrantId : entrantIds) {
            profileRepository.getProfile(entrantId, new ProfileRepository.OnProfileLoadedListener() {
                @Override
                public void onProfileLoaded(Profile profile) {
                    synchronized (processedCount) {
                        if (profile.isNotificationEnabled()) {
                            enabledRecipients.add(entrantId);
                            Log.d(TAG, "User " + entrantId + " has notifications enabled");
                        } else {
                            skippedCount[0]++;
                            Log.d(TAG, "User " + entrantId + " has notifications disabled - skipping");
                        }

                        processedCount[0]++;
                        if (processedCount[0] == entrantIds.size()) {
                            Log.d(TAG, "Preference filtering complete: " + enabledRecipients.size() + " enabled, " + skippedCount[0] + " disabled");
                            listener.onRecipientsFiltered(enabledRecipients);
                        }
                    }
                }

                @Override
                public void onError(Exception e) {
                    synchronized (processedCount) {
                        Log.w(TAG, "Failed to get profile for: " + entrantId + " - excluding from recipients", e);
                        processedCount[0]++;
                        if (processedCount[0] == entrantIds.size()) {
                            Log.d(TAG, "Preference filtering complete (with errors): " + enabledRecipients.size() + " enabled");
                            listener.onRecipientsFiltered(enabledRecipients);
                        }
                    }
                }
            });
        }
    }

    // ------------------- Firestore helpers -------------------

    /**
     * Loads all entrant document IDs from a given event subcollection (e.g., waitingList,
     * responsePendingList, inEventList) and returns them along with the parent {@link Event}.
     *
     * @param eventId       parent event id.
     * @param subcollection one of the {@code SUBCOLLECTION_*} constants.
     * @param listener      callback with entrantIds and event or error.
     */
    private void getEntrantIdsFromSubcollection(@NonNull String eventId,
                                               @NonNull String subcollection,
                                               @NonNull OnEntrantIdsLoadedListener listener) {
        Log.d(TAG, "Fetching entrants from subcollection: " + subcollection + " for event: " + eventId);

        // First get the event
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .get()
                .addOnSuccessListener(eventDoc -> {
                    if (!eventDoc.exists()) {
                        Log.e(TAG, "Event not found: " + eventId);
                        listener.onError(new Exception("Event not found"));
                        return;
                    }

                    Event event = eventDoc.toObject(Event.class);
                    Log.d(TAG, "Event loaded: " + (event != null ? event.getName() : "null"));

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
                                if (entrantIds.isEmpty()) {
                                    Log.w(TAG, "Subcollection " + subcollection + " is empty for event " + eventId);
                                } else {
                                    Log.d(TAG, "Entrant IDs from " + subcollection + ": " + entrantIds.toString());
                                }

                                listener.onEntrantIdsLoaded(entrantIds, event);
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to get entrants from " + subcollection + " for event " + eventId, e);
                                listener.onError(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get event: " + eventId, e);
                    listener.onError(e);
                });
    }

    // ------------------- Callbacks -------------------

    /**
     * Callback for notification send operations.
     */
    public interface OnNotificationSentListener {
        /**
         * Called when messages were (attempted to be) sent.
         * @param sentCount number of intended recipients successfully handed off to dispatch.
         */
        void onNotificationSent(int sentCount);
        /**
         * Called when an error occurred prior to, or during, dispatch.
         * (e.g., preference filtering, logging, or Cloud Function failure)
         * @param e the underlying error.
         */
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
    // ------------------- Dispatch (Cloud Function) -------------------

    /**
     * Dispatches a data payload to a set of recipient userIds by calling the HTTPS Cloud Function
     * {@code sendBatchNotifications}. This function is expected to resolve userIds to FCM tokens
     * server-side and send the push messages.
     *
     * <p>The device-side display behavior is handled in {@code MyFirebaseMessagingService}.</p>
     *
     * @param recipientIds    filtered recipient userIds.
     * @param messageBody     user-visible body text.
     * @param event           event context for payload (id/name; optional deadline).
     * @param notificationType semantic type string.
     * @param listener        completion/error callback.
     */
    private void sendFCMMessages(@NonNull List<String> recipientIds,
                                 @NonNull String messageBody,
                                 @NonNull Event event,
                                 @NonNull String notificationType,
                                 @NonNull OnNotificationSentListener listener) {
        Log.d(TAG, "sendFCMMessages called for " + recipientIds.size() + " recipients");
        Log.d(TAG, "Recipient IDs before copy: " + recipientIds.toString());

        // Create a completely fresh ArrayList by copying each element individually
        ArrayList<String> freshUserIds = new ArrayList<>();
        for (String id : recipientIds) {
            freshUserIds.add(id);
        }

        HashMap<String, Object> messageData = new HashMap<>();
        messageData.put("type", notificationType);
        messageData.put("eventId", event.getEventId());
        messageData.put("eventName", event.getName());
        messageData.put("message", messageBody);
        if (event.getInvitationDeadlineMillis() != 0L) {
            messageData.put("invitationDeadline", String.valueOf(event.getInvitationDeadlineMillis()));
        }

        HashMap<String, Object> requestPayload = new HashMap<>();
        requestPayload.put("userIds", freshUserIds);
        requestPayload.put("data", messageData);

        Log.d(TAG, "Calling Cloud Function sendBatchNotifications with payload: " + requestPayload);
        Log.d(TAG, "userIds class: " + freshUserIds.getClass().getName());
        Log.d(TAG, "userIds size: " + freshUserIds.size());
        Log.d(TAG, "userIds contents: " + freshUserIds.toString());
        Log.d(TAG, "data class: " + messageData.getClass().getName());
        Log.d(TAG, "data contents: " + messageData.toString());

        FirebaseFunctions.getInstance()
                .getHttpsCallable("sendBatchNotifications")
                .call(requestPayload)
                .addOnSuccessListener(result -> {
                    Log.d(TAG, "Cloud Function succeeded for " + recipientIds.size() + " recipients");
                    listener.onNotificationSent(recipientIds.size());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Cloud Function failed", e);
                    listener.onError(e);
                });
    }

    // ============================================================
// ADD THIS METHOD TO NotificationService.java
//
// Location: Add after the sendOrganizerMessage method
// (around line 156 in your current file)
// ============================================================

    /**
     * Send cancellation notification to an entrant who was cancelled by the organizer
     *
     * @param entrantId ID of the cancelled entrant
     * @param event Event object
     * @param reason Optional reason for cancellation (can be null)
     * @param listener Callback
     *
     * Related User Stories: US 02.06.02
     */
    public void sendCancellationNotification(@NonNull String entrantId,
                                             @NonNull Event event,
                                             String reason,
                                             @NonNull OnNotificationSentListener listener) {
        String message;
        if (reason != null && !reason.isEmpty()) {
            message = "Your selection for " + event.getName() + " has been cancelled. Reason: " + reason;
        } else {
            message = "Your selection for " + event.getName() + " has been cancelled by the organizer.";
        }

        List<String> recipients = new ArrayList<>();
        recipients.add(entrantId);

        sendNotifications(recipients, message, "entrant_cancelled", event, listener);
    }
}
