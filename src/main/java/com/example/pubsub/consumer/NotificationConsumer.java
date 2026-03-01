package com.example.pubsub.consumer;

import com.example.pubsub.model.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * Consumes notification events from the single-partition "notification-events" topic.
 *
 * KEY CONCEPT — Ordering guarantee with single partition:
 *
 *   Because notification-events has exactly 1 partition, all messages are
 *   delivered to this consumer in the exact order they were produced.
 *   There is only ever ONE active consumer for this topic (can't parallelize
 *   across partitions that don't exist).
 *
 *   Trade-off: strict ordering vs throughput. Use single-partition topics when
 *   sequence matters; use multi-partition topics when you need parallelism.
 */
@Service
public class NotificationConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationConsumer.class);

    @KafkaListener(topics = "notification-events", groupId = "notification-group")
    public void consume(
            NotificationEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[NOTIFICATION] Received notification {} | type={} | partition={} offset={}",
                event.notificationId(), event.type(), partition, offset);

        // Simulate dispatching the notification
        log.info("[NOTIFICATION] Dispatching {} to {} — \"{}\"",
                event.type(), event.recipientEmail(), event.message());
    }
}
