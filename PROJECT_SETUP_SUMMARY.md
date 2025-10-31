# PickMe Event Lottery System - Developer Documentation

**Project**: CMPUT 301 - Event Lottery Mobile Application  
**Last Updated**: October 31, 2025  
**Status**: Phase 1-7.1 Complete (Entrant, Organizer, Admin features implemented)  
**Min SDK**: 34 (Android 14.0) | **Target SDK**: 36

---

## Project Overview

PickMe is an Android lottery event management system allowing organizers to create events, entrants to join waiting lists, and admins to manage the system. Uses Firebase (Firestore, Storage, FCM) with device-based authentication.

**Core Architecture**: Repository Pattern + MVVM-style UI + Firebase Backend

---

## Build Configuration

### Root `build.gradle.kts`
```kotlin
plugins {
    id("com.google.gms.google-services") version "4.4.4" apply false
}
```

### App `build.gradle.kts`
```kotlin
plugins {
    id("com.google.gms.google-services")
}

dependencies {
    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:34.4.0"))
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-storage")
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.firebase:firebase-auth")
    
    // QR Code
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    implementation("com.google.zxing:core:3.5.3")
    
    // UI
    implementation("com.google.android.material:material:1.11.0")
    implementation("de.hdodenhof:circleimageview:3.1.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    
    // Location
    implementation("com.google.android.gms:play-services-location:21.3.0")
}
```

### Permissions (`AndroidManifest.xml`)
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

---

## Package Structure

```
com.example.pickme/
├── models/              # Data classes (Event, Profile, Notification, etc.)
├── repositories/        # Firestore data access layer
├── services/            # Firebase services, QR code, device auth
├── ui/                  # Activities and Fragments
│   ├── events/         # Event browsing, creation, management
│   ├── profile/        # User profile management
│   ├── invitations/    # Invitation handling
│   ├── history/        # Event history
│   └── admin/          # Admin dashboard
├── adapters/           # RecyclerView adapters
└── utils/              # Helper classes
```

---

## Core Services Layer

### 1. FirebaseManager (Singleton)
**Path**: `services/FirebaseManager.java`

Central Firebase initialization and access point.

```java
public class FirebaseManager {
    private static FirebaseManager instance;
    private FirebaseFirestore firestore;
    private FirebaseStorage storage;
    private FirebaseAuth auth;
    private FirebaseMessaging messaging;
    
    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }
    
    private FirebaseManager() {
        firestore = FirebaseFirestore.getInstance();
        firestore.setFirestoreSettings(new FirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build());
        storage = FirebaseStorage.getInstance();
        auth = FirebaseAuth.getInstance();
        messaging = FirebaseMessaging.getInstance();
    }
    
    // Static accessors for convenience
    public static FirebaseFirestore getFirestore() { ... }
    public static StorageReference getStorageReference() { ... }
}
```

**Key Features**:
- Offline persistence enabled
- Anonymous authentication
- FCM token management
- Connection state monitoring

### 2. DeviceAuthenticator (Singleton)
**Path**: `services/DeviceAuthenticator.java`

Device-based user identification using Android ID + Firebase UID.

```java
public class DeviceAuthenticator {
    private String cachedDeviceId;
    private Profile cachedProfile;
    
    public void initializeUser(OnUserInitializedListener listener) {
        String deviceId = getDeviceId();
        ProfileRepository profileRepo = new ProfileRepository();
        
        profileRepo.getProfile(deviceId, new OnProfileLoadedListener() {
            public void onProfileLoaded(Profile profile) {
                cachedProfile = profile;
                listener.onUserInitialized(profile, false);
            }
            
            public void onError(Exception e) {
                // Create new profile
                Profile newProfile = new Profile(deviceId, "User" + deviceId.substring(0,6));
                profileRepo.createProfile(newProfile, ...);
            }
        });
    }
    
    private String getDeviceId() {
        String androidId = Settings.Secure.getString(
            context.getContentResolver(), 
            Settings.Secure.ANDROID_ID
        );
        String firebaseUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        return androidId + "_" + firebaseUid;
    }
}
```

