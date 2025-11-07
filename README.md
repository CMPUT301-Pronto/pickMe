# PickMe - Event Lottery Management System

A mobile application for managing event registrations through a lottery-based system. Organizers create events with limited capacity, entrants join waiting lists, and winners are selected through a randomized lottery draw.

## Project Overview

**Course**: CMPUT 301 - Introduction to Software Engineering  
**Platform**: Android (Min SDK 34, Target SDK 36)  
**Backend**: Firebase (Firestore, Storage, Authentication, Cloud Messaging)  
**Language**: Java

### Key Features

**For Entrants:**
- Browse and search events
- Join waiting lists via QR code or event browser
- Receive lottery notifications (win/loss)
- Accept or decline event invitations
- View event history

**For Organizers:**
- Create events with capacity limits and registration windows
- Generate QR codes for event registration
- Execute lottery draws to select winners
- Manage waiting lists and confirmed attendees
- Send notifications to participants
- View entrant locations (if geolocation enabled)

**For Admins:**
- Browse and delete all events
- Browse and delete user profiles
- View notification logs
- Remove organizers and their events

## Architecture

**Pattern**: Repository Pattern + MVVM-style UI  
**Structure**:
```
com.example.pickme/
├── models/          # Data models (Event, Profile, Notification)
├── repositories/    # Firestore data access layer
├── services/        # Firebase services, QR code, device auth
├── ui/              # Activities and Fragments
└── adapters/        # RecyclerView adapters
```

**Authentication**: Device-based (Android ID + Firebase UID)  
**Database**: Firestore with offline persistence  
**Storage**: Firebase Storage for images (profiles, posters, QR codes)

## Getting Started

### Prerequisites

1. **Android Studio**: Latest stable version (Hedgehog or newer)
2. **Android Device/Emulator**: API 34+ (Android 14.0 or higher)
3. **Firebase Account**: Google account for Firebase Console access
4. **Java**: JDK 11 or higher

### Firebase Setup

1. **Create Firebase Project**:
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Click "Add project" and follow the wizard
   - Enable Google Analytics (optional)

2. **Add Android App**:
   - In Firebase Console, click "Add app" → Android
   - Package name: `com.example.pickme`
   - Download `google-services.json`

3. **Enable Firebase Services**:
   - **Firestore Database**: Create database in production mode
   - **Authentication**: Enable Anonymous sign-in
   - **Storage**: Enable with default rules
   - **Cloud Messaging**: Automatically enabled

4. **Place Configuration File**:
   ```bash
   # Copy google-services.json to app directory
   cp ~/Downloads/google-services.json app/google-services.json
   ```

### Installation

1. **Clone Repository**:
   ```bash
   git clone <repository-url>
   cd pickme
   ```

2. **Add Firebase Configuration**:
   - Ensure `google-services.json` is in `app/` directory
   - File should NOT be committed to version control

3. **Sync Gradle**:
   - Open project in Android Studio
   - Wait for Gradle sync to complete
   - Resolve any dependency issues

4. **Build Project**:
   ```bash
   # Using Gradle wrapper
   ./gradlew assembleDebug
   
   # Or use Android Studio: Build → Make Project
   ```

5. **Run Application**:
   - Connect Android device or start emulator (API 34+)
   - Click "Run" or use `Shift + F10`

## Testing the Application

### Initial Setup

1. **First Launch**:
   - App creates anonymous Firebase user automatically
   - Profile is created with device-based ID
   - Default role: "Entrant"

2. **Switch Roles**:
   - Open "My Profile"
   - Change role in spinner: Entrant / Organizer / Admin
   - Save changes
   - Return to main screen to see role-specific navigation

### Test Scenarios

**As Organizer:**
```
1. Switch to Organizer role in profile
2. Create New Event → Fill in details
   - Registration Start: Today
   - Registration End: Tomorrow or later
   - Set capacity (e.g., 50)
3. Event auto-publishes with status "OPEN"
4. QR code generated automatically
```

**As Entrant:**
```
1. Switch to Entrant role
2. Browse Events → Find available events
3. Join Waiting List for an event
4. View in "My Invitations"
```

**As Organizer (Lottery):**
```
1. Go to My Events Dashboard
2. Select event → Manage Event
3. View "Waiting" tab (entrants on waiting list)
4. Click menu (FAB) → Execute Lottery
5. Select number of winners
6. Notifications sent automatically
7. View "Selected" tab for lottery winners
```

