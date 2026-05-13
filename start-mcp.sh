#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

# --- Cleanup on exit ---
cleanup() {
  echo ""
  echo "Shutting down..."
  kill 0 2>/dev/null
  wait 2>/dev/null
}
trap cleanup EXIT INT TERM

# --- MCP app (Spring Boot) ---
echo "Starting MCP app..."
cd mcp-server
./mvnw -q spring-boot:run &


echo ""
echo "All services starting. Press Ctrl+C to stop everything."
wait
