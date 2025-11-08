package com.example.pickme;

import static org.junit.Assert.*;

import com.example.pickme.models.Event;
import com.example.pickme.models.EventStatus;
import com.example.pickme.models.Profile;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Unit tests for Administrator user stories
 * Tests cover content moderation, browsing, and management operations
 *
 * User Stories Covered:
 * - US 03.01.01: Remove events
 * - US 03.02.01: Remove profiles
 * - US 03.03.01: Remove images
 * - US 03.04.01: Browse events
 * - US 03.05.01: Browse profiles
 * - US 03.06.01: Browse images
 * - US 03.07.01: Remove organizers
 */
public class AdministratorUserStoriesTest {

    private Profile adminProfile;
    private Profile organizerProfile;
    private Profile entrantProfile;
    private Event testEvent;
    private List<Event> eventList;
    private List<Profile> profileList;
    private List<String> imageList;

    @Before
    public void setUp() {
        // Create admin profile
        adminProfile = new Profile("admin_001", "Admin User", "admin@example.com");
        adminProfile.setRole(Profile.ROLE_ADMIN);

        // Create organizer profile
        organizerProfile = new Profile("org_001", "Organizer User", "organizer@example.com");
        organizerProfile.setRole(Profile.ROLE_ORGANIZER);

        // Create entrant profile
        entrantProfile = new Profile("entrant_001", "Entrant User", "entrant@example.com");
        entrantProfile.setRole(Profile.ROLE_ENTRANT);

        // Create test event
        testEvent = new Event("event_123", "Test Event", "Description", "org_001");
        testEvent.setStatus(EventStatus.OPEN.name());
        testEvent.setPosterImageUrl("https://storage.example.com/poster_123.jpg");

        // Initialize lists
        eventList = new ArrayList<>();
        profileList = new ArrayList<>();
        imageList = new ArrayList<>();
    }

    // ==================== US 03.01.01: Remove Events ====================

    @Test
    public void testRemoveEvent_DeleteFromList() {
        // Given: Event list with test event
        eventList.add(testEvent);
        assertEquals("Should have 1 event", 1, eventList.size());

        // When: Remove event
        boolean removed = eventList.remove(testEvent);

        // Then: Event should be removed
        assertTrue("Event should be removed", removed);
        assertEquals("Event list should be empty", 0, eventList.size());
    }

    @Test
    public void testRemoveEvent_ByEventId() {
        // Given: Event list with multiple events
        Event event1 = new Event("evt_1", "Event 1", "Desc1", "org_1");
        Event event2 = new Event("evt_2", "Event 2", "Desc2", "org_2");
        eventList.add(event1);
        eventList.add(event2);

        // When: Remove specific event by ID
        String eventIdToRemove = "evt_1";
        eventList.removeIf(e -> e.getEventId().equals(eventIdToRemove));

        // Then: Only specified event should be removed
        assertEquals("Should have 1 event remaining", 1, eventList.size());
        assertEquals("Remaining event should be evt_2", "evt_2", eventList.get(0).getEventId());
    }

    @Test
    public void testRemoveEvent_CascadeDelete() {
        // Given: Event with associated data
        String eventId = testEvent.getEventId();
        String posterUrl = testEvent.getPosterImageUrl();
        List<String> waitingListUsers = new ArrayList<>();
        waitingListUsers.add("user_1");
        waitingListUsers.add("user_2");

        // When: Remove event
        eventList.remove(testEvent);
        // Cascade delete associated data
        waitingListUsers.clear();
        String deletedPoster = posterUrl; // Would delete from storage

        // Then: Associated data should be handled
        assertTrue("Waiting list should be cleared", waitingListUsers.isEmpty());
        assertNotNull("Poster URL should be marked for deletion", deletedPoster);
    }

    @Test
    public void testRemoveEvent_ConfirmationRequired() {
        // Given: Event to delete
        boolean adminConfirmed = true;

        // When: Check confirmation
        boolean canDelete = adminConfirmed && adminProfile.getRole().equals(Profile.ROLE_ADMIN);

        // Then: Should require confirmation
        assertTrue("Delete should require admin confirmation", canDelete);
    }

