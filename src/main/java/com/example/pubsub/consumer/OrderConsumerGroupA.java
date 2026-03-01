package com.example.pubsub.consumer;

import com.example.pubsub.model.OrderEvent;
import com.example.pubsub.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * Order consumer simulating the fulfillment service.
 */
@Service
public class OrderConsumerGroupA {

    private static final Logger log = LoggerFactory.getLogger(OrderConsumerGroupA.class);

    private final OrderRepository orderRepository;

    public OrderConsumerGroupA(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            dltTopicSuffix = "-dlt",
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "false"
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
            log.warn("[GROUP-A / FULFILLMENT] Order {} has FAIL status, throwing to trigger retry",
                    event.orderId());
            throw new RuntimeException("Order processing failed for orderId=" + event.orderId());
        }

        log.info("[GROUP-A / FULFILLMENT] Successfully fulfilled order {} for customer {} (amount={})",
                event.orderId(), event.customerId(), event.amount());

        orderRepository.findById(event.orderId()).ifPresentOrElse(
                order -> {
                    order.setStatus("COMPLETED");
                    order.setUpdatedAt(Instant.now());
                    orderRepository.save(order);
                    log.info("[GROUP-A] Order {} status updated to COMPLETED in DB", event.orderId());
                },
                () -> log.warn("[GROUP-A] Order {} not found in DB, skipping status update", event.orderId())
        );
    }

    @DltHandler
    public void handleDlt(
            OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic
    ) {
        log.error("[DLT] Order {} exhausted all retries, arrived at dead-letter topic: {}",
                event.orderId(), topic);
        log.error("[DLT] Manual intervention required for orderId={} customerId={} amount={}",
                event.orderId(), event.customerId(), event.amount());
    }
}
