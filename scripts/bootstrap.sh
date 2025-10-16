#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.."; pwd)"
cd "$ROOT"

ENV_FILE=".env-flyingcloud"
COMPOSE="docker compose --env-file ${ENV_FILE} -f docker-compose.yml"

echo "[1/5] Load env: ${ENV_FILE}"
set -a; source "${ENV_FILE}"; set +a

echo "[2/5] Clean down (with volumes) ..."
${COMPOSE} down -v --remove-orphans || true

echo "[3/5] Prepare directories ..."
mkdir -p "${MYSQL_DATA_DIR}" "${MYSQL_CONF_DIR}" "${MYSQL_INIT_DIR}" "${REDIS_DATA_DIR}" "./ops/sql"
# 官方 mysql 镜像常用 uid/gid = 999:999；先尝试修正权限（若失败可忽略）
sudo chown -R 999:999 "${MYSQL_DATA_DIR}" "${MYSQL_CONF_DIR}" "${MYSQL_INIT_DIR}" "${REDIS_DATA_DIR}" 2>/dev/null || true

echo "[4/5] Build & up ..."
${COMPOSE} up -d --build

echo "[5/5] Waiting for healthchecks ..."
# 简单轮询健康状态
for s in "${MYSQL_CTN}" "${REDIS_CTN}" "${APP_CTN}"; do
  echo -n " - ${s}: "
  for i in {1..40}; do
    st=$(docker inspect -f '{{.State.Health.Status}}' "$s" 2>/dev/null || echo "starting")
    if [[ "$st" == "healthy" ]]; then echo "healthy"; break; fi
    sleep 2
  done
done

echo "Stack is up. Try: curl -i http://localhost:${SERVER_PORT}/hi || true"