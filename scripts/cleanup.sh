#!/usr/bin/env bash
set -euo pipefail

# Navigate to the project root directory, assuming this script is in the 'scripts' folder.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.."; pwd)"
cd "$ROOT"

# Load environment variables for compose
set -a; source ./.env-flyingcloud; set +a

# Define compose command shortcut
COMPOSE="docker compose --env-file .env-flyingcloud -f docker-compose.yml"

echo "[1/7] Stopping all services to ensure a clean state..."
# Stop everything first.
${COMPOSE} stop

echo "[2/7] Removing the app and mysql containers..."
# We need to remove them to restart them cleanly. Redis can often be left alone.
docker rm -f "${APP_CTN:-flyingcloud-4156}" 2>/dev/null || true
docker rm -f "${MYSQL_CTN:-mysql}"
 2>/dev/null || true

echo "[3/7] Completely deleting dirty MySQL data..."
sudo rm -rf "${MYSQL_DATA_DIR:-./ops/mysql/data}"
mkdir -p "${MYSQL_DATA_DIR:-./ops/mysql/data}" "${MYSQL_CONF_DIR:-./ops/mysql/conf}" "${MYSQL_INIT_DIR:-./ops/mysql/init}"

sudo chown -R 999:999 "${MYSQL_DATA_DIR:-./ops/mysql/data}" "${MYSQL_CONF_DIR:-./ops/mysql/conf}" "${MYSQL_INIT_DIR:-./ops/mysql/init}" 2>/dev/null || true

echo "[4/7] Removing temporary init scripts..."
rm -f ./ops/mysql/init/01_appuser.sql 2>/dev/null || true

echo "[5/7] Starting only MySQL to allow full initialization..."
# We use --no-deps to ensure ONLY mysql is started here.
${COMPOSE} up -d --no-deps mysql

echo "[6/7] Waiting for MySQL to become healthy..."
for i in {1..60}; do
  s=$(docker inspect -f '{{.State.Health.Status}}' "${MYSQL_CTN:-mysql}" 2>/dev/null || echo "starting")
  if [[ "$s" == "healthy" ]]; then
    echo "MySQL is healthy ✅"
    break
  fi
  echo "waiting for mysql... ($i)"
  sleep 5
done

# Final check if MySQL actually became healthy
s=$(docker inspect -f '{{.State.Health.Status}}' "${MYSQL_CTN:-mysql}" 2>/dev/null)
if [[ "$s" != "healthy" ]]; then
  echo "❌ MySQL failed to start. Please check 'docker logs mysql'."
  exit 1
fi

echo "[7/7] Building app image and starting remaining services (redis + app)..."
# The crucial --build flag for the app.
# --no-deps prevents it from trying to restart mysql.
${COMPOSE} up -d --build --no-deps redis app

echo "Done. Check status:"
${COMPOSE} ps