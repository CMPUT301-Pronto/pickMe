package com.example.pickme.ui.invitations;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
 * InvitationsFragment - Display and handle lottery invitations in fragment form
 *
 * Shows events where the user has been selected in the lottery
 * (user is in responsePendingList). Users can accept or decline invitations.
 *
 * Related User Stories: US 01.04.01, US 01.05.01, US 01.05.02, US 01.05.03
 */
public class InvitationsFragment extends Fragment {

    private static final String TAG = "InvitationsFragment";

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

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_event_invitations, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize
        initializeViews(view);
        initializeData();
        setupRecyclerView();

        // Load invitations
        loadInvitations();
    }

    /**
     * Initialize view references
     */
    private void initializeViews(View view) {
        recyclerViewInvitations = view.findViewById(R.id.recyclerViewInvitations);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
    }

    /**
     * Initialize data components
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        lotteryService = LotteryService.getInstance();
        deviceAuthenticator = DeviceAuthenticator.getInstance(requireContext());
        currentUserId = deviceAuthenticator.getStoredUserId();
    }

    /**
     * Setup RecyclerView with adapter
     */
    private void setupRecyclerView() {
        invitationAdapter = new InvitationAdapter();
        recyclerViewInvitations.setLayoutManager(new LinearLayoutManager(requireContext()));
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
                        Toast.makeText(requireContext(),
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
                        Toast.makeText(requireContext(),
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
                        Toast.makeText(requireContext(),
                                getString(R.string.accept_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show decline confirmation dialog
     */
    private void showDeclineConfirmation(Event event, int position) {
        new AlertDialog.Builder(requireContext())
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
                    public void onDeclineHandled(String entrantId, boolean isReplacement) {
                        showLoading(false);
                        Toast.makeText(requireContext(),
                                R.string.invitation_declined,
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
                        Toast.makeText(requireContext(),
                                getString(R.string.decline_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerViewInvitations != null) {
            recyclerViewInvitations.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Show/hide empty state
     */
    private void showEmptyState(boolean show) {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (recyclerViewInvitations != null) {
            recyclerViewInvitations.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload invitations when fragment becomes visible
        if (currentUserId != null) {
            loadInvitations();
        }
    }
}

