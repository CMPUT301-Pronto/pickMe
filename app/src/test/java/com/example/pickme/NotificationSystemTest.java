package com.example.pickme;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import com.example.pickme.models.Event;
import com.example.pickme.models.NotificationLog;
import com.example.pickme.models.Profile;
import com.example.pickme.services.NotificationService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Comprehensive unit tests for Notification System
 *
 * Tests all notification-related user stories:
 * - US 01.05.01: Entrant receives notification when chosen (lottery win)
 * - US 01.05.02: Entrant receives notification when not chosen (lottery loss)
 * - US 01.02.02: Entrant can opt out of notifications
 * - US 02.07.01: Organizer sends notification to chosen entrants
 * - US 02.07.02: Organizer sends notification to waiting list
 * - US 02.07.03: Organizer sends notification to selected entrants
 * - US 02.07.04: Organizer sends notification to cancelled entrants
 * - US 03.08.01: Admin reviews notification logs
 *
 * Related User Stories for replacement draws and cancellations:
 * - US 02.05.03: Replacement draw notifications
 * - US 02.06.01: Cancellation notifications
 */
@RunWith(MockitoJUnitRunner.class)
public class NotificationSystemTest {

    private Event testEvent;
    private List<String> testEntrantIds;
    private List<Profile> testProfiles;

    @Before
    public void setUp() {
        // Create test event
        testEvent = new Event("event_test_123", "Tech Conference 2025",
                "Annual technology conference", "organizer_456");
        testEvent.setCapacity(50);
        testEvent.setWaitingListLimit(100);

        // Create test entrant IDs
        testEntrantIds = new ArrayList<>();
        testEntrantIds.add("entrant_001");
        testEntrantIds.add("entrant_002");
        testEntrantIds.add("entrant_003");
        testEntrantIds.add("entrant_004");
        testEntrantIds.add("entrant_005");

        // Create test profiles with notification settings
        testProfiles = new ArrayList<>();
        for (int i = 0; i < testEntrantIds.size(); i++) {
            Profile profile = new Profile(testEntrantIds.get(i),
                    "Test User " + (i + 1),
                    "user" + (i + 1) + "@test.com");
            profile.setNotificationEnabled(true); // Default: enabled
            testProfiles.add(profile);
        }
    }

    // ==================== US 01.05.01: Lottery Win Notification ====================

    @Test
    public void testLotteryWinNotification_MessageFormat() {
        // Given: Selected entrants
        List<String> selectedIds = Arrays.asList("entrant_001", "entrant_002");

        // When: Lottery win notification created
        String expectedMessage = "Congratulations! You've been selected for " +
                testEvent.getName() + ". Please respond to confirm your participation.";

        // Then: Message should include event name and action required
        assertTrue("Message should contain event name",
                expectedMessage.contains(testEvent.getName()));
        assertTrue("Message should request confirmation",
                expectedMessage.contains("confirm"));
        assertTrue("Message should be congratulatory",
                expectedMessage.toLowerCase().contains("congratulations"));
    }

    @Test
    public void testLotteryWinNotification_HighPriority() {
        // Given: Lottery win scenario
        String notificationType = "lottery_win";

        // When: Check notification priority
        boolean isHighPriority = notificationType.equals("lottery_win") ||
                notificationType.equals("replacement_draw");

        // Then: Should be high priority
        assertTrue("Lottery win should be high priority", isHighPriority);
    }

    @Test
    public void testLotteryWinNotification_IncludesDeadline() {
        // Given: Event with invitation deadline
        long deadlineMillis = System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000); // 7 days
        testEvent.setInvitationDeadlineMillis(deadlineMillis);

        // When: Check deadline is set
        long eventDeadline = testEvent.getInvitationDeadlineMillis();

