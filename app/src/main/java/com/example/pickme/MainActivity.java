package com.example.pickme;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pickme.models.Profile;
import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.ui.events.CreateEventActivity;
import com.example.pickme.ui.events.EventBrowserActivity;
import com.example.pickme.ui.events.OrganizerDashboardActivity;
import com.example.pickme.ui.history.EventHistoryActivity;
import com.example.pickme.ui.invitations.EventInvitationsActivity;
import com.example.pickme.ui.profile.ProfileActivity;

/**
 * MainActivity - Role-based launcher for PickMe app
 *
 * Provides access to features based on user role:
 * - Entrants: Profile, Browse Events, Invitations, History
 * - Organizers: Profile, Create Event, My Events Dashboard, Browse Events
 * - Admins: Additional admin features (future)
 *
 * Detects user role from Profile and displays appropriate navigation options.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    // Common UI elements
    private TextView tvWelcome;
    private TextView tvUserId;
    private TextView tvRoleBadge;
    private ProgressBar progressBar;
    private View contentCard;

    // Entrant buttons
    private View entrantSection;
    private Button btnProfile;
    private Button btnBrowseEvents;
    private Button btnInvitations;
    private Button btnHistory;

    // Organizer buttons
    private View organizerSection;
    private Button btnOrganizerProfile;
    private Button btnCreateEvent;
    private Button btnMyEvents;
    private Button btnOrganizerBrowse;

    // Admin buttons
    private View adminSection;
    private Button btnAdminProfile;
    private Button btnAdminDashboard;
    private Button btnAdminBrowse;

    private DeviceAuthenticator deviceAuth;
    private String userId;
    private Profile currentProfile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        initializeViews();

        // Show loading state
        showLoading(true);

        // Initialize device authentication
        deviceAuth = DeviceAuthenticator.getInstance(this);

        // Initialize user (async)
        deviceAuth.initializeUser(new DeviceAuthenticator.OnUserInitializedListener() {
            @Override
            public void onUserInitialized(Profile profile, boolean isNewUser) {
                currentProfile = profile;
                userId = profile.getUserId();
                Log.d(TAG, "User initialized: " + userId + ", role: " + profile.getRole());

                // Update UI on main thread
                runOnUiThread(() -> {
                    showLoading(false);
                    displayUserInfo();
                    setupRoleBasedUI();
                });
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to initialize user", e);

                // Show error on main thread
                runOnUiThread(() -> {
                    showLoading(false);
                    Toast.makeText(MainActivity.this,
                            "Failed to initialize: " + e.getMessage(),
                            Toast.LENGTH_LONG).show();

                    // Still allow navigation with fallback to entrant role
                    userId = "unknown";
                    displayUserInfo();
                    setupEntrantUI();
                });
            }
        });
    }

    /**
     * Initialize all view references
     */
    private void initializeViews() {
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserId = findViewById(R.id.tvUserId);
        tvRoleBadge = findViewById(R.id.tvRoleBadge);
        progressBar = findViewById(R.id.progressBar);
        contentCard = findViewById(R.id.contentCard);

        // Entrant section
        entrantSection = findViewById(R.id.entrantSection);
        btnProfile = findViewById(R.id.btnProfile);
        btnBrowseEvents = findViewById(R.id.btnBrowseEvents);
        btnInvitations = findViewById(R.id.btnInvitations);
        btnHistory = findViewById(R.id.btnHistory);

        // Organizer section
        organizerSection = findViewById(R.id.organizerSection);
        btnOrganizerProfile = findViewById(R.id.btnOrganizerProfile);
        btnCreateEvent = findViewById(R.id.btnCreateEvent);
        btnMyEvents = findViewById(R.id.btnMyEvents);
        btnOrganizerBrowse = findViewById(R.id.btnOrganizerBrowse);

        // Admin section
        adminSection = findViewById(R.id.adminSection);
        btnAdminProfile = findViewById(R.id.btnAdminProfile);
        btnAdminDashboard = findViewById(R.id.btnAdminDashboard);
        btnAdminBrowse = findViewById(R.id.btnAdminBrowse);
    }

    /**
     * Display user information with role badge
     */
    private void displayUserInfo() {
        if (userId != null && userId.length() > 8) {
            tvUserId.setText("Device ID: " + userId.substring(0, 8) + "...");
        } else if (userId != null) {
            tvUserId.setText("Device ID: " + userId);
        } else {
            tvUserId.setText("Device ID: Loading...");
        }

        // Update role badge
        if (currentProfile != null) {
            String role = currentProfile.getRole();
            tvRoleBadge.setText(role.toUpperCase());
            tvRoleBadge.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Setup UI based on user role
     */
    private void setupRoleBasedUI() {
        if (currentProfile == null) {
            setupEntrantUI();
            return;
        }

        String role = currentProfile.getRole();

        if (Profile.ROLE_ORGANIZER.equals(role)) {
            setupOrganizerUI();
        } else if (Profile.ROLE_ADMIN.equals(role)) {
            setupAdminUI();
        } else {
            setupEntrantUI();
        }
    }

    /**
     * Setup entrant-specific UI and navigation
     */
    private void setupEntrantUI() {
        tvWelcome.setText("Welcome, Entrant!");

        // Hide all sections first
        entrantSection.setVisibility(View.GONE);
        organizerSection.setVisibility(View.GONE);
        adminSection.setVisibility(View.GONE);

        // Show only entrant section
        entrantSection.setVisibility(View.VISIBLE);

        btnProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
        });

        btnBrowseEvents.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventBrowserActivity.class);
            startActivity(intent);
        });

        btnInvitations.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventInvitationsActivity.class);
            startActivity(intent);
        });

        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventHistoryActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Setup organizer-specific UI and navigation
     */
    private void setupOrganizerUI() {
        tvWelcome.setText("Welcome, Organizer!");

        // Hide all sections first
        entrantSection.setVisibility(View.GONE);
        organizerSection.setVisibility(View.GONE);
        adminSection.setVisibility(View.GONE);

        // Show only organizer section
        organizerSection.setVisibility(View.VISIBLE);

        btnOrganizerProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
        });

        btnCreateEvent.setOnClickListener(v -> {
            Intent intent = new Intent(this, CreateEventActivity.class);
            startActivity(intent);
        });

        btnMyEvents.setOnClickListener(v -> {
            Intent intent = new Intent(this, OrganizerDashboardActivity.class);
            startActivity(intent);
        });

        btnOrganizerBrowse.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventBrowserActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Setup admin-specific UI and navigation
     */
    private void setupAdminUI() {
        tvWelcome.setText("Welcome, Administrator!");

        // Hide all sections first
        entrantSection.setVisibility(View.GONE);
        organizerSection.setVisibility(View.GONE);
        adminSection.setVisibility(View.GONE);

        // Show only admin section
        adminSection.setVisibility(View.VISIBLE);

        btnAdminProfile.setOnClickListener(v -> {
            Intent intent = new Intent(this, ProfileActivity.class);
            startActivity(intent);
        });

        btnAdminDashboard.setOnClickListener(v -> {
            Intent intent = new Intent(this, com.example.pickme.ui.admin.AdminDashboardActivity.class);
            startActivity(intent);
        });

        btnAdminBrowse.setOnClickListener(v -> {
            Intent intent = new Intent(this, EventBrowserActivity.class);
            startActivity(intent);
        });
    }

    /**
     * Show/hide loading state
     */
    private void showLoading(boolean loading) {
        if (progressBar != null && contentCard != null) {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
            contentCard.setVisibility(loading ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh user info and role when returning to main screen
        if (deviceAuth != null) {
            String currentUserId = deviceAuth.getStoredUserId();
            if (currentUserId != null) {
                userId = currentUserId;

                // Check if profile is cached
                Profile cachedProfile = deviceAuth.getCachedProfile();
                if (cachedProfile != null) {
                    // Profile is cached, use it to update UI
                    currentProfile = cachedProfile;
                    displayUserInfo();
                    setupRoleBasedUI();
                } else {
                    // No cached profile, reload from Firestore
                    reloadProfile();
                }
            }
        }
    }

    /**
     * Reload profile from Firestore (for onResume)
     */
    private void reloadProfile() {
        ProfileRepository profileRepository = new ProfileRepository();
        profileRepository.getProfile(userId, new ProfileRepository.OnProfileLoadedListener() {
            @Override
            public void onProfileLoaded(Profile profile) {
                currentProfile = profile;
                deviceAuth.updateCachedProfile(profile);
                displayUserInfo();
                setupRoleBasedUI();
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Failed to reload profile in onResume", e);
                // Keep existing UI if reload fails
            }
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInviteIntent(intent);
    }

    @Override protected void onStart() {
        super.onStart();
        handleInviteIntent(getIntent());
    }

    private void handleInviteIntent(Intent i) {
        if (i == null) return;
        if (!"com.example.pickme.ACTION_OPEN_INVITATION".equals(i.getAction())) return;

        String eventId = i.getStringExtra("eventId");
        String invId   = i.getStringExtra("invitationId");
        long deadline  = 0L;
        try { deadline = Long.parseLong(i.getStringExtra("deadline")); } catch (Exception ignored) {}

        com.example.pickme.ui.invitations.InvitationDialogFragment
                .newInstance(eventId, invId, deadline)
                .show(getSupportFragmentManager(), "invite");
    }
}