**Usage Pattern**:
```java
DeviceAuthenticator.getInstance(this).initializeUser(new OnUserInitializedListener() {
    public void onUserInitialized(Profile profile, boolean isNewUser) {
        // Use profile
    }
});
```

### 3. QRCodeGenerator
**Path**: `services/QRCodeGenerator.java`

Generates QR codes for event registration using ZXing.

```java
public class QRCodeGenerator {
    public static Bitmap generateQRCode(String eventId, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(
                eventId,
                BarcodeFormat.QR_CODE,
                size, size
            );
            return convertToBitmap(matrix);
        } catch (WriterException e) {
            return null;
        }
    }
}
```

### 4. QRCodeScanner
**Path**: `services/QRCodeScanner.java`

Scans QR codes and extracts event IDs.

```java
public class QRCodeScanner {
    public void launchScanner(Activity activity) {
        IntentIntegrator integrator = new IntentIntegrator(activity);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE);
        integrator.setPrompt("Scan event QR code");
        integrator.setCameraId(0);
        integrator.setBeepEnabled(true);
        integrator.initiateScan();
    }
    
    public String handleScanResult(IntentResult result) {
        if (result != null && result.getContents() != null) {
            return result.getContents(); // Returns event ID
        }
        return null;
    }
}
```

---

## Data Models

### Profile
**Path**: `models/Profile.java`

```java
public class Profile implements Parcelable {
    private String userId;
    private String name;
    private String email;
    private String phoneNumber;
    private String profileImageUrl;
    private String role; // "entrant", "organizer", "admin"
    private boolean notificationEnabled;
    
    public static final String ROLE_ENTRANT = "entrant";
    public static final String ROLE_ORGANIZER = "organizer";
    public static final String ROLE_ADMIN = "admin";
    
    // Firestore collection: profiles/{userId}
}
```

### Event
**Path**: `models/Event.java`

```java
public class Event implements Parcelable {
    private String eventId;
    private String name;
    private String description;
    private String organizerId;
    private String location;
    private ArrayList<Long> eventDates;
    private long registrationStartDate;
    private long registrationEndDate;
    private int capacity;
    private double price;
    private int waitingListLimit; // -1 = unlimited
    private boolean geolocationRequired;
    private String qrCodeHash;
    private String posterImageUrl;
    private String status; // Stores EventStatus enum as string
    
    public boolean isRegistrationOpen() {
        long currentTime = System.currentTimeMillis();
        return EventStatus.OPEN.name().equals(status)
                && currentTime >= registrationStartDate
                && currentTime <= registrationEndDate;
    }
    
    // Firestore: events/{eventId}
    // Subcollections: waitingList/, responsePendingList/, inEventList/, cancelledList/
}
```

### EventStatus Enum
```java
public enum EventStatus {
    DRAFT,      // Created but not published
    OPEN,       // Accepting registrations
    CLOSED,     // Registration closed
    COMPLETED,  // Event finished
    CANCELLED   // Cancelled by organizer
}
```

### Notification
**Path**: `models/Notification.java`

```java
public class Notification {
    private String notificationId;
    private String recipientId;
    private String senderId;
    private String title;
    private String message;
    private long timestamp;
    private String type; // "lottery_win", "lottery_loss", "organizer_message", etc.
    private String eventId;
    private boolean isRead;
    
    // Firestore: notifications/{userId}/userNotifications/{notificationId}
}
```

---

## Repository Layer

All repositories extend `BaseRepository<T>` which provides common CRUD operations.

### BaseRepository Pattern
```java
public abstract class BaseRepository<T> {
    protected FirebaseFirestore db;
    protected CollectionReference collection;
    protected String collectionName;
    
    public void getById(String id, OnLoadedListener<T> listener) {
        collection.document(id).get()
            .addOnSuccessListener(doc -> {
                T item = doc.toObject(getModelClass());
                listener.onLoaded(item);
            })
            .addOnFailureListener(listener::onError);
    }
    
    protected abstract Class<T> getModelClass();
}
```

### EventRepository
**Path**: `repositories/EventRepository.java`

