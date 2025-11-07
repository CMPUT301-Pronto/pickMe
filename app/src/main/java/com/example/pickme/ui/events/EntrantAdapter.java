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
import com.example.pickme.models.Profile;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * EntrantAdapter - RecyclerView adapter for displaying entrant profiles
 * Used in waiting list, selected, confirmed, and cancelled tabs
 */
public class EntrantAdapter extends RecyclerView.Adapter<EntrantAdapter.EntrantViewHolder> {

    private static final String TAG = "EntrantAdapter";
    private List<Profile> profiles;
    private Map<String, Long> joinTimestamps; // Optional: userId -> timestamp
    private OnEntrantClickListener listener;
    private SimpleDateFormat dateFormat;
    private boolean showJoinTime;
    private boolean showStatus;

    public EntrantAdapter(boolean showJoinTime, boolean showStatus) {
        this.profiles = new ArrayList<>();
        this.showJoinTime = showJoinTime;
        this.showStatus = showStatus;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    public void setProfiles(List<Profile> profiles) {
        this.profiles = profiles != null ? profiles : new ArrayList<>();
        Log.d(TAG, "setProfiles called with " + this.profiles.size() + " profiles");
        notifyDataSetChanged();
    }

    public void setJoinTimestamps(Map<String, Long> timestamps) {
        this.joinTimestamps = timestamps;
        notifyDataSetChanged();
    }

    public void setOnEntrantClickListener(OnEntrantClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public EntrantViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.entrant_card_item, parent, false);
        return new EntrantViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull EntrantViewHolder holder, int position) {
        Profile profile = profiles.get(position);
        holder.bind(profile);
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    class EntrantViewHolder extends RecyclerView.ViewHolder {

        private ImageView ivProfileImage;
        private TextView tvEntrantName;
        private TextView tvEntrantEmail;
        private TextView tvStatus;
        private TextView tvJoinTime;

        public EntrantViewHolder(@NonNull View itemView) {
            super(itemView);

            ivProfileImage = itemView.findViewById(R.id.ivProfileImage);
            tvEntrantName = itemView.findViewById(R.id.tvEntrantName);
            tvEntrantEmail = itemView.findViewById(R.id.tvEntrantEmail);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvJoinTime = itemView.findViewById(R.id.tvJoinTime);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onEntrantClick(profiles.get(position));
                }
            });
        }

        public void bind(Profile profile) {
            // Entrant name
            String displayName = profile.getName();
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = "Anonymous User";
            }
            tvEntrantName.setText(displayName);

            // Email
            tvEntrantEmail.setText(profile.getEmail() != null ? profile.getEmail() : "");

            // Profile image
            if (profile.getProfileImageUrl() != null && !profile.getProfileImageUrl().isEmpty()) {
                Glide.with(itemView.getContext())
                        .load(profile.getProfileImageUrl())
                        .placeholder(R.drawable.ic_launcher_foreground)
                        .into(ivProfileImage);
            } else {
                ivProfileImage.setImageResource(R.drawable.ic_launcher_foreground);
            }

            // Join time
            if (showJoinTime && joinTimestamps != null && joinTimestamps.containsKey(profile.getUserId())) {
                Long timestamp = joinTimestamps.get(profile.getUserId());
                if (timestamp != null) {
                    String formattedDate = dateFormat.format(new Date(timestamp));
                    tvJoinTime.setText(itemView.getContext().getString(R.string.joined_on, formattedDate));
                    tvJoinTime.setVisibility(View.VISIBLE);
                } else {
                    tvJoinTime.setVisibility(View.GONE);
                }
            } else {
                tvJoinTime.setVisibility(View.GONE);
            }

            // Status badge
            if (showStatus) {
                tvStatus.setVisibility(View.VISIBLE);
                // Status text can be customized based on tab
            } else {
                tvStatus.setVisibility(View.GONE);
            }
        }
    }

    public interface OnEntrantClickListener {
        void onEntrantClick(Profile profile);
    }
}

