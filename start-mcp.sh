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
# When running locally (no cloudflared), these defaults work out of the box.
# When exposing publicly via cloudflared, set these env vars before running:
export AJ_OAUTH_SERVER_URL=https://aj.cybernuta.eu
export AJ_MCP_BASE_URL=https://architekt-jutra-mcp.cybernuta.eu

echo "Starting MCP app..."
cd mcp-server
./mvnw -q spring-boot:run &


echo ""
echo "All services starting. Press Ctrl+C to stop everything."
wait
