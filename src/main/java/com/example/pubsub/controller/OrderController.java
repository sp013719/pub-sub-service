package com.example.pubsub.controller;

import com.example.pubsub.entity.Order;
import com.example.pubsub.model.OrderEvent;
import com.example.pubsub.model.OrderPublishResponse;
import com.example.pubsub.producer.OrderProducer;
import com.example.pubsub.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    private final OrderRepository orderRepository;

    @PostMapping
    public ResponseEntity<OrderPublishResponse> publishOrder(@RequestBody OrderEvent event) {
        log.info("[HTTP] Publishing order event: orderId={} status={}", event.orderId(), event.status());

        // Save first so failed publish attempts remain visible as SUBMITTED rows.
        Order order = Order.createSubmitted(event.orderId(), event.customerId(), event.amount());
        orderRepository.save(order);
        log.info("[HTTP] Order {} persisted with status=SUBMITTED", event.orderId());

        orderProducer.send(event);
        return ResponseEntity.accepted()
                .body(new OrderPublishResponse(
                        "accepted",
                        event.orderId(),
                        "Order event published to Kafka"
                ));
    }
}