```java
public class EventRepository extends BaseRepository<Event> {
    private static final String COLLECTION_EVENTS = "events";
    
    // Create event
    public void createEvent(Event event, OnSuccessListener listener) {
        String eventId = collection.document().getId();
        event.setEventId(eventId);
        collection.document(eventId).set(event)
            .addOnSuccessListener(v -> listener.onSuccess(eventId));
    }
    
    // Get events for entrant (registration open)
    public void getEventsForEntrant(OnEventsLoadedListener listener) {
        collection.whereEqualTo("status", "OPEN")
            .get()
            .addOnSuccessListener(snapshot -> {
                List<Event> events = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    Event event = doc.toObject(Event.class);
                    if (event != null && event.isRegistrationOpen()) {
                        events.add(event);
                    }
                }
                listener.onEventsLoaded(events);
            });
    }
    
    // Get organizer's events
    public void getEventsByOrganizer(String organizerId, OnEventsLoadedListener listener) {
        collection.whereEqualTo("organizerId", organizerId).get()...
    }
    
    // Waiting list operations
    public void addToWaitingList(String eventId, String userId, Geolocation location) {
        DocumentReference waitingListDoc = collection.document(eventId)
            .collection("waitingList").document(userId);
        
        Map<String, Object> data = new HashMap<>();
        data.put("userId", userId);
        data.put("timestamp", System.currentTimeMillis());
        if (location != null) {
            data.put("latitude", location.getLatitude());
            data.put("longitude", location.getLongitude());
        }
        
        waitingListDoc.set(data)...
    }
    
    // Get waiting list
    public void getWaitingList(String eventId, OnEntrantsLoadedListener listener) {
        collection.document(eventId).collection("waitingList")
            .get()
            .addOnSuccessListener(snapshot -> {
                List<String> userIds = new ArrayList<>();
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    userIds.add(doc.getString("userId"));
                }
                listener.onEntrantsLoaded(userIds);
            });
    }
}
```

**Subcollection Structure**:
```
events/{eventId}/
├── waitingList/{userId}        # Users waiting for lottery
├── responsePendingList/{userId} # Selected, awaiting response
├── inEventList/{userId}         # Confirmed attendees
└── cancelledList/{userId}       # Declined or cancelled
```

### ProfileRepository
**Path**: `repositories/ProfileRepository.java`

```java
public class ProfileRepository extends BaseRepository<Profile> {
    public void createProfile(Profile profile, OnSuccessListener listener) {
        collection.document(profile.getUserId()).set(profile)...
    }
    
    public void updateProfile(String userId, Map<String, Object> updates, OnSuccessListener listener) {
        collection.document(userId).update(updates)...
    }
    
    public void getAllProfiles(OnProfilesLoadedListener listener) {
        collection.get()...
    }
}
```

### NotificationRepository
**Path**: `repositories/NotificationRepository.java`

```java
public class NotificationRepository {
    // User-specific notifications
    public void getUserNotifications(String userId, OnNotificationsLoadedListener listener) {
        db.collection("notifications").document(userId)
          .collection("userNotifications")
          .orderBy("timestamp", Query.Direction.DESCENDING)
          .get()...
    }
    
    public void sendNotification(Notification notification) {
        db.collection("notifications")
          .document(notification.getRecipientId())
          .collection("userNotifications")
          .add(notification)...
    }
}
```

---

## Lottery System

### LotteryService
**Path**: `services/LotteryService.java`

Executes lottery draws for event registration.

