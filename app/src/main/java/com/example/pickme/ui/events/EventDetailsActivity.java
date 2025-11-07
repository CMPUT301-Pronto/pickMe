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
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.models.Geolocation;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.GeolocationService;
import com.google.android.material.appbar.CollapsingToolbarLayout;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * JAVADOC LLM GENERATED
 *
 * Displays details for a single {@link Event} and lets the user join/leave its waiting list.
 *
 * <p><b>Responsibilities</b></p>
 * <ul>
 *   <li>Render event metadata (poster, dates, price, location, etc.).</li>
 *   <li>Reflect registration state and waiting-list membership in UI.</li>
 *   <li>Gate joining by geolocation if required by the event.</li>
 *   <li>Coordinate with repositories/services for data and permissions.</li>
 * </ul>
 *
 * <p><b>Related User Stories:</b>
 * US 01.01.01, US 01.01.02, US 01.05.04, US 01.05.05</p>
 */
public class EventDetailsActivity extends AppCompatActivity {

    private static final String TAG = "EventDetailsActivity";

    // UI
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
    private TextView tvLotteryCriteria; // present for future criteria display
    private Button btnJoinLeave;
    private ProgressBar progressBar;

    // Data / services
    private EventRepository eventRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private GeolocationService geolocationService;

    // State
    private String eventId;
    private Event currentEvent;
    private String currentUserId;
    private boolean isUserOnWaitingList = false;
    private SimpleDateFormat dateFormat;

    /**
     * Lifecycle entry: inflates layout, wires services and UI, then loads the event.
     *
     * <p>Expects the launching intent to include {@link EventBrowseFragment#EXTRA_EVENT_ID}.</p>
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        // Get event ID from intent; bail if missing
        eventId = getIntent().getStringExtra(EventBrowseFragment.EXTRA_EVENT_ID);
        if (eventId == null) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Basic setup
        initializeViews();
        initializeData();
        setupToolbar();
        setupButton();

        // Pull data and render
        loadEvent();
    }

    /**
     * Finds and caches view references.
     */
    private void initializeViews() {
        collapsingToolbar    = findViewById(R.id.collapsingToolbar);
        ivEventPoster        = findViewById(R.id.ivEventPoster);
        tvEventName          = findViewById(R.id.tvEventName);
        tvRegistrationStatus = findViewById(R.id.tvRegistrationStatus);
        tvEventDate          = findViewById(R.id.tvEventDate);
        tvEventLocation      = findViewById(R.id.tvEventLocation);
        tvEventPrice         = findViewById(R.id.tvEventPrice);
        tvCapacity           = findViewById(R.id.tvCapacity);
        tvWaitingListStatus  = findViewById(R.id.tvWaitingListStatus);
        tvGeolocationWarning = findViewById(R.id.tvGeolocationWarning);
        tvEventDescription   = findViewById(R.id.tvEventDescription);
        tvLotteryCriteria    = findViewById(R.id.tvLotteryCriteria);
        btnJoinLeave         = findViewById(R.id.btnJoinLeave);
        progressBar          = findViewById(R.id.progressBar);
    }

    /**
     * Instantiates repositories/services and derives initial state (user id, date formatting).
     */
    private void initializeData() {
        eventRepository     = new EventRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        geolocationService  = new GeolocationService(this);

        // We may not have a cached profile yet; that's fine — we lazily init on demand
        currentUserId = deviceAuthenticator.getStoredUserId();

        dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
    }

