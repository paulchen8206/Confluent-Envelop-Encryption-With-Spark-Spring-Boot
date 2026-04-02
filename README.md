# <span style="color: #E74C3C">Confluent Envelope Encryption With Spring Boot</span>

Multi-module Spring Boot project with:
- `common`: shared event model
- `producer-app`: HTTP producer exposing `POST /api/events`
- `consumer-app`: Kafka consumer using `@KafkaListener`
- `producer-app` publishes to Kafka topic only
- `consumer-app` sinks each consumed event to MinIO object storage
- Confluent Kafka + Schema Registry + DEK registry topic
- JSON Schema pre-registration with field-level encryption rule for `ssn`
- LocalStack-backed AWS KMS for KEK management in local container runs

## <span style="color: #E67E22">Modules</span>
- `common`
- `producer-app`
- `consumer-app`

## <span style="color: #F1C40F">Build</span>

From project root:

```bash
./mvnw clean package
```

## <span style="color: #2ECC71">Integration Test (Testcontainers)</span>

Producer module includes a Kafka + Schema Registry integration test that also verifies schema registration.

Run it with:

```bash
./mvnw -pl producer-app -am -Dtest=KafkaSchemaRegistryIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

Note:
- Docker must be running for this test to execute.
- If Docker is unavailable, the test is skipped automatically.

## <span style="color: #3498DB">Run with Docker Compose</span>

From project root:

```bash
cd docker
docker compose up --build
```

Then publish a sample event:

```bash
sh ../scripts/send-event.sh
```

Producer API endpoint:
- `http://localhost:8080/api/events`

## <span style="color: #9B59B6">Run in Kubernetes</span>

1. Build module images locally:

```bash
docker build -f docker/Dockerfile.producer -t producer-app:latest .
docker build -f docker/Dockerfile.consumer -t consumer-app:latest .
```

2. Load images into your cluster runtime if needed (kind/minikube specific).

3. Apply manifests:

```bash
kubectl apply -f src/main/resources/k8s/confluent-spring-stack.yaml
```

4. Port-forward producer service:

```bash
kubectl -n confluent-envelope port-forward svc/spring-producer 8080:8080
```

5. Inspect consumer logs:

```bash
kubectl -n confluent-envelope logs deploy/spring-consumer -f
```

## <span style="color: #1ABC9C">Notes</span>
- Docker Compose includes:
	- MinIO on `http://localhost:9000` (console `http://localhost:9001`)
	- LocalStack KMS on `http://localhost:4566`
	- Kafka Connect REST on `http://localhost:8083`
	- Kafka UI on `http://localhost:8085`
- Compose initializes `alias/customer-pii-kek` in LocalStack KMS and creates the MinIO bucket `secure-customer-events`.
- Compose also bootstraps Kafka topic `_dek_registry_keys` so DEK registry storage is visible immediately in Kafka UI.
- Consumer writes each consumed event payload to MinIO as `<eventId>.json`.
- Schema registration runs from `scripts/register-schema.sh` in Compose and from a Kubernetes Job.

Quick DEK topic smoke check:

```bash
sh scripts/check-dek-topic.sh
```

Kafka UI is pre-wired to:
- Kafka broker (`kafka:29092`)
- Schema Registry (`http://schema-registry:8081`)
- Kafka Connect (`http://kafka-connect:8083`)
