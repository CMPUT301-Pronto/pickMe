package com.example.pickme.ui.admin;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.models.NotificationLog;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ProfileRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AdminNotificationLogsActivity - View and manage notification logs
 *
 * Features:
 * - View all notifications sent through the system
 * - Filter by date range
 * - Filter by organizer
 * - Search by event name or message content
 * - View full notification details
 * - Export logs to CSV
 *
 * Related User Stories: US 03.08.01 - Admin review notification logs
 */
public class AdminNotificationLogsActivity extends AppCompatActivity implements NotificationLogAdapter.OnLogClickListener {

    private static final String TAG = "AdminNotificationLogs";
    private static final int LOGS_PER_PAGE = 50;

    // UI Components
    private MaterialToolbar toolbar;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;
    private TextView tvEmptyMessage;
    private TextInputEditText etSearch;

    // Data
    private NotificationLogAdapter adapter;
    private FirebaseFirestore db;
    private EventRepository eventRepository;
    private ProfileRepository profileRepository;
    private List<NotificationLog> allLogs;
    private List<NotificationLog> filteredLogs;
    private Map<String, String> eventNameCache;
    private Map<String, String> organizerNameCache;
    private boolean isLoading = false;
    private DocumentSnapshot lastDocument;
    private SimpleDateFormat dateFormat;

