package com.example.pickme.repositories;

import android.util.Log;

import com.example.pickme.models.User;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * UserRepository - Repository for User data operations
 *
 * Handles all Firestore operations related to users:
 * - Creating new user profiles
 * - Retrieving user data
 * - Updating user information
 * - Querying users by various criteria
 *
 * Extends BaseRepository to inherit common CRUD operations.
 *
 * Firestore Collection: "users"
 * Document ID: Firebase Auth UID (userId)
 *
 * Usage example:
 *   UserRepository userRepo = new UserRepository();
 *   userRepo.createUser(user, callback);
 *   userRepo.getUserById(userId, callback);
 */
public class UserRepository extends BaseRepository {

    private static final String TAG = "UserRepository";
    private static final String COLLECTION_USERS = "users";

    /**
     * Constructor - initializes repository for "users" collection
     */
    public UserRepository() {
        super(COLLECTION_USERS);
    }

    /**
     * Create a new user in Firestore
     * Uses userId as document ID for easy retrieval
     *
     * @param user User object to create
     * @param callback Callback for operation result
     */
    public void createUser(User user, OperationCallback callback) {
        if (user.getUserId() == null || user.getUserId().isEmpty()) {
            callback.onFailure(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        // Use userId as document ID
        setDocument(user.getUserId(), user.toMap(), new OperationCallback() {
            @Override
            public void onSuccess(String documentId) {
                Log.d(TAG, "User created successfully: " + documentId);
                callback.onSuccess(documentId);
            }

            @Override
            public void onFailure(Exception e) {
                Log.e(TAG, "Failed to create user", e);
                callback.onFailure(e);
            }
        });
    }

    /**
     * Get user by ID
     *
     * @param userId User's unique identifier
     * @param callback Callback with User object or error
     */
    public void getUserById(String userId, DataCallback<User> callback) {
        getDocumentReference(userId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        User user = documentSnapshot.toObject(User.class);
                        Log.d(TAG, "User retrieved: " + userId);
                        callback.onSuccess(user);
                    } else {
                        Log.w(TAG, "User not found: " + userId);
                        callback.onFailure(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user: " + userId, e);
                    callback.onFailure(e);
                });
    }

    /**
     * Update user profile
     *
     * @param user User object with updated data
     * @param callback Callback for operation result
     */
    public void updateUser(User user, OperationCallback callback) {
        if (user.getUserId() == null || user.getUserId().isEmpty()) {
            callback.onFailure(new IllegalArgumentException("User ID cannot be null or empty"));
            return;
        }

        updateDocument(user.getUserId(), user.toMap(), callback);
    }

    /**
     * Delete user
     *
     * @param userId User ID to delete
     * @param callback Callback for operation result
     */
    public void deleteUser(String userId, OperationCallback callback) {
        deleteDocument(userId, callback);
    }

    /**
     * Check if user exists in Firestore
     *
     * @param userId User ID to check
     * @param callback Callback with result
     */
    public void userExists(String userId, ExistsCallback callback) {
        documentExists(userId, callback);
    }

    /**
     * Get all users with a specific role
     *
     * @param role User role (ROLE_ENTRANT, ROLE_ORGANIZER, ROLE_ADMIN)
     * @param callback Callback with list of users
     */
    public void getUsersByRole(String role, ListCallback<User> callback) {
        getCollectionReference()
                .whereEqualTo("role", role)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<User> users = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            users.add(user);
                        }
                    }
                    Log.d(TAG, "Retrieved " + users.size() + " users with role: " + role);
                    callback.onSuccess(users);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting users by role: " + role, e);
                    callback.onFailure(e);
                });
    }

    /**
     * Get user by device ID
     * Useful for device-based authentication
     *
     * @param deviceId Device identifier
     * @param callback Callback with User object or error
     */
    public void getUserByDeviceId(String deviceId, DataCallback<User> callback) {
        getCollectionReference()
                .whereEqualTo("deviceId", deviceId)
                .limit(1)  // Should only be one user per device
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        User user = querySnapshot.getDocuments().get(0).toObject(User.class);
                        Log.d(TAG, "User found by device ID: " + deviceId);
                        callback.onSuccess(user);
                    } else {
                        Log.w(TAG, "No user found for device ID: " + deviceId);
                        callback.onFailure(new Exception("User not found"));
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting user by device ID: " + deviceId, e);
                    callback.onFailure(e);
                });
    }

    /**
     * Search users by name (case-insensitive prefix match)
     * Note: Firestore doesn't support full-text search natively
     * For production, consider using Algolia or Elasticsearch
     *
     * @param namePrefix Name prefix to search for
     * @param callback Callback with list of matching users
     */
    public void searchUsersByName(String namePrefix, ListCallback<User> callback) {
        // Convert to lowercase for case-insensitive search
        String lowerPrefix = namePrefix.toLowerCase();

        getCollectionReference()
                .orderBy("name")
                .startAt(lowerPrefix)
                .endAt(lowerPrefix + "\uf8ff")  // Unicode character that sorts after all others
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<User> users = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            users.add(user);
                        }
                    }
                    Log.d(TAG, "Found " + users.size() + " users matching: " + namePrefix);
                    callback.onSuccess(users);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error searching users by name: " + namePrefix, e);
                    callback.onFailure(e);
                });
    }

    /**
     * Get all users (use with caution - can be expensive for large datasets)
     * Consider implementing pagination for production
     *
     * @param callback Callback with list of all users
     */
    public void getAllUsers(ListCallback<User> callback) {
        getCollectionReference()
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<User> users = new ArrayList<>();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        User user = doc.toObject(User.class);
                        if (user != null) {
                            users.add(user);
                        }
                    }
                    Log.d(TAG, "Retrieved all users: " + users.size());
                    callback.onSuccess(users);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error getting all users", e);
                    callback.onFailure(e);
                });
    }
}
