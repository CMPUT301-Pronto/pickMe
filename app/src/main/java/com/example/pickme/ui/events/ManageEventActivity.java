package com.example.pickme.ui.events;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ImageRepository;
import com.example.pickme.services.LotteryService;
import com.example.pickme.services.NotificationService;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.example.pickme.utils.CsvExporter;
import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * ManageEventActivity - Comprehensive event management for organizers
 *
 * Features:
 * - View event details and statistics
 * - 4 tabs: Waiting List, Selected, Confirmed, Cancelled
 * - Execute lottery draw with winner selection
 * - Send notifications to entrant groups
 * - Update event poster
 * - Edit registration dates before lottery is drawn
 * - Export entrant lists to CSV
 * - Cancel entrants (triggers replacement draw)
 * - View entrants on map (if geolocation enabled)
 *
 * Related User Stories: US 02.02.01-03, US 02.04.02, US 02.05.01-03,
 *                       US 02.06.01-05, US 02.07.01-03
 */
public class ManageEventActivity extends AppCompatActivity {

    public static final String EXTRA_EVENT_ID = "event_id";
    private static final String TAG = "ManageEventActivity";

    // UI Components
    private TextView tvEventName;
    private TextView tvEventDate;
    private TextView tvEventStatus;
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private FloatingActionButton fabMenu;

    // Data
    private EventRepository eventRepository;
    private ImageRepository imageRepository;
    private LotteryService lotteryService;
    private NotificationService notificationService;
    private CsvExporter csvExporter;
    private Event currentEvent;
    private String eventId;

    // Fragments
    private WaitingListFragment waitingListFragment;
    private SelectedEntrantsFragment selectedFragment;
    private ConfirmedEntrantsFragment confirmedFragment;
    private CancelledEntrantsFragment cancelledFragment;

