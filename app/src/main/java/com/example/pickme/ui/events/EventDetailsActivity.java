package com.example.pickme.ui.events;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.models.Geolocation;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.GeolocationService;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * EventDetailsActivity - View event details and join/leave waiting list
 *
 * Features:
 * - Display full event information
 * - Show waiting list status
 * - Join waiting list (with geolocation if required)
 * - Leave waiting list with confirmation
 * - Show lottery criteria
 *
 * Related User Stories: US 01.01.01, US 01.01.02, US 01.05.04, US 01.05.05
 */
public class EventDetailsActivity extends AppCompatActivity {

    private static final String TAG = "EventDetailsActivity";

    // UI Components
    private CollapsingToolbarLayout collapsingToolbar;
    private ImageView ivEventPoster;
    private TextView tvEventName;
    private TextView tvRegistrationStatus;
    private TextView tvEventDate;
    private TextView tvEventLocation;
    private TextView tvEventPrice;
    private TextView tvCapacity;
    private TextView tvWaitingListStatus;
    private TextView tvGeolocationWarning;
    private TextView tvEventDescription;
    private TextView tvLotteryCriteria;
    private Button btnJoinLeave;
    private ProgressBar progressBar;

    // Data
    private EventRepository eventRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private GeolocationService geolocationService;
    private String eventId;
    private Event currentEvent;
    private String currentUserId;
    private boolean isUserOnWaitingList = false;
    private SimpleDateFormat dateFormat;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        // Get event ID from intent
        eventId = getIntent().getStringExtra(EventBrowserActivity.EXTRA_EVENT_ID);
        if (eventId == null) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Initialize
        initializeViews();
        initializeData();
        setupToolbar();
        setupButton();

