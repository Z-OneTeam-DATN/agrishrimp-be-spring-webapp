#!/usr/bin/env bash
# =============================================================
# healthcheck.sh — Kiểm tra sức khoẻ tất cả services
# Chạy: bash healthcheck.sh
# Dùng trong cron: */5 * * * * /home/bnhien40/agrishrimp/scripts/healthcheck.sh >> /var/log/agrishrimp-health.log 2>&1
# =============================================================
set -uo pipefail

TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
FAIL=0

check() {
  local name="$1"
  local cmd="$2"
  local expected="$3"

  result=$(eval "$cmd" 2>/dev/null || echo "ERROR")
  if echo "$result" | grep -q "$expected"; then
    echo "[$TIMESTAMP] ✅ $name — OK"
  else
    echo "[$TIMESTAMP] ❌ $name — FAILED (got: $result)"
    FAIL=1
  fi
}

echo "[$TIMESTAMP] ── Health Check Start ──────────────────"

# Frontend — HTTP 200 or redirect
check "webapp (port 3004)" \
  "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:3004" \
  "200\|301\|302"

# Backend — Spring Actuator
check "api (port 8004) — actuator" \
  "curl -sf http://127.0.0.1:8004/actuator/health" \
  '"status":"UP"'

# AI Service — /health endpoint
check "ai-visual-search (port 5001)" \
  "curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:5001/health" \
  "200"

# Docker containers running
for container in agrishrimp-webapp agrishrimp-api agrishrimp-ai-visual-search agrishrimp-db agrishrimp-redis; do
  status=$(docker inspect --format='{{.State.Status}}' "$container" 2>/dev/null || echo "missing")
  if [ "$status" = "running" ]; then
    echo "[$TIMESTAMP] ✅ container $container — running"
  else
    echo "[$TIMESTAMP] ❌ container $container — $status"
    FAIL=1
  fi
done

echo "[$TIMESTAMP] ── Health Check End ────────────────────"

if [ $FAIL -ne 0 ]; then
  echo "[$TIMESTAMP] ⚠️  One or more checks FAILED"
  exit 1
fi
exit 0
