# Project Architecture

## Overview
This project is a local end-to-end event pipeline demonstrating:
- Event generation to MinIO
- Spark-style ingestion from MinIO to Kafka
- Kafka + Schema Registry integration
- Consumer processing and sink to MinIO
- DEK bootstrap topic and local KMS simulation

The stack runs with Docker Compose and includes observability via Kafka UI.

## Modules
- `common`
  - Shared domain model (`CustomerEvent`)
- `event-generator`
  - Scheduled event producer that writes JSON files to MinIO inbox bucket (`event-inbox/pending/`)
- `producer-app`
  - Ingests `CustomerEvent` JSON files from MinIO (micro-batch polling)
  - Publishes events to Kafka topic `secure-customer-events`
  - Publishes DEK bootstrap record to `_dek_registry_keys` topic
  - Encrypts sensitive field (`ssn`) before Kafka publish
- `consumer-app`
  - Consumes events from Kafka topic `secure-customer-events`
  - Decrypts `ssn`
  - Sinks consumed events to MinIO bucket `secure-customer-events`

## Runtime Components (Docker)
- Zookeeper
- Kafka broker
- Kafka init job (`kafka-init`)
  - Ensures `_dek_registry_keys` exists
- Schema Registry
- Schema init job (`schema-init`)
  - Registers JSON schema and rule set
- Kafka Connect
- Kafka UI
- MinIO + MinIO init job (`minio-init`)
  - Ensures buckets exist (`event-inbox`, `secure-customer-events`)
- LocalStack + LocalStack init job (`localstack-init`)
  - Simulates AWS KMS and creates key alias
- `event-generator` app container
- `spring-producer` app container
- `spring-consumer` app container

## Data Flow
1. `event-generator` creates `CustomerEvent` objects and writes JSON files to:
   - `minio://event-inbox/pending/<eventId>.json`
2. `spring-producer` micro-batch ingester polls `event-inbox/pending/`.
3. For each file:
   - Reads JSON into `CustomerEvent`
   - Ensures DEK bootstrap record exists in `_dek_registry_keys`
   - Encrypts `ssn` at application layer
   - Publishes event to Kafka topic `secure-customer-events`
   - Moves source file to `event-inbox/processed/`
4. `spring-consumer` reads from `secure-customer-events`.
5. Consumer decrypts `ssn` and sinks full event JSON to:
   - `minio://secure-customer-events/<eventId>.json`

## Security and Key Management
- LocalStack simulates AWS KMS for local development.
- KEK alias is created during startup (`alias/customer-pii-kek`).
- DEK registry topic: `_dek_registry_keys`.
- Producer currently performs field encryption/decryption in application services (AES/GCM) for the `ssn` field.
- Shared encryption secret is configured through environment variables (`APP_FIELD_ENCRYPTION_SECRET`) and must match between producer and consumer.

## Topic and Schema
- Business topic: `secure-customer-events`
- Internal topic: `_dek_registry_keys`
- Schema subject: `secure-customer-events-value`
- Schema Registry endpoint: `http://schema-registry:8081`

## Operational Endpoints
- Producer API: `http://localhost:8080/api/events`
- Kafka UI: `http://localhost:8085`
- Kafka Connect: `http://localhost:8083`
- Schema Registry: `http://localhost:8081`
- MinIO API/Console: `http://localhost:9000` / `http://localhost:9001`
- LocalStack: `http://localhost:4566`

## Processing Semantics
- Event generation and ingestion are scheduled micro-batches.
- MinIO ingester uses file move (`pending/` -> `processed/`) to avoid duplicate processing.
- At-least-once behavior can occur during failures between Kafka publish and move operation.

## Extensibility Notes
- Replace scheduled ingester with real Spark Structured Streaming if needed.
- Replace LocalStack KMS with managed AWS KMS in non-local environments.
- Add dead-letter handling for malformed input files.
- Add idempotency keys or deduplication for stronger exactly-once behavior.
