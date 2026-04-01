# Runbook

## Purpose
This runbook documents day-to-day operations for the local event pipeline stack:
- MinIO inbox and sink buckets
- Event generator
- Producer ingestion and Kafka publish
- Consumer processing
- LocalStack KMS alias and DEK bootstrap topic
- Kafka UI and Schema Registry checks

All commands assume execution from project root:

```bash
cd /Users/pchen/mygithub/Confluent-Envelop-Encryption-With-Spring
```

## Key Endpoints
- Producer API: `http://localhost:8080/api/events`
- Schema Registry: `http://localhost:8081`
- Kafka Connect: `http://localhost:8083`
- Kafka UI: `http://localhost:8085`
- MinIO API: `http://localhost:9000`
- MinIO Console: `http://localhost:9001`
- LocalStack: `http://localhost:4566`

## 1. Start and Stop

### Start full stack
```bash
docker compose -f docker/docker-compose.yml up -d --build
```

### Check service status
```bash
docker compose -f docker/docker-compose.yml ps
```

### Stop without deleting volumes
```bash
docker compose -f docker/docker-compose.yml down
```

### Clean reboot (delete containers, network, volumes)
```bash
docker compose -f docker/docker-compose.yml down -v
docker compose -f docker/docker-compose.yml up -d --build
```

## 2. Health Verification

### Application and platform health
```bash
curl -s http://localhost:8080/actuator/health
curl -s http://localhost:8085/actuator/health
curl -s http://localhost:8083/connectors
```

Expected:
- Producer health is `UP`
- Kafka UI health is `UP`
- Kafka Connect responds (empty list is valid if no connectors)

### Core service logs
```bash
docker compose -f docker/docker-compose.yml logs producer --tail=80
docker compose -f docker/docker-compose.yml logs consumer --tail=80
docker compose -f docker/docker-compose.yml logs event-generator --tail=80
```

## 3. KMS and KEK Operations

### Verify KMS alias exists
```bash
docker compose -f docker/docker-compose.yml exec -T localstack awslocal kms list-aliases
```

Expected alias:
- `alias/customer-pii-kek`

### Create alias if missing
```bash
docker compose -f docker/docker-compose.yml exec -T localstack sh -lc '
KEY_ID=$(awslocal kms create-key --description "customer pii kek" --query "KeyMetadata.KeyId" --output text)
awslocal kms create-alias --alias-name alias/customer-pii-kek --target-key-id "$KEY_ID"
echo "ALIAS_CREATED_FOR=$KEY_ID"
'
```

## 4. Kafka and Schema Operations

### Verify required topics
```bash
docker compose -f docker/docker-compose.yml exec -T kafka kafka-topics --bootstrap-server kafka:29092 --list
```

Expected topics include:
- `secure-customer-events`
- `_schemas`
- `_dek_registry_keys`

### Verify DEK registry topic offset
```bash
docker compose -f docker/docker-compose.yml exec -T kafka kafka-get-offsets --bootstrap-server kafka:29092 --topic _dek_registry_keys
```

### Verify schema registration
```bash
curl -s http://localhost:8081/subjects
curl -s http://localhost:8081/subjects/secure-customer-events-value/versions/latest
```

## 5. Data Flow Validation

### Confirm event generator writes to MinIO inbox
```bash
docker compose -f docker/docker-compose.yml logs event-generator --tail=40
```

Expected log pattern:
- `Generated event ... -> minio://event-inbox/pending/...`

### Confirm producer ingests pending files and publishes to Kafka
```bash
docker compose -f docker/docker-compose.yml logs producer --tail=80
```

Expected log pattern:
- `Spark micro-batch: ingested pending/... -> Kafka`

### Confirm consumer receives and processes events
```bash
docker compose -f docker/docker-compose.yml logs consumer --tail=80
```

Expected log pattern:
- `Consumed decrypted event: ...`

## 6. Encryption Validation

### Validate latest message contains encrypted `ssn`
```bash
docker compose -f docker/docker-compose.yml exec -T kafka sh -lc "
kafka-console-consumer --bootstrap-server kafka:29092 --topic secure-customer-events --partition 0 --offset latest --max-messages 1 --property print.value=true --timeout-ms 20000 > /tmp/latest.bin 2>/tmp/latest.err || true
grep -aoE '[0-9]{3}-[0-9]{2}-[0-9]{4}|enc:v1:[A-Za-z0-9+/=]+:[A-Za-z0-9+/=]+' /tmp/latest.bin || true
cat /tmp/latest.err | tail -n 5
"
```

Expected for new messages:
- `enc:v1:...` present
- plaintext SSN pattern absent

Note:
- Older historical messages may still contain plaintext if produced before encryption was enabled.

## 7. Manual Publish Test (HTTP)

```bash
curl -X POST http://localhost:8080/api/events \
	-H 'Content-Type: application/json' \
	-d '{
		"eventId": "evt-manual-1001",
		"customerId": "cust-77",
		"fullName": "Ada Lovelace",
		"ssn": "123-45-6789",
		"action": "ACCOUNT_OPENED"
	}'
```

Expected response:
- `{"status":"queued",...}`

## 8. MinIO Validation

### Verify consumer sink bucket objects
```bash
docker exec minio sh -lc 'ls -R /data/secure-customer-events'
```

### Verify inbox processing folders
```bash
docker exec minio sh -lc 'ls -R /data/event-inbox'
```

Expected:
- `pending/` should stay near-empty under normal operation
- `processed/` should accumulate ingested files

## 9. Kafka UI Operations

Open:
- `http://localhost:8085`

Checks:
- Cluster `local` is online
- Topic `secure-customer-events` receives messages
- Internal topic `_dek_registry_keys` visible when showing internal topics
- Consumer group `secure-consumer-group` present

## 10. Common Incidents and Recovery

### Consumer not running
```bash
docker compose -f docker/docker-compose.yml logs consumer --tail=120
docker compose -f docker/docker-compose.yml up -d --build consumer
```

### Producer not publishing
```bash
docker compose -f docker/docker-compose.yml logs producer --tail=120
docker compose -f docker/docker-compose.yml up -d --build producer
```

### KMS alias missing after reboot
Run the alias creation command in section 3, then restart producer and consumer:
```bash
docker compose -f docker/docker-compose.yml up -d --build producer consumer
```

### Stuck pipeline or old state contamination
```bash
docker compose -f docker/docker-compose.yml down -v
docker compose -f docker/docker-compose.yml up -d --build
```

## 11. Quick Smoke Checklist
1. `docker compose ... ps` shows all required services up
2. `awslocal kms list-aliases` includes `alias/customer-pii-kek`
3. Topic list includes `_dek_registry_keys` and `secure-customer-events`
4. Producer logs show ingestion from `event-inbox/pending/`
5. Consumer logs show decrypted events
6. Latest Kafka payload check shows `enc:v1:...` marker
