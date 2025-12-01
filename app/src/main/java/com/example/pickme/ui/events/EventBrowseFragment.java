package com.example.pickme.ui.events;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.utils.Constants;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * EventBrowseFragment - Browse and discover events
 *
 * Features:
 * - Load events where registration is open
 * - Search and filter events by text
 * - Advanced filtering by date range, location, and event type
 * - View event cards in RecyclerView
 * - Navigate to event details
 * - Scan QR codes to join events
 * - Filter indicator shows when filters are active
 *
 * Related User Stories: US 01.01.03, US 01.01.04, US 01.06.01, US 01.06.02
 */
public class EventBrowseFragment extends Fragment implements EventFilterDialogFragment.OnFilterAppliedListener {

    public static final String EXTRA_EVENT_ID = "event_id";

    // UI
    private SearchView searchView;
    private ImageButton btnFilter;
    private View filterActiveIndicator;
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
    private List<Event> allEvents = new ArrayList<>();
    private List<Event> filteredEvents = new ArrayList<>();
    private String currentUserId;

    // Basic filter state (chips)
    private String searchQuery = "";
    private FilterType currentFilter = FilterType.ALL;

    // Advanced filter state
    private Long filterStartDate = null;
    private Long filterEndDate = null;
    private String filterLocation = null;
    private String filterEventType = Constants.EVENT_TYPE_ALL;

    private enum FilterType { ALL, OPEN, UPCOMING }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_event_browser, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        initializeViews(root);
        initializeData();
        setupRecyclerView();
        setupSearchView();
        setupFilterChips();
        setupFilterButton();
        setupFAB();

