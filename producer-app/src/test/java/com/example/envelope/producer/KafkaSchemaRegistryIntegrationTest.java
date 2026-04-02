package com.example.envelope.producer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.envelope.model.CustomerEvent;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@Tag("integration")
class KafkaSchemaRegistryIntegrationTest {

    private static final String TOPIC = "secure-customer-events";
    private static final String SUBJECT = TOPIC + "-value";

    private static final Network NETWORK = Network.newNetwork();

    @Container
        static final KafkaContainer KAFKA =
              new KafkaContainer(
                  DockerImageName.parse("confluentinc/cp-kafka:7.7.1")
                    .asCompatibleSubstituteFor("apache/kafka"))
                    .withNetwork(NETWORK)
                    .withNetworkAliases("kafka")
                    .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true");

    @Container
    static final GenericContainer<?> SCHEMA_REGISTRY =
            new GenericContainer<>(DockerImageName.parse("confluentinc/cp-schema-registry:7.7.1"))
                    .withNetwork(NETWORK)
                    .withExposedPorts(8081)
                    .withEnv("SCHEMA_REGISTRY_HOST_NAME", "schema-registry")
                    .withEnv("SCHEMA_REGISTRY_LISTENERS", "http://0.0.0.0:8081")
                  .withEnv("SCHEMA_REGISTRY_KAFKASTORE_BOOTSTRAP_SERVERS", "PLAINTEXT://kafka:9092")
                    .withEnv("SCHEMA_REGISTRY_DEK_REGISTRY_TOPIC", "_dek_registry_keys")
                    .withEnv("SCHEMA_REGISTRY_KAFKASTORE_TOPIC_REPLICATION_FACTOR", "1")
                    .waitingFor(Wait.forHttp("/subjects").forStatusCode(200));

    @Test
    void registersSchemaAndRoundTripsEncryptedEvent() throws Exception {
        String schemaRegistryUrl =
                "http://" + SCHEMA_REGISTRY.getHost() + ":" + SCHEMA_REGISTRY.getMappedPort(8081);

        String registerResult = registerSchema(schemaRegistryUrl);
        assertTrue(registerResult.contains("\"id\""), "Schema registration should return an id");

        CustomerEvent sent = new CustomerEvent();
        sent.setEventId("evt-" + UUID.randomUUID());
        sent.setCustomerId("cust-77");
        sent.setFullName("Ada Lovelace");
        sent.setSsn("123-45-6789");
        sent.setAction("ACCOUNT_OPENED");

        produce(schemaRegistryUrl, sent);
        CustomerEvent received = consumeOne(schemaRegistryUrl);

        assertEquals(sent.getEventId(), received.getEventId());
        assertEquals(sent.getCustomerId(), received.getCustomerId());
        assertEquals(sent.getFullName(), received.getFullName());
        assertEquals(sent.getSsn(), received.getSsn());
        assertEquals(sent.getAction(), received.getAction());
    }

    private String registerSchema(String schemaRegistryUrl) throws IOException, InterruptedException {
        String payload = """
                {
                  "schemaType": "JSON",
                  "schema": "{\\\"$schema\\\":\\\"https://json-schema.org/draft/2020-12/schema\\\",\\\"title\\\":\\\"CustomerEvent\\\",\\\"type\\\":\\\"object\\\",\\\"properties\\\":{\\\"eventId\\\":{\\\"type\\\":\\\"string\\\"},\\\"customerId\\\":{\\\"type\\\":\\\"string\\\"},\\\"fullName\\\":{\\\"type\\\":\\\"string\\\"},\\\"ssn\\\":{\\\"type\\\":\\\"string\\\",\\\"confluent:tags\\\":[\\\"PII\\\"]},\\\"action\\\":{\\\"type\\\":\\\"string\\\"}},\\\"required\\\":[\\\"eventId\\\",\\\"customerId\\\",\\\"fullName\\\",\\\"ssn\\\",\\\"action\\\"]}",
                  "ruleSet": {
                    "domainRules": [
                      {
                        "name": "encrypt-pii",
                        "kind": "TRANSFORM",
                        "mode": "WRITEREAD",
                        "type": "ENCRYPT",
                        "tags": ["PII"],
                        "params": {
                          "encrypt.kek.name": "customer-pii-kek",
                          "encrypt.kms.type": "local-kms",
                          "encrypt.kms.key.id": "local-demo-master-key"
                        },
                        "onFailure": "ERROR,NONE"
                      }
                    ]
                  }
                }
                """;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(schemaRegistryUrl + "/subjects/" + SUBJECT + "/versions"))
                .header("Content-Type", "application/vnd.schemaregistry.v1+json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 200 && response.statusCode() < 300, "Schema registration failed: " + response.body());
        return response.body();
    }

    private void produce(String schemaRegistryUrl, CustomerEvent event) {
        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", KAFKA.getBootstrapServers());
        producerProps.put("key.serializer", StringSerializer.class.getName());
        producerProps.put("value.serializer", io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer.class.getName());
        producerProps.put("schema.registry.url", schemaRegistryUrl);
        producerProps.put("auto.register.schemas", "false");
        producerProps.put("use.latest.version", "true");
        producerProps.put("rule.executors._default_.class", "io.confluent.kafka.schemaregistry.encryption.FieldEncryptionExecutor");
        producerProps.put("rule.executors._default_.param.kek.name", "customer-pii-kek");
        producerProps.put("rule.executors._default_.param.kms.type", "local-kms");
        producerProps.put("rule.executors._default_.param.kms.key.id", "local-demo-master-key");
        producerProps.put("rule.executors._default_.param.kms.key.secret", "local-master-key-secret");

        try (KafkaProducer<String, CustomerEvent> producer = new KafkaProducer<>(producerProps)) {
            producer.send(new ProducerRecord<>(TOPIC, event.getCustomerId(), event));
            producer.flush();
        }
    }

    private CustomerEvent consumeOne(String schemaRegistryUrl) {
        Properties consumerProps = new Properties();
        consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "it-group");
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        consumerProps.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer.class.getName());
        consumerProps.put("schema.registry.url", schemaRegistryUrl);
        consumerProps.put("json.value.type", CustomerEvent.class.getName());
        consumerProps.put("rule.executors._default_.class", "io.confluent.kafka.schemaregistry.encryption.FieldEncryptionExecutor");
        consumerProps.put("rule.executors._default_.param.kek.name", "customer-pii-kek");
        consumerProps.put("rule.executors._default_.param.kms.type", "local-kms");
        consumerProps.put("rule.executors._default_.param.kms.key.id", "local-demo-master-key");
        consumerProps.put("rule.executors._default_.param.kms.key.secret", "local-master-key-secret");

        try (KafkaConsumer<String, CustomerEvent> consumer = new KafkaConsumer<>(consumerProps)) {
            consumer.subscribe(java.util.List.of(TOPIC));
            long timeoutAt = System.currentTimeMillis() + Duration.ofSeconds(30).toMillis();
            while (System.currentTimeMillis() < timeoutAt) {
                ConsumerRecords<String, CustomerEvent> records = consumer.poll(Duration.ofSeconds(1));
                if (!records.isEmpty()) {
                    return records.iterator().next().value();
                }
            }
        }

        throw new IllegalStateException("No event consumed within timeout");
    }
}
