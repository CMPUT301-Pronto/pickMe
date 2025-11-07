package com.example.pickme;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

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
import com.example.pickme.ui.profile.CreateProfileActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import android.view.MenuItem;

/**
 * JAVADOC LLM GENERATED
 *
 * MainActivity
 *
 * The main entry point of the PickMe app. This activity manages the bottom navigation bar,
 * navigation between fragments, user authentication initialization, and profile gating logic.
 *
 * Features:
 * - Hosts the NavHostFragment and handles fragment navigation.
 * - Initializes the device-based user identity.
 * - Syncs bottom navigation tab state with the current destination.
 * - Redirects to CreateProfileActivity when a user has no profile yet.
 * - Handles deep links and invitations via Intent actions.
 */
public class MainActivity extends AppCompatActivity implements RoleChangeListener {

    private DeviceAuthenticator deviceAuth;
    private NavController navController;
    private BottomNavigationView bottomNav;
    private String currentRole = Profile.ROLE_ENTRANT;
    private NavController.OnDestinationChangedListener destinationChangedListener;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Layout must include: NavHostFragment(id=nav_host_fragment) + BottomNavigationView(id=bottom_nav)
        setContentView(R.layout.activity_main);

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
     * Handles deep links or custom intents used to open invitation dialogs.
     *
     * @param i The intent received by the activity.
     */
    private void handleInviteIntent(@Nullable Intent i) {
        if (i == null) return;
        if (!"com.example.pickme.ACTION_OPEN_INVITATION".equals(i.getAction())) return;

        String eventId = i.getStringExtra("eventId");
        String invId = i.getStringExtra("invitationId");
        long deadline = 0L;

        try {
            deadline = Long.parseLong(i.getStringExtra("deadline"));
        } catch (Exception ignored) {}

        com.example.pickme.ui.invitations.InvitationDialogFragment
                .newInstance(eventId, invId, deadline)
                .show(getSupportFragmentManager(), "invite");
    }

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

