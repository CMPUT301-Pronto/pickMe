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
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminOrganizersFragment - Browse and remove organizer profiles
 * Shows only profiles with the "organizer" role
 * Related User Stories: US 03.02.01
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

    private void showDeleteConfirmation(Profile profile) {
        new AlertDialog.Builder(requireContext())
                .setTitle("Remove Organizer")
                .setMessage("Are you sure you want to remove this organizer? This will change their role to entrant and they will no longer be able to create events.")
                .setPositiveButton(R.string.delete, (dialog, which) -> removeOrganizer(profile))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void removeOrganizer(Profile profile) {
        Toast.makeText(requireContext(), "Removing organizer...", Toast.LENGTH_SHORT).show();

        // Change the user's role to entrant instead of deleting the profile
        java.util.Map<String, Object> updates = new java.util.HashMap<>();
        updates.put("role", Profile.ROLE_ENTRANT);

        profileRepository.updateProfile(profile.getUserId(), updates,
                id -> {
                    Toast.makeText(requireContext(), "Organizer role removed successfully", Toast.LENGTH_SHORT).show();
                    loadOrganizers(); // Reload the list
                },
                e -> {
                    Log.e(TAG, "Failed to remove organizer", e);
                    Toast.makeText(requireContext(), "Failed to remove organizer: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }
}

