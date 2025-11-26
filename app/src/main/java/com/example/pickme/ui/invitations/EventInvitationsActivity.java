package com.example.pickme.ui.invitations;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.LotteryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * EventInvitationsActivity - Display and handle lottery invitations
 *
 * Shows events where the user has been selected in the lottery
 * (user is in responsePendingList). Users can accept or decline
 * invitations.
 *
 * Related User Stories: US 01.04.01, US 01.05.01, US 01.05.02, US 01.05.03
 */
public class EventInvitationsActivity extends AppCompatActivity {

    private static final String TAG = "EventInvitationsActivity";

    // UI Components
    private RecyclerView recyclerViewInvitations;
    private ProgressBar progressBar;
    private View emptyStateLayout;

    // Data
    private EventRepository eventRepository;
    private LotteryService lotteryService;
    private DeviceAuthenticator deviceAuthenticator;
    private InvitationAdapter invitationAdapter;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_invitations);

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize
        initializeViews();
        initializeData();
        setupRecyclerView();

        // Load invitations
        loadInvitations();
    }

    /**
     * Initialize view references
     */
    private void initializeViews() {
        recyclerViewInvitations = findViewById(R.id.recyclerViewInvitations);
        progressBar = findViewById(R.id.progressBar);
        emptyStateLayout = findViewById(R.id.emptyStateLayout);
    }

    /**
     * Initialize data components
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        lotteryService = LotteryService.getInstance();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        currentUserId = deviceAuthenticator.getStoredUserId();
    }

    /**
     * Setup RecyclerView with adapter
     */
    private void setupRecyclerView() {
        invitationAdapter = new InvitationAdapter();
        recyclerViewInvitations.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewInvitations.setAdapter(invitationAdapter);

        invitationAdapter.setOnInvitationActionListener(new InvitationAdapter.OnInvitationActionListener() {
            @Override
            public void onAcceptInvitation(Event event, int position) {
                acceptInvitation(event, position);
            }

            @Override
            public void onDeclineInvitation(Event event, int position) {
                showDeclineConfirmation(event, position);
            }
        });
    }

    /**
     * Load user's pending invitations
     */
    private void loadInvitations() {
        showLoading(true);

        // Query Firestore for events where user is in responsePendingList subcollection
        eventRepository.getEventsWhereEntrantInResponsePending(currentUserId,
                new EventRepository.OnEventsWithMetadataLoadedListener() {
                    @Override
                    public void onEventsLoaded(List<Event> events, Map<String, Object> metadata) {
                        // Extract deadlines from metadata
                        List<Long> deadlines = new ArrayList<>();
                        for (Event event : events) {
                            Object deadlineObj = metadata.get(event.getEventId() + "_deadline");
                            if (deadlineObj instanceof Long) {
                                deadlines.add((Long) deadlineObj);
                            } else {
                                // Default: 7 days from now if no deadline found
                                deadlines.add(System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L));
                            }
                        }

                        updateInvitationsList(events, deadlines);
                    }

                    @Override
                    public void onError(Exception e) {
                        showLoading(false);
                        Toast.makeText(EventInvitationsActivity.this,
                                getString(R.string.error_occurred) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        showEmptyState(true);
                    }
                });
    }

    /**
     * Update invitations list in adapter
     */
    private void updateInvitationsList(List<Event> invitations, List<Long> deadlines) {
        showLoading(false);
        invitationAdapter.setInvitations(invitations, deadlines);

        if (invitations.isEmpty()) {
            showEmptyState(true);
        } else {
            showEmptyState(false);
        }
    }

    /**
     * Accept invitation
     */
    private void acceptInvitation(Event event, int position) {
        showLoading(true);

        lotteryService.handleEntrantAcceptance(event.getEventId(), currentUserId,
                new LotteryService.OnAcceptanceHandledListener() {
                    @Override
                    public void onAcceptanceHandled(String entrantId) {
                        showLoading(false);
                        Toast.makeText(EventInvitationsActivity.this,
                                R.string.invitation_accepted,
                                Toast.LENGTH_SHORT).show();

                        // Remove from list
                        invitationAdapter.removeInvitation(position);

                        // Check if list is now empty
                        if (invitationAdapter.getItemCount() == 0) {
                            showEmptyState(true);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        showLoading(false);
                        Toast.makeText(EventInvitationsActivity.this,
                                getString(R.string.accept_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show decline confirmation dialog
     */
    private void showDeclineConfirmation(Event event, int position) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.decline_invitation)
                .setMessage(R.string.confirm_decline_invitation)
                .setPositiveButton(R.string.decline_invitation, (dialog, which) ->
                        declineInvitation(event, position))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /**
     * Decline invitation
     */
    private void declineInvitation(Event event, int position) {
        showLoading(true);

        lotteryService.handleEntrantDecline(event.getEventId(), currentUserId,
                new LotteryService.OnDeclineHandledListener() {
                    @Override
                    public void onDeclineHandled(String entrantId, boolean shouldTriggerReplacement) {
                        showLoading(false);
                        Toast.makeText(EventInvitationsActivity.this,
                                R.string.invitation_declined,
                                Toast.LENGTH_SHORT).show();

                        // Remove from list
                        invitationAdapter.removeInvitation(position);

                        // Check if list is now empty
                        if (invitationAdapter.getItemCount() == 0) {
                            showEmptyState(true);
                        }

                        // Trigger replacement draw if needed
                        if (shouldTriggerReplacement) {
                            triggerReplacementDraw(event);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        showLoading(false);
                        Toast.makeText(EventInvitationsActivity.this,
                                getString(R.string.decline_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Trigger replacement draw to fill the declined spot
     */
    private void triggerReplacementDraw(Event event) {
        lotteryService.executeReplacementDraw(event.getEventId(), 1,
                new LotteryService.OnLotteryCompleteListener() {
                    @Override
                    public void onLotteryComplete(LotteryService.LotteryResult result) {
                        if (!result.winners.isEmpty()) {
                            // Replacement winner selected - notification handled by service
                            android.util.Log.d(TAG, "Replacement winner selected for event: " + event.getName());
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        // Log but don't show error to user (background operation)
                        android.util.Log.w(TAG, "Failed to execute replacement draw", e);
                    }
                });
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewInvitations.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    /**
     * Show/hide empty state
     */
    private void showEmptyState(boolean show) {
        emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        recyclerViewInvitations.setVisibility(show ? View.GONE : View.VISIBLE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