```java
public class LotteryService {
    public void executeLottery(String eventId, int numberOfWinners, LotteryCallback callback) {
        // 1. Get waiting list
        eventRepository.getWaitingList(eventId, new OnEntrantsLoadedListener() {
            public void onEntrantsLoaded(List<String> waitingList) {
                // 2. Shuffle and select winners
                Collections.shuffle(waitingList);
                List<String> winners = waitingList.subList(0, 
                    Math.min(numberOfWinners, waitingList.size()));
                
                // 3. Move winners to responsePendingList
                moveToResponsePending(eventId, winners, () -> {
                    // 4. Send notifications to winners
                    sendWinnerNotifications(eventId, winners);
                    
                    // 5. Send loser notifications
                    List<String> losers = new ArrayList<>(waitingList);
                    losers.removeAll(winners);
                    sendLoserNotifications(eventId, losers);
                    
                    callback.onLotteryComplete(new LotteryResult(winners, losers));
                });
            }
        });
    }
    
    private void moveToResponsePending(String eventId, List<String> userIds, Runnable onComplete) {
        WriteBatch batch = db.batch();
        
        for (String userId : userIds) {
            // Remove from waiting list
            DocumentReference waitingDoc = db.collection("events").document(eventId)
                .collection("waitingList").document(userId);
            batch.delete(waitingDoc);
            
            // Add to response pending
            DocumentReference pendingDoc = db.collection("events").document(eventId)
                .collection("responsePendingList").document(userId);
            batch.set(pendingDoc, Map.of(
                "userId", userId,
                "selectedTimestamp", System.currentTimeMillis(),
                "status", "PENDING"
            ));
        }
        
        batch.commit().addOnSuccessListener(v -> onComplete.run());
    }
}
```

---

## UI Layer Architecture

### MainActivity (Role-Based Navigation)
**Path**: `MainActivity.java`

Main entry point with role-based navigation.

```java
public class MainActivity extends AppCompatActivity {
    private Profile currentProfile;
    private View entrantSection, organizerSection, adminSection;
    
    private void setupRoleBasedUI() {
        String role = currentProfile.getRole();
        
        // Hide all sections first
        entrantSection.setVisibility(View.GONE);
        organizerSection.setVisibility(View.GONE);
        adminSection.setVisibility(View.GONE);
        
        // Show appropriate section
        if (Profile.ROLE_ORGANIZER.equals(role)) {
            setupOrganizerUI();
        } else if (Profile.ROLE_ADMIN.equals(role)) {
            setupAdminUI();
        } else {
            setupEntrantUI();
        }
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Reload profile to detect role changes
        Profile cachedProfile = deviceAuth.getCachedProfile();
        if (cachedProfile != null) {
            currentProfile = cachedProfile;
            setupRoleBasedUI();
        } else {
            reloadProfile(); // Fetch from Firestore
        }
    }
}
```

### ProfileActivity
**Path**: `ui/profile/ProfileActivity.java`

User profile management with image upload and role selection.

```java
public class ProfileActivity extends AppCompatActivity {
    private CircleImageView profileImage;
    private Spinner spinnerRole;
    private SwitchMaterial switchNotifications;
    
    private void saveProfile() {
        String name = etName.getText().toString().trim();
        String email = etEmail.getText().toString().trim();
        String role = getSelectedRole(); // From spinner
        boolean notifications = switchNotifications.isChecked();
        
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("email", email);
        updates.put("role", role);
        updates.put("notificationEnabled", notifications);
        
        profileRepository.updateProfile(userId, updates, new OnSuccessListener() {
            public void onSuccess(String id) {
                // Update cached profile
                currentProfile.setRole(role);
                deviceAuth.updateCachedProfile(currentProfile);
                finish(); // Return to main - will show new role UI
            }
        });
    }
}
```

**Role Switcher**: Users can change between entrant/organizer/admin roles via spinner for testing.

### EventBrowserActivity
**Path**: `ui/events/EventBrowserActivity.java`

Browse and search events with QR code scanning.

```java
public class EventBrowserActivity extends AppCompatActivity {
    private EventAdapter eventAdapter;
    private List<Event> allEvents, filteredEvents;
    private SearchView searchView;
    private ChipGroup chipGroupFilter;
    
    private void loadEvents() {
        eventRepository.getEventsForEntrant(new OnEventsLoadedListener() {
            public void onEventsLoaded(List<Event> events) {
                allEvents = events;
                applyFilters();
            }
        });
    }
    
    private void applyFilters() {
        filteredEvents = allEvents.stream()
            .filter(event -> matchesSearch(event, searchQuery))
            .filter(event -> matchesFilter(event, currentFilter))
            .collect(Collectors.toList());
        
        eventAdapter.setEvents(filteredEvents);
    }
    
    // QR Code scanning
    private void launchQRScanner() {
        qrCodeScanner.launchScanner(this);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        IntentResult result = IntentIntegrator.parseActivityResult(requestCode, resultCode, data);
        String eventId = qrCodeScanner.handleScanResult(result);
        if (eventId != null) {
            navigateToEventDetails(eventId);
        }
    }
}
```

