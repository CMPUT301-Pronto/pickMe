package com.example.pickme.ui.events;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.utils.Constants;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * BaseEntrantListFragment - Abstract base class for entrant list display fragments
 *
 * Provides common functionality for displaying lists of entrants with different configurations.
 * Eliminates code duplication across WaitingList, Selected, Confirmed, and Cancelled fragments.
 *
 * Features:
 * - Lifecycle-aware profile loading with proper cancellation
 * - Thread-safe profile aggregation using AtomicInteger
 * - Configurable adapter display options
 * - Common loading, error, and empty state handling
 * - Memory leak prevention through lifecycle checks
 * - Support for entrant actions (cancel, etc.) via adapter action listener
 *
 * Subclasses must implement:
 * - getSubcollectionName(): Firestore subcollection to query
 * - showJoinTime(): Whether adapter should show join timestamps
 * - showStatus(): Whether adapter should show entrant status
 * - getLogTag(): Tag for logging
 *
 * Subclasses may override:
 * - showCancelOption(): Whether to show cancel option in menu (default: false)
 * - getActionListener(): Listener for entrant actions (default: null)
 *
 * Related User Stories: US 02.02.01, US 02.06.01, US 02.06.02, US 02.06.04
 */
public abstract class BaseEntrantListFragment extends Fragment {

    // UI Components
    protected RecyclerView recyclerView;
    protected ProgressBar progressBar;
    protected View emptyStateLayout;

    // Data
    protected EventRepository eventRepository;
    protected ProfileRepository profileRepository;
    protected EntrantAdapter adapter;
    protected String eventId;

    // Thread-safe tracking
    private final AtomicInteger pendingLoads = new AtomicInteger(0);
    private final List<Profile> loadedProfiles = new ArrayList<>();

    /**
     * Get the Firestore subcollection name to query
     * @return Subcollection name (e.g., "waitingList", "inEventList")
     */
    protected abstract String getSubcollectionName();

    /**
     * Configure whether adapter should show join timestamps
     * @return true to show join time, false to hide
     */
    protected abstract boolean showJoinTime();

    /**
     * Configure whether adapter should show entrant status
     * @return true to show status, false to hide
     */
    protected abstract boolean showStatus();

    /**
     * Get logging tag for this fragment
     * @return Tag for Log statements
     */
    protected abstract String getLogTag();

    /**
     * Configure whether adapter should show cancel option in menu
     * Override in subclasses that need cancel functionality (e.g., SelectedEntrantsFragment)
     * @return true to show cancel option, false to hide (default)
     */
    protected boolean showCancelOption() {
        return false;
    }

    /**
     * Get the action listener for entrant menu actions
     * Override in subclasses that handle actions (e.g., SelectedEntrantsFragment)
     * @return Action listener or null (default)
     */
    protected EntrantAdapter.OnEntrantActionListener getActionListener() {
        return null;
    }

    /**
     * Create new instance with event ID
     * @param eventId Event ID to display entrants for
     * @return Fragment instance
     */
    public static BaseEntrantListFragment newInstance(String eventId, BaseEntrantListFragment fragment) {
        Bundle args = new Bundle();
        args.putString(Constants.ARG_EVENT_ID, eventId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId = getArguments().getString(Constants.ARG_EVENT_ID);
        }
        eventRepository = new EventRepository();
        profileRepository = new ProfileRepository();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_entrant_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupAdapter();
        loadEntrantList();
    }