        loadEvents();
    }

    private void initializeViews(View root) {
        searchView = root.findViewById(R.id.searchView);
        btnFilter = root.findViewById(R.id.btnFilter);
        filterActiveIndicator = root.findViewById(R.id.filterActiveIndicator);
        chipGroupFilter = root.findViewById(R.id.chipGroupFilter);
        chipAll = root.findViewById(R.id.chipAll);
        chipOpen = root.findViewById(R.id.chipOpen);
        chipUpcoming = root.findViewById(R.id.chipUpcoming);
        recyclerViewEvents = root.findViewById(R.id.recyclerViewEvents);
        progressBar = root.findViewById(R.id.progressBar);
        emptyStateLayout = root.findViewById(R.id.emptyStateLayout);
        tvEmptyMessage = root.findViewById(R.id.tvEmptyMessage);
        fabScanQR = root.findViewById(R.id.fabScanQR);
    }

    private void initializeData() {
        eventRepository = new EventRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(requireContext());
        currentUserId = deviceAuthenticator.getStoredUserId();
    }

    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(0);
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewEvents.setAdapter(eventAdapter);

        eventAdapter.setOnEventClickListener(event -> {
            if (!isAdded() || getContext() == null) return;
            Intent intent = new Intent(requireContext(), EventDetailsActivity.class);
            intent.putExtra(EXTRA_EVENT_ID, event.getEventId());
            startActivity(intent);
        });
    }

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

    private void setupFilterChips() {
        chipGroupFilter.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.contains(R.id.chipAll)) currentFilter = FilterType.ALL;
            else if (checkedIds.contains(R.id.chipOpen)) currentFilter = FilterType.OPEN;
            else if (checkedIds.contains(R.id.chipUpcoming)) currentFilter = FilterType.UPCOMING;
            applyFilters();
        });
    }

    private void setupFilterButton() {
        btnFilter.setOnClickListener(v -> showFilterDialog());
    }

    private void setupFAB() {
        fabScanQR.setOnClickListener(v -> launchQRScanner());
    }

    /**
     * Show the filter dialog with current filter state
     */
    private void showFilterDialog() {
        if (!isAdded()) return;

        // Collect unique locations from all events
        List<String> availableLocations = getUniqueLocations();

        EventFilterDialogFragment filterDialog = EventFilterDialogFragment.newInstance(availableLocations);
        filterDialog.setCurrentFilters(filterStartDate, filterEndDate, filterLocation, filterEventType);
        filterDialog.setOnFilterAppliedListener(this);
        filterDialog.show(getChildFragmentManager(), EventFilterDialogFragment.TAG);
    }

    /**
     * Get unique locations from all loaded events
     */
    private List<String> getUniqueLocations() {
        Set<String> locationSet = new HashSet<>();
        for (Event event : allEvents) {
            if (event.getLocation() != null && !event.getLocation().trim().isEmpty()) {
                locationSet.add(event.getLocation());
            }
        }
        return new ArrayList<>(locationSet);
    }

    /**
     * Callback when filters are applied from the dialog
     */
    @Override
    public void onFilterApplied(Long startDate, Long endDate, String location, String eventType) {
        filterStartDate = startDate;
        filterEndDate = endDate;
        filterLocation = location;
        filterEventType = eventType;

        // Update filter indicator
        updateFilterIndicator();

        // Apply filters
        applyFilters();
    }

    /**
     * Update the filter active indicator visibility
     */
    private void updateFilterIndicator() {
        boolean hasActiveFilters = filterStartDate != null
                || filterEndDate != null
                || filterLocation != null
                || (filterEventType != null && !Constants.EVENT_TYPE_ALL.equals(filterEventType));

        if (filterActiveIndicator != null) {
            filterActiveIndicator.setVisibility(hasActiveFilters ? View.VISIBLE : View.GONE);
        }
    }

    private void loadEvents() {
        showLoading(true);
        eventRepository.getEventsForEntrant(new EventRepository.OnEventsLoadedListener() {
            @Override
            public void onEventsLoaded(List<Event> events) {
                // Check if fragment is still attached before updating UI
                if (!isAdded() || getContext() == null) {
                    return;
                }
                allEvents = events;
                applyFilters();
                showLoading(false);
            }

            @Override
            public void onError(Exception e) {
                // Check if fragment is still attached before updating UI
                if (!isAdded() || getContext() == null) {
                    return;
                }
                showLoading(false);
                showEmptyState(true, getString(R.string.error_occurred));
                Toast.makeText(getContext(),
                        getString(R.string.error_occurred) + ": " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Apply all filters (search, chips, and advanced filters)
     */
    private void applyFilters() {
        // Safety check for fragment lifecycle
        if (!isAdded()) return;

        filteredEvents = new ArrayList<>(allEvents);

        // 1. Apply search query filter
        if (!searchQuery.isEmpty()) {
            filteredEvents = filteredEvents.stream()
                    .filter(event -> event.getName().toLowerCase().contains(searchQuery.toLowerCase())
                            || (event.getLocation() != null
                            && event.getLocation().toLowerCase().contains(searchQuery.toLowerCase())))
                    .collect(Collectors.toList());
        }

        // 2. Apply chip filter (All/Open/Upcoming)
        long now = System.currentTimeMillis();
        switch (currentFilter) {
            case OPEN:
                filteredEvents = filteredEvents.stream()
                        .filter(Event::isRegistrationOpen)
                        .collect(Collectors.toList());
                break;
            case UPCOMING:
                filteredEvents = filteredEvents.stream()
                        .filter(e -> e.getRegistrationStartDate() > now)
                        .collect(Collectors.toList());
                break;
            case ALL:
            default:
                break;
        }

        // 3. Apply date range filter
        if (filterStartDate != null || filterEndDate != null) {
            filteredEvents = filteredEvents.stream()
                    .filter(event -> {
                        // Check if any event date falls within the range
                        if (event.getEventDates() == null || event.getEventDates().isEmpty()) {
                            return false; // No dates = doesn't match date filter
                        }

                        for (Long eventDate : event.getEventDates()) {
                            boolean afterStart = filterStartDate == null || eventDate >= filterStartDate;
                            boolean beforeEnd = filterEndDate == null || eventDate <= filterEndDate;
                            if (afterStart && beforeEnd) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toList());
        }

        // 4. Apply location filter
        if (filterLocation != null && !filterLocation.isEmpty()) {
            filteredEvents = filteredEvents.stream()
                    .filter(event -> filterLocation.equalsIgnoreCase(event.getLocation()))
                    .collect(Collectors.toList());
        }

        // 5. Apply event type filter
        if (filterEventType != null && !Constants.EVENT_TYPE_ALL.equals(filterEventType)) {
            filteredEvents = filteredEvents.stream()
                    .filter(event -> filterEventType.equalsIgnoreCase(event.getEventType()))
                    .collect(Collectors.toList());
        }

        // Update UI
        eventAdapter.setEvents(filteredEvents);

        if (filteredEvents.isEmpty()) {
            String msg = hasAnyFilter()
                    ? getString(R.string.no_events_match_filters)
                    : getString(R.string.no_events_available);
            showEmptyState(true, msg);
        } else {
            showEmptyState(false, "");
        }
    }

    /**
     * Check if any filter is currently active
     */
    private boolean hasAnyFilter() {
        return !searchQuery.isEmpty()
                || currentFilter != FilterType.ALL
                || filterStartDate != null
                || filterEndDate != null
                || filterLocation != null
                || (filterEventType != null && !Constants.EVENT_TYPE_ALL.equals(filterEventType));
    }

    /**
     * Clear all advanced filters (called if needed)
     */
    public void clearAdvancedFilters() {
        filterStartDate = null;
        filterEndDate = null;
        filterLocation = null;
        filterEventType = Constants.EVENT_TYPE_ALL;
        updateFilterIndicator();
        applyFilters();
    }

    private void launchQRScanner() {
        if (!isAdded() || getContext() == null) return;

        if (getActivity() instanceof com.example.pickme.MainActivity) {
            ((com.example.pickme.MainActivity) getActivity()).launchQRScanner();
        } else {
            Toast.makeText(getContext(),
                    "QR scanner not available",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void showLoading(boolean show) {
        if (!isAdded() || progressBar == null || recyclerViewEvents == null) return;

        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewEvents.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show, String message) {
        if (!isAdded() || emptyStateLayout == null || recyclerViewEvents == null) return;

        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewEvents.setVisibility(show ? View.GONE : View.VISIBLE);
        if (tvEmptyMessage != null) {
            tvEmptyMessage.setText(message);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
    }
}