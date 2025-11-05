package com.example.pickme.ui.profile;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Patterns;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.pickme.R;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.Map;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import com.example.pickme.utils.BioAuthUtil;
import com.example.pickme.utils.BioPrefsUtil;
import com.example.pickme.services.DeviceAuthenticator;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * ProfileActivity - View and edit user profile
 *
 * Features:
 * - Load current user's profile
 * - Edit name, email, phone
 * - Toggle notifications
 * - Upload/change profile image
 * - View event history
 * - Delete account with confirmation
 *
 * Related User Stories: US 01.02.01, US 01.02.02, US 01.02.04
 */
public class ProfileActivity extends AppCompatActivity {

    private static final String TAG = "ProfileActivity";

    // UI Components
    private CircleImageView profileImage;
    private Button btnChangePhoto;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPhone;
    private SwitchMaterial switchNotifications;
    private Spinner spinnerRole;
    private Button btnSave;
    private Button btnViewHistory;
    private Button btnDeleteAccount;
    private ProgressBar progressBar;

    // Data
    private ProfileRepository profileRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private SwitchMaterial switchBiometric;
    private Profile currentProfile;
    private String userId;
    private Uri selectedImageUri;


    // Image picker
    private ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize
        initializeViews();
        initializeRepositories();
        setupImagePicker();
        setupListeners();
        loadProfile();
        switchBiometric = findViewById(R.id.switchBiometric);
        switchBiometric.setChecked(BioPrefsUtil.isEnabled(this));

