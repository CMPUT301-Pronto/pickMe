package com.example.pickme.ui.admin;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pickme.R;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.google.android.material.appbar.MaterialToolbar;

/**
 * AdminDashboardActivity - Central hub for admin features
 *
 * Features:
 * - Direct access to all admin options (no drawer)
 * - Browse events, profiles, images
 * - View notification logs
 * - Remove organizers
 * - Role-based access control (admin only)
 *
 * Related User Stories: US 03.01.01-08.01
 */
public class AdminDashboardActivity extends AppCompatActivity {

    private static final String TAG = "AdminDashboard";

    private MaterialToolbar toolbar;
    private Button btnBrowseEvents;
    private Button btnBrowseProfiles;
    private Button btnBrowseImages;
    private Button btnNotificationLogs;
    private Button btnRemoveOrganizer;

    private DeviceAuthenticator deviceAuth;
    private ProfileRepository profileRepository;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Initialize
        initializeViews();
        initializeData();
        setupToolbar();
        checkAdminAccess();
        setupButtons();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        btnBrowseEvents = findViewById(R.id.btnBrowseEvents);
        btnBrowseProfiles = findViewById(R.id.btnBrowseProfiles);
        btnBrowseImages = findViewById(R.id.btnBrowseImages);
        btnNotificationLogs = findViewById(R.id.btnNotificationLogs);
        btnRemoveOrganizer = findViewById(R.id.btnRemoveOrganizer);
    }

    private void initializeData() {
        deviceAuth = DeviceAuthenticator.getInstance(this);
        profileRepository = new ProfileRepository();
        currentUserId = deviceAuth.getStoredUserId();
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    /**
     * Check if user has admin access
     */
    private void checkAdminAccess() {
        Profile cachedProfile = deviceAuth.getCachedProfile();
        if (cachedProfile != null) {
            checkRole(cachedProfile);
        } else {
            // Load profile to check role
            profileRepository.getProfile(currentUserId, new ProfileRepository.OnProfileLoadedListener() {
                @Override
                public void onProfileLoaded(Profile profile) {
                    checkRole(profile);
                }

                @Override
                public void onError(Exception e) {
                    showAccessDenied();
                }
            });
        }
    }

    private void checkRole(Profile profile) {
        if (profile == null || !Profile.ROLE_ADMIN.equals(profile.getRole())) {
            showAccessDenied();
        }
    }

    private void showAccessDenied() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.admin_access_denied_title)
                .setMessage(R.string.admin_access_denied_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> finish())
                .setCancelable(false)
                .show();
    }

    /**
     * Setup button click listeners
     */
    private void setupButtons() {
        btnBrowseEvents.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminEventsActivity.class);
            startActivity(intent);
        });

        btnBrowseProfiles.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminProfilesActivity.class);
            startActivity(intent);
        });

        btnBrowseImages.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminImagesActivity.class);
            startActivity(intent);
        });

        btnNotificationLogs.setOnClickListener(v -> {
            Intent intent = new Intent(this, AdminNotificationLogsActivity.class);
            startActivity(intent);
        });

        btnRemoveOrganizer.setOnClickListener(v -> {
            Toast.makeText(this, "Remove Organizer - Coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}

