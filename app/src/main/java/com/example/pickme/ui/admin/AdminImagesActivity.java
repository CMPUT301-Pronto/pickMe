package com.example.pickme.ui.admin;

import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.models.EventPoster;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ImageRepository;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AdminImagesActivity - Browse and manage event poster images
 *
 * Features:
 * - Display gallery of all event posters in grid
 * - Show event name for each poster
 * - Remove button on each image
 * - Confirmation dialog before deletion
 * - Delete from Firebase Storage
 * - Update event poster reference to null
 * - Remove from gallery view
 *
 * Related User Stories: US 03.06.01, US 03.03.01
 */
public class AdminImagesActivity extends AppCompatActivity implements EventPosterAdapter.OnPosterActionListener {

    private static final String TAG = "AdminImagesActivity";
    private static final int GRID_COLUMNS = 2;

    // UI Components
    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;
    private TextView tvEmptyMessage;

    // Data
    private EventPosterAdapter adapter;
    private ImageRepository imageRepository;
    private EventRepository eventRepository;
    private List<EventPoster> allPosters;
    private Map<String, String> eventNameCache;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_images);

        initializeViews();
        initializeData();
        setupToolbar();
        loadAllPosters();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);

        // Setup RecyclerView with Grid Layout
        GridLayoutManager gridLayoutManager = new GridLayoutManager(this, GRID_COLUMNS);
        recyclerView.setLayoutManager(gridLayoutManager);

        // Performance optimizations
        recyclerView.setHasFixedSize(true);  // All items same size - optimization
        recyclerView.setItemViewCacheSize(20);  // Cache more views for smooth scrolling
        recyclerView.setDrawingCacheEnabled(true);
        recyclerView.setDrawingCacheQuality(View.DRAWING_CACHE_QUALITY_HIGH);

        // Pause Glide during fast scrolls to improve performance
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    // Resume image loading when scroll stops
                    if (!isFinishing()) {
                        com.bumptech.glide.Glide.with(AdminImagesActivity.this).resumeRequests();
                    }
                } else {
                    // Pause image loading during scroll for smoother performance
                    com.bumptech.glide.Glide.with(AdminImagesActivity.this).pauseRequests();
                }
            }
        });

        adapter = new EventPosterAdapter(this, eventNameCache);
        recyclerView.setAdapter(adapter);
    }

    private void initializeData() {
        imageRepository = new ImageRepository();
        eventRepository = new EventRepository();
        allPosters = new ArrayList<>();
        eventNameCache = new HashMap<>();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Browse Images");
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_admin_images, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_refresh) {
            loadAllPosters();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Load all event posters from Firestore
     */
    private void loadAllPosters() {
        showLoading(true);

        imageRepository.getAllEventPosters(new ImageRepository.OnPostersLoadedListener() {
            @Override
            public void onPostersLoaded(List<EventPoster> posters) {
                allPosters = posters;

                if (posters.isEmpty()) {
                    showEmptyState("No event posters found");
                    showLoading(false);
                    return;
                }

                // Load event names for all posters
                loadEventNames(posters);
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to load posters", e);
                showLoading(false);
                showEmptyState("Failed to load images");
                Toast.makeText(AdminImagesActivity.this,
                    "Failed to load images: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Load event names for all posters
     */
    private void loadEventNames(List<EventPoster> posters) {
        int[] loadedCount = {0};
        int totalPosters = posters.size();

        for (EventPoster poster : posters) {
            String eventId = poster.getEventId();

            if (eventNameCache.containsKey(eventId)) {
                loadedCount[0]++;
                checkAllNamesLoaded(loadedCount[0], totalPosters);
                continue;
            }

            eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
                @Override
                public void onEventLoaded(Event event) {
                    eventNameCache.put(eventId, event.getName());
                    loadedCount[0]++;
                    checkAllNamesLoaded(loadedCount[0], totalPosters);
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Failed to load event name for: " + eventId, e);
                    eventNameCache.put(eventId, "Unknown Event");
                    loadedCount[0]++;
                    checkAllNamesLoaded(loadedCount[0], totalPosters);
                }
            });
        }
    }

    /**
     * Check if all event names have been loaded
     */
    private void checkAllNamesLoaded(int loaded, int total) {
        if (loaded >= total) {
            // All names loaded, update adapter
            adapter.setPosters(allPosters);
            showLoading(false);
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);

            Log.d(TAG, "Loaded " + allPosters.size() + " posters with event names");
        }
    }

    @Override
    public void onRemovePoster(EventPoster poster, int position) {
        String eventName = eventNameCache.getOrDefault(poster.getEventId(), "this event");

        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_image_title)
                .setMessage("Are you sure you want to delete the poster for \"" + eventName +
                           "\"?\n\nThis will delete the image from Firebase Storage and update the event.")
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    deletePoster(poster, position);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Delete poster from Firebase Storage and Firestore
     */
    private void deletePoster(EventPoster poster, int position) {
        showLoading(true);

        imageRepository.deleteEventPoster(poster.getEventId(),
            new ImageRepository.OnDeleteCompleteListener() {
                @Override
                public void onDeleteComplete() {
                    Log.d(TAG, "Poster deleted successfully: " + poster.getPosterId());

                    // Remove from local list and update adapter
                    allPosters.remove(position);
                    adapter.removePoster(position);

                    showLoading(false);
                    Toast.makeText(AdminImagesActivity.this,
                        R.string.deleted_successfully,
                        Toast.LENGTH_SHORT).show();

                    // Show empty state if no more posters
                    if (allPosters.isEmpty()) {
                        showEmptyState("No event posters found");
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.e(TAG, "Failed to delete poster", e);
                    showLoading(false);
                    Toast.makeText(AdminImagesActivity.this,
                        "Failed to delete image: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                }
            });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyState(String message) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        tvEmptyMessage.setText(message);
    }
}

