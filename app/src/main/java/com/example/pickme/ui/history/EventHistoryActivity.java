package com.example.pickme.ui.history;

import android.os.Bundle;
import android.util.Log;
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

    // Store events data until fragments are ready
    private List<Event> upcomingEvents = new ArrayList<>();
    private List<Event> waitingEvents = new ArrayList<>();
    private List<Event> pastEvents = new ArrayList<>();
    private List<Event> cancelledEvents = new ArrayList<>();

    // Fragments per tab - these will be populated by ViewPager2
    private EventListFragment upcomingFragment;
    private EventListFragment waitingFragment;
    private EventListFragment pastFragment;
    private EventListFragment cancelledFragment;

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
     * Initializes repository/auth and grabs current user id.
     * <p>Preconditions: DeviceAuthenticator must have stored a user id earlier in app flow.</p>
     */
    private void initializeData() {
        eventRepository = new EventRepository();
        deviceAuthenticator = DeviceAuthenticator.getInstance(this);
        currentUserId = deviceAuthenticator.getStoredUserId();
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
        Log.d(TAG, "loadEventHistory() called");

        // Show loading on fragments if they exist
        if (upcomingFragment != null) upcomingFragment.showLoading(true);
        if (waitingFragment != null) waitingFragment.showLoading(true);
        if (pastFragment != null) pastFragment.showLoading(true);
        if (cancelledFragment != null) cancelledFragment.showLoading(true);

        // Track completion of all queries
        final boolean[] queriesComplete = {false, false, false, false}; // Updated to 4

        // Query 1: Upcoming events (user in inEventList)
        eventRepository.getEventsWhereEntrantInEventList(currentUserId,
                new EventRepository.OnEventsWithMetadataLoadedListener() {
                    @Override
                    public void onEventsLoaded(List<Event> events, Map<String, Object> metadata) {
                        // Filter out completed/cancelled events
                        upcomingEvents = new ArrayList<>();
                        for (Event event : events) {
                            if (!event.isCompleted() && !event.isCancelled()) {
                                upcomingEvents.add(event);
                            }
                        }

                        Log.d(TAG, "Loaded " + upcomingEvents.size() + " upcoming events");
                        updateFragmentEvents(0, upcomingEvents);
                        queriesComplete[0] = true;
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error loading upcoming events", e);
                        upcomingEvents = new ArrayList<>();
                        updateFragmentEvents(0, upcomingEvents);
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
                        Log.d(TAG, "Waiting list query returned " + events.size() + " events");

                        // Filter out completed/cancelled events
                        waitingEvents = new ArrayList<>();
                        for (Event event : events) {
                            Log.d(TAG, "Processing event: " + event.getName() +
                                  ", completed=" + event.isCompleted() +
                                  ", cancelled=" + event.isCancelled());
                            if (!event.isCompleted() && !event.isCancelled()) {
                                waitingEvents.add(event);
                            }
                        }

                        Log.d(TAG, "After filtering: " + waitingEvents.size() + " waiting events");
                        updateFragmentEvents(1, waitingEvents);
                        queriesComplete[1] = true;
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error loading waiting list events", e);
                        waitingEvents = new ArrayList<>();
                        updateFragmentEvents(1, waitingEvents);
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
                pastEvents = new ArrayList<>();
                for (Event event : allEvents) {
                    if (event.isCompleted() || event.isCancelled()) {
                        pastEvents.add(event);
                    }
                }

                Log.d(TAG, "Loaded " + pastEvents.size() + " past events");
                updateFragmentEvents(2, pastEvents);
                queriesComplete[2] = true;
            }

            @Override
            public void onError(Exception e) {
                Log.e(TAG, "Error loading past events", e);
                pastEvents = new ArrayList<>();
                updateFragmentEvents(2, pastEvents);
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
                        cancelledEvents = new ArrayList<>(events);

                        Log.d(TAG, "Loaded " + cancelledEvents.size() + " cancelled events");
                        updateFragmentEvents(3, cancelledEvents);
                        queriesComplete[3] = true;
                    }

                    @Override
                    public void onError(Exception e) {
                        Log.e(TAG, "Error loading declined events", e);
                        cancelledEvents = new ArrayList<>();
                        updateFragmentEvents(3, cancelledEvents);
                        queriesComplete[3] = true;
                        Toast.makeText(EventHistoryActivity.this,
                                "Error loading declined events: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    /**
     * Helper method to update fragment events or store them for later if fragment doesn't exist yet
     */
    private void updateFragmentEvents(int position, List<Event> events) {
        EventListFragment fragment = getFragmentAtPosition(position);

        if (fragment != null) {
            Log.d(TAG, "updateFragmentEvents: Setting " + events.size() +
                  " events to fragment at position " + position);
            fragment.setEvents(events);
            fragment.showLoading(false);
        } else {
            Log.d(TAG, "updateFragmentEvents: Fragment at position " + position +
                  " not created yet. " + events.size() + " events stored for later.");
            // Events are already stored in the class variables (upcomingEvents, waitingEvents, etc.)
            // When fragment is created, we'll need to set them
        }
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
            Log.d(TAG, "createFragment called for position=" + position);
            EventListFragment fragment;
            List<Event> eventsToSet = null;

            switch (position) {
                case 0:
                    fragment = EventListFragment.newInstance(EventListFragment.TAB_UPCOMING);
                    upcomingFragment = fragment;
                    eventsToSet = upcomingEvents;
                    break;
                case 1:
                    fragment = EventListFragment.newInstance(EventListFragment.TAB_WAITING);
                    waitingFragment = fragment;
                    eventsToSet = waitingEvents;
                    break;
                case 2:
                    fragment = EventListFragment.newInstance(EventListFragment.TAB_PAST);
                    pastFragment = fragment;
                    eventsToSet = pastEvents;
                    break;
                case 3:
                    fragment = EventListFragment.newInstance(EventListFragment.TAB_CANCELLED);
                    cancelledFragment = fragment;
                    eventsToSet = cancelledEvents;
                    break;
                default:
                    fragment = EventListFragment.newInstance(EventListFragment.TAB_UPCOMING);
                    eventsToSet = upcomingEvents;
                    break;
            }

            // If we already have events loaded, set them on the fragment
            if (eventsToSet != null && !eventsToSet.isEmpty()) {
                Log.d(TAG, "Setting " + eventsToSet.size() +
                      " pre-loaded events to newly created fragment at position " + position);
                // We need to set events after the fragment is created and attached
                // Use a post to ensure the fragment is fully initialized
                final List<Event> finalEvents = eventsToSet;
                fragment.getView(); // This will be null until onCreateView is called
                viewPager.post(() -> {
                    fragment.setEvents(finalEvents);
                    fragment.showLoading(false);
                });
            }

            return fragment;
        }
        // Number of taps/pages
        @Override
        public int getItemCount() {
            return 4; // CHANGED from 3 to 4
        }
    }

    /**
     * Helper method to get or find fragment by position
     */
    private EventListFragment getFragmentAtPosition(int position) {
        String tag = "f" + position;
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);
        if (fragment instanceof EventListFragment) {
            return (EventListFragment) fragment;
        }
        return null;
    }
}