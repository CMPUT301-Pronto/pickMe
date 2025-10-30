package com.example.pickme.ui.events;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.QRCodeScanner;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * EventBrowserActivity - Browse and discover events
 *
 * Features:
 * - Load events where registration is open
 * - Search and filter events
 * - View event cards in RecyclerView
 * - Navigate to event details
 * - Scan QR codes to join events
 *
 * Related User Stories: US 01.01.03, US 01.01.04, US 01.06.01, US 01.06.02
 */
public class EventBrowserActivity extends AppCompatActivity {

    private static final String TAG = "EventBrowserActivity";
    public static final String EXTRA_EVENT_ID = "event_id";

    // UI Components
    private SearchView searchView;
    private ChipGroup chipGroupFilter;
    private Chip chipAll, chipOpen, chipUpcoming;
    private RecyclerView recyclerViewEvents;
    private ProgressBar progressBar;
    private View emptyStateLayout;
    private TextView tvEmptyMessage;
    private FloatingActionButton fabScanQR;

    // Data
    private EventRepository eventRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private EventAdapter eventAdapter;
    private List<Event> allEvents;
    private List<Event> filteredEvents;
    private String currentUserId;
    private QRCodeScanner qrCodeScanner;

    // Filter state
    private String searchQuery = "";
    private FilterType currentFilter = FilterType.ALL;

    private enum FilterType {
        ALL, OPEN, UPCOMING
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_browser);

        // Initialize
        initializeViews();
        initializeData();
        setupRecyclerView();
        setupSearchView();
        setupFilterChips();
        setupFAB();

        // Load events
        loadEvents();
    }

    /**
     * Initialize view references
     */
    private void initializeViews() {
        searchView = findViewById(R.id.searchView);
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        chipAll = findViewById(R.id.chipAll);
        chipOpen = findViewById(R.id.chipOpen);
        chipUpcoming = findViewById(R.id.chipUpcoming);
        recyclerViewEvents = findViewById(R.id.recyclerViewEvents);
        progressBar = findViewById(R.id.progressBar);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        fabScanQR = findViewById(R.id.fabScanQR);
    }

    /**
     * Initialize data components
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        qrCodeScanner = new QRCodeScanner();
        allEvents = new ArrayList<>();
        filteredEvents = new ArrayList<>();

        currentUserId = deviceAuthenticator.getStoredUserId();
    }

    /**
     * Setup RecyclerView with adapter
     */
    private void setupRecyclerView() {
        eventAdapter = new EventAdapter();
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewEvents.setAdapter(eventAdapter);

        eventAdapter.setOnEventClickListener(event -> {
            // Navigate to event details
            Intent intent = new Intent(EventBrowserActivity.this, EventDetailsActivity.class);
            intent.putExtra(EXTRA_EVENT_ID, event.getEventId());
            startActivity(intent);
        });
    }

    /**
     * Setup search view
     */
    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                applyFilters();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchQuery = newText;
                applyFilters();
                return true;
            }
        });
    }

    /**
     * Setup filter chips
     */
    private void setupFilterChips() {
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipAll)) {
                currentFilter = FilterType.ALL;
            } else if (checkedIds.contains(R.id.chipOpen)) {
                currentFilter = FilterType.OPEN;
            } else if (checkedIds.contains(R.id.chipUpcoming)) {
                currentFilter = FilterType.UPCOMING;
            }
            applyFilters();
        });
    }

    /**
     * Setup FAB for QR scanning
     */
    private void setupFAB() {
        fabScanQR.setOnClickListener(v -> launchQRScanner());
    }

    /**
     * Load events from repository
     */
    private void loadEvents() {
        showLoading(true);

        // Load events for entrants (registration open)
        eventRepository.getEventsForEntrant(new EventRepository.OnEventsLoadedListener() {
            @Override
            public void onEventsLoaded(List<Event> events) {
                allEvents = events;
                applyFilters();
                showLoading(false);

                // TODO: Load user's joined events to show badges
                // For now, just display all events
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(EventBrowserActivity.this,
                        getString(R.string.error_occurred) + ": " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                showEmptyState(true, getString(R.string.error_occurred));
            }
        });
    }

    /**
     * Apply search and filter to events
     */
    private void applyFilters() {
        filteredEvents = new ArrayList<>(allEvents);

        // Apply search filter
        if (!searchQuery.isEmpty()) {
            filteredEvents = filteredEvents.stream()
                    .filter(event -> event.getName().toLowerCase().contains(searchQuery.toLowerCase())
                            || (event.getLocation() != null && event.getLocation().toLowerCase().contains(searchQuery.toLowerCase())))
                    .collect(Collectors.toList());
        }

        // Apply status filter
        long currentTime = System.currentTimeMillis();
        switch (currentFilter) {
            case OPEN:
                filteredEvents = filteredEvents.stream()
                        .filter(Event::isRegistrationOpen)
                        .collect(Collectors.toList());
                break;
            case UPCOMING:
                filteredEvents = filteredEvents.stream()
                        .filter(event -> event.getRegistrationStartDate() > currentTime)
                        .collect(Collectors.toList());
                break;
            case ALL:
            default:
                // No additional filter
                break;
        }

        // Update adapter
        eventAdapter.setEvents(filteredEvents);

        // Show/hide empty state
        if (filteredEvents.isEmpty()) {
            String message = searchQuery.isEmpty()
                    ? getString(R.string.no_events_available)
                    : getString(R.string.no_events_found);
            showEmptyState(true, message);
        } else {
            showEmptyState(false, "");
        }
    }

    /**
     * Launch QR code scanner
     */
    private void launchQRScanner() {
        qrCodeScanner.startScanning(this);
    }

    /**
     * Handle QR scan result
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result != null) {
            qrCodeScanner.parseScannedResult(result, new QRCodeScanner.OnQRScannedListener() {
                @Override
                public void onQRScanned(Event event) {
                    // Navigate to event details
                    Intent intent = new Intent(EventBrowserActivity.this, EventDetailsActivity.class);
                    intent.putExtra(EXTRA_EVENT_ID, event.getEventId());
                    startActivity(intent);
                }

                @Override
                public void onError(Exception e) {
                    Toast.makeText(EventBrowserActivity.this,
                            "Invalid QR code: " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            super.onActivityResult(requestCode, resultCode, data);
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
    private void showEmptyState(boolean show, String message) {
        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewEvents.setVisibility(show ? View.GONE : View.VISIBLE);
        tvEmptyMessage.setText(message);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reload events when returning from details
        loadEvents();
    }
}

