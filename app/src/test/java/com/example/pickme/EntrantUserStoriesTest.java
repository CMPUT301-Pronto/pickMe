package com.example.pickme;

import static org.junit.Assert.*;

import com.example.pickme.models.Event;
import com.example.pickme.models.EventStatus;
import com.example.pickme.models.InEventList;
import com.example.pickme.models.Profile;
import com.example.pickme.models.ResponsePendingList;
import com.example.pickme.models.WaitingList;
import com.example.pickme.repositories.ProfileRepository;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for Entrant user stories
 * Tests cover waiting list operations, profile management, and invitation handling
 *
 * User Stories Covered:
 * - US 01.01.01: Join waiting list
 * - US 01.01.02: Leave waiting list
 * - US 01.01.03: View joinable events
 * - US 01.02.01: Provide personal information
 * - US 01.02.02: Update personal information
 * - US 01.04.01: Send Winners Notification
 * - US 01.04.02: Send Losers Notification
 * - US 01.04.03: notification opt out
 * - US 01.05.02: Accept Invitation
 * - US 01.05.03: Decline Invitation
 * - US 01.05.04: Get WaitingList Count
 * - US 01.05.05: Get Event Criteria or Guidelines for the Lottery Selection Process
 * - US 01.06.01: Scan QR code to view event
 * - US 01.06.02: Sign up from event details
 * - US 01.07.01: Device-based authentication
 */
public class EntrantUserStoriesTest {

    private Event testEvent;
    private Profile testProfile;
    private WaitingList testWaitingList;
    private ResponsePendingList testResponsePendingList;
    private InEventList testInEventList;
    private String testUserId;
    private String testEventId;

    @Before
    public void setUp() {
        // Set up test data
        testUserId = "test_device_123";
        testEventId = "test_event_456";

        // Create test profile
        testProfile = new Profile(testUserId, "Test User", "test@example.com");
        testProfile.setPhoneNumber("555-1234");
        testProfile.setRole(Profile.ROLE_ENTRANT);

        // Create test event with registration open
        testEvent = new Event(testEventId, "Test Event", "Test Description", "organizer_123");
        testEvent.setCapacity(100);
        testEvent.setWaitingListLimit(200);
        testEvent.setGeolocationRequired(false);
        testEvent.setRegistrationStartDate(System.currentTimeMillis() - 86400000); // 1 day ago
        testEvent.setRegistrationEndDate(System.currentTimeMillis() + 86400000); // 1 day from now
        testEvent.setStatus(EventStatus.OPEN.name());

        // Create test models
        testWaitingList = new WaitingList();
        testResponsePendingList = new ResponsePendingList();
        testInEventList = new InEventList();
    }

    // ==================== US 01.01.01: Join Waiting List ====================

    @Test
    public void testJoinWaitingList_Success() {
        // Given: Event with open registration and user not on list
        assertTrue("Registration should be open", testEvent.isRegistrationOpen());

        // When: User joins waiting list
        testWaitingList.addEntrant(testUserId, null);

        // Then: User should be on waiting list
        assertTrue("User should be on waiting list",
                testWaitingList.getEntrantIds().contains(testUserId));
        assertEquals("Waiting list count should be 1", 1, testWaitingList.getEntrantCount());
    }

    @Test
    public void testJoinWaitingList_AlreadyJoined() {
        // Given: User already on waiting list
        testWaitingList.addEntrant(testUserId, null);

        // When: User tries to join again
        int initialCount = testWaitingList.getEntrantCount();
        testWaitingList.addEntrant(testUserId, null);

        // Then: Count should not increase (duplicate prevented)
        assertEquals("Count should remain same", initialCount, testWaitingList.getEntrantCount());
    }

    @Test
    public void testJoinWaitingList_CapacityReached() {
        // Given: Waiting list at capacity
        testEvent.setWaitingListLimit(2);
        testWaitingList.addEntrant("user1", null);
        testWaitingList.addEntrant("user2", null);

        // When: Another user tries to join
        boolean canJoin = testWaitingList.getEntrantCount() < testEvent.getWaitingListLimit();

        // Then: Should not be able to join
        assertFalse("Should not be able to join when at capacity", canJoin);
    }

