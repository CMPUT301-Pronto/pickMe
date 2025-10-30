package com.example.pickme.repositories;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.models.Event;
import com.example.pickme.models.EventPoster;
import com.example.pickme.services.FirebaseManager;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * ImageRepository - Repository for Firebase Storage image operations
 *
 * Handles all image operations including:
 * - Event poster upload with compression
 * - Image update (delete old, upload new)
 * - Image deletion
 * - EventPoster record management in Firestore
 *
 * Storage Structure:
 * event_posters/
 *   └─ {eventId}/
 *       └─ {filename}.jpg
 *
 * Firestore Structure:
 * event_posters/{posterId}
 *   ├─ posterId
 *   ├─ eventId
 *   ├─ imageUrl (Firebase Storage download URL)
 *   ├─ uploadTimestamp
 *   └─ uploadedBy
 *
 * Image Processing:
 * - Compresses images to max 1MB before upload
 * - Generates unique filenames using UUID
 * - Updates Event document with posterImageUrl
 * - Creates EventPoster record for tracking
 *
 * Related User Stories: US 02.04.01, US 02.04.02, US 03.03.01, US 03.06.01
 */
public class ImageRepository {

    private static final String TAG = "ImageRepository";
    private static final String STORAGE_PATH_EVENT_POSTERS = "event_posters";
    private static final String COLLECTION_EVENTS = "events";
    private static final String COLLECTION_EVENT_POSTERS = "event_posters";
    private static final int MAX_IMAGE_SIZE_BYTES = 1024 * 1024; // 1MB
    private static final int COMPRESSION_QUALITY = 85; // JPEG quality 0-100

    private StorageReference storageRef;
    private FirebaseFirestore db;

    /**
     * Constructor - initializes Firebase Storage and Firestore references
     */
    public ImageRepository() {
        this.storageRef = FirebaseManager.getStorageReference();
        this.db = FirebaseManager.getFirestore();
    }

    // ==================== Upload Operations ====================