**Features**:
- Search by name/location
- Filter: All / Open / Upcoming
- QR code scanner (FloatingActionButton)
- Click event → EventDetailsActivity

### EventDetailsActivity
**Path**: `ui/events/EventDetailsActivity.java`

View event details and join waiting list.

```java
public class EventDetailsActivity extends AppCompatActivity {
    private Button btnJoinWaitingList;
    private Button btnLeaveWaitingList;
    
    private void joinWaitingList() {
        if (event.isGeolocationRequired()) {
            requestLocationAndJoin();
        } else {
            addToWaitingList(null);
        }
    }
    
    private void addToWaitingList(Geolocation location) {
        eventRepository.addToWaitingList(eventId, currentUserId, location, 
            new OnSuccessListener() {
                public void onSuccess(String id) {
                    // Send notification
                    Notification notif = new Notification();
                    notif.setRecipientId(currentUserId);
                    notif.setTitle("Joined Waiting List");
                    notif.setMessage("You joined " + event.getName());
                    notificationRepository.sendNotification(notif);
                    
                    updateButtonState();
                }
            });
    }
}
```

### CreateEventActivity (Organizer)
**Path**: `ui/events/CreateEventActivity.java`

Organizers create events with QR code generation.

```java
public class CreateEventActivity extends AppCompatActivity {
    private void createEvent(String name, String description, ...) {
        Event event = new Event();
        event.setName(name);
        event.setDescription(description);
        event.setOrganizerId(currentUserId);
        event.setStatusEnum(EventStatus.OPEN);
        event.setRegistrationStartDate(regStartTimestamp);
        event.setRegistrationEndDate(regEndTimestamp);
        event.setCapacity(capacity);
        event.setGeolocationRequired(switchGeolocation.isChecked());
        
        eventRepository.createEvent(event, eventId -> {
            if (selectedPosterUri != null) {
                uploadPosterImage(eventId, event);
            } else {
                generateQRCode(eventId, event);
            }
        });
    }
    
    private void generateQRCode(String eventId, Event event) {
        Bitmap qrBitmap = QRCodeGenerator.generateQRCode(eventId, 512);
        showQRCodeDialog(qrBitmap);
        
        // Save QR to storage
        imageRepository.uploadQRCode(eventId, qrBitmap, organizerId, ...)
    }
}
```

**Date Picker Fix**: Creates fresh Calendar instances to avoid timestamp contamination.
```java
private void showRegStartDatePicker() {
    Calendar selectedCal = Calendar.getInstance();
    selectedCal.set(Calendar.YEAR, year);
    selectedCal.set(Calendar.MONTH, month);
    selectedCal.set(Calendar.DAY_OF_MONTH, dayOfMonth);
    // Keeps current time for immediate registration
}

private void showRegEndDatePicker() {
    Calendar selectedCal = Calendar.getInstance();
    selectedCal.set(Calendar.HOUR_OF_DAY, 23);
    selectedCal.set(Calendar.MINUTE, 59);
    selectedCal.set(Calendar.SECOND, 59);
    // Registration open until end of day
}
```

### OrganizerDashboardActivity
**Path**: `ui/events/OrganizerDashboardActivity.java`

View all organizer's events.

```java
public class OrganizerDashboardActivity extends AppCompatActivity {
    private RecyclerView recyclerView;
    private FloatingActionButton fabCreateEvent;
    
    private void loadEvents() {
        eventRepository.getEventsByOrganizer(currentUserId, events -> {
            adapter.setEvents(events);
        });
    }
    
    // Click event → ManageEventActivity
}
```

### ManageEventActivity (Organizer)
**Path**: `ui/events/ManageEventActivity.java`

Comprehensive event management with tabs and lottery execution.