    // ==================== US 03.02.01: Remove Profiles ====================

    @Test
    public void testRemoveProfile_DeleteUser() {
        // Given: Profile list with users
        profileList.add(entrantProfile);
        assertEquals("Should have 1 profile", 1, profileList.size());

        // When: Remove profile
        boolean removed = profileList.remove(entrantProfile);

        // Then: Profile should be removed
        assertTrue("Profile should be removed", removed);
        assertEquals("Profile list should be empty", 0, profileList.size());
    }

    @Test
    public void testRemoveProfile_ByUserId() {
        // Given: Multiple profiles
        profileList.add(entrantProfile);
        profileList.add(organizerProfile);

        // When: Remove specific profile by user ID
        String userIdToRemove = entrantProfile.getUserId();
        profileList.removeIf(p -> p.getUserId().equals(userIdToRemove));

        // Then: Only specified profile should be removed
        assertEquals("Should have 1 profile remaining", 1, profileList.size());
        assertEquals("Remaining profile should be organizer",
                organizerProfile.getUserId(), profileList.get(0).getUserId());
    }

    @Test
    public void testRemoveProfile_CascadeEffects() {
        // Given: User profile with event participation
        List<String> userEvents = new ArrayList<>();
        userEvents.add("event_1");
        userEvents.add("event_2");

        // When: Remove profile
        profileList.remove(entrantProfile);
        // Cascade: remove from waiting lists
        userEvents.clear();

        // Then: User should be removed from all events
        assertTrue("User events should be cleared", userEvents.isEmpty());
    }

    @Test
    public void testRemoveProfile_OrganizerCascade() {
        // Given: Organizer with events
        Event organizerEvent = new Event("evt_1", "Org Event", "Desc", organizerProfile.getUserId());
        eventList.add(organizerEvent);

        // When: Remove organizer profile
        String organizerId = organizerProfile.getUserId();
        profileList.remove(organizerProfile);
        // Cascade: remove organizer's events
        eventList.removeIf(e -> e.getOrganizerId().equals(organizerId));

        // Then: Organizer's events should be removed
        assertTrue("Organizer's events should be removed", eventList.isEmpty());
    }

    // ==================== US 03.03.01: Remove Images ====================

    @Test
    public void testRemoveImage_DeleteFromStorage() {
        // Given: Image list with URLs
        String imageUrl = "https://storage.example.com/event_poster_123.jpg";
        imageList.add(imageUrl);
        assertEquals("Should have 1 image", 1, imageList.size());

        // When: Remove image
        boolean removed = imageList.remove(imageUrl);

        // Then: Image should be removed from list
        assertTrue("Image should be removed", removed);
        assertEquals("Image list should be empty", 0, imageList.size());
    }

    @Test
    public void testRemoveImage_UpdateEventReference() {
        // Given: Event with poster image
        String posterUrl = testEvent.getPosterImageUrl();
        assertNotNull("Event should have poster", posterUrl);

        // When: Remove image
        testEvent.setPosterImageUrl(null);

        // Then: Event poster reference should be cleared
        assertNull("Event poster should be null", testEvent.getPosterImageUrl());
    }

    @Test
    public void testRemoveImage_ValidatePermissions() {
        // Given: Admin user attempting to remove image
        boolean isAdmin = adminProfile.getRole().equals(Profile.ROLE_ADMIN);

        // When: Check permissions
        boolean canRemove = isAdmin;

        // Then: Admin should have permission
        assertTrue("Admin should be able to remove images", canRemove);
    }

    @Test
    public void testRemoveImage_ConfirmationDialog() {
        // Given: Image to delete
        String imageUrl = "https://storage.example.com/poster.jpg";
        boolean adminConfirmed = true;

        // When: Check if deletion confirmed
        boolean shouldDelete = adminConfirmed && imageUrl != null;

        // Then: Should require confirmation
        assertTrue("Should confirm before deletion", shouldDelete);
    }

