#!/usr/bin/env bash

set -euo pipefail

BASE_ENV_FILE="${1:-.env}"
OVERRIDE_ENV_FILE="${2:-.env.local}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BASE_ENV_PATH="$SCRIPT_DIR/$BASE_ENV_FILE"
OVERRIDE_ENV_PATH="$SCRIPT_DIR/$OVERRIDE_ENV_FILE"

if [[ ! -f "$OVERRIDE_ENV_PATH" ]]; then
  echo "Missing $OVERRIDE_ENV_FILE."
  echo "Copy .env.local.example to $OVERRIDE_ENV_FILE and update the DB host/user/password first."
  exit 1
fi

set -a
if [[ -f "$BASE_ENV_PATH" ]]; then
  # shellcheck disable=SC1090
  source "$BASE_ENV_PATH"
fi
# shellcheck disable=SC1090
source "$OVERRIDE_ENV_PATH"
set +a

if [[ "${SPRING_PROFILES_ACTIVE:-}" == "" ]]; then
  export SPRING_PROFILES_ACTIVE="dev"
fi

if [[ "${SPRING_DATASOURCE_URL:-}" == *"YOUR_DB_HOST"* || "${SPRING_DATASOURCE_USERNAME:-}" == "YOUR_DB_USER" ]]; then
  echo "The override file still contains placeholder remote DB values."
  echo "Update $OVERRIDE_ENV_FILE before starting the backend."
  exit 1
fi

echo "Starting AgriShrimp backend with local app config + remote DB override..."
echo "Profile: ${SPRING_PROFILES_ACTIVE}"
echo "API URL: ${APP_SERVER_URL:-}"
echo "Datasource: ${SPRING_DATASOURCE_URL:-}"
echo "Redis host: ${SPRING_DATA_REDIS_HOST:-}"

cd "$SCRIPT_DIR"
./mvnw spring-boot:run "-Dspring-boot.run.profiles=${SPRING_PROFILES_ACTIVE}"
