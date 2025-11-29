package com.example.pickme.ui.profile;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pickme.MainActivity;
import com.example.pickme.R;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.FirebaseManager;
import com.google.android.material.textfield.TextInputEditText;

import de.hdodenhof.circleimageview.CircleImageView;
/**
 * THIS JAVADOC WAS ASSISTED BY AI
 * An activity for first-time user profile creation.
 * <p>
 * This screen allows a new user to set up their profile by providing a name,
 * email, and a profile picture. It handles user input validation, image selection
 * from the device, and communicates with {@link ProfileRepository} to persist the new profile data.
 * Users also have the option to skip profile creation. The back button is disabled
 * to ensure the setup flow is completed or explicitly skipped.
 * Covers user stories that utilize the profile creation
 * US: 01.02.02
 *
 */
public class CreateProfileActivity extends AppCompatActivity {

    // UI
    private CircleImageView profileImage;
    private Button btnUploadPhoto, btnCreateProfile, btnSkip;
    private TextInputEditText etName, etEmail;
    private ProgressBar progressBar;

    // Data
    private ProfileRepository profileRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private String userId;                 // device/installation ID
    private Uri selectedImageUri;

    private ActivityResultLauncher<String> imagePickerLauncher;
    /**
     * Initializes the activity, its views, services, and listeners.
     * The action buttons are disabled until a unique device ID is successfully retrieved.
     *
     * @param savedInstanceState If the activity is being re-initialized after
     *                           previously being shut down then this Bundle contains the data it most
     *                           recently supplied in onSaveInstanceState(Bundle). Otherwise it is null.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_profile);
        if (getSupportActionBar() != null) getSupportActionBar().hide();

        initializeViews();
        initializeServices();
        setupImagePicker();
        setupListeners();
    }
    /**
     * Binds the layout's UI components to the class variables.
     * Initially disables the main action buttons to prevent use before services are ready.
     */
    private void initializeViews() {
        profileImage     = findViewById(R.id.profileImage);
        btnUploadPhoto   = findViewById(R.id.btnUploadPhoto);
        etName           = findViewById(R.id.etName);
        etEmail          = findViewById(R.id.etEmail);
        btnCreateProfile = findViewById(R.id.btnCreateProfile);
        btnSkip          = findViewById(R.id.btnSkip);
        progressBar      = findViewById(R.id.progressBar);

        // Disable actions until deviceId is ready
        btnCreateProfile.setEnabled(false);
        btnSkip.setEnabled(false);
    }
    /**
     * Initializes repository and authentication services.
     * It fetches a unique device ID asynchronously and, upon success, generates a
     * suggested username and enables the UI for user interaction.
     */
    private void initializeServices() {
        profileRepository   = new ProfileRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);

        deviceAuthenticator.getDeviceId(deviceId -> {
            userId = deviceId;
            String suggestedName = "User" + deviceId.substring(0, Math.min(8, deviceId.length()));
            etName.setText(suggestedName);
            etName.setSelection(suggestedName.length());

            // Now the user can proceed
            btnCreateProfile.setEnabled(true);
            btnSkip.setEnabled(true);
        });
    }
    /**
     * Sets up the {@link ActivityResultLauncher} for picking an image from the device's storage.
     * When an image is selected, its URI is stored and the image is displayed
     * in the CircleImageView using Glide.
     */
    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        Glide.with(this)
                                .load(uri)
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .into(profileImage);
                    }
                }
        );
    }
    /**
     * Validates user input and creates a new profile in Firestore.
     * It ensures a device ID has been obtained and the name field is not empty.
     * It constructs a {@link Profile} object and uses {@link ProfileRepository} to save it.
     * On success, it navigates to the MainActivity. On failure, it shows an error message.
     */
    private void setupListeners() {
        btnUploadPhoto.setOnClickListener(v -> openImagePicker());
        btnCreateProfile.setOnClickListener(v -> createProfile());
        btnSkip.setOnClickListener(v -> skipProfileCreation());
    }

    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    /** Create profile with name/email (no passwords). (UPDATED) */
    private void createProfile() {
        if (userId == null) {
            Toast.makeText(this, "Initializing device… try again in a moment.", Toast.LENGTH_SHORT).show();
            return;
        }

        String name  = etName.getText()  != null ? etName.getText().toString().trim()  : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        if (TextUtils.isEmpty(name)) {
            etName.setError(getString(R.string.error_name_required));
            etName.requestFocus();
            return;
        }
        if (!TextUtils.isEmpty(email) && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_email_invalid));
            etEmail.requestFocus();
            return;
        }

        Profile profile = new Profile(userId, name, email);
        profile.setNotificationEnabled(true);
        profile.setRole(Profile.ROLE_ENTRANT);

        // TODO: If you upload selectedImageUri to Storage, set profile.setProfileImageUrl(url)

        showLoading(true);
        profileRepository.createProfile(profile,
                uid -> {
                    showLoading(false);
                    deviceAuthenticator.updateCachedProfile(profile);
                    // Store FCM token now that identity exists
                    FirebaseManager.refreshAndStoreFcmToken();
                    Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
                    navigateToMain();
                },
                e -> {
                    showLoading(false);
                    Toast.makeText(this,
                            getString(R.string.error_profile_save_failed) + ": " +
                                    (e != null ? e.getMessage() : "Unknown error"),
                            Toast.LENGTH_SHORT).show();
                });
    }

    /** Skip for now: DO NOT create a Firestore profile. Just go back to Main. */
    private void skipProfileCreation() {
        if (userId == null) {
            Toast.makeText(this, "Initializing device… try again in a moment.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Make sure nothing is cached as a "profile" yet
        deviceAuthenticator.updateCachedProfile(null);

        // Do not write anything to Firestore here.
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
    /**
     * Navigates to the {@link MainActivity} and clears the activity stack.
     * This prevents the user from returning to the profile creation screen.
     */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
    /**
     * Overrides the default back button behavior to prevent the user from
     * navigating away from the first-time profile setup screen.
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnCreateProfile.setEnabled(!show);
        btnSkip.setEnabled(!show);
        btnUploadPhoto.setEnabled(!show);
        etName.setEnabled(!show);
        etEmail.setEnabled(!show);
    }

    @Override
    @SuppressWarnings("MissingSuperCall")
    public void onBackPressed() {
        // Block back during first-time setup
    }
}