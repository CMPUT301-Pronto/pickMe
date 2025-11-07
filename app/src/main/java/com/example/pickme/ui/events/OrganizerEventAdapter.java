package com.example.pickme.ui.events;

import android.util.Log;
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
import com.example.pickme.models.EventStatus;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * OrganizerEventAdapter - RecyclerView adapter for organizer's event list
 *
 * Displays event cards with organizer-specific information:
 * - Event poster image
 * - Name, date, location
 * - Registration status badge (OPEN, CLOSED, DRAFT, etc.)
 * - Waiting list count
 * - Selected entrants count (in responsePendingList)
 * - Enrolled count (in inEventList)
 *
 * Related User Stories: US 02.02.01, US 02.06.01
 */
public class OrganizerEventAdapter extends RecyclerView.Adapter<OrganizerEventAdapter.OrganizerEventViewHolder> {

    private static final String TAG = "OrganizerEventAdapter";
    private List<Event> events;
    private Map<String, EventMetrics> eventMetrics; // Event ID -> metrics
    private OnEventClickListener listener;
    private SimpleDateFormat dateFormat;

    public OrganizerEventAdapter() {
        this.events = new ArrayList<>();
        this.eventMetrics = new HashMap<>();
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    /**
     * Update event list
     */
    public void setEvents(List<Event> events) {
        this.events = events != null ? events : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Update event metrics (waiting list, selected, enrolled counts)
     */
    public void setEventMetrics(Map<String, EventMetrics> metrics) {
        Log.d(TAG, "setEventMetrics called with " + (metrics != null ? metrics.size() : 0) + " entries");
        this.eventMetrics = metrics != null ? metrics : new HashMap<>();
        if (metrics != null) {
            for (Map.Entry<String, EventMetrics> entry : metrics.entrySet()) {
                EventMetrics m = entry.getValue();
                Log.d(TAG, "  Metrics for " + entry.getKey() + ": waiting=" + m.waitingListCount +
                      ", selected=" + m.selectedCount + ", enrolled=" + m.enrolledCount);
            }
        }
        notifyDataSetChanged();
        Log.d(TAG, "notifyDataSetChanged called");
    }

    /**
     * Set click listener
     */
    public void setOnEventClickListener(OnEventClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public OrganizerEventViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.organizer_event_card_item, parent, false);
        return new OrganizerEventViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull OrganizerEventViewHolder holder, int position) {
        Event event = events.get(position);
        EventMetrics metrics = eventMetrics.get(event.getEventId());
        holder.bind(event, metrics);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    /**
     * ViewHolder for organizer event cards
     */
    class OrganizerEventViewHolder extends RecyclerView.ViewHolder {

        private ImageView ivEventPoster;
        private TextView tvStatusBadge;
        private TextView tvEventName;
        private TextView tvEventDate;
        private TextView tvEventLocation;
        private TextView tvWaitingCount;
        private TextView tvSelectedCount;
        private TextView tvEnrolledCount;

        public OrganizerEventViewHolder(@NonNull View itemView) {
            super(itemView);

            ivEventPoster = itemView.findViewById(R.id.ivEventPoster);
            tvStatusBadge = itemView.findViewById(R.id.tvStatusBadge);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            tvEventDate = itemView.findViewById(R.id.tvEventDate);
            tvEventLocation = itemView.findViewById(R.id.tvEventLocation);
            tvWaitingCount = itemView.findViewById(R.id.tvWaitingCount);
            tvSelectedCount = itemView.findViewById(R.id.tvSelectedCount);
            tvEnrolledCount = itemView.findViewById(R.id.tvEnrolledCount);

            // Click listener
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEventClick(events.get(position));
                }
            });
        }

        public void bind(Event event, EventMetrics metrics) {
            Log.d(TAG, "Binding event: " + event.getEventId() + " (" + event.getName() + ")");

            // Event name
            tvEventName.setText(event.getName());

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

            // Status badge
            EventStatus status = event.getStatusEnum();
            tvStatusBadge.setText(getStatusText(status));
            tvStatusBadge.setBackgroundResource(getStatusBackground(status));

            // Metrics
            if (metrics != null) {
                Log.d(TAG, "  Setting metrics: waiting=" + metrics.waitingListCount +
                      ", selected=" + metrics.selectedCount + ", enrolled=" + metrics.enrolledCount);
                tvWaitingCount.setText(String.valueOf(metrics.waitingListCount));
                tvSelectedCount.setText(String.valueOf(metrics.selectedCount));
                tvEnrolledCount.setText(String.valueOf(metrics.enrolledCount));
            } else {
                Log.d(TAG, "  No metrics available, setting to 0");
                tvWaitingCount.setText("0");
                tvSelectedCount.setText("0");
                tvEnrolledCount.setText("0");
            }
        }

        /**
         * Get status text for badge
         */
        private String getStatusText(EventStatus status) {
            switch (status) {
                case OPEN:
                    return "OPEN";
                case CLOSED:
                    return "CLOSED";
                case DRAFT:
                    return "DRAFT";
                case CANCELLED:
                    return "CANCELLED";
                case COMPLETED:
                    return "COMPLETED";
                default:
                    return "UNKNOWN";
            }
        }

        /**
         * Get background drawable for status badge
         */
        private int getStatusBackground(EventStatus status) {
            // Use badge_background as default
            // In a production app, you might have different colored badges
            return R.drawable.badge_background;
        }
    }

    /**
     * Data class to hold event metrics
     */
    public static class EventMetrics {
        public int waitingListCount;
        public int selectedCount;
        public int enrolledCount;

        public EventMetrics(int waitingListCount, int selectedCount, int enrolledCount) {
            this.waitingListCount = waitingListCount;
            this.selectedCount = selectedCount;
            this.enrolledCount = enrolledCount;
        }
    }

    /**
     * Interface for event click events
     */
    public interface OnEventClickListener {
        void onEventClick(Event event);
    }
}

