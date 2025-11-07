package com.example.pickme.ui.invitations;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.pickme.R;
import com.example.pickme.models.Event;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * InvitationAdapter - RecyclerView adapter for event invitations
 *
 * Displays event cards with Accept/Decline buttons for events
 * where the user is in the responsePendingList (lottery winners).
 */
public class InvitationAdapter extends RecyclerView.Adapter<InvitationAdapter.InvitationViewHolder> {

    private List<Event> invitations;
    private List<Long> deadlines; // Response deadlines for each invitation
    private OnInvitationActionListener listener;
    private SimpleDateFormat dateFormat;

    public InvitationAdapter() {
        this.invitations = new ArrayList<>();
        this.deadlines = new ArrayList<>();
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    /**
     * Update invitation list
     */
    public void setInvitations(List<Event> invitations, List<Long> deadlines) {
        this.invitations = invitations != null ? invitations : new ArrayList<>();
        this.deadlines = deadlines != null ? deadlines : new ArrayList<>();
        notifyDataSetChanged();
    }

    /**
     * Set action listener
     */
    public void setOnInvitationActionListener(OnInvitationActionListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public InvitationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.invitation_card_item, parent, false);
        return new InvitationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull InvitationViewHolder holder, int position) {
        Event event = invitations.get(position);
        Long deadline = position < deadlines.size() ? deadlines.get(position) : null;
        holder.bind(event, deadline);
    }

    @Override
    public int getItemCount() {
        return invitations.size();
    }

    /**
     * ViewHolder for invitation cards
     */
    class InvitationViewHolder extends RecyclerView.ViewHolder {

        private ImageView ivEventPoster;
        private TextView tvEventName;
        private TextView tvEventDate;
        private TextView tvDeadline;
        private Button btnAccept;
        private Button btnDecline;

        public InvitationViewHolder(@NonNull View itemView) {
            super(itemView);

            ivEventPoster = itemView.findViewById(R.id.ivEventPoster);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            tvEventDate = itemView.findViewById(R.id.tvEventDate);
            tvDeadline = itemView.findViewById(R.id.tvDeadline);
            btnAccept = itemView.findViewById(R.id.btnAccept);
            btnDecline = itemView.findViewById(R.id.btnDecline);
        }

        public void bind(Event event, Long deadline) {
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

            // Deadline
            if (deadline != null) {
                String deadlineStr = dateFormat.format(new Date(deadline));
                tvDeadline.setText(itemView.getContext().getString(R.string.respond_by, deadlineStr));

                // Check if expired
                if (deadline < System.currentTimeMillis()) {
                    tvDeadline.setTextColor(itemView.getContext().getColor(R.color.error_red));
                    btnAccept.setEnabled(false);
                    btnDecline.setEnabled(false);
                }
            } else {
                tvDeadline.setText("");
            }

            // Event poster
            if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(event.getPosterImageUrl())
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .into(ivEventPoster);
            } else {
                ivEventPoster.setImageResource(R.drawable.ic_launcher_foreground);
            }

            // Button listeners
            btnAccept.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onAcceptInvitation(event, getAdapterPosition());
                }
            });

            btnDecline.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeclineInvitation(event, getAdapterPosition());
                }
            });
        }
    }

    /**
     * Remove invitation at position
     */
    public void removeInvitation(int position) {
        if (position >= 0 && position < invitations.size()) {
            invitations.remove(position);
            if (position < deadlines.size()) {
                deadlines.remove(position);
            }
            notifyItemRemoved(position);
        }
    }

    /**
     * Interface for invitation action events
     */
    public interface OnInvitationActionListener {
        void onAcceptInvitation(Event event, int position);
        void onDeclineInvitation(Event event, int position);
    }
}