```java
public class ManageEventActivity extends AppCompatActivity {
    private TabLayout tabLayout;
    private ViewPager2 viewPager;
    private FloatingActionButton fabMenu;
    
    private void setupTabs() {
        FragmentAdapter adapter = new FragmentAdapter(this);
        adapter.addFragment(new WaitingListFragment(), "Waiting");
        adapter.addFragment(new SelectedEntrantsFragment(), "Selected");
        adapter.addFragment(new ConfirmedEntrantsFragment(), "Confirmed");
        adapter.addFragment(new CancelledEntrantsFragment(), "Cancelled");
        viewPager.setAdapter(adapter);
    }
    
    private void showActionMenu() {
        // Options: Execute Lottery, Send Notification, Update Poster, Export Lists
    }
    
    private void executeLottery() {
        AlertDialog dialog = new AlertDialog.Builder(this)
            .setTitle("Execute Lottery")
            .setView(R.layout.dialog_lottery_input) // NumberPicker for winners
            .setPositiveButton("Execute", (d, w) -> {
                int numWinners = numberPicker.getValue();
                lotteryService.executeLottery(eventId, numWinners, result -> {
                    showSuccess("Lottery complete! " + result.getWinners().size() + " selected");
                    refreshFragments();
                });
            })
            .create();
    }
}
```

### AdminDashboardActivity
**Path**: `ui/admin/AdminDashboardActivity.java`

Admin panel with direct button access (no drawer).

```java
public class AdminDashboardActivity extends AppCompatActivity {
    private void setupButtons() {
        btnBrowseEvents.setOnClickListener(v -> 
            startActivity(new Intent(this, AdminEventsActivity.class)));
        
        btnBrowseProfiles.setOnClickListener(v -> 
            startActivity(new Intent(this, AdminProfilesActivity.class)));
    }
    
    private void checkAdminAccess() {
        Profile profile = deviceAuth.getCachedProfile();
        if (!Profile.ROLE_ADMIN.equals(profile.getRole())) {
            showAccessDenied();
            finish();
        }
    }
}
```

### AdminEventsActivity
**Path**: `ui/admin/AdminEventsActivity.java`

Browse and delete all events.

```java
public class AdminEventsActivity extends AppCompatActivity {
    private void loadEvents() {
        eventRepository.getAllEvents(events -> {
            adapter.setEvents(events);
        });
    }
    
    // Click event → Delete confirmation dialog
    private void deleteEvent(Event event) {
        eventRepository.deleteEvent(event.getEventId(), 
            id -> { loadEvents(); }
        );
    }
}
```

---

## Adapters

### EventAdapter
**Path**: `adapters/EventAdapter.java`

RecyclerView adapter for event cards.

```java
public class EventAdapter extends RecyclerView.Adapter<EventAdapter.EventViewHolder> {
    private List<Event> events;
    private OnEventClickListener listener;
    
    @Override
    public void onBindViewHolder(EventViewHolder holder, int position) {
        Event event = events.get(position);
        holder.tvEventName.setText(event.getName());
        holder.tvLocation.setText(event.getLocation());
        holder.tvPrice.setText(String.format("$%.2f", event.getPrice()));
        
        Glide.with(holder.itemView)
            .load(event.getPosterImageUrl())
            .placeholder(R.drawable.placeholder_event)
            .into(holder.ivPoster);
        
        holder.itemView.setOnClickListener(v -> 
            listener.onEventClick(event));
    }
}
```

### EntrantAdapter
**Path**: `adapters/EntrantAdapter.java`

Display users in waiting lists with profile images.

```java
public class EntrantAdapter extends RecyclerView.Adapter<EntrantAdapter.EntrantViewHolder> {
    class EntrantViewHolder extends RecyclerView.ViewHolder {
        CircleImageView ivProfileImage;
        TextView tvName, tvStatus;
        
        void bind(Profile profile) {
            tvName.setText(profile.getName());
            Glide.with(itemView)
                .load(profile.getProfileImageUrl())
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(ivProfileImage);
        }
    }
}
```

---

## Theme & Styling

