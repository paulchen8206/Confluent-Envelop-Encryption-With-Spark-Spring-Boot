package com.example.envelope.consumer.config;

import com.example.envelope.model.CustomerEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.util.StringUtils;

@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;

    @Value("${spring.kafka.properties.rule.executors._default_.class}")
    private String ruleExecutorClass;

    @Value("${spring.kafka.properties.rule.executors._default_.param.kek.name}")
    private String kekName;

    @Value("${spring.kafka.properties.rule.executors._default_.param.kms.type}")
    private String kmsType;

    @Value("${spring.kafka.properties.rule.executors._default_.param.kms.key.id}")
    private String kmsKeyId;

    @Value("${spring.kafka.properties.rule.executors._default_.param.kms.key.secret}")
    private String kmsKeySecret;

    @Value("${spring.kafka.properties.rule.executors._default_.param.kms.region:}")
    private String kmsRegion;

    @Value("${spring.kafka.properties.rule.executors._default_.param.kms.endpoint:}")
    private String kmsEndpoint;

    @Value("${spring.kafka.properties.rule.executors._default_.param.kms.access.key.id:}")
    private String kmsAccessKeyId;

    @Value("${spring.kafka.properties.rule.executors._default_.param.kms.secret.access.key:}")
    private String kmsSecretAccessKey;

    @Value("${app.kafka.listener.auto-startup:true}")
    private boolean listenerAutoStartup;

    @Bean
    public ConsumerFactory<String, CustomerEvent> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer.class);
        config.put("json.value.type", CustomerEvent.class.getName());
        config.put("schema.registry.url", schemaRegistryUrl);
        config.put("rule.executors._default_.class", ruleExecutorClass);
        config.put("rule.executors._default_.param.kek.name", kekName);
        config.put("rule.executors._default_.param.kms.type", kmsType);
        config.put("rule.executors._default_.param.kms.key.id", kmsKeyId);
        if (StringUtils.hasText(kmsKeySecret)) {
            config.put("rule.executors._default_.param.kms.key.secret", kmsKeySecret);
        }
        if (StringUtils.hasText(kmsRegion)) {
            config.put("rule.executors._default_.param.kms.region", kmsRegion);
        }
        if (StringUtils.hasText(kmsEndpoint)) {
            config.put("rule.executors._default_.param.kms.endpoint", kmsEndpoint);
        }
        if (StringUtils.hasText(kmsAccessKeyId)) {
            config.put("rule.executors._default_.param.kms.access.key.id", kmsAccessKeyId);
        }
        if (StringUtils.hasText(kmsSecretAccessKey)) {
            config.put("rule.executors._default_.param.kms.secret.access.key", kmsSecretAccessKey);
        }
        config.put("use.latest.version", true);
        config.put("auto.register.schemas", false);
        return new DefaultKafkaConsumerFactory<>(config);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CustomerEvent> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CustomerEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setAutoStartup(listenerAutoStartup);
        return factory;
    }
}
