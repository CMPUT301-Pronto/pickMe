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
import com.example.pickme.services.DeviceAuthenticator;
import com.example.pickme.ui.events.EventBrowserActivity;
import com.example.pickme.ui.history.EventHistoryActivity;
import com.example.pickme.ui.invitations.EventInvitationsActivity;
import com.example.pickme.ui.profile.ProfileActivity;

/**
 * MainActivity - Temporary launcher for Phase 5 testing
 *
 * Provides access to all implemented entrant features:
 * - Profile Management
 * - Event Browser
 * - Event Invitations
 * - Event History
 *
 * TODO: Replace with proper home screen UI in later phases
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private TextView tvWelcome;
    private TextView tvUserId;
    private Button btnProfile;
    private Button btnBrowseEvents;
    private Button btnInvitations;
    private Button btnHistory;
    private ProgressBar progressBar;
    private View contentCard;

    private DeviceAuthenticator deviceAuth;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize views
        tvWelcome = findViewById(R.id.tvWelcome);
        tvUserId = findViewById(R.id.tvUserId);
        btnProfile = findViewById(R.id.btnProfile);
        btnBrowseEvents = findViewById(R.id.btnBrowseEvents);
        btnInvitations = findViewById(R.id.btnInvitations);
        btnHistory = findViewById(R.id.btnHistory);
        progressBar = findViewById(R.id.progressBar);
        contentCard = findViewById(R.id.contentCard);

        // Show loading state
        showLoading(true);

        // Initialize device authentication
        deviceAuth = DeviceAuthenticator.getInstance(this);

        // Initialize user (async)
        deviceAuth.initializeUser(new DeviceAuthenticator.OnUserInitializedListener() {
            @Override
            public void onUserInitialized(Profile profile, boolean isNewUser) {
                userId = profile.getUserId();
                Log.d(TAG, "User initialized: " + userId);

                // Update UI on main thread
                runOnUiThread(() -> {
                    showLoading(false);
                    displayUserInfo();
                    setupNavigationButtons();
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

                    // Still allow navigation with fallback
                    userId = "unknown";
                    displayUserInfo();
                    setupNavigationButtons();
                });
            }
        });
    }

    /**
     * Display user information
     */
    private void displayUserInfo() {
        if (userId != null && userId.length() > 8) {
            tvUserId.setText("Device ID: " + userId.substring(0, 8) + "...");
        } else if (userId != null) {
            tvUserId.setText("Device ID: " + userId);
        } else {
            tvUserId.setText("Device ID: Loading...");
        }
    }

    /**
     * Setup navigation button listeners
     */
    private void setupNavigationButtons() {
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
        // Refresh user info when returning to main screen
        if (deviceAuth != null) {
            String currentUserId = deviceAuth.getStoredUserId();
            if (currentUserId != null) {
                userId = currentUserId;
                displayUserInfo();
            }
        }
    }
}