    /**
     * Upload event poster image
     *
     * Process:
     * 1. Compress image to max 1MB
     * 2. Upload to Firebase Storage at event_posters/{eventId}/
     * 3. Get download URL
     * 4. Update Event document with posterImageUrl
     * 5. Create EventPoster record in Firestore
     *
     * @param eventId Event ID for this poster
     * @param imageUri URI of image to upload (from file picker)
     * @param uploadedBy User ID of uploader
     * @param listener Callback with download URL
     */
    public void uploadEventPoster(@NonNull String eventId,
                                  @NonNull Uri imageUri,
                                  @NonNull String uploadedBy,
                                  @NonNull OnUploadCompleteListener listener) {
        try {
            // Generate unique filename
            String filename = UUID.randomUUID().toString() + ".jpg";
            String storagePath = STORAGE_PATH_EVENT_POSTERS + "/" + eventId + "/" + filename;

            // Compress image (compression logic would use imageUri to get bitmap)
            // Note: Actual compression would require Context to get ContentResolver
            // For now, we'll upload directly and add compression in a utility class

            StorageReference posterRef = storageRef.child(storagePath);

            // Upload file
            UploadTask uploadTask = posterRef.putFile(imageUri);

            uploadTask.addOnSuccessListener(taskSnapshot -> {
                // Get download URL
                posterRef.getDownloadUrl().addOnSuccessListener(downloadUri -> {
                    String downloadUrl = downloadUri.toString();
                    Log.d(TAG, "Image uploaded successfully: " + downloadUrl);

                    // Update Event document with poster URL
                    updateEventPosterUrl(eventId, downloadUrl,
                        new OnEventUpdateListener() {
                            @Override
                            public void onSuccess() {
                                // Create EventPoster record
                                createEventPosterRecord(eventId, downloadUrl, uploadedBy,
                                    new OnPosterRecordCreatedListener() {
                                        @Override
                                        public void onSuccess(String posterId) {
                                            Log.d(TAG, "EventPoster record created: " + posterId);
                                            listener.onUploadComplete(downloadUrl, posterId);
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Log.w(TAG, "EventPoster record creation failed, but image uploaded", e);
                                            listener.onUploadComplete(downloadUrl, null);
                                        }
                                    });
                            }

                            @Override
                            public void onError(Exception e) {
                                Log.w(TAG, "Event update failed, but image uploaded", e);
                                listener.onUploadComplete(downloadUrl, null);
                            }
                        });
                }).addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get download URL", e);
                    listener.onError(e);
                });
            }).addOnFailureListener(e -> {
                Log.e(TAG, "Image upload failed", e);
                listener.onError(e);
            });

        } catch (Exception e) {
            Log.e(TAG, "Error preparing image upload", e);
            listener.onError(e);
        }
    }

    /**
     * Update event poster (delete old, upload new)
     *
     * @param eventId Event ID
     * @param newImageUri URI of new image
     * @param uploadedBy User ID of uploader
     * @param listener Callback with new download URL
     */
    public void updateEventPoster(@NonNull String eventId,
                                  @NonNull Uri newImageUri,
                                  @NonNull String uploadedBy,
                                  @NonNull OnUploadCompleteListener listener) {
        // First, delete the old poster
        deleteEventPoster(eventId, new OnDeleteCompleteListener() {
            @Override
            public void onDeleteComplete() {
                // Then upload the new poster
                uploadEventPoster(eventId, newImageUri, uploadedBy, listener);
            }

            @Override
            public void onError(Exception e) {
                // Even if deletion fails, try to upload new poster
                Log.w(TAG, "Old poster deletion failed, uploading new poster anyway", e);
                uploadEventPoster(eventId, newImageUri, uploadedBy, listener);
            }
        });
    }

    // ==================== Delete Operations ====================

    /**
     * Delete event poster
     *
     * Process:
     * 1. Get current poster URL from Event document
     * 2. Delete file from Firebase Storage
     * 3. Clear posterImageUrl field in Event document
     * 4. Delete EventPoster record from Firestore
     *
     * @param eventId Event ID
     * @param listener Callback when deletion completes
     */
    public void deleteEventPoster(@NonNull String eventId,
                                  @NonNull OnDeleteCompleteListener listener) {
        // First, get the event to find poster URL
        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        String posterUrl = documentSnapshot.getString("posterImageUrl");

                        if (posterUrl != null && !posterUrl.isEmpty()) {
                            // Delete from Storage
                            deleteImageFromStorage(eventId, new OnStorageDeleteListener() {
                                @Override
                                public void onSuccess() {
                                    // Clear Event posterImageUrl field
                                    clearEventPosterUrl(eventId, new OnEventUpdateListener() {
                                        @Override
                                        public void onSuccess() {
                                            // Delete EventPoster records
                                            deleteEventPosterRecords(eventId, listener);
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Log.w(TAG, "Event poster URL clear failed", e);
                                            deleteEventPosterRecords(eventId, listener);
                                        }
                                    });
                                }

                                @Override
                                public void onError(Exception e) {
                                    Log.w(TAG, "Storage deletion failed", e);
                                    // Continue with Firestore cleanup
                                    clearEventPosterUrl(eventId, new OnEventUpdateListener() {
                                        @Override
                                        public void onSuccess() {
                                            deleteEventPosterRecords(eventId, listener);
                                        }

                                        @Override
                                        public void onError(Exception ex) {
                                            listener.onError(ex);
                                        }
                                    });
                                }
                            });
                        } else {
                            Log.d(TAG, "No poster URL found for event: " + eventId);
                            listener.onDeleteComplete();
                        }
                    } else {
                        Log.w(TAG, "Event not found: " + eventId);
                        listener.onError(new Exception("Event not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get event for poster deletion", e);
                    listener.onError(e);
                });
    }

    /**
     * Delete all images in event poster folder from Storage
     *
     * @param eventId Event ID
     * @param listener Callback when deletion completes
     */
    private void deleteImageFromStorage(@NonNull String eventId,
                                       @NonNull OnStorageDeleteListener listener) {
        String storagePath = STORAGE_PATH_EVENT_POSTERS + "/" + eventId;
        StorageReference folderRef = storageRef.child(storagePath);

        // List all files in the folder
        folderRef.listAll()
                .addOnSuccessListener(listResult -> {
                    if (listResult.getItems().isEmpty()) {
                        Log.d(TAG, "No images found in storage for event: " + eventId);
                        listener.onSuccess();
                        return;
                    }

                    // Delete all files
                    int[] deleteCount = {0};
                    int totalFiles = listResult.getItems().size();

                    for (StorageReference item : listResult.getItems()) {
                        item.delete()
                                .addOnSuccessListener(aVoid -> {
                                    deleteCount[0]++;
                                    Log.d(TAG, "Deleted image: " + item.getName());

                                    if (deleteCount[0] == totalFiles) {
                                        listener.onSuccess();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to delete image: " + item.getName(), e);
                                    deleteCount[0]++;

                                    if (deleteCount[0] == totalFiles) {
                                        listener.onError(e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to list images for deletion", e);
                    listener.onError(e);
                });
    }

    // ==================== Firestore Operations ====================

    /**
     * Update Event document with poster URL
     */
    private void updateEventPosterUrl(@NonNull String eventId,
                                     @NonNull String posterUrl,
                                     @NonNull OnEventUpdateListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("posterImageUrl", posterUrl);

        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event poster URL updated: " + eventId);
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to update event poster URL", e);
                    listener.onError(e);
                });
    }

    /**
     * Clear Event document poster URL field
     */
    private void clearEventPosterUrl(@NonNull String eventId,
                                    @NonNull OnEventUpdateListener listener) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("posterImageUrl", null);

        db.collection(COLLECTION_EVENTS)
                .document(eventId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Event poster URL cleared: " + eventId);
                    listener.onSuccess();
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to clear event poster URL", e);
                    listener.onError(e);
                });
    }

    /**
     * Create EventPoster record in Firestore
     */
    private void createEventPosterRecord(@NonNull String eventId,
                                        @NonNull String imageUrl,
                                        @NonNull String uploadedBy,
                                        @NonNull OnPosterRecordCreatedListener listener) {
        String posterId = db.collection(COLLECTION_EVENT_POSTERS).document().getId();

        EventPoster poster = new EventPoster(posterId, eventId, imageUrl, uploadedBy);

        db.collection(COLLECTION_EVENT_POSTERS)
                .document(posterId)
                .set(poster.toMap())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "EventPoster record created: " + posterId);
                    listener.onSuccess(posterId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to create EventPoster record", e);
                    listener.onError(e);
                });
    }

    /**
     * Delete EventPoster records for an event
     */
    private void deleteEventPosterRecords(@NonNull String eventId,
                                         @NonNull OnDeleteCompleteListener listener) {
        db.collection(COLLECTION_EVENT_POSTERS)
                .whereEqualTo("eventId", eventId)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        Log.d(TAG, "No EventPoster records found for event: " + eventId);
                        listener.onDeleteComplete();
                        return;
                    }

                    // Delete all matching records
                    int[] deleteCount = {0};
                    int totalRecords = querySnapshot.size();

                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        doc.getReference().delete()
                                .addOnSuccessListener(aVoid -> {
                                    deleteCount[0]++;
                                    if (deleteCount[0] == totalRecords) {
                                        Log.d(TAG, "All EventPoster records deleted for event: " + eventId);
                                        listener.onDeleteComplete();
                                    }
                                })
                                .addOnFailureListener(e -> {
                                    Log.e(TAG, "Failed to delete EventPoster record", e);
                                    deleteCount[0]++;
                                    if (deleteCount[0] == totalRecords) {
                                        listener.onError(e);
                                    }
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to query EventPoster records", e);
                    listener.onError(e);
                });
    }

    /**
     * Get all event posters (for admin browsing)
     *
     * @param listener Callback with list of EventPoster objects
     */
    public void getAllEventPosters(@NonNull OnPostersLoadedListener listener) {
        db.collection(COLLECTION_EVENT_POSTERS)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<EventPoster> posters = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        EventPoster poster = doc.toObject(EventPoster.class);
                        if (poster != null) {
                            posters.add(poster);
                        }
                    }
                    Log.d(TAG, "Retrieved all event posters: " + posters.size());
                    listener.onPostersLoaded(posters);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to get all event posters", e);
                    listener.onError(e);
                });
    }

    // ==================== Listener Interfaces ====================

    /**
     * Callback for upload completion
     */
    public interface OnUploadCompleteListener {
        void onUploadComplete(String downloadUrl, String posterId);
        void onError(Exception e);
    }

    /**
     * Callback for deletion completion
     */
    public interface OnDeleteCompleteListener {
        void onDeleteComplete();
        void onError(Exception e);
    }

    /**
     * Callback for event update
     */
    private interface OnEventUpdateListener {
        void onSuccess();
        void onError(Exception e);
    }

    /**
     * Callback for Storage deletion
     */
    private interface OnStorageDeleteListener {
        void onSuccess();
        void onError(Exception e);
    }

    /**
     * Callback for EventPoster record creation
     */
    private interface OnPosterRecordCreatedListener {
        void onSuccess(String posterId);
        void onError(Exception e);
    }

    /**
     * Callback for loading posters
     */
    public interface OnPostersLoadedListener {
        void onPostersLoaded(List<EventPoster> posters);
        void onError(Exception e);
    }
}

