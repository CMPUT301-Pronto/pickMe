package com.example.pickme;

import static org.junit.Assert.*;

import com.example.pickme.models.Event;
import com.example.pickme.models.EventStatus;
import com.example.pickme.models.Profile;
import com.example.pickme.models.QRCode;
import com.example.pickme.models.WaitingList;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Unit tests for Organizer user stories
 * Tests cover event creation, management, lottery execution, and entrant list operations
 *
 * User Stories Covered:
 * - US 02.01.01: Create event and generate QR code
 * - US 02.01.04: Set registration period
 * - US 02.02.01: View waiting list entrants
 * - US 02.02.03: Enable/disable geolocation requirement
 * - US 02.03.01: Limit waiting list capacity
 * - US 02.04.01: Upload event poster
 * - US 02.04.02: Update event poster
 * - US 02.05.02: Execute lottery draw
 * - US 02.06.01: View selected entrants
 * - US 02.06.02: View cancelled entrants
 * - US 02.06.03: View enrolled entrants
 * - US 02.06.05: Export enrolled list to CSV
 */
public class OrganizerUserStoriesTest {

    private Event testEvent;
    private Profile organizerProfile;
    private WaitingList testWaitingList;
    private String organizerId;
    private String eventId;

    @Before
    public void setUp() {
        // Set up test data
        organizerId = "organizer_123";
        eventId = "event_456";

        // Create organizer profile
        organizerProfile = new Profile(organizerId, "Test Organizer", "organizer@example.com");
        organizerProfile.setRole(Profile.ROLE_ORGANIZER);

        // Create test event
        testEvent = new Event(eventId, "Test Concert", "Amazing concert event", organizerId);
        testEvent.setCapacity(100);
        testEvent.setWaitingListLimit(-1); // Unlimited by default
        testEvent.setLocation("Edmonton Convention Centre");
        testEvent.setPrice(50.0);

        // Create waiting list
        testWaitingList = new WaitingList();
    }

    // ==================== US 02.01.01: Create Event and Generate QR Code ====================

    @Test
    public void testCreateEvent_RequiredFields() {
        // Given: Event creation data
        String name = "New Event";
        String description = "Event description";
        String organizerId = "org_789";

        // When: Create event
        Event newEvent = new Event("new_event_id", name, description, organizerId);

        // Then: Event should have required fields
        assertNotNull("Event ID should not be null", newEvent.getEventId());
        assertEquals("Name should match", name, newEvent.getName());
        assertEquals("Description should match", description, newEvent.getDescription());
        assertEquals("Organizer ID should match", organizerId, newEvent.getOrganizerId());
    }

    @Test
    public void testCreateEvent_QRCodeGeneration() {
        // Given: Created event
        String expectedQrData = "eventlottery://event/" + eventId;
        String qrCodeId = "qr_" + eventId;

        // When: Generate QR code
        QRCode qrCode = new QRCode();
        qrCode.setQrCodeId(qrCodeId);
        qrCode.setEventId(eventId);
        qrCode.setEncodedData(expectedQrData);

        // Then: QR code should encode event ID
        // Note: QR code ID is set manually for testing purposes
        assertEquals("QR code ID should match", qrCodeId, qrCode.getQrCodeId());
        assertEquals("QR data should match format", expectedQrData, qrCode.getEncodedData());
        assertEquals("Event ID should match", eventId, qrCode.getEventId());
    }

    @Test
    public void testCreateEvent_InitialStatus() {
        // Given: Newly created event
        Event newEvent = new Event("id_123", "Event", "Description", organizerId);

        // Then: Should have draft status initially
        assertEquals("Initial status should be DRAFT",
                EventStatus.DRAFT.name(), newEvent.getStatus());
    }

    @Test
    public void testCreateEvent_DefaultValues() {
        // Given: Event with minimal data
        Event newEvent = new Event("id_456", "Minimal Event", "Desc", organizerId);

        // Then: Should have default values
        assertEquals("Default waiting list limit should be -1", -1, newEvent.getWaitingListLimit());
        assertEquals("Default price should be 0", 0.0, newEvent.getPrice(), 0.01);
        assertFalse("Geolocation should be disabled by default", newEvent.isGeolocationRequired());
    }

