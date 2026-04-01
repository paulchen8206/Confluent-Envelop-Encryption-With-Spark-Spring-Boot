package com.example.envelope.consumer.service;

import com.example.envelope.model.CustomerEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
public class EventConsumerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventConsumerService.class);
    private final MinioSinkService minioSinkService;
    private final SsnCryptoService ssnCryptoService;

    public EventConsumerService(MinioSinkService minioSinkService, SsnCryptoService ssnCryptoService) {
        this.minioSinkService = minioSinkService;
        this.ssnCryptoService = ssnCryptoService;
    }

    @KafkaListener(topics = "${app.topic}", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(CustomerEvent event) {
        event.setSsn(ssnCryptoService.decrypt(event.getSsn()));
        LOGGER.info(
                "Consumed decrypted event: eventId={}, customerId={}, fullName={}, ssn={}, action={}",
                event.getEventId(),
                event.getCustomerId(),
                event.getFullName(),
                event.getSsn(),
                event.getAction());
        minioSinkService.sink(event);
    }
}
