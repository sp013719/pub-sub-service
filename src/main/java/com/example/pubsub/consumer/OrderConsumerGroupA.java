package com.example.pubsub.consumer;

import com.example.pubsub.entity.Order;
import com.example.pubsub.model.OrderEvent;
import com.example.pubsub.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Order consumer simulating the FULFILLMENT service.
 *
 * KEY CONCEPT — Retry + Dead-Letter Topic pipeline:
 *
 *   @RetryableTopic creates a retry chain automatically:
 *     order-events → order-events-retry-0 (1s) → order-events-retry-1 (2s)
 *                  → order-events-retry-2 (4s) → order-events-dlt
 *
 *   When processing throws an exception (triggered here by status="FAIL"):
 *     1. Spring re-publishes the message to the next retry topic
 *     2. After exhausting all attempts, the message lands on the DLT
 *     3. @DltHandler receives it for alerting / manual reprocessing
 *
 *   The original topic (order-events) is NOT blocked during retries — other
 *   messages continue flowing. Retries happen on dedicated retry topics.
 *
 * KEY CONCEPT — Consumer Groups:
 *   groupId="order-group-a" gets its own independent offset pointer.
 *   group-a and group-b both receive EVERY message — they don't share work,
 *   they each do their own work on the same stream.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderConsumerGroupA {

    private final OrderRepository orderRepository;

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            // Use the explicit DLT bean we declared in KafkaTopicConfig
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "false"   // topics already declared as beans
    )
    @KafkaListener(topics = "order-events", groupId = "order-group-a")
    public void consume(
            OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[GROUP-A / FULFILLMENT] Received order {} | status={} | partition={} offset={}",
                event.orderId(), event.status(), partition, offset);

        if ("FAIL".equals(event.status())) {
            // Simulate a processing failure — triggers the retry pipeline
            log.warn("[GROUP-A / FULFILLMENT] Order {} has FAIL status — throwing to trigger retry",
                    event.orderId());
            throw new RuntimeException("Order processing failed for orderId=" + event.orderId());
        }

        // Happy path — simulate fulfillment work
        log.info("[GROUP-A / FULFILLMENT] Successfully fulfilled order {} for customer {} (amount={})",
                event.orderId(), event.customerId(), event.amount());

        // Update order status in DB to COMPLETED.
        // The order should always exist (saved by the controller before publishing),
        // but we guard defensively so a missed DB write doesn't crash the consumer.
        orderRepository.findById(event.orderId()).ifPresentOrElse(
                order -> {
                    order.setStatus("COMPLETED");
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);
                    log.info("[GROUP-A / FULFILLMENT] Order {} status updated to COMPLETED in DB", event.orderId());
                },
                () -> log.warn("[GROUP-A / FULFILLMENT] Order {} not found in DB — skipping status update", event.orderId())
        );
    }

    /**
     * Called after all retry attempts are exhausted.
     * In production: send an alert, write to a DB, open a support ticket, etc.
     */
    @DltHandler
    public void handleDlt(
            OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        log.error("[DLT] Order {} exhausted all retries → arrived at dead-letter topic: {}",
                event.orderId(), topic);
        log.error("[DLT] Manual intervention required for orderId={} customerId={} amount={}",
                event.orderId(), event.customerId(), event.amount());
    }
}