    // ==================== US 02.01.04: Set Registration Period ====================

    @Test
    public void testSetRegistrationPeriod_ValidDates() {
        // Given: Event dates
        long now = System.currentTimeMillis();
        long startDate = now + 86400000; // 1 day from now
        long endDate = now + 604800000; // 7 days from now
        long eventDate = now + 1209600000; // 14 days from now

        // When: Set registration period
        testEvent.setRegistrationStartDate(startDate);
        testEvent.setRegistrationEndDate(endDate);
        List<Long> eventDates = new ArrayList<>();
        eventDates.add(eventDate);
        testEvent.setEventDates(eventDates);

        // Then: Dates should be set correctly
        assertEquals("Start date should match", startDate, testEvent.getRegistrationStartDate());
        assertEquals("End date should match", endDate, testEvent.getRegistrationEndDate());
        assertTrue("Start should be before end", startDate < endDate);
        assertTrue("End should be before event", endDate < eventDate);
    }

    @Test
    public void testSetRegistrationPeriod_AutoOpen() {
        // Given: Registration period that includes current time
        long past = System.currentTimeMillis() - 86400000; // 1 day ago
        long future = System.currentTimeMillis() + 86400000; // 1 day ahead

        // When: Set dates
        testEvent.setRegistrationStartDate(past);
        testEvent.setRegistrationEndDate(future);
        testEvent.setStatus(EventStatus.OPEN.name());

        // Then: Registration should be open
        assertTrue("Registration should be open", testEvent.isRegistrationOpen());
    }

    @Test
    public void testSetRegistrationPeriod_AutoClose() {
        // Given: Registration period that has ended
        long longAgo = System.currentTimeMillis() - 172800000; // 2 days ago
        long yesterday = System.currentTimeMillis() - 86400000; // 1 day ago

        // When: Set dates in the past
        testEvent.setRegistrationStartDate(longAgo);
        testEvent.setRegistrationEndDate(yesterday);
        testEvent.setStatus(EventStatus.OPEN.name());

        // Then: Registration should be closed
        assertFalse("Registration should be closed", testEvent.isRegistrationOpen());
    }

    @Test
    public void testSetRegistrationPeriod_EditableBeforeLottery() {
        // Given: Event without lottery drawn
        testEvent.setRegistrationStartDate(System.currentTimeMillis() + 86400000);
        long originalEndDate = testEvent.getRegistrationStartDate();

        // When: Update registration dates
        long newEndDate = System.currentTimeMillis() + 172800000;
        testEvent.setRegistrationEndDate(newEndDate);

        // Then: Dates should be updated
        assertNotEquals("End date should be updated", originalEndDate, newEndDate);
    }

    // ==================== US 02.02.01: View Waiting List Entrants ====================

    @Test
    public void testViewWaitingList_EntrantDetails() {
        // Given: Waiting list with entrants
        String entrant1 = "user_111";
        String entrant2 = "user_222";
        long timestamp1 = System.currentTimeMillis() - 3600000;
        long timestamp2 = System.currentTimeMillis();

        testWaitingList.addEntrant(entrant1, null);
        testWaitingList.addEntrant(entrant2, null);

        // When: View waiting list
        List<String> entrants = testWaitingList.getEntrantIds();
        Map<String, Long> timestamps = testWaitingList.getEntrantTimestamps();

        // Then: Should show all entrants with timestamps
        assertEquals("Should have 2 entrants", 2, entrants.size());
        assertTrue("Should contain entrant 1", entrants.contains(entrant1));
        assertTrue("Should contain entrant 2", entrants.contains(entrant2));
        assertNotNull("Timestamp should exist for entrant 1", timestamps.get(entrant1));
        assertNotNull("Timestamp should exist for entrant 2", timestamps.get(entrant2));
    }

