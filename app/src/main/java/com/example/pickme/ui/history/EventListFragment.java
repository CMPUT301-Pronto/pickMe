package com.example.pickme.ui.history;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.ui.events.EventAdapter;
import com.example.pickme.ui.events.EventDetailsActivity;

import java.util.ArrayList;
import java.util.List;
/**
 * JAVADOCS LLM GENERATED
 *
 * # EventListFragment
 * Reusable fragment responsible for displaying lists of events in various states.
 *
 * <p>Used in {@link EventHistoryActivity}'s ViewPager2 to show:</p>
 * <ul>
 *     <li><b>Upcoming</b> — Accepted invitations (user in {@code inEventList})</li>
 *     <li><b>Waiting</b> — Joined events awaiting lottery draw ({@code waitingList})</li>
 *     <li><b>Past</b> — Completed or cancelled events</li>
 *     <li><b>Cancelled</b> — Declined invitations ({@code cancelledList})</li>
 * </ul>
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *     <li>Inflates list layout with empty and loading states</li>
 *     <li>Binds {@link EventAdapter} for displaying events</li>
 *     <li>Handles item click navigation to {@link EventDetailsActivity}</li>
 * </ul>
 *
 * <p>Related User Stories: US 01.02.03, US 01.05.03</p>
 */
public class EventListFragment extends Fragment {

    private static final String TAG = "EventListFragment";
    private static final String ARG_TAB_TYPE = "tab_type";

    // Use consistent integer constants for all tab types
    public static final int TAB_UPCOMING = 0;
    public static final int TAB_WAITING = 1;
    public static final int TAB_PAST = 2;
    public static final int TAB_CANCELLED = 3;

    // UI Components
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyStateLayout;
    private TextView tvEmptyMessage;

    // Data
    private EventAdapter eventAdapter;
    private int tabType;
    private List<Event> events;

    /**
     * Factory method for creating a new {@link EventListFragment} instance for a given tab type.
     *
     * @param tabType One of {@link #TAB_UPCOMING}, {@link #TAB_WAITING},
     *                {@link #TAB_PAST}, or {@link #TAB_CANCELLED}.
     * @return Configured fragment instance.
     */
    public static EventListFragment newInstance(int tabType) {
        EventListFragment fragment = new EventListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TAB_TYPE, tabType);
        fragment.setArguments(args);
        return fragment;
    }

    /** Initializes data from arguments. */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabType = getArguments().getInt(ARG_TAB_TYPE);
            Log.d(TAG, "onCreate: tabType set to " + tabType);
        } else {
            Log.e(TAG, "onCreate: No arguments found! tabType will be 0 by default");
        }
        events = new ArrayList<>();
    }
    /** Inflates the fragment layout. */
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_event_list, container, false);
    }
    /** Called when view hierarchy is created, initializes views and adapter. */
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Log.d(TAG, "onViewCreated for tabType=" + tabType + ", events size=" +
              (events != null ? events.size() : "null"));

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);

        // Setup RecyclerView
        setupRecyclerView();

        // Set empty message based on tab type
        setEmptyMessage();

        // Initial state: if we have events, show them; otherwise show empty state
        // The loading state should already be managed by showLoading() calls from Activity
        if (events != null && !events.isEmpty()) {
            showEmptyState(false);
            progressBar.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        } else {
            // Check if we're still loading or if we've already finished with no results
            // If events is an empty list (not null), we've already loaded and found nothing
            if (events != null && events.isEmpty()) {
                showEmptyState(true);
                progressBar.setVisibility(View.GONE);
            } else {
                // Still loading (events is null or wasn't set yet)
                progressBar.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                emptyStateLayout.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Setup RecyclerView with adapter
     */
    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(tabType);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(eventAdapter);

        Log.d(TAG, "setupRecyclerView called for tabType=" + tabType + ", events size=" +
              (events != null ? events.size() : "null"));

        // If events were set before view was created, apply them now
        if (events != null && !events.isEmpty()) {
            Log.d(TAG, "Setting " + events.size() + " events to adapter");
            eventAdapter.setEvents(events);
            showEmptyState(false);
        } else {
            Log.d(TAG, "No events to display, showing empty state");
            showEmptyState(true);
        }

        eventAdapter.setOnEventClickListener(event -> {
            // Navigate to event details
            Intent intent = new Intent(getContext(), EventDetailsActivity.class);
            intent.putExtra("event_id", event.getEventId());

            // Pass tab type to show appropriate status in details
            intent.putExtra("tab_type", tabType);

            // For cancelled tab, show as read-only (user already declined)
            if (tabType == TAB_CANCELLED) {
                intent.putExtra("status", "DECLINED");
                intent.putExtra("read_only", true);
            }

            startActivity(intent);
        });
    }

    /**
     * Set empty message based on tab type
     */
    private void setEmptyMessage() {
        switch (tabType) {
            case TAB_UPCOMING:
                tvEmptyMessage.setText(R.string.no_upcoming_events);
                break;
            case TAB_WAITING:
                tvEmptyMessage.setText(R.string.no_waiting_lists);
                break;
            case TAB_PAST:
                tvEmptyMessage.setText(R.string.no_past_events);
                break;
            case TAB_CANCELLED:
                tvEmptyMessage.setText(R.string.no_declined_events);
                break;
            default:
                tvEmptyMessage.setText(R.string.no_events);
                break;
        }
    }

    /**
     * Sets or updates the list of events displayed in this fragment.
     * Automatically toggles the empty state view.
     *
     * @param events List of {@link Event} objects to display.
     */
    public void setEvents(List<Event> events) {
        Log.d(TAG, "setEvents called for tabType=" + tabType +
              ", events size=" + (events != null ? events.size() : "null") +
              ", adapter initialized=" + (eventAdapter != null));

        this.events = events != null ? events : new ArrayList<>();

        // If adapter is initialized, update it immediately
        if (eventAdapter != null) {
            Log.d(TAG, "Adapter exists, updating with " + this.events.size() + " events");
            eventAdapter.setEvents(this.events);

            if (this.events.isEmpty()) {
                Log.d(TAG, "Events list is empty, showing empty state");
                showEmptyState(true);
            } else {
                Log.d(TAG, "Events list has data, hiding empty state");
                showEmptyState(false);
            }
        } else {
            // Otherwise, events are stored and will be set when view is created
            Log.d(TAG, "Adapter not yet initialized, events stored for later. Will be applied in setupRecyclerView()");
        }
    }

    /**
     * Show/hide loading indicator
     */
    public void showLoading(boolean show) {
        Log.d(TAG, "showLoading(" + show + ") for tabType=" + tabType);
        if (progressBar != null && recyclerView != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        } else {
            Log.w(TAG, "showLoading called but views not initialized yet");
        }
    }

    /**
     * Show/hide empty state
     */
    private void showEmptyState(boolean show) {
        if (emptyStateLayout != null && recyclerView != null) {
            emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Get tab type
     */
    public int getTabType() {
        return tabType;
    }
}