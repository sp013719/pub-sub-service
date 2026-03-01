package com.example.pubsub.producer;

import com.example.pubsub.model.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Publishes NotificationEvent messages to the "notification-events" topic.
 *
 * No message key is set here — with a single-partition topic, all messages
 * are consumed in strict FIFO order regardless of key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationProducer {

    private static final String TOPIC = "notification-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void send(NotificationEvent event) {
        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[PRODUCER] Failed to send notification {}: {}",
                        event.notificationId(), ex.getMessage());
            } else {
                int partition = result.getRecordMetadata().partition();
                long offset   = result.getRecordMetadata().offset();
                log.info("[PRODUCER] Sent notification {} → topic={} partition={} offset={}",
                        event.notificationId(), TOPIC, partition, offset);
            }
        });
    }
}
