package com.umang.notification.config;

import com.umang.notification.event.NotificationRequestedEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka wiring for the pub/sub backbone: JSON-serialized {@link NotificationRequestedEvent}
 * values on the {@code notification-requests} topic, plus a dead-letter topic and a consumer
 * error handler that retries a few times with a fixed backoff before routing the poison
 * record to the DLT.
 *
 * <p>The main topic is partitioned so consumers scale horizontally within a group: each
 * partition is processed by exactly one consumer instance, so throughput grows by adding
 * partitions + instances. Keying by {@code userId} would additionally preserve per-user order.
 */
@Configuration
@EnableKafka
public class KafkaConfig {

    public static final String NOTIFICATION_REQUESTS_TOPIC = "notification-requests";
    public static final String NOTIFICATION_REQUESTS_DLT = "notification-requests.DLT";

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Bean
    public NewTopic notificationRequestsTopic() {
        return TopicBuilder.name(NOTIFICATION_REQUESTS_TOPIC).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic notificationRequestsDltTopic() {
        return TopicBuilder.name(NOTIFICATION_REQUESTS_DLT).partitions(3).replicas(1).build();
    }

    @Bean
    public ProducerFactory<String, NotificationRequestedEvent> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, NotificationRequestedEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ConsumerFactory<String, NotificationRequestedEvent> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "notification-service");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.umang.notification.event");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, NotificationRequestedEvent.class.getName());
        JsonDeserializer<NotificationRequestedEvent> valueDeserializer =
                new JsonDeserializer<>(NotificationRequestedEvent.class);
        valueDeserializer.addTrustedPackages("com.umang.notification.event");
        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, NotificationRequestedEvent>
            kafkaListenerContainerFactory(
                    KafkaTemplate<String, NotificationRequestedEvent> kafkaTemplate) {
        ConcurrentKafkaListenerContainerFactory<String, NotificationRequestedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());

        // Retry 3 times, 2s apart; then publish the failed record to <topic>.DLT.
        // Deserialization / template-not-found errors are non-retryable and are handled
        // in the consumer (recorded FAILED without throwing) so they never reach here.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> new TopicPartition(NOTIFICATION_REQUESTS_DLT, record.partition()));
        DefaultErrorHandler errorHandler =
                new DefaultErrorHandler(recoverer, new FixedBackOff(2000L, 3L));
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }
}
