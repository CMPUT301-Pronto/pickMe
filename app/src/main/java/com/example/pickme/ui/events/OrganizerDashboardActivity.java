package com.example.pickme.ui.events;

import android.content.Intent;
import android.os.Bundle;
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
            // TODO: Navigate to ManageEventActivity when implemented
            Toast.makeText(this, "Manage: " + event.getName(), Toast.LENGTH_SHORT).show();
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
        Map<String, OrganizerEventAdapter.EventMetrics> metricsMap = new HashMap<>();
        int[] pendingLoads = {events.size() * 3}; // 3 subcollections per event

        for (Event event : events) {
            String eventId = event.getEventId();

            // Initialize metrics
            metricsMap.put(eventId, new OrganizerEventAdapter.EventMetrics(0, 0, 0));

            // Load waiting list count
            db.collection("events")
                    .document(eventId)
                    .collection("waitingList")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        OrganizerEventAdapter.EventMetrics metrics = metricsMap.get(eventId);
                        if (metrics != null) {
                            metrics.waitingListCount = querySnapshot.size();
                        }
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    })
                    .addOnFailureListener(e -> {
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    });

            // Load selected count (responsePendingList)
            db.collection("events")
                    .document(eventId)
                    .collection("responsePendingList")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        OrganizerEventAdapter.EventMetrics metrics = metricsMap.get(eventId);
                        if (metrics != null) {
                            metrics.selectedCount = querySnapshot.size();
                        }
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    })
                    .addOnFailureListener(e -> {
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    });

            // Load enrolled count (inEventList)
            db.collection("events")
                    .document(eventId)
                    .collection("inEventList")
                    .get()
                    .addOnSuccessListener(querySnapshot -> {
                        OrganizerEventAdapter.EventMetrics metrics = metricsMap.get(eventId);
                        if (metrics != null) {
                            metrics.enrolledCount = querySnapshot.size();
                        }
                        checkMetricsLoadComplete(pendingLoads, metricsMap);
                    })
                    .addOnFailureListener(e -> {
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
            if (pendingLoads[0] == 0) {
                // All metrics loaded - update adapter
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

