package com.example.pickme.ui.history;

import android.content.Intent;
import android.os.Bundle;
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
 * EventListFragment - Reusable fragment for displaying event lists
 *
 * Used in EventHistoryActivity's ViewPager2 to show:
 * - Upcoming events (accepted invitations)
 * - Waiting list events (currently joined)
 * - Past events (completed/cancelled)
 * - Cancelled events (declined invitations)
 */
public class EventListFragment extends Fragment {

    private static final String ARG_TAB_TYPE = "tab_type";

    // Use consistent integer constants for all tab types
    public static final int TAB_UPCOMING = 0;
    public static final int TAB_WAITING = 1;
    public static final int TAB_PAST = 2;
    public static final int TAB_CANCELLED = 3;  // CHANGED: Now an integer

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
     * Create new fragment instance
     */
    public static EventListFragment newInstance(int tabType) {
        EventListFragment fragment = new EventListFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_TAB_TYPE, tabType);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tabType = getArguments().getInt(ARG_TAB_TYPE);
        }
        events = new ArrayList<>();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_event_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize views
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);

        // Setup RecyclerView
        setupRecyclerView();

        // Set empty message based on tab type
        setEmptyMessage();
    }

    /**
     * Setup RecyclerView with adapter
     */
    private void setupRecyclerView() {
        eventAdapter = new EventAdapter(0);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(eventAdapter);

        // If events were set before view was created, apply them now
        if (events != null && !events.isEmpty()) {
            eventAdapter.setEvents(events);
            showEmptyState(false);
        } else {
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
     * Update events list
     */
    public void setEvents(List<Event> events) {
        this.events = events;

        // If adapter is not initialized yet, events will be set when view is created
        if (eventAdapter != null) {
            eventAdapter.setEvents(events);

            if (events.isEmpty()) {
                showEmptyState(true);
            } else {
                showEmptyState(false);
            }
        }
        // Otherwise, events are stored in this.events and will be used when adapter is created
    }

    /**
     * Show/hide loading indicator
     */
    public void showLoading(boolean show) {
        if (progressBar != null && recyclerView != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(show ? View.GONE : View.VISIBLE);
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