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
 * FIXED: Improved notification intent handling for all notification types
 *
 * Responsibilities:
 * - Hosts NavHostFragment for fragment navigation
 * - Manages bottom navigation bar across different user roles
 * - Initializes device-based authentication
 * - Handles deep links for QR codes and invitations
 * - Handles push notification intents
 * - Coordinates role-based navigation graph switching
 * - Gates profile access (redirects to profile creation if needed)
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

        // Handle any intent passed when launching the app
        handleIntent(getIntent());
    }

    /**
     * Setup modern QR scanner using ActivityResultContracts API
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
     */
    public void launchQRScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt("Scan Event QR Code");
        options.setCameraId(0);
        options.setBeepEnabled(true);
        options.setOrientationLocked(false);
        qrScannerLauncher.launch(options);
    }

    /**
     * Navigates to a destination with singleTop behavior
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
     */
    private void gateProfileThenNavigate() {
        String userId = deviceAuth.getStoredUserId();

        if (userId == null) {
            deviceAuth.initializeUser(new DeviceAuthenticator.OnUserInitializedListener() {
                @Override
                public void onUserInitialized(Profile p, boolean isNew) {
                    checkAndRouteProfile(deviceAuth.getStoredUserId());
                }

                @Override
                public void onError(Exception e) {
                    bottomNav.setSelectedItemId(R.id.navigation_browse);
                }
            });
            return;
        }

        checkAndRouteProfile(userId);
    }

    /**
     * Verifies profile existence in cache or Firestore and routes accordingly.
     */
    private void checkAndRouteProfile(String userId) {
        if (deviceAuth.getCachedProfile() != null) {
            navigateSingleTop(R.id.navigation_profile);
            return;
        }

        new ProfileRepository().profileExists(userId, new ProfileRepository.OnProfileExistsListener() {
            @Override
            public void onCheckComplete(boolean exists) {
                if (exists) {
                    navigateSingleTop(R.id.navigation_profile);
                } else {
                    startActivity(new Intent(MainActivity.this, CreateProfileActivity.class));
                    bottomNav.setSelectedItemId(R.id.navigation_browse);
                }
            }

            @Override
            public void onError(Exception e) {
                bottomNav.setSelectedItemId(R.id.navigation_browse);
            }
        });
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        handleIntent(getIntent());
    }

    /**
     * FIXED: Handles all types of intents - invitations, notifications, events, and deep links
     *
     * Supports:
     * 1. ACTION_OPEN_INVITATION: Opens invitation dialog for lottery wins
     * 2. ACTION_OPEN_NOTIFICATIONS: Navigates to invitations/notifications screen
     * 3. ACTION_OPEN_EVENT: Opens event details
     * 4. Generic intent with eventId: Opens invitation dialog
     * 5. Deep link (eventlottery://event/{eventId}): Opens event details
     */
    private void handleIntent(@Nullable Intent i) {
        if (i == null) return;

        String action = i.getAction();
        String eventId = i.getStringExtra(Constants.EXTRA_EVENT_ID);

        // 1. Handle invitation intent (lottery win notification tap)
        if (Constants.ACTION_OPEN_INVITATION.equals(action)) {
            String invId = i.getStringExtra(Constants.EXTRA_INVITATION_ID);
            long deadline = 0L;
            try {
                String deadlineStr = i.getStringExtra(Constants.EXTRA_DEADLINE);
                if (deadlineStr != null) {
                    deadline = Long.parseLong(deadlineStr);
                }
            } catch (Exception ignored) {}

            Log.d(TAG, "Opening invitation dialog for event: " + eventId);
            com.example.pickme.ui.invitations.InvitationDialogFragment
                    .newInstance(eventId, invId, deadline)
                    .show(getSupportFragmentManager(), "invite");

            clearIntent(i);
            return;
        }

        // 2. Handle notifications screen intent (loss/message notification tap)
        if (Constants.ACTION_OPEN_NOTIFICATIONS.equals(action)) {
            Log.d(TAG, "Opening invitations/notifications screen");
            // Navigate to invitations tab
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.navigation_invitations);
            }
            clearIntent(i);
            return;
        }

        // 3. Handle event details intent
        if (Constants.ACTION_OPEN_EVENT.equals(action) && eventId != null) {
            Log.d(TAG, "Opening event details for: " + eventId);
            Intent eventIntent = new Intent(this, EventDetailsActivity.class);
            eventIntent.putExtra(Constants.EXTRA_EVENT_ID, eventId);
            startActivity(eventIntent);
            clearIntent(i);
            return;
        }

        // 4. Handle generic notification with eventId but no specific action
        if (eventId != null && !eventId.isEmpty() && action == null) {
            Log.d(TAG, "Generic notification tap with eventId: " + eventId);
            // Default: navigate to invitations screen
            if (bottomNav != null) {
                bottomNav.setSelectedItemId(R.id.navigation_invitations);
            }
            clearIntent(i);
            return;
        }

        // 5. Handle QR code deep link: eventlottery://event/{eventId}
        android.net.Uri data = i.getData();
        if (data != null && Constants.DEEP_LINK_SCHEME.equals(data.getScheme())
                && Constants.DEEP_LINK_HOST_EVENT.equals(data.getHost())) {
            String path = data.getPath();
            if (path != null && path.startsWith("/")) {
                String deepLinkEventId = path.substring(1);
                if (!deepLinkEventId.isEmpty()) {
                    Log.d(TAG, "Deep link scanned for event: " + deepLinkEventId);
                    Intent eventIntent = new Intent(this, EventDetailsActivity.class);
                    eventIntent.putExtra(Constants.EXTRA_EVENT_ID, deepLinkEventId);
                    startActivity(eventIntent);
                }
            }
            clearIntent(i);
        }
    }

    /**
     * Clear intent data to prevent re-processing on configuration change
     */
    private void clearIntent(Intent i) {
        if (i != null) {
            i.setAction(null);
            i.removeExtra(Constants.EXTRA_EVENT_ID);
            i.removeExtra(Constants.EXTRA_INVITATION_ID);
            i.removeExtra(Constants.EXTRA_DEADLINE);
            i.setData(null);
        }
    }

    @Override
    public void onRoleChanged(String newRole) {
        currentRole = newRole;
        setupNavigationForRole(newRole);
    }

    /**
     * Setup navigation graph and menu based on user role
     */
    private void setupNavigationForRole(String role) {
        if (navController != null && destinationChangedListener != null) {
            navController.removeOnDestinationChangedListener(destinationChangedListener);
        }

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

        bottomNav.getMenu().clear();
        bottomNav.inflateMenu(menuResId);

        NavInflater inflater = navController.getNavInflater();
        NavGraph graph = inflater.inflate(navGraphResId);
        graph.setStartDestination(startDestination);
        navController.setGraph(graph);

        destinationChangedListener = (controller, destination, arguments) -> {
            int destId = destination.getId();
            MenuItem menuItem = bottomNav.getMenu().findItem(destId);
            if (menuItem != null) {
                menuItem.setChecked(true);
            }
        };
        navController.addOnDestinationChangedListener(destinationChangedListener);

        setupBottomNavListener();
    }

    /**
     * Setup bottom navigation item selection listener
     */
    private void setupBottomNavListener() {
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.navigation_browse) {
                navigateSingleTop(R.id.navigation_browse);
                return true;
            }

            if (id == R.id.navigation_profile) {
                gateProfileThenNavigate();
                return true;
            }

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