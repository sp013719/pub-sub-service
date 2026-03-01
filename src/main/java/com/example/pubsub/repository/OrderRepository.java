package com.example.pubsub.repository;

import com.example.pubsub.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for Order persistence.
 *
 * JpaRepository<Order, String> provides out-of-the-box:
 *   save(order)        – INSERT or UPDATE
 *   findById(orderId)  – SELECT by PK → returns Optional<Order>
 *
 * No custom queries needed — the built-in methods cover all use cases for this demo.
 */
public interface OrderRepository extends JpaRepository<Order, String> {
}
