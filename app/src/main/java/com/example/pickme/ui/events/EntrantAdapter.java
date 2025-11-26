package com.example.pickme.ui.events;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupMenu;
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
 * EntrantAdapter - RecyclerView adapter for displaying entrant profile lists
 *
 * Displays a list of entrant profiles in a RecyclerView with configurable display options.
 * Used across multiple event management screens (waiting list, selected, confirmed, cancelled).
 *
 * Features:
 * - Displays profile information (name, email, profile picture)
 * - Optional join timestamp display (for waiting list)
 * - Optional status indicators (for selected entrants)
 * - Click handling for entrant selection
 * - Long-press menu for entrant actions (cancel, etc.)
 * - Optional overflow menu button support
 * - Glide image loading with error handling
 *
 * Configuration Options:
 * - showJoinTime: Display when entrant joined waiting list
 * - showStatus: Display entrant response status (pending/accepted/declined)
 * - showCancelOption: Display cancel option in overflow menu (for selected entrants)
 *
 * Usage:
 * ```java
 * EntrantAdapter adapter = new EntrantAdapter(true, false, false);
 * recyclerView.setAdapter(adapter);
 * adapter.setProfiles(profileList);
 * adapter.setOnEntrantActionListener(actionListener); // For cancel actions
 * ```
 *
 * Related User Stories: US 02.02.01, US 02.06.01-04
 */
public class EntrantAdapter extends RecyclerView.Adapter<EntrantAdapter.EntrantViewHolder> {

    private static final String TAG = "EntrantAdapter";

    private List<Profile> profiles;
    private Map<String, Long> joinTimestamps; // Optional: userId -> timestamp
    private OnEntrantClickListener clickListener;
    private OnEntrantActionListener actionListener;
    private SimpleDateFormat dateFormat;
    private boolean showJoinTime;
    private boolean showStatus;
    private boolean showCancelOption;

    /**
     * Constructor with default cancel option disabled
     *
     * @param showJoinTime Whether to display join timestamps (for waiting list)
     * @param showStatus Whether to display status indicators (for selected entrants)
     */
    public EntrantAdapter(boolean showJoinTime, boolean showStatus) {
        this(showJoinTime, showStatus, false);
    }

    /**
     * Constructor with all options
     *
     * @param showJoinTime Whether to display join timestamps (for waiting list)
     * @param showStatus Whether to display status indicators (for selected entrants)
     * @param showCancelOption Whether to show cancel option in overflow menu
     */
    public EntrantAdapter(boolean showJoinTime, boolean showStatus, boolean showCancelOption) {
        this.profiles = new ArrayList<>();
        this.showJoinTime = showJoinTime;
        this.showStatus = showStatus;
        this.showCancelOption = showCancelOption;
        this.dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    }

    /**
     * Enable or disable the cancel option
     * @param showCancelOption true to show cancel option in menu
     */
    public void setShowCancelOption(boolean showCancelOption) {
        this.showCancelOption = showCancelOption;
        notifyDataSetChanged();
    }

    /**
     * Set the list of profiles to display
     *
     * @param profiles List of Profile objects to display
     */
    public void setProfiles(List<Profile> profiles) {
        this.profiles = profiles != null ? profiles : new ArrayList<>();
        Log.d(TAG, "setProfiles called with " + this.profiles.size() + " profiles");
        notifyDataSetChanged();
    }

    /**
     * Set join timestamps for entrants (optional, used with waiting list)
     *
     * @param timestamps Map of userId to timestamp (milliseconds)
     */
    public void setJoinTimestamps(Map<String, Long> timestamps) {
        this.joinTimestamps = timestamps;
        notifyDataSetChanged();
    }

    public void setOnEntrantClickListener(OnEntrantClickListener listener) {
        this.clickListener = listener;
    }

    /**
     * Set listener for entrant actions (cancel, etc.)
     * @param listener Action listener
     */
    public void setOnEntrantActionListener(OnEntrantActionListener listener) {
        this.actionListener = listener;
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

            // Click listener for viewing details
            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && clickListener != null) {
                    clickListener.onEntrantClick(profiles.get(position));
                }
            });

            // Long press listener for showing context menu
            itemView.setOnLongClickListener(v -> {
                if (showCancelOption && actionListener != null) {
                    int position = getAdapterPosition();
                    if (position != RecyclerView.NO_POSITION) {
                        showPopupMenu(v, profiles.get(position));
                        return true;
                    }
                }
                return false;
            });
        }

        /**
         * Show popup menu with entrant actions
         */
        private void showPopupMenu(View anchor, Profile profile) {
            PopupMenu popup = new PopupMenu(anchor.getContext(), anchor);

            // Try to inflate the menu resource
            try {
                popup.getMenuInflater().inflate(R.menu.menu_entrant_actions, popup.getMenu());

                // Show/hide cancel option based on configuration
                if (popup.getMenu().findItem(R.id.action_cancel_entrant) != null) {
                    popup.getMenu().findItem(R.id.action_cancel_entrant).setVisible(showCancelOption);
                }
            } catch (Exception e) {
                // Menu resource not found - create menu programmatically
                Log.w(TAG, "Menu resource not found, creating programmatically", e);
                if (showCancelOption) {
                    popup.getMenu().add(0, 1, 0, "Cancel Entrant");
                }
            }

            popup.setOnMenuItemClickListener(item -> {
                // Handle both resource-based and programmatic menu items
                if (item.getItemId() == R.id.action_cancel_entrant || item.getItemId() == 1) {
                    if (actionListener != null) {
                        actionListener.onCancelEntrant(profile);
                    }
                    return true;
                }
                return false;
            });

            popup.show();
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

    /**
     * Interface for entrant click events
     */
    public interface OnEntrantClickListener {
        void onEntrantClick(Profile profile);
    }

    /**
     * Interface for entrant action events (cancel, etc.)
     */
    public interface OnEntrantActionListener {
        /**
         * Called when organizer requests to cancel an entrant
         * @param profile The profile of the entrant to cancel
         */
        void onCancelEntrant(Profile profile);
    }
}