        switchBiometric.setOnCheckedChangeListener((btn, enabled) -> {
            if (enabled) enableBiometricFlow();
            else BioPrefsUtil.disable(getApplicationContext());
        });
    }

    /**
     * Initialize all view references
     */
    private void initializeViews() {
        profileImage = findViewById(R.id.profileImage);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        etPhone = findViewById(R.id.etPhone);
        switchNotifications = findViewById(R.id.switchNotifications);
        spinnerRole = findViewById(R.id.spinnerRole);
        btnSave = findViewById(R.id.btnSave);
        btnViewHistory = findViewById(R.id.btnViewHistory);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        progressBar = findViewById(R.id.progressBar);

        // Setup role spinner
        String[] roles = {
                getString(R.string.role_entrant),
                getString(R.string.role_organizer),
                getString(R.string.role_admin)
        };
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, roles);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);
    }

    /**
     * Initialize repositories and services
     */
    private void initializeRepositories() {
        profileRepository = new ProfileRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);

        // Get current user ID
        userId = deviceAuthenticator.getStoredUserId();
        if (userId == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            finish();
        }
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

                        // TODO: Upload image to Firebase Storage via ImageRepository
                        Toast.makeText(this, "Image selected. Save profile to upload.", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Setup button listeners
     */
    private void setupListeners() {
        btnChangePhoto.setOnClickListener(v -> openImagePicker());
        btnSave.setOnClickListener(v -> saveProfile());
        btnViewHistory.setOnClickListener(v -> viewEventHistory());
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    /**
     * Load current user's profile from Firestore
     */
    private void loadProfile() {
        showLoading(true);

        profileRepository.getProfile(userId, new ProfileRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(Profile profile) {
                currentProfile = profile;
                deviceAuthenticator.updateCachedProfile(profile);
                displayProfile(profile);
                showLoading(false);
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(ProfileActivity.this,
                        getString(R.string.error_occurred) + ": " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Display profile data in UI
     */
    private void displayProfile(Profile profile) {
        etName.setText(profile.getName());
        etEmail.setText(profile.getEmail());
        etPhone.setText(profile.getPhoneNumber());
        switchNotifications.setChecked(profile.isNotificationEnabled());

        // Set role spinner
        String role = profile.getRole();
        int rolePosition = 0; // Default to entrant
        if (Profile.ROLE_ORGANIZER.equals(role)) {
            rolePosition = 1;
        } else if (Profile.ROLE_ADMIN.equals(role)) {
            rolePosition = 2;
        }
        spinnerRole.setSelection(rolePosition);

        // Load profile image if available
        if (profile.getProfileImageUrl() != null && !profile.getProfileImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(profile.getProfileImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(profileImage);
        }
    }

    /**
     * Open image picker
     */
    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    /**
     * Validate and save profile
     */
    private void saveProfile() {
        // Get input values
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";
        boolean notificationsEnabled = switchNotifications.isChecked();

        // Get selected role
        int rolePosition = spinnerRole.getSelectedItemPosition();
        String role = Profile.ROLE_ENTRANT; // Default
        if (rolePosition == 1) {
            role = Profile.ROLE_ORGANIZER;
        } else if (rolePosition == 2) {
            role = Profile.ROLE_ADMIN;
        }

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

        // Create updates map
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("email", email);
        updates.put("phoneNumber", phone);
        updates.put("notificationEnabled", notificationsEnabled);
        updates.put("role", role);

        // TODO: If image was selected, upload to Storage and add profileImageUrl to updates

        // Save to Firestore
        showLoading(true);
        profileRepository.updateProfile(userId, updates,
                new ProfileRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String userId) {
                        showLoading(false);
                        Toast.makeText(ProfileActivity.this,
                                getString(R.string.profile_saved),
                                Toast.LENGTH_SHORT).show();

                        // Reload profile to update cache
                        loadProfile();
                    }
                },
                new ProfileRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showLoading(false);
                        Toast.makeText(ProfileActivity.this,
                                getString(R.string.error_profile_save_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    /**
     * Navigate to event history activity
     */
    private void viewEventHistory() {
        Intent intent = new Intent(this, com.example.pickme.ui.history.EventHistoryActivity.class);
        startActivity(intent);
    }

    /**
     * Show delete account confirmation dialog
     */
    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setPositiveButton(R.string.delete_account_confirm, (dialog, which) -> deleteAccount())
                .setNegativeButton(R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Delete user account with cascade deletion
     */
    private void deleteAccount() {
        showLoading(true);

        profileRepository.deleteProfile(userId,
                new ProfileRepository.OnSuccessListener() {
                    @Override
                    public void onSuccess(String userId) {
                        showLoading(false);
                        Toast.makeText(ProfileActivity.this,
                                getString(R.string.account_deleted),
                                Toast.LENGTH_LONG).show();

                        // Clear authentication data
                        deviceAuthenticator.clearAuthData();

                        // Navigate back to main screen or login
                        finish();
                    }
                },
                new ProfileRepository.OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        showLoading(false);
                        Toast.makeText(ProfileActivity.this,
                                getString(R.string.error_delete_failed) + ": " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }
    private void enableBiometricFlow() {
        int can = BiometricManager.from(this)
                .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            switchBiometric.setChecked(false);
            // optionally show a toast that biometrics not enrolled/available
            return;
        }

        try {
            BioAuthUtil.ensureKeyExists();
            final javax.crypto.Cipher enc = BioAuthUtil.getEncryptCipher();

            BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Enable biometric login")
                    .setSubtitle("Authenticate to secure your login")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                    .setConfirmationRequired(false)
                    .build();

            BiometricPrompt prompt = new BiometricPrompt(
                    this,
                    ContextCompat.getMainExecutor(this),
                    new BiometricPrompt.AuthenticationCallback() {
                        @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                            try {
                                // Use a tiny token; device/user id is perfect
                                String token = DeviceAuthenticator.getInstance(ProfileActivity.this).getStoredUserId();
                                if (token == null) token = "OK";

                                javax.crypto.Cipher c = result.getCryptoObject().getCipher();
                                byte[] blob = c.doFinal(token.getBytes());
                                byte[] iv = c.getIV();
                                BioPrefsUtil.save(getApplicationContext(), true, iv, blob);
                                switchBiometric.setChecked(true);
                            } catch (Exception e) {
                                BioPrefsUtil.disable(getApplicationContext());
                                switchBiometric.setChecked(false);
                            }
                        }
                        @Override public void onAuthenticationError(int code, CharSequence err) {
                            BioPrefsUtil.disable(getApplicationContext());
                            switchBiometric.setChecked(false);
                        }
                        @Override public void onAuthenticationFailed() {
                            // do nothing; prompt stays
                        }
                    });

            prompt.authenticate(info, new BiometricPrompt.CryptoObject(enc));

        } catch (Exception e) {
            BioPrefsUtil.disable(this);
            switchBiometric.setChecked(false);
        }
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
        btnViewHistory.setEnabled(!show);
        btnDeleteAccount.setEnabled(!show);
        btnChangePhoto.setEnabled(!show);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

