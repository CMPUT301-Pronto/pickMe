package com.example.pickme.ui.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.pickme.R;
import com.example.pickme.models.EventPoster;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EventPosterAdapter - Adapter for displaying event posters in grid
 *
 * Features:
 * - Grid layout with poster images
 * - Event name display
 * - Remove button for each poster
 * - Image loading with Glide
 *
 * Related User Stories: US 03.06.01
 */
public class EventPosterAdapter extends RecyclerView.Adapter<EventPosterAdapter.PosterViewHolder> {

    private List<EventPoster> posters;
    private Map<String, String> eventNameCache;
    private OnPosterActionListener listener;

    public interface OnPosterActionListener {
        void onRemovePoster(EventPoster poster, int position);
    }

    public EventPosterAdapter(OnPosterActionListener listener, Map<String, String> eventNameCache) {
        this.posters = new ArrayList<>();
        this.listener = listener;
        this.eventNameCache = eventNameCache;
    }

    @NonNull
    @Override
    public PosterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_event_poster, parent, false);
        return new PosterViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PosterViewHolder holder, int position) {
        EventPoster poster = posters.get(position);
        holder.bind(poster, position);
    }

    @Override
    public int getItemCount() {
        return posters.size();
    }

    public void setPosters(List<EventPoster> posters) {
        this.posters = posters != null ? posters : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void removePoster(int position) {
        if (position >= 0 && position < posters.size()) {
            posters.remove(position);
            notifyItemRemoved(position);
            notifyItemRangeChanged(position, posters.size());
        }
    }

    class PosterViewHolder extends RecyclerView.ViewHolder {
        private ImageView ivPoster;
        private TextView tvEventName;
        private ImageButton btnRemove;

        public PosterViewHolder(@NonNull View itemView) {
            super(itemView);
            ivPoster = itemView.findViewById(R.id.ivPoster);
            tvEventName = itemView.findViewById(R.id.tvEventName);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }

        public void bind(EventPoster poster, int position) {
            // Load poster image with Glide
            Glide.with(itemView.getContext())
                    .load(poster.getImageUrl())
                    .placeholder(R.drawable.ic_event_placeholder)
                    .error(R.drawable.ic_event_placeholder)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .centerCrop()
                    .into(ivPoster);

            // Set event name
            String eventName = "Loading...";
            if (eventNameCache != null && eventNameCache.containsKey(poster.getEventId())) {
                eventName = eventNameCache.get(poster.getEventId());
            }
            tvEventName.setText(eventName);

            // Remove button click
            btnRemove.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRemovePoster(poster, position);
                }
            });
        }
    }
}

