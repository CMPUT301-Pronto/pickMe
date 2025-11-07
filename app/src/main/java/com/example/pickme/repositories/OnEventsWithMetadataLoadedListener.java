package com.example.pickme.repositories;

import com.example.pickme.models.Event;
import java.util.List;
import java.util.Map;

/**
 * Callback for repository methods returning events with optional metadata.
 *
 * Metadata map can contain additional per-event information like:
 * - "{eventId}_deadline" -> Long (timestamp for response deadline)
 * - "{eventId}_joinedAt" -> Long (timestamp when user joined waiting list)
 */
public interface OnEventsWithMetadataLoadedListener {
    /**
     * Called when events are successfully loaded.
     *
     * @param events list of Event objects
     * @param metadata map keyed by eventId + suffix for extra values (e.g. "eventId_deadline" -> Long)
     */
    void onEventsLoaded(List<Event> events, Map<String, Object> metadata);

    /**
     * Called on failure.
     *
     * @param e exception
     */
    void onError(Exception e);
}

