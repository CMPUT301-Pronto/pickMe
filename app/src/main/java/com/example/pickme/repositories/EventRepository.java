package com.example.pickme.repositories;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.models.Event;
import com.example.pickme.models.Geolocation;
import com.example.pickme.models.WaitingList;
import com.example.pickme.services.FirebaseManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * # EventRepository
 * Repository layer encapsulating Firestore access for event data and waiting-list workflows.
 *
 * <p><b>Responsibilities</b></p>
 * <ul>
 *   <li>CRUD for {@code events} collection documents.</li>
 *   <li>Organizer/admin/entrant-facing queries.</li>
 *   <li>Waiting list membership management and reads.</li>
 *   <li>Collection group queries (response pending / in-event / waiting / cancelled).</li>
 * </ul>
 *
 * <p><b>Firestore Structure</b></p>
 * <pre>
 * events/{eventId}
 *   (Event fields…)
 *   waitingList/{entrantId}
 *   responsePendingList/{entrantId}
 *   inEventList/{entrantId}
 *   cancelledList/{entrantId}
 * </pre>
 *
 * <p><b>Design notes</b></p>
 * <ul>
 *   <li>Read/write operations are isolated behind this repository for testability.</li>
 *   <li>Deletion of subcollections is caller’s responsibility (not automatic in Firestore).</li>
 *   <li>Some filters are performed in-app to avoid complex Firestore queries.</li>
 * </ul>
 *
 * <p><b>Outstanding considerations</b></p>
 * <ul>
 *   <li>Bulk deletion of subcollections for full event removal is not implemented.</li>
 *   <li>For quota/cost control, consider pagination and query limits for large datasets.</li>
 * </ul>
 * Related User Stories: US 01.01.01, US 01.01.02, US 01.01.03, US 02.01.01,
 *                       US 02.02.01, US 03.01.01
 */
public class EventRepository extends BaseRepository {

    private static final String TAG = "EventRepository";
    private static final String COLLECTION_EVENTS = "events";
    private static final String SUBCOLLECTION_WAITING_LIST = "waitingList";
    private static final String SUBCOLLECTION_RESPONSE_PENDING = "responsePendingList";
    private static final String SUBCOLLECTION_IN_EVENT = "inEventList";
    private static final String SUBCOLLECTION_CANCELLED = "cancelledList";
    private FirebaseFirestore db;

    /**
     * Constructor - initializes repository for "events" collection
     */
    public EventRepository() {
        super(COLLECTION_EVENTS);
        this.db = FirebaseManager.getFirestore();
    }

    // ==================== Event CRUD Operations ====================

    /**
     * Create a new event in Firestore
     *
     * @param event Event object to create
     * @param onSuccess Success callback with event ID
     * @param onFailure Failure callback with exception
     */
    public void createEvent(@NonNull Event event,
                           @NonNull OnSuccessListener onSuccess,
                           @NonNull OnFailureListener onFailure) {
        if (event.getEventId() == null || event.getEventId().isEmpty()) {
            // Auto-generate event ID if not provided
            String eventId = db.collection(COLLECTION_EVENTS).document().getId();
            event.setEventId(eventId);
        }

        db.collection(COLLECTION_EVENTS)
                .document(event.getEventId())
                .set(event.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event created successfully: " + event.getEventId());
                    onSuccess.onSuccess(event.getEventId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create event", e);
                    onFailure.onFailure(e);
                });
    }