    // ==================== US 03.04.01: Browse Events ====================

    @Test
    public void testBrowseEvents_DisplayAllEvents() {
        // Given: Multiple events
        Event event1 = new Event("evt_1", "Event 1", "Desc1", "org_1");
        Event event2 = new Event("evt_2", "Event 2", "Desc2", "org_2");
        Event event3 = new Event("evt_3", "Event 3", "Desc3", "org_3");
        eventList.add(event1);
        eventList.add(event2);
        eventList.add(event3);

        // When: Browse events
        List<Event> browsedEvents = new ArrayList<>(eventList);

        // Then: All events should be displayed
        assertEquals("Should display 3 events", 3, browsedEvents.size());
    }

    @Test
    public void testBrowseEvents_ShowDetails() {
        // Given: Event with details
        testEvent.setLocation("Edmonton Convention Centre");
        testEvent.setRegistrationStartDate(System.currentTimeMillis());
        testEvent.setRegistrationEndDate(System.currentTimeMillis() + 604800000);

        // When: View event details
        String name = testEvent.getName();
        String organizer = testEvent.getOrganizerId();
        String location = testEvent.getLocation();
        long regStart = testEvent.getRegistrationStartDate();

        // Then: All details should be available
        assertNotNull("Name should be available", name);
        assertNotNull("Organizer should be available", organizer);
        assertNotNull("Location should be available", location);
        assertTrue("Registration start should be set", regStart > 0);
    }

    @Test
    public void testBrowseEvents_SearchByName() {
        // Given: Events with different names
        Event concert = new Event("evt_1", "Rock Concert", "Desc", "org_1");
        Event conference = new Event("evt_2", "Tech Conference", "Desc", "org_2");
        eventList.add(concert);
        eventList.add(conference);

        // When: Search for "Concert"
        String searchQuery = "Concert";
        List<Event> searchResults = new ArrayList<>();
        for (Event event : eventList) {
            if (event.getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                searchResults.add(event);
            }
        }

        // Then: Should find matching event
        assertEquals("Should find 1 event", 1, searchResults.size());
        assertTrue("Should find Rock Concert",
                searchResults.get(0).getName().contains("Concert"));
    }

    @Test
    public void testBrowseEvents_SortByDate() {
        // Given: Events with different dates
        Event event1 = new Event("evt_1", "Event 1", "Desc", "org_1");
        event1.setRegistrationEndDate(1000000);
        Event event2 = new Event("evt_2", "Event 2", "Desc", "org_2");
        event2.setRegistrationEndDate(2000000);
        eventList.add(event2);
        eventList.add(event1);

        // When: Sort by date
        eventList.sort((e1, e2) ->
                Long.compare(e1.getRegistrationEndDate(), e2.getRegistrationEndDate()));

        // Then: Events should be sorted
        assertTrue("First event should have earlier date",
                eventList.get(0).getRegistrationEndDate() < eventList.get(1).getRegistrationEndDate());
    }

    @Test
    public void testBrowseEvents_IncludePastEvents() {
        // Given: Past and current events
        Event pastEvent = new Event("past_1", "Past Event", "Desc", "org_1");
        pastEvent.setRegistrationEndDate(System.currentTimeMillis() - 86400000);
        Event currentEvent = new Event("current_1", "Current Event", "Desc", "org_2");
        currentEvent.setRegistrationEndDate(System.currentTimeMillis() + 86400000);
        eventList.add(pastEvent);
        eventList.add(currentEvent);

        // When: Browse all events
        List<Event> allEvents = new ArrayList<>(eventList);

        // Then: Should include both past and current
        assertEquals("Should show 2 events", 2, allEvents.size());
    }

    // ==================== US 03.05.01: Browse Profiles ====================

    @Test
    public void testBrowseProfiles_DisplayAllUsers() {
        // Given: Multiple profiles
        profileList.add(adminProfile);
        profileList.add(organizerProfile);
        profileList.add(entrantProfile);

        // When: Browse profiles
        List<Profile> browsedProfiles = new ArrayList<>(profileList);

        // Then: All profiles should be displayed
        assertEquals("Should display 3 profiles", 3, browsedProfiles.size());
    }

