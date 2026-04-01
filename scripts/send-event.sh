#!/usr/bin/env sh

curl -X POST http://localhost:8080/api/events \
  -H 'Content-Type: application/json' \
  -d '{
    "eventId": "evt-1001",
    "customerId": "cust-77",
    "fullName": "Ada Lovelace",
    "ssn": "123-45-6789",
    "action": "ACCOUNT_OPENED"
  }'
