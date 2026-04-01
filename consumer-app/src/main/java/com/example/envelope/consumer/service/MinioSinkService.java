package com.example.envelope.consumer.service;

import com.example.envelope.model.CustomerEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MinioSinkService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinioSinkService.class);

    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String bucket;
    private final MinioClient minioClient;

    public MinioSinkService(
            ObjectMapper objectMapper,
            @Value("${app.minio.enabled:true}") boolean enabled,
            @Value("${app.minio.endpoint:http://localhost:9000}") String endpoint,
            @Value("${app.minio.access-key:minioadmin}") String accessKey,
            @Value("${app.minio.secret-key:minioadmin}") String secretKey,
            @Value("${app.minio.bucket:secure-customer-events}") String bucket) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.bucket = bucket;
        this.minioClient = MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    public void sink(CustomerEvent event) {
        if (!enabled) {
            return;
        }

        try {
            ensureBucketExists();
            String payload = objectMapper.writeValueAsString(event);
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            String objectName = event.getEventId() + ".json";

            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectName)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType("application/json")
                            .build());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize event for MinIO sink", e);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write event to MinIO sink", e);
        }
    }

    private void ensureBucketExists() throws Exception {
        boolean bucketExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!bucketExists) {
            LOGGER.info("Creating missing MinIO bucket: {}", bucket);
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }
}
