package com.example.pickme.ui.events;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * OrganizerDashboardActivity - Dashboard for organizers to manage their events
 *
 * Features:
 * - Display all events created by the organizer
 * - Show event statistics (waiting list, selected, enrolled counts)
 * - Navigate to ManageEventActivity to manage specific events
 * - FAB to create new events
 * - Empty state for organizers with no events
 *
 * Event Metrics:
 * - Waiting list count: number of entrants in waitingList subcollection
 * - Selected count: number of entrants in responsePendingList subcollection
 * - Enrolled count: number of entrants in inEventList subcollection
 *
 * Related User Stories: US 02.02.01, US 02.06.01
 */
public class OrganizerDashboardActivity extends AppCompatActivity {

    private static final String TAG = "OrganizerDashboard";

    // UI Components
    private RecyclerView recyclerViewEvents;
    private ProgressBar progressBar;
    private View emptyStateLayout;
    private FloatingActionButton fabCreateEvent;

    // Data
    private EventRepository eventRepository;
    private FirebaseFirestore db;
    private OrganizerEventAdapter eventAdapter;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_organizer_dashboard);

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize components
        initializeViews();
        initializeData();
        setupRecyclerView();
        setupFAB();

        // Load organizer's events
        loadOrganizerEvents();
    }

    /**
     * Initialize view references
     */
    private void initializeViews() {
        recyclerViewEvents = findViewById(R.id.recyclerViewEvents);
        progressBar = findViewById(R.id.progressBar);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        fabCreateEvent = findViewById(R.id.fabCreateEvent);
    }

    /**
     * Initialize data components
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        db = com.example.pickme.services.FirebaseManager.getFirestore();
        eventAdapter = new OrganizerEventAdapter();

        // Get device ID asynchronously
        DeviceAuthenticator deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        deviceAuthenticator.getDeviceId(deviceId -> currentUserId = deviceId);
    }

    /**
     * Setup RecyclerView with adapter
     */
    private void setupRecyclerView() {
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewEvents.setAdapter(eventAdapter);

        // Set click listener for events
        eventAdapter.setOnEventClickListener(event -> {
            // Navigate to ManageEventActivity
            Intent intent = new Intent(this, ManageEventActivity.class);
            intent.putExtra(ManageEventActivity.EXTRA_EVENT_ID, event.getEventId());
            startActivity(intent);
        });
    }

    /**
     * Setup FAB click listener
     */
    private void setupFAB() {
        fabCreateEvent.setOnClickListener(v -> {
            // Navigate to CreateEventActivity
            Intent intent = new Intent(this, CreateEventActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Load all events created by the current organizer
     */
    private void loadOrganizerEvents() {
        if (currentUserId == null) {
            // Wait for device ID to be loaded
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(this::loadOrganizerEvents, 100);
            return;
        }

        showLoading(true);

        eventRepository.getEventsByOrganizer(currentUserId, new EventRepository.OnEventsLoadedListener() {
            @Override
            public void onEventsLoaded(List<Event> events) {
                showLoading(false);

                if (events.isEmpty()) {
                    showEmptyState(true);
                } else {
                    showEmptyState(false);
                    eventAdapter.setEvents(events);

                    // Load metrics for each event
                    loadEventMetrics(events);
                }
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(OrganizerDashboardActivity.this,
                        R.string.error_occurred, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Load metrics (waiting list, selected, enrolled counts) for all events
     */
    private void loadEventMetrics(List<Event> events) {
        Log.d(TAG, "loadEventMetrics called for " + events.size() + " events");
        Map<String, OrganizerEventAdapter.EventMetrics> metricsMap = new HashMap<>();
        int[] pendingLoads = {events.size() * 3}; // 3 subcollections per event

        for (Event event : events) {
            String eventId = event.getEventId();
            Log.d(TAG, "Loading metrics for event: " + eventId + " (" + event.getName() + ")");

            // Initialize metrics
            metricsMap.put(eventId, new OrganizerEventAdapter.EventMetrics(0, 0, 0));

            // Load waiting list count
            db.collection("events")
                    .document(eventId)
                    .collection("waitingList")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int count = querySnapshot.size();
                        Log.d(TAG, "Event " + eventId + " waiting list count: " + count);
                        OrganizerEventAdapter.EventMetrics metrics = metricsMap.get(eventId);
                        if (metrics != null) {
                            metrics.waitingListCount = count;
                        }
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load waiting list for " + eventId, e);
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    });

            // Load selected count (responsePendingList)
            db.collection("events")
                    .document(eventId)
                    .collection("responsePendingList")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int count = querySnapshot.size();
                        Log.d(TAG, "Event " + eventId + " selected count: " + count);
                        OrganizerEventAdapter.EventMetrics metrics = metricsMap.get(eventId);
                        if (metrics != null) {
                            metrics.selectedCount = count;
                        }
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load selected list for " + eventId, e);
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    });

            // Load enrolled count (inEventList)
            db.collection("events")
                    .document(eventId)
                    .collection("inEventList")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        int count = querySnapshot.size();
                        Log.d(TAG, "Event " + eventId + " enrolled count: " + count);
                        OrganizerEventAdapter.EventMetrics metrics = metricsMap.get(eventId);
                        if (metrics != null) {
                            metrics.enrolledCount = count;
                        }
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to load enrolled list for " + eventId, e);
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    });
        }
    }

    /**
     * Check if all metrics have been loaded and update adapter
     */
    private void checkMetricsLoadComplete(int[] pendingLoads,
                                         Map<String, OrganizerEventAdapter.EventMetrics> metricsMap) {
        synchronized (pendingLoads) {
            pendingLoads[0]--;
            Log.d(TAG, "Pending loads remaining: " + pendingLoads[0]);
            if (pendingLoads[0] == 0) {
                // All metrics loaded - update adapter
                Log.d(TAG, "All metrics loaded! Updating adapter with metrics:");
                for (Map.Entry<String, OrganizerEventAdapter.EventMetrics> entry : metricsMap.entrySet()) {
                    OrganizerEventAdapter.EventMetrics m = entry.getValue();
                    Log.d(TAG, "  Event " + entry.getKey() + ": waiting=" + m.waitingListCount +
                          ", selected=" + m.selectedCount + ", enrolled=" + m.enrolledCount);
                }
                runOnUiThread(() -> eventAdapter.setEventMetrics(metricsMap));
            }
        }
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewEvents.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * Show/hide empty state
     */
    private void showEmptyState(boolean show) {
        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewEvents.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload events when returning to this activity
        if (currentUserId != null) {
            loadOrganizerEvents();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

