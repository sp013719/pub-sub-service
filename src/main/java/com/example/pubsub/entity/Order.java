package com.example.pubsub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * JPA entity representing an order persisted in the "orders" table.
 *
 * Lifecycle:
 *   SUBMITTED  – saved by OrderController before publishing to Kafka
 *   COMPLETED  – updated by OrderConsumerGroupA (fulfillment) on success
 *
 * Orders with status "FAIL" remain SUBMITTED permanently in this demo
 * (they exhaust retries and land on the DLT — educational intent: show
 * that Kafka retry/DLT and DB persistence are independent concerns).
 */
@Entity
@Table(name = "orders")
@Getter
@Setter
@NoArgsConstructor
public class Order {

    /**
     * orderId from the incoming OrderEvent — used as the primary key.
     *
     * Using the business key as the PK keeps the schema simple and makes
     * consumer lookups straightforward: findById(event.orderId()).
     */
    @Id
    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * Persistence lifecycle status. Values: "SUBMITTED", "COMPLETED".
     * Note: OrderEvent.status ("NEW", "FAIL", etc.) is the event/intent status —
     * intentionally separate from this DB status field.
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /**
     * Set at insert time. Marked updatable=false so Hibernate never includes
     * it in UPDATE statements.
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Null until the order has been processed — a null value visually shows
     * in-flight orders when querying the DB directly.
     */
    @Column(name = "updated_at")
    private Instant updatedAt;

    /**
     * Factory method used by OrderController to create the initial record.
     */
    public static Order createSubmitted(String orderId, String customerId, BigDecimal amount) {
        Order order = new Order();
        order.orderId    = orderId;
        order.customerId = customerId;
        order.amount     = amount;
        order.status     = "SUBMITTED";
        order.createdAt  = Instant.now();
        return order;
    }
}
