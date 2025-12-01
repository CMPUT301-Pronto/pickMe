package com.example.pickme.ui.admin;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.pickme.R;

/**
 * AdminDashboardFragment - Central hub for admin features in fragment form
 *
 * Features:
 * - Direct access to all admin options
 * - Browse events, profiles, images
 * - View notification logs
 * - Remove organizers
 *
 * Related User Stories: US 03.01.01-08.01
 */
public class AdminDashboardFragment extends Fragment {

    private static final String TAG = "AdminDashboardFrag";

    private Button btnBrowseEvents;
    private Button btnBrowseProfiles;
    private Button btnBrowseImages;
    private Button btnNotificationLogs;
    private Button btnRemoveOrganizer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_admin_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        initializeViews(view);
        setupButtons();
    }

    private void initializeViews(View view) {
        btnBrowseEvents = view.findViewById(R.id.btnBrowseEvents);
        btnBrowseProfiles = view.findViewById(R.id.btnBrowseProfiles);
        btnBrowseImages = view.findViewById(R.id.btnBrowseImages);
        btnNotificationLogs = view.findViewById(R.id.btnNotificationLogs);
        btnRemoveOrganizer = view.findViewById(R.id.btnRemoveOrganizer);
    }

    /**
     * Setup button click listeners
     */
    private void setupButtons() {
        btnBrowseEvents.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AdminEventsActivity.class);
            startActivity(intent);
        });

        btnBrowseProfiles.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AdminProfilesActivity.class);
            startActivity(intent);
        });

        btnBrowseImages.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AdminImagesActivity.class);
            startActivity(intent);
        });

        btnNotificationLogs.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AdminNotificationLogsActivity.class);
            startActivity(intent);
        });

        btnRemoveOrganizer.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AdminOrganizersActivity.class);
            startActivity(intent);
        });
    }
}

