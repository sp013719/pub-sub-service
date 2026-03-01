package com.example.pubsub.consumer;

import com.example.pubsub.model.OrderEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * Order consumer simulating the ANALYTICS service.
 *
 * KEY CONCEPT — Independent Consumer Groups:
 *
 *   This listener uses groupId="order-group-b", which is a completely separate
 *   consumer group from "order-group-a". Each group maintains its own offset
 *   pointer in Kafka, meaning:
 *
 *     - group-a and group-b BOTH receive every message on order-events
 *     - group-a committing an offset does NOT affect group-b's position
 *     - If group-b is restarted, it picks up where IT left off, independent of group-a
 *
 *   This is the pub-sub fan-out pattern: one topic, many independent subscribers.
 *   Compare this to a queue pattern where consumers in the SAME group share work
 *   (each message goes to exactly one consumer in the group).
 *
 *   No retry here — analytics can afford to skip a bad message rather than
 *   blocking the pipeline. In production you might log to a metrics store instead.
 */
@Slf4j
@Service
public class OrderConsumerGroupB {

    @KafkaListener(topics = "order-events", groupId = "order-group-b")
    public void consume(
            OrderEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset
    ) {
        log.info("[GROUP-B / ANALYTICS] Received order {} | status={} | partition={} offset={}",
                event.orderId(), event.status(), partition, offset);

        // Simulate analytics processing — count orders, aggregate revenue, etc.
        log.info("[GROUP-B / ANALYTICS] Recording metric: orderId={} amount={} customerId={}",
                event.orderId(), event.amount(), event.customerId());
    }
}
