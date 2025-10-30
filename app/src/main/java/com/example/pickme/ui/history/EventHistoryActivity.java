package com.example.pickme.ui.history;

import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.repositories.EventRepository;
import com.example.pickme.services.DeviceAuthenticator;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.util.ArrayList;
import java.util.List;

/**
 * EventHistoryActivity - Display user's event history
 *
 * Shows three tabs:
 * 1. Upcoming Events - Accepted invitations (user in inEventList)
 * 2. Waiting Lists - Currently joined (user in waitingList)
 * 3. Past Events - Completed/cancelled events
 *
 * Related User Stories: US 01.02.03
 */
public class EventHistoryActivity extends AppCompatActivity {

    private static final String TAG = "EventHistoryActivity";

    // UI Components
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    // Data
    private EventRepository eventRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private String currentUserId;

    // Fragments
    private EventListFragment upcomingFragment;
    private EventListFragment waitingFragment;
    private EventListFragment pastFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_event_history);

        // Setup toolbar
        setSupportActionBar(findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize
        initializeViews();
        initializeData();
        setupViewPager();

        // Load history
        loadEventHistory();
    }

    /**
     * Initialize view references
     */
    private void initializeViews() {
        tabLayout = findViewById(R.id.tabLayout);
        viewPager = findViewById(R.id.viewPager);
    }

    /**
     * Initialize data components
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        currentUserId = deviceAuthenticator.getStoredUserId();

        // Create fragments
        upcomingFragment = EventListFragment.newInstance(EventListFragment.TAB_UPCOMING);
        waitingFragment = EventListFragment.newInstance(EventListFragment.TAB_WAITING);
        pastFragment = EventListFragment.newInstance(EventListFragment.TAB_PAST);
    }

    /**
     * Setup ViewPager2 with tabs
     */
    private void setupViewPager() {
        // Create adapter
        EventHistoryPagerAdapter pagerAdapter = new EventHistoryPagerAdapter(this);
        viewPager.setAdapter(pagerAdapter);

        // Connect TabLayout with ViewPager2
        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> {
                    switch (position) {
                        case 0:
                            tab.setText(R.string.tab_upcoming);
                            break;
                        case 1:
                            tab.setText(R.string.tab_waiting);
                            break;
                        case 2:
                            tab.setText(R.string.tab_past);
                            break;
                    }
                }
        ).attach();
    }

    /**
     * Load event history from repositories
     */
    private void loadEventHistory() {
        // Show loading on all fragments
        upcomingFragment.showLoading(true);
        waitingFragment.showLoading(true);
        pastFragment.showLoading(true);

        // Load all events and categorize them
        eventRepository.getAllEvents(new EventRepository.OnEventsLoadedListener() {
            @Override
            public void onEventsLoaded(List<Event> allEvents) {
                categorizeEvents(allEvents);
            }

            @Override
            public void onError(Exception e) {
                upcomingFragment.showLoading(false);
                waitingFragment.showLoading(false);
                pastFragment.showLoading(false);

                Toast.makeText(EventHistoryActivity.this,
                        getString(R.string.error_occurred) + ": " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Categorize events into upcoming, waiting, and past
     */
    private void categorizeEvents(List<Event> allEvents) {
        // TODO: Implement proper Firestore queries for event categorization
        // Proper implementation requires adding query methods to EventRepository:
        // - getEventsWhereEntrantInEventList(String userId, OnEventsLoadedListener listener)
        // - getEventsWhereEntrantInWaitingList(String userId, OnEventsLoadedListener listener)

        List<Event> upcomingEvents = new ArrayList<>();
        List<Event> waitingEvents = new ArrayList<>();
        List<Event> pastEvents = new ArrayList<>();

        // Placeholder implementation - would query specific subcollections in production
        // For now, just show empty lists

        upcomingFragment.setEvents(upcomingEvents);
        upcomingFragment.showLoading(false);

        waitingFragment.setEvents(waitingEvents);
        waitingFragment.showLoading(false);

        pastFragment.setEvents(pastEvents);
        pastFragment.showLoading(false);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /**
     * ViewPager2 adapter for event history tabs
     */
    private class EventHistoryPagerAdapter extends FragmentStateAdapter {

        public EventHistoryPagerAdapter(@NonNull FragmentActivity fragmentActivity) {
            super(fragmentActivity);
        }

        @NonNull
        @Override
        public Fragment createFragment(int position) {
            switch (position) {
                case 0:
                    return upcomingFragment;
                case 1:
                    return waitingFragment;
                case 2:
                    return pastFragment;
                default:
                    return upcomingFragment;
            }
        }

        @Override
        public int getItemCount() {
            return 3;
        }
    }
}