### colors.xml
```xml
<color name="primary_pink">#FF6B9D</color>
<color name="primary_pink_dark">#E5578A</color>
<color name="primary_pink_light">#FFB3C9</color>
<color name="background_light">#F5F5F5</color>
<color name="text_primary">#212121</color>
<color name="text_secondary">#757575</color>
<color name="text_on_primary">#FFFFFF</color>
<color name="accent_color">#FF4081</color>
```

### themes.xml
```xml
<style name="Theme.PickMe" parent="Theme.MaterialComponents.DayNight.DarkActionBar">
    <item name="colorPrimary">@color/primary_pink</item>
    <item name="colorPrimaryVariant">@color/primary_pink_dark</item>
    <item name="colorSecondary">@color/primary_pink_light</item>
</style>

<style name="Widget.PickMe.Button" parent="Widget.MaterialComponents.Button">
    <item name="android:textColor">@color/text_on_primary</item>
    <item name="backgroundTint">@color/primary_pink</item>
    <item name="cornerRadius">8dp</item>
</style>
```

### Layout Patterns

**Standard Activity Layout**:
```xml
<androidx.coordinatorlayout.widget.CoordinatorLayout>
    <com.google.android.material.appbar.AppBarLayout>
        <MaterialToolbar 
            android:background="@color/primary_pink"
            app:titleTextColor="@color/text_on_primary" />
    </com.google.android.material.appbar.AppBarLayout>
    
    <NestedScrollView app:layout_behavior="@string/appbar_scrolling_view_behavior">
        <!-- Content -->
    </NestedScrollView>
</androidx.coordinatorlayout.widget.CoordinatorLayout>
```

---

## Key Implementation Details

### 1. Device Authentication Flow
```
App Launch → FirebaseManager.signInAnonymously()
          → DeviceAuthenticator.initializeUser()
          → Check Firestore for existing profile
          → Create new profile if not found
          → Cache profile in memory
```

### 2. Event Registration Flow
```
Browse Events → Select Event → Check Registration Status
             → Join Waiting List → Store in waitingList subcollection
             → Send Notification → User sees in My Invitations
```

### 3. Lottery Execution Flow
```
Organizer → Manage Event → Execute Lottery
         → Select N winners from waiting list
         → Move to responsePendingList
         → Send notifications (winners + losers)
         → Winners accept/decline
         → Accepted → Move to inEventList
         → Declined → Move to cancelledList + Replacement draw
```

### 4. Role-Based Access Control
```
User Role Check:
- MainActivity.onResume() → Load profile → Check role
- Show appropriate navigation (entrant/organizer/admin)
- Admin activities check role in onCreate()
- Access denied dialog if unauthorized
```

### 5. Firebase Serialization Rules
- Use unique setter names (no method overloading)
- Event uses `setStatus(String)` for Firebase, `setStatusEnum(EventStatus)` for code
- All models implement Parcelable for intent passing
- Default constructors required for Firestore deserialization

---

## Common Issues & Fixes

### Issue 1: Events Not Appearing in Browse
**Cause**: Registration dates set incorrectly (past or future).
**Fix**: Date pickers now create fresh Calendar instances with proper time boundaries.
```java
// Registration start: Current time on selected day
// Registration end: 23:59:59 on selected day
```

### Issue 2: Firebase Serialization Crash
**Cause**: Multiple setter methods with same name (setStatus).
**Fix**: Renamed enum version to setStatusEnum().

### Issue 3: Role UI Not Updating
**Cause**: MainActivity.onResume() not reloading profile.
**Fix**: Check cached profile and call setupRoleBasedUI().

### Issue 4: Toolbar Cut Off by Status Bar
**Fix**: Add `android:fitsSystemWindows="true"` to root layout.

---

## Testing Guidelines

### Creating Test Data

**1. Create Organizer Profile**:
```
Profile → Select "Organizer" role → Save
```

**2. Create Test Event**:
```
Create Event → Fill details
Registration Start: Today
Registration End: Tomorrow
Capacity: 50
Status: Automatically set to OPEN
```

**3. Join as Entrant**:
```
Switch role to "Entrant"
Browse Events → Find event → Join Waiting List
```

