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

    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

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