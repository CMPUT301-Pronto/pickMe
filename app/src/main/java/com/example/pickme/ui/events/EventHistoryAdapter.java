// EventHistoryAdapter.java
package com.example.pickme.ui.events;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
/**
 * JAVADOCS LLM GENERATED
 *
 * RecyclerView adapter that renders a simple, read-only list of past event items.
 *
 * <p><b>Role in architecture:</b> Presentation layer component for history tabs/filters
 * (e.g., CANCELLED / ACCEPTED). Given a list of {@link Event} and a fixed status label,
 * it binds minimal fields (name, first date, location, status) to {@code item_event_history} rows.</p>
 *
 * <p><b>Behavior:</b>
 * <ul>
 *   <li>Uses the first timestamp in {@code eventDates} as the display date.</li>
 *   <li>Applies a status tint based on the {@code statusToShow} passed in the constructor.</li>
 *   <li>Offers {@link #updateEvents(List)} to refresh the backing dataset.</li>
 * </ul>
 *
 * <p><b>Outstanding notes:</b>
 * <ul>
 *   <li>No diffing: consider ListAdapter+DiffUtil for large lists.</li>
 *   <li>Assumes non-null colors: ensure {@code R.color.status_*} exist.</li>
 *   <li>Only shows the first date; extend if multi-day display is needed.</li>
 * </ul>
 */
public class EventHistoryAdapter extends RecyclerView.Adapter<EventHistoryAdapter.ViewHolder> {

    private List<Event> events;
    private String statusToShow; // "CANCELLED", "ACCEPTED", etc.
    private SimpleDateFormat dateFormat;

    /**
     * Create an adapter for event history.
     *
     * @param events initial list of events to display (may be empty)
     * @param statusToShow fixed status label to render for each row
     */
    public EventHistoryAdapter(List<Event> events, String statusToShow) {
        this.events = events;
        this.statusToShow = statusToShow;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }
    /** {@inheritDoc} */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_history, parent, false);
        return new ViewHolder(view);
    }
    /** {@inheritDoc} */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);
        holder.bind(event);
    }
    /** {@inheritDoc} */
    @Override
    public int getItemCount() {
        return events.size();
    }

    /**
     * Replace the current list and refresh the UI.
     *
     * @param newEvents new dataset to display (null treated as empty)
     */
    public void updateEvents(List<Event> newEvents) {
        this.events = newEvents;
        notifyDataSetChanged();
    }

    // simple row holder for item_event_history
    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvEventName;
        private TextView tvEventDate;
        private TextView tvStatus;
        private TextView tvLocation;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            tvEventDate = itemView.findViewById(R.id.tvEventDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvLocation = itemView.findViewById(R.id.tvEventLocation);
        }


        /** Bind minimal event data into the row views. */
        public void bind(Event event) {
            // name
            tvEventName.setText(event.getName());

            // Show event date if present
            if (event.getEventDates() != null && !event.getEventDates().isEmpty()) {
                String date = dateFormat.format(new Date(event.getEventDates().get(0)));
                tvEventDate.setText(date);
            }

            // Show location
            if (tvLocation != null && event.getLocation() != null) {
                tvLocation.setText(event.getLocation());
            }

            // Show status + color
            if (tvStatus != null) {
                tvStatus.setText(statusToShow);
                tvStatus.setTextColor(getStatusColor());
            }
        }
        /** Resolve the status color for the fixed {@code statusToShow}. */
        private int getStatusColor() {
            if ("CANCELLED".equals(statusToShow)) {
                return itemView.getContext().getColor(R.color.status_cancelled);
            } else if ("ACCEPTED".equals(statusToShow)) {
                return itemView.getContext().getColor(R.color.status_accepted);
            } else {
                return itemView.getContext().getColor(R.color.text_secondary);
            }
        }
    }
}