package com.example.envelope.consumer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "app.kafka.listener.auto-startup=false")
class ConsumerApplicationTests {

    @Test
    void contextLoads() {
    }
}
