/**
 * Import function triggers from their respective submodules:
 *
 * const {onCall} = require("firebase-functions/v2/https");
 * const {onDocumentWritten} = require("firebase-functions/v2/firestore");
 *
 * See a full list of supported triggers at https://firebase.google.com/docs/functions
 */

const {setGlobalOptions} = require("firebase-functions");
const {onRequest} = require("firebase-functions/https");
const logger = require("firebase-functions/logger");

// For cost control, you can set the maximum number of containers that can be
// running at the same time. This helps mitigate the impact of unexpected
// traffic spikes by instead downgrading performance. This limit is a
// per-function limit. You can override the limit for each function using the
// `maxInstances` option in the function's options, e.g.
// `onRequest({ maxInstances: 5 }, (req, res) => { ... })`.
// NOTE: setGlobalOptions does not apply to functions using the v1 API. V1
// functions should each use functions.runWith({ maxInstances: 10 }) instead.
// In the v1 API, each function can only serve one request per container, so
// this will be the maximum concurrent request count.
setGlobalOptions({ maxInstances: 10 });

// Create and deploy your first functions
// https://firebase.google.com/docs/functions/get-started

const functions = require('firebase-functions');
const admin = require('firebase-admin');

admin.initializeApp();

const FUNCTION_VERSION = '2.0.0-debug-' + Date.now();

/**
 * sendBatchNotifications - Cloud Function to send FCM notifications to multiple users
 *
 * Called by Android app's NotificationService
 *
 * Input payload:
 * {
 *   userIds: string[],     // Array of user IDs to send notifications to
 *   data: {                // Data payload for FCM message
 *     type: string,        // lottery_win, lottery_loss, organizer_message, etc.
 *     eventId: string,
 *     eventName: string,
 *     message: string,
 *     invitationDeadline?: string  // Optional, for lottery wins
 *   }
 * }
 *
 * Returns:
 * {
 *   success: boolean,
 *   sent: number,          // Number of successfully sent messages
 *   failed: number,        // Number of failed sends
 *   errors: string[]       // Array of error messages (if any)
 * }
 */
