package com.example.pickme.ui.events;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
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
 * EventBrowseFragment - Browse and discover events
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

public class EventBrowseFragment extends Fragment {

    public static final String EXTRA_EVENT_ID = "event_id";

    // UI
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
    private List<Event> allEvents = new ArrayList<>();
    private List<Event> filteredEvents = new ArrayList<>();
    private String currentUserId;
    private QRCodeScanner qrCodeScanner;

    // Filter state
    private String searchQuery = "";
    private FilterType currentFilter = FilterType.ALL;

    private enum FilterType { ALL, OPEN, UPCOMING }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Reuse your existing layout (consider renaming to fragment_event_browse.xml)
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
        setupFAB();

        loadEvents();
    }
    private void initializeViews(View root) {
        searchView = root.findViewById(R.id.searchView);
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
        qrCodeScanner = new QRCodeScanner();
        currentUserId = deviceAuthenticator.getStoredUserId();
    }

    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(0);
        recyclerViewEvents.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerViewEvents.setAdapter(eventAdapter);

        eventAdapter.setOnEventClickListener(event -> {
            Intent intent = new Intent(requireContext(), EventDetailsActivity.class);
            intent.putExtra(EXTRA_EVENT_ID, event.getEventId());
            startActivity(intent);
        });
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) {
                searchQuery = query;
                applyFilters();
                return true;
            }
            @Override public boolean onQueryTextChange(String newText) {
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

    private void setupFAB() {
        fabScanQR.setOnClickListener(v -> launchQRScanner());
    }

    private void loadEvents() {
        showLoading(true);
        eventRepository.getEventsForEntrant(new EventRepository.OnEventsLoadedListener() {
            @Override public void onEventsLoaded(List<Event> events) {
                allEvents = events;
                applyFilters();
                showLoading(false);
            }
            @Override public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(requireContext(),
                        getString(R.string.error_occurred) + ": " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                showEmptyState(true, getString(R.string.error_occurred));
            }
        });
    }

    private void applyFilters() {
        filteredEvents = new ArrayList<>(allEvents);

        if (!searchQuery.isEmpty()) {
            filteredEvents = filteredEvents.stream()
                    .filter(event -> event.getName().toLowerCase().contains(searchQuery.toLowerCase())
                            || (event.getLocation() != null
                            && event.getLocation().toLowerCase().contains(searchQuery.toLowerCase())))
                    .collect(Collectors.toList());
        }

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

        eventAdapter.setEvents(filteredEvents);

        if (filteredEvents.isEmpty()) {
            String msg = searchQuery.isEmpty()
                    ? getString(R.string.no_events_available)
                    : getString(R.string.no_events_found);
            showEmptyState(true, msg);
        } else {
            showEmptyState(false, "");
        }
    }

    private void launchQRScanner() {
        // If your QRCodeScanner expects an Activity, pass requireActivity().
        // If it supports Fragments, passing `this` is fine.
        qrCodeScanner.startScanning(requireActivity());
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        if (result == null) return;

        qrCodeScanner.parseScannedResult(result, new QRCodeScanner.OnQRScannedListener() {
            @Override public void onQRScanned(Event event) {
                Intent intent = new Intent(requireContext(), EventDetailsActivity.class);
                intent.putExtra(EXTRA_EVENT_ID, event.getEventId());
                startActivity(intent);
            }
            @Override public void onError(Exception e) {
                Toast.makeText(requireContext(),
                        "Invalid QR code: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewEvents.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    private void showEmptyState(boolean show, String message) {
        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewEvents.setVisibility(show ? View.GONE : View.VISIBLE);
        tvEmptyMessage.setText(message);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadEvents();
    }
}
