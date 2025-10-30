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
import com.google.android.material.textfield.TextInputEditText;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * CreateProfileActivity - First-time profile setup
 *
 * Shown on first app launch after device authentication.
 * Collects minimal required information (name, optional email).
 *
 * Related User Stories: US 01.02.01, US 01.07.01
 */
public class CreateProfileActivity extends AppCompatActivity {

    private static final String TAG = "CreateProfileActivity";

    // UI Components
    private CircleImageView profileImage;
    private Button btnUploadPhoto;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private Button btnCreateProfile;
    private Button btnSkip;
    private ProgressBar progressBar;

    // Data
    private ProfileRepository profileRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private String userId;
    private Uri selectedImageUri;

    // Image picker
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_profile);

        // Hide action bar for cleaner first-time experience
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Initialize
        initializeViews();
        initializeServices();
        setupImagePicker();
        setupListeners();
    }

    /**
     * Initialize all view references
     */
    private void initializeViews() {
        profileImage = findViewById(R.id.profileImage);
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        btnCreateProfile = findViewById(R.id.btnCreateProfile);
        btnSkip = findViewById(R.id.btnSkip);
        progressBar = findViewById(R.id.progressBar);
    }

    /**
     * Initialize services
     */
    private void initializeServices() {
        profileRepository = new ProfileRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);

        // Get device ID
        deviceAuthenticator.getDeviceId(new DeviceAuthenticator.OnDeviceIdLoadedListener() {
            @Override
            public void onDeviceIdLoaded(String deviceId) {
                userId = deviceId;

                // Pre-fill name with suggested value
                String suggestedName = "User" + deviceId.substring(0, 8);
                etName.setText(suggestedName);
                etName.setSelection(suggestedName.length()); // Move cursor to end
            }
        });
    }

    /**
     * Setup image picker launcher
     */
    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        // Display selected image
                        Glide.with(this)
                                .load(uri)
                                .placeholder(R.drawable.ic_launcher_foreground)
                                .into(profileImage);
                    }
                }
        );
    }

    /**
     * Setup button listeners
     */
    private void setupListeners() {
        btnUploadPhoto.setOnClickListener(v -> openImagePicker());
        btnCreateProfile.setOnClickListener(v -> createProfile());
        btnSkip.setOnClickListener(v -> skipProfileCreation());
    }

    /**
     * Open image picker
     */
    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    /**
     * Validate and create profile
     */
    private void createProfile() {
        // Get input values
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";

        // Validate name (required)
        if (TextUtils.isEmpty(name)) {
            etName.setError(getString(R.string.error_name_required));
            etName.requestFocus();
            return;
        }

        // Validate email format if provided
        if (!TextUtils.isEmpty(email) && !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            etEmail.setError(getString(R.string.error_email_invalid));
            etEmail.requestFocus();
            return;
        }

        // Create profile object
        Profile profile = new Profile(userId, name, email);
        profile.setNotificationEnabled(true); // Default to enabled
        profile.setRole(Profile.ROLE_ENTRANT); // Default role

        // TODO: If image was selected, upload to Storage first and set profileImageUrl

        // Save to Firestore
        showLoading(true);
        profileRepository.createProfile(profile,
                new ProfileRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String userId) {
                        showLoading(false);
                        Toast.makeText(CreateProfileActivity.this,
                                getString(R.string.profile_saved),
                                Toast.LENGTH_SHORT).show();

                        // Update cached profile
                        deviceAuthenticator.updateCachedProfile(profile);

                        // Navigate to main activity
                        navigateToMain();
                    }
                },
                new ProfileRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showLoading(false);
                        Toast.makeText(CreateProfileActivity.this,
                                getString(R.string.error_profile_save_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Skip profile creation and use minimal default profile
     */
    private void skipProfileCreation() {
        // Create minimal profile with device ID as name
        Profile profile = new Profile(userId, "User" + userId.substring(0, 8));
        profile.setNotificationEnabled(true);
        profile.setRole(Profile.ROLE_ENTRANT);

        showLoading(true);
        profileRepository.createProfile(profile,
                new ProfileRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String userId) {
                        showLoading(false);
                        deviceAuthenticator.updateCachedProfile(profile);
                        navigateToMain();
                    }
                },
                new ProfileRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showLoading(false);
                        Toast.makeText(CreateProfileActivity.this,
                                "Error: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Navigate to main activity
     */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Show/hide loading indicator
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
        // Prevent back navigation during first-time setup
        // User must either create profile or skip
    }
}

