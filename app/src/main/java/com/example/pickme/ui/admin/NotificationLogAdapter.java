package com.example.pickme.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.NotificationLog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * NotificationLogAdapter - Adapter for displaying notification logs in admin view
 *
 * Displays notification logs with:
 * - Timestamp
 * - Notification type
 * - Event name
 * - Recipient count
 * - Message preview
 *
 * Related User Stories: US 03.08.01 - Admin review notification logs
 */
public class NotificationLogAdapter extends RecyclerView.Adapter<NotificationLogAdapter.ViewHolder> {

    private List<NotificationLog> logs;
    private SimpleDateFormat dateFormat;
    private OnLogClickListener clickListener;

    public interface OnLogClickListener {
        void onLogClick(NotificationLog log);
    }

    public NotificationLogAdapter(OnLogClickListener clickListener) {
        this.logs = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_notification_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NotificationLog log = logs.get(position);
        holder.bind(log);
    }

    @Override
    public int getItemCount() {
        return logs.size();
    }

    public void setLogs(List<NotificationLog> logs) {
        this.logs = logs != null ? logs : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void addLogs(List<NotificationLog> newLogs) {
        int startPosition = this.logs.size();
        this.logs.addAll(newLogs);
        notifyItemRangeInserted(startPosition, newLogs.size());
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTimestamp;
        private TextView tvType;
        private TextView tvRecipientCount;
        private TextView tvMessagePreview;
        private TextView tvEventName;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.tvTimestamp);
            tvType = itemView.findViewById(R.id.tvNotificationType);
            tvRecipientCount = itemView.findViewById(R.id.tvRecipientCount);
            tvMessagePreview = itemView.findViewById(R.id.tvMessagePreview);
            tvEventName = itemView.findViewById(R.id.tvEventName);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onLogClick(logs.get(position));
                }
            });
        }

        public void bind(NotificationLog log) {
            // Format timestamp
            String timestamp = dateFormat.format(new Date(log.getTimestamp()));
            tvTimestamp.setText(timestamp);

            // Format notification type
            String type = formatNotificationType(log.getNotificationType());
            tvType.setText(type);

            // Recipient count
            int count = log.getRecipientIds() != null ? log.getRecipientIds().size() : 0;
            tvRecipientCount.setText(count + " recipient" + (count != 1 ? "s" : ""));

            // Message preview (truncate if too long)
            String message = log.getMessageContent();
            if (message != null && message.length() > 100) {
                message = message.substring(0, 97) + "...";
            }
            tvMessagePreview.setText(message != null ? message : "No message");

            // Event name (will be loaded separately)
            tvEventName.setText("Event ID: " + log.getEventId());
        }

        private String formatNotificationType(String type) {
            if (type == null) return "Unknown";

            switch (type) {
                case "lottery_win":
                    return "🎉 Lottery Win";
                case "lottery_loss":
                    return "📋 Lottery Loss";
                case "replacement_draw":
                    return "🎊 Replacement Draw";
                case "organizer_message":
                    return "💬 Organizer Message";
                case "entrant_cancelled":
                    return "❌ Cancellation";
                default:
                    return type.replace("_", " ").toUpperCase();
            }
        }
    }
}

