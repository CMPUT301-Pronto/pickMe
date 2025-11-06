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
import java.util.Map;


/**
 * JAVADOCS LLM GENERATED
 *
 * # EventHistoryActivity
 * Hosts a 4-tab history dashboard for the current user:
 * <ol>
 *   <li><b>Upcoming</b> — accepted invitations (user in {@code inEventList})</li>
 *   <li><b>Waiting</b> — events joined but not yet drawn (user in {@code waitingList})</li>
 *   <li><b>Past</b> — completed or organizer-cancelled events</li>
 *   <li><b>Cancelled</b> — invitations the user declined ({@code cancelledList})</li>
 * </ol>
 *
 * <p><b>Design</b>:
 * Uses a ViewPager2 + TabLayout with four {@link EventListFragment} instances. Data loads via
 * {@link EventRepository} and user id is provided by {@link DeviceAuthenticator}.</p>
 *
 * <p><b>Notes</b>:
 * <ul>
 *   <li>Past tab uses {@link EventRepository#getAllEvents} and filters client-side.</li>
 *   <li>Other tabs rely on collection-group queries exposed by the repository.</li>
 *   <li>Error handling is per-tab; failures do not block other tabs.</li>
 * </ul>
 *
 * <p><b>Related US</b>: US 01.02.03, US 01.05.03</p>
 * Related User Stories: US 01.02.03, US 01.05.03
 */
public class EventHistoryActivity extends AppCompatActivity {

    private static final String TAG = "EventHistoryActivity";

    // UI Components
    private TabLayout tabLayout;
    private ViewPager2 viewPager;

    // Data/services
    private EventRepository eventRepository;
    private DeviceAuthenticator deviceAuthenticator;
    private String currentUserId;

    // Fragments per tab
    private EventListFragment upcomingFragment;
    private EventListFragment waitingFragment;
    private EventListFragment pastFragment;
    private EventListFragment cancelledFragment; // NEW: For declined invitations
    /**
     * Activity entry point: initializes UI, data dependencies, pager, and triggers loads.
     */
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
     * Initializes repository/auth, grabs current user id, and constructs tab fragments.
     * <p>Preconditions: DeviceAuthenticator must have stored a user id earlier in app flow.</p>
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        currentUserId = deviceAuthenticator.getStoredUserId();

        // Create 4 tabs fragments
        upcomingFragment = EventListFragment.newInstance(EventListFragment.TAB_UPCOMING);
        waitingFragment = EventListFragment.newInstance(EventListFragment.TAB_WAITING);
        pastFragment = EventListFragment.newInstance(EventListFragment.TAB_PAST);
        cancelledFragment = EventListFragment.newInstance(EventListFragment.TAB_CANCELLED); // NEW
        cancelledFragment = EventListFragment.newInstance(EventListFragment.TAB_CANCELLED);
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
                        case 3: // NEW
                            tab.setText(R.string.tab_cancelled);
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
        cancelledFragment.showLoading(true); // NEW

        // Track completion of all queries
        final boolean[] queriesComplete = {false, false, false, false}; // Updated to 4

        // Query 1: Upcoming events (user in inEventList)
        eventRepository.getEventsWhereEntrantInEventList(currentUserId,
                new EventRepository.OnEventsWithMetadataLoadedListener() {
                    @Override
                    public void onEventsLoaded(List<Event> events, Map<String, Object> metadata) {
                        // Filter out completed/cancelled events
                        List<Event> upcomingEvents = new ArrayList<>();
                        for (Event event : events) {
                            if (!event.isCompleted() && !event.isCancelled()) {
                                upcomingEvents.add(event);
                            }
                        }

                        upcomingFragment.setEvents(upcomingEvents);
                        upcomingFragment.showLoading(false);
                        queriesComplete[0] = true;
                    }

                    @Override
                    public void onError(Exception e) {
                        upcomingFragment.setEvents(new ArrayList<>());
                        upcomingFragment.showLoading(false);
                        queriesComplete[0] = true;
                        Toast.makeText(EventHistoryActivity.this,
                                "Error loading upcoming events: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        // Query 2: Waiting list events (user in waitingList)
        eventRepository.getEventsWhereEntrantInWaitingList(currentUserId,
                new EventRepository.OnEventsWithMetadataLoadedListener() {
                    @Override
                    public void onEventsLoaded(List<Event> events, Map<String, Object> metadata) {
                        // Filter out completed/cancelled events
                        List<Event> waitingEvents = new ArrayList<>();
                        for (Event event : events) {
                            if (!event.isCompleted() && !event.isCancelled()) {
                                waitingEvents.add(event);
                            }
                        }

                        waitingFragment.setEvents(waitingEvents);
                        waitingFragment.showLoading(false);
                        queriesComplete[1] = true;
                    }

                    @Override
                    public void onError(Exception e) {
                        waitingFragment.setEvents(new ArrayList<>());
                        waitingFragment.showLoading(false);
                        queriesComplete[1] = true;
                        Toast.makeText(EventHistoryActivity.this,
                                "Error loading waiting list: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });

        // Query 3: Past events (completed or cancelled)
        eventRepository.getAllEvents(new EventRepository.OnEventsLoadedListener() {
            @Override
            public void onEventsLoaded(List<Event> allEvents) {
                List<Event> pastEvents = new ArrayList<>();
                for (Event event : allEvents) {
                    if (event.isCompleted() || event.isCancelled()) {
                        pastEvents.add(event);
                    }
                }

                pastFragment.setEvents(pastEvents);
                pastFragment.showLoading(false);
                queriesComplete[2] = true;
            }

            @Override
            public void onError(Exception e) {
                pastFragment.setEvents(new ArrayList<>());
                pastFragment.showLoading(false);
                queriesComplete[2] = true;
                Toast.makeText(EventHistoryActivity.this,
                        "Error loading past events: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });

        // Query 4: Cancelled/Declined events (user in cancelledList) - NEW
        eventRepository.getEventsWhereEntrantDeclined(currentUserId,
                new EventRepository.OnEventsWithMetadataLoadedListener() {
                    @Override
                    public void onEventsLoaded(List<Event> events, Map<String, Object> metadata) {
                        // These are events the user declined
                        cancelledFragment.setEvents(events);
                        cancelledFragment.showLoading(false);

                        // Optionally, you can add metadata like when they declined
                        for (Event event : events) {
                            Object declinedAt = metadata.get(event.getEventId() + "_declinedAt");
                            // You could pass this to the fragment if needed
                        }

                        queriesComplete[3] = true;
                    }

                    @Override
                    public void onError(Exception e) {
                        cancelledFragment.setEvents(new ArrayList<>());
                        cancelledFragment.showLoading(false);
                        queriesComplete[3] = true;
                        Toast.makeText(EventHistoryActivity.this,
                                "Error loading declined events: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
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
        /**
         * Pager adapter binding the four tab fragments.
         * <p>Positions: 0=Upcoming, 1=Waiting, 2=Past, 3=Cancelled.</p>
         */
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
                case 3: // NEW
                    return cancelledFragment;
                default:
                    return upcomingFragment;
            }
        }
        // Number of taps/pages
        @Override
        public int getItemCount() {
            return 4; // CHANGED from 3 to 4
        }
    }
}