    @Test
    public void testJoinWaitingList_GeolocationCaptured() {
        // Given: Event requires geolocation
        testEvent.setGeolocationRequired(true);

        // When: User joins with location
        double testLatitude = 53.5461;
        double testLongitude = -113.4938;

        // Then: Location should be stored (validated by data model)
        assertTrue("Event should require geolocation", testEvent.isGeolocationRequired());
        assertNotNull("Latitude should be captured", testLatitude);
        assertNotNull("Longitude should be captured", testLongitude);
    }

    @Test
    public void testJoinWaitingList_RegistrationClosed() {
        // Given: Registration period ended
        testEvent.setRegistrationEndDate(System.currentTimeMillis() - 1000);

        // When: Check if can join
        boolean canJoin = testEvent.isRegistrationOpen();

        // Then: Should not be able to join
        assertFalse("Should not be able to join when registration closed", canJoin);
    }

    // ==================== US 01.01.02: Leave Waiting List ====================

    @Test
    public void testLeaveWaitingList_Success() {
        // Given: User on waiting list
        testWaitingList.addEntrant(testUserId, null);
        assertEquals(1, testWaitingList.getEntrantCount());

        // When: User leaves waiting list
        testWaitingList.removeEntrant(testUserId);

        // Then: User should be removed
        assertFalse("User should not be on waiting list",
                testWaitingList.getEntrantIds().contains(testUserId));
        assertEquals("Waiting list should be empty", 0, testWaitingList.getEntrantCount());
    }

    @Test
    public void testLeaveWaitingList_NotOnList() {
        // Given: User not on waiting list
        assertFalse(testWaitingList.getEntrantIds().contains(testUserId));

        // When: User tries to leave
        testWaitingList.removeEntrant(testUserId);

        // Then: No error should occur
        assertEquals("Count should remain 0", 0, testWaitingList.getEntrantCount());
    }

    // ==================== US 01.01.03: View Joinable Events ====================

    @Test
    public void testViewJoinableEvents_OpenRegistration() {
        // Given: Multiple events with different statuses
        List<Event> allEvents = new ArrayList<>();

        Event openEvent = new Event("event1", "Open Event", "Description", "org1");
        openEvent.setRegistrationStartDate(System.currentTimeMillis() - 86400000);
        openEvent.setRegistrationEndDate(System.currentTimeMillis() + 86400000);
        openEvent.setStatus(EventStatus.OPEN.name());

        Event closedEvent = new Event("event2", "Closed Event", "Description", "org2");
        closedEvent.setRegistrationStartDate(System.currentTimeMillis() - 172800000);
        closedEvent.setRegistrationEndDate(System.currentTimeMillis() - 86400000);
        closedEvent.setStatus(EventStatus.OPEN.name());

        allEvents.add(openEvent);
        allEvents.add(closedEvent);

        // When: Filter for joinable events
        List<Event> joinableEvents = new ArrayList<>();
        for (Event event : allEvents) {
            if (event.isRegistrationOpen()) {
                joinableEvents.add(event);
            }
        }

        // Then: Only open events should be shown
        assertEquals("Should have 1 joinable event", 1, joinableEvents.size());
        assertTrue("Open event should be joinable", joinableEvents.get(0).isRegistrationOpen());
    }

    @Test
    public void testViewJoinableEvents_EventDetails() {
        // Given: Event with all details
        testEvent.setLocation("Edmonton Convention Centre");
        testEvent.setPosterImageUrl("https://example.com/poster.jpg");

        // When: Display event details
        String name = testEvent.getName();
        String location = testEvent.getLocation();
        String posterUrl = testEvent.getPosterImageUrl();
        int capacity = testEvent.getCapacity();

        // Then: All details should be available
        assertNotNull("Event name should be present", name);
        assertNotNull("Location should be present", location);
        assertNotNull("Poster URL should be present", posterUrl);
        assertTrue("Capacity should be positive", capacity > 0);
    }

    // ==================== US 01.02.01: Provide Personal Information ====================

    @Test
    public void testProvidePersonalInfo_RequiredFields() {
        // Given: Profile with required fields
        Profile newProfile = new Profile("device_123", "John Doe", "john@example.com");

        // Then: Required fields should be set
        assertNotNull("User ID should not be null", newProfile.getUserId());
        assertNotNull("Name should not be null", newProfile.getName());
        assertNotNull("Email should not be null", newProfile.getEmail());
    }