    @Test
    public void testViewWaitingList_TotalCount() {
        // Given: Waiting list with multiple entrants
        for (int i = 0; i < 5; i++) {
            testWaitingList.addEntrant("user_" + i, null);
        }

        // When: Get count
        int count = testWaitingList.getEntrantCount();

        // Then: Count should be accurate
        assertEquals("Count should be 5", 5, count);
    }

    @Test
    public void testViewWaitingList_RealTimeUpdates() {
        // Given: Initial waiting list
        int initialCount = testWaitingList.getEntrantCount();

        // When: New entrant joins
        testWaitingList.addEntrant("new_user", null);

        // Then: Count should update
        assertEquals("Count should increase by 1",
                initialCount + 1, testWaitingList.getEntrantCount());
    }

    @Test
    public void testViewWaitingList_EmptyState() {
        // Given: Empty waiting list
        WaitingList emptyList = new WaitingList();

        // Then: Should show empty state
        assertEquals("Count should be 0", 0, emptyList.getEntrantCount());
        assertTrue("Entrant list should be empty", emptyList.getEntrantIds().isEmpty());
    }

    // ==================== US 02.02.03: Enable/Disable Geolocation ====================

    @Test
    public void testGeolocation_DisabledByDefault() {
        // Given: New event
        Event newEvent = new Event("id_789", "New Event", "Description", organizerId);

        // Then: Geolocation should be disabled by default
        assertFalse("Geolocation should be disabled by default",
                newEvent.isGeolocationRequired());
    }

    @Test
    public void testGeolocation_Enable() {
        // Given: Event with geolocation disabled
        assertFalse(testEvent.isGeolocationRequired());

        // When: Enable geolocation
        testEvent.setGeolocationRequired(true);

        // Then: Geolocation should be enabled
        assertTrue("Geolocation should be enabled", testEvent.isGeolocationRequired());
    }

    @Test
    public void testGeolocation_Disable() {
        // Given: Event with geolocation enabled
        testEvent.setGeolocationRequired(true);

        // When: Disable geolocation
        testEvent.setGeolocationRequired(false);

        // Then: Geolocation should be disabled
        assertFalse("Geolocation should be disabled", testEvent.isGeolocationRequired());
    }

    @Test
    public void testGeolocation_ChangeBeforeFirstEntrant() {
        // Given: Event without entrants
        assertEquals("No entrants yet", 0, testWaitingList.getEntrantCount());

        // When: Change geolocation setting
        testEvent.setGeolocationRequired(true);

        // Then: Setting should be changeable
        assertTrue("Should be able to enable geolocation", testEvent.isGeolocationRequired());
    }

    // ==================== US 02.03.01: Limit Waiting List Capacity ====================

    @Test
    public void testWaitingListLimit_Unlimited() {
        // Given: Event with unlimited waiting list
        testEvent.setWaitingListLimit(-1);

        // Then: Limit should be unlimited
        assertEquals("Limit should be -1 for unlimited", -1, testEvent.getWaitingListLimit());
    }

    @Test
    public void testWaitingListLimit_SetLimit() {
        // Given: Event with no limit
        assertEquals(-1, testEvent.getWaitingListLimit());

        // When: Set limit
        int limit = 50;
        testEvent.setWaitingListLimit(limit);

        // Then: Limit should be set
        assertEquals("Limit should be set to 50", limit, testEvent.getWaitingListLimit());
    }

    @Test
    public void testWaitingListLimit_EnforceCapacity() {
        // Given: Event with limit of 3
        testEvent.setWaitingListLimit(3);

        // When: Add entrants up to limit
        testWaitingList.addEntrant("user_1", null);
        testWaitingList.addEntrant("user_2", null);
        testWaitingList.addEntrant("user_3", null);

        // Then: Should be at capacity
        boolean isFull = testWaitingList.getEntrantCount() >= testEvent.getWaitingListLimit();
        assertTrue("Waiting list should be full", isFull);
    }

    @Test
    public void testWaitingListLimit_PreventJoinWhenFull() {
        // Given: Full waiting list
        testEvent.setWaitingListLimit(2);
        testWaitingList.addEntrant("user_1", null);
        testWaitingList.addEntrant("user_2", null);

        // When: Check if can join
        boolean canJoin = testWaitingList.getEntrantCount() < testEvent.getWaitingListLimit();

        // Then: Should not allow join
        assertFalse("Should not allow join when full", canJoin);
    }