    // Filter state
    private Long filterStartDate = null;
    private Long filterEndDate = null;
    private String filterOrganizerId = null;
    private String searchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_notification_logs);

        initializeViews();
        initializeData();
        setupToolbar();
        setupSearch();
        loadNotificationLogs();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        recyclerView = findViewById(R.id.recyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyState = findViewById(R.id.emptyState);
        tvEmptyMessage = findViewById(R.id.tvEmptyMessage);
        etSearch = findViewById(R.id.etSearch);

        // Setup RecyclerView
        adapter = new NotificationLogAdapter(this, eventNameCache, organizerNameCache);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Setup pagination
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
        filteredLogs = new ArrayList<>();
        eventNameCache = new HashMap<>();
        organizerNameCache = new HashMap<>();
        dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Notification Logs");
        }
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString().toLowerCase().trim();
                applyFilters();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_notification_logs, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == android.R.id.home) {
            finish();
            return true;
        } else if (id == R.id.action_filter_date) {
            showDateRangeFilter();
            return true;
        } else if (id == R.id.action_filter_organizer) {
            showOrganizerFilter();
            return true;
        } else if (id == R.id.action_clear_filters) {
            clearFilters();
            return true;
        } else if (id == R.id.action_export_csv) {
            exportToCSV();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void loadNotificationLogs() {
        if (isLoading) return;

        isLoading = true;
        showLoading(true);

        Query query = db.collection("notification_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING);

        // Apply date filter if set
        if (filterStartDate != null) {
            query = query.whereGreaterThanOrEqualTo("timestamp", filterStartDate);
        }
        if (filterEndDate != null) {
            query = query.whereLessThanOrEqualTo("timestamp", filterEndDate);
        }

        // Apply organizer filter if set
        if (filterOrganizerId != null) {
            query = query.whereEqualTo("senderId", filterOrganizerId);
        }

        query.limit(LOGS_PER_PAGE)
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
                            // Pre-load event and organizer names
                            loadEventName(log.getEventId());
                            loadOrganizerName(log.getSenderId());
                        }
                    }

                    allLogs = logs;
                    applyFilters();

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

        Query query = db.collection("notification_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING);

        if (filterStartDate != null) {
            query = query.whereGreaterThanOrEqualTo("timestamp", filterStartDate);
        }
        if (filterEndDate != null) {
            query = query.whereLessThanOrEqualTo("timestamp", filterEndDate);
        }
        if (filterOrganizerId != null) {
            query = query.whereEqualTo("senderId", filterOrganizerId);
        }

        query.startAfter(lastDocument)
                .limit(LOGS_PER_PAGE)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        List<NotificationLog> logs = new ArrayList<>();
                        for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                            NotificationLog log = doc.toObject(NotificationLog.class);
                            if (log != null) {
                                logs.add(log);
                                loadEventName(log.getEventId());
                                loadOrganizerName(log.getSenderId());
                            }
                        }

                        allLogs.addAll(logs);
                        applyFilters();
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

    private void loadEventName(String eventId) {
        if (eventId == null || eventNameCache.containsKey(eventId)) return;

        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                eventNameCache.put(eventId, event.getName());
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Exception e) {
                eventNameCache.put(eventId, "Unknown Event");
            }
        });
    }

    private void loadOrganizerName(String organizerId) {
        if (organizerId == null || organizerNameCache.containsKey(organizerId)) return;

        profileRepository.getProfile(organizerId, new ProfileRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(Profile profile) {
                organizerNameCache.put(organizerId, profile.getName());
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Exception e) {
                organizerNameCache.put(organizerId, "System");
            }
        });
    }

    private void applyFilters() {
        filteredLogs.clear();

        for (NotificationLog log : allLogs) {
            if (matchesSearchQuery(log)) {
                filteredLogs.add(log);
            }
        }

        adapter.setLogs(filteredLogs);

        if (filteredLogs.isEmpty() && !allLogs.isEmpty()) {
            showEmptyState("No logs match your search");
        } else if (filteredLogs.isEmpty()) {
            showEmptyState();
        } else {
            emptyState.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private boolean matchesSearchQuery(NotificationLog log) {
        if (searchQuery.isEmpty()) return true;

        // Search in message content
        if (log.getMessageContent() != null &&
            log.getMessageContent().toLowerCase().contains(searchQuery)) {
            return true;
        }

        // Search in event name
        String eventName = eventNameCache.get(log.getEventId());
        if (eventName != null && eventName.toLowerCase().contains(searchQuery)) {
            return true;
        }

        // Search in organizer name
        String organizerName = organizerNameCache.get(log.getSenderId());
        if (organizerName != null && organizerName.toLowerCase().contains(searchQuery)) {
            return true;
        }

        return false;
    }

    private void showDateRangeFilter() {
        Calendar calendar = Calendar.getInstance();

        // Show start date picker
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            Calendar startCal = Calendar.getInstance();
            startCal.set(year, month, dayOfMonth, 0, 0, 0);
            filterStartDate = startCal.getTimeInMillis();

            // Show end date picker
            new DatePickerDialog(this, (view2, year2, month2, dayOfMonth2) -> {
                Calendar endCal = Calendar.getInstance();
                endCal.set(year2, month2, dayOfMonth2, 23, 59, 59);
                filterEndDate = endCal.getTimeInMillis();

                // Reload with filters
                allLogs.clear();
                lastDocument = null;
                loadNotificationLogs();

                String dateRange = dateFormat.format(new Date(filterStartDate)) +
                                 " to " + dateFormat.format(new Date(filterEndDate));
                Toast.makeText(this, "Filtered by: " + dateRange, Toast.LENGTH_SHORT).show();

            }, year, month, dayOfMonth).show();

        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
           calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void showOrganizerFilter() {
        // Load all unique organizers from current logs
        Map<String, String> organizers = new HashMap<>();
        for (NotificationLog log : allLogs) {
            String senderId = log.getSenderId();
            if (senderId != null && !organizers.containsKey(senderId)) {
                String name = organizerNameCache.getOrDefault(senderId, senderId);
                organizers.put(senderId, name);
            }
        }

        if (organizers.isEmpty()) {
            Toast.makeText(this, "No organizers found in current logs", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] organizerNames = organizers.values().toArray(new String[0]);
        String[] organizerIds = organizers.keySet().toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle("Filter by Organizer")
                .setItems(organizerNames, (dialog, which) -> {
                    filterOrganizerId = organizerIds[which];

                    // Reload with filter
                    allLogs.clear();
                    lastDocument = null;
                    loadNotificationLogs();

                    Toast.makeText(this, "Filtered by: " + organizerNames[which],
                                 Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearFilters() {
        filterStartDate = null;
        filterEndDate = null;
        filterOrganizerId = null;
        searchQuery = "";
        etSearch.setText("");

        allLogs.clear();
        lastDocument = null;
        loadNotificationLogs();

        Toast.makeText(this, "Filters cleared", Toast.LENGTH_SHORT).show();
    }

    private void exportToCSV() {
        if (filteredLogs.isEmpty()) {
            Toast.makeText(this, "No logs to export", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Export Logs")
                .setMessage("Export " + filteredLogs.size() + " logs to CSV?")
                .setPositiveButton("Export", (dialog, which) -> {
                    performCSVExport();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performCSVExport() {
        try {
            // Create file in Downloads directory
            File downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
            File csvFile = new File(downloadsDir, "notification_logs_" + timestamp + ".csv");

            FileWriter writer = new FileWriter(csvFile);

            // Write header
            writer.append("Timestamp,Type,Event,Organizer,Recipients,Message\n");

            // Write data
            SimpleDateFormat csvDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            for (NotificationLog log : filteredLogs) {
                writer.append("\"").append(csvDateFormat.format(new Date(log.getTimestamp()))).append("\",");
                writer.append("\"").append(log.getNotificationType() != null ? log.getNotificationType() : "").append("\",");
                writer.append("\"").append(eventNameCache.getOrDefault(log.getEventId(), "Unknown")).append("\",");
                writer.append("\"").append(organizerNameCache.getOrDefault(log.getSenderId(), "System")).append("\",");
                writer.append(String.valueOf(log.getRecipientIds() != null ? log.getRecipientIds().size() : 0)).append(",");

                String message = log.getMessageContent() != null ? log.getMessageContent().replace("\"", "\"\"") : "";
                writer.append("\"").append(message).append("\"\n");
            }

            writer.close();

            Toast.makeText(this, "Exported to " + csvFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            Log.d(TAG, "CSV exported to: " + csvFile.getAbsolutePath());

        } catch (IOException e) {
            Log.e(TAG, "Failed to export CSV", e);
            Toast.makeText(this, "Failed to export: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onLogClick(NotificationLog log) {
        showLogDetailsDialog(log);
    }

    private void showLogDetailsDialog(NotificationLog log) {
        StringBuilder details = new StringBuilder();
        details.append("Timestamp: ").append(dateFormat.format(new Date(log.getTimestamp()))).append("\n\n");
        details.append("Type: ").append(formatType(log.getNotificationType())).append("\n\n");

        String eventName = eventNameCache.getOrDefault(log.getEventId(), "Loading...");
        details.append("Event: ").append(eventName).append("\n\n");

        String organizerName = organizerNameCache.getOrDefault(log.getSenderId(), "System");
        details.append("Organizer: ").append(organizerName).append("\n\n");

        details.append("Recipients: ").append(log.getRecipientIds() != null ? log.getRecipientIds().size() : 0).append("\n\n");
        details.append("Message:\n").append(log.getMessageContent());

        new AlertDialog.Builder(this)
                .setTitle("Notification Details")
                .setMessage(details.toString())
                .setPositiveButton("View Recipients", (dialog, which) -> {
                    showRecipientsDialog(log.getRecipientIds());
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void showRecipientsDialog(List<String> recipientIds) {
        if (recipientIds == null || recipientIds.isEmpty()) {
            Toast.makeText(this, "No recipients", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder recipients = new StringBuilder();
        recipients.append("Total Recipients: ").append(recipientIds.size()).append("\n\n");

        // Load recipient names
        int count = Math.min(50, recipientIds.size());
        for (int i = 0; i < count; i++) {
            String recipientId = recipientIds.get(i);
            recipients.append((i + 1)).append(". ").append(recipientId).append("\n");
        }

        if (recipientIds.size() > 50) {
            recipients.append("\n... and ").append(recipientIds.size() - 50).append(" more");
        }

        new AlertDialog.Builder(this)
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
}

