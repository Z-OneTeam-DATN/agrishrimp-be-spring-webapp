#!/usr/bin/env bash
# =============================================================
# rollback.sh — Rollback một service về commit SHA cụ thể
#
# Cách dùng:
#   bash rollback.sh <service> <sha>
#
# Ví dụ:
#   bash rollback.sh webapp sha-abc1234
#   bash rollback.sh api sha-def5678
#   bash rollback.sh ai-visual-search sha-ghi9012
#
# Image naming (GHCR):
#   webapp           → ghcr.io/z-oneteam-datn/agrishrimp-fe-react
#   api              → ghcr.io/z-oneteam-datn/agrishrimp-be-spring-webapp
#   ai-visual-search → ghcr.io/z-oneteam-datn/agrishrimp-ai-visual-search
# =============================================================
set -euo pipefail

DEPLOY_DIR="/opt/agrishrimp"
REGISTRY="ghcr.io/z-oneteam-datn"

SERVICE="${1:-}"
SHA_TAG="${2:-}"

if [ -z "$SERVICE" ] || [ -z "$SHA_TAG" ]; then
  echo "Usage: $0 <service> <sha-tag>"
  echo ""
  echo "Services: webapp | api | ai-visual-search"
  echo "SHA tag format: sha-abc1234 (short SHA from GitHub Actions)"
  echo ""
  echo "Find available tags:"
  echo "  docker images | grep agrishrimp"
  exit 1
fi

# Map service name → image name
case "$SERVICE" in
  webapp)          IMAGE="$REGISTRY/agrishrimp-fe-react" ;;
  api)             IMAGE="$REGISTRY/agrishrimp-be-spring-webapp" ;;
  ai-visual-search) IMAGE="$REGISTRY/agrishrimp-ai-visual-search" ;;
  *)
    echo "❌ Unknown service: $SERVICE"
    echo "Valid services: webapp, api, ai-visual-search"
    exit 1
    ;;
esac

echo "══════════════════════════════════════"
echo "  ROLLBACK: $SERVICE → $SHA_TAG"
echo "  Image: $IMAGE:$SHA_TAG"
echo "══════════════════════════════════════"
read -r -p "Confirm rollback? [y/N] " confirm
if [[ ! "$confirm" =~ ^[Yy]$ ]]; then
  echo "Aborted."
  exit 0
fi

cd "$DEPLOY_DIR"

# Pull image với SHA tag cụ thể
echo "Pulling $IMAGE:$SHA_TAG ..."
docker pull "$IMAGE:$SHA_TAG"

# Tag thành latest để docker-compose dùng
docker tag "$IMAGE:$SHA_TAG" "$IMAGE:latest"

# Restart service
echo "Restarting $SERVICE..."
docker compose -f docker-compose.prod.yml up -d --no-deps --pull never "$SERVICE"

# Health check
echo "Waiting for $SERVICE to start..."
sleep 15

case "$SERVICE" in
  webapp)
    for i in $(seq 1 5); do
      code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:3004 || echo "000")
      [ "$code" = "200" ] || [ "$code" = "301" ] || [ "$code" = "302" ] && break
      echo "  Retry $i — HTTP $code"
      sleep 5
    done
    ;;
  api)
    for i in $(seq 1 6); do
      status=$(curl -sf http://127.0.0.1:8004/actuator/health 2>/dev/null | grep -o '"UP"' || echo "DOWN")
      [ "$status" = '"UP"' ] && break
      echo "  Retry $i — $status"
      sleep 10
    done
    ;;
  ai-visual-search)
    for i in $(seq 1 6); do
      code=$(curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:5001/health || echo "000")
      [ "$code" = "200" ] && break
      echo "  Retry $i — HTTP $code"
      sleep 15
    done
    ;;
esac

echo ""
echo "✅ Rollback complete: $SERVICE → $SHA_TAG"
echo "   Run healthcheck to verify: bash $DEPLOY_DIR/scripts/healthcheck.sh"