    /**
     * Configures the top app bar and back navigation.
     */
    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                // Title is shown in the collapsing layout instead
                getSupportActionBar().setTitle("");
            }
            toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed());
        }
    }

    /**
     * Wires the primary CTA to either join or leave the waiting list depending on current state.
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
     * Fetches the event from the repository and renders it.
     *
     * <p>Also ensures we have a device user id before checking waiting-list membership.</p>
     */
    private void loadEvent() {
        showLoading(true);

        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                currentEvent = event;
                displayEvent(event);

                // If we don't have a user id yet, initialize the device identity first
                if (currentUserId == null || currentUserId.isEmpty()) {
                    DeviceAuthenticator.getInstance(EventDetailsActivity.this)
                            .initializeUser(new DeviceAuthenticator.OnUserInitializedListener() {
                                @Override
                                public void onUserInitialized(Profile profile, boolean isNewUser) {
                                    currentUserId = profile.getUserId();
                                    checkWaitingListStatusSafely();
                                    showLoading(false);
                                }

                                @Override
                                public void onError(Exception e) {
                                    // Proceed without a user id; just render UI as "not joined"
                                    checkWaitingListStatusSafely();
                                    showLoading(false);
                                }
                            });
                } else {
                    checkWaitingListStatusSafely();
                    showLoading(false);
                }
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(
                        EventDetailsActivity.this,
                        getString(R.string.error_occurred)
                                + (e != null && e.getMessage() != null ? (": " + e.getMessage()) : ""),
                        Toast.LENGTH_SHORT
                ).show();
                finish();
            }
        });
    }

    /**
     * Defensive wrapper: only checks membership when both event and user id exist.
     */
    private void checkWaitingListStatusSafely() {
        if (currentEvent == null || currentUserId == null || currentUserId.isEmpty()) {
            // No user -> reflect as "not on list"
            isUserOnWaitingList = false;
            updateButtonState();
            return;
        }
        checkWaitingListStatus();
    }

    /**
     * Renders all event fields into the view.
     */
    private void displayEvent(Event event) {
        // Name / title
        tvEventName.setText(event.getName());
        collapsingToolbar.setTitle(event.getName());

        // Poster (optional)
        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(event.getPosterImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(ivEventPoster);
        }

        // Registration badges
        if (event.isRegistrationOpen()) {
            tvRegistrationStatus.setText(R.string.registration_open);
            tvRegistrationStatus.setBackgroundResource(R.drawable.badge_background);
        } else {
            tvRegistrationStatus.setText(R.string.registration_closed);
            tvRegistrationStatus.setBackgroundColor(getColor(R.color.text_secondary));
        }

        // First event date, if available
        if (event.getEventDates() != null && !event.getEventDates().isEmpty()) {
            long dateMillis = event.getEventDates().get(0);
            String formattedDate = dateFormat.format(new Date(dateMillis));
            tvEventDate.setText(formattedDate);
        }

        // Basic fields
        tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "");

        if (event.getPrice() > 0) {
            tvEventPrice.setText(getString(R.string.event_price, event.getPrice()));
        } else {
            tvEventPrice.setText(R.string.free_event);
        }

        tvCapacity.setText(getString(R.string.event_capacity, event.getCapacity()));

        // TODO: Replace "0" with actual size of waiting list if you track it
        tvWaitingListStatus.setText(getString(R.string.waiting_list_status, 0));

        tvGeolocationWarning.setVisibility(event.isGeolocationRequired() ? View.VISIBLE : View.GONE);

        tvEventDescription.setText(event.getDescription() != null ? event.getDescription() : "");
    }

    /**
     * Asks the repository whether this device user is already in the waiting list.
     */
    private void checkWaitingListStatus() {
        eventRepository.isEntrantInWaitingList(
                eventId,
                currentUserId,
                new EventRepository.OnEntrantCheckListener() {
                    @Override
                    public void onCheckComplete(boolean exists) {
                        isUserOnWaitingList = exists;
                        updateButtonState();
                    }

                    @Override
                    public void onError(Exception e) {
                        // On error just assume not in list to keep UI usable
                        isUserOnWaitingList = false;
                        updateButtonState();
                    }
                }
        );
    }

    /**
     * Updates the CTA text/color and enables/disables based on state.
     */
    private void updateButtonState() {
        if (isUserOnWaitingList) {
            btnJoinLeave.setText(R.string.leave_waiting_list);
            btnJoinLeave.setBackgroundColor(getColor(R.color.error_red));
        } else {
            btnJoinLeave.setText(R.string.join_waiting_list);
            btnJoinLeave.setBackgroundColor(getColor(R.color.primary_pink));
        }

        // If registration is closed, joining/leaving shouldn't be clickable
        btnJoinLeave.setEnabled(currentEvent != null && currentEvent.isRegistrationOpen());
    }

    /**
     * Handles the "join" path. If geolocation is required, requests permission and location first.
     */
    private void joinWaitingList() {
        if (currentEvent == null) return;

        if (currentEvent.isGeolocationRequired()) {
            // Need location permission first
            if (!geolocationService.hasLocationPermission(this)) {
                showLocationPermissionDialog();
                return;
            }
            // Already have permission — capture and submit
            captureLocationAndJoin();
        } else {
            // No geolocation requirement — submit directly
            addToWaitingList(null);
        }
    }

    /**
     * Prompts the user for location permission and continues flow based on their decision.
     */
    private void showLocationPermissionDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.location_required_title)
                .setMessage(R.string.location_required_message)
                .setPositiveButton(R.string.grant_permission, (dialog, which) ->
                        geolocationService.requestLocationPermission(
                                this,
                                granted -> {
                                    if (granted) {
                                        captureLocationAndJoin();
                                    } else {
                                        Toast.makeText(
                                                EventDetailsActivity.this,
                                                R.string.location_permission_denied,
                                                Toast.LENGTH_SHORT
                                        ).show();
                                    }
                                }
                        ))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Obtains a single location fix and then attempts to join the waiting list.
     */
    private void captureLocationAndJoin() {
        showLoading(true);

        geolocationService.getCurrentLocation(this, location -> {
            if (location != null) {
                addToWaitingList(location);
            } else {
                showLoading(false);
                Toast.makeText(
                        EventDetailsActivity.this,
                        "Unable to get location. Please try again.",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    /**
     * Submits the join request to the repository, optionally including a {@link Geolocation}.
     *
     * @param location nullable location to attach if the event requires it.
     */
    private void addToWaitingList(Geolocation location) {
        showLoading(true);

        eventRepository.addEntrantToWaitingList(
                eventId,
                currentUserId,
                location,
                new EventRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String id) {
                        showLoading(false);
                        isUserOnWaitingList = true;
                        updateButtonState();
                        Toast.makeText(
                                EventDetailsActivity.this,
                                R.string.join_success,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                },
                new EventRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showLoading(false);
                        Toast.makeText(
                                EventDetailsActivity.this,
                                getString(R.string.join_failed)
                                        + (e != null && e.getMessage() != null ? (": " + e.getMessage()) : ""),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    /**
     * Confirms the user's intent to leave the waiting list before proceeding.
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
     * Removes the user from the waiting list and updates the UI.
     */
    private void leaveWaitingList() {
        showLoading(true);

        eventRepository.removeEntrantFromWaitingList(
                eventId,
                currentUserId,
                new EventRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String id) {
                        showLoading(false);
                        isUserOnWaitingList = false;
                        updateButtonState();
                        Toast.makeText(
                                EventDetailsActivity.this,
                                R.string.leave_success,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                },
                new EventRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showLoading(false);
                        Toast.makeText(
                                EventDetailsActivity.this,
                                getString(R.string.leave_failed)
                                        + (e != null && e.getMessage() != null ? (": " + e.getMessage()) : ""),
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    /**
     * Toggles the progress indicator and disables primary controls while work is in flight.
     *
     * @param show true to show progress and disable actions; false to hide and re-enable
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnJoinLeave.setEnabled(!show);
    }

    /**
     * Handles up-navigation from the toolbar.
     *
     * @return true after finishing this Activity.
     */
    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}