        // Load event
        loadEvent();
    }

    /**
     * Initialize view references
     */
    private void initializeViews() {
        collapsingToolbar = findViewById(R.id.collapsingToolbar);
        ivEventPoster = findViewById(R.id.ivEventPoster);
        tvEventName = findViewById(R.id.tvEventName);
        tvRegistrationStatus = findViewById(R.id.tvRegistrationStatus);
        tvEventDate = findViewById(R.id.tvEventDate);
        tvEventLocation = findViewById(R.id.tvEventLocation);
        tvEventPrice = findViewById(R.id.tvEventPrice);
        tvCapacity = findViewById(R.id.tvCapacity);
        tvWaitingListStatus = findViewById(R.id.tvWaitingListStatus);
        tvGeolocationWarning = findViewById(R.id.tvGeolocationWarning);
        tvEventDescription = findViewById(R.id.tvEventDescription);
        tvLotteryCriteria = findViewById(R.id.tvLotteryCriteria);
        btnJoinLeave = findViewById(R.id.btnJoinLeave);
        progressBar = findViewById(R.id.progressBar);
    }

    /**
     * Initialize data components
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        geolocationService = new GeolocationService(this);
        currentUserId = deviceAuthenticator.getStoredUserId();
        dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
    }

    /**
     * Setup toolbar
     */
    private void setupToolbar() {
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("");
        }
    }

    /**
     * Setup join/leave button
     */
    private void setupButton() {
        btnJoinLeave.setOnClickListener(v -> {
            if (isUserOnWaitingList) {
                showLeaveConfirmation();
            } else {
                joinWaitingList();
            }
        });
    }

    /**
     * Load event details
     */
    private void loadEvent() {
        showLoading(true);

        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                currentEvent = event;
                displayEvent(event);
                checkWaitingListStatus();
                showLoading(false);
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(EventDetailsActivity.this,
                        getString(R.string.error_occurred) + ": " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * Display event information
     */
    private void displayEvent(Event event) {
        // Event name
        tvEventName.setText(event.getName());
        collapsingToolbar.setTitle(event.getName());

        // Event poster
        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(event.getPosterImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(ivEventPoster);
        }

        // Registration status
        if (event.isRegistrationOpen()) {
            tvRegistrationStatus.setText(R.string.registration_open);
            tvRegistrationStatus.setBackgroundResource(R.drawable.badge_background);
        } else {
            tvRegistrationStatus.setText(R.string.registration_closed);
            tvRegistrationStatus.setBackgroundColor(getColor(R.color.text_secondary));
        }

        // Event date
        if (event.getEventDates() != null && !event.getEventDates().isEmpty()) {
            long dateMillis = event.getEventDates().get(0);
            String formattedDate = dateFormat.format(new Date(dateMillis));
            tvEventDate.setText(formattedDate);
        }

        // Location
        tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "");

        // Price
        if (event.getPrice() > 0) {
            tvEventPrice.setText(getString(R.string.event_price, event.getPrice()));
        } else {
            tvEventPrice.setText(R.string.free_event);
        }

        // Capacity
        tvCapacity.setText(getString(R.string.event_capacity, event.getCapacity()));

        // Waiting list status (placeholder)
        // TODO: Get actual waiting list count from Firestore
        tvWaitingListStatus.setText(getString(R.string.waiting_list_status, 0));

        // Geolocation warning
        if (event.isGeolocationRequired()) {
            tvGeolocationWarning.setVisibility(View.VISIBLE);
        } else {
            tvGeolocationWarning.setVisibility(View.GONE);
        }

        // Description
        tvEventDescription.setText(event.getDescription() != null ? event.getDescription() : "");
    }

    /**
     * Check if user is on waiting list
     */
    private void checkWaitingListStatus() {
        eventRepository.isEntrantInWaitingList(eventId, currentUserId,
                new EventRepository.OnEntrantCheckListener() {
                    @Override
                    public void onCheckComplete(boolean exists) {
                        isUserOnWaitingList = exists;
                        updateButtonState();
                    }

                    @Override
                    public void onError(Exception e) {
                        // Assume not on list if check fails
                        isUserOnWaitingList = false;
                        updateButtonState();
                    }
                });
    }

    /**
     * Update button based on waiting list status
     */
    private void updateButtonState() {
        if (isUserOnWaitingList) {
            btnJoinLeave.setText(R.string.leave_waiting_list);
            btnJoinLeave.setBackgroundColor(getColor(R.color.error_red));
        } else {
            btnJoinLeave.setText(R.string.join_waiting_list);
            btnJoinLeave.setBackgroundColor(getColor(R.color.primary_pink));
        }

        // Disable button if registration closed
        btnJoinLeave.setEnabled(currentEvent != null && currentEvent.isRegistrationOpen());
    }

    /**
     * Join waiting list
     */
    private void joinWaitingList() {
        if (currentEvent == null) return;

        // Check if geolocation is required
        if (currentEvent.isGeolocationRequired()) {
            if (!geolocationService.hasLocationPermission(this)) {
                // Show permission dialog
                showLocationPermissionDialog();
                return;
            }

            // Get location and join
            captureLocationAndJoin();
        } else {
            // Join without location
            addToWaitingList(null);
        }
    }

    /**
     * Show location permission dialog
     */
    private void showLocationPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_required_title)
                .setMessage(R.string.location_required_message)
                .setPositiveButton(R.string.grant_permission, (dialog, which) -> {
                    geolocationService.requestLocationPermission(this,
                            granted -> {
                                if (granted) {
                                    captureLocationAndJoin();
                                } else {
                                    Toast.makeText(EventDetailsActivity.this,
                                            R.string.location_permission_denied,
                                            Toast.LENGTH_SHORT).show();
                                }
                            });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Capture location and add to waiting list
     */
    private void captureLocationAndJoin() {
        showLoading(true);

        geolocationService.getCurrentLocation(this, location -> {
            if (location != null) {
                addToWaitingList(location);
            } else {
                showLoading(false);
                Toast.makeText(EventDetailsActivity.this,
                        "Unable to get location. Please try again.",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Add user to waiting list
     */
    private void addToWaitingList(Geolocation location) {
        showLoading(true);

        eventRepository.addEntrantToWaitingList(eventId, currentUserId, location,
                new EventRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String id) {
                        showLoading(false);
                        isUserOnWaitingList = true;
                        updateButtonState();
                        Toast.makeText(EventDetailsActivity.this,
                                R.string.join_success,
                                Toast.LENGTH_SHORT).show();
                    }
                },
                new EventRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showLoading(false);
                        Toast.makeText(EventDetailsActivity.this,
                                getString(R.string.join_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show leave confirmation dialog
     */
    private void showLeaveConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_leave_title)
                .setMessage(R.string.confirm_leave_message)
                .setPositiveButton(R.string.leave, (dialog, which) -> leaveWaitingList())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Leave waiting list
     */
    private void leaveWaitingList() {
        showLoading(true);

        eventRepository.removeEntrantFromWaitingList(eventId, currentUserId,
                new EventRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String id) {
                        showLoading(false);
                        isUserOnWaitingList = false;
                        updateButtonState();
                        Toast.makeText(EventDetailsActivity.this,
                                R.string.leave_success,
                                Toast.LENGTH_SHORT).show();
                    }
                },
                new EventRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showLoading(false);
                        Toast.makeText(EventDetailsActivity.this,
                                getString(R.string.leave_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnJoinLeave.setEnabled(!show);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