    // Image picker for poster update
    private ActivityResultLauncher<String> imagePickerLauncher;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_event);

        // Get event ID from intent
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null) {
            Toast.makeText(this, "Event ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize components
        initializeViews();
        initializeData();
        setupImagePicker();
        setupTabs();
        setupFAB();

        // Load event details
        loadEvent();
    }

    private void initializeViews() {
        tvEventName = findViewById(R.id.tvEventName);
        tvEventDate = findViewById(R.id.tvEventDate);
        tvEventStatus = findViewById(R.id.tvEventStatus);
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
        fabMenu = findViewById(R.id.fabMenu);
    }

    private void initializeData() {
        eventRepository = new EventRepository();
        imageRepository = new ImageRepository();
        lotteryService = LotteryService.getInstance();
        notificationService = NotificationService.getInstance();
        csvExporter = new CsvExporter(this);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        updateEventPoster(uri);
                    }
                });
    }

    private void setupTabs() {
        // Create fragments
        waitingListFragment = WaitingListFragment.newInstance(eventId);
        selectedFragment = SelectedEntrantsFragment.newInstance(eventId);
        confirmedFragment = ConfirmedEntrantsFragment.newInstance(eventId);
        cancelledFragment = CancelledEntrantsFragment.newInstance(eventId);

        // Setup ViewPager2
        viewPager.setAdapter(new FragmentStateAdapter(this) {
            @NonNull
            @Override
            public Fragment createFragment(int position) {
                switch (position) {
                    case 0:
                        return waitingListFragment;
                    case 1:
                        return selectedFragment;
                    case 2:
                        return confirmedFragment;
                    case 3:
                        return cancelledFragment;
                    default:
                        return waitingListFragment;
                }
            }

            @Override
            public int getItemCount() {
                return 4;
            }
        });

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            switch (position) {
                case 0:
                    tab.setText(R.string.tab_waiting_list);
                    break;
                case 1:
                    tab.setText(R.string.tab_selected);
                    break;
                case 2:
                    tab.setText(R.string.tab_confirmed);
                    break;
                case 3:
                    tab.setText(R.string.tab_cancelled);
                    break;
            }
        }).attach();
    }

    private void setupFAB() {
        fabMenu.setOnClickListener(v -> showActionMenu());
    }

    private void loadEvent() {
        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                currentEvent = event;
                displayEventDetails(event);
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(ManageEventActivity.this,
                        "Failed to load event", Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    private void displayEventDetails(Event event) {
        tvEventName.setText(event.getName());

        // Format date and location
        String dateLocation = "";
        if (event.getEventDates() != null && !event.getEventDates().isEmpty()) {
            dateLocation = dateFormat.format(new Date(event.getEventDates().get(0)));
        }
        if (event.getLocation() != null) {
            dateLocation += " • " + event.getLocation();
        }
        tvEventDate.setText(dateLocation);

        // Status
        tvEventStatus.setText(event.getStatusEnum().name());
    }

    private void showActionMenu() {
        String[] actions = {
                "Execute Lottery Draw",
                "Send Notification",
                "Update Poster",
                "Export Lists",
                "Edit Registration Dates"
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.manage_actions)
                .setItems(actions, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            showLotteryDrawDialog();
                            break;
                        case 1:
                            showSendNotificationDialog();
                            break;
                        case 2:
                            imagePickerLauncher.launch("image/*");
                            break;
                        case 3:
                            showExportDialog();
                            break;
                        case 4:
                            showEditRegistrationDatesDialog();
                            break;

                    }
                })
                .show();
    }

    private void showLotteryDrawDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_lottery_draw, null);
        EditText etNumberOfWinners = dialogView.findViewById(R.id.etNumberOfWinners);

        new AlertDialog.Builder(this)
                .setTitle(R.string.execute_lottery_title)
                .setView(dialogView)
                .setPositiveButton(R.string.execute_lottery_button, (dialog, which) -> {
                    String input = etNumberOfWinners.getText().toString().trim();
                    if (input.isEmpty()) {
                        Toast.makeText(this, R.string.lottery_invalid_number, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        int numberOfWinners = Integer.parseInt(input);
                        executeLottery(numberOfWinners);
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, R.string.lottery_invalid_number, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void executeLottery(int numberOfWinners) {
        ProgressBar progressBar = new ProgressBar(this);
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.lottery_executing)
                .setView(progressBar)
                .setCancelable(false)
                .create();
        progressDialog.show();

        lotteryService.executeLotteryDraw(eventId, numberOfWinners,
                new LotteryService.OnLotteryCompleteListener() {
                    @Override
                    public void onLotteryComplete(LotteryService.LotteryResult result) {
                        progressDialog.dismiss();
                        Toast.makeText(ManageEventActivity.this,
                                getString(R.string.lottery_success, result.winners.size()),
                                Toast.LENGTH_LONG).show();
                        refreshFragments();
                    }

                    @Override
                    public void onError(Exception e) {
                        progressDialog.dismiss();
                        Toast.makeText(ManageEventActivity.this,
                                e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showSendNotificationDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_send_notification, null);
        EditText etMessage = dialogView.findViewById(R.id.etNotificationMessage);
        Spinner spinnerRecipients = dialogView.findViewById(R.id.spinnerRecipients);

        // Setup spinner
        String[] recipients = {
                getString(R.string.recipients_all),
                getString(R.string.recipients_waiting),
                getString(R.string.recipients_selected),
                getString(R.string.recipients_confirmed)
        };
        spinnerRecipients.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, recipients));

        new AlertDialog.Builder(this)
                .setTitle(R.string.send_notification_title)
                .setView(dialogView)
                .setPositiveButton(R.string.send_notification_button, (dialog, which) -> {
                    String message = etMessage.getText().toString().trim();
                    if (message.isEmpty()) {
                        Toast.makeText(this, R.string.notification_message_required,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    int recipientGroup = spinnerRecipients.getSelectedItemPosition();
                    sendNotification(message, recipientGroup);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void sendNotification(String message, int recipientGroup) {
        Toast.makeText(this, R.string.notification_sending, Toast.LENGTH_SHORT).show();
        // Notification sending implementation would go here
        // For now, just show success message
        Toast.makeText(this, R.string.notification_sent, Toast.LENGTH_SHORT).show();
    }

    private void showExportDialog() {
        String[] options = {
                getString(R.string.export_waiting_list),
                getString(R.string.export_selected_list),
                getString(R.string.export_confirmed_list)
        };

        new AlertDialog.Builder(this)
                .setTitle(R.string.export_csv_title)
                .setItems(options, (dialog, which) -> {
                    exportList(which);
                })
                .show();
    }

    private void exportList(int listType) {
        ProgressBar progressBar = new ProgressBar(this);
        AlertDialog progressDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.exporting)
                .setView(progressBar)
                .setCancelable(false)
                .create();
        progressDialog.show();

        // Determine which list to export
        String subcollection;
        String listTypeName;

        switch (listType) {
            case 0: // Waiting list
                subcollection = "waitingList";
                listTypeName = "waiting";
                break;
            case 1: // Selected list
                subcollection = "responsePendingList";
                listTypeName = "selected";
                break;
            case 2: // Confirmed list
                subcollection = "inEventList";
                listTypeName = "confirmed";
                break;
            default:
                progressDialog.dismiss();
                return;
        }

        // Fetch entrant IDs from the subcollection
        eventRepository.getEntrantIdsFromSubcollection(eventId, subcollection,
                new EventRepository.OnEntrantIdsLoadedListener() {
                    @Override
                    public void onEntrantIdsLoaded(List<String> entrantIds) {
                        if (entrantIds.isEmpty()) {
                            progressDialog.dismiss();
                            Toast.makeText(ManageEventActivity.this,
                                    "No entrants to export", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Export to CSV
                        csvExporter.exportEntrantList(currentEvent, entrantIds, listTypeName,
                                new CsvExporter.OnExportCompleteListener() {
                                    @Override
                                    public void onExportComplete(Uri fileUri, String filePath) {
                                        progressDialog.dismiss();
                                        showExportSuccessDialog(fileUri, filePath);
                                    }

                                    @Override
                                    public void onError(Exception e) {
                                        progressDialog.dismiss();
                                        Toast.makeText(ManageEventActivity.this,
                                                "Export failed: " + e.getMessage(),
                                                Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }

                    @Override
                    public void onError(Exception e) {
                        progressDialog.dismiss();
                        Toast.makeText(ManageEventActivity.this,
                                "Failed to fetch entrants: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void showExportSuccessDialog(Uri fileUri, String filePath) {
        new AlertDialog.Builder(this)
                .setTitle("Export Successful")
                .setMessage("CSV file saved to: " + filePath)
                .setPositiveButton("Share", (dialog, which) -> {
                    CsvExporter.shareCSV(fileUri, this);
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void updateEventPoster(Uri imageUri) {
        Toast.makeText(this, "Updating poster...", Toast.LENGTH_SHORT).show();

        imageRepository.uploadEventPoster(eventId, imageUri, currentEvent.getOrganizerId(),
                new ImageRepository.OnUploadCompleteListener() {
                    @Override
                    public void onUploadComplete(String downloadUrl, String posterId) {
                        Toast.makeText(ManageEventActivity.this,
                                R.string.poster_updated, Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(Exception e) {
                        Toast.makeText(ManageEventActivity.this,
                                R.string.poster_update_failed, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void refreshFragments() {
        if (waitingListFragment != null) {
            waitingListFragment.refresh();
        }
        if (selectedFragment != null) {
            selectedFragment.refresh();
        }
        if (confirmedFragment != null) {
            confirmedFragment.refresh();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    // Allows the organizer to edit registration dates before lottery is drawn.


    private void showEditRegistrationDatesDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_edit_registration_dates, null);
        TextInputEditText etRegStart = dialogView.findViewById(R.id.etEditRegStartDate);
        TextInputEditText etRegEnd = dialogView.findViewById(R.id.etEditRegEndDate);

        // Pre-fill current dates
        SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        long currentStart = currentEvent.getRegistrationStartDate();
        long currentEnd = currentEvent.getRegistrationEndDate();
        long eventDate = !currentEvent.getEventDates().isEmpty() ? currentEvent.getEventDates().get(0) : 0;

        final long[] newStart = {currentStart};
        final long[] newEnd = {currentEnd};

        etRegStart.setText(dateFormat.format(new Date(currentStart)));
        etRegEnd.setText(dateFormat.format(new Date(currentEnd)));

        // MaterialDatePicker for Start Date
        etRegStart.setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Registration Start Date")
                    .setSelection(newStart[0])
                    .build();
            picker.show(getSupportFragmentManager(), "reg_start_picker");
            picker.addOnPositiveButtonClickListener(selection -> {
                newStart[0] = selection;
                etRegStart.setText(dateFormat.format(new Date(selection)));
            });
        });

        // MaterialDatePicker for End Date
        etRegEnd.setOnClickListener(v -> {
            MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Registration End Date")
                    .setSelection(newEnd[0])
                    .build();
            picker.show(getSupportFragmentManager(), "reg_end_picker");
            picker.addOnPositiveButtonClickListener(selection -> {
                newEnd[0] = selection;
                etRegEnd.setText(dateFormat.format(new Date(selection)));
            });
        });

        // Build dialog with system default buttons (no custom XML buttons)
        new AlertDialog.Builder(this)
                .setTitle("Edit Registration Dates")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    // Validate
                    if (newStart[0] == 0 || newEnd[0] == 0) {
                        Toast.makeText(this, "Please select both start and end dates.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newEnd[0] <= newStart[0]) {
                        Toast.makeText(this, R.string.error_reg_end_before_start, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (eventDate <= newEnd[0]) {
                        Toast.makeText(this, R.string.error_event_date_before_reg, Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Show centered transparent progress bar overlay
                    ProgressBar pb = new ProgressBar(this);
                    AlertDialog progressDialog = new AlertDialog.Builder(this)
                            .setView(pb)
                            .setCancelable(false)
                            .create();
                    if (progressDialog.getWindow() != null) {
                        progressDialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
                    }
                    progressDialog.show();

                    // Update Firestore
                    eventRepository.updateRegistrationDates(
                            currentEvent.getEventId(), newStart[0], newEnd[0],
                            aVoid -> {
                                progressDialog.dismiss();
                                currentEvent.setRegistrationStartDate(newStart[0]);
                                currentEvent.setRegistrationEndDate(newEnd[0]);
                                Toast.makeText(this, "Registration dates updated successfully!", Toast.LENGTH_SHORT).show();
                                displayEventDetails(currentEvent);
                            },
                            e -> {
                                progressDialog.dismiss();
                                Toast.makeText(this, "Failed to update dates: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            }
                    );
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }


}

