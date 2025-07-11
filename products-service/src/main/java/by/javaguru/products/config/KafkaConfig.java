package by.javaguru.products.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.UUID;

@Configuration
public class KafkaConfig {

    @Value("${products.events.topic.name}")
    private String productsEventsTopicName;
    private final static Integer TOPIC_REPLICATION_FACTOR=3;
    private final static Integer TOPIC_PARTITIONS=3;

    @Bean
    KafkaTemplate<UUID, Object> kafkaTemplate(ProducerFactory<UUID, Object> producerFactory) {
        KafkaTemplate<UUID, Object> kafkaTemplate = new KafkaTemplate<>(producerFactory);
        kafkaTemplate.setObservationEnabled(true);

        return kafkaTemplate;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<UUID, Object> kafkaListenerContainerFactory(
            ConsumerFactory<UUID, Object> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<UUID, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setObservationEnabled(true);

        return factory;
    }

    @Bean
    NewTopic createProductsEventsTopic() {
        return TopicBuilder.name(productsEventsTopicName)
                .partitions(TOPIC_PARTITIONS)
                .replicas(TOPIC_REPLICATION_FACTOR)
                .build();
    }
}
