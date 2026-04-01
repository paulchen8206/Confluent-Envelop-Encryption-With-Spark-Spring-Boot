package com.example.envelope.generator.service;

import com.example.envelope.model.CustomerEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class CustomerEventGeneratorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CustomerEventGeneratorService.class);

    private static final List<String> NAMES = List.of(
            "Ada Lovelace", "Grace Hopper", "Alan Turing", "Dorothy Vaughan", "Margaret Hamilton");
    private static final List<String> ACTIONS = List.of(
            "ACCOUNT_OPENED", "ACCOUNT_UPDATED", "ACCOUNT_CLOSED", "PROFILE_VIEWED", "PASSWORD_CHANGED");

    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final String bucket;
    private final Random random = new Random();

    public CustomerEventGeneratorService(
            ObjectMapper objectMapper,
            @Value("${app.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.minio.secret-key:minioadmin}") String secretKey,
            @Value("${app.minio.bucket:event-inbox}") String bucket) {
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Scheduled(fixedDelayString = "${app.generator.interval-ms:5000}")
    public void generate() {
        try {
            ensureBucketExists();
            CustomerEvent event = buildRandomEvent();
            String payload = objectMapper.writeValueAsString(event);
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            String objectName = "pending/" + event.getEventId() + ".json";

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType("application/json")
                            .build());

            LOGGER.info("Generated event {} -> minio://{}/{}", event.getEventId(), bucket, objectName);
        } catch (Exception e) {
            LOGGER.error("Failed to generate event", e);
        }
    }

    private CustomerEvent buildRandomEvent() {
        CustomerEvent event = new CustomerEvent();
        event.setEventId("evt-" + UUID.randomUUID());
        event.setCustomerId("cust-" + (1000 + random.nextInt(9000)));
        event.setFullName(NAMES.get(random.nextInt(NAMES.size())));
        event.setSsn(String.format("%03d-%02d-%04d",
                random.nextInt(900) + 100,
                random.nextInt(90) + 10,
                random.nextInt(9000) + 1000));
        event.setAction(ACTIONS.get(random.nextInt(ACTIONS.size())));
        return event;
    }

    private void ensureBucketExists() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            LOGGER.info("Creating MinIO bucket: {}", bucket);
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
