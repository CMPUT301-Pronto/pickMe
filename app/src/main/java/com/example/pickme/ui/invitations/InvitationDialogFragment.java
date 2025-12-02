package com.example.pickme.ui.invitations;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.app.AlertDialog;

import com.example.pickme.R;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.LotteryService;

import java.util.Date;

/**
 * DialogFragment that presents an invitation prompt to an entrant who has been
 * selected for an event lottery.
 *
 * FIXED: Now broadcasts when invitation is responded to, so InvitationsFragment can refresh
 */
public class InvitationDialogFragment extends DialogFragment {

    private static final String ARG_EVENT_ID = "eventId";
    private static final String ARG_INVITATION_ID = "invitationId";
    private static final String ARG_DEADLINE = "deadline";

    // Broadcast action for notifying that an invitation was responded to
    public static final String ACTION_INVITATION_RESPONDED = "com.example.pickme.INVITATION_RESPONDED";

    /**
     * Factory method for creating a new dialog instance with the required event parameters.
     */
    public static InvitationDialogFragment newInstance(String eventId, String invitationId, long deadlineMillis) {
        InvitationDialogFragment f = new InvitationDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_EVENT_ID, eventId);
        b.putString(ARG_INVITATION_ID, invitationId);
        b.putLong(ARG_DEADLINE, deadlineMillis);
        f.setArguments(b);
        return f;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() != null ? getArguments() : Bundle.EMPTY;
        String eventId = args.getString(ARG_EVENT_ID, "");
        long deadline = args.getLong(ARG_DEADLINE, 0L);

        String deadlineText = deadline > 0
                ? DateFormat.getMediumDateFormat(requireContext()).format(new Date(deadline))
                : getString(R.string.invitation_deadline_unknown);

        return new AlertDialog.Builder(requireContext())
                .setTitle(R.string.invitations_title)
                .setMessage(getString(R.string.invitation_body_with_deadline, deadlineText))
                .setPositiveButton(R.string.accept_invitation, (d, w) -> accept(eventId))
                .setNegativeButton(R.string.decline_invitation, (d, w) -> decline(eventId))
                .setNeutralButton(R.string.view_more, (d, w) -> {
                    // Optional: open full invitations screen
                })
                .create();
    }

    /**
     * Handles the user accepting the invitation.
     */
    private void accept(String eventId) {
        String userId = DeviceAuthenticator.getInstance(requireContext()).getStoredUserId();
        LotteryService.getInstance().handleEntrantAcceptance(eventId, userId,
                new LotteryService.OnAcceptanceHandledListener() {
                    @Override
                    public void onAcceptanceHandled(String entrantId) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Invitation accepted!", Toast.LENGTH_SHORT).show();
                        }
                        notifyInvitationResponded();
                        dismissAllowingStateLoss();
                    }

                    @Override
                    public void onError(Exception e) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        dismissAllowingStateLoss();
                    }
                });
    }

    /**
     * Handles the user declining the invitation.
     */
    private void decline(String eventId) {
        String userId = DeviceAuthenticator.getInstance(requireContext()).getStoredUserId();
        LotteryService.getInstance().handleEntrantDecline(eventId, userId,
                new LotteryService.OnDeclineHandledListener() {
                    @Override
                    public void onDeclineHandled(String entrantId, boolean replacement) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Invitation declined", Toast.LENGTH_SHORT).show();
                        }
                        notifyInvitationResponded();
                        dismissAllowingStateLoss();
                    }

                    @Override
                    public void onError(Exception e) {
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                        dismissAllowingStateLoss();
                    }
                });
    }

    /**
     * Notify that an invitation was responded to so lists can refresh
     */
    private void notifyInvitationResponded() {
        if (getContext() != null) {
            // Send local broadcast so InvitationsFragment can refresh
            Intent intent = new Intent(ACTION_INVITATION_RESPONDED);
            LocalBroadcastManager.getInstance(getContext()).sendBroadcast(intent);
        }
    }
}