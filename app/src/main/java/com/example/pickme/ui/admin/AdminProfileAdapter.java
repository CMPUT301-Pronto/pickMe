package com.example.pickme.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Profile;

import java.util.ArrayList;
import java.util.List;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * AdminProfileAdapter - Adapter for displaying profiles in admin view
 */
public class AdminProfileAdapter extends RecyclerView.Adapter<AdminProfileAdapter.ProfileViewHolder> {

    private List<Profile> profiles = new ArrayList<>();
    private OnProfileClickListener listener;

    public void setProfiles(List<Profile> profiles) {
        this.profiles = profiles != null ? profiles : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setOnProfileClickListener(OnProfileClickListener listener) {
        this.listener = listener;
    }

    @NonNull
    @Override
    public ProfileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.entrant_card_item, parent, false);
        return new ProfileViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProfileViewHolder holder, int position) {
        holder.bind(profiles.get(position));
    }

    @Override
    public int getItemCount() {
        return profiles.size();
    }

    class ProfileViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivProfileImage;
        TextView tvEntrantName;
        TextView tvEntrantEmail;
        TextView tvStatus;

        ProfileViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProfileImage = itemView.findViewById(R.id.ivProfileImage);
            tvEntrantName = itemView.findViewById(R.id.tvEntrantName);
            tvEntrantEmail = itemView.findViewById(R.id.tvEntrantEmail);
            tvStatus = itemView.findViewById(R.id.tvStatus);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (position != RecyclerView.NO_POSITION && listener != null) {
                    listener.onProfileClick(profiles.get(position));
                }
            });
        }

        void bind(Profile profile) {
            tvEntrantName.setText(profile.getName());
            tvEntrantEmail.setText(profile.getEmail() != null ? profile.getEmail() : "No email");
            tvStatus.setText(profile.getRole().toUpperCase());
            tvStatus.setVisibility(View.VISIBLE);
        }
    }

    public interface OnProfileClickListener {
        void onProfileClick(Profile profile);
    }
}