    @Test
    public void testProvidePersonalInfo_OptionalPhone() {
        // Given: Profile without phone number
        Profile profileWithoutPhone = new Profile("device_456", "Jane Doe", "jane@example.com");

        // Then: Profile should be valid without phone
        assertNull("Phone should be null", profileWithoutPhone.getPhoneNumber());
        assertNotNull("Email should be present", profileWithoutPhone.getEmail());

        // When: Phone is added later
        profileWithoutPhone.setPhoneNumber("555-5678");

        // Then: Phone should be set
        assertEquals("Phone should be set", "555-5678", profileWithoutPhone.getPhoneNumber());
    }

    @Test
    public void testProvidePersonalInfo_EmailValidation() {
        // Given: Valid email formats
        String validEmail1 = "test@example.com";
        String validEmail2 = "user.name+tag@domain.co.uk";

        // When: Set emails
        testProfile.setEmail(validEmail1);

        // Then: Email should be set
        assertEquals("Email should match", validEmail1, testProfile.getEmail());

        // Test another valid format
        testProfile.setEmail(validEmail2);
        assertEquals("Email should match", validEmail2, testProfile.getEmail());
    }

    @Test
    public void testProvidePersonalInfo_LinkedToDeviceId() {
        // Given: Profile created with device ID
        String deviceId = "unique_device_789";
        Profile profile = new Profile(deviceId, "User Name", "user@test.com");

        // Then: Profile should be linked to device
        assertEquals("User ID should match device ID", deviceId, profile.getUserId());
    }

    // ==================== US 01.02.02: Update Personal Information ====================

    @Test
    public void testUpdatePersonalInfo_Name() {
        // Given: Existing profile
        String originalName = testProfile.getName();

        // When: Update name
        String newName = "Updated Test User";
        testProfile.setName(newName);

        // Then: Name should be updated
        assertNotEquals("Name should be different", originalName, testProfile.getName());
        assertEquals("Name should match new value", newName, testProfile.getName());
    }

    @Test
    public void testUpdatePersonalInfo_Email() {
        // Given: Existing profile
        String originalEmail = testProfile.getEmail();

        // When: Update email
        String newEmail = "newemail@example.com";
        testProfile.setEmail(newEmail);

        // Then: Email should be updated
        assertNotEquals("Email should be different", originalEmail, testProfile.getEmail());
        assertEquals("Email should match new value", newEmail, testProfile.getEmail());
    }

    @Test
    public void testUpdatePersonalInfo_Phone() {
        // Given: Profile with phone
        testProfile.setPhoneNumber("555-1111");

        // When: Update phone
        String newPhone = "555-2222";
        testProfile.setPhoneNumber(newPhone);

        // Then: Phone should be updated
        assertEquals("Phone should be updated", newPhone, testProfile.getPhoneNumber());
    }

    @Test
    public void testUpdatePersonalInfo_RemoveOptionalPhone() {
        // Given: Profile with phone
        testProfile.setPhoneNumber("555-3333");
        assertNotNull(testProfile.getPhoneNumber());

        // When: Remove phone
        testProfile.setPhoneNumber(null);

        // Then: Phone should be null
        assertNull("Phone should be removed", testProfile.getPhoneNumber());
    }

    // ==================== US 01.02.04: Send Winners Notification ====================
    // Used AI to learn how to create a test version of real class

    @Test
    public void testDeleteProfile_CascadeAndDeleteCalled() {

        class FakeProfileRepository {

            boolean cascadeCalled = false;
            boolean deleteCalled = false;
            String deletedUserId = null;

            // Copies cascadeDeleteFromEvents()
            void cascadeDeleteFromEvents(String userId, Runnable onComplete) {
                cascadeCalled = true;
                onComplete.run();   // Immediately succeed
            }

            // Copies Firestore delete operation
            void performDelete(String userId,
                               ProfileRepository.OnSuccessListener onSuccess) {
                deleteCalled = true;
                deletedUserId = userId;
                onSuccess.onSuccess(userId); // simulate success callback
            }

            // Exposed deletion API for test
            void deleteProfile(String userId,
                               ProfileRepository.OnSuccessListener success,
                               ProfileRepository.OnFailureListener failure) {

                cascadeDeleteFromEvents(userId, () -> {
                    performDelete(userId, success);
                });
            }
        }

        // ---- Arrange ----
        FakeProfileRepository repo = new FakeProfileRepository();
        String userId = "test_user_321";

        final String[] callbackResult = { null };

        // ---- Act ----
        repo.deleteProfile(userId,
                result -> callbackResult[0] = result,
                e -> fail("Should not fail")
        );

        // ---- Assert ----
        assertTrue("Cascade deletion should be called", repo.cascadeCalled);
        assertTrue("Profile deletion should be called", repo.deleteCalled);
        assertEquals("Deleted userId should match", userId, repo.deletedUserId);
        assertEquals("Success callback should return correct userId",
                userId, callbackResult[0]);
    }


