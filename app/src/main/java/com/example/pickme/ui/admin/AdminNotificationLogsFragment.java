package com.example.pickme.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.models.NotificationLog;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ProfileRepository;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * AdminNotificationLogsFragment - View and review all notification logs
 *
 * Allows admins to:
 * - View all notifications sent through the system
 * - See timestamp, sender, event, recipient count, and message
 * - Click to view full details
 * - Sort by date (newest first)
 *
 * Related User Stories: US 03.08.01 - Admin review notification logs
 */
public class AdminNotificationLogsFragment extends Fragment implements NotificationLogAdapter.OnLogClickListener {

    private static final String TAG = "AdminNotificationLogs";
    private static final int LOGS_PER_PAGE = 20;

    // UI Components
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;
    private TextView tvEmptyMessage;

    // Data
    private NotificationLogAdapter adapter;
    private FirebaseFirestore db;
    private EventRepository eventRepository;
    private ProfileRepository profileRepository;
    private List<NotificationLog> allLogs;
    private boolean isLoading = false;
    private DocumentSnapshot lastDocument;
    private SimpleDateFormat dateFormat;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize
        initializeViews(view);
        initializeData();
        loadNotificationLogs();
    }

    private void initializeViews(View view) {
        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyState = view.findViewById(R.id.emptyState);
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage);

        // Setup RecyclerView
        adapter = new NotificationLogAdapter(this);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.setAdapter(adapter);

        // Setup pagination (load more on scroll)
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                if (layoutManager != null && !isLoading) {
                    int visibleItemCount = layoutManager.getChildCount();
                    int totalItemCount = layoutManager.getItemCount();
                    int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();

                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 5) {
                        loadMoreLogs();
                    }
                }
            }
        });
    }

    private void initializeData() {
        db = FirebaseFirestore.getInstance();
        eventRepository = new EventRepository();
        profileRepository = new ProfileRepository();
        allLogs = new ArrayList<>();
        dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault());
    }

    private void loadNotificationLogs() {
        if (isLoading) return;

        isLoading = true;
        showLoading(true);

        db.collection("notification_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(LOGS_PER_PAGE)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        showEmptyState();
                        return;
                    }

                    List<NotificationLog> logs = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        NotificationLog log = doc.toObject(NotificationLog.class);
                        if (log != null) {
                            logs.add(log);
                        }
                    }

                    allLogs = logs;
                    adapter.setLogs(logs);

                    if (!querySnapshot.isEmpty()) {
                        lastDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);
                    }

                    showLoading(false);
                    isLoading = false;

                    Log.d(TAG, "Loaded " + logs.size() + " notification logs");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load notification logs", e);
                    showLoading(false);
                    isLoading = false;
                    showEmptyState("Failed to load notification logs");
                });
    }

    private void loadMoreLogs() {
        if (isLoading || lastDocument == null) return;

        isLoading = true;

        db.collection("notification_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .startAfter(lastDocument)
                .limit(LOGS_PER_PAGE)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        List<NotificationLog> logs = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            NotificationLog log = doc.toObject(NotificationLog.class);
                            if (log != null) {
                                logs.add(log);
                            }
                        }

                        allLogs.addAll(logs);
                        adapter.addLogs(logs);
                        lastDocument = querySnapshot.getDocuments().get(querySnapshot.size() - 1);

                        Log.d(TAG, "Loaded " + logs.size() + " more notification logs");
                    }

                    isLoading = false;
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load more logs", e);
                    isLoading = false;
                });
    }

    @Override
    public void onLogClick(NotificationLog log) {
        showLogDetailsDialog(log);
    }

    private void showLogDetailsDialog(NotificationLog log) {
        if (getContext() == null) return;

        // Build detailed message
        StringBuilder details = new StringBuilder();
        details.append("Timestamp: ").append(dateFormat.format(new Date(log.getTimestamp()))).append("\n\n");
        details.append("Type: ").append(formatType(log.getNotificationType())).append("\n\n");
        details.append("Event ID: ").append(log.getEventId()).append("\n\n");

        // Load event name
        loadEventName(log.getEventId(), eventName -> {
            details.append("Event: ").append(eventName).append("\n\n");

            // Load sender name
            loadSenderName(log.getSenderId(), senderName -> {
                details.append("Sender: ").append(senderName).append("\n\n");
                details.append("Recipients: ").append(log.getRecipientIds().size()).append("\n\n");
                details.append("Message:\n").append(log.getMessageContent());

                // Show dialog
                new AlertDialog.Builder(getContext())
                        .setTitle("Notification Details")
                        .setMessage(details.toString())
                        .setPositiveButton("View Recipients", (dialog, which) -> {
                            showRecipientsDialog(log.getRecipientIds());
                        })
                        .setNegativeButton("Close", null)
                        .show();
            });
        });
    }

    private void loadEventName(String eventId, OnNameLoadedListener listener) {
        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                listener.onNameLoaded(event.getName());
            }

            @Override
            public void onError(Exception e) {
                listener.onNameLoaded("Unknown Event");
            }
        });
    }

    private void loadSenderName(String senderId, OnNameLoadedListener listener) {
        profileRepository.getProfile(senderId, new ProfileRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(Profile profile) {
                listener.onNameLoaded(profile.getName());
            }

            @Override
            public void onError(Exception e) {
                listener.onNameLoaded("System");
            }
        });
    }

    private void showRecipientsDialog(List<String> recipientIds) {
        if (getContext() == null || recipientIds == null) return;

        StringBuilder recipients = new StringBuilder();
        recipients.append("Total Recipients: ").append(recipientIds.size()).append("\n\n");

        // Load first 20 recipient names
        int count = Math.min(20, recipientIds.size());
        for (int i = 0; i < count; i++) {
            String recipientId = recipientIds.get(i);
            recipients.append((i + 1)).append(". ").append(recipientId).append("\n");
        }

        if (recipientIds.size() > 20) {
            recipients.append("\n... and ").append(recipientIds.size() - 20).append(" more");
        }

        new AlertDialog.Builder(getContext())
                .setTitle("Recipients")
                .setMessage(recipients.toString())
                .setPositiveButton("OK", null)
                .show();
    }

    private String formatType(String type) {
        if (type == null) return "Unknown";

        switch (type) {
            case "lottery_win": return "Lottery Win";
            case "lottery_loss": return "Lottery Loss";
            case "replacement_draw": return "Replacement Draw";
            case "organizer_message": return "Organizer Message";
            case "entrant_cancelled": return "Cancellation";
            default: return type.replace("_", " ");
        }
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void showEmptyState() {
        showEmptyState("No notification logs found");
    }

    private void showEmptyState(String message) {
        progressBar.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);
        emptyState.setVisibility(View.VISIBLE);
        tvEmptyMessage.setText(message);
    }

    private interface OnNameLoadedListener {
        void onNameLoaded(String name);
    }
}

