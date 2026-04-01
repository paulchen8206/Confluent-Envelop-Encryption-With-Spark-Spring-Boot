package com.example.envelope.producer.config;

import com.example.envelope.model.CustomerEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.util.StringUtils;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.properties.schema.registry.url}")
    private String schemaRegistryUrl;

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

    @Value("${spring.kafka.properties.latest.compatibility.strict:false}")
    private boolean latestCompatibilityStrict;

    @Bean
    public ProducerFactory<String, CustomerEvent> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer.class);

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
        config.put("latest.compatibility.strict", latestCompatibilityStrict);
        config.put("use.latest.version", true);
        config.put("auto.register.schemas", false);

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, CustomerEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public KafkaTemplate<String, String> stringKafkaTemplate() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(config));
    }
}