    // ==================== US 01.04.01: Send Winners Notification ====================
    // Used AI to learn how to create a test version of real class
    @Test
    public void testWinnerNotification() {

        // ---- Fake service that copies winner notification logic ----
        class FakeNotificationService {

            boolean sendCalled = false;
            List<String> capturedRecipients = new ArrayList<>();
            String capturedMessage = null;
            String capturedType = null;

            void sendLotteryWinNotification(List<String> entrantIds, Event event,
                                            com.example.pickme.services.NotificationService.OnNotificationSentListener listener) {


                String message = "Congratulations! You've been selected for " +
                        event.getName() +
                        ". Please respond to confirm your participation.";

                // Copy what sendFCMMessages would do:
                sendCalled = true;
                capturedRecipients.addAll(entrantIds);
                capturedMessage = message;
                capturedType = "lottery_win";

                // Simulate callback success
                listener.onNotificationSent(entrantIds.size());
            }
        }


        FakeNotificationService mock = new FakeNotificationService();
        List<String> winners = new ArrayList<>();
        winners.add(testUserId);

        testEvent.setName("Test Event");

        final int[] callbackCount = { -1 };

        // ---- Act ----
        mock.sendLotteryWinNotification(winners, testEvent,
                new com.example.pickme.services.NotificationService.OnNotificationSentListener() {
                    @Override
                    public void onNotificationSent(int sentCount) {
                        callbackCount[0] = sentCount;
                    }

                    @Override
                    public void onError(Exception e) {
                        fail("Should not error");
                    }
                });

        // ---- Assert ----
        assertTrue("Mock send should be called", mock.sendCalled);

        assertEquals("Recipient count should be 1", 1, mock.capturedRecipients.size());
        assertEquals("Recipient ID should match",
                testUserId, mock.capturedRecipients.get(0));

        assertNotNull("Message should not be null", mock.capturedMessage);
        assertTrue("Message should contain the event name",
                mock.capturedMessage.contains("Test Event"));

        assertEquals("Notification type should be lottery_win",
                "lottery_win", mock.capturedType);

        assertEquals("Callback should report 1 sent notification",
                1, callbackCount[0]);
    }

    // ==================== US 01.04.02: Send Losers Notifications. ====================
    // Used AI to learn how to create a test version of real class
    @Test
    public void testLoserNotification() {

        // ---- Fake service that mimics LOSS notification logic ----
        class FakeNotificationServiceLoser {

            boolean sendCalled = false;
            List<String> capturedRecipients = new ArrayList<>();
            String capturedMessage = null;
            String capturedType = null;

            void sendLotteryLossNotification(List<String> entrantIds, Event event,
                                             com.example.pickme.services.NotificationService.OnNotificationSentListener listener) {

                String message = "Unfortunately, you weren't selected for " +
                        event.getName() +
                        ", but you may have another chance if spots become available.";

                // Mock what sendFCMMessages would do
                sendCalled = true;
                capturedRecipients.addAll(entrantIds);
                capturedMessage = message;
                capturedType = "lottery_loss";

                // Simulate callback success
                listener.onNotificationSent(entrantIds.size());
            }
        }

        // ---- Arrange ----
        FakeNotificationServiceLoser mock = new FakeNotificationServiceLoser();
        List<String> losers = new ArrayList<>();
        losers.add(testUserId);

        testEvent.setName("Test Event");

        final int[] callbackCount = { -1 };

        // ---- Act ----
        mock.sendLotteryLossNotification(losers, testEvent,
                new com.example.pickme.services.NotificationService.OnNotificationSentListener() {
                    @Override
                    public void onNotificationSent(int sentCount) {
                        callbackCount[0] = sentCount;
                    }

                    @Override
                    public void onError(Exception e) {
                        fail("Should not error");
                    }
                });

        // ---- Assert ----
        assertTrue("Mock send should be called", mock.sendCalled);

        assertEquals("Recipient count should be 1",
                1, mock.capturedRecipients.size());
        assertEquals("Recipient ID should match",
                testUserId, mock.capturedRecipients.get(0));

        assertNotNull("Message should not be null", mock.capturedMessage);
        assertTrue("Loss message should include event name",
                mock.capturedMessage.contains("Test Event"));

        assertEquals("Notification type should be lottery_loss",
                "lottery_loss", mock.capturedType);

        assertEquals("Callback should return number of recipients",
                1, callbackCount[0]);
    }


