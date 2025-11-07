package com.example.pickme.ui.events;

import android.os.Bundle;
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
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.repositories.ProfileRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * SelectedEntrantsFragment - Display selected entrants (responsePendingList)
 * Related User Stories: US 02.06.01
 */
public class SelectedEntrantsFragment extends Fragment {
    private static final String TAG = "SelectedFragment";
    private static final String ARG_EVENT_ID = "event_id";
    private String eventId;
    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyStateLayout;
    private EventRepository eventRepository;
    private ProfileRepository profileRepository;
    private EntrantAdapter adapter;

    public static SelectedEntrantsFragment newInstance(String eventId) {
        SelectedEntrantsFragment fragment = new SelectedEntrantsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_EVENT_ID, eventId);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            eventId = getArguments().getString(ARG_EVENT_ID);
        }
        eventRepository = new EventRepository();
        profileRepository = new ProfileRepository();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_entrant_list, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.recyclerViewEntrants);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        
        adapter = new EntrantAdapter(false, true); // Don't show join time, show status
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);
        
        loadSelectedList();
    }

    private void loadSelectedList() {
        progressBar.setVisibility(View.VISIBLE);
        recyclerView.setVisibility(View.GONE);
        emptyStateLayout.setVisibility(View.GONE);

        eventRepository.getEntrantIdsFromSubcollection(eventId, "responsePendingList",
                new EventRepository.OnEntrantIdsLoadedListener() {
                    @Override
                    public void onEntrantIdsLoaded(List<String> entrantIds) {
                        Log.d(TAG, "Loaded " + entrantIds.size() + " selected entrants");
                        if (entrantIds.isEmpty()) {
                            progressBar.setVisibility(View.GONE);
                            emptyStateLayout.setVisibility(View.VISIBLE);
                            recyclerView.setVisibility(View.GONE);
                        } else {
                            loadProfiles(entrantIds);
                        }
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Failed to load selected list", e);
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Failed to load selected list",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadProfiles(List<String> entrantIds) {
        List<Profile> profiles = new ArrayList<>();
        int[] remaining = {entrantIds.size()};

        for (String entrantId : entrantIds) {
            profileRepository.getProfile(entrantId, new ProfileRepository.OnProfileLoadedListener() {
                @Override
                public void onProfileLoaded(Profile profile) {
                    profiles.add(profile);
                    remaining[0]--;

                    if (remaining[0] == 0) {
                        progressBar.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.setProfiles(profiles);
                    }
                }

                @Override
                public void onError(Exception e) {
                    Log.w(TAG, "Failed to load profile", e);
                    remaining[0]--;
                    if (remaining[0] == 0) {
                        progressBar.setVisibility(View.GONE);
                        if (profiles.isEmpty()) {
                            emptyStateLayout.setVisibility(View.VISIBLE);
                        } else {
                            recyclerView.setVisibility(View.VISIBLE);
                            adapter.setProfiles(profiles);
                        }
                    }
                }
            });
        }
    }

    public void refresh() {
        if (isAdded()) loadSelectedList();
    }
}

