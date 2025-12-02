package com.example.pickme.ui.events;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Displays details for a single {@link Event} and lets the user join/leave its waiting list.
 *
 * FIXED: Now properly checks all subcollections (waitingList, responsePendingList,
 * inEventList, cancelledList) to show correct user state.
 *
 * Related User Stories: US 01.01.01, US 01.01.02, US 01.04.01, US 01.05.04, US 01.05.05, US 01.06.03
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
    private TextView tvLotteryCriteria;
    private TextView tvUserStatus;  // NEW: Show user's current status
    private Button btnJoinLeave;
    private ProgressBar progressBar;

    // Data / services
    private EventRepository eventRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private GeolocationService geolocationService;
    private FirebaseFirestore db;

    // State
    private String eventId;
    private Event currentEvent;
    private String currentUserId;
    private SimpleDateFormat dateFormat;

    // User's current status in the event
    private enum UserStatus {
        NOT_JOINED,           // User hasn't joined any list
        ON_WAITING_LIST,      // User is on the waiting list
        SELECTED_PENDING,     // User was selected, awaiting response
        CONFIRMED,            // User accepted and is confirmed
        CANCELLED             // User was cancelled
    }
    private UserStatus userStatus = UserStatus.NOT_JOINED;

    // Real-time listener for waiting list count
    private ListenerRegistration waitingListListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_details);

        eventId = getIntent().getStringExtra(EventBrowseFragment.EXTRA_EVENT_ID);
        if (eventId == null) {
            Toast.makeText(this, "Event not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        initializeData();
        setupToolbar();
        setupButton();

        loadEvent();
    }

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

        // Try to find user status TextView (may not exist in older layouts)
        tvUserStatus = findViewById(R.id.tvUserStatus);
    }

    private void initializeData() {
        eventRepository     = new EventRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        geolocationService  = new GeolocationService(this);
        db                  = FirebaseFirestore.getInstance();

        currentUserId = deviceAuthenticator.getStoredUserId();
        dateFormat = new SimpleDateFormat("MMMM dd, yyyy", Locale.getDefault());
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
                getSupportActionBar().setTitle("");
            }
            toolbar.setNavigationOnClickListener(v ->
                    getOnBackPressedDispatcher().onBackPressed());
        }
    }

    private void setupButton() {
        btnJoinLeave.setOnClickListener(v -> {
            switch (userStatus) {
                case ON_WAITING_LIST:
                    showLeaveConfirmation();
                    break;
                case NOT_JOINED:
                    joinWaitingList();
                    break;
                case SELECTED_PENDING:
                    // Navigate to invitations
                    Toast.makeText(this, "Please check your invitations to respond", Toast.LENGTH_SHORT).show();
                    break;
                case CONFIRMED:
                    // Could allow leaving confirmed status
                    showLeaveConfirmedDialog();
                    break;
                case CANCELLED:
                    // User was cancelled - they can't rejoin
                    Toast.makeText(this, "You were removed from this event", Toast.LENGTH_SHORT).show();
                    break;
            }
        });
    }

    private void loadEvent() {
        showLoading(true);

        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                currentEvent = event;
                displayEvent(event);
                startWaitingListListener();

                if (currentUserId == null || currentUserId.isEmpty()) {
                    DeviceAuthenticator.getInstance(EventDetailsActivity.this)
                            .initializeUser(new DeviceAuthenticator.OnUserInitializedListener() {
                                @Override
                                public void onUserInitialized(Profile profile, boolean isNewUser) {
                                    currentUserId = profile.getUserId();
                                    checkAllListsForUser();
                                    showLoading(false);
                                }

                                @Override
                                public void onError(Exception e) {
                                    userStatus = UserStatus.NOT_JOINED;
                                    updateButtonState();
                                    showLoading(false);
                                }
                            });
                } else {
                    checkAllListsForUser();
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
     * FIXED: Check ALL subcollections to determine user's actual status
     * This fixes US 01.04.01 - Event page now properly reflects user state
     */
    private void checkAllListsForUser() {
        if (currentEvent == null || currentUserId == null || currentUserId.isEmpty()) {
            userStatus = UserStatus.NOT_JOINED;
            updateButtonState();
            return;
        }

        // Check waitingList first
        db.collection("events").document(eventId)
                .collection("waitingList").document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        userStatus = UserStatus.ON_WAITING_LIST;
                        updateButtonState();
                    } else {
                        // Not on waiting list, check responsePendingList
                        checkResponsePendingList();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check waiting list", e);
                    userStatus = UserStatus.NOT_JOINED;
                    updateButtonState();
                });
    }

    private void checkResponsePendingList() {
        db.collection("events").document(eventId)
                .collection("responsePendingList").document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        userStatus = UserStatus.SELECTED_PENDING;
                        updateButtonState();
                    } else {
                        // Not pending, check inEventList
                        checkInEventList();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check response pending list", e);
                    checkInEventList();
                });
    }

    private void checkInEventList() {
        db.collection("events").document(eventId)
                .collection("inEventList").document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        userStatus = UserStatus.CONFIRMED;
                        updateButtonState();
                    } else {
                        // Not confirmed, check cancelledList
                        checkCancelledList();
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check in-event list", e);
                    checkCancelledList();
                });
    }

    private void checkCancelledList() {
        db.collection("events").document(eventId)
                .collection("cancelledList").document(currentUserId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists()) {
                        userStatus = UserStatus.CANCELLED;
                    } else {
                        userStatus = UserStatus.NOT_JOINED;
                    }
                    updateButtonState();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to check cancelled list", e);
                    userStatus = UserStatus.NOT_JOINED;
                    updateButtonState();
                });
    }

    /**
     * FIXED: Update button based on actual user status in all lists
     */
    private void updateButtonState() {
        String statusText = "";

        switch (userStatus) {
            case ON_WAITING_LIST:
                btnJoinLeave.setText(R.string.leave_waiting_list);
                btnJoinLeave.setBackgroundColor(getColor(R.color.error_red));
                btnJoinLeave.setEnabled(true);
                statusText = "You are on the waiting list";
                break;

            case SELECTED_PENDING:
                btnJoinLeave.setText("Respond to Invitation");
                btnJoinLeave.setBackgroundColor(getColor(R.color.warning_orange));
                btnJoinLeave.setEnabled(true);
                statusText = "You have been selected! Check invitations to respond.";
                break;

            case CONFIRMED:
                btnJoinLeave.setText("Leave Event");
                btnJoinLeave.setBackgroundColor(getColor(R.color.error_red));
                btnJoinLeave.setEnabled(true);
                statusText = "You are confirmed for this event";
                break;

            case CANCELLED:
                btnJoinLeave.setText("Cannot Join");
                btnJoinLeave.setBackgroundColor(getColor(R.color.text_secondary));
                btnJoinLeave.setEnabled(false);
                statusText = "You were removed from this event";
                break;

            case NOT_JOINED:
            default:
                btnJoinLeave.setText(R.string.join_waiting_list);
                btnJoinLeave.setBackgroundColor(getColor(R.color.primary_pink));
                btnJoinLeave.setEnabled(currentEvent != null && currentEvent.isRegistrationOpen());
                statusText = "";
                break;
        }

        // Update status text if view exists
        if (tvUserStatus != null) {
            if (statusText.isEmpty()) {
                tvUserStatus.setVisibility(View.GONE);
            } else {
                tvUserStatus.setVisibility(View.VISIBLE);
                tvUserStatus.setText(statusText);
            }
        }
    }

    private void displayEvent(Event event) {
        tvEventName.setText(event.getName());
        collapsingToolbar.setTitle(event.getName());

        if (event.getPosterImageUrl() != null && !event.getPosterImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(event.getPosterImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(ivEventPoster);
        }

        if (event.isRegistrationOpen()) {
            tvRegistrationStatus.setText(R.string.registration_open);
            tvRegistrationStatus.setBackgroundResource(R.drawable.badge_background);
        } else {
            tvRegistrationStatus.setText(R.string.registration_closed);
            tvRegistrationStatus.setBackgroundColor(getColor(R.color.text_secondary));
        }

        if (event.getEventDates() != null && !event.getEventDates().isEmpty()) {
            long dateMillis = event.getEventDates().get(0);
            String formattedDate = dateFormat.format(new Date(dateMillis));
            tvEventDate.setText(formattedDate);
        }

        tvEventLocation.setText(event.getLocation() != null ? event.getLocation() : "");

        if (event.getPrice() > 0) {
            tvEventPrice.setText(getString(R.string.event_price, event.getPrice()));
        } else {
            tvEventPrice.setText(R.string.free_event);
        }

        tvCapacity.setText(getString(R.string.event_capacity, event.getCapacity()));
        tvWaitingListStatus.setText(getString(R.string.waiting_list_status, 0));
        tvGeolocationWarning.setVisibility(event.isGeolocationRequired() ? View.VISIBLE : View.GONE);
        tvEventDescription.setText(event.getDescription() != null ? event.getDescription() : "");
    }

    private void startWaitingListListener() {
        stopWaitingListListener();

        waitingListListener = db.collection("events")
                .document(eventId)
                .collection("waitingList")
                .addSnapshotListener((querySnapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to waiting list", error);
                        return;
                    }

                    if (querySnapshot != null) {
                        int count = querySnapshot.size();
                        updateWaitingListCount(count);
                        Log.d(TAG, "Waiting list count updated: " + count);
                    }
                });
    }

    private void stopWaitingListListener() {
        if (waitingListListener != null) {
            waitingListListener.remove();
            waitingListListener = null;
        }
    }

    private void updateWaitingListCount(int count) {
        if (tvWaitingListStatus != null) {
            tvWaitingListStatus.setText(getString(R.string.waiting_list_status, count));
        }
    }

    private void joinWaitingList() {
        if (currentEvent == null) return;

        if (currentEvent.isGeolocationRequired()) {
            if (!geolocationService.hasLocationPermission(this)) {
                showLocationPermissionDialog();
                return;
            }
            captureLocationAndJoin();
        } else {
            addToWaitingList(null);
        }
    }

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
                        userStatus = UserStatus.ON_WAITING_LIST;
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

    private void showLeaveConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.confirm_leave_title)
                .setMessage(R.string.confirm_leave_message)
                .setPositiveButton(R.string.leave, (dialog, which) -> leaveWaitingList())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showLeaveConfirmedDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Leave Event?")
                .setMessage("You are confirmed for this event. Are you sure you want to leave? Your spot may be given to someone else.")
                .setPositiveButton("Leave", (dialog, which) -> leaveConfirmedEvent())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void leaveWaitingList() {
        showLoading(true);

        eventRepository.removeEntrantFromWaitingList(
                eventId,
                currentUserId,
                new EventRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String id) {
                        showLoading(false);
                        userStatus = UserStatus.NOT_JOINED;
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
     * Leave a confirmed event (remove from inEventList)
     */
    private void leaveConfirmedEvent() {
        showLoading(true);

        db.collection("events").document(eventId)
                .collection("inEventList").document(currentUserId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    showLoading(false);
                    userStatus = UserStatus.NOT_JOINED;
                    updateButtonState();
                    Toast.makeText(this, "You have left this event", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    showLoading(false);
                    Toast.makeText(this, "Failed to leave event: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnJoinLeave.setEnabled(!show);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh user status when returning to this activity
        if (currentEvent != null && currentUserId != null) {
            checkAllListsForUser();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopWaitingListListener();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}