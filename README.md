# pub-sub-service

A Spring Boot + Apache Kafka educational demo that illustrates core pub-sub patterns through two independent event streams.

## What This Is

This project is a runnable reference implementation for learning Kafka concepts hands-on. It is **not** a production service — every design decision is annotated with comments explaining the trade-offs.

## Architecture Overview

```
HTTP Client
    │
    ├── POST /api/orders         ──► OrderProducer ──► order-events (3 partitions)
    │                                                       │
    │                                           ┌───────────┴───────────┐
    │                                           ▼                       ▼
    │                                    order-group-a           order-group-b
    │                                   (Fulfillment)            (Analytics)
    │                                    + retry chain
    │                                    + DLT handler
    │
    └── POST /api/notifications  ──► NotificationProducer ──► notification-events (1 partition)
                                                                    │
                                                                    ▼
                                                          notification-group
                                                          (strict FIFO order)
```

## Kafka Concepts Demonstrated

### 1. Consumer Groups — Fan-Out vs Load Balancing

`order-events` has **two independent consumer groups**:

| Group | Simulates | Behaviour |
|---|---|---|
| `order-group-a` | Fulfillment service | Receives every message; has retry + DLT |
| `order-group-b` | Analytics service | Receives every message independently |

Both groups receive **every** order event. This is the pub-sub fan-out pattern. Consumers in the **same** group share work (load balancing); consumers in **different** groups each get a full copy.

### 2. Partition Routing and Ordering

`order-events` has **3 partitions**. The producer uses `orderId` as the message key. Kafka hashes the key deterministically, so:

- All events for the same `orderId` always land on the same partition → guaranteed per-order ordering
- Different `orderId`s can be processed in parallel across partitions

`notification-events` has **1 partition** → strict global FIFO, but no parallelism.

### 3. Retry Pipeline and Dead-Letter Topic (DLT)

`OrderConsumerGroupA` uses `@RetryableTopic` to build an automatic retry chain:

```
order-events
  → order-events-retry-0  (after 1 s)
  → order-events-retry-1  (after 2 s)
  → order-events-retry-2  (after 4 s)
  → order-events-dlt      (@DltHandler — manual intervention)
```

Send an event with `"status": "FAIL"` to watch the full pipeline in the logs. The original topic is **never blocked** during retries; retries happen on dedicated topics.

### 4. Type-Safe JSON Serialization

Messages are serialized as JSON. A short logical type name (`orderEvent`, `notificationEvent`) is embedded in the Kafka `__TypeId__` header rather than the full Java class name. This keeps headers portable across languages and services while allowing the consumer to deserialize to the correct Java type.

## Project Structure

```
src/main/java/com/example/pubsub/
├── PubSubApplication.java
├── config/
│   ├── KafkaProducerConfig.java   # Shared KafkaTemplate, type-header mapping
│   └── KafkaTopicConfig.java      # Topic declarations (partitions, replicas, DLT)
├── model/
│   ├── OrderEvent.java            # record: orderId, customerId, amount, status
│   └── NotificationEvent.java     # record: notificationId, recipientEmail, message, type
├── producer/
│   ├── OrderProducer.java         # Sends to order-events keyed by orderId
│   └── NotificationProducer.java  # Sends to notification-events (no key)
├── consumer/
│   ├── OrderConsumerGroupA.java   # Fulfillment — retry + DLT pipeline
│   ├── OrderConsumerGroupB.java   # Analytics — no retry
│   └── NotificationConsumer.java  # Single-partition FIFO consumer
└── controller/
    ├── OrderController.java        # POST /api/orders → 202 Accepted
    └── NotificationController.java # POST /api/notifications → 202 Accepted
```

## Tech Stack

| Component | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.3 |
| Spring Kafka | (managed by Boot) |
| Apache Kafka | 3.9.0 (KRaft, no Zookeeper) |
| Kafka UI | provectuslabs/kafka-ui:latest |
| Lombok | (compile-time only) |

## Running Locally

### 1. Start Kafka and Kafka UI

```bash
docker compose up -d
```

- Kafka broker: `localhost:9092`
- Kafka UI: [http://localhost:8090](http://localhost:8090)

Kafka UI connects via the internal Docker listener (`kafka:29092`). The Spring Boot app connects via the host-facing listener (`localhost:9092`).

### 2. Start the Spring Boot Application

```bash
./mvnw spring-boot:run
```

The app starts on **port 8081** (port 8080 is used by Kafka UI's internal container port).

### 3. Verify

- Health check: [http://localhost:8081/actuator/health](http://localhost:8081/actuator/health)
- Kafka UI topics: [http://localhost:8090](http://localhost:8090)

## API Reference

### Publish an Order Event

```bash
POST /api/orders
Content-Type: application/json

{
  "orderId": "ORD-001",
  "customerId": "CUST-42",
  "amount": 129.99,
  "status": "NEW"
}
```

**status values:**
- `NEW` / `PROCESSING` — happy path; fulfilled by group-a, recorded by group-b
- `FAIL` — triggers the retry + DLT pipeline in group-a

Returns `202 Accepted` immediately. Processing is asynchronous.

### Publish a Notification Event

```bash
POST /api/notifications
Content-Type: application/json

{
  "notificationId": "NOTIF-001",
  "recipientEmail": "user@example.com",
  "message": "Your order has shipped!",
  "type": "EMAIL"
}
```

**type values:** `EMAIL`, `SMS`, `PUSH`

Returns `202 Accepted` immediately.

## Example curl Recipes

```bash
# Happy-path order
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-001","customerId":"CUST-1","amount":99.99,"status":"NEW"}' | jq

# Trigger the retry + DLT pipeline (watch logs for retry-0, retry-1, retry-2, dlt)
curl -s -X POST http://localhost:8081/api/orders \
  -H "Content-Type: application/json" \
  -d '{"orderId":"ORD-BAD","customerId":"CUST-1","amount":0,"status":"FAIL"}' | jq

# Demonstrate partition routing — same orderId always → same partition
for id in ORD-001 ORD-002 ORD-003; do
  curl -s -X POST http://localhost:8081/api/orders \
    -H "Content-Type: application/json" \
    -d "{\"orderId\":\"$id\",\"customerId\":\"CUST-1\",\"amount\":10,\"status\":\"NEW\"}" | jq .orderId
done

# Send a notification
curl -s -X POST http://localhost:8081/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"notificationId":"NOTIF-001","recipientEmail":"user@example.com","message":"Your order shipped!","type":"EMAIL"}' | jq
```

## Key Configuration Notes

| Setting | Value | Why |
|---|---|---|
| `auto-offset-reset` | `earliest` | New consumer groups read from the beginning of the topic |
| `ack-mode` | `RECORD` | Offset committed after each message — predictable, fine-grained |
| `autoCreateTopics` (RetryableTopic) | `false` | Topics are declared as explicit Spring beans |
| `trusted.packages` | `com.example.pubsub.model` | Security guardrail on JSON deserialization |