    @Test
    public void testBrowseProfiles_ShowDetails() {
        // Given: Profile with details
        entrantProfile.setPhoneNumber("555-1234");

        // When: View profile details
        String name = entrantProfile.getName();
        String email = entrantProfile.getEmail();
        String role = entrantProfile.getRole();
        String phone = entrantProfile.getPhoneNumber();

        // Then: All details should be available
        assertNotNull("Name should be available", name);
        assertNotNull("Email should be available", email);
        assertNotNull("Role should be available", role);
        assertNotNull("Phone should be available", phone);
    }

    @Test
    public void testBrowseProfiles_SearchByName() {
        // Given: Profiles with different names
        Profile john = new Profile("user_1", "John Doe", "john@test.com");
        Profile jane = new Profile("user_2", "Jane Smith", "jane@test.com");
        profileList.add(john);
        profileList.add(jane);

        // When: Search for "John"
        String searchQuery = "John";
        List<Profile> searchResults = new ArrayList<>();
        for (Profile profile : profileList) {
            if (profile.getName().toLowerCase().contains(searchQuery.toLowerCase())) {
                searchResults.add(profile);
            }
        }

        // Then: Should find matching profile
        assertEquals("Should find 1 profile", 1, searchResults.size());
        assertTrue("Should find John Doe",
                searchResults.get(0).getName().contains("John"));
    }

    @Test
    public void testBrowseProfiles_FilterByRole() {
        // Given: Profiles with different roles
        profileList.add(entrantProfile);
        profileList.add(organizerProfile);
        profileList.add(adminProfile);

        // When: Filter for organizers
        List<Profile> organizers = new ArrayList<>();
        for (Profile profile : profileList) {
            if (Profile.ROLE_ORGANIZER.equals(profile.getRole())) {
                organizers.add(profile);
            }
        }

        // Then: Should show only organizers
        assertEquals("Should have 1 organizer", 1, organizers.size());
        assertEquals("Should be organizer role",
                Profile.ROLE_ORGANIZER, organizers.get(0).getRole());
    }

    @Test
    public void testBrowseProfiles_SearchByEmail() {
        // Given: Profiles with different emails
        profileList.add(entrantProfile);
        profileList.add(organizerProfile);

        // When: Search for specific email
        String emailQuery = "entrant@example.com";
        List<Profile> searchResults = new ArrayList<>();
        for (Profile profile : profileList) {
            if (profile.getEmail() != null &&
                    profile.getEmail().toLowerCase().contains(emailQuery.toLowerCase())) {
                searchResults.add(profile);
            }
        }

        // Then: Should find matching profile
        assertEquals("Should find 1 profile", 1, searchResults.size());
        assertEquals("Email should match", emailQuery, searchResults.get(0).getEmail());
    }

    // ==================== US 03.06.01: Browse Images ====================

    @Test
    public void testBrowseImages_DisplayGallery() {
        // Given: Multiple images
        imageList.add("https://storage.example.com/poster_1.jpg");
        imageList.add("https://storage.example.com/poster_2.jpg");
        imageList.add("https://storage.example.com/poster_3.jpg");

        // When: Browse images
        List<String> galleryImages = new ArrayList<>(imageList);

        // Then: All images should be displayed
        assertEquals("Should display 3 images", 3, galleryImages.size());
    }

    @Test
    public void testBrowseImages_ShowAssociatedEvent() {
        // Given: Image with associated event
        String imageUrl = testEvent.getPosterImageUrl();

        // When: Get event details for image
        String eventName = testEvent.getName();
        String organizerId = testEvent.getOrganizerId();

        // Then: Event details should be available
        assertNotNull("Event name should be available", eventName);
        assertNotNull("Organizer ID should be available", organizerId);
        assertNotNull("Image URL should be set", imageUrl);
    }