**As Admin:**
```
1. Switch to Admin role
2. Admin Dashboard → Browse Events/Profiles
3. Click any item to delete
4. Confirm deletion
```

## Project Structure

### Key Files

**Configuration:**
- `app/build.gradle.kts` - Dependencies and build config
- `app/google-services.json` - Firebase configuration (not in repo)
- `AndroidManifest.xml` - Permissions and activities

**Core Services:**
- `services/FirebaseManager.java` - Firebase initialization singleton
- `services/DeviceAuthenticator.java` - Device-based authentication
- `services/LotteryService.java` - Lottery execution logic
- `services/QRCodeGenerator.java` - QR code generation
- `services/QRCodeScanner.java` - QR code scanning

**Repositories:**
- `repositories/EventRepository.java` - Event CRUD operations
- `repositories/ProfileRepository.java` - User profile operations
- `repositories/NotificationRepository.java` - Notification management

**Main Activities:**
- `MainActivity.java` - Role-based navigation hub
- `ui/profile/ProfileActivity.java` - Profile management
- `ui/events/EventBrowserActivity.java` - Browse events
- `ui/events/CreateEventActivity.java` - Create events (organizer)
- `ui/events/ManageEventActivity.java` - Event management (organizer)
- `ui/admin/AdminDashboardActivity.java` - Admin panel

### Firestore Collections

```
profiles/                         # User profiles
  {userId}/
    - name, email, role, etc.

events/                           # Events
  {eventId}/
    - name, status, capacity, etc.
    waitingList/{userId}          # Waiting for lottery
    responsePendingList/{userId}   # Selected, awaiting response
    inEventList/{userId}           # Confirmed attendees
    cancelledList/{userId}         # Declined/cancelled

notifications/                    # User notifications
  {userId}/
    userNotifications/{notificationId}
```

## Common Issues

### Build Errors

**"google-services.json not found"**
- Ensure file is in `app/` directory
- File name must be exact: `google-services.json`
- Run Gradle sync after adding file

**"Failed to resolve Firebase dependencies"**
- Check internet connection
- Ensure Gradle sync completed successfully
- Try: File → Invalidate Caches → Restart

### Runtime Issues

**"Events not appearing in browse list"**
- Check event registration dates are valid (start <= now <= end)
- Event status must be "OPEN"
- View Logcat for "EventRepository" tag

**"Role UI not updating after change"**
- Make sure to save profile changes
- Return to main screen (press back)
- MainActivity.onResume() reloads profile automatically

**"Firebase Authentication failed"**
- Check internet connection
- Verify Firebase Authentication is enabled in console
- Anonymous sign-in must be enabled

## Development Guidelines

### Adding New Features

1. **Create Model** in `models/` package
2. **Create Repository** extending `BaseRepository<T>`
3. **Create Activity/Fragment** in appropriate `ui/` package
4. **Register Activity** in `AndroidManifest.xml`
5. **Update Navigation** in `MainActivity.java` if needed

### Firebase Rules

Firestore and Storage rules should be configured in Firebase Console for production:

**Firestore Rules** (for production):
```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /profiles/{userId} {
      allow read, write: if request.auth != null;
    }
    match /events/{eventId} {
      allow read: if request.auth != null;
      allow write: if request.auth != null;
    }
  }
}
```

### Code Style

- Follow Java naming conventions
- Use meaningful variable names
- Add Javadoc comments for public methods
- Keep activities under 500 lines (extract fragments)
- Use repository pattern for all Firestore access

## Documentation

**Detailed Developer Docs**: See `PROJECT_SETUP_SUMMARY.md`  
**Testing Guide**: See `HOW_TO_TEST_PHASE5.md`

## Dependencies

**Firebase** (BOM 34.4.0):
- Firestore - NoSQL database
- Storage - Image storage
- Authentication - Anonymous auth
- Cloud Messaging - Push notifications

**UI Libraries**:
- Material Design Components 1.11.0
- Glide 4.16.0 - Image loading
- CircleImageView 3.1.0 - Round profile images

**QR Code**:
- ZXing Android Embedded 4.3.0
- ZXing Core 3.5.3

**Location**:
- Google Play Services Location 21.3.0

## Team

CMPUT 301 - pronto



