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

# --- Plugins ---
for dir in plugins/*/; do
  if [ -f "$dir/package.json" ]; then
    name=$(basename "$dir")
    echo "Starting plugin: $name"
    (cd "$dir" && npm run dev) &
    sleep 15
  fi
done


echo ""
echo "All services starting. Press Ctrl+C to stop everything."
wait
