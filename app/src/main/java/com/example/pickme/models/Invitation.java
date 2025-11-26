package com.example.pickme.models;

/**
 * JAVADOCS LLM GENERATED
 *
 * Represents an event invitation sent to an entrant after a lottery draw.
 *
 * <p><b>Role / Pattern:</b> Plain Old Java Object (POJO) model used for serialization
 * and storage in Firestore under an event’s invitations subcollection.</p>
 *
 * <p><b>Responsibilities:</b>
 * <ul>
 *   <li>Encapsulate metadata about an event invitation (event info, organizer, status).</li>
 *   <li>Track invitation lifecycle states (PENDING, ACCEPTED, DECLINED, EXPIRED).</li>
 *   <li>Support easy serialization/deserialization by Firebase.</li>
 * </ul>
 * </p>
 *
 * <p><b>Firestore Structure Example:</b></p>
 * <pre>
 * events/{eventId}/invitations/{invitationId}
 *   ├─ eventId: "event123"
 *   ├─ eventName: "Campus Concert"
 *   ├─ organizerId: "org456"
 *   ├─ deadline: 1730841600000
 *   ├─ status: "PENDING"
 *   └─ createdAt: 1730838000000
 * </pre>
 *
 * <p><b>Used By:</b>
 * <ul>
 *   <li>{@link com.example.pickme.services.NotificationService} — when sending invitation notifications.</li>
 *   <li>{@link com.example.pickme.ui.invitations.InvitationDialogFragment} — for displaying invitation prompts.</li>
 *   <li>{@link com.example.pickme.services.LotteryService} — for updating invitation responses.</li>
 * </ul>
 * </p>
 */
public class Invitation {

    /** Event ID this invitation belongs to. */
    private String eventId;

    /** Event display name for UI reference. */
    private String eventName;

    /** Organizer’s user ID who created the event. */
    private String organizerId;

    /** Deadline timestamp (in milliseconds since epoch) for responding to the invitation. */
    private long deadline;

    /** Invitation status: PENDING, ACCEPTED, DECLINED, or EXPIRED. */
    private String status;

    /** Timestamp when the invitation was created (in milliseconds since epoch). */
    private long createdAt;

    /** Default no-arg constructor required for Firestore deserialization. */
    public Invitation() {}

    /** Returns the event ID associated with this invitation. */
    public String getEventId() { return eventId; }

    /** Sets the event ID. */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /** Returns the event name. */
    public String getEventName() { return eventName; }

    /** Sets the event name. */
    public void setEventName(String eventName) { this.eventName = eventName; }

    /** Returns the organizer ID. */
    public String getOrganizerId() { return organizerId; }

    /** Sets the organizer ID. */
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    /** Returns the response deadline in milliseconds. */
    public long getDeadline() { return deadline; }

    /** Sets the response deadline in milliseconds. */
    public void setDeadline(long deadline) { this.deadline = deadline; }

    /** Returns the current invitation status. */
    public String getStatus() { return status; }

    /** Sets the invitation status (PENDING, ACCEPTED, DECLINED, EXPIRED). */
    public void setStatus(String status) { this.status = status; }

    /** Returns the creation timestamp. */
    public long getCreatedAt() { return createdAt; }

    /** Sets the creation timestamp. */
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}