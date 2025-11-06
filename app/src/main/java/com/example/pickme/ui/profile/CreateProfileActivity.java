package com.example.pickme.ui.profile;
import com.example.pickme.utils.PasswordUtil;
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
import com.google.android.material.textfield.TextInputLayout;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * JAVADOCS LLM GENERATED
 *
 * # CreateProfileActivity
 *
 * Activity responsible for first-time (or explicit) profile creation and onboarding.
 * It collects basic user info (name, email, optional avatar) and—if provided—creates
 * a salted PBKDF2 password hash that’s stored with the {@link Profile} in Firestore.
 *
 * ## Role in architecture
 * - **Presentation layer** of the profile creation flow (MVx-friendly).
 * - Delegates persistence to {@link ProfileRepository}.
 * - Delegates device identity and cached profile state to {@link DeviceAuthenticator}.
 *
 * ## Key behaviors
 * - Suggests a default display name derived from the device/installation ID.
 * - Validates inputs (name, email pattern, password rules).
 * - Hashes and salts passwords via {@link PasswordUtil} (PBKDF2WithHmacSHA256).
 * - Optionally previews a chosen profile image (Storage upload is TODO).
 *
 * ## Outstanding items / technical debt
 * - Avatar preview is wired, **but actual upload to Firebase Storage is a TODO**.
 * - Consider client-side password strength meter & breach checks.
 * - Consider throttling / retry logic on Firestore write failure.
 * - Consider replacing Toasts with a UI surface (e.g., Snackbar) for accessibility.
 *
 * @since 1.0
 */
public class CreateProfileActivity extends AppCompatActivity {

    private static final String TAG = "CreateProfileActivity";

    // UI Components
    private CircleImageView profileImage;
    private Button btnUploadPhoto;
    private TextInputEditText etName;
    private TextInputEditText etEmail;
    private TextInputEditText etPassword;
    private TextInputEditText etConfirm;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmLayout;
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
    /**
     * Android lifecycle entry point. Initializes views, services, and UI listeners for the
     * profile creation flow. Also pre-fills a suggested display name using the device ID.
     *
     * @param savedInstanceState saved state bundle if activity is being re-created
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

    // Bind view references
    private void initializeViews() {
        profileImage = findViewById(R.id.profileImage);
        btnUploadPhoto = findViewById(R.id.btnUploadPhoto);
        etName = findViewById(R.id.etName);
        etEmail = findViewById(R.id.etEmail);
        // NEW: password fields
        passwordLayout = findViewById(R.id.passwordLayout);
        confirmLayout  = findViewById(R.id.confirmLayout);
        etPassword     = findViewById(R.id.etPassword);
        etConfirm      = findViewById(R.id.etConfirm);

        btnCreateProfile = findViewById(R.id.btnCreateProfile);
        btnSkip = findViewById(R.id.btnSkip);
        progressBar = findViewById(R.id.progressBar);
    }

    /** Resolve repositories/services and derive a suggested display name. */
    private void initializeServices() {
        profileRepository = new ProfileRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);

        deviceAuthenticator.getDeviceId(deviceId -> {
            userId = deviceId;
            String suggestedName = "User" + deviceId.substring(0, 8);
            etName.setText(suggestedName);
            etName.setSelection(suggestedName.length());
        });
    }

    /** Configure the system file picker for avatar selection. */
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

    /** Wire up click listeners for primary actions. */
    private void setupListeners() {
        btnUploadPhoto.setOnClickListener(v -> openImagePicker());
        btnCreateProfile.setOnClickListener(v -> createProfile());
        btnSkip.setOnClickListener(v -> skipProfileCreation());
    }

    /** Launch the system image picker for avatar selection. */
    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    /**
     * Validate inputs, hash password (if provided), and persist a {@link Profile} to Firestore.
     * On success, caches the profile and transitions to {@link MainActivity}.
     *
     * <p><b>Security notes</b>:
     * <ul>
     *   <li>Passwords are immediately salted and hashed on-device using PBKDF2 (no plaintext
     *       storage).</li>
     *   <li>Consider moving password creation into a dedicated screen if you later add auth flows.</li>
     * </ul>
     */
    private void createProfile() {
        String name  = etName.getText()  != null ? etName.getText().toString().trim()  : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String pass  = etPassword.getText() != null ? etPassword.getText().toString()  : "";
        String conf  = etConfirm.getText()  != null ? etConfirm.getText().toString()   : "";

        // Basic validation
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

        // Password validation (require for account w/ email)
        passwordLayout.setError(null);
        confirmLayout.setError(null);

        if (TextUtils.isEmpty(pass)) {
            passwordLayout.setError("Password required");
            etPassword.requestFocus();
            return;
        }
        if (pass.length() < 6) {
            passwordLayout.setError("Minimum 6 characters");
            etPassword.requestFocus();
            return;
        }
        if (!pass.equals(conf)) {
            confirmLayout.setError("Passwords do not match");
            etConfirm.requestFocus();
            return;
        }

        // Build profile
        Profile profile = new Profile(userId, name, email);
        profile.setNotificationEnabled(true);
        profile.setRole(Profile.ROLE_ENTRANT);

        // OPTIONAL: upload selectedImageUri to Storage and set profile.setProfileImageUrl(...)

        // Hash + salt
        String saltB64 = PasswordUtil.generateSaltB64();
        String hashB64 = PasswordUtil.hashToB64(pass.toCharArray(), saltB64);
        profile.setPasswordSalt(saltB64);
        profile.setPasswordHash(hashB64);
        profile.setPasswordAlgo(PasswordUtil.algorithmName());

        // Save to Firestore
        showLoading(true);
        profileRepository.createProfile(profile,
                userId -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.profile_saved), Toast.LENGTH_SHORT).show();
                    deviceAuthenticator.updateCachedProfile(profile);
                    navigateToMain();
                },
                e -> {
                    showLoading(false);
                    Toast.makeText(this, getString(R.string.error_profile_save_failed) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
        );
    }

    /**
     * Create a minimal {@link Profile} without password (fast-track onboarding),
     * then persist and navigate to {@link MainActivity}.
     *
     * <p>Use this for “skip for now” flow; user can enrich profile later.</p>
     */
    private void skipProfileCreation() {
        Profile profile = new Profile(userId, "User" + userId.substring(0, 8));
        profile.setNotificationEnabled(true);
        profile.setRole(Profile.ROLE_ENTRANT);

        showLoading(true);
        profileRepository.createProfile(profile,
                userId -> {
                    showLoading(false);
                    deviceAuthenticator.updateCachedProfile(profile);
                    navigateToMain();
                },
                e -> {
                    showLoading(false);
                    Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
        );
    }
    /** Navigate to the main screen */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Toggle progress visibility and temporarily disable interactive controls to prevent
     * duplicate submissions while network work is in flight.
     *
     * @param show true to show progress and disable inputs; false to hide and re-enable
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnCreateProfile.setEnabled(!show);
        btnSkip.setEnabled(!show);
        btnUploadPhoto.setEnabled(!show);
        etName.setEnabled(!show);
        etEmail.setEnabled(!show);
        if (etPassword != null) etPassword.setEnabled(!show);
        if (etConfirm  != null) etConfirm.setEnabled(!show);
    }
    /**
     * Override back navigation during first-time setup to ensure the user completes
     * the minimum required onboarding flow before entering the app.
     *
     * <p>Intentionally suppresses the super call.</p>
     */
    @Override
    @SuppressWarnings("MissingSuperCall")
    public void onBackPressed() {
        // Prevent back navigation during first-time setup
    }
}