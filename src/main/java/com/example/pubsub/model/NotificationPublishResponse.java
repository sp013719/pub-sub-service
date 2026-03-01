package com.example.pubsub.model;

/**
 * Response body for POST /api/notifications when a notification event is accepted for publishing.
 */
public record NotificationPublishResponse(
        String status,
        String notificationId,
        String message
) {}
