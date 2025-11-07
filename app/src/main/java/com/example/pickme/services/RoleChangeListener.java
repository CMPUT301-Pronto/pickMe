package com.example.pickme.services;

/**
 * Listener interface for role change notifications.
 * Implemented by components that need to respond to user role changes.
 */
public interface RoleChangeListener {
    /**
     * Called when the user's role changes.
     *
     * @param newRole The new role (Profile.ROLE_ENTRANT, ROLE_ORGANIZER, or ROLE_ADMIN)
     */
    void onRoleChanged(String newRole);
}

