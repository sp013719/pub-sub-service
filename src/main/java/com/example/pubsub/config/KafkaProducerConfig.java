package com.example.pubsub.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures a shared KafkaTemplate used by all producers.
 *
 * Key decisions:
 *  - Value type is Object so one template can send both OrderEvent and NotificationEvent
 *  - JsonSerializer handles serialization automatically via Jackson
 *  - Type headers use SHORT logical names (e.g. "orderEvent") not full Java class names.
 *    This keeps headers portable across languages/services while still giving the
 *    consumer enough info to pick the right deserializer target type.
 *    Mapping: orderEvent ↔ OrderEvent, notificationEvent ↔ NotificationEvent
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        // Embed a short logical type name in the __TypeId__ header so consumers
        // know which class to deserialize to (without embedding the Java FQCN)
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        props.put(JsonSerializer.TYPE_MAPPINGS,
                "orderEvent:com.example.pubsub.model.OrderEvent," +
                "notificationEvent:com.example.pubsub.model.NotificationEvent");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
