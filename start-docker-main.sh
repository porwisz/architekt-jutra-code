#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# --- Docker Compose ---
echo "Ensuring docker-compose services are up..."
if docker compose ps --status running 2>/dev/null | grep -q postgres; then
  echo "  Services already running."
else
  docker compose up -d --wait
  echo "  Postgres, LiteLLM, and Langfuse started."
fi

# --- Cleanup on exit ---
cleanup() {
  echo ""
  echo "Shutting down..."
  kill 0 2>/dev/null
  wait 2>/dev/null
}
trap cleanup EXIT INT TERM

# --- Host app (Spring Boot) ---
echo "Starting host app..."
./mvnw -q spring-boot:run &

echo "All services starting. Press Ctrl+C to stop everything."
wait
