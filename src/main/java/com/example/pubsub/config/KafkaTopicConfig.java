package com.example.pubsub.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declares all Kafka topics as Spring beans.
 *
 * Making topics explicit here (rather than relying on auto-creation) means:
 *  - Partition counts and replication factors are version-controlled
 *  - Topics are created before consumers try to subscribe
 *  - The topology of your event system is visible at a glance
 *
 * Partition notes:
 *  - order-events has 3 partitions → up to 3 consumers in a group can run in parallel
 *  - notification-events has 1 partition → strict FIFO ordering guaranteed
 *  - order-events-dlt has 1 partition → dead-letter messages are ordered
 */
@Configuration
public class KafkaTopicConfig {

    /** Main order event stream — 3 partitions enable parallel consumption. */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name("order-events")
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Notification stream — single partition guarantees strict delivery order.
     * Useful when sequence matters (e.g. "welcome" must arrive before "receipt").
     */
    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name("notification-events")
                .partitions(1)
                .replicas(1)
                .build();
    }

    /**
     * Dead-letter topic for orders that exhausted all retries.
     * Messages here need manual inspection / alerting.
     */
    @Bean
    public NewTopic orderEventsDlt() {
        return TopicBuilder.name("order-events-dlt")
                .partitions(1)
                .replicas(1)
                .build();
    }
}
