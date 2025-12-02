package com.example.pickme.ui.profile;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Patterns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ImageRepository;
import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.ui.history.EventHistoryActivity;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * ProfileFragment - View and edit user profile
 *
 * Features:
 * - Load current user's profile
 * - Edit name, email, phone
 * - Toggle notifications (with clear UI labeling)
 * - Upload/change profile image
 * - View event history
 * - Delete account with confirmation (deletes user's events too)
 *
 * Related User Stories: US 01.02.01, US 01.02.02, US 01.02.04, US 01.04.04
 */
public class ProfileFragment extends Fragment {

    private static final String TAG = "ProfileFragment";

    // UI
    private CircleImageView profileImage;
    private Button btnChangePhoto, btnSave, btnViewHistory, btnDeleteAccount;

    private SwitchMaterial switchNotifications;
    private TextInputEditText etName, etEmail, etPhone;
    private Spinner spinnerRole;
    private ProgressBar progressBar;
    private boolean notificationsEnabled;

    // Data / services
    private ProfileRepository profileRepository;
    private EventRepository eventRepository;
    private ImageRepository imageRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private Profile currentProfile;
    private String userId;
    private Uri selectedImageUri;

    private ActivityResultLauncher<String> imagePickerLauncher;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View root, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(root, savedInstanceState);

        initializeViews(root);
        initializeRepositories();
        setupImagePicker();
        setupListeners();

        loadProfile();
    }

    private void initializeViews(View root) {
        profileImage = root.findViewById(R.id.profileImage);
        btnChangePhoto = root.findViewById(R.id.btnChangePhoto);
        etName = root.findViewById(R.id.etName);
        etEmail = root.findViewById(R.id.etEmail);
        etPhone = root.findViewById(R.id.etPhone);
        spinnerRole = root.findViewById(R.id.spinnerRole);
        btnSave = root.findViewById(R.id.btnSave);
        btnViewHistory = root.findViewById(R.id.btnViewHistory);
        btnDeleteAccount = root.findViewById(R.id.btnDeleteAccount);
        progressBar = root.findViewById(R.id.progressBar);
        switchNotifications = root.findViewById(R.id.switchNotifications);

        // Spinner
        String[] roles = {
                getString(R.string.role_entrant),
                getString(R.string.role_organizer),
                getString(R.string.role_admin)
        };
        ArrayAdapter<String> roleAdapter = new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_spinner_item, roles);
        roleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerRole.setAdapter(roleAdapter);
    }

    private void initializeRepositories() {
        profileRepository = new ProfileRepository();
        eventRepository = new EventRepository();
        imageRepository = new ImageRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(requireContext());

        userId = deviceAuthenticator.getStoredUserId();
        if (userId == null) {
            Toast.makeText(requireContext(), "User not authenticated", Toast.LENGTH_SHORT).show();
            requireActivity().finish();
        }
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
                        Toast.makeText(requireContext(),
                                "Image selected. Save profile to upload.",
                                Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void setupListeners() {
        btnChangePhoto.setOnClickListener(v -> openImagePicker());
        btnSave.setOnClickListener(v -> saveProfile());
        btnViewHistory.setOnClickListener(v ->
                startActivity(new Intent(requireContext(), EventHistoryActivity.class)));
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            notificationsEnabled = isChecked;
            // Update the switch text to make it clear what state it's in
            updateNotificationSwitchText(isChecked);

            // Auto-save notification preference immediately
            if (currentProfile != null) {
                saveNotificationPreference(isChecked);
            }
        });
    }

    /**
     * Update switch text to clearly indicate notification state
     * FIX for US 01.04.04 - Make notification toggle UI clear
     */
    private void updateNotificationSwitchText(boolean enabled) {
        if (switchNotifications != null) {
            switchNotifications.setText(enabled ? "ON" : "OFF");
        }
    }

    /**
     * Auto-save notification preference to Firestore immediately
     * FIX: Toggle was not persisting because it only saved on "Save" button click
     */
    private void saveNotificationPreference(boolean enabled) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("notificationEnabled", enabled);

        profileRepository.updateProfile(userId, updates,
                uid -> {
                    Log.d(TAG, "Notification preference saved: " + enabled);
                    // Update cached profile
                    if (currentProfile != null) {
                        currentProfile.setNotificationEnabled(enabled);
                        deviceAuthenticator.updateCachedProfile(currentProfile);
                    }
                },
                e -> {
                    Log.e(TAG, "Failed to save notification preference", e);
                    // Revert the switch on error
                    switchNotifications.setChecked(!enabled);
                    Toast.makeText(requireContext(),
                            "Failed to update notification setting",
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void loadProfile() {
        showLoading(true);
        profileRepository.getProfile(userId, new ProfileRepository.OnProfileLoadedListener() {
            @Override public void onProfileLoaded(Profile profile) {
                currentProfile = profile;
                deviceAuthenticator.updateCachedProfile(profile);
                displayProfile(profile);
                showLoading(false);
            }

            @Override public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(requireContext(),
                        getString(R.string.error_occurred) + ": " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayProfile(Profile profile) {
        etName.setText(profile.getName());
        etEmail.setText(profile.getEmail());
        etPhone.setText(profile.getPhoneNumber());
        notificationsEnabled = profile.isNotificationEnabled();
        switchNotifications.setChecked(notificationsEnabled);
        updateNotificationSwitchText(notificationsEnabled);

        int rolePosition = 0;
        if (Profile.ROLE_ORGANIZER.equals(profile.getRole())) rolePosition = 1;
        else if (Profile.ROLE_ADMIN.equals(profile.getRole())) rolePosition = 2;
        spinnerRole.setSelection(rolePosition);

        if (!TextUtils.isEmpty(profile.getProfileImageUrl())) {
            Glide.with(this)
                    .load(profile.getProfileImageUrl())
                    .placeholder(R.drawable.ic_launcher_foreground)
                    .into(profileImage);
        }
    }

    private void openImagePicker() {
        imagePickerLauncher.launch("image/*");
    }

    private void saveProfile() {
        String name = etName.getText() != null ? etName.getText().toString().trim() : "";
        String email = etEmail.getText() != null ? etEmail.getText().toString().trim() : "";
        String phone = etPhone.getText() != null ? etPhone.getText().toString().trim() : "";

        int rolePosition = spinnerRole.getSelectedItemPosition();
        String role = Profile.ROLE_ENTRANT;
        if (rolePosition == 1) role = Profile.ROLE_ORGANIZER;
        else if (rolePosition == 2) role = Profile.ROLE_ADMIN;

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

        // Track old role for comparison
        String oldRole = currentProfile != null ? currentProfile.getRole() : Profile.ROLE_ENTRANT;
        final String newRole = role;

        // Check if image was selected
        if (selectedImageUri != null) {
            Log.d(TAG, "Profile image selected, uploading...");
            uploadProfileImageAndSave(name, email, phone, newRole, oldRole);
        } else {
            Log.d(TAG, "No new profile image selected, saving profile data only");
            saveProfileData(name, email, phone, newRole, oldRole, null);
        }
    }

    /**
     * Upload profile image to Firebase Storage, then save profile data
     */
    private void uploadProfileImageAndSave(String name, String email, String phone,
                                           String newRole, String oldRole) {
        showLoading(true);
        Log.d(TAG, "Starting profile image upload for user: " + userId);

        imageRepository.uploadProfileImage(userId, selectedImageUri,
                new ImageRepository.OnUploadCompleteListener() {
                    @Override
                    public void onUploadComplete(String downloadUrl, String posterId) {
                        Log.d(TAG, "Profile image uploaded successfully: " + downloadUrl);
                        saveProfileData(name, email, phone, newRole, oldRole, downloadUrl);
                        selectedImageUri = null;
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Profile image upload failed", e);
                        showLoading(false);
                        Toast.makeText(requireContext(),
                                "Failed to upload profile image: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                        new AlertDialog.Builder(requireContext())
                                .setTitle("Upload Failed")
                                .setMessage("Failed to upload profile image. Save profile without image?")
                                .setPositiveButton("Yes", (d, w) -> {
                                    saveProfileData(name, email, phone, newRole, oldRole, null);
                                    selectedImageUri = null;
                                })
                                .setNegativeButton("No", null)
                                .show();
                    }
                });
    }

    /**
     * Save profile data to Firestore
     */
    private void saveProfileData(String name, String email, String phone,
                                 String newRole, String oldRole, String profileImageUrl) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("email", email);
        updates.put("phoneNumber", phone);
        updates.put("notificationEnabled", notificationsEnabled);
        updates.put("role", newRole);

        if (profileImageUrl != null) {
            Log.d(TAG, "Adding profileImageUrl to updates: " + profileImageUrl);
            updates.put("profileImageUrl", profileImageUrl);
        }

        showLoading(true);
        Log.d(TAG, "Updating profile in Firestore for user: " + userId);

        profileRepository.updateProfile(userId, updates,
                uid -> {
                    showLoading(false);
                    Log.d(TAG, "Profile updated successfully in Firestore");
                    Toast.makeText(requireContext(),
                            getString(R.string.profile_saved),
                            Toast.LENGTH_SHORT).show();
                    loadProfile();

                    // Show role change confirmation if role changed
                    if (!oldRole.equals(newRole)) {
                        showRoleChangeDialog(newRole);
                    }
                },
                e -> {
                    showLoading(false);
                    Log.e(TAG, "Failed to update profile in Firestore", e);
                    Toast.makeText(requireContext(),
                            getString(R.string.error_profile_save_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_account_title)
                .setMessage(R.string.delete_account_message)
                .setPositiveButton(R.string.delete_account_confirm, (d, w) -> deleteAccount())
                .setNegativeButton(R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Delete account - FIXED to properly handle app state
     *
     * FIX for US 01.02.04: App was crashing because we called finish() on activity
     * FIX for US 01.04.02: Now deletes user's events before deleting profile
     * FIX for US 03.07.01: Properly cascades event deletion for organizers
     */
    private void deleteAccount() {
        showLoading(true);

        // First, delete all events created by this user (US 01.04.02, US 03.07.01)
        deleteUserEventsAndThenProfile();
    }

    /**
     * Delete all events created by this user, then delete the profile
     */
    private void deleteUserEventsAndThenProfile() {
        eventRepository.getEventsByOrganizer(userId, new EventRepository.OnEventsLoadedListener() {
            @Override
            public void onEventsLoaded(List<Event> events) {
                if (events.isEmpty()) {
                    // No events to delete, proceed to delete profile
                    deleteProfileAndRedirect();
                } else {
                    // Delete all events first
                    deleteEventsRecursively(events, 0);
                }
            }

            @Override
            public void onError(Exception e) {
                // Even if we can't get events, try to delete the profile
                Log.e(TAG, "Failed to get user events for deletion, proceeding with profile deletion", e);
                deleteProfileAndRedirect();
            }
        });
    }

    /**
     * Recursively delete events one by one
     */
    private void deleteEventsRecursively(List<Event> events, int index) {
        if (index >= events.size()) {
            // All events deleted, now delete profile
            deleteProfileAndRedirect();
            return;
        }

        Event event = events.get(index);
        eventRepository.deleteEvent(event.getEventId(),
                id -> {
                    Log.d(TAG, "Deleted event: " + event.getEventId());
                    // Delete next event
                    deleteEventsRecursively(events, index + 1);
                },
                e -> {
                    Log.e(TAG, "Failed to delete event: " + event.getEventId(), e);
                    // Continue with next event anyway
                    deleteEventsRecursively(events, index + 1);
                });
    }

    /**
     * Delete the profile and redirect to CreateProfileActivity
     * FIXED: Instead of finishing activity (which crashes), redirect to profile creation
     */
    private void deleteProfileAndRedirect() {
        profileRepository.deleteProfile(userId,
                uid -> {
                    showLoading(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.account_deleted),
                            Toast.LENGTH_LONG).show();

                    // Clear auth data
                    deviceAuthenticator.clearAuthData();

                    // FIX: Instead of finishing (which crashes), redirect to CreateProfileActivity
                    // This creates a new user identity and profile creation flow
                    Intent intent = new Intent(requireContext(), CreateProfileActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);

                    // Now it's safe to finish since we've started a new activity
                    if (getActivity() != null) {
                        getActivity().finish();
                    }
                },
                e -> {
                    showLoading(false);
                    Toast.makeText(requireContext(),
                            getString(R.string.error_delete_failed) + ": " + e.getMessage(),
                            Toast.LENGTH_SHORT).show();
                });
    }

    private void showRoleChangeDialog(String newRole) {
        String roleName;
        switch (newRole) {
            case Profile.ROLE_ORGANIZER:
                roleName = getString(R.string.role_organizer);
                break;
            case Profile.ROLE_ADMIN:
                roleName = getString(R.string.role_admin);
                break;
            default:
                roleName = getString(R.string.role_entrant);
                break;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.role_changed_title)
                .setMessage(getString(R.string.role_changed_message, roleName))
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        btnSave.setEnabled(!show);
        btnViewHistory.setEnabled(!show);
        btnDeleteAccount.setEnabled(!show);
        btnChangePhoto.setEnabled(!show);
    }
}