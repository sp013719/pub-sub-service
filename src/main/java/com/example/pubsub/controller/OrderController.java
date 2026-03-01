package com.example.pubsub.controller;

import com.example.pubsub.model.OrderEvent;
import com.example.pubsub.producer.OrderProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST endpoint for publishing order events.
 *
 * POST /api/orders  →  publishes to Kafka "order-events" topic
 *
 * The controller immediately returns 202 Accepted — the actual processing
 * (fulfillment, analytics) happens asynchronously in consumers.
 * This decoupling is the key value proposition of pub-sub architecture.
 */
@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderProducer orderProducer;

    @PostMapping
    public ResponseEntity<Map<String, String>> publishOrder(@RequestBody OrderEvent event) {
        log.info("[HTTP] Publishing order event: orderId={} status={}", event.orderId(), event.status());
        orderProducer.send(event);
        return ResponseEntity.accepted()
                .body(Map.of(
                        "status", "accepted",
                        "orderId", event.orderId(),
                        "message", "Order event published to Kafka"
                ));
    }
}
