package com.example.envelope.producer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class DekRegistryBootstrapService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DekRegistryBootstrapService.class);

    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String dekRegistryTopic;
    private final String kekName;
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public DekRegistryBootstrapService(
            KafkaTemplate<String, String> stringKafkaTemplate,
            ObjectMapper objectMapper,
            @Value("${app.dek.registry.topic:_dek_registry_keys}") String dekRegistryTopic,
            @Value("${spring.kafka.properties.rule.executors._default_.param.kek.name:customer-pii-kek}")
                    String kekName) {
        this.stringKafkaTemplate = stringKafkaTemplate;
        this.objectMapper = objectMapper;
        this.dekRegistryTopic = dekRegistryTopic;
        this.kekName = kekName;
    }

    public void ensureDekForTopic(String businessTopic) {
        if (!initialized.compareAndSet(false, true)) {
            return;
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("kekName", kekName);
            payload.put("topic", businessTopic);
            payload.put("algorithm", "AES-256");
            payload.put("generatedAt", Instant.now().toString());
            payload.put("dek", generateDek());

            String key = kekName + ":" + businessTopic;
            String value = objectMapper.writeValueAsString(payload);
            stringKafkaTemplate.send(dekRegistryTopic, key, value);

            LOGGER.info("DEK bootstrap published to {} for topic {}", dekRegistryTopic, businessTopic);
        } catch (Exception e) {
            initialized.set(false);
            throw new IllegalStateException("Failed to publish DEK bootstrap record", e);
        }
    }

    private String generateDek() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