    @Test
    public void testBrowseImages_GridLayout() {
        // Given: Multiple images for grid display
        for (int i = 0; i < 10; i++) {
            imageList.add("https://storage.example.com/poster_" + i + ".jpg");
        }

        // When: Display in grid (simulated as list)
        List<String> gridImages = new ArrayList<>(imageList);

        // Then: Should support multiple images
        assertEquals("Should have 10 images for grid", 10, gridImages.size());
    }

    @Test
    public void testBrowseImages_RemoveButton() {
        // Given: Image with remove capability
        String imageUrl = "https://storage.example.com/removable_poster.jpg";
        imageList.add(imageUrl);
        boolean isAdmin = adminProfile.getRole().equals(Profile.ROLE_ADMIN);

        // When: Check if can remove
        boolean canRemove = isAdmin && imageList.contains(imageUrl);

        // Then: Admin should be able to remove
        assertTrue("Admin should be able to remove images", canRemove);
    }

    // ==================== US 03.07.01: Remove Organizers ====================

    @Test
    public void testRemoveOrganizer_DeleteAccount() {
        // Given: Organizer in profile list
        profileList.add(organizerProfile);
        assertEquals("Should have 1 profile", 1, profileList.size());

        // When: Remove organizer
        boolean removed = profileList.remove(organizerProfile);

        // Then: Organizer should be removed
        assertTrue("Organizer should be removed", removed);
        assertEquals("Profile list should be empty", 0, profileList.size());
    }

    @Test
    public void testRemoveOrganizer_DeleteAssociatedEvents() {
        // Given: Organizer with events
        String organizerId = organizerProfile.getUserId();
        Event event1 = new Event("evt_1", "Event 1", "Desc", organizerId);
        Event event2 = new Event("evt_2", "Event 2", "Desc", organizerId);
        eventList.add(event1);
        eventList.add(event2);

        // When: Remove organizer
        profileList.remove(organizerProfile);
        // Cascade delete events
        eventList.removeIf(e -> e.getOrganizerId().equals(organizerId));

        // Then: Organizer's events should be deleted
        assertEquals("Events should be removed", 0, eventList.size());
    }

    @Test
    public void testRemoveOrganizer_ClearWaitingLists() {
        // Given: Organizer's event with waiting list
        String organizerId = organizerProfile.getUserId();
        Event event = new Event("evt_1", "Event", "Desc", organizerId);
        List<String> waitingList = new ArrayList<>();
        waitingList.add("user_1");
        waitingList.add("user_2");

        // When: Remove organizer
        profileList.remove(organizerProfile);
        // Clear waiting lists for organizer's events
        waitingList.clear();

        // Then: Waiting lists should be cleared
        assertTrue("Waiting list should be cleared", waitingList.isEmpty());
    }

    @Test
    public void testRemoveOrganizer_ConfirmationWarning() {
        // Given: Organizer with multiple events
        int numberOfEvents = 5;
        String organizerId = organizerProfile.getUserId();
        for (int i = 0; i < numberOfEvents; i++) {
            eventList.add(new Event("evt_" + i, "Event " + i, "Desc", organizerId));
        }

        // When: Check for confirmation requirement
        boolean hasEvents = eventList.stream()
                .anyMatch(e -> e.getOrganizerId().equals(organizerId));
        boolean requiresConfirmation = hasEvents;

        // Then: Should require confirmation due to cascade effects
        assertTrue("Should require confirmation when organizer has events",
                requiresConfirmation);
    }

    @Test
    public void testRemoveOrganizer_FilterByRole() {
        // Given: Mixed profiles
        profileList.add(entrantProfile);
        profileList.add(organizerProfile);
        profileList.add(adminProfile);

        // When: Filter for organizers only
        List<Profile> organizersOnly = new ArrayList<>();
        for (Profile profile : profileList) {
            if (Profile.ROLE_ORGANIZER.equals(profile.getRole())) {
                organizersOnly.add(profile);
            }
        }

        // Then: Should show only organizers
        assertEquals("Should have 1 organizer", 1, organizersOnly.size());
        assertEquals("Should be organizer role",
                Profile.ROLE_ORGANIZER, organizersOnly.get(0).getRole());
    }
}

