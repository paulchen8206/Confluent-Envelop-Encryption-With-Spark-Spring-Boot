#!/usr/bin/env sh
set -e

SCHEMA_REGISTRY_URL="${SCHEMA_REGISTRY_URL:-http://schema-registry:8081}"
SUBJECT="${SUBJECT:-secure-customer-events-value}"
APP_KEK_NAME="${APP_KEK_NAME:-customer-pii-kek}"
APP_KMS_TYPE="${APP_KMS_TYPE:-aws-kms}"
APP_KMS_KEY_ID="${APP_KMS_KEY_ID:-alias/customer-pii-kek}"

echo "Waiting for Schema Registry at ${SCHEMA_REGISTRY_URL}"
until curl -s "${SCHEMA_REGISTRY_URL}/subjects" >/dev/null 2>&1; do
  sleep 2
done

PAYLOAD=$(cat <<JSON
{
  "schemaType": "JSON",
  "schema": "{\"\$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"title\":\"CustomerEvent\",\"type\":\"object\",\"properties\":{\"eventId\":{\"type\":\"string\"},\"customerId\":{\"type\":\"string\"},\"fullName\":{\"type\":\"string\"},\"ssn\":{\"type\":\"string\",\"confluent:tags\":[\"PII\"]},\"action\":{\"type\":\"string\"}},\"required\":[\"eventId\",\"customerId\",\"fullName\",\"ssn\",\"action\"]}",
  "ruleSet": {
    "domainRules": [
      {
        "name": "encrypt-pii",
        "kind": "TRANSFORM",
        "mode": "WRITEREAD",
        "type": "ENCRYPT",
        "tags": ["PII"],
        "params": {
          "encrypt.kek.name": "${APP_KEK_NAME}",
          "encrypt.kms.type": "${APP_KMS_TYPE}",
          "encrypt.kms.key.id": "${APP_KMS_KEY_ID}"
        },
        "onFailure": "ERROR,NONE"
      }
    ]
  }
}
JSON
)

curl -s -X POST \
  -H "Content-Type: application/vnd.schemaregistry.v1+json" \
  --data "$PAYLOAD" \
  "${SCHEMA_REGISTRY_URL}/subjects/${SUBJECT}/versions" | cat

echo "Schema registration completed for ${SUBJECT}"