package com.example.pickme.ui.events;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.models.EventStatus;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ImageRepository;
import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.QRCodeGenerator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * CreateEventActivity - Allows organizers to create new events
 *
 * Features:
 * - Input event details (name, description, location, dates, capacity, price)
 * - Upload event poster image
 * - Set registration period
 * - Configure waiting list limit (optional)
 * - Enable geolocation requirement
 * - Auto-generate QR code on publish
 * - Display QR code with share/download options
 *
 * Validation:
 * - All required fields must be filled
 * - Dates must be logical (reg end > reg start, event date > reg end)
 * - Capacity must be > 0
 * - Waiting list limit must be > capacity (if specified)
 * - Only users with Organizer role can access
 *
 * Related User Stories: US 02.01.01, US 02.01.04, US 02.03.01, US 02.04.01, US 02.02.03
 */
public class CreateEventActivity extends AppCompatActivity {

    private static final String TAG = "CreateEventActivity";

    // UI Components
    private ImageView ivEventPoster;
    private Button btnUploadPoster;
    private TextInputEditText etEventName;
    private TextInputEditText etEventDescription;
    private TextInputEditText etEventLocation;
    private TextInputEditText etEventDate;
    private TextInputEditText etRegStartDate;
    private TextInputEditText etRegEndDate;
    private TextInputEditText etCapacity;
    private TextInputEditText etPrice;
    private TextInputEditText etWaitingListLimit;
    private SwitchMaterial switchGeolocation;
    private Button btnPublishEvent;
    private ProgressBar progressBar;

    // Data
    private EventRepository eventRepository;
    private ImageRepository imageRepository;
    private ProfileRepository profileRepository;
    private QRCodeGenerator qrCodeGenerator;
    private String currentUserId;
    private Uri selectedPosterUri;

    // Date storage
    private long eventDateTimestamp = 0;
    private long regStartTimestamp = 0;
    private long regEndTimestamp = 0;

