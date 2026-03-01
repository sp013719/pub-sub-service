package com.example.pubsub.controller;

import com.example.pubsub.entity.Order;
import com.example.pubsub.model.OrderEvent;
import com.example.pubsub.model.OrderPublishResponse;
import com.example.pubsub.producer.OrderProducer;
import com.example.pubsub.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for publishing order events.
 *
 * POST /api/orders publishes to Kafka "order-events" after the order is persisted.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderProducer orderProducer;
    private final OrderRepository orderRepository;

    public OrderController(OrderProducer orderProducer, OrderRepository orderRepository) {
        this.orderProducer = orderProducer;
        this.orderRepository = orderRepository;
    }

    @PostMapping
    public ResponseEntity<OrderPublishResponse> publishOrder(@RequestBody OrderEvent event) {
        log.info("[HTTP] Publishing order event: orderId={} status={}", event.orderId(), event.status());

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
