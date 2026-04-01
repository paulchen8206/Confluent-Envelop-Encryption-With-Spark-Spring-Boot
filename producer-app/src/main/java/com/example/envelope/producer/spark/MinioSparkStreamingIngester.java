package com.example.envelope.producer.spark;

import com.example.envelope.model.CustomerEvent;
import com.example.envelope.producer.service.EventProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.GetObjectArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Simulates a Spark Structured Streaming micro-batch that polls MinIO
 * for new CustomerEvent JSON files written by event-generator under
 * {@code event-inbox/pending/}, deserialises them, publishes to Kafka
 * via {@link EventProducerService}, then moves each file to
 * {@code event-inbox/processed/} to avoid re-processing.
 *
 * <p>In a real Spark deployment this component would be replaced by a
 * {@code spark.readStream().format("json").load("s3a://event-inbox/pending/")}
 * pipeline running in a Spark cluster. The scheduling interval acts as
 * the micro-batch trigger interval.
 */
@Component
public class MinioSparkStreamingIngester {

    private static final Logger LOGGER = LoggerFactory.getLogger(MinioSparkStreamingIngester.class);
    private static final String PENDING_PREFIX = "pending/";
    private static final String PROCESSED_PREFIX = "processed/";

    private final EventProducerService producerService;
    private final ObjectMapper objectMapper;
    private final MinioClient minioClient;
    private final String bucket;

    public MinioSparkStreamingIngester(
            EventProducerService producerService,
            ObjectMapper objectMapper,
            @Value("${app.minio.inbox.endpoint:http://minio:9000}") String endpoint,
            @Value("${app.minio.inbox.access-key:minioadmin}") String accessKey,
            @Value("${app.minio.inbox.secret-key:minioadmin}") String secretKey,
            @Value("${app.minio.inbox.bucket:event-inbox}") String bucket) {
        this.producerService = producerService;
        this.objectMapper = objectMapper;
        this.bucket = bucket;
        this.minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Scheduled(fixedDelayString = "${app.spark.micro-batch.interval-ms:3000}")
    public void processBatch() {
        Iterable<Result<Item>> objects;
        try {
            objects = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(PENDING_PREFIX)
                            .recursive(true)
                            .build());
        } catch (Exception e) {
            LOGGER.warn("MinIO inbox not reachable yet: {}", e.getMessage());
            return;
        }

        int processed = 0;
        for (Result<Item> result : objects) {
            try {
                Item item = result.get();
                String objectName = item.objectName();
                if (!objectName.endsWith(".json")) {
                    continue;
                }

                CustomerEvent event = readEvent(objectName);
                producerService.publish(event);
                moveToProcessed(objectName);
                LOGGER.info("Spark micro-batch: ingested {} -> Kafka", objectName);
                processed++;
            } catch (Exception e) {
                LOGGER.error("Failed to process MinIO object", e);
            }
        }

        if (processed > 0) {
            LOGGER.info("Spark micro-batch complete: {} event(s) sent to Kafka", processed);
        }
    }

    private CustomerEvent readEvent(String objectName) throws Exception {
        try (InputStream stream = minioClient.getObject(
                GetObjectArgs.builder().bucket(bucket).object(objectName).build())) {
            String json = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, CustomerEvent.class);
        }
    }

    private void moveToProcessed(String objectName) throws Exception {
        String fileName = objectName.substring(PENDING_PREFIX.length());
        String destObjectName = PROCESSED_PREFIX + fileName;

        minioClient.copyObject(
                CopyObjectArgs.builder()
                        .bucket(bucket)
                        .object(destObjectName)
                        .source(CopySource.builder().bucket(bucket).object(objectName).build())
                        .build());

        minioClient.removeObject(
                RemoveObjectArgs.builder().bucket(bucket).object(objectName).build());
    }
}
