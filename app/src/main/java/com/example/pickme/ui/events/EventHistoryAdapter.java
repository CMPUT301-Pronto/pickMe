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

public class EventHistoryAdapter extends RecyclerView.Adapter<EventHistoryAdapter.ViewHolder> {

    private List<Event> events;
    private String statusToShow; // "CANCELLED", "ACCEPTED", etc.
    private SimpleDateFormat dateFormat;

    public EventHistoryAdapter(List<Event> events, String statusToShow) {
        this.events = events;
        this.statusToShow = statusToShow;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_history, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Event event = events.get(position);
        holder.bind(event);
    }

    @Override
    public int getItemCount() {
        return events.size();
    }

    public void updateEvents(List<Event> newEvents) {
        this.events = newEvents;
        notifyDataSetChanged();
    }

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

        public void bind(Event event) {
            tvEventName.setText(event.getName());

            // Show event date
            if (event.getEventDates() != null && !event.getEventDates().isEmpty()) {
                String date = dateFormat.format(new Date(event.getEventDates().get(0)));
                tvEventDate.setText(date);
            }

            // Show location
            if (tvLocation != null && event.getLocation() != null) {
                tvLocation.setText(event.getLocation());
            }

            // Show status
            if (tvStatus != null) {
                tvStatus.setText(statusToShow);
                tvStatus.setTextColor(getStatusColor());
            }
        }

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