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
/**
 * JAVADOCS LLM GENERATED
 *
 * DialogFragment that presents an invitation prompt to an entrant who has been
 * selected for an event lottery.
 *
 * <p><b>Role / Pattern:</b> UI component that serves as a modal interface for responding
 * to event invitations (accept/decline). It is typically launched in response to an FCM
 * notification tap (via {@link com.example.pickme.services.MyFirebaseMessagingService})
 * and allows the user to interact with {@link LotteryService} directly from the alert.</p>
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Display the event invitation with its response deadline.</li>
 *   <li>Allow the entrant to accept or decline participation.</li>
 *   <li>Invoke backend logic via {@link LotteryService} to update event rosters.</li>
 *   <li>Dismiss itself after a response is submitted or an error occurs.</li>
 * </ul>
 * </p>
 *
 * <p><b>Arguments:</b> passed via {@link #newInstance(String, String, long)}</p>
 * <ul>
 *   <li>{@code eventId} – the Firestore ID of the event.</li>
 *   <li>{@code invitationId} – the document ID of the specific invitation (optional).</li>
 *   <li>{@code deadline} – UTC timestamp (millis) indicating when the invitation expires.</li>
 * </ul>
 *
 * <p><b>Lifecycle:</b> created dynamically and shown from {@code MainActivity} when
 * handling an ACTION_OPEN_INVITATION intent. No persistent ViewModel is required.</p>
 */
public class InvitationDialogFragment extends DialogFragment {

    private static final String ARG_EVENT_ID = "eventId";
    private static final String ARG_INVITATION_ID = "invitationId";
    private static final String ARG_DEADLINE = "deadline";
    /**
     * Factory method for creating a new dialog instance with the required event parameters.
     *
     * @param eventId       ID of the event for which the invitation applies.
     * @param invitationId  ID of the invitation record (may be null if not applicable).
     * @param deadlineMillis deadline timestamp in milliseconds since epoch.
     * @return a configured {@link InvitationDialogFragment} ready to be displayed.
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

    /**
     * Creates the dialog UI. Displays an invitation message with the response deadline,
     * and provides buttons to accept, decline, or view more information.
     *
     * @param savedInstanceState optional saved state from system recreation.
     * @return constructed {@link AlertDialog} instance.
     */
    @NonNull @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Bundle args = getArguments() != null ? getArguments() : Bundle.EMPTY;
        String eventId = args.getString(ARG_EVENT_ID, "");
        long deadline = args.getLong(ARG_DEADLINE, 0L);
        // format deadline into user-friendly text
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

    /**
     * Handles the user accepting the invitation.
     * Fetches the device’s user ID and notifies {@link LotteryService} to record acceptance.
     *
     * @param eventId ID of the event being accepted.
     */
    private void accept(String eventId) {
        String userId = DeviceAuthenticator.getInstance(requireContext()).getStoredUserId();
        LotteryService.getInstance().handleEntrantAcceptance(eventId, userId,
                new LotteryService.OnAcceptanceHandledListener() {
                    @Override public void onAcceptanceHandled(String entrantId) { dismissAllowingStateLoss(); }
                    @Override public void onError(Exception e) { dismissAllowingStateLoss(); }
                });
    }

    /**
     * Handles the user declining the invitation.
     * Fetches the device’s user ID and notifies {@link LotteryService} to record decline.
     *
     * @param eventId ID of the event being declined.
     */
    private void decline(String eventId) {
        String userId = DeviceAuthenticator.getInstance(requireContext()).getStoredUserId();
        LotteryService.getInstance().handleEntrantDecline(eventId, userId,
                new LotteryService.OnDeclineHandledListener() {
                    @Override public void onDeclineHandled(String entrantId, boolean replacement) { dismissAllowingStateLoss(); }
                    @Override public void onError(Exception e) { dismissAllowingStateLoss(); }
                });
    }
}