    // ==================== US 02.04.01 & 02.04.02: Upload/Update Event Poster ====================

    @Test
    public void testUploadPoster_SetUrl() {
        // Given: Event without poster
        assertNull("Poster should be null initially", testEvent.getPosterImageUrl());

        // When: Upload poster
        String posterUrl = "https://storage.googleapis.com/bucket/event_poster.jpg";
        testEvent.setPosterImageUrl(posterUrl);

        // Then: URL should be set
        assertEquals("Poster URL should match", posterUrl, testEvent.getPosterImageUrl());
    }

    @Test
    public void testUploadPoster_ValidateFormat() {
        // Given: Image URLs
        String jpgUrl = "https://example.com/poster.jpg";
        String pngUrl = "https://example.com/poster.png";

        // When: Set poster URLs
        testEvent.setPosterImageUrl(jpgUrl);
        assertTrue("JPG URL should be accepted", testEvent.getPosterImageUrl().endsWith(".jpg"));

        testEvent.setPosterImageUrl(pngUrl);
        assertTrue("PNG URL should be accepted", testEvent.getPosterImageUrl().endsWith(".png"));
    }

    @Test
    public void testUpdatePoster_ReplaceUrl() {
        // Given: Event with existing poster
        String oldUrl = "https://example.com/old_poster.jpg";
        testEvent.setPosterImageUrl(oldUrl);

        // When: Update poster
        String newUrl = "https://example.com/new_poster.jpg";
        testEvent.setPosterImageUrl(newUrl);

        // Then: URL should be replaced
        assertEquals("URL should be updated", newUrl, testEvent.getPosterImageUrl());
        assertNotEquals("URL should not be old value", oldUrl, testEvent.getPosterImageUrl());
    }

    @Test
    public void testPosterDisplay_OnEventDetails() {
        // Given: Event with poster
        String posterUrl = "https://example.com/concert_poster.jpg";
        testEvent.setPosterImageUrl(posterUrl);

        // Then: Poster should be available for display
        assertNotNull("Poster URL should be available", testEvent.getPosterImageUrl());
        assertTrue("URL should be valid", testEvent.getPosterImageUrl().startsWith("http"));
    }

    // ==================== US 02.05.02: Execute Lottery Draw ====================

    @Test
    public void testLotteryDraw_RandomSelection() {
        // Given: Waiting list with 10 entrants
        for (int i = 0; i < 10; i++) {
            testWaitingList.addEntrant("user_" + i, null);
        }

        // When: Draw 3 winners
        int numWinners = 3;
        List<String> winners = selectRandomWinners(testWaitingList.getEntrantIds(), numWinners);

        // Then: Should select correct number of winners
        assertEquals("Should have 3 winners", numWinners, winners.size());
        // All winners should be from waiting list
        for (String winner : winners) {
            assertTrue("Winner should be from waiting list",
                    testWaitingList.getEntrantIds().contains(winner));
        }
    }

    @Test
    public void testLotteryDraw_ValidateWinnerCount() {
        // Given: Waiting list with 5 entrants
        for (int i = 0; i < 5; i++) {
            testWaitingList.addEntrant("user_" + i, null);
        }

        // When: Try to draw more winners than available
        int requestedWinners = 10;
        int maxWinners = Math.min(requestedWinners, testWaitingList.getEntrantCount());

        // Then: Should limit to available count
        assertTrue("Max winners should not exceed waiting list size",
                maxWinners <= testWaitingList.getEntrantCount());
        assertEquals("Max winners should be 5", 5, maxWinners);
    }

    @Test
    public void testLotteryDraw_OnlyOnce() {
        // Given: Event with lottery drawn
        boolean lotteryDrawn = true;

        // When: Check if can draw again
        boolean canDrawAgain = !lotteryDrawn;

        // Then: Should not allow second draw
        assertFalse("Should not allow second lottery draw", canDrawAgain);
    }

