package com.example.pickme.models;

/**
 * EventStatus - Enum representing the lifecycle status of an event
 *
 * Status flow:
 * DRAFT → OPEN → CLOSED → COMPLETED
 *         ↓
 *      CANCELLED
 *
 * - DRAFT: Event created but not published yet
 * - OPEN: Event published and accepting registrations
 * - CLOSED: Registration closed, lottery/selection in progress
 * - COMPLETED: Event finished
 * - CANCELLED: Event cancelled by organizer
 */
public enum EventStatus {
    DRAFT,
    OPEN,
    CLOSED,
    COMPLETED,
    CANCELLED
}