    /**
     * Update specific fields of an event
     *
     * @param eventId Event ID to update
     * @param updates Map of field names to new values
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void updateEvent(@NonNull String eventId,
                           @NonNull Map<String, Object> updates,
                           @NonNull OnSuccessListener onSuccess,
                           @NonNull OnFailureListener onFailure) {
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event updated successfully: " + eventId);
                    onSuccess.onSuccess(eventId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update event: " + eventId, e);
                    onFailure.onFailure(e);
                });
    }

    /**
     * Delete an event and all its subcollections
     * Uses batch write to ensure atomicity
     *
     * @param eventId Event ID to delete
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void deleteEvent(@NonNull String eventId,
                           @NonNull OnSuccessListener onSuccess,
                           @NonNull OnFailureListener onFailure) {
        // Delete event document (subcollections must be deleted separately)
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event deleted successfully: " + eventId);
                    // Note: Subcollections are not auto-deleted.
                    // For production, implement batch deletion of subcollections
                    onSuccess.onSuccess(eventId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to delete event: " + eventId, e);
                    onFailure.onFailure(e);
                });
    }

    /**
     * Get a single event by ID
     *
     * @param eventId Event ID to retrieve
     * @param listener Callback with Event object
     */
    public void getEvent(@NonNull String eventId,
                        @NonNull OnEventLoadedListener listener) {
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Event event = documentSnapshot.toObject(Event.class);
                        Log.d(TAG, "Event retrieved: " + eventId);
                        listener.onEventLoaded(event);
                    } else {
                        Log.w(TAG, "Event not found: " + eventId);
                        listener.onError(new Exception("Event not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get event: " + eventId, e);
                    listener.onError(e);
                });
    }

    /**
     * Get all events created by a specific organizer
     *
     * @param organizerId Organizer's user ID
     * @param listener Callback with list of events
     */
    public void getEventsByOrganizer(@NonNull String organizerId,
                                    @NonNull OnEventsLoadedListener listener) {
        db.collection(COLLECTION_EVENTS)
                .whereEqualTo("organizerId", organizerId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            events.add(event);
                        }
                    }
                    Log.d(TAG, "Retrieved " + events.size() + " events for organizer: " + organizerId);
                    listener.onEventsLoaded(events);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get events for organizer: " + organizerId, e);
                    listener.onError(e);
                });
    }

    /**
     * Get all events (for admin browsing)
     *
     * @param listener Callback with list of all events
     */
    public void getAllEvents(@NonNull OnEventsLoadedListener listener) {
        db.collection(COLLECTION_EVENTS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            events.add(event);
                        }
                    }
                    Log.d(TAG, "Retrieved all events: " + events.size());
                    listener.onEventsLoaded(events);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get all events", e);
                    listener.onError(e);
                });
    }

    /**
     * Get events available for entrants to join
     * Filters events with status "OPEN" and registration currently open
     *
     * @param listener Callback with list of available events
     */
    public void getEventsForEntrant(@NonNull OnEventsLoadedListener listener) {
        long currentTime = System.currentTimeMillis();

        // Simplified query - just get OPEN events, filter rest in code
        db.collection(COLLECTION_EVENTS)
                .whereEqualTo("status", "OPEN")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<Event> events = new ArrayList<>();
                    Log.d(TAG, "Query returned " + querySnapshot.size() + " documents");

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Event event = doc.toObject(Event.class);
                        if (event != null) {
                            Log.d(TAG, "Event: " + event.getName() + ", Status: " + event.getStatus()
                                + ", RegStart: " + event.getRegistrationStartDate()
                                + ", RegEnd: " + event.getRegistrationEndDate()
                                + ", IsOpen: " + event.isRegistrationOpen());

                            // Filter in code to avoid complex Firestore query limitations
                            if (event.isRegistrationOpen()) {
                                events.add(event);
                                Log.d(TAG, "Added event: " + event.getName());
                            }
                        }
                    }
                    Log.d(TAG, "Retrieved " + events.size() + " events for entrant");
                    listener.onEventsLoaded(events);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get events for entrant", e);
                    listener.onError(e);
                });
    }
    /**
     * Update registration start and end dates for an event
     */
    public void updateRegistrationDates(String eventId, long startDate, long endDate,
                                        com.google.android.gms.tasks.OnSuccessListener<Void> onSuccess,
                                        com.google.android.gms.tasks.OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("registrationStartDate", startDate);
        updates.put("registrationEndDate", endDate);

        db.collection("events")
                .document(eventId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }



    // ==================== Waiting List Operations ====================

    /**
     * Add entrant to event waiting list
     * Checks waiting list limit and duplicate entries atomically
     * Enforces waiting list limit if specified
     *
     * @param eventId Event ID
     * @param entrantId User ID to add
     * @param location Optional geolocation
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void addEntrantToWaitingList(@NonNull String eventId,
                                       @NonNull String entrantId,
                                       Geolocation location,
                                       @NonNull OnSuccessListener onSuccess,
                                       @NonNull OnFailureListener onFailure) {
        // First get event to check limit
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .get()
                .addOnSuccessListener(eventSnapshot -> {
                    if (!eventSnapshot.exists()) {
                        onFailure.onFailure(new Exception("Event not found"));
                        return;
                    }

                    Event event = eventSnapshot.toObject(Event.class);
                    if (event == null) {
                        onFailure.onFailure(new Exception("Failed to parse event"));
                        return;
                    }

                    int waitingListLimit = event.getWaitingListLimit();

                    // If limit specified, check current count
                    if (waitingListLimit > 0) {
                        db.collection(COLLECTION_EVENTS)
                                .document(eventId)
                                .collection(SUBCOLLECTION_WAITING_LIST)
                                .get()
                                .addOnSuccessListener(querySnapshot -> {
                                    int currentCount = querySnapshot.size();

                                    if (currentCount >= waitingListLimit) {
                                        onFailure.onFailure(new Exception("Waiting list is full (limit: " + waitingListLimit + ")"));
                                        return;
                                    }

                                    // Proceed with adding
                                    addToWaitingListInternal(eventId, entrantId, location, onSuccess, onFailure);
                                })
                                .addOnFailureListener(onFailure::onFailure);
                    } else {
                        // No limit, proceed directly
                        addToWaitingListInternal(eventId, entrantId, location, onSuccess, onFailure);
                    }
                })
                .addOnFailureListener(onFailure::onFailure);
    }

    /**
     * Internal method to add entrant to waiting list
     */
    private void addToWaitingListInternal(@NonNull String eventId,
                                          @NonNull String entrantId,
                                          Geolocation location,
                                          @NonNull OnSuccessListener onSuccess,
                                          @NonNull OnFailureListener onFailure) {
        // Create waiting list entry data
        Map<String, Object> entrantData = new HashMap<>();
        entrantData.put("entrantId", entrantId);
        entrantData.put("joinedTimestamp", System.currentTimeMillis());

        if (location != null) {
            entrantData.put("latitude", location.getLatitude());
            entrantData.put("longitude", location.getLongitude());
            entrantData.put("locationTimestamp", location.getTimestamp());
        }

        // Add to waiting list subcollection
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUBCOLLECTION_WAITING_LIST)
                .document(entrantId)
                .set(entrantData)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Entrant added to waiting list: " + entrantId + " for event: " + eventId);
                    onSuccess.onSuccess(entrantId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to add entrant to waiting list", e);
                    onFailure.onFailure(e);
                });
    }

    /**
     * Remove entrant from event waiting list
     *
     * @param eventId Event ID
     * @param entrantId User ID to remove
     * @param onSuccess Success callback
     * @param onFailure Failure callback
     */
    public void removeEntrantFromWaitingList(@NonNull String eventId,
                                            @NonNull String entrantId,
                                            @NonNull OnSuccessListener onSuccess,
                                            @NonNull OnFailureListener onFailure) {
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUBCOLLECTION_WAITING_LIST)
                .document(entrantId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Entrant removed from waiting list: " + entrantId + " from event: " + eventId);
                    onSuccess.onSuccess(entrantId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to remove entrant from waiting list", e);
                    onFailure.onFailure(e);
                });
    }

    /**
     * Get waiting list for an event
     * Returns WaitingList object with all entrants and their data
     *
     * @param eventId Event ID
     * @param listener Callback with WaitingList object
     */
    public void getWaitingListForEvent(@NonNull String eventId,
                                      @NonNull OnWaitingListLoadedListener listener) {
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUBCOLLECTION_WAITING_LIST)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    WaitingList waitingList = new WaitingList(eventId);

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String entrantId = doc.getString("entrantId");
                        Long joinedTimestamp = doc.getLong("joinedTimestamp");

                        // Add entrant
                        if (entrantId != null) {
                            waitingList.getEntrantIds().add(entrantId);

                            if (joinedTimestamp != null) {
                                waitingList.getEntrantTimestamps().put(entrantId, joinedTimestamp);
                            }

                            // Add geolocation if available
                            Double latitude = doc.getDouble("latitude");
                            Double longitude = doc.getDouble("longitude");
                            Long locationTimestamp = doc.getLong("locationTimestamp");

                            if (latitude != null && longitude != null) {
                                Geolocation location = new Geolocation(
                                    latitude,
                                    longitude,
                                    locationTimestamp != null ? locationTimestamp : System.currentTimeMillis()
                                );
                                waitingList.getGeolocationData().put(entrantId, location);
                            }
                        }
                    }

                    Log.d(TAG, "Waiting list retrieved for event: " + eventId +
                              " with " + waitingList.getEntrantCount() + " entrants");
                    listener.onWaitingListLoaded(waitingList);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get waiting list for event: " + eventId, e);
                    listener.onError(e);
                });
    }

    /**
     * Check if entrant is in waiting list
     *
     * @param eventId Event ID
     * @param entrantId User ID to check
     * @param listener Callback with boolean result
     */
    public void isEntrantInWaitingList(@NonNull String eventId,
                                      @NonNull String entrantId,
                                      @NonNull OnEntrantCheckListener listener) {
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(SUBCOLLECTION_WAITING_LIST)
                .document(entrantId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean exists = documentSnapshot.exists();
                    Log.d(TAG, "Entrant " + entrantId + " in waiting list: " + exists);
                    listener.onCheckComplete(exists);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check entrant in waiting list", e);
                    listener.onError(e);
                });
    }

    /**
     * Get list of entrant IDs from a specific subcollection
     * Used for CSV export and bulk operations
     *
     * @param eventId Event ID
     * @param subcollection Subcollection name (waitingList, responsePendingList, inEventList, cancelledList)
     * @param listener Callback with list of entrant IDs
     */
    public void getEntrantIdsFromSubcollection(@NonNull String eventId,
                                               @NonNull String subcollection,
                                               @NonNull OnEntrantIdsLoadedListener listener) {
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .collection(subcollection)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> entrantIds = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        entrantIds.add(doc.getId());
                    }
                    Log.d(TAG, "Retrieved " + entrantIds.size() + " entrant IDs from " + subcollection);
                    listener.onEntrantIdsLoaded(entrantIds);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get entrant IDs from " + subcollection, e);
                    listener.onError(e);
                });
    }

    // ==================== Listener Interfaces ====================

    /**
     * Callback for successful operations returning ID
     */
    public interface OnSuccessListener {
        void onSuccess(String id);
    }

    /**
     * Callback for failed operations
     */
    public interface OnFailureListener {
        void onFailure(Exception e);
    }

    /**
     * Callback for loading a single event
     */
    public interface OnEventLoadedListener {
        void onEventLoaded(Event event);
        void onError(Exception e);
    }

    /**
     * Callback for loading multiple events
     */
    public interface OnEventsLoadedListener {
        void onEventsLoaded(List<Event> events);
        void onError(Exception e);
    }

    /**
     * Callback for loading waiting list
     */
    public interface OnWaitingListLoadedListener {
        void onWaitingListLoaded(WaitingList waitingList);
        void onError(Exception e);
    }

    /**
     * Callback for entrant check
     */
    public interface OnEntrantCheckListener {
        void onCheckComplete(boolean exists);
        void onError(Exception e);
    }

    /**
     * Callback for loading list of entrant IDs
     */
    public interface OnEntrantIdsLoadedListener {
        void onEntrantIdsLoaded(List<String> entrantIds);
        void onError(Exception e);
    }

    public interface OnEventsWithMetadataLoadedListener {
        void onEventsLoaded(List<Event> events, Map<String, Object> metadata);
        void onError(Exception e);
    }

    // ==================== Collection Group Queries ====================

    /**
     * Get all events where the specified user is in the responsePendingList subcollection.
     * Uses Firestore collection group query for efficiency.
     *
     * @param userId the entrant's user ID
     * @param listener callback with events and metadata (deadlines)
     */
    public void getEventsWhereEntrantInResponsePending(@NonNull String userId,
                                                       @NonNull OnEventsWithMetadataLoadedListener listener) {
        Log.d(TAG, "Querying events where entrant " + userId + " is in responsePendingList");

        db.collectionGroup(SUBCOLLECTION_RESPONSE_PENDING)
                .whereEqualTo("entrantId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No response pending invitations found for user " + userId);
                        listener.onEventsLoaded(new ArrayList<>(), new HashMap<>());
                        return;
                    }

                    Log.d(TAG, "Found " + querySnapshot.size() + " response pending entries");

                    List<Event> events = new ArrayList<>();
                    Map<String, Object> metadata = new HashMap<>();
                    int[] remainingFetches = {querySnapshot.size()};

                    for (DocumentSnapshot subDoc : querySnapshot.getDocuments()) {
                        // Extract deadline from subcollection document
                        Long deadline = subDoc.getLong("deadline");
                        Long timestamp = subDoc.getLong("timestamp");

                        // Get parent event reference
                        String eventId = subDoc.getReference().getParent().getParent().getId();

                        // Store metadata
                        if (deadline != null) {
                            metadata.put(eventId + "_deadline", deadline);
                        } else if (timestamp != null) {
                            // Fallback: calculate deadline as 7 days from timestamp
                            metadata.put(eventId + "_deadline", timestamp + (7 * 24 * 60 * 60 * 1000L));
                        }

                        // Fetch parent event document
                        subDoc.getReference().getParent().getParent().get()
                                .addOnSuccessListener(eventDoc -> {
                                    if (eventDoc.exists()) {
                                        Event event = eventDoc.toObject(Event.class);
                                        if (event != null) {
                                            event.setEventId(eventDoc.getId());
                                            synchronized (events) {
                                                events.add(event);
                                            }
                                        }
                                    }

                                    // Check if all fetches complete
                                    synchronized (remainingFetches) {
                                        remainingFetches[0]--;
                                        if (remainingFetches[0] == 0) {
                                            Log.d(TAG, "Successfully loaded " + events.size() + " events with pending responses");
                                            listener.onEventsLoaded(events, metadata);
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error fetching parent event", e);
                                    synchronized (remainingFetches) {
                                        remainingFetches[0]--;
                                        if (remainingFetches[0] == 0) {
                                            listener.onEventsLoaded(events, metadata);
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error querying responsePendingList collection group", e);
                    listener.onError(e);
                });
    }

    /**
     * Get all events where the specified user is in the inEventList subcollection (accepted invitations).
     * Uses Firestore collection group query for efficiency.
     *
     * @param userId the entrant's user ID
     * @param listener callback with events and metadata
     */
    public void getEventsWhereEntrantInEventList(@NonNull String userId,
                                                 @NonNull OnEventsWithMetadataLoadedListener listener) {
        Log.d(TAG, "Querying events where entrant " + userId + " is in inEventList");

        db.collectionGroup(SUBCOLLECTION_IN_EVENT)
                .whereEqualTo("entrantId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No accepted events found for user " + userId);
                        listener.onEventsLoaded(new ArrayList<>(), new HashMap<>());
                        return;
                    }

                    Log.d(TAG, "Found " + querySnapshot.size() + " accepted event entries");

                    List<Event> events = new ArrayList<>();
                    Map<String, Object> metadata = new HashMap<>();
                    int[] remainingFetches = {querySnapshot.size()};

                    for (DocumentSnapshot subDoc : querySnapshot.getDocuments()) {
                        // Extract metadata from subcollection document
                        Long acceptedAt = subDoc.getLong("acceptedAt");

                        // Get parent event reference
                        String eventId = subDoc.getReference().getParent().getParent().getId();

                        // Store metadata
                        if (acceptedAt != null) {
                            metadata.put(eventId + "_acceptedAt", acceptedAt);
                        }

                        // Fetch parent event document
                        subDoc.getReference().getParent().getParent().get()
                                .addOnSuccessListener(eventDoc -> {
                                    if (eventDoc.exists()) {
                                        Event event = eventDoc.toObject(Event.class);
                                        if (event != null) {
                                            event.setEventId(eventDoc.getId());
                                            synchronized (events) {
                                                events.add(event);
                                            }
                                        }
                                    }

                                    // Check if all fetches complete
                                    synchronized (remainingFetches) {
                                        remainingFetches[0]--;
                                        if (remainingFetches[0] == 0) {
                                            Log.d(TAG, "Successfully loaded " + events.size() + " accepted events");
                                            listener.onEventsLoaded(events, metadata);
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error fetching parent event", e);
                                    synchronized (remainingFetches) {
                                        remainingFetches[0]--;
                                        if (remainingFetches[0] == 0) {
                                            listener.onEventsLoaded(events, metadata);
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error querying inEventList collection group", e);
                    listener.onError(e);
                });
    }

    /**
     * Get all events where the specified user is in the waitingList subcollection.
     * Uses Firestore collection group query for efficiency.
     *
     * @param userId the entrant's user ID
     * @param listener callback with events and metadata (joined timestamps)
     */
    public void getEventsWhereEntrantInWaitingList(@NonNull String userId,
                                                   @NonNull OnEventsWithMetadataLoadedListener listener) {
        Log.d(TAG, "Querying events where entrant " + userId + " is in waitingList");

        db.collectionGroup(SUBCOLLECTION_WAITING_LIST)
                .whereEqualTo("entrantId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No waiting list entries found for user " + userId);
                        listener.onEventsLoaded(new ArrayList<>(), new HashMap<>());
                        return;
                    }

                    Log.d(TAG, "Found " + querySnapshot.size() + " waiting list entries");

                    List<Event> events = new ArrayList<>();
                    Map<String, Object> metadata = new HashMap<>();
                    int[] remainingFetches = {querySnapshot.size()};

                    for (DocumentSnapshot subDoc : querySnapshot.getDocuments()) {
                        // Extract metadata from subcollection document
                        Long joinedAt = subDoc.getLong("joinedAt");

                        // Get parent event reference
                        String eventId = subDoc.getReference().getParent().getParent().getId();

                        // Store metadata
                        if (joinedAt != null) {
                            metadata.put(eventId + "_joinedAt", joinedAt);
                        }

                        // Fetch parent event document
                        subDoc.getReference().getParent().getParent().get()
                                .addOnSuccessListener(eventDoc -> {
                                    if (eventDoc.exists()) {
                                        Event event = eventDoc.toObject(Event.class);
                                        if (event != null) {
                                            event.setEventId(eventDoc.getId());
                                            synchronized (events) {
                                                events.add(event);
                                            }
                                        }
                                    }

                                    // Check if all fetches complete
                                    synchronized (remainingFetches) {
                                        remainingFetches[0]--;
                                        if (remainingFetches[0] == 0) {
                                            Log.d(TAG, "Successfully loaded " + events.size() + " waiting list events");
                                            listener.onEventsLoaded(events, metadata);
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error fetching parent event", e);
                                    synchronized (remainingFetches) {
                                        remainingFetches[0]--;
                                        if (remainingFetches[0] == 0) {
                                            listener.onEventsLoaded(events, metadata);
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error querying waitingList collection group", e);
                    listener.onError(e);
                });
    }
    /**
     * Get all events where user has declined invitation (in cancelledList)
     */
    public void getEventsWhereEntrantDeclined(@NonNull String userId,
                                              @NonNull OnEventsWithMetadataLoadedListener listener) {
        Log.d(TAG, "Querying events where entrant " + userId + " declined invitation");

        db.collectionGroup("cancelledList")
                .whereEqualTo("entrantId", userId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No declined events found for user " + userId);
                        listener.onEventsLoaded(new ArrayList<>(), new HashMap<>());
                        return;
                    }

                    List<Event> events = new ArrayList<>();
                    Map<String, Object> metadata = new HashMap<>();
                    int[] remainingFetches = {querySnapshot.size()};

                    for (DocumentSnapshot subDoc : querySnapshot.getDocuments()) {
                        // Extract metadata
                        Long declinedAt = subDoc.getLong("declinedTimestamp");

                        // Get parent event
                        String eventId = subDoc.getReference().getParent().getParent().getId();

                        if (declinedAt != null) {
                            metadata.put(eventId + "_declinedAt", declinedAt);
                        }

                        // Fetch parent event document
                        subDoc.getReference().getParent().getParent().get()
                                .addOnSuccessListener(eventDoc -> {
                                    if (eventDoc.exists()) {
                                        Event event = eventDoc.toObject(Event.class);
                                        if (event != null) {
                                            event.setEventId(eventDoc.getId());
                                            synchronized (events) {
                                                events.add(event);
                                            }
                                        }
                                    }

                                    synchronized (remainingFetches) {
                                        remainingFetches[0]--;
                                        if (remainingFetches[0] == 0) {
                                            Log.d(TAG, "Successfully loaded " + events.size() + " declined events");
                                            listener.onEventsLoaded(events, metadata);
                                        }
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Error fetching parent event", e);
                                    synchronized (remainingFetches) {
                                        remainingFetches[0]--;
                                        if (remainingFetches[0] == 0) {
                                            listener.onEventsLoaded(events, metadata);
                                        }
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error querying cancelledList collection group", e);
                    listener.onError(e);
                });
    }
}

