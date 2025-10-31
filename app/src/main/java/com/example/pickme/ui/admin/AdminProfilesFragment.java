package com.example.pickme.ui.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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
 * AdminProfilesFragment - Browse and manage all profiles
 * Related User Stories: US 03.05.01, US 03.02.01
 */
public class AdminProfilesFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;
    private TextInputEditText etSearch;

    private ProfileRepository profileRepository;
    private AdminProfileAdapter adapter;
    private List<Profile> allProfiles = new ArrayList<>();

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

        profileRepository = new ProfileRepository();
        setupRecyclerView();
        setupSearch();
        loadProfiles();
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
                filterProfiles(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadProfiles() {
        progressBar.setVisibility(View.VISIBLE);
        profileRepository.getAllProfiles(new ProfileRepository.OnProfilesLoadedListener() {
            @Override
            public void onProfilesLoaded(List<Profile> profiles) {
                progressBar.setVisibility(View.GONE);
                allProfiles = profiles;
                adapter.setProfiles(profiles);

                if (profiles.isEmpty()) {
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
                Toast.makeText(requireContext(), "Failed to load profiles", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterProfiles(String query) {
        if (query.isEmpty()) {
            adapter.setProfiles(allProfiles);
        } else {
            List<Profile> filtered = allProfiles.stream()
                    .filter(profile -> profile.getName().toLowerCase().contains(query.toLowerCase()) ||
                            (profile.getEmail() != null && profile.getEmail().toLowerCase().contains(query.toLowerCase())))
                    .collect(Collectors.toList());
            adapter.setProfiles(filtered);
        }
    }

    private void showDeleteConfirmation(Profile profile) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_profile_title)
                .setMessage(R.string.delete_profile_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteProfile(profile))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteProfile(Profile profile) {
        Toast.makeText(requireContext(), R.string.deleting, Toast.LENGTH_SHORT).show();
        profileRepository.deleteProfile(profile.getUserId(),
                id -> {
                    Toast.makeText(requireContext(), R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                    loadProfiles();
                },
                e -> Toast.makeText(requireContext(), R.string.delete_failed, Toast.LENGTH_SHORT).show());
    }
}