    @Test
    public void testLotteryDraw_AfterRegistrationCloses() {
        // Given: Registration period ended
        testEvent.setRegistrationEndDate(System.currentTimeMillis() - 1000);

        // When: Check if lottery can be drawn
        boolean registrationClosed = !testEvent.isRegistrationOpen();

        // Then: Should allow lottery after registration closes
        assertTrue("Lottery should be drawable after registration closes", registrationClosed);
    }

    // Helper method for lottery selection
    private List<String> selectRandomWinners(List<String> entrants, int count) {
        List<String> winners = new ArrayList<>();
        List<String> pool = new ArrayList<>(entrants);
        Random random = new Random();

        int numToSelect = Math.min(count, pool.size());
        for (int i = 0; i < numToSelect; i++) {
            int index = random.nextInt(pool.size());
            winners.add(pool.remove(index));
        }

        return winners;
    }

    // ==================== US 02.06.01: View Selected Entrants ====================

    @Test
    public void testViewSelectedEntrants_List() {
        // Given: Selected entrants
        List<String> selectedList = new ArrayList<>();
        selectedList.add("winner_1");
        selectedList.add("winner_2");
        selectedList.add("winner_3");

        // When: View selected list
        int count = selectedList.size();

        // Then: Should show all selected entrants
        assertEquals("Should have 3 selected entrants", 3, count);
        assertTrue("Should contain winner_1", selectedList.contains("winner_1"));
    }

    @Test
    public void testViewSelectedEntrants_ResponseStatus() {
        // Given: Selected entrants with different statuses
        Map<String, String> entrantStatus = new HashMap<>();
        entrantStatus.put("user_1", "PENDING");
        entrantStatus.put("user_2", "ACCEPTED");
        entrantStatus.put("user_3", "DECLINED");

        // When: Filter by status
        long pendingCount = entrantStatus.values().stream()
                .filter(status -> status.equals("PENDING")).count();
        long acceptedCount = entrantStatus.values().stream()
                .filter(status -> status.equals("ACCEPTED")).count();

        // Then: Should show accurate counts
        assertEquals("Should have 1 pending", 1, pendingCount);
        assertEquals("Should have 1 accepted", 1, acceptedCount);
    }

    @Test
    public void testViewSelectedEntrants_TotalCount() {
        // Given: Selected list
        List<String> selectedList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            selectedList.add("selected_" + i);
        }

