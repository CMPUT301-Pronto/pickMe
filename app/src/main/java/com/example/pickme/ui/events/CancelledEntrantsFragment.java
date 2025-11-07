package com.example.pickme.ui.events;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.pickme.R;

/**
 * CancelledEntrantsFragment - Display cancelled entrants
 * Related User Stories: US 02.06.03
 */
public class CancelledEntrantsFragment extends Fragment {
    private static final String ARG_EVENT_ID = "event_id";
    private String eventId;

    public static CancelledEntrantsFragment newInstance(String eventId) {
        CancelledEntrantsFragment fragment = new CancelledEntrantsFragment();
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
        RecyclerView recyclerView = view.findViewById(R.id.recyclerViewEntrants);
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        view.findViewById(R.id.emptyStateLayout).setVisibility(View.VISIBLE);
    }

    public void refresh() {}
}