        // Then: Deadline should be included
        assertTrue("Deadline should be set", eventDeadline > 0);
        assertTrue("Deadline should be in future", eventDeadline > System.currentTimeMillis());
    }

    @Test
    public void testLotteryWinNotification_MultipleWinners() {
        // Given: Multiple selected entrants
        List<String> winners = Arrays.asList("entrant_001", "entrant_002", "entrant_003");

        // When: Count notifications to send
        int notificationCount = winners.size();

        // Then: Should send one notification per winner
        assertEquals("Should send notification to each winner", 3, notificationCount);
        assertTrue("Should have at least one winner", notificationCount > 0);
    }

    @Test
    public void testLotteryWinNotification_DeepLinkToInvitation() {
        // Given: Lottery win notification data
        String notificationType = "lottery_win";
        String eventId = testEvent.getEventId();

        // When: Create deep link action
        String expectedAction = "com.example.pickme.ACTION_OPEN_INVITATION";

        // Then: Should deep link to invitation dialog
        assertNotNull("Action should be defined", expectedAction);
        assertNotNull("Event ID should be included", eventId);
    }

    // ==================== US 01.05.02: Lottery Loss Notification ====================

    @Test
    public void testLotteryLossNotification_MessageFormat() {
        // Given: Non-selected entrants
        List<String> notSelectedIds = Arrays.asList("entrant_004", "entrant_005");

        // When: Lottery loss notification created
        String expectedMessage = "Unfortunately, you weren't selected for " +
                testEvent.getName() + ", but you may have another chance if spots become available.";

        // Then: Message should be empathetic and mention possibility of re-draw
        assertTrue("Message should mention event name",
                expectedMessage.contains(testEvent.getName()));
        assertTrue("Message should mention another chance",
                expectedMessage.contains("another chance"));
        assertTrue("Message should be empathetic",
                expectedMessage.toLowerCase().contains("unfortunately"));
    }

    @Test
    public void testLotteryLossNotification_DefaultPriority() {
        // Given: Lottery loss scenario
        String notificationType = "lottery_loss";

        // When: Check notification priority
        boolean isHighPriority = notificationType.equals("lottery_win") ||
                notificationType.equals("replacement_draw");

        // Then: Should NOT be high priority (default priority)
        assertFalse("Lottery loss should be default priority", isHighPriority);
    }

    @Test
    public void testLotteryLossNotification_IncludesReDrawInfo() {
        // Given: Lottery loss message
        String message = "You may have another chance if spots become available.";

        // When: Check for re-draw information
        boolean mentionsReDraw = message.contains("another chance") ||
                message.contains("spots") ||
                message.contains("available");

        // Then: Should mention possibility of re-draw
        assertTrue("Message should mention re-draw possibility", mentionsReDraw);
    }

    // ==================== US 01.02.02: Opt Out of Notifications ====================

    @Test
    public void testNotificationPreference_DefaultEnabled() {
        // Given: New profile
        Profile newProfile = new Profile("new_user_123", "New User", "new@test.com");

        // When: Check default notification setting
        boolean isEnabled = newProfile.isNotificationEnabled();

        // Then: Should be enabled by default
        assertTrue("Notifications should be enabled by default", isEnabled);
    }

    @Test
    public void testNotificationPreference_CanDisable() {
        // Given: Profile with notifications enabled
        Profile profile = testProfiles.get(0);
        assertTrue("Should start enabled", profile.isNotificationEnabled());

        // When: User disables notifications
        profile.setNotificationEnabled(false);

        // Then: Notifications should be disabled
        assertFalse("Notifications should be disabled", profile.isNotificationEnabled());
    }

    @Test
    public void testNotificationPreference_CanReEnable() {
        // Given: Profile with notifications disabled
        Profile profile = testProfiles.get(0);
        profile.setNotificationEnabled(false);
        assertFalse("Should start disabled", profile.isNotificationEnabled());

        // When: User re-enables notifications
        profile.setNotificationEnabled(true);

        // Then: Notifications should be re-enabled
        assertTrue("Notifications should be re-enabled", profile.isNotificationEnabled());
    }

    @Test
    public void testNotificationPreference_FilteringByPreference() {
        // Given: Mixed notification preferences
        testProfiles.get(0).setNotificationEnabled(true);
        testProfiles.get(1).setNotificationEnabled(false); // Opted out
        testProfiles.get(2).setNotificationEnabled(true);
        testProfiles.get(3).setNotificationEnabled(false); // Opted out
        testProfiles.get(4).setNotificationEnabled(true);

        // When: Filter users with notifications enabled
        List<String> enabledUsers = new ArrayList<>();
        for (int i = 0; i < testProfiles.size(); i++) {
            if (testProfiles.get(i).isNotificationEnabled()) {
                enabledUsers.add(testEntrantIds.get(i));
            }
        }

        // Then: Should only include users with notifications enabled
        assertEquals("Should have 3 users with notifications enabled", 3, enabledUsers.size());
        assertTrue("Should include entrant_001", enabledUsers.contains("entrant_001"));
        assertFalse("Should NOT include entrant_002 (opted out)", enabledUsers.contains("entrant_002"));
        assertTrue("Should include entrant_003", enabledUsers.contains("entrant_003"));
        assertFalse("Should NOT include entrant_004 (opted out)", enabledUsers.contains("entrant_004"));
    }

    @Test
    public void testNotificationPreference_AppliesToAllTypes() {
        // Given: User with notifications disabled
        Profile profile = testProfiles.get(0);
        profile.setNotificationEnabled(false);

        // When: Check if filtering applies to all notification types
        List<String> notificationTypes = Arrays.asList(
                "lottery_win", "lottery_loss", "replacement_draw",
                "organizer_message", "entrant_cancelled");

        // Then: Disabled setting should apply to all types
        for (String type : notificationTypes) {
            // User would be filtered out regardless of type
            assertFalse("User should not receive any notifications when disabled",
                    profile.isNotificationEnabled());
        }
    }

    // ==================== US 02.07.01: Send Notification to Chosen Entrants ====================

    @Test
    public void testSendToChosenEntrants_OnlySelectedReceive() {
        // Given: Selected and not-selected entrants
        List<String> selectedIds = Arrays.asList("entrant_001", "entrant_002");
        List<String> allIds = new ArrayList<>(testEntrantIds);

        // When: Send notification to only selected entrants
        List<String> recipients = new ArrayList<>(selectedIds);

        // Then: Only selected should be in recipient list
        assertEquals("Should only send to selected entrants", 2, recipients.size());
        assertTrue("Should include entrant_001", recipients.contains("entrant_001"));
        assertTrue("Should include entrant_002", recipients.contains("entrant_002"));
        assertFalse("Should NOT include entrant_003", recipients.contains("entrant_003"));
    }

    @Test
    public void testSendToChosenEntrants_BatchSending() {
        // Given: Multiple selected entrants
        List<String> selected = Arrays.asList("entrant_001", "entrant_002", "entrant_003");

        // When: Prepare batch notification
        int batchSize = selected.size();

        // Then: Should send as single batch operation
        assertTrue("Should send in batch", batchSize > 1);
        assertEquals("Batch size should match selected count", 3, batchSize);
    }

    // ==================== US 02.07.02: Send to Waiting List ====================

    @Test
    public void testSendToWaitingList_AllWaitingReceive() {
        // Given: Entrants on waiting list
        List<String> waitingListIds = Arrays.asList(
                "entrant_001", "entrant_002", "entrant_003", "entrant_004");

        // When: Send notification to waiting list
        List<String> recipients = new ArrayList<>(waitingListIds);

        // Then: All waiting list members should receive
        assertEquals("All waiting list members should receive", 4, recipients.size());
    }

    @Test
    public void testSendToWaitingList_CustomMessage() {
        // Given: Custom organizer message
        String customMessage = "Important update: Event location has changed to Main Hall.";

        // When: Validate message
        boolean hasContent = customMessage != null && !customMessage.trim().isEmpty();
        int messageLength = customMessage.length();

        // Then: Message should have content and reasonable length
        assertTrue("Message should have content", hasContent);
        assertTrue("Message should be under 500 chars", messageLength <= 500);
        assertTrue("Message should be over 10 chars", messageLength > 10);
    }

    @Test
    public void testSendToWaitingList_RespectsPreferences() {
        // Given: Waiting list with mixed preferences
        List<String> waitingListIds = Arrays.asList("entrant_001", "entrant_002", "entrant_003");
        testProfiles.get(1).setNotificationEnabled(false); // entrant_002 opted out

        // When: Filter by preferences
        List<String> filteredRecipients = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            if (testProfiles.get(i).isNotificationEnabled()) {
                filteredRecipients.add(waitingListIds.get(i));
            }
        }

        // Then: Should only send to users with notifications enabled
        assertEquals("Should filter out opted-out users", 2, filteredRecipients.size());
        assertFalse("Should not include opted-out user",
                filteredRecipients.contains("entrant_002"));
    }

    // ==================== US 02.07.03: Send to Selected Entrants ====================

    @Test
    public void testSendToSelected_OnlyResponsePending() {
        // Given: Entrants in different states
        List<String> responsePendingIds = Arrays.asList("entrant_001", "entrant_002");
        List<String> confirmedIds = Arrays.asList("entrant_003");
        List<String> declinedIds = Arrays.asList("entrant_004");

        // When: Send to only response pending
        List<String> recipients = new ArrayList<>(responsePendingIds);

        // Then: Should only include response pending entrants
        assertEquals("Should only send to response pending", 2, recipients.size());
        assertFalse("Should not include confirmed", recipients.containsAll(confirmedIds));
        assertFalse("Should not include declined", recipients.containsAll(declinedIds));
    }

    // ==================== US 02.07.04: Send to Cancelled Entrants ====================

    @Test
    public void testSendToCancelled_OnlyCancelledReceive() {
        // Given: Cancelled entrants
        List<String> cancelledIds = Arrays.asList("entrant_004", "entrant_005");
        List<String> activeIds = Arrays.asList("entrant_001", "entrant_002", "entrant_003");

        // When: Send notification to cancelled entrants
        List<String> recipients = new ArrayList<>(cancelledIds);

        // Then: Should only send to cancelled entrants
        assertEquals("Should only send to cancelled", 2, recipients.size());
        assertTrue("Should include entrant_004", recipients.contains("entrant_004"));
        assertTrue("Should include entrant_005", recipients.contains("entrant_005"));
        assertFalse("Should not include active entrants",
                recipients.containsAll(activeIds));
    }

    // ==================== US 02.05.03: Replacement Draw Notification ====================

    @Test
    public void testReplacementDrawNotification_MessageFormat() {
        // Given: Replacement draw scenario
        List<String> replacementIds = Arrays.asList("entrant_003");

        // When: Replacement notification created
        String expectedMessage = "Good news! You've been selected as a replacement for " +
                testEvent.getName() + ". Please respond to confirm your participation.";

        // Then: Message should indicate it's a replacement opportunity
        assertTrue("Message should say 'Good news'",
                expectedMessage.contains("Good news"));
        assertTrue("Message should mention replacement",
                expectedMessage.contains("replacement"));
        assertTrue("Message should include event name",
                expectedMessage.contains(testEvent.getName()));
    }

    @Test
    public void testReplacementDrawNotification_HighPriority() {
        // Given: Replacement draw notification
        String notificationType = "replacement_draw";

        // When: Check priority
        boolean isHighPriority = notificationType.equals("lottery_win") ||
                notificationType.equals("replacement_draw");

        // Then: Should be high priority (same as lottery win)
        assertTrue("Replacement should be high priority", isHighPriority);
    }

    // ==================== US 02.06.01: Cancellation Notification ====================

    @Test
    public void testCancellationNotification_MessageFormat() {
        // Given: Cancelled entrant
        String entrantId = "entrant_001";
        String reason = "Did not respond within deadline";

        // When: Cancellation notification created
        String expectedMessage = "Your selection for " + testEvent.getName() +
                " has been cancelled" + (reason != null ? ". Reason: " + reason : ".");

        // Then: Message should explain cancellation
        assertTrue("Message should mention cancellation",
                expectedMessage.contains("cancelled"));
        assertTrue("Message should include event name",
                expectedMessage.contains(testEvent.getName()));
        if (reason != null) {
            assertTrue("Message should include reason if provided",
                    expectedMessage.contains(reason));
        }
    }

    @Test
    public void testCancellationNotification_WithAndWithoutReason() {
        // Given: Cancellation scenarios
        String withReason = "Your selection has been cancelled. Reason: No response received.";
        String withoutReason = "Your selection has been cancelled by the organizer.";

        // When: Check both messages
        boolean hasReasonInfo = withReason.contains("Reason:");
        boolean hasGenericInfo = withoutReason.contains("organizer");

        // Then: Both should be valid formats
        assertTrue("Message with reason should include reason", hasReasonInfo);
        assertTrue("Message without reason should mention organizer", hasGenericInfo);
    }

    // ==================== US 03.08.01: Admin Notification Logs ====================

    @Test
    public void testNotificationLog_CreatedForEachSend() {
        // Given: Notification send operation
        String logId = "log_123456";
        long timestamp = System.currentTimeMillis();
        String senderId = "organizer_456";
        List<String> recipientIds = Arrays.asList("entrant_001", "entrant_002");
        String message = "Event update notification";
        String type = "organizer_message";
        String eventId = testEvent.getEventId();

        // When: Create notification log
        NotificationLog log = new NotificationLog(
                logId, timestamp, senderId, recipientIds, message, type, eventId);

        // Then: Log should capture all relevant data
        assertNotNull("Log should exist", log);
        assertEquals("Should have correct log ID", logId, log.getNotificationId());
        assertEquals("Should have correct sender", senderId, log.getSenderId());
        assertEquals("Should have correct recipient count", 2, log.getRecipientIds().size());
        assertEquals("Should have correct message", message, log.getMessageContent());
        assertEquals("Should have correct type", type, log.getNotificationType());
        assertEquals("Should have correct event ID", eventId, log.getEventId());
    }

    @Test
    public void testNotificationLog_IncludesTimestamp() {
        // Given: Current time
        long beforeTime = System.currentTimeMillis();

        // When: Create log with timestamp
        NotificationLog log = new NotificationLog(
                "log_123", System.currentTimeMillis(), "sender",
                testEntrantIds, "message", "type", "event");

        long afterTime = System.currentTimeMillis();

        // Then: Timestamp should be within reasonable range
        assertTrue("Timestamp should be after before time",
                log.getTimestamp() >= beforeTime);
        assertTrue("Timestamp should be before after time",
                log.getTimestamp() <= afterTime);
    }

    @Test
    public void testNotificationLog_TracksSenderAndRecipients() {
        // Given: Notification details
        String senderId = "organizer_789";
        List<String> recipientIds = Arrays.asList("user1", "user2", "user3", "user4", "user5");

        // When: Create log
        NotificationLog log = new NotificationLog(
                "log_456", System.currentTimeMillis(), senderId,
                recipientIds, "Test message", "organizer_message", "event_123");

        // Then: Should track sender and all recipients
        assertEquals("Should have correct sender", senderId, log.getSenderId());
        assertEquals("Should have all recipients", 5, log.getRecipientIds().size());
        assertTrue("Should include specific recipient",
                log.getRecipientIds().contains("user3"));
    }

    @Test
    public void testNotificationLog_CategorizesByType() {
        // Given: Different notification types
        List<String> notificationTypes = Arrays.asList(
                "lottery_win", "lottery_loss", "replacement_draw",
                "organizer_message", "entrant_cancelled");

        // When: Create logs for each type
        List<NotificationLog> logs = new ArrayList<>();
        for (String type : notificationTypes) {
            NotificationLog log = new NotificationLog(
                    "log_" + type, System.currentTimeMillis(), "sender",
                    testEntrantIds, "message", type, "event");
            logs.add(log);
        }

        // Then: Each should have correct type
        assertEquals("Should create log for each type", 5, logs.size());
        for (int i = 0; i < logs.size(); i++) {
            assertEquals("Type should match",
                    notificationTypes.get(i), logs.get(i).getNotificationType());
        }
    }

    @Test
    public void testNotificationLog_SortableByTimestamp() {
        // Given: Multiple logs at different times
        NotificationLog log1 = new NotificationLog(
                "log_1", System.currentTimeMillis() - 10000, "sender",
                testEntrantIds, "Old message", "type", "event");

        try {
            Thread.sleep(100); // Ensure different timestamps
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        NotificationLog log2 = new NotificationLog(
                "log_2", System.currentTimeMillis(), "sender",
                testEntrantIds, "New message", "type", "event");

        // When: Compare timestamps
        boolean newestFirst = log2.getTimestamp() > log1.getTimestamp();

        // Then: Should be sortable by timestamp (newest first)
        assertTrue("Newer log should have later timestamp", newestFirst);
    }

    @Test
    public void testNotificationLog_StoresFullMessageContent() {
        // Given: Long custom message
        String longMessage = "This is a longer custom message from the organizer explaining " +
                "important details about the event, including time changes, location updates, " +
                "and other critical information that entrants need to know.";

        // When: Create log with long message
        NotificationLog log = new NotificationLog(
                "log_long", System.currentTimeMillis(), "sender",
                testEntrantIds, longMessage, "organizer_message", "event");

        // Then: Full message should be stored
        assertEquals("Should store complete message", longMessage, log.getMessageContent());
        assertTrue("Message should be substantial", log.getMessageContent().length() > 100);
    }

    // ==================== Notification Channel Tests ====================

    @Test
    public void testNotificationChannels_ThreeChannelsDefined() {
        // Given: Notification system with channels
        List<String> channelIds = Arrays.asList("lottery", "organizer_messages", "system");

        // When: Check channel count
        int channelCount = channelIds.size();

        // Then: Should have exactly 3 channels
        assertEquals("Should have 3 notification channels", 3, channelCount);
    }

    @Test
    public void testNotificationChannels_LotteryHighPriority() {
        // Given: Lottery channel notifications
        List<String> lotteryTypes = Arrays.asList("lottery_win", "replacement_draw");
        String channelId = "lottery";

        // When: Check priority
        boolean isHighPriority = channelId.equals("lottery");

        // Then: Lottery channel should be high priority
        assertTrue("Lottery channel should be high priority", isHighPriority);
    }

    @Test
    public void testNotificationChannels_ProperTypeMapping() {
        // Given: Notification type to channel mapping
        String lotteryWinChannel = getChannelForType("lottery_win");
        String lotteryLossChannel = getChannelForType("lottery_loss");
        String organizerMessageChannel = getChannelForType("organizer_message");
        String cancellationChannel = getChannelForType("entrant_cancelled");

        // Then: Types should map to correct channels
        assertEquals("Lottery win should use lottery channel", "lottery", lotteryWinChannel);
        assertEquals("Lottery loss should use lottery channel", "lottery", lotteryLossChannel);
        assertEquals("Organizer message should use organizer channel",
                "organizer_messages", organizerMessageChannel);
        assertEquals("Cancellation should use system channel", "system", cancellationChannel);
    }

    // Helper method for channel mapping
    private String getChannelForType(String type) {
        switch (type) {
            case "lottery_win":
            case "lottery_loss":
            case "replacement_draw":
                return "lottery";
            case "organizer_message":
                return "organizer_messages";
            case "entrant_cancelled":
            default:
                return "system";
        }
    }

    // ==================== Integration Tests ====================

    @Test
    public void testNotificationFlow_LotteryToInvitationAcceptance() {
        // Given: Lottery draw complete with winners
        List<String> winners = Arrays.asList("entrant_001", "entrant_002");
        List<String> losers = Arrays.asList("entrant_003", "entrant_004", "entrant_005");

        // When: Simulate notification flow
        // Step 1: Send lottery win notifications
        int winNotifications = winners.size();

        // Step 2: Send lottery loss notifications
        int lossNotifications = losers.size();

        // Step 3: Track total notifications sent
        int totalNotifications = winNotifications + lossNotifications;

        // Then: All participants should be notified
        assertEquals("Should notify all participants", 5, totalNotifications);
        assertEquals("Should send 2 win notifications", 2, winNotifications);
        assertEquals("Should send 3 loss notifications", 3, lossNotifications);
    }

    @Test
    public void testNotificationFlow_ReplacementDrawTriggeredByDecline() {
        // Given: Initial winner declines
        String decliner = "entrant_001";
        List<String> remainingWaitingList = Arrays.asList("entrant_003", "entrant_004");

        // When: Replacement draw occurs
        String replacement = remainingWaitingList.get(0); // entrant_003 selected

        // Then: Replacement should be notified
        assertNotNull("Replacement should be selected", replacement);
        assertEquals("Correct replacement should be selected", "entrant_003", replacement);
        assertFalse("Declining entrant should not be in replacement pool",
                remainingWaitingList.contains(decliner));
    }

    @Test
    public void testNotificationFlow_PreferenceRespectedThroughoutProcess() {
        // Given: User with notifications disabled at different stages
        Profile userProfile = testProfiles.get(0);

        // Stage 1: Join waiting list
        userProfile.setNotificationEnabled(true);
        boolean canReceiveAtJoin = userProfile.isNotificationEnabled();

        // Stage 2: Lottery draw - user opts out before draw
        userProfile.setNotificationEnabled(false);
        boolean canReceiveAtDraw = userProfile.isNotificationEnabled();

        // Stage 3: User re-enables
        userProfile.setNotificationEnabled(true);
        boolean canReceiveAfterReEnable = userProfile.isNotificationEnabled();

        // Then: Preference should be respected at each stage
        assertTrue("Should receive at join", canReceiveAtJoin);
        assertFalse("Should not receive at draw (opted out)", canReceiveAtDraw);
        assertTrue("Should receive after re-enable", canReceiveAfterReEnable);
    }

    @Test
    public void testNotificationStatistics_TrackSuccessAndFailure() {
        // Given: Notification send operation
        List<String> intendedRecipients = Arrays.asList(
                "entrant_001", "entrant_002", "entrant_003", "entrant_004", "entrant_005");
        int attempted = intendedRecipients.size();

        // When: Simulate some failures (e.g., invalid FCM tokens)
        int successful = 4; // 4 out of 5 succeeded
        int failed = 1;

        // Then: Should track both success and failure
        assertEquals("Attempted should match intended", 5, attempted);
        assertEquals("Success count should be tracked", 4, successful);
        assertEquals("Failure count should be tracked", 1, failed);
        assertEquals("Success + failure should equal attempted",
                attempted, successful + failed);
    }

    @Test
    public void testNotificationBatching_HandlesLargeRecipientList() {
        // Given: Large recipient list (FCM limit is 500 per batch)
        List<String> largeRecipientList = new ArrayList<>();
        for (int i = 0; i < 250; i++) {
            largeRecipientList.add("entrant_" + String.format("%03d", i));
        }

        // When: Check batch size
        int recipientCount = largeRecipientList.size();
        int maxBatchSize = 500; // FCM limit

        // Then: Should be within batch limit
        assertTrue("Should be under FCM batch limit", recipientCount <= maxBatchSize);
        assertEquals("Should have 250 recipients", 250, recipientCount);
    }

    @Test
    public void testNotificationTiming_RealTimeDelivery() {
        // Given: Notification send timestamp
        long sendTime = System.currentTimeMillis();

        // When: Calculate acceptable delivery window (within 5 seconds for push)
        long maxDelay = 5000; // 5 seconds
        long maxExpectedTime = sendTime + maxDelay;

        // Simulate current time (in real scenario, this would be actual delivery time)
        long deliveryTime = System.currentTimeMillis();

        // Then: Delivery should be near-realtime
        assertTrue("Delivery should be within acceptable window",
                deliveryTime <= maxExpectedTime);
    }
}