// Updated: Enhanced debugging for userIds validation
exports.sendBatchNotifications = functions.https.onCall(async (data, context) => {
  try {
    console.log('=== sendBatchNotifications Function Version:', FUNCTION_VERSION, '===');

    // Firebase Callable Functions wrap data in a 'data' field
    // The actual payload is at data.data
    const payload = data?.data || data || {};

    // Log for debugging
    console.log('Incoming data keys:', Object.keys(data || {}));
    console.log('Payload keys:', Object.keys(payload));
    console.log('userIds from payload:', payload.userIds);
    console.log('data field from payload:', payload.data);

    // Extract userIds - handle both direct array and nested structures
    let userIds = payload.userIds;

    // Debug log
    console.log('userIds type:', typeof userIds);
    console.log('userIds value:', userIds);
    console.log('Is array?', Array.isArray(userIds));

    // Validate userIds
    if (!userIds) {
      console.error('userIds is null or undefined');
      throw new functions.https.HttpsError(
        'invalid-argument',
        'userIds must be a non-empty array (received null/undefined)'
      );
    }

    if (!Array.isArray(userIds)) {
      console.error('userIds is not an array:', typeof userIds, userIds);
      throw new functions.https.HttpsError(
        'invalid-argument',
        'userIds must be a non-empty array (received non-array type: ' + typeof userIds + ')'
      );
    }

    if (userIds.length === 0) {
      console.error('userIds is an empty array');
      throw new functions.https.HttpsError(
        'invalid-argument',
        'userIds must be a non-empty array (received empty array)'
      );
    }

    const messageData = payload.data || payload.messageData || payload.payload || {};

    if (!messageData || typeof messageData !== 'object') {
      throw new functions.https.HttpsError(
        'invalid-argument',
        'data must be an object'
      );
    }

    console.log(`Sending notifications to ${userIds.length} users`);

    // Get FCM tokens for all user IDs
    const db = admin.firestore();
    const tokensMap = {};
    const errors = [];

    // Fetch tokens in parallel
    const tokenPromises = userIds.map(async (userId) => {
      try {
        console.log(`Fetching profile for user: ${userId}`);
        const profileDoc = await db.collection('profiles').doc(userId).get();
        if (!profileDoc.exists) {
          console.warn(`Profile not found for user: ${userId}`);
          return null;
        }

        const profile = profileDoc.data();
        console.log(`Profile data for ${userId}:`, {
          hasNotificationEnabled: profile.notificationEnabled,
          hasFcmToken: !!profile.fcmToken,
          fcmTokenLength: profile.fcmToken ? profile.fcmToken.length : 0
        });

        const fcmToken = profile.fcmToken;

        if (!fcmToken) {
          console.warn(`No FCM token for user: ${userId}`);
          errors.push(`No FCM token for user ${userId}`);
          return null;
        }

        tokensMap[userId] = fcmToken;
        console.log(`Successfully retrieved FCM token for user: ${userId}`);
        return fcmToken;
      } catch (error) {
        console.error(`Error fetching token for ${userId}:`);
        console.error('Error message:', error.message);
        console.error('Error code:', error.code);
        errors.push(`Failed to get token for ${userId}: ${error.message}`);
        return null;
      }
    });

    await Promise.all(tokenPromises);

    const tokens = Object.values(tokensMap).filter(token => token != null);
    console.log(`Retrieved ${tokens.length} valid FCM tokens out of ${userIds.length} users`);

    if (tokens.length === 0) {
      return {
        success: true,
        sent: 0,
        failed: userIds.length,
        errors: ['No valid FCM tokens found for any recipients']
      };
    }

    // Prepare FCM messages - create individual message for each token
    const messages = tokens.map(token => ({
      data: {
        // Convert all data fields to strings (FCM requirement)
        type: String(messageData.type || 'notification'),
        eventId: String(messageData.eventId || ''),
        eventName: String(messageData.eventName || ''),
        message: String(messageData.message || ''),
        ...(messageData.invitationDeadline && {
          invitationDeadline: String(messageData.invitationDeadline)
        })
      },
      token: token
    }));

    // Send messages using sendEach (correct Firebase Admin SDK method)
    console.log(`Sending ${messages.length} messages to ${tokens.length} tokens`);
    const response = await admin.messaging().sendEach(messages);

    console.log(`Successfully sent ${response.successCount} messages`);
    if (response.failureCount > 0) {
      console.log(`Failed to send ${response.failureCount} messages`);

      // Log specific failures (avoid circular references)
      response.responses.forEach((resp, idx) => {
        if (!resp.success) {
          const errorMsg = resp.error?.message || 'Unknown error';
          const errorCode = resp.error?.code || 'unknown';
          console.error(`Error for token ${idx}: ${errorCode} - ${errorMsg}`);
          errors.push(`Token ${idx}: ${errorMsg}`);
        }
      });
    }

    return {
      success: true,
      sent: response.successCount,
      failed: response.failureCount,
      errors: errors
    };

  } catch (error) {
    // Safely log error without circular references
    console.error('Error sending batch notifications:');
    console.error('Error message:', error.message);
    console.error('Error code:', error.code);
    console.error('Error stack:', error.stack);

    throw new functions.https.HttpsError(
      'internal',
      error.message || 'Failed to send notifications'
    );
  }
});

/**
 * Optional: Clean up invalid FCM tokens
 *
 * NOTE: Scheduled functions require additional setup and Cloud Scheduler API
 * Commented out for now - can be enabled later if needed
 *
 * To enable:
 * 1. Enable Cloud Scheduler API in Google Cloud Console
 * 2. Uncomment the code below
 * 3. Redeploy functions
 */

// exports.cleanupInvalidTokens = functions.pubsub
//   .schedule('every 24 hours')
//   .onRun(async (context) => {
//     // Implementation to remove invalid tokens from Firestore
//     console.log('Token cleanup job started');
//     // Add your cleanup logic here
//     return null;
//   });
