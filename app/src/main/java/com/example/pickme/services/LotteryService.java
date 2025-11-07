package com.example.pickme.services;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.models.Event;
import com.example.pickme.models.EventHistoryItem;
import com.example.pickme.models.Geolocation;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ProfileRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * JAVADOCS LLM GENERATED
 *
 * # LotteryService
 * Centralized domain service that implements the lottery lifecycle for events.
 *
 * <p><b>Responsibilities</b></p>
 * <ul>
 *   <li>Initial draw: randomly select winners from the waiting list (US 02.05.02).</li>
 *   <li>Replacement draw: select new winners when invitations are declined/expired (US 02.05.03).</li>
 *   <li>Acceptance/Decline handling: move entrants across event subcollections
 *       <code>waitingList → responsePendingList → inEventList</code>, or to <code>cancelledList</code>.</li>
 *   <li>Profile history updates per outcome (selected / not_selected / enrolled / CANCELLED).</li>
 * </ul>
 *
 * <p><b>Design notes</b></p>
 * <ul>
 *   <li><b>Fairness:</b> Uses {@link SecureRandom} and {@link Collections#shuffle(List, java.util.Random)} for unbiased selection.</li>
 *   <li><b>Atomicity:</b> Uses Firestore {@link WriteBatch} for multi-document updates that must succeed together.</li>
 *   <li><b>Separation of concerns:</b> Reads are delegated to {@link EventRepository}; user history to {@link ProfileRepository}.</li>
 * </ul>
 *
 * <p><b>Data model touchpoints</b></p>
 * <ul>
 *   <li>{@code events/{eventId}/waitingList}</li>
 *   <li>{@code events/{eventId}/responsePendingList}</li>
 *   <li>{@code events/{eventId}/inEventList}</li>
 *   <li>{@code events/{eventId}/cancelledList}</li>
 * </ul>
 *
 * <p><b>Outstanding considerations</b></p>
 * <ul>
 *   <li>Deadline policy is fixed (7 days). Externalize per-event configuration if needed.</li>
 *   <li>No automatic notification here—callers should invoke {@code NotificationService} upon completion.</li>
 *   <li>Batch is used (not transaction). If cross-read consistency is required mid-operation, consider a full transaction.</li>
 * </ul>
 */
public class LotteryService {

    private static final String TAG = "LotteryService";
    private static final String COLLECTION_EVENTS = "events";
    private static final String SUBCOLLECTION_WAITING_LIST = "waitingList";
    private static final String SUBCOLLECTION_RESPONSE_PENDING = "responsePendingList";
    private static final String SUBCOLLECTION_IN_EVENT = "inEventList";

    private static LotteryService instance;
    private FirebaseFirestore db;
    private EventRepository eventRepository;
    private ProfileRepository profileRepository;
    private SecureRandom secureRandom;

    private LotteryService() {
        this.db = FirebaseManager.getFirestore();
        this.eventRepository = new EventRepository();
        this.profileRepository = new ProfileRepository();
        this.secureRandom = new SecureRandom();
    }

    public static synchronized LotteryService getInstance() {
        if (instance == null) {
            instance = new LotteryService();
        }
        return instance;
    }

    /**
     * Execute lottery draw - randomly select winners from waiting list
     *
     * HIGH-RISK: Uses Firestore transaction to ensure atomicity
     *
     * Process:
     * 1. Get all waiting list entrants
     * 2. Randomly select numberOfWinners using SecureRandom
     * 3. Use transaction to:
     *    - Move winners to responsePendingList
     *    - Mark losers as "not selected" (stay in waiting list)
     * 4. Trigger notifications
     * 5. Log execution for audit
     *
     * @param eventId Event ID
     * @param numberOfWinners Number of entrants to select
     * @param listener Callback with results
     */
    public void executeLotteryDraw(@NonNull String eventId,
                                   int numberOfWinners,
                                   @NonNull OnLotteryCompleteListener listener) {
        Log.d(TAG, "Executing lottery draw for event: " + eventId + ", winners: " + numberOfWinners);

        // First, get the event and waiting list
        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                eventRepository.getWaitingListForEvent(eventId, new EventRepository.OnWaitingListLoadedListener() {
                    @Override
                    public void onWaitingListLoaded(com.example.pickme.models.WaitingList waitingList) {
                        List<String> allEntrants = waitingList.getAllEntrants();

                        if (allEntrants.isEmpty()) {
                            listener.onError(new Exception("No entrants in waiting list"));
                            return;
                        }

                        if (numberOfWinners > allEntrants.size()) {
                            listener.onError(new Exception("Not enough entrants for draw"));
                            return;
                        }

                        // Randomly select winners
                        List<String> winners = selectRandomEntrants(allEntrants, numberOfWinners);
                        List<String> losers = new ArrayList<>(allEntrants);
                        losers.removeAll(winners);

                        Log.d(TAG, "Selected " + winners.size() + " winners, " + losers.size() + " losers");

                        // Execute transaction to move winners
                        executeLotteryTransaction(eventId, winners, losers, waitingList, event, listener);
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Failed to get waiting list", e);
                        listener.onError(e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to get event", e);
                listener.onError(e);
            }
        });
    }

    /**
     * Execute Firestore transaction to move winners and update losers
     */
    private void executeLotteryTransaction(@NonNull String eventId,
                                          @NonNull List<String> winners,
                                          @NonNull List<String> losers,
                                          @NonNull com.example.pickme.models.WaitingList waitingList,
                                          @NonNull Event event,
                                          @NonNull OnLotteryCompleteListener listener) {
        // Use WriteBatch for atomic operations
        WriteBatch batch = db.batch();
        long currentTime = System.currentTimeMillis();
        long responseDeadline = currentTime + (7 * 24 * 60 * 60 * 1000); // 7 days

        // Move winners to response pending list
        for (String winnerId : winners) {
            Map<String, Object> winnerData = new HashMap<>();
            winnerData.put("entrantId", winnerId);
            winnerData.put("selectedTimestamp", currentTime);
            winnerData.put("responseDeadline", responseDeadline);

            // Add geolocation if available
            Geolocation location = waitingList.getEntrantLocation(winnerId);
            if (location != null) {
                winnerData.put("latitude", location.getLatitude());
                winnerData.put("longitude", location.getLongitude());
                winnerData.put("locationTimestamp", location.getTimestamp());
            }

            batch.set(db.collection(COLLECTION_EVENTS)
                    .document(eventId)
                    .collection(SUBCOLLECTION_RESPONSE_PENDING)
                    .document(winnerId), winnerData);

            // Remove from waiting list
            batch.delete(db.collection(COLLECTION_EVENTS)
                    .document(eventId)
                    .collection(SUBCOLLECTION_WAITING_LIST)
                    .document(winnerId));
        }

        // Mark losers as "not selected" in waiting list
        for (String loserId : losers) {
            batch.update(db.collection(COLLECTION_EVENTS)
                    .document(eventId)
                    .collection(SUBCOLLECTION_WAITING_LIST)
                    .document(loserId), "status", "not_selected");
        }

        // Commit batch
        batch.commit()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Lottery draw committed successfully");

                    // Update event history for winners
                    updateProfileHistories(winners, event, "selected");
                    updateProfileHistories(losers, event, "not_selected");

                    // Return results
                    LotteryResult result = new LotteryResult(winners, losers, responseDeadline);
                    listener.onLotteryComplete(result);

                    // Note: NotificationService should be called by the caller
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Lottery draw transaction failed", e);
                    listener.onError(e);
                });
    }

    /**
     * Execute replacement draw when someone declines
     *
     * Selects from remaining waiting list entrants who weren't previously drawn
     *
     * @param eventId Event ID
     * @param numberOfReplacements Number of replacements needed
     * @param listener Callback with results
     */
    public void executeReplacementDraw(@NonNull String eventId,
                                      int numberOfReplacements,
                                      @NonNull OnLotteryCompleteListener listener) {
        Log.d(TAG, "Executing replacement draw for event: " + eventId + ", count: " + numberOfReplacements);

        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                eventRepository.getWaitingListForEvent(eventId, new EventRepository.OnWaitingListLoadedListener() {
                    @Override
                    public void onWaitingListLoaded(com.example.pickme.models.WaitingList waitingList) {
                        // Get entrants who haven't been selected before (status != "not_selected")
                        List<String> eligibleEntrants = new ArrayList<>();

                        // Query waiting list for entrants without "not_selected" status
                        db.collection(COLLECTION_EVENTS)
                                .document(eventId)
                                .collection(SUBCOLLECTION_WAITING_LIST)
                                .whereEqualTo("status", null) // Not yet processed
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                                        eligibleEntrants.add(doc.getId());
                                    }

                                    if (eligibleEntrants.isEmpty()) {
                                        listener.onError(new Exception("No eligible entrants for replacement draw"));
                                        return;
                                    }

                                    if (numberOfReplacements > eligibleEntrants.size()) {
                                        listener.onError(new Exception("Not enough eligible entrants"));
                                        return;
                                    }

                                    // Select replacements
                                    List<String> replacements = selectRandomEntrants(eligibleEntrants, numberOfReplacements);
                                    List<String> remaining = new ArrayList<>(eligibleEntrants);
                                    remaining.removeAll(replacements);

                                    Log.d(TAG, "Selected " + replacements.size() + " replacements");

                                    // Execute transaction
                                    executeLotteryTransaction(eventId, replacements, remaining, waitingList, event, listener);
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to query eligible entrants", e);
                                    listener.onError(e);
                                });
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Failed to get waiting list", e);
                        listener.onError(e);
                    }
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to get event", e);
                listener.onError(e);
            }
        });
    }

    /**
     * Handle entrant acceptance - move to in-event list
     *
     * @param eventId Event ID
     * @param entrantId Entrant who accepted
     * @param listener Callback
     */
    public void handleEntrantAcceptance(@NonNull String eventId,
                                       @NonNull String entrantId,
                                       @NonNull OnAcceptanceHandledListener listener) {
        Log.d(TAG, "Handling acceptance for entrant: " + entrantId + " in event: " + eventId);

        // Get entrant data from response pending list
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUBCOLLECTION_RESPONSE_PENDING)
                .document(entrantId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        listener.onError(new Exception("Entrant not found in response pending list"));
                        return;
                    }

                    // Get entrant data
                    Map<String, Object> entrantData = new HashMap<>();
                    entrantData.put("entrantId", entrantId);
                    entrantData.put("enrolledTimestamp", System.currentTimeMillis());
                    entrantData.put("checkInStatus", false);

                    // Copy geolocation if available
                    if (documentSnapshot.contains("latitude")) {
                        entrantData.put("latitude", documentSnapshot.getDouble("latitude"));
                        entrantData.put("longitude", documentSnapshot.getDouble("longitude"));
                        entrantData.put("locationTimestamp", documentSnapshot.getLong("locationTimestamp"));
                    }

                    // Use batch for atomic operation
                    WriteBatch batch = db.batch();

                    // Add to in-event list
                    batch.set(db.collection(COLLECTION_EVENTS)
                            .document(eventId)
                            .collection(SUBCOLLECTION_IN_EVENT)
                            .document(entrantId), entrantData);

                    // Remove from response pending list
                    batch.delete(db.collection(COLLECTION_EVENTS)
                            .document(eventId)
                            .collection(SUBCOLLECTION_RESPONSE_PENDING)
                            .document(entrantId));

                    // Commit batch
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Entrant acceptance handled successfully");

                                // Update profile history
                                eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
                                    @Override
                                    public void onEventLoaded(Event event) {
                                        EventHistoryItem historyItem = new EventHistoryItem(
                                                eventId,
                                                event.getName(),
                                                System.currentTimeMillis(),
                                                "enrolled"
                                        );

                                        profileRepository.addEventToHistory(entrantId, historyItem,
                                                new ProfileRepository.OnSuccessListener() {
                                                    @Override
                                                    public void onSuccess(String userId) {
                                                        listener.onAcceptanceHandled(entrantId);
                                                    }
                                                },
                                                new ProfileRepository.OnFailureListener() {
                                                    @Override
                                                    public void onFailure(Exception e) {
                                                        Log.w(TAG, "Failed to update history, but acceptance recorded", e);
                                                        listener.onAcceptanceHandled(entrantId);
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        Log.w(TAG, "Failed to get event for history update", e);
                                        listener.onAcceptanceHandled(entrantId);
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to handle acceptance", e);
                                listener.onError(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get entrant data", e);
                    listener.onError(e);
                });
    }

    /**
     * Handle entrant decline - remove from response pending list
     *
     * @param eventId Event ID
     * @param entrantId Entrant who declined
     * @param listener Callback
     */

    public void handleEntrantDecline(@NonNull String eventId,
                                     @NonNull String entrantId,
                                     @NonNull OnDeclineHandledListener listener) {
        Log.d(TAG, "Handling decline for entrant: " + entrantId + " in event: " + eventId);

        // Get entrant data first to preserve it
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUBCOLLECTION_RESPONSE_PENDING)
                .document(entrantId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (!documentSnapshot.exists()) {
                        listener.onError(new Exception("Entrant not found in response pending list"));
                        return;
                    }

                    // Prepare cancelled entry data
                    Map<String, Object> cancelledData = new HashMap<>();
                    cancelledData.put("entrantId", entrantId);
                    cancelledData.put("declinedTimestamp", System.currentTimeMillis());
                    cancelledData.put("reason", "user_declined");

                    // Copy geolocation if available
                    if (documentSnapshot.contains("latitude")) {
                        cancelledData.put("latitude", documentSnapshot.getDouble("latitude"));
                        cancelledData.put("longitude", documentSnapshot.getDouble("longitude"));
                        cancelledData.put("locationTimestamp", documentSnapshot.getLong("locationTimestamp"));
                    }

                    // Use batch for atomic operations
                    WriteBatch batch = db.batch();

                    // 1. Add to cancelled list
                    batch.set(db.collection(COLLECTION_EVENTS)
                            .document(eventId)
                            .collection("cancelledList")
                            .document(entrantId), cancelledData);

                    // 2. Remove from response pending list
                    batch.delete(db.collection(COLLECTION_EVENTS)
                            .document(eventId)
                            .collection(SUBCOLLECTION_RESPONSE_PENDING)
                            .document(entrantId));

                    // Commit batch
                    batch.commit()
                            .addOnSuccessListener(aVoid -> {
                                Log.d(TAG, "Entrant decline handled successfully");

                                // Update profile history
                                eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
                                    @Override
                                    public void onEventLoaded(Event event) {
                                        // Update event history
                                        EventHistoryItem historyItem = new EventHistoryItem(
                                                eventId,
                                                event.getName(),
                                                System.currentTimeMillis(),
                                                "CANCELLED"  // Use uppercase for consistency
                                        );

                                        profileRepository.addEventToHistory(entrantId, historyItem,
                                                new ProfileRepository.OnSuccessListener() {
                                                    @Override
                                                    public void onSuccess(String userId) {
                                                        // Notify organizer
                                                        notifyOrganizerOfDecline(event, entrantId);

                                                        // Indicate replacement should be triggered
                                                        listener.onDeclineHandled(entrantId, true);
                                                    }
                                                },
                                                new ProfileRepository.OnFailureListener() {
                                                    @Override
                                                    public void onFailure(Exception e) {
                                                        Log.w(TAG, "Failed to update history, but decline recorded", e);
                                                        notifyOrganizerOfDecline(event, entrantId);
                                                        listener.onDeclineHandled(entrantId, true);
                                                    }
                                                });
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        Log.w(TAG, "Failed to get event for notifications", e);
                                        listener.onDeclineHandled(entrantId, true);
                                    }
                                });
                            })
                            .addOnFailureListener(e -> {
                                Log.e(TAG, "Failed to handle decline", e);
                                listener.onError(e);
                            });
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get entrant data", e);
                    listener.onError(e);
                });
    }

    /**
     * Notify organizer about entrant decline
     */
    private void notifyOrganizerOfDecline(@NonNull Event event, @NonNull String entrantId) {
        // Create notification for organizer
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ENTRANT_DECLINED");
        notification.put("eventId", event.getEventId());
        notification.put("eventName", event.getName());
        notification.put("entrantId", entrantId);
        notification.put("timestamp", System.currentTimeMillis());
        notification.put("message", "An entrant has declined their invitation to " + event.getName());
        notification.put("read", false);

        // Add to organizer's notifications subcollection
        db.collection("users")
                .document(event.getOrganizerId())
                .collection("notifications")
                .add(notification)
                .addOnSuccessListener(docRef -> {
                    Log.d(TAG, "Organizer notified of decline");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to notify organizer", e);
                });
    }

    /**
     * Randomly select N entrants from list using SecureRandom for fairness
     */
    private List<String> selectRandomEntrants(List<String> entrants, int count) {
        List<String> shuffled = new ArrayList<>(entrants);
        Collections.shuffle(shuffled, secureRandom);
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    /**
     * Update profile histories for list of entrants
     */
    private void updateProfileHistories(List<String> entrantIds, Event event, String status) {
        for (String entrantId : entrantIds) {
            EventHistoryItem historyItem = new EventHistoryItem(
                    event.getEventId(),
                    event.getName(),
                    System.currentTimeMillis(),
                    status
            );

            profileRepository.addEventToHistory(entrantId, historyItem,
                    userId -> Log.d(TAG, "History updated for: " + userId),
                    e -> Log.w(TAG, "Failed to update history for: " + entrantId, e));
        }
    }

    /**
     * Lottery result container
     */
    public static class LotteryResult {
        public final List<String> winners;
        public final List<String> losers;
        public final long responseDeadline;

        public LotteryResult(List<String> winners, List<String> losers, long responseDeadline) {
            this.winners = winners;
            this.losers = losers;
            this.responseDeadline = responseDeadline;
        }
    }

    /**
     * Callback for lottery completion
     */
    public interface OnLotteryCompleteListener {
        void onLotteryComplete(LotteryResult result);
        void onError(Exception e);
    }

    /**
     * Callback for acceptance handling
     */
    public interface OnAcceptanceHandledListener {
        void onAcceptanceHandled(String entrantId);
        void onError(Exception e);
    }

    /**
     * Callback for decline handling
     */
    public interface OnDeclineHandledListener {
        void onDeclineHandled(String entrantId, boolean shouldTriggerReplacement);
        void onError(Exception e);
    }
}

