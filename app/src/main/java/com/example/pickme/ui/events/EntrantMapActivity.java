package com.example.pickme.ui.events;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.example.pickme.R;
import com.example.pickme.models.Event;
import com.example.pickme.models.Geolocation;
import com.example.pickme.repositories.EventRepository;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EntrantMapActivity - Display entrant locations on a Google Map
 *
 * Shows markers for each entrant with geolocation data from different categories.
 * Organizers can filter by category: waiting list, selected, enrolled, or cancelled.
 *
 * Features:
 * - Google Maps with zoom and pan controls
 * - Filter chips to select user category
 * - Markers for each entrant location with different colors per category
 * - Marker info window shows entrant name
 * - Auto-zoom to fit all markers
 * - Handles events without geolocation enabled
 *
 * Related User Stories: US 02.02.01, US 02.02.03
 */
public class EntrantMapActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String TAG = "EntrantMapActivity";
    public static final String EXTRA_EVENT_ID = "event_id";

    /**
     * Enum for different user categories/lists
     */
    private enum UserCategory {
        WAITING_LIST("waitingList", "Waiting List", BitmapDescriptorFactory.HUE_BLUE),
        RESPONSE_PENDING("responsePendingList", "Selected", BitmapDescriptorFactory.HUE_ORANGE),
        IN_EVENT("inEventList", "Enrolled", BitmapDescriptorFactory.HUE_GREEN),
        CANCELLED("cancelledList", "Cancelled", BitmapDescriptorFactory.HUE_RED);

        final String collectionName;
        final String displayName;
        final float markerHue;

        UserCategory(String collectionName, String displayName, float markerHue) {
            this.collectionName = collectionName;
            this.displayName = displayName;
            this.markerHue = markerHue;
        }
    }

    // UI Components
    private GoogleMap googleMap;
    private ProgressBar progressBar;
    private View layoutNoLocationData;
    private TextView tvNoLocationMessage;
    private TextView tvEntrantCount;
    private ChipGroup chipGroupFilters;
    private Chip chipWaitingList, chipResponsePending, chipInEvent, chipCancelled;

    // Data
    private String eventId;
    private Event currentEvent;
    private EventRepository eventRepository;
    private FirebaseFirestore db;
    private UserCategory currentCategory = UserCategory.WAITING_LIST;

    // Entrant location data
    private Map<String, EntrantLocation> entrantLocations = new HashMap<>();
    private List<Marker> markers = new ArrayList<>();

    /**
     * Data class to hold entrant location info
     */
    private static class EntrantLocation {
        String odId;
        String name;
        double latitude;
        double longitude;

        EntrantLocation(String odId, String name, double latitude, double longitude) {
            this.odId = odId;
            this.name = name;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_entrant_map);

        // Get event ID from intent
        eventId = getIntent().getStringExtra(EXTRA_EVENT_ID);
        if (eventId == null) {
            Toast.makeText(this, "Event ID is required", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initializeViews();
        initializeData();
        setupToolbar();
        setupMap();

        // Load event first to check if geolocation is enabled
        loadEvent();
    }

    private void initializeViews() {
        progressBar = findViewById(R.id.progressBar);
        layoutNoLocationData = findViewById(R.id.tvNoLocationData);
        tvNoLocationMessage = findViewById(R.id.tvNoLocationMessage);
        tvEntrantCount = findViewById(R.id.tvEntrantCount);

        // Initialize filter chips
        chipGroupFilters = findViewById(R.id.chipGroupFilters);
        chipWaitingList = findViewById(R.id.chipWaitingList);
        chipResponsePending = findViewById(R.id.chipResponsePending);
        chipInEvent = findViewById(R.id.chipInEvent);
        chipCancelled = findViewById(R.id.chipCancelled);

        setupFilterChips();
    }

    private void initializeData() {
        eventRepository = new EventRepository();
        db = FirebaseFirestore.getInstance();
    }

    /**
     * Setup filter chip listeners
     */
    private void setupFilterChips() {
        chipGroupFilters.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) {
                return; // Prevent no selection
            }

            int checkedId = checkedIds.get(0);
            UserCategory newCategory = null;

            if (checkedId == R.id.chipWaitingList) {
                newCategory = UserCategory.WAITING_LIST;
            } else if (checkedId == R.id.chipResponsePending) {
                newCategory = UserCategory.RESPONSE_PENDING;
            } else if (checkedId == R.id.chipInEvent) {
                newCategory = UserCategory.IN_EVENT;
            } else if (checkedId == R.id.chipCancelled) {
                newCategory = UserCategory.CANCELLED;
            }

            if (newCategory != null && newCategory != currentCategory) {
                currentCategory = newCategory;
                onCategoryChanged();
            }
        });
    }

    /**
     * Called when user selects a different category filter
     */
    private void onCategoryChanged() {
        // Clear existing data
        entrantLocations.clear();

        // Hide no data message
        layoutNoLocationData.setVisibility(View.GONE);
        tvEntrantCount.setVisibility(View.GONE);

        // Reload data for new category
        if (currentEvent != null && currentEvent.isGeolocationRequired()) {
            loadEntrantLocations();
        }
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.entrant_map_title);
        }
    }

    private void setupMap() {
        // Get the map fragment and initialize
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    /**
     * Load event to check geolocation requirement
     */
    private void loadEvent() {
        showLoading(true);

        eventRepository.getEvent(eventId, new EventRepository.OnEventLoadedListener() {
            @Override
            public void onEventLoaded(Event event) {
                currentEvent = event;

                if (getSupportActionBar() != null) {
                    getSupportActionBar().setSubtitle(event.getName());
                }

                // Check if geolocation was enabled for this event
                if (!event.isGeolocationRequired()) {
                    showNoLocationData(getString(R.string.geolocation_not_enabled));
                    showLoading(false);
                } else {
                    // Geolocation is enabled, load entrant locations
                    loadEntrantLocations();
                }
            }

            @Override
            public void onError(Exception e) {
                showLoading(false);
                Toast.makeText(EntrantMapActivity.this,
                        "Failed to load event: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    /**
     * Load entrant locations from the selected category subcollection
     */
    private void loadEntrantLocations() {
        db.collection("events")
                .document(eventId)
                .collection(currentCategory.collectionName)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        showNoLocationData(getString(R.string.no_entrants_on_waiting_list));
                        showLoading(false);
                        return;
                    }

                    // Extract entrant IDs and their locations
                    List<String> entrantIds = new ArrayList<>();
                    Map<String, Geolocation> locationMap = new HashMap<>();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        String odId = doc.getId();
                        entrantIds.add(odId);

                        // Check if document has geolocation data
                        if (doc.contains("latitude") && doc.contains("longitude")) {
                            Double lat = doc.getDouble("latitude");
                            Double lng = doc.getDouble("longitude");
                            if (lat != null && lng != null) {
                                Geolocation geo = new Geolocation(lat, lng);
                                locationMap.put(odId, geo);
                            }
                        } else if (doc.contains("geolocation")) {
                            // Alternative structure: nested geolocation object
                            Map<String, Object> geoData = (Map<String, Object>) doc.get("geolocation");
                            if (geoData != null) {
                                Double lat = (Double) geoData.get("latitude");
                                Double lng = (Double) geoData.get("longitude");
                                if (lat != null && lng != null) {
                                    Geolocation geo = new Geolocation(lat, lng);
                                    locationMap.put(odId, geo);
                                }
                            }
                        }
                    }

                    if (locationMap.isEmpty()) {
                        showNoLocationData(getString(R.string.no_location_data_available));
                        showLoading(false);
                        return;
                    }

                    // Load profile names for entrants with locations
                    loadEntrantProfiles(locationMap);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to load " + currentCategory.displayName, e);
                    showLoading(false);
                    Toast.makeText(this, "Failed to load entrants", Toast.LENGTH_SHORT).show();
                });
    }

    /**
     * Load profile information for entrants to get their names
     */
    private void loadEntrantProfiles(Map<String, Geolocation> locationMap) {
        List<String> userIds = new ArrayList<>(locationMap.keySet());
        final int[] loadedCount = {0};
        final int totalCount = userIds.size();

        for (String odId : userIds) {
            Geolocation geo = locationMap.get(odId);

            // Load profile directly from Firestore
            db.collection("profiles").document(odId).get()
                    .addOnSuccessListener(documentSnapshot -> {
                        String name = "Unknown Entrant";
                        if (documentSnapshot.exists()) {
                            String profileName = documentSnapshot.getString("name");
                            if (profileName != null && !profileName.isEmpty()) {
                                name = profileName;
                            }
                        }

                        entrantLocations.put(odId, new EntrantLocation(
                                odId, name, geo.getLatitude(), geo.getLongitude()
                        ));

                        loadedCount[0]++;
                        checkAllProfilesLoaded(loadedCount[0], totalCount);
                    })
                    .addOnFailureListener(e -> {
                        // Still add the location with unknown name
                        entrantLocations.put(odId, new EntrantLocation(
                                odId, "Unknown Entrant", geo.getLatitude(), geo.getLongitude()
                        ));

                        loadedCount[0]++;
                        checkAllProfilesLoaded(loadedCount[0], totalCount);
                    });
        }
    }

    /**
     * Check if all profiles have been loaded
     */
    private void checkAllProfilesLoaded(int loaded, int total) {
        if (loaded >= total) {
            showLoading(false);
            displayMarkersOnMap();
        }
    }

    /**
     * Called when the map is ready to use
     */
    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;

        // Configure map UI settings
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setZoomGesturesEnabled(true);
        googleMap.getUiSettings().setScrollGesturesEnabled(true);
        googleMap.getUiSettings().setRotateGesturesEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        // Set default camera position (will be updated when markers are added)
        LatLng defaultLocation = new LatLng(53.5461, -113.4938); // Edmonton, AB
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, 10f));

        // If locations are already loaded, display them
        if (!entrantLocations.isEmpty()) {
            displayMarkersOnMap();
        }
    }

    /**
     * Display markers for all entrant locations on the map
     */
    private void displayMarkersOnMap() {
        if (googleMap == null || entrantLocations.isEmpty()) {
            return;
        }

        // Clear existing markers
        for (Marker marker : markers) {
            marker.remove();
        }
        markers.clear();

        // Create bounds builder to fit all markers
        LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();

        // Add markers for each entrant with category-specific color
        for (EntrantLocation entrant : entrantLocations.values()) {
            LatLng position = new LatLng(entrant.latitude, entrant.longitude);

            MarkerOptions markerOptions = new MarkerOptions()
                    .position(position)
                    .title(entrant.name)
                    .snippet(currentCategory.displayName)
                    .icon(BitmapDescriptorFactory.defaultMarker(currentCategory.markerHue));

            Marker marker = googleMap.addMarker(markerOptions);
            if (marker != null) {
                markers.add(marker);
                boundsBuilder.include(position);
            }
        }

        // Update entrant count
        tvEntrantCount.setText(getString(R.string.entrants_on_map, entrantLocations.size()));
        tvEntrantCount.setVisibility(View.VISIBLE);

        // Zoom to fit all markers
        if (markers.size() > 0) {
            try {
                LatLngBounds bounds = boundsBuilder.build();
                int padding = 100; // Padding in pixels

                if (markers.size() == 1) {
                    // Single marker - zoom to it directly
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(
                            markers.get(0).getPosition(), 15f));
                } else {
                    // Multiple markers - fit bounds
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, padding));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error zooming to bounds", e);
            }
        }

        // Set marker click listener to show info window
        googleMap.setOnMarkerClickListener(marker -> {
            marker.showInfoWindow();
            return true;
        });
    }

    /**
     * Show loading state
     */
    private void showLoading(boolean show) {
        progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    /**
     * Show message when no location data is available
     */
    private void showNoLocationData(String message) {
        if (tvNoLocationMessage != null) {
            tvNoLocationMessage.setText(message);
        }
        if (layoutNoLocationData != null) {
            layoutNoLocationData.setVisibility(View.VISIBLE);
        }
        tvEntrantCount.setVisibility(View.GONE);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}