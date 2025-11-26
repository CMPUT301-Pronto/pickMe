package com.example.pickme;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.NavGraph;
import androidx.navigation.NavInflater;
import androidx.navigation.NavOptions;
import androidx.navigation.fragment.NavHostFragment;

import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.services.FirebaseManager;
import com.example.pickme.services.RoleChangeListener;
import com.example.pickme.models.Profile;
import com.example.pickme.ui.events.EventBrowseFragment;
import com.example.pickme.ui.events.EventDetailsActivity;
import com.example.pickme.ui.profile.CreateProfileActivity;
import com.example.pickme.utils.Constants;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import android.view.MenuItem;

/**
 * MainActivity - Main entry point and navigation controller for PickMe app
 *
 * Responsibilities:
 * - Hosts NavHostFragment for fragment navigation
 * - Manages bottom navigation bar across different user roles
 * - Initializes device-based authentication
 * - Handles deep links for QR codes and invitations
 * - Coordinates role-based navigation graph switching
 * - Gates profile access (redirects to profile creation if needed)
 *
 * Navigation Structure:
 * - Entrant: Browse → Invitations → Profile
 * - Organizer: My Events → Browse → Profile
 * - Admin: Browse → Admin Dashboard → Profile
 *
 * Deep Link Support:
 * - eventlottery://event/{eventId} - QR code scanned event links
 * - ACTION_OPEN_INVITATION - Push notification invitation links
 *
 * Lifecycle:
 * - onCreate: Initialize auth, setup navigation, handle intents
 * - onNewIntent: Handle deep links when app is already running
 * - onStart: Re-check for pending deep links
 * - onDestroy: Cleanup role change listeners
 *
 * Related User Stories: US 01.07.01 (device auth), US 01.06.01 (QR scanning),
 *                       US 01.04.01 (navigation), US 01.02.01 (profile management)
 */
public class MainActivity extends AppCompatActivity implements RoleChangeListener {

    private static final String TAG = "MainActivity";

    private DeviceAuthenticator deviceAuth;
    private NavController navController;
    private BottomNavigationView bottomNav;
    private String currentRole = Profile.ROLE_ENTRANT;
    private NavController.OnDestinationChangedListener destinationChangedListener;

    /**
     * Modern QR scanner launcher using ActivityResultContracts API
     * Replaces deprecated onActivityResult() pattern
     */
    private ActivityResultLauncher<ScanOptions> qrScannerLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Setup modern QR scanner before any other initialization
        setupQRScannerLauncher();