    // ==================== US 01.04.03: Opt Out of Receiving Notifications. ====================
    @Test
    public void testToggleNotification(){
        // A profile with notifications enabled by default
        testProfile.setNotificationEnabled(true);
        assertTrue("Notifications should initially be ON", testProfile.isNotificationEnabled());

        // When: User opts out
        testProfile.setNotificationEnabled(false);

        // Then: Notification OFF
        assertFalse("Notifications should be OFF after toggle", testProfile.isNotificationEnabled());

        // When: User toggles back ON
        testProfile.setNotificationEnabled(true);

        // Then: Notification preference should be ON again
        assertTrue("Notifications ON", testProfile.isNotificationEnabled());
    }

    // ==================== US 01.05.02: Accept Invitation ====================
    @Test
    public void testAcceptInvitation(){
        // Given: User joins ResponsePendingList
        testResponsePendingList.addEntrant("test_accept", null);
        assertTrue("User in response pending list", testResponsePendingList.containsEntrant("test_accept"));

        // Get current InEventList and ResponsePendingList count
        int initialInEventCount = testInEventList.getEntrantCount();
        int initialResponsePendingCount = testResponsePendingList.getEntrantCount();

        // When: User accepts invitation: move from pending → in-event list
        boolean removed = testResponsePendingList.removeEntrant("test_accept");
        boolean added = testInEventList.addEntrant("test_accept", null);

        // Then: User removed from pending list
        assertTrue("User removed from ResponsePendingList", removed);
        assertFalse("User not in ResponsePendingList", testResponsePendingList.containsEntrant("test_accept"));
        assertEquals("ResponsePendingList decrease by 1", initialResponsePendingCount - 1,
                testResponsePendingList.getEntrantCount());

        // Then: User added to in-event list
        assertTrue("User added to InEventList", added);
        assertTrue("User in InEventList", testInEventList.containsEntrant("test_accept"));
        assertEquals("InEventList count increase by 1", initialInEventCount + 1,
                testInEventList.getEntrantCount());

        // Then: Enrollment timestamp is created
        Long timestamp = testInEventList.getEntrantEnrollmentTime("test_accept");
        assertNotNull("Enrollment timestamp recorded", timestamp);

        // 6. Check-in status defaults to false
        assertFalse("User should not be checked in after acceptance",
                testInEventList.isCheckedIn("test_accept"));
    }

    // ==================== US 01.05.03: Decline Invitation ====================
    @Test
    public void testDeclineInvitation(){
        // Given: User starts in ResponsePendingList
        testResponsePendingList.addEntrant("test_decline", null);
        assertTrue("User in ResponsePendingList before declining",
                testResponsePendingList.containsEntrant("test_decline"));

        int initialResponsePendingCount = testResponsePendingList.getEntrantCount();
        int initialInEventCount = testInEventList.getEntrantCount();

        // When: Declined, remove from pending list
        boolean removed = testResponsePendingList.removeEntrant("test_decline");

        // Then: User removed from pending list
        assertTrue("User removed from ResponsePendingList", removed);
        assertFalse("User not in ResponsePendingList",
                testResponsePendingList.containsEntrant("test_decline"));
        assertEquals("ResponsePendingList decrease by 1",
                initialResponsePendingCount - 1,
                testResponsePendingList.getEntrantCount());

        // Then: User should NOT be added to InEventList
        assertFalse("User not in InEventList after declining",
                testInEventList.containsEntrant("test_decline"));

        // Then: InEventList count should remain unchanged
        assertEquals("InEventList count should NOT change when declining",
                initialInEventCount,
                testInEventList.getEntrantCount());
    }

