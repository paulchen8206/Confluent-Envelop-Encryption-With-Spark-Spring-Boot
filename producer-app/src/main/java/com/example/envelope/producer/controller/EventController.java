package com.example.envelope.producer.controller;

import com.example.envelope.model.CustomerEvent;
import com.example.envelope.producer.service.EventProducerService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventProducerService producerService;

    public EventController(EventProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> publish(@Valid @RequestBody CustomerEvent event) {
        producerService.publish(event);
        return ResponseEntity.accepted().body(Map.of("status", "queued", "eventId", event.getEventId()));
    }
}
