package com.example.pubsub.model;

import java.math.BigDecimal;

/**
 * Immutable event published when an order is created or updated.
 *
 * status values:
 *   NEW        – normal happy-path order
 *   PROCESSING – order being fulfilled
 *   FAIL       – triggers retry + DLT pipeline in OrderConsumerGroupA
 */
public record OrderEvent(
        String orderId,
        String customerId,
        BigDecimal amount,
        OrderStatus status
) {}
