package com.example.pubsub.model;

/**
 * Response body for POST /api/orders when an order event is accepted for publishing.
 */
public record OrderPublishResponse(
        String status,
        String orderId,
        String message
) {}
