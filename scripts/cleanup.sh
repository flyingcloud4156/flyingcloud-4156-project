#!/usr/bin/env bash
set -euo pipefail

cd /home/zh2701/code/flyingcloud-4156-project

set -a; source ./.env-flyingcloud; set +a

echo "[1/6] Stop apps depending on MySQL to avoid network occupation / waiting for health checks"
docker compose --env-file .env-flyingcloud -f docker-compose.yml stop app || true

echo "[2/6] Remove the MySQL container (ignore errors if it doesn’t exist)"
docker rm -f "${MYSQL_CTN:-mysql}" 2>/dev/null || true

echo "[3/6] Completely delete dirty MySQL data (you have explicitly said data can be deleted)"
rm -rf "${MYSQL_DATA_DIR:-./ops/mysql/data}"
mkdir -p "${MYSQL_DATA_DIR:-./ops/mysql/data}" "${MYSQL_CONF_DIR:-./ops/mysql/conf}" "${MYSQL_INIT_DIR:-./ops/mysql/init}"

sudo chown -R 999:999 "${MYSQL_DATA_DIR:-./ops/mysql/data}" "${MYSQL_CONF_DIR:-./ops/mysql/conf}" "${MYSQL_INIT_DIR:-./ops/mysql/init}" 2>/dev/null || true

echo "[4/6] If you don’t need the manual init script (01_appuser.sql), make sure it doesn’t exist"
rm -f ./ops/mysql/init/01_appuser.sql || true

echo "[5/6] Start only MySQL to allow full initialization (first startup will be slow, don’t interrupt)"
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d mysql

echo "[6/6] Wait for MySQL to become healthy (up to ~5 minutes, depending on your compose health check settings)"
for i in {1..60}; do
  s=$(docker inspect -f '{{.State.Health.Status}}' "${MYSQL_CTN:-mysql}" 2>/dev/null || echo starting)
  if [[ "$s" == "healthy" ]]; then echo "MySQL is healthy ✅"; break; fi
  echo "waiting mysql... ($i)"; sleep 5
done

echo "→ Now start/restart other services (redis + app)"
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d redis app

echo "Done. Check:"
docker compose --env-file .env-flyingcloud -f docker-compose.yml ps