    /**
     * Initialize view references
     */
    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerViewEntrants);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
    }

    /**
     * Setup RecyclerView and adapter
     * Now includes cancel option and action listener configuration
     */
    private void setupAdapter() {
        // Create adapter with all configuration options
        adapter = new EntrantAdapter(showJoinTime(), showStatus(), showCancelOption());

        // Set action listener if provided by subclass
        EntrantAdapter.OnEntrantActionListener actionListener = getActionListener();
        if (actionListener != null) {
            adapter.setOnEntrantActionListener(actionListener);
        }

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
    }

    /**
     * Load entrant list from Firestore
     * Handles special case for waiting list which has timestamps
     */
    protected void loadEntrantList() {
        showLoading(true);
        Log.d(getLogTag(), "Loading entrant list for event: " + eventId);

        // Special handling for waiting list (has timestamps)
        if (Constants.SUBCOLLECTION_WAITING_LIST.equals(getSubcollectionName())) {
            loadWaitingList();
        } else {
            loadStandardList();
        }
    }

    /**
     * Load waiting list with timestamps
     */
    private void loadWaitingList() {
        eventRepository.getWaitingListForEvent(eventId, new EventRepository.OnWaitingListLoadedListener() {
            @Override
            public void onWaitingListLoaded(com.example.pickme.models.WaitingList waitingList) {
                if (!isAdded() || getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                    Log.w(getLogTag(), "Fragment not active, ignoring waiting list result");
                    return;
                }

                Log.d(getLogTag(), "Waiting list loaded with " + waitingList.getEntrantCount() + " entrants");

                if (waitingList.getEntrantCount() == 0) {
                    showEmptyState();
                } else {
                    loadProfiles(waitingList.getEntrantIds(), waitingList.getEntrantTimestamps());
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;
                Log.e(getLogTag(), "Failed to load waiting list", e);
                showError("Failed to load waiting list: " + e.getMessage());
            }
        });
    }

    /**
     * Load standard entrant list (selected, confirmed, cancelled)
     */
    private void loadStandardList() {
        eventRepository.getEntrantIdsFromSubcollection(eventId, getSubcollectionName(),
                new EventRepository.OnEntrantIdsLoadedListener() {
                    @Override
                    public void onEntrantIdsLoaded(List<String> entrantIds) {
                        if (!isAdded() || getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                            Log.w(getLogTag(), "Fragment not active, ignoring entrant IDs result");
                            return;
                        }

                        Log.d(getLogTag(), "Loaded " + entrantIds.size() + " entrant IDs");

                        if (entrantIds.isEmpty()) {
                            showEmptyState();
                        } else {
                            loadProfiles(entrantIds, null);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        if (!isAdded()) return;
                        Log.e(getLogTag(), "Failed to load entrant list", e);
                        showError("Failed to load entrant list: " + e.getMessage());
                    }
                });
    }

    /**
     * Load profiles for all entrant IDs
     * Thread-safe implementation with AtomicInteger to prevent race conditions
     *
     * @param entrantIds List of user IDs to load profiles for
     * @param timestamps Optional map of join timestamps (for waiting list)
     */
    private void loadProfiles(List<String> entrantIds, @Nullable Map<String, Long> timestamps) {
        if (entrantIds == null || entrantIds.isEmpty()) {
            showEmptyState();
            return;
        }

        Log.d(getLogTag(), "Loading profiles for " + entrantIds.size() + " entrants");

        // Reset state
        loadedProfiles.clear();
        pendingLoads.set(entrantIds.size());

        // Load each profile
        for (String entrantId : entrantIds) {
            loadSingleProfile(entrantId, entrantIds.size(), timestamps);
        }
    }

    /**
     * Load a single profile with lifecycle awareness
     *
     * @param entrantId User ID to load
     * @param totalCount Total number of profiles being loaded
     * @param timestamps Optional timestamps map
     */
    private void loadSingleProfile(String entrantId, int totalCount, @Nullable Map<String, Long> timestamps) {
        profileRepository.getProfile(entrantId, new ProfileRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(Profile profile) {
                // Check if fragment is still active
                if (!isAdded() || getLifecycle().getCurrentState() == Lifecycle.State.DESTROYED) {
                    Log.w(getLogTag(), "Fragment destroyed, cancelling profile load");
                    return;
                }

                // Thread-safe add to list
                synchronized (loadedProfiles) {
                    loadedProfiles.add(profile);
                }

                // Decrement counter and check if all loaded
                int remaining = pendingLoads.decrementAndGet();
                Log.d(getLogTag(), "Profile loaded: " + profile.getName() + ", remaining: " + remaining);

                if (remaining == 0) {
                    onAllProfilesLoaded(timestamps);
                }
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return;

                Log.w(getLogTag(), "Failed to load profile: " + entrantId, e);

                // Still decrement counter
                int remaining = pendingLoads.decrementAndGet();

                if (remaining == 0) {
                    onAllProfilesLoaded(timestamps);
                }
            }
        });
    }

    /**
     * Called when all profiles have finished loading
     *
     * @param timestamps Optional timestamps map
     */
    private void onAllProfilesLoaded(@Nullable Map<String, Long> timestamps) {
        if (!isAdded()) return;

        Log.d(getLogTag(), "All profiles loaded: " + loadedProfiles.size());

        showLoading(false);

        if (loadedProfiles.isEmpty()) {
            showEmptyState();
        } else {
            showContent();
            adapter.setProfiles(loadedProfiles);
            if (timestamps != null && showJoinTime()) {
                adapter.setJoinTimestamps(timestamps);
            }
        }
    }

    /**
     * Show loading state
     * Made protected so subclasses can call it
     */
    protected void showLoading(boolean show) {
        if (!isAdded()) return;

        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    /**
     * Show empty state
     */
    protected void showEmptyState() {
        if (!isAdded()) return;

        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.VISIBLE);
    }

    /**
     * Show content state
     */
    protected void showContent() {
        if (!isAdded()) return;

        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.VISIBLE);
        emptyStateLayout.setVisibility(View.GONE);
    }

    /**
     * Show error message
     */
    protected void showError(String message) {
        if (!isAdded()) return;

        showLoading(false);
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    /**
     * Refresh the entrant list
     * Called by parent activity when data may have changed
     */
    public void refresh() {
        if (isAdded()) {
            loadEntrantList();
        }
    }

    /**
     * Get the current event ID
     * @return Event ID or null if not set
     */
    protected String getEventId() {
        return eventId;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        // Cancel any pending loads
        pendingLoads.set(0);
        loadedProfiles.clear();
    }
}