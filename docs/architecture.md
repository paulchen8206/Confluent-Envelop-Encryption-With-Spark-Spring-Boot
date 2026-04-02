# <span style="color: #3498DB">Project Architecture</span>

## <span style="color: #E74C3C">Overview</span>
This project is a local end-to-end event pipeline demonstrating:
- Event generation to MinIO
- Spark-style ingestion from MinIO to Kafka
- Kafka + Schema Registry integration
- Consumer processing and sink to MinIO
- DEK bootstrap topic and local KMS simulation

The stack runs with Docker Compose and includes observability via Kafka UI.

## <span style="color: #E67E22">Modules</span>
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

## <span style="color: #2ECC71">Runtime Components (Docker)</span>
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

## <span style="color: #9B59B6">Data Flow</span>
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

## <span style="color: #C0392B">Security and Key Management</span>
- LocalStack simulates AWS KMS for local development.
- KEK alias is created during startup (`alias/customer-pii-kek`).
- DEK registry topic: `_dek_registry_keys`.
- Producer currently performs field encryption/decryption in application services (AES/GCM) for the `ssn` field.
- Shared encryption secret is configured through environment variables (`APP_FIELD_ENCRYPTION_SECRET`) and must match between producer and consumer.

## <span style="color: #1ABC9C">Topic and Schema</span>
- Business topic: `secure-customer-events`
- Internal topic: `_dek_registry_keys`
- Schema subject: `secure-customer-events-value`
- Schema Registry endpoint: `http://schema-registry:8081`

## <span style="color: #F1C40F">Operational Endpoints</span>
- Producer API: `http://localhost:8080/api/events`
- Kafka UI: `http://localhost:8085`
- Kafka Connect: `http://localhost:8083`
- Schema Registry: `http://localhost:8081`
- MinIO API/Console: `http://localhost:9000` / `http://localhost:9001`
- LocalStack: `http://localhost:4566`

## <span style="color: #2980B9">Processing Semantics</span>
- Event generation and ingestion are scheduled micro-batches.
- MinIO ingester uses file move (`pending/` -> `processed/`) to avoid duplicate processing.
- At-least-once behavior can occur during failures between Kafka publish and move operation.

## <span style="color: #884EA0">Extensibility Notes</span>
- Replace scheduled ingester with real Spark Structured Streaming if needed.
- Replace LocalStack KMS with managed AWS KMS in non-local environments.
- Add dead-letter handling for malformed input files.
- Add idempotency keys or deduplication for stronger exactly-once behavior.

## <span style="color: #D35400">Envelope Encryption</span>

Envelope encryption is a security pattern that involves encrypting data with a unique Data Encryption Key (DEK), and then encrypting (or "wrapping") that DEK using a master Key Encryption Key (KEK) managed in a KMS. This approach allows secure storage of encrypted data alongside its encrypted key, improving performance and enabling easy key rotation without re-encrypting large data payloads.

### <span style="color: #1E8BC3">Key Components and Flow</span>
- **Plaintext Data**: The raw data to be protected.
- **DEK (Data Encryption Key)**: A symmetric key used to encrypt the actual data.
- **KEK (Key Encryption Key)**: A master key (stored in a KMS/HSM) that encrypts the DEK.
- **Encrypted Data**: Data encrypted by the DEK.
- **Wrapped DEK**: The DEK encrypted by the KEK.

### <span style="color: #27AE60">The Envelope Encryption Process</span>
1. **Generate**: The system requests a new DEK from the Key Management Service (KMS). The KMS returns both a plaintext and an encrypted version of the DEK.
2. **Encrypt**: The application uses the plaintext DEK to encrypt the data.
3. **Secure & Store**: The plaintext DEK is discarded from memory. The encrypted data and the wrapped (encrypted) DEK are stored together.
4. **Decrypt**: To decrypt, the application sends the wrapped DEK to the KMS, which uses the KEK to decrypt it and returns the plaintext DEK, which is then used to decrypt the data.

### <span style="color: #C0392B">Key Benefits</span>
- **Performance**: Large data volumes are encrypted with fast symmetric keys (DEK), while only small keys are sent to the KMS.
- **Security**: The KEK never leaves the KMS or hardware security module.
- **Key Rotation**: You can rotate the KEK without re-encrypting the data itself; you only need to decrypt and re-encrypt the DEK (re-wrapping).

This pattern is widely used in cloud environments, including AWS KMS, Google Cloud KMS, and Azure Key Vault.
