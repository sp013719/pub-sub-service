package com.example.pubsub.controller;

import com.example.pubsub.model.NotificationEvent;
import com.example.pubsub.model.NotificationPublishResponse;
import com.example.pubsub.producer.NotificationProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for publishing notification events.
 *
 * POST /api/notifications  →  publishes to Kafka "notification-events" topic
 */
@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationProducer notificationProducer;

    @PostMapping
    public ResponseEntity<NotificationPublishResponse> publishNotification(@RequestBody NotificationEvent event) {
        log.info("[HTTP] Publishing notification event: notificationId={} type={}",
                event.notificationId(), event.type());
        notificationProducer.send(event);
        return ResponseEntity.accepted()
                .body(new NotificationPublishResponse(
                        "accepted",
                        event.notificationId(),
                        "Notification event published to Kafka"
                ));
    }
}
