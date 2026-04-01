#!/usr/bin/env sh
set -e

TOPIC="${1:-_dek_registry_keys}"

ROOT_DIR="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker/docker-compose.yml"

echo "Checking Kafka topic: $TOPIC"

TOPICS=$(docker compose -f "$COMPOSE_FILE" exec -T kafka kafka-topics --bootstrap-server kafka:29092 --list)

if printf '%s\n' "$TOPICS" | grep -qx "$TOPIC"; then
  echo "OK: topic '$TOPIC' exists"
  exit 0
fi

echo "ERROR: topic '$TOPIC' not found"
exit 1
