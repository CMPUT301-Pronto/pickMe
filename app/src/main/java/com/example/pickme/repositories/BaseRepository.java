package com.example.pickme.repositories;

import android.util.Log;

import androidx.annotation.NonNull;

import com.example.pickme.services.FirebaseManager;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

/**
 * BaseRepository - Abstract base class for all Firebase repository classes
 *
 * This class provides common Firestore operations that all repositories can inherit.
 * Follows the Repository Pattern to separate data access logic from business logic.
 *
 * Benefits:
 * - Centralized Firebase error handling
 * - Consistent data access patterns across the app
 * - Easier testing (repositories can be mocked)
 * - Single source of truth for each data type
 *
 * Subclasses should:
 * - Define their collection name in constructor
 * - Implement specific query methods for their data type
 * - Convert Firestore documents to model objects
 *
 * Example usage:
 *   public class EventRepository extends BaseRepository {
 *       public EventRepository() {
 *           super("events");
 *       }
 *
 *       public void getEventById(String eventId, DataCallback<Event> callback) {
 *           getDocument(eventId, callback);
 *       }
 *   }
 */
public abstract class BaseRepository {

    private static final String TAG = "BaseRepository";

    protected FirebaseFirestore db;
    protected String collectionName;

    /**
     * Constructor - initializes Firestore and sets collection name
     *
     * @param collectionName Name of Firestore collection this repository manages
     */
    public BaseRepository(String collectionName) {
        this.db = FirebaseManager.getFirestore();
        this.collectionName = collectionName;
        Log.d(TAG, "Repository created for collection: " + collectionName);
    }

    /**
     * Get reference to this repository's collection
     *
     * @return CollectionReference for Firestore operations
     */
    protected CollectionReference getCollectionReference() {
        return db.collection(collectionName);
    }

    /**
     * Get reference to a specific document
     *
     * @param documentId ID of the document
     * @return DocumentReference for Firestore operations
     */
    protected DocumentReference getDocumentReference(String documentId) {
        return db.collection(collectionName).document(documentId);
    }

    /**
     * Add a new document to the collection with auto-generated ID
     *
     * @param data Map of field names to values
     * @param callback Callback for operation result
     */
    public void addDocument(Map<String, Object> data, OperationCallback callback) {
        getCollectionReference()
                .add(data)
                .addOnSuccessListener(documentReference -> {
                    String documentId = documentReference.getId();
                    Log.d(TAG, "Document added with ID: " + documentId);
                    callback.onSuccess(documentId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding document", e);
                    callback.onFailure(e);
                });
    }

    /**
     * Set/overwrite a document with a specific ID
     *
     * @param documentId ID for the document
     * @param data Map of field names to values
     * @param callback Callback for operation result
     */
    public void setDocument(String documentId, Map<String, Object> data, OperationCallback callback) {
        getDocumentReference(documentId)
                .set(data)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Document set successfully: " + documentId);
                    callback.onSuccess(documentId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error setting document: " + documentId, e);
                    callback.onFailure(e);
                });
    }

    /**
     * Update specific fields in a document
     *
     * @param documentId ID of the document to update
     * @param updates Map of field names to new values
     * @param callback Callback for operation result
     */
    public void updateDocument(String documentId, Map<String, Object> updates, OperationCallback callback) {
        getDocumentReference(documentId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Document updated successfully: " + documentId);
                    callback.onSuccess(documentId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating document: " + documentId, e);
                    callback.onFailure(e);
                });
    }

    /**
     * Delete a document
     *
     * @param documentId ID of the document to delete
     * @param callback Callback for operation result
     */
    public void deleteDocument(String documentId, OperationCallback callback) {
        getDocumentReference(documentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Document deleted successfully: " + documentId);
                    callback.onSuccess(documentId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting document: " + documentId, e);
                    callback.onFailure(e);
                });
    }

    /**
     * Check if a document exists
     *
     * @param documentId ID of the document to check
     * @param callback Callback with existence result
     */
    public void documentExists(String documentId, ExistsCallback callback) {
        getDocumentReference(documentId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    boolean exists = documentSnapshot.exists();
                    Log.d(TAG, "Document " + documentId + " exists: " + exists);
                    callback.onResult(exists);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error checking document existence: " + documentId, e);
                    callback.onResult(false);
                });
    }

    // Callback Interfaces

    /**
     * Callback for Firestore operations that return document IDs
     */
    public interface OperationCallback {
        void onSuccess(String documentId);
        void onFailure(Exception e);
    }

    /**
     * Callback for document existence checks
     */
    public interface ExistsCallback {
        void onResult(boolean exists);
    }

    /**
     * Generic callback for data retrieval operations
     *
     * @param <T> Type of data being retrieved
     */
    public interface DataCallback<T> {
        void onSuccess(T data);
        void onFailure(Exception e);
    }

    /**
     * Callback for operations that return lists of data
     *
     * @param <T> Type of data in the list
     */
    public interface ListCallback<T> {
        void onSuccess(java.util.List<T> dataList);
        void onFailure(Exception e);
    }
}

