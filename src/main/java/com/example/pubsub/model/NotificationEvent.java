package com.example.pubsub.model;

/**
 * Immutable event published when a notification should be sent.
 *
 * type values: "EMAIL", "SMS", "PUSH"
 */
public record NotificationEvent(
        String notificationId,
        String recipientEmail,
        String message,
        String type
) {}