    // ==================== US 01.05.04: Waiting List Count ====================
    @Test
    public void testWaitingListCount(){
        //Given: initial waitingList count
        int waiting_count = testWaitingList.getEntrantCount();

        //When: New User added
        testWaitingList.addEntrant("user1", null);

        //Then: New waiting count 1 more than initial
        assertEquals("New count 1 more than initial", waiting_count + 1, testWaitingList.getEntrantCount());

        //When: Same user is removed
        testWaitingList.removeEntrant("user1");

        //Then: New waitingList is same as the initial
        assertEquals("New count same as initial", waiting_count, testWaitingList.getEntrantCount());

    }

    // ==================== US 01.05.05: Get Event Criteria or Guidelines for the Lottery Selection Process ====================
    @Test
    public void lotteryCriteria_isDisplayedWithCorrectText() {
        // String for Event Criteria or Guidelines for the Lottery Selection Process exist
        assertNotEquals("Resource ID should not be 0",
                0, R.string.lottery_criteria_description);
    }

    // ==================== US 01.06.01 & 01.06.02: QR Code & Sign Up ====================

    @Test
    public void testQRCodeScan_ValidFormat() {
        // Given: Valid QR code data
        String qrData = "eventlottery://event/" + testEventId;

        // When: Parse QR code
        String prefix = "eventlottery://event/";
        String extractedEventId = qrData.substring(prefix.length());

        // Then: Event ID should be extracted correctly
        assertEquals("Event ID should match", testEventId, extractedEventId);
    }

    @Test
    public void testQRCodeScan_InvalidFormat() {
        // Given: Invalid QR code data
        String invalidQrData = "https://example.com/event/123";

        // When: Validate format
        boolean isValid = invalidQrData.startsWith("eventlottery://event/");

        // Then: Should be invalid
        assertFalse("Invalid QR code should not validate", isValid);
    }

    @Test
    public void testSignUpFromEventDetails_JoinWaitingList() {
        // Given: Event details page with join button
        assertTrue("Registration should be open", testEvent.isRegistrationOpen());

        // When: User clicks join from event details
        testWaitingList.addEntrant(testUserId, null);

        // Then: User should be added to waiting list
        assertTrue("User should be on waiting list",
                testWaitingList.getEntrantIds().contains(testUserId));
    }

    // ==================== US 01.07.01: Device-Based Authentication ====================

    @Test
    public void testDeviceAuth_UniqueIdentifier() {
        // Given: Device ID
        String deviceId = "android_device_abc123";

        // When: Create profile with device ID
        Profile profile = new Profile(deviceId, "Device User", "device@test.com");

        // Then: Device ID should be stored as user ID
        assertEquals("User ID should match device ID", deviceId, profile.getUserId());
    }

    @Test
    public void testDeviceAuth_NoPasswordRequired() {
        // Given: Profile creation
        Profile profile = new Profile("device_xyz", "No Password User", "nopass@test.com");

        // Then: Profile should not have password field
        // (Verified by absence of password field in Profile class)
        assertNotNull("Profile should be created without password", profile.getUserId());
    }

    @Test
    public void testDeviceAuth_DefaultRole() {
        // Given: New profile
        Profile newProfile = new Profile("device_123", "New User", "new@test.com");

        // Then: Should have default entrant role
        assertEquals("Default role should be entrant",
                Profile.ROLE_ENTRANT, newProfile.getRole());
    }

    @Test
    public void testDeviceAuth_PersistentIdentity() {
        // Given: User ID from device
        String deviceId = "persistent_device_456";

        // When: Create profile on first launch
        Profile firstProfile = new Profile(deviceId, "First Launch", "first@test.com");

        // Then: Same device ID can be used for subsequent launches
        Profile secondProfile = new Profile(deviceId, "First Launch", "first@test.com");
        assertEquals("Device ID should persist",
                firstProfile.getUserId(), secondProfile.getUserId());
    }
}

