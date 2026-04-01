package com.example.envelope.producer.service;

import com.example.envelope.model.CustomerEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class EventProducerService {

    private final KafkaTemplate<String, CustomerEvent> kafkaTemplate;
    private final DekRegistryBootstrapService dekRegistryBootstrapService;
    private final SsnCryptoService ssnCryptoService;
    private final String topic;

    public EventProducerService(
            KafkaTemplate<String, CustomerEvent> kafkaTemplate,
            DekRegistryBootstrapService dekRegistryBootstrapService,
            SsnCryptoService ssnCryptoService,
            @Value("${app.topic}") String topic) {
        this.kafkaTemplate = kafkaTemplate;
        this.dekRegistryBootstrapService = dekRegistryBootstrapService;
        this.ssnCryptoService = ssnCryptoService;
        this.topic = topic;
    }

    public void publish(CustomerEvent event) {
        dekRegistryBootstrapService.ensureDekForTopic(topic);
        event.setSsn(ssnCryptoService.encrypt(event.getSsn()));
        kafkaTemplate.send(topic, event.getCustomerId(), event);
    }
}
