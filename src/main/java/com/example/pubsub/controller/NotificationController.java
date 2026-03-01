package com.example.pubsub.controller;

import com.example.pubsub.model.NotificationEvent;
import com.example.pubsub.model.NotificationPublishResponse;
import com.example.pubsub.producer.NotificationProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationProducer notificationProducer;

    public NotificationController(NotificationProducer notificationProducer) {
        this.notificationProducer = notificationProducer;
    }

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
