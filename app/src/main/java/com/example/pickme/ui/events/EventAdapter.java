package com.example.pickme.ui.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.ui.history.EventListFragment;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * EventAdapter - RecyclerView adapter for displaying event cards
 *
 * Displays event information in a card layout with poster image, details, and status indicators.
 * Used in browse events, event history, and organizer dashboard.
 *
 * Features:
 * - Event poster image loading with Glide
 * - Event details (name, date, location, price)
 * - Capacity and waiting list information
 * - "Joined" badge for events user has enrolled in
 * - Registration status indicators
 * - Click handling for navigation to event details
 *
 * Tab Types:
 * - 0: Browse tab (shows all available events)
 * - 1: Waiting tab (shows events user is waiting for)
 * - 2: Accepted tab (shows events user was selected for)
 * - 3: Declined tab (shows events user declined)
 *
 * Usage:
 * ```java
 * EventAdapter adapter = new EventAdapter(0); // Browse tab
 * recyclerView.setAdapter(adapter);
 * adapter.setEvents(eventList);
 * adapter.setJoinedEventIds(joinedIds); // Optional
 * adapter.setOnEventClickListener(event -> {
 *     // Handle click
 * });
 * ```
 *
 * Related User Stories: US 01.01.03, US 01.01.04, US 01.04.01
 */
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {

    private List<Event> events;
    private List<String> joinedEventIds; // Events user has joined
    private OnEventClickListener listener;
    private SimpleDateFormat dateFormat;
    private int tabType;

    /**
     * Constructor
     *
     * @param tabType Tab type (0=Browse, 1=Waiting, 2=Accepted, 3=Declined)
     */
    public EventAdapter(int tabType) {
        this.tabType = tabType;
        this.events = new ArrayList<>();
        this.joinedEventIds = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    /**
     * Update the list of events to display
     *
     * @param events List of Event objects
     */
    public void setEvents(List<Event> events) {
        this.events = events != null ? events : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Update joined event IDs
     */
    public void setJoinedEventIds(List<String> joinedEventIds) {
        this.joinedEventIds = joinedEventIds != null ? joinedEventIds : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Set click listener
     */
    public void setOnEventClickListener(OnEventClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public EventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.event_card_item, parent, false);
        return new EventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EventViewHolder holder, int position) {
        Event event = events.get(position);
        holder.bind(event, tabType);

    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    /**
     * ViewHolder for event cards
     */
    class EventViewHolder extends RecyclerView.ViewHolder {

        private ImageView ivEventPoster;
        private TextView tvJoinedBadge;
        private TextView tvEventName;
        private TextView tvEventDate;
        private TextView tvEventLocation;
        private TextView tvEventStatus;
        private TextView tvEventPrice;

        public EventViewHolder(@NonNull View itemView) {
            super(itemView);

            ivEventPoster = itemView.findViewById(R.id.ivEventPoster);
            tvJoinedBadge = itemView.findViewById(R.id.tvJoinedBadge);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            tvEventDate = itemView.findViewById(R.id.tvEventDate);
            tvEventLocation = itemView.findViewById(R.id.tvEventLocation);
            tvEventStatus = itemView.findViewById(R.id.tvEventStatus);
            tvEventPrice = itemView.findViewById(R.id.tvEventPrice);

            // Click listener
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEventClick(events.get(position));
                }
            });
        }

        public void bind(Event event, int tabType) {
            // Event name
            tvEventName.setText(event.getName());
            if (tabType == EventListFragment.TAB_CANCELLED) {
                // Show a status TextView or badge if you have one
                if (tvEventName != null) {
                    tvEventName.setVisibility(View.VISIBLE);
                    tvEventName.setText("Declined");
                    tvEventName.setTextColor(itemView.getContext().getColor(R.color.status_declined));
                }
            }

            // Event date
            if (event.getEventDates() != null && !event.getEventDates().isEmpty()) {
                long dateMillis = event.getEventDates().get(0);
                String formattedDate = dateFormat.format(new Date(dateMillis));
                tvEventDate.setText(formattedDate);
            } else {
                tvEventDate.setText("");
            }

            // Event location
            tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "");

            // Event poster image
            if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(event.getPosterImageUrl())
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .into(ivEventPoster);
            } else {
                ivEventPoster.setImageResource(R.drawable.ic_launcher_foreground);
            }

            // Joined badge
            boolean isJoined = joinedEventIds.contains(event.getEventId());
            tvJoinedBadge.setVisibility(isJoined ? View.VISIBLE : View.GONE);

            // Status (spots available or waiting list count)
            // TODO: Get actual waiting list count from Firestore
            int capacity = event.getCapacity();
            int waitingListCount = 0; // Placeholder

            if (capacity > 0) {
                int spotsAvailable = capacity - waitingListCount;
                if (spotsAvailable > 0) {
                    tvEventStatus.setText(itemView.getContext().getString(
                            R.string.spots_available, spotsAvailable));
                } else {
                    tvEventStatus.setText(itemView.getContext().getString(
                            R.string.waiting_list_count, waitingListCount));
                }
            } else {
                tvEventStatus.setText(itemView.getContext().getString(
                        R.string.waiting_list_count, waitingListCount));
            }

            // Price
            if (event.getPrice() > 0) {
                tvEventPrice.setText(itemView.getContext().getString(
                        R.string.event_price, event.getPrice()));
            } else {
                tvEventPrice.setText(R.string.free_event);
            }
        }

    }

    /**
     * Interface for event click events
     */
    public interface OnEventClickListener {
        void onEventClick(Event event);
    }
}

