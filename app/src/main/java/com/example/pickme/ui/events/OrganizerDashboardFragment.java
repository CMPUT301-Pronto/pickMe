package com.example.pickme.ui.events;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
 * OrganizerDashboardFragment - Dashboard for organizers to manage their events
 *
 * Features:
 * - Display all events created by the organizer
 * - Show event statistics (waiting list, selected, enrolled counts)
 * - Navigate to ManageEventActivity to manage specific events
 * - FAB to create new events
 * - Empty state for organizers with no events
 *
 * Related User Stories: US 02.02.01, US 02.06.01
 */
public class OrganizerDashboardFragment extends Fragment {

    private static final String TAG = "OrganizerDashboardFrag";

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_organizer_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize components
        initializeViews(view);
        initializeData();
        setupRecyclerView();
        setupFAB();

        // Load organizer's events
        loadOrganizerEvents();
    }

    /**
     * Initialize view references
     */
    private void initializeViews(View view) {
        recyclerViewEvents = view.findViewById(R.id.recyclerViewEvents);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        fabCreateEvent = view.findViewById(R.id.fabCreateEvent);
    }

    /**
     * Initialize data components
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        db = com.example.pickme.services.FirebaseManager.getFirestore();
        eventAdapter = new OrganizerEventAdapter();

        // Get device ID
        DeviceAuthenticator deviceAuthenticator = DeviceAuthenticator.getInstance(requireContext());
        currentUserId = deviceAuthenticator.getStoredUserId();

        if (currentUserId == null) {
            deviceAuthenticator.getDeviceId(deviceId -> {
                currentUserId = deviceId;
                loadOrganizerEvents();
            });
        }
    }

    /**
     * Setup RecyclerView with adapter
     */
    private void setupRecyclerView() {
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewEvents.setAdapter(eventAdapter);

        // Set click listener for events
        eventAdapter.setOnEventClickListener(event -> {
            // Navigate to ManageEventActivity
            Intent intent = new Intent(requireContext(), ManageEventActivity.class);
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
            Intent intent = new Intent(requireContext(), CreateEventActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Load all events created by the current organizer
     */
    private void loadOrganizerEvents() {
        if (currentUserId == null) {
            // Wait for device ID to be loaded
            new Handler(Looper.getMainLooper()).postDelayed(this::loadOrganizerEvents, 100);
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
                Toast.makeText(requireContext(),
                        getString(R.string.error_occurred) + ": " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                showEmptyState(true);
            }
        });
    }

    /**
     * Load metrics for all events
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

                // Update on UI thread
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> eventAdapter.setEventMetrics(metricsMap));
                }
            }
        }
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Show/hide empty state
     */
    private void showEmptyState(boolean show) {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerViewEvents != null) {
            recyclerViewEvents.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload events when fragment becomes visible
        if (currentUserId != null) {
            loadOrganizerEvents();
        }
    }
}