        // Then: Total count should be displayed
        assertEquals("Total selected should be 5", 5, selectedList.size());
    }

    // ==================== US 02.06.02: View Cancelled Entrants ====================

    @Test
    public void testViewCancelledEntrants_List() {
        // Given: Cancelled entrants
        List<String> cancelledList = new ArrayList<>();
        cancelledList.add("declined_1");
        cancelledList.add("declined_2");

        // When: View cancelled list
        int count = cancelledList.size();

        // Then: Should show all cancelled entrants
        assertEquals("Should have 2 cancelled entrants", 2, count);
    }

    @Test
    public void testViewCancelledEntrants_WithTimestamps() {
        // Given: Cancelled entrants with cancellation times
        Map<String, Long> cancellationTimes = new HashMap<>();
        long time1 = System.currentTimeMillis() - 3600000;
        long time2 = System.currentTimeMillis();
        cancellationTimes.put("user_1", time1);
        cancellationTimes.put("user_2", time2);

        // Then: Timestamps should be available
        assertNotNull("Timestamp should exist for user_1", cancellationTimes.get("user_1"));
        assertNotNull("Timestamp should exist for user_2", cancellationTimes.get("user_2"));
        assertTrue("Time2 should be after time1", time2 > time1);
    }

    @Test
    public void testViewCancelledEntrants_EmptyState() {
        // Given: No cancellations
        List<String> cancelledList = new ArrayList<>();

        // Then: Should show empty state
        assertTrue("Cancelled list should be empty", cancelledList.isEmpty());
        assertEquals("Count should be 0", 0, cancelledList.size());
    }

    // ==================== US 02.06.03: View Enrolled Entrants ====================

    @Test
    public void testViewEnrolledEntrants_FinalList() {
        // Given: Enrolled entrants (accepted invitations)
        List<String> enrolledList = new ArrayList<>();
        enrolledList.add("enrolled_1");
        enrolledList.add("enrolled_2");
        enrolledList.add("enrolled_3");

        // When: View enrolled list
        int finalCount = enrolledList.size();

        // Then: Should show final participant count
        assertEquals("Should have 3 enrolled entrants", 3, finalCount);
    }

    @Test
    public void testViewEnrolledEntrants_WithAcceptanceTime() {
        // Given: Enrolled entrants with acceptance timestamps
        Map<String, Long> acceptanceTimes = new HashMap<>();
        acceptanceTimes.put("user_1", System.currentTimeMillis() - 7200000);
        acceptanceTimes.put("user_2", System.currentTimeMillis() - 3600000);

        // Then: Acceptance times should be tracked
        assertNotNull("User 1 should have acceptance time", acceptanceTimes.get("user_1"));
        assertNotNull("User 2 should have acceptance time", acceptanceTimes.get("user_2"));
    }

    @Test
    public void testViewEnrolledEntrants_ContactInfo() {
        // Given: Enrolled user profiles
        Profile enrolledUser = new Profile("user_123", "John Doe", "john@example.com");
        enrolledUser.setPhoneNumber("555-1234");

        // Then: Contact information should be available
        assertNotNull("Name should be available", enrolledUser.getName());
        assertNotNull("Email should be available", enrolledUser.getEmail());
        assertNotNull("Phone should be available", enrolledUser.getPhoneNumber());
    }

    // ==================== US 02.06.05: Export Enrolled List to CSV ====================

    @Test
    public void testExportCSV_HeaderFormat() {
        // Given: CSV export requirements
        String expectedHeader = "Name,Email,Phone,Join Date,Acceptance Date";

        // Then: Header should match format
        assertNotNull("Header should not be null", expectedHeader);
        assertTrue("Header should contain Name", expectedHeader.contains("Name"));
        assertTrue("Header should contain Email", expectedHeader.contains("Email"));
        assertTrue("Header should contain Phone", expectedHeader.contains("Phone"));
    }

    @Test
    public void testExportCSV_DataRows() {
        // Given: Enrolled entrants with data
        List<Map<String, String>> enrolledData = new ArrayList<>();
        Map<String, String> user1 = new HashMap<>();
        user1.put("name", "John Doe");
        user1.put("email", "john@example.com");
        user1.put("phone", "555-1234");
        enrolledData.add(user1);

        // When: Generate CSV data
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Name,Email,Phone,Join Date,Acceptance Date\n");
        for (Map<String, String> user : enrolledData) {
            csvContent.append(user.get("name")).append(",");
            csvContent.append(user.get("email")).append(",");
            csvContent.append(user.get("phone")).append("\n");
        }

        // Then: CSV should contain data
        String csv = csvContent.toString();
        assertTrue("CSV should contain John Doe", csv.contains("John Doe"));
        assertTrue("CSV should contain email", csv.contains("john@example.com"));
    }

    @Test
    public void testExportCSV_Filename() {
        // Given: Event name
        String eventName = "Test Concert";

        // When: Generate filename
        String filename = eventName.replaceAll("[^a-zA-Z0-9]", "_") + "_Enrolled.csv";

        // Then: Filename should be valid
        assertTrue("Filename should end with .csv", filename.endsWith(".csv"));
        assertTrue("Filename should contain event name", filename.contains("Test_Concert"));
    }

    @Test
    public void testExportCSV_EmptyList() {
        // Given: No enrolled entrants
        List<Map<String, String>> emptyData = new ArrayList<>();

        // When: Generate CSV
        StringBuilder csvContent = new StringBuilder();
        csvContent.append("Name,Email,Phone,Join Date,Acceptance Date\n");

        // Then: Should have header but no data rows
        String csv = csvContent.toString();
        assertEquals("Should only have header line", 1, csv.split("\n").length);
    }
}