**4. Execute Lottery**:
```
Switch back to "Organizer"
My Events → Select Event → Manage
Execute Lottery → Select 10 winners
```

**5. Test Admin Features**:
```
Switch role to "Admin"
Admin Dashboard → Browse Events/Profiles
Delete test data
```

### Logcat Monitoring

Key tags to watch:
```
EventRepository - Event queries and operations
ProfileRepository - Profile operations
LotteryService - Lottery execution
DeviceAuthenticator - User initialization
FirebaseManager - Connection status
```

---

## Firestore Data Structure

```
pickme-database/
├── profiles/
│   └── {userId}/
│       ├── name: String
│       ├── email: String
│       ├── role: String
│       ├── notificationEnabled: Boolean
│       └── profileImageUrl: String
│
├── events/
│   └── {eventId}/
│       ├── name: String
│       ├── status: String (OPEN/CLOSED/etc)
│       ├── organizerId: String
│       ├── capacity: Number
│       ├── registrationStartDate: Timestamp
│       ├── registrationEndDate: Timestamp
│       ├── posterImageUrl: String
│       ├── qrCodeUrl: String
│       │
│       ├── waitingList/
│       │   └── {userId}/
│       │       ├── timestamp: Timestamp
│       │       ├── latitude: Number (optional)
│       │       └── longitude: Number (optional)
│       │
│       ├── responsePendingList/
│       │   └── {userId}/
│       │       ├── selectedTimestamp: Timestamp
│       │       └── status: String (PENDING/ACCEPTED/DECLINED)
│       │
│       ├── inEventList/
│       │   └── {userId}/
│       │       └── confirmedTimestamp: Timestamp
│       │
│       └── cancelledList/
│           └── {userId}/
│               ├── cancelledTimestamp: Timestamp
│               └── reason: String
│
└── notifications/
    └── {userId}/
        └── userNotifications/
            └── {notificationId}/
                ├── title: String
                ├── message: String
                ├── timestamp: Timestamp
                ├── type: String
                ├── eventId: String
                └── isRead: Boolean
```

---

## Build & Run

### Initial Setup
```bash
1. Clone repository
2. Add google-services.json to app/ directory
3. Sync Gradle
4. Run on emulator (API 34+) or physical device
```

### Build Commands
```bash
# Debug build
./gradlew assembleDebug

# Clean build
./gradlew clean assembleDebug

# Install on connected device
./gradlew installDebug
```

### Firebase Console Setup
1. Create Firebase project
2. Add Android app (package: com.example.pickme)
3. Download google-services.json
4. Enable Firestore, Storage, Authentication, FCM

---

## Current Implementation Status

**Phase 1-4**: Firebase setup, models, repositories, services - COMPLETE
**Phase 5**: Entrant UI (Profile, Browse, Invitations, History) - COMPLETE
**Phase 6**: Organizer UI (Create, Dashboard, Manage, Lottery) - COMPLETE
**Phase 7.1**: Admin UI (Dashboard, Browse Events/Profiles) - COMPLETE

**Total Lines of Code**: ~14,000+ documented Java code
**Activities**: 15+ activities
**Fragments**: 8+ fragments
**Repositories**: 5 repositories
**Services**: 6 service classes
**Models**: 8+ data models

---

## Next Development Steps

1. Complete AdminImagesFragment (browse/delete event posters)
2. Implement AdminNotificationLogsFragment (view all notifications)
3. Add RemoveOrganizerActivity (delete organizer + their events)
4. Implement MapActivity for geolocation visualization
5. Add CSV export for entrant lists
6. Enhance notification system with FCM push notifications
7. Add event analytics for organizers
8. Implement waiting list limit enforcement

---

## Additional Resources

**Firebase Documentation**: https://firebase.google.com/docs/android/setup
**Material Design**: https://material.io/develop/android
**ZXing QR Code**: https://github.com/journeyapps/zxing-android-embedded
**Glide Image Loading**: https://github.com/bumptech/glide

---

**Document Version**: 2.0  
**Last Updated**: October 31, 2025  
**Maintained By**: CMPUT 301 Team - PickMe Development Team