    // Date formatter
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    // Image picker
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_event);

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize components
        initializeViews();
        initializeData();
        setupImagePicker();
        checkOrganizerRole();
        setupClickListeners();
    }

    /**
     * Initialize view references
     */
    private void initializeViews() {
        ivEventPoster = findViewById(R.id.ivEventPoster);
        btnUploadPoster = findViewById(R.id.btnUploadPoster);
        etEventName = findViewById(R.id.etEventName);
        etEventDescription = findViewById(R.id.etEventDescription);
        etEventLocation = findViewById(R.id.etEventLocation);
        etEventDate = findViewById(R.id.etEventDate);
        etRegStartDate = findViewById(R.id.etRegStartDate);
        etRegEndDate = findViewById(R.id.etRegEndDate);
        etCapacity = findViewById(R.id.etCapacity);
        etPrice = findViewById(R.id.etPrice);
        etWaitingListLimit = findViewById(R.id.etWaitingListLimit);
        switchGeolocation = findViewById(R.id.switchGeolocation);
        btnPublishEvent = findViewById(R.id.btnPublishEvent);
        progressBar = findViewById(R.id.progressBar);
    }

    /**
     * Initialize data components
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        imageRepository = new ImageRepository();
        profileRepository = new ProfileRepository();
        qrCodeGenerator = new QRCodeGenerator();
        DeviceAuthenticator deviceAuthenticator = DeviceAuthenticator.getInstance(this);

        // Get device ID asynchronously
        deviceAuthenticator.getDeviceId(deviceId -> currentUserId = deviceId);
    }

    /**
     * Setup image picker launcher
     */
    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedPosterUri = uri;
                        // Display selected image
                        Glide.with(this)
                                .load(uri)
                                .centerCrop()
                                .into(ivEventPoster);
                    }
                });
    }

    /**
     * Check if current user has Organizer role
     * If not, show error and finish activity
     */
    private void checkOrganizerRole() {
        showLoading(true);
        profileRepository.getProfile(currentUserId, new ProfileRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(Profile profile) {
                showLoading(false);
                if (profile == null || !Profile.ROLE_ORGANIZER.equals(profile.getRole())) {
                    // User is not an organizer
                    showAccessDeniedDialog();
                }
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(CreateEventActivity.this,
                        R.string.error_occurred, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * Show access denied dialog and close activity
     */
    private void showAccessDeniedDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.access_denied_title)
                .setMessage(R.string.organizer_only_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    /**
     * Setup click listeners for all interactive elements
     */
    private void setupClickListeners() {
        btnUploadPoster.setOnClickListener(v -> openImagePicker());
        etEventDate.setOnClickListener(v -> showEventDatePicker());
        etRegStartDate.setOnClickListener(v -> showRegStartDatePicker());
        etRegEndDate.setOnClickListener(v -> showRegEndDatePicker());
        btnPublishEvent.setOnClickListener(v -> validateAndPublishEvent());
    }

    /**
     * Open image picker
     */
    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    /**
     * Show date picker for event date
     */
    private void showEventDatePicker() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    eventDateTimestamp = calendar.getTimeInMillis();
                    etEventDate.setText(dateFormat.format(new Date(eventDateTimestamp)));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    /**
     * Show date picker for registration start date
     */
    private void showRegStartDatePicker() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    regStartTimestamp = calendar.getTimeInMillis();
                    etRegStartDate.setText(dateFormat.format(new Date(regStartTimestamp)));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    /**
     * Show date picker for registration end date
     */
    private void showRegEndDatePicker() {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> {
                    calendar.set(year, month, dayOfMonth);
                    regEndTimestamp = calendar.getTimeInMillis();
                    etRegEndDate.setText(dateFormat.format(new Date(regEndTimestamp)));
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH))
                .show();
    }

    /**
     * Validate all input fields and publish event
     */
    private void validateAndPublishEvent() {
        // Get input values
        String name = etEventName.getText() != null ? etEventName.getText().toString().trim() : "";
        String description = etEventDescription.getText() != null ? etEventDescription.getText().toString().trim() : "";
        String location = etEventLocation.getText() != null ? etEventLocation.getText().toString().trim() : "";
        String capacityStr = etCapacity.getText() != null ? etCapacity.getText().toString().trim() : "";
        String priceStr = etPrice.getText() != null ? etPrice.getText().toString().trim() : "0";
        String waitingListStr = etWaitingListLimit.getText() != null ? etWaitingListLimit.getText().toString().trim() : "";

        // Validate required fields
        if (TextUtils.isEmpty(name)) {
            etEventName.setError(getString(R.string.error_event_name_required));
            etEventName.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(description)) {
            etEventDescription.setError(getString(R.string.error_description_required));
            etEventDescription.requestFocus();
            return;
        }

        if (TextUtils.isEmpty(location)) {
            etEventLocation.setError(getString(R.string.error_location_required));
            etEventLocation.requestFocus();
            return;
        }

        if (eventDateTimestamp == 0) {
            Toast.makeText(this, R.string.error_event_date_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (regStartTimestamp == 0 || regEndTimestamp == 0) {
            Toast.makeText(this, R.string.error_reg_dates_required, Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(capacityStr)) {
            etCapacity.setError(getString(R.string.error_capacity_required));
            etCapacity.requestFocus();
            return;
        }

        // Parse and validate numeric fields
        int capacity;
        double price;
        int waitingListLimit = -1; // -1 means unlimited

        try {
            capacity = Integer.parseInt(capacityStr);
            if (capacity <= 0) {
                etCapacity.setError(getString(R.string.error_capacity_invalid));
                etCapacity.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            etCapacity.setError(getString(R.string.error_capacity_invalid));
            etCapacity.requestFocus();
            return;
        }

        try {
            price = Double.parseDouble(priceStr);
            if (price < 0) {
                etPrice.setError(getString(R.string.error_price_invalid));
                etPrice.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            etPrice.setError(getString(R.string.error_price_invalid));
            etPrice.requestFocus();
            return;
        }

        if (!TextUtils.isEmpty(waitingListStr)) {
            try {
                waitingListLimit = Integer.parseInt(waitingListStr);
                if (waitingListLimit < capacity) {
                    etWaitingListLimit.setError(getString(R.string.error_waiting_list_invalid));
                    etWaitingListLimit.requestFocus();
                    return;
                }
            } catch (NumberFormatException e) {
                etWaitingListLimit.setError(getString(R.string.error_waiting_list_invalid));
                etWaitingListLimit.requestFocus();
                return;
            }
        }

        // Validate date logic
        if (regEndTimestamp <= regStartTimestamp) {
            Toast.makeText(this, R.string.error_reg_end_before_start, Toast.LENGTH_SHORT).show();
            return;
        }

        if (eventDateTimestamp <= regEndTimestamp) {
            Toast.makeText(this, R.string.error_event_date_before_reg, Toast.LENGTH_SHORT).show();
            return;
        }

        // All validation passed - create event
        createEvent(name, description, location, capacity, price, waitingListLimit);
    }

    /**
     * Create event object and save to Firestore
     *
     * @param name Event name
     * @param description Event description
     * @param location Event location
     * @param capacity Maximum participants
     * @param price Event price
     * @param waitingListLimit Waiting list limit (-1 for unlimited)
     */
    private void createEvent(String name, String description, String location,
                            int capacity, double price, int waitingListLimit) {
        showLoading(true);

        // Create event object
        Event event = new Event();
        event.setName(name);
        event.setDescription(description);
        event.setLocation(location);
        event.setOrganizerId(currentUserId);

        // Set dates
        ArrayList<Long> eventDates = new ArrayList<>();
        eventDates.add(eventDateTimestamp);
        event.setEventDates(eventDates);
        event.setRegistrationStartDate(regStartTimestamp);
        event.setRegistrationEndDate(regEndTimestamp);

        // Set capacity and pricing
        event.setCapacity(capacity);
        event.setPrice(price);
        event.setWaitingListLimit(waitingListLimit);

        // Set geolocation requirement
        event.setGeolocationRequired(switchGeolocation.isChecked());

        // Set status to OPEN (ready for registration)
        event.setStatus(EventStatus.OPEN);

        // Save event to Firestore
        eventRepository.createEvent(event,
                eventId -> {
                    // Event created successfully
                    if (selectedPosterUri != null) {
                        // Upload poster image
                        uploadPosterImage(eventId, event);
                    } else {
                        // No poster - generate QR code directly
                        generateQRCode(eventId, event);
                    }
                },
                e -> {
                    showLoading(false);
                    Toast.makeText(this, R.string.event_creation_failed, Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Upload event poster image
     *
     * @param eventId Event ID
     * @param event Event object
     */
    private void uploadPosterImage(String eventId, Event event) {
        imageRepository.uploadEventPoster(eventId, selectedPosterUri, currentUserId,
                new ImageRepository.OnUploadCompleteListener() {
                    @Override
                    public void onUploadComplete(String downloadUrl, String posterId) {
                        // Poster uploaded - update event with poster URL
                        event.setPosterImageUrl(downloadUrl);
                        eventRepository.updateEvent(eventId,
                                java.util.Collections.singletonMap("posterImageUrl", downloadUrl),
                                id -> generateQRCode(eventId, event),
                                e -> generateQRCode(eventId, event) // Continue even if update fails
                        );
                    }

                    @Override
                    public void onError(Exception e) {
                        // Poster upload failed - continue without poster
                        generateQRCode(eventId, event);
                    }
                });
    }

    /**
     * Generate QR code for event
     *
     * @param eventId Event ID
     * @param event Event object
     */
    private void generateQRCode(String eventId, Event event) {
        qrCodeGenerator.generateQRCode(eventId, this,
                new QRCodeGenerator.OnQRGeneratedListener() {
                    @Override
                    public void onQRGenerated(Bitmap qrBitmap, String filePath, String qrCodeId) {
                        showLoading(false);
                        // Update event with QR code ID
                        if (qrCodeId != null) {
                            event.setQrCodeId(qrCodeId);
                            eventRepository.updateEvent(eventId,
                                    java.util.Collections.singletonMap("qrCodeId", qrCodeId),
                                    id -> {}, // Silent success
                                    e -> {} // Silent failure
                            );
                        }
                        // Show success dialog with QR code
                        showSuccessDialog(qrBitmap, filePath);
                    }

                    @Override
                    public void onError(Exception e) {
                        showLoading(false);
                        // Event created but QR generation failed
                        Toast.makeText(CreateEventActivity.this,
                                R.string.qr_code_generation_failed, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
    }

    /**
     * Show success dialog with QR code
     *
     * @param qrBitmap QR code bitmap
     * @param filePath File path where QR code is saved
     */
    private void showSuccessDialog(Bitmap qrBitmap, String filePath) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_event_created, null);
        ImageView ivQRCode = dialogView.findViewById(R.id.ivQRCode);
        Button btnShare = dialogView.findViewById(R.id.btnShare);
        Button btnDownload = dialogView.findViewById(R.id.btnDownload);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        ivQRCode.setImageBitmap(qrBitmap);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnShare.setOnClickListener(v -> {
            shareQRCode(filePath);
        });

        btnDownload.setOnClickListener(v -> {
            Toast.makeText(this, "QR code saved to: " + filePath, Toast.LENGTH_LONG).show();
        });

        btnClose.setOnClickListener(v -> {
            dialog.dismiss();
            finish();
        });

        dialog.show();
    }

    /**
     * Share QR code image
     *
     * @param filePath File path of QR code image
     */
    private void shareQRCode(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            Toast.makeText(this, "QR code file not found", Toast.LENGTH_SHORT).show();
            return;
        }

        Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                this,
                getPackageName() + ".fileprovider",
                file
        );

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("image/*");
        shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(shareIntent, getString(R.string.share_qr_code)));
    }

    /**
     * Show/hide loading indicator
     *
     * @param show true to show, false to hide
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnPublishEvent.setEnabled(!show);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

