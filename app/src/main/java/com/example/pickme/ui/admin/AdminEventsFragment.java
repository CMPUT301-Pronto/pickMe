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
import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.ui.events.EventAdapter;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AdminEventsFragment - Browse and manage all events
 * Related User Stories: US 03.04.01, US 03.01.01
 */
public class AdminEventsFragment extends Fragment {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private View emptyState;
    private TextInputEditText etSearch;

    private EventRepository eventRepository;
    private EventAdapter adapter;
    private List<Event> allEvents = new ArrayList<>();

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

        eventRepository = new EventRepository();
        setupRecyclerView();
        setupSearch();
        loadEvents();
    }

    private void setupRecyclerView() {
        adapter = new EventAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(adapter);

        adapter.setOnEventClickListener(event -> {
            // Long press to delete
            showDeleteConfirmation(event);
        });
    }

    private void setupSearch() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterEvents(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void loadEvents() {
        progressBar.setVisibility(View.VISIBLE);
        eventRepository.getAllEvents(new EventRepository.OnEventsLoadedListener() {
            @Override
            public void onEventsLoaded(List<Event> events) {
                progressBar.setVisibility(View.GONE);
                allEvents = events;
                adapter.setEvents(events);

                if (events.isEmpty()) {
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
                Toast.makeText(requireContext(), "Failed to load events", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterEvents(String query) {
        if (query.isEmpty()) {
            adapter.setEvents(allEvents);
        } else {
            List<Event> filtered = allEvents.stream()
                    .filter(event -> event.getName().toLowerCase().contains(query.toLowerCase()) ||
                            (event.getLocation() != null && event.getLocation().toLowerCase().contains(query.toLowerCase())))
                    .collect(Collectors.toList());
            adapter.setEvents(filtered);
        }
    }

    private void showDeleteConfirmation(Event event) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.delete_event_title)
                .setMessage(R.string.delete_event_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteEvent(event))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteEvent(Event event) {
        Toast.makeText(requireContext(), R.string.deleting, Toast.LENGTH_SHORT).show();
        eventRepository.deleteEvent(event.getEventId(),
                id -> {
                    Toast.makeText(requireContext(), R.string.deleted_successfully, Toast.LENGTH_SHORT).show();
                    loadEvents();
                },
                e -> Toast.makeText(requireContext(), R.string.delete_failed, Toast.LENGTH_SHORT).show());
    }
}

