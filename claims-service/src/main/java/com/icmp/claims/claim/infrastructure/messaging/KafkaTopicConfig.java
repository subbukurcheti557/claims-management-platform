package com.icmp.claims.claim.infrastructure.messaging;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/** claims-service owns the lifecycle topic, so it declares it (and the shared dead-letter topic). */
@Configuration
public class KafkaTopicConfig {

    @Bean
    NewTopic claimsLifecycleTopic(@Value("${app.kafka.claims-topic}") String topic,
                                  @Value("${app.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }

    @Bean
    NewTopic claimsLifecycleDeadLetterTopic(@Value("${app.kafka.claims-topic}") String topic,
                                            @Value("${app.kafka.partitions:3}") int partitions) {
        return TopicBuilder.name(topic + ".DLT").partitions(partitions).replicas(1).build();
    }
}
