#!/usr/bin/env bash
set -euo pipefail

cd /home/zh2701/code/flyingcloud-4156-project

# 读取 env（为了拿到目录/变量）
set -a; source ./.env-flyingcloud; set +a

echo "[1/6] 停掉依赖 MySQL 的 app，避免占用网络/等待健康检查"
docker compose --env-file .env-flyingcloud -f docker-compose.yml stop app || true

echo "[2/6] 删除 MySQL 容器（忽略不存在报错）"
docker rm -f "${MYSQL_CTN:-mysql}" 2>/dev/null || true

echo "[3/6] 彻底删除 MySQL 脏数据（你明确说数据可删）"
rm -rf "${MYSQL_DATA_DIR:-./ops/mysql/data}"
mkdir -p "${MYSQL_DATA_DIR:-./ops/mysql/data}" "${MYSQL_CONF_DIR:-./ops/mysql/conf}" "${MYSQL_INIT_DIR:-./ops/mysql/init}"
# 官方 mysql 镜像通常用 999:999
sudo chown -R 999:999 "${MYSQL_DATA_DIR:-./ops/mysql/data}" "${MYSQL_CONF_DIR:-./ops/mysql/conf}" "${MYSQL_INIT_DIR:-./ops/mysql/init}" 2>/dev/null || true

echo "[4/6] 如果你不需要 init 里的人为脚本（01_appuser.sql），就确保它不存在"
rm -f ./ops/mysql/init/01_appuser.sql || true

echo "[5/6] 只启动 MySQL，让它完成全新初始化（首启会很慢，别打断）"
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d mysql

echo "[6/6] 等待 MySQL 健康（最多 ~5 分钟，取决于你的 compose 健康检查设置）"
for i in {1..60}; do
  s=$(docker inspect -f '{{.State.Health.Status}}' "${MYSQL_CTN:-mysql}" 2>/dev/null || echo starting)
  if [[ "$s" == "healthy" ]]; then echo "MySQL is healthy ✅"; break; fi
  echo "waiting mysql... ($i)"; sleep 5
done

echo "→ 现在启动/重启其余服务（redis + app）"
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d redis app

echo "完成。检查："
docker compose --env-file .env-flyingcloud -f docker-compose.yml ps