        // Request runtime notification permission on Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 1001);
            }
        }

        // Get reference to the BottomNavigationView
        bottomNav = findViewById(R.id.bottom_nav);

        // Get the NavHostFragment responsible for managing fragment navigation
        NavHostFragment navHost =
                (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment);
        if (navHost == null) return;

        navController = navHost.getNavController();

        // Initialize device identity
        deviceAuth = DeviceAuthenticator.getInstance(this);

        // Register role change listener
        deviceAuth.addRoleChangeListener(this);

        // Load initial navigation based on cached profile
        Profile cachedProfile = deviceAuth.getCachedProfile();
        currentRole = cachedProfile != null ? cachedProfile.getRole() : Profile.ROLE_ENTRANT;
        setupNavigationForRole(currentRole);

        // Initialize device identity; do not auto-launch CreateProfile
        deviceAuth.initializeUser(new DeviceAuthenticator.OnUserInitializedListener() {
            @Override
            public void onUserInitialized(Profile profile, boolean isNewUser) {
                // Store or refresh FCM token once the device identity is initialized
                FirebaseManager.refreshAndStoreFcmToken();

                // Update navigation if role is different
                if (profile != null && !profile.getRole().equals(currentRole)) {
                    currentRole = profile.getRole();
                    setupNavigationForRole(currentRole);
                }
            }

            @Override
            public void onError(Exception e) {
                Toast.makeText(MainActivity.this,
                        "Auth init failed" + (e != null ? (": " + e.getMessage()) : ""),
                        Toast.LENGTH_LONG).show();
            }
        });

        // Handle any invitation intent passed when launching the app
        handleInviteIntent(getIntent());
    }

    /**
     * Setup modern QR scanner using ActivityResultContracts API
     * Replaces deprecated onActivityResult() pattern with lifecycle-aware launcher
     */
    private void setupQRScannerLauncher() {
        qrScannerLauncher = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) {
                String scannedData = result.getContents();
                Log.d(TAG, "QR code scanned: " + scannedData);

                // Check if it's an event lottery QR code
                if (scannedData.startsWith(Constants.DEEP_LINK_PREFIX_EVENT)) {
                    String eventId = scannedData.substring(Constants.DEEP_LINK_PREFIX_EVENT.length());
                    if (!eventId.isEmpty()) {
                        Log.d(TAG, "Opening event: " + eventId);
                        // Navigate to EventDetailsActivity
                        Intent eventIntent = new Intent(this, EventDetailsActivity.class);
                        eventIntent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
                        startActivity(eventIntent);
                    } else {
                        Toast.makeText(this, "Invalid QR code: empty event ID", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Invalid QR code format", Toast.LENGTH_SHORT).show();
                }
            } else {
                Log.d(TAG, "QR scan cancelled");
            }
        });
    }

    /**
     * Public method to launch QR scanner from fragments
     * Uses modern ActivityResultLauncher pattern
     */
    public void launchQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan Event QR Code");
        options.setCameraId(0); // Use back camera
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        qrScannerLauncher.launch(options);
    }

    // ...existing code...

    /**
     * Navigates to a destination with singleTop behavior, preventing multiple copies of the same fragment
     * from being stacked in the back stack.
     *
     * @param destId ID of the destination to navigate to.
     */
    private void navigateSingleTop(int destId) {
        NavDestination current = navController.getCurrentDestination();
        if (current != null && current.getId() == destId) return;

        NavOptions opts = new NavOptions.Builder()
                .setLaunchSingleTop(true)
                .setPopUpTo(navController.getGraph().getStartDestinationId(), false)
                .build();

        navController.navigate(destId, null, opts);
    }

    /**
     * Checks if a profile exists for the current device.
     * If not, redirects to CreateProfileActivity. Otherwise, navigates to the Profile screen.
     */
    private void gateProfileThenNavigate() {
        String userId = deviceAuth.getStoredUserId();

        // If device initialization hasn't completed yet, wait until it does
        if (userId == null) {
            deviceAuth.initializeUser(new DeviceAuthenticator.OnUserInitializedListener() {
                @Override
                public void onUserInitialized(com.example.pickme.models.Profile p, boolean isNew) {
                    // Once initialized, perform the check again
                    checkAndRouteProfile(deviceAuth.getStoredUserId());
                }

                @Override
                public void onError(Exception e) {
                    // If initialization fails, stay on the Browse tab
                    bottomNav.setSelectedItemId(R.id.navigation_browse);
                }
            });
            return;
        }

        checkAndRouteProfile(userId);
    }

    /**
     * Verifies profile existence in cache or Firestore and routes accordingly.
     *
     * @param userId The device's unique user identifier.
     */
    private void checkAndRouteProfile(String userId) {
        // Fast path: cached profile already exists, navigate directly
        if (deviceAuth.getCachedProfile() != null) {
            navigateSingleTop(R.id.navigation_profile);
            return;
        }

        // Otherwise, check Firestore for an existing profile
        new ProfileRepository().profileExists(userId, new ProfileRepository.OnProfileExistsListener() {
            @Override
            public void onCheckComplete(boolean exists) {
                if (exists) {
                    // Profile exists → show the Profile screen
                    navigateSingleTop(R.id.navigation_profile);
                } else {
                    // No profile found → prompt user to create one
                    startActivity(new Intent(MainActivity.this, CreateProfileActivity.class));
                    // Keep Browse tab selected to avoid being "stuck" on Profile
                    bottomNav.setSelectedItemId(R.id.navigation_browse);
                }
            }

            @Override
            public void onError(Exception e) {
                // Firestore check failed; stay on Browse tab
                bottomNav.setSelectedItemId(R.id.navigation_browse);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleInviteIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handleInviteIntent(getIntent());
    }

    /**
     * Handles deep links or custom intents used to open invitation dialogs or event details.
     *
     * Supports two types of intents:
     * 1. ACTION_OPEN_INVITATION: Custom action for push notification invitations
     *    - Opens invitation dialog fragment with event and invitation details
     *
     * 2. eventlottery://event/{eventId}: Deep link from QR code or external source
     *    - Navigates directly to EventDetailsActivity to show event information
     *    - Allows entrant to join waiting list
     *
     * @param i The intent received by the activity (from onCreate, onNewIntent, or onStart)
     */
    private void handleInviteIntent(@Nullable Intent i) {
        if (i == null) return;

        // Handle invitation intent from push notification
        if (Constants.ACTION_OPEN_INVITATION.equals(i.getAction())) {
            String eventId = i.getStringExtra(Constants.EXTRA_EVENT_ID);
            String invId = i.getStringExtra(Constants.EXTRA_INVITATION_ID);
            long deadline = 0L;

            try {
                deadline = Long.parseLong(i.getStringExtra(Constants.EXTRA_DEADLINE));
            } catch (Exception ignored) {}

            Log.d(TAG, "Opening invitation dialog for event: " + eventId);
            com.example.pickme.ui.invitations.InvitationDialogFragment
                    .newInstance(eventId, invId, deadline)
                    .show(getSupportFragmentManager(), "invite");
            return;
        }

        // Handle QR code deep link: eventlottery://event/{eventId}
        android.net.Uri data = i.getData();
        if (data != null && Constants.DEEP_LINK_SCHEME.equals(data.getScheme())
                && Constants.DEEP_LINK_HOST_EVENT.equals(data.getHost())) {
            // Extract event ID from path (format: eventlottery://event/{eventId})
            String path = data.getPath();
            if (path != null && path.startsWith("/")) {
                String eventId = path.substring(1); // Remove leading slash
                if (!eventId.isEmpty()) {
                    Log.d(TAG, "Deep link scanned for event: " + eventId);
                    // Navigate to EventDetailsActivity
                    Intent eventIntent = new Intent(this, EventDetailsActivity.class);
                    eventIntent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
                    startActivity(eventIntent);

                    // Clear the intent data to prevent re-opening on back press
                    i.setData(null);
                } else {
                    Log.w(TAG, "Deep link has empty event ID");
                }
            }
        }
    }

    // ...existing code...

    @Override
    public void onRoleChanged(String newRole) {
        currentRole = newRole;
        setupNavigationForRole(newRole);
    }

    /**
     * Setup navigation graph and menu based on user role
     *
     * @param role The user's role (ROLE_ENTRANT, ROLE_ORGANIZER, or ROLE_ADMIN)
     */
    private void setupNavigationForRole(String role) {
        // Clear previous destination change listener
        if (navController != null && destinationChangedListener != null) {
            navController.removeOnDestinationChangedListener(destinationChangedListener);
        }

        // Get appropriate navigation graph and menu based on role
        int navGraphResId;
        int menuResId;
        int startDestination;

        switch (role) {
            case Profile.ROLE_ORGANIZER:
                navGraphResId = R.navigation.navigation_organizer;
                menuResId = R.menu.bottom_nav_menu_organizer;
                startDestination = R.id.navigation_my_events;
                break;
            case Profile.ROLE_ADMIN:
                navGraphResId = R.navigation.navigation_admin;
                menuResId = R.menu.bottom_nav_menu_admin;
                startDestination = R.id.navigation_browse;
                break;
            case Profile.ROLE_ENTRANT:
            default:
                navGraphResId = R.navigation.navigation_entrant;
                menuResId = R.menu.bottom_nav_menu_entrant;
                startDestination = R.id.navigation_browse;
                break;
        }

        // Update bottom nav menu
        bottomNav.getMenu().clear();
        bottomNav.inflateMenu(menuResId);

        // Update navigation graph
        NavInflater inflater = navController.getNavInflater();
        NavGraph graph = inflater.inflate(navGraphResId);
        graph.setStartDestination(startDestination);
        navController.setGraph(graph);

        // Create and attach destination change listener
        destinationChangedListener = (controller, destination, arguments) -> {
            int destId = destination.getId();
            MenuItem menuItem = bottomNav.getMenu().findItem(destId);
            if (menuItem != null) {
                menuItem.setChecked(true);
            }
        };
        navController.addOnDestinationChangedListener(destinationChangedListener);

        // Setup bottom nav listener
        setupBottomNavListener();
    }

    /**
     * Setup bottom navigation item selection listener
     */
    private void setupBottomNavListener() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            // Handle common navigation items
            if (id == R.id.navigation_browse) {
                navigateSingleTop(R.id.navigation_browse);
                return true;
            }

            if (id == R.id.navigation_profile) {
                // Gated navigation: only allow access if a profile exists
                gateProfileThenNavigate();
                return true;
            }

            // Handle role-specific items
            if (id == R.id.navigation_invitations) {
                navigateSingleTop(R.id.navigation_invitations);
                return true;
            }

            if (id == R.id.navigation_my_events) {
                navigateSingleTop(R.id.navigation_my_events);
                return true;
            }

            if (id == R.id.navigation_admin) {
                navigateSingleTop(R.id.navigation_admin);
                return true;
            }

            // Legacy notification support (if it exists)
            if (id == R.id.navigation_notifications) {
                navigateSingleTop(R.id.navigation_notifications);
                return true;
            }

            return false;
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (deviceAuth != null) {
            deviceAuth.removeRoleChangeListener(this);
        }
    }
}

