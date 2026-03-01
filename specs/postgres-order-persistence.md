# Spec: Add PostgreSQL Persistence to Order Flow

## Context
The pub-sub-service is currently a pure Kafka streaming demo with no database. The goal is to add PostgreSQL persistence so that:
- When a POST /api/orders request arrives, the order is saved to DB with status **"SUBMITTED"**
- When OrderConsumerGroupA (fulfillment) successfully processes the event, it updates the order to **"COMPLETED"**
- Failed orders (status="FAIL" → retry → DLT) stay **"SUBMITTED"** in DB — demonstrating that Kafka retries and DB state are independent concerns

## Data Flow After Change
```
POST /api/orders
  ├─► orderRepository.save(order, status=SUBMITTED)   [synchronous]
  └─► orderProducer.send(event)                        [async, unchanged]
           │
           └─► Kafka "order-events"
                   │
                   ├─► OrderConsumerGroupA
                   │     ├─► happy path → findById(orderId) → status=COMPLETED → save
                   │     └─► FAIL path  → throw → retry → DLT (no DB update)
                   │
                   └─► OrderConsumerGroupB (analytics, no DB change)
```

---

## Files to Modify

| File | Change |
|---|---|
| `pom.xml` | Add JPA starter + PostgreSQL driver |
| `docker-compose.yml` | Add postgres service + named volume |
| `src/main/resources/application.yml` | Add datasource + JPA config |
| `src/main/java/com/example/pubsub/controller/OrderController.java` | Inject repo, save before publish |
| `src/main/java/com/example/pubsub/consumer/OrderConsumerGroupA.java` | Inject repo, update to COMPLETED on success |

## Files to Create

| File | Purpose |
|---|---|
| `src/main/java/com/example/pubsub/entity/Order.java` | JPA entity for `orders` table |
| `src/main/java/com/example/pubsub/repository/OrderRepository.java` | Spring Data JPA interface |

---

## Step 1 — pom.xml: Add Dependencies

Inside `<dependencies>`, after the Lombok block:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```
No version attributes needed — Spring Boot 3.4.3 BOM manages both.

---

## Step 2 — docker-compose.yml: Add PostgreSQL

Add as a sibling service under `services:`. Also add a top-level `volumes:` section.

```yaml
  postgres:
    image: postgres:16-alpine
    container_name: postgres
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: pubsub
      POSTGRES_USER: pubsub
      POSTGRES_PASSWORD: pubsub
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U pubsub -d pubsub"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

volumes:
  postgres_data:
```

---

## Step 3 — application.yml: Add Datasource + JPA

Add inside `spring:` block, as a sibling to `spring.kafka:`:
```yaml
  datasource:
    url: jdbc:postgresql://localhost:5432/pubsub
    username: pubsub
    password: pubsub
    driver-class-name: org.postgresql.Driver

  jpa:
    hibernate:
      ddl-auto: update    # auto-creates table on first run; never use in production
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
```

---

## Step 4 — Create Order.java Entity

**Path:** `src/main/java/com/example/pubsub/entity/Order.java`

Key design decisions:
- Use `orderId` (String) as `@Id` — the business key, makes `findById(event.orderId())` natural in the consumer
- `createdAt` is `Instant`, marked `updatable = false` — set once at insert time
- `updatedAt` is nullable — null means the order is still in-flight (visually informative in psql)
- Static factory method `createSubmitted(...)` encapsulates creation logic

```java
package com.example.pubsub.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Getter @Setter @NoArgsConstructor
public class Order {

    @Id
    @Column(name = "order_id", nullable = false)
    private String orderId;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public static Order createSubmitted(String orderId, String customerId, BigDecimal amount) {
        Order order = new Order();
        order.orderId    = orderId;
        order.customerId = customerId;
        order.amount     = amount;
        order.status     = "SUBMITTED";
        order.createdAt  = Instant.now();
        return order;
    }
}
```

---

## Step 5 — Create OrderRepository.java

**Path:** `src/main/java/com/example/pubsub/repository/OrderRepository.java`

```java
package com.example.pubsub.repository;

import com.example.pubsub.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, String> {
    // save(order) and findById(orderId) from JpaRepository cover all use cases
}
```

---

## Step 6 — Modify OrderController.java

Add `private final OrderRepository orderRepository;` field (Lombok `@RequiredArgsConstructor` handles injection automatically).

In the `publishOrder` method, save to DB **before** the Kafka publish:
```java
// Save to DB first — if Kafka publish fails, order is visible as stuck SUBMITTED
Order order = Order.createSubmitted(event.orderId(), event.customerId(), event.amount());
orderRepository.save(order);
log.info("[HTTP] Order {} persisted with status=SUBMITTED", event.orderId());

orderProducer.send(event);  // unchanged async publish
```

The ordering (DB save → Kafka publish) is intentional: a failed Kafka publish leaves a detectable stuck order. The reverse would create phantom Kafka events with no DB record.

---

## Step 7 — Modify OrderConsumerGroupA.java

Add `@RequiredArgsConstructor` to the class and inject `private final OrderRepository orderRepository`.

In the `consume` method, after the successful happy-path log, add:
```java
orderRepository.findById(event.orderId()).ifPresentOrElse(
    order -> {
        order.setStatus("COMPLETED");
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        log.info("[GROUP-A] Order {} status updated to COMPLETED in DB", event.orderId());
    },
    () -> log.warn("[GROUP-A] Order {} not found in DB — skipping status update", event.orderId())
);
```

The `@DltHandler` method and the FAIL-path exception throw are **unchanged**.

---

## Verification

### 1. Start infrastructure
```bash
docker-compose up -d
docker-compose ps   # postgres, kafka, kafka-ui all "healthy"
```

### 2. Start the app — confirm Hibernate creates the table
```bash
./mvnw spring-boot:run
# Watch for: Hibernate: create table if not exists orders (...)
```

### 3. Happy path — order should reach COMPLETED
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerId":"CUST-A","amount":99.99,"status":"NEW"}'
```
```bash
docker exec -it postgres psql -U pubsub -d pubsub -c "SELECT * FROM orders;"
# Expected: ORD-001 | CUST-A | 99.99 | COMPLETED | <created_at> | <updated_at>
```

### 4. Failure path — order should stay SUBMITTED
```bash
curl -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-FAIL","customerId":"CUST-B","amount":50.00,"status":"FAIL"}'
```
```bash
docker exec -it postgres psql -U pubsub -d pubsub \
  -c "SELECT order_id, status, updated_at FROM orders WHERE order_id = 'ORD-FAIL';"
# Expected: ORD-FAIL | SUBMITTED | (null)  — stuck order, DLT consumed on Kafka side
```
