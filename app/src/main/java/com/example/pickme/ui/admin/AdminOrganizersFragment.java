package com.example.pickme.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.pickme.R;
import com.example.pickme.models.Profile;
import com.example.pickme.repositories.ProfileRepository;
import com.example.pickme.utils.AdminUtils;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminOrganizersFragment - Browse and remove organizer profiles
 * Shows only profiles with the "organizer" role
 *
 * UPDATED: Now deletes organizer AND all their events using AdminUtils
 *
 * Related User Stories: US 03.02.01, US 03.07.01, US 03.07.02
 */
public class AdminOrganizersFragment extends Fragment {

    private static final String TAG = "AdminOrganizersFragment";

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;
    private TextInputEditText etSearch;

    private ProfileRepository profileRepository;
    private AdminProfileAdapter adapter;
    private List<Profile> allOrganizers = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_admin_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        recyclerView = view.findViewById(R.id.recyclerView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyState = view.findViewById(R.id.emptyState);
        etSearch = view.findViewById(R.id.etSearch);

        // Set hint for search
        if (etSearch != null) {
            etSearch.setHint("Search organizers...");
        }

        profileRepository = new ProfileRepository();
        setupRecyclerView();
        setupSearch();
        loadOrganizers();
    }

    private void setupRecyclerView() {
        adapter = new AdminProfileAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnProfileClickListener(profile -> {
            showDeleteConfirmation(profile);
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterOrganizers(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadOrganizers() {
        progressBar.setVisibility(View.VISIBLE);
        Log.d(TAG, "Loading all profiles to filter organizers...");

        profileRepository.getAllProfiles(new ProfileRepository.OnProfilesLoadedListener() {
            @Override
            public void onProfilesLoaded(List<Profile> profiles) {
                progressBar.setVisibility(View.GONE);

                // Filter to only organizers
                allOrganizers = profiles.stream()
                        .filter(profile -> Profile.ROLE_ORGANIZER.equals(profile.getRole()))
                        .collect(Collectors.toList());

                Log.d(TAG, "Found " + allOrganizers.size() + " organizers out of " + profiles.size() + " total profiles");

                adapter.setProfiles(allOrganizers);

                if (allOrganizers.isEmpty()) {
                    emptyState.setVisibility(View.VISIBLE);
                    recyclerView.setVisibility(View.GONE);
                } else {
                    emptyState.setVisibility(View.GONE);
                    recyclerView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(Exception e) {
                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to load profiles", e);
                Toast.makeText(requireContext(), "Failed to load organizers", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterOrganizers(String query) {
        if (query.isEmpty()) {
            adapter.setProfiles(allOrganizers);
        } else {
            List<Profile> filtered = allOrganizers.stream()
                    .filter(profile -> profile.getName().toLowerCase().contains(query.toLowerCase()) ||
                            (profile.getEmail() != null && profile.getEmail().toLowerCase().contains(query.toLowerCase())))
                    .collect(Collectors.toList());
            adapter.setProfiles(filtered);
        }
    }

    /**
     * Show confirmation dialog before deleting organizer
     * UPDATED: Now warns that all events will be deleted too
     */
    private void showDeleteConfirmation(Profile profile) {
        String name = profile.getName() != null ? profile.getName() : "this organizer";

        new AlertDialog.Builder(requireContext())
                .setTitle("Delete Organizer")
                .setMessage("Are you sure you want to delete " + name + "?\n\n" +
                        "⚠️ WARNING: This will permanently delete:\n" +
                        "• The organizer's profile\n" +
                        "• ALL events they created\n" +
                        "• All waiting lists and entrant data for those events\n\n" +
                        "This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteOrganizer(profile))
                .setNegativeButton(R.string.cancel, null)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    /**
     * Delete organizer and all their events using AdminUtils
     * UPDATED: Now performs cascade delete instead of just changing role
     */
    private void deleteOrganizer(Profile profile) {
        progressBar.setVisibility(View.VISIBLE);
        Toast.makeText(requireContext(), "Deleting organizer and their events...", Toast.LENGTH_SHORT).show();

        AdminUtils.deleteOrganizerWithEvents(profile.getUserId(), new AdminUtils.OnDeleteCompleteListener() {
            @Override
            public void onDeleteComplete(String userId) {
                if (!isAdded()) return; // Fragment might be detached

                progressBar.setVisibility(View.GONE);
                Toast.makeText(requireContext(),
                        "Organizer and all their events deleted successfully",
                        Toast.LENGTH_SHORT).show();
                loadOrganizers(); // Reload the list
            }

            @Override
            public void onError(Exception e) {
                if (!isAdded()) return; // Fragment might be detached

                progressBar.setVisibility(View.GONE);
                Log.e(TAG, "Failed to delete organizer", e);
                Toast.makeText(requireContext(),
                        "Failed to delete organizer: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}