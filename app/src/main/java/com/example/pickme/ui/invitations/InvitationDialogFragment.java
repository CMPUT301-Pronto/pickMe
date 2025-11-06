package com.example.pickme.ui.invitations;

import android.app.Dialog;
import android.os.Bundle;
import android.text.format.DateFormat;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import android.app.AlertDialog;
import com.example.pickme.R;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.LotteryService;

import java.util.Date;

public class InvitationDialogFragment extends DialogFragment {

    private static final String ARG_EVENT_ID = "eventId";
    private static final String ARG_INVITATION_ID = "invitationId";
    private static final String ARG_DEADLINE = "deadline";

    public static InvitationDialogFragment newInstance(String eventId, String invitationId, long deadlineMillis) {
        InvitationDialogFragment f = new InvitationDialogFragment();
        Bundle b = new Bundle();
        b.putString(ARG_EVENT_ID, eventId);
        b.putString(ARG_INVITATION_ID, invitationId);
        b.putLong(ARG_DEADLINE, deadlineMillis);
        f.setArguments(b);
        return f;
    }

    @NonNull @Override
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
                    // startActivity(new Intent(requireContext(), EventInvitationsActivity.class));
                })
                .create();
    }

    private void accept(String eventId) {
        String userId = DeviceAuthenticator.getInstance(requireContext()).getStoredUserId();
        LotteryService.getInstance().handleEntrantAcceptance(eventId, userId,
                new LotteryService.OnAcceptanceHandledListener() {
                    @Override public void onAcceptanceHandled(String entrantId) { dismissAllowingStateLoss(); }
                    @Override public void onError(Exception e) { dismissAllowingStateLoss(); }
                });
    }

    private void decline(String eventId) {
        String userId = DeviceAuthenticator.getInstance(requireContext()).getStoredUserId();
        LotteryService.getInstance().handleEntrantDecline(eventId, userId,
                new LotteryService.OnDeclineHandledListener() {
                    @Override public void onDeclineHandled(String entrantId, boolean replacement) { dismissAllowingStateLoss(); }
                    @Override public void onError(Exception e) { dismissAllowingStateLoss(); }
                });
    }
}