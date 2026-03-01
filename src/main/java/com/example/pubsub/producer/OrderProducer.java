package com.example.pubsub.producer;

import com.example.pubsub.model.OrderEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes OrderEvent messages to the "order-events" topic.
 *
 * KEY CONCEPT — Partition routing by message key:
 *   Kafka hashes the message key (orderId) to determine which partition receives it.
 *   All messages with the same orderId go to the same partition → guaranteed ordering
 *   per order, while different orders can be processed in parallel across partitions.
 *
 *   Run the partition-routing curl loop and watch the logs — you'll see:
 *     ORD-001 → partition 2, ORD-002 → partition 0, etc. (deterministic per key)
 */
@Service
public class OrderProducer {

    private static final String TOPIC = "order-events";
    private static final Logger log = LoggerFactory.getLogger(OrderProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void send(OrderEvent event) {
        // orderId is the message key → same orderId always lands on the same partition
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, event.orderId(), event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[PRODUCER] Failed to send order {}: {}", event.orderId(), ex.getMessage());
            } else {
                int partition = result.getRecordMetadata().partition();
                long offset   = result.getRecordMetadata().offset();
                log.info("[PRODUCER] Sent order {} → topic={} partition={} offset={}",
                        event.orderId(), TOPIC, partition, offset);
            }
        });
    }
}
