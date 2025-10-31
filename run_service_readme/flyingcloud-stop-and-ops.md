# FlyingCloud 4156 – Operations: Stop & Shutdown Guide (Docker Compose)

This document is a **terminal-only** guide for stopping and tearing down the FlyingCloud 4156 stack on a Linux host using Docker Compose. It is designed to be safe-by-default and clearly indicates when actions will wipe persistent data.

> Project root assumed: `/home/zh2701/code/flyingcloud-4156-project`  
> Compose uses `.env-flyingcloud` for environment variables.  
> Services: **app** (`flyingcloud-4156`), **mysql** (`mysql`), **redis** (`redis`).

---

## Table of Contents

- [Prerequisites](#prerequisites)
- [Quick Reference](#quick-reference)
- [Stop vs Down (What’s the Difference?)](#stop-vs-down-whats-the-difference)
- [Option A — Stop Containers (Keep Containers & Volumes)](#option-a--stop-containers-keep-containers--volumes)
- [Option B — Down (Remove Containers/Network, Keep Volumes)](#option-b--down-remove-containersnetwork-keep-volumes)
- [Option C — Down + Volumes (Remove Everything, **Wipe Data**)](#option-c--down--volumes-remove-everything-wipe-data)
- [Service-Specific Stop/Start](#service-specific-stopstart)
- [Restart & Rebuild Snippets](#restart--rebuild-snippets)
- [Health Checks & Verification](#health-checks--verification)
- [Logs & Troubleshooting](#logs--troubleshooting)
- [Data Safety Notes](#data-safety-notes)
- [One-Click Scripts](#one-click-scripts)
- [Appendix: Compose & Container Names](#appendix-compose--container-names)

---

## Prerequisites

1. Docker and Docker Compose installed.
2. You can run `docker` commands (or prefix with `sudo`).
3. You are in the project directory:
   ```bash
   cd /home/zh2701/code/flyingcloud-4156-project
   ```
4. The Compose file and env file exist:
   - `docker-compose.yml`
   - `.env-flyingcloud`

---

## Quick Reference

- **Stop everything (keep containers & volumes):**
  ```bash
  docker compose --env-file .env-flyingcloud -f docker-compose.yml stop
  ```

- **Down (remove containers/network; keep volumes):**
  ```bash
  docker compose --env-file .env-flyingcloud -f docker-compose.yml down --remove-orphans
  ```

- **Down + Volumes (wipe data):**
  ```bash
  docker compose --env-file .env-flyingcloud -f docker-compose.yml down -v --remove-orphans
  ```

- **Stop one service (e.g., app only):**
  ```bash
  docker compose --env-file .env-flyingcloud -f docker-compose.yml stop app
  ```

---

## Stop vs Down (What’s the Difference?)

- **stop**: Sends a stop signal to running containers. Containers and volumes remain on disk; you can restart quickly with `up -d`.
- **down**: Stops containers **and removes** the containers + network. If you pass `-v`, **named volumes are removed** too (data loss for MySQL/Redis).

Choose based on your intent:
- Need a quick pause? → **stop**
- Need a clean re-create but keep data? → **down --remove-orphans**
- Need a full reset including data? → **down -v --remove-orphans**

---

## Option A — Stop Containers (Keep Containers & Volumes)

> Safe. Preserves MySQL/Redis data and the container filesystem layers.

```bash
cd /home/zh2701/code/flyingcloud-4156-project
docker compose --env-file .env-flyingcloud -f docker-compose.yml stop
```

Restart later with:
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d
```

---

## Option B — Down (Remove Containers/Network, Keep Volumes)

> Safe for data. Removes containers and network, **keeps volumes** (MySQL/Redis data persists).

```bash
cd /home/zh2701/code/flyingcloud-4156-project
docker compose --env-file .env-flyingcloud -f docker-compose.yml down --remove-orphans
```

Recreate with:
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d --build
```

---

## Option C — Down + Volumes (Remove Everything, **Wipe Data**)

> **Dangerous**: deletes volumes bound to MySQL/Redis (wipes `ops/mysql/data`, `ops/redis/data` data directories). Use for **clean re-deploy** only.

```bash
cd /home/zh2701/code/flyingcloud-4156-project
docker compose --env-file .env-flyingcloud -f docker-compose.yml down -v --remove-orphans
```

Then bootstrap again (example):
```bash
sudo ./scripts/bootstrap.sh
```

---

## Service-Specific Stop/Start

Stop only the **app**:
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml stop app
```

Stop only **MySQL** or **Redis**:
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml stop mysql
docker compose --env-file .env-flyingcloud -f docker-compose.yml stop redis
```

Start specific services:
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d app
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d mysql
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d redis
```

Direct by container name (equivalent):
```bash
docker stop flyingcloud-4156 mysql redis
docker start flyingcloud-4156 mysql redis
```

---

## Restart & Rebuild Snippets

Rebuild **app** image only and restart app:
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml build app --no-cache
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d app
```

Recreate entire stack (respecting `.env-flyingcloud`):
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d --build
```

---

## Health Checks & Verification

List stack and health:
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml ps
```

Inspect individual container health:
```bash
docker inspect -f '{{.State.Health.Status}}' flyingcloud-4156   # app
docker inspect -f '{{.State.Health.Status}}' mysql              # mysql
docker inspect -f '{{.State.Health.Status}}' redis              # redis
```

App URL and docs:
- App: `http://<HOST>:8081`
- Swagger UI: `http://<HOST>:8081/swagger-ui.html`
- OpenAPI JSON: `http://<HOST>:8081/v3/api-docs`

---

## Logs & Troubleshooting

Follow logs:
```bash
docker logs -f flyingcloud-4156   # app
docker logs -f mysql              # mysql
docker logs -f redis              # redis
```

Common issues:
- **App cannot connect to DB**: ensure MySQL is healthy and credentials match `.env-flyingcloud`.
- **Redis auth fails**: confirm `REDIS_PASS` in `.env-flyingcloud`.
- **Swagger shows “Not logged in”**: use `X-Auth-Token: <accessToken>` from `/api/auth/login` response in the Swagger security dialog.

---

## Data Safety Notes

Persistent volumes (from the project layout):
- MySQL data: `ops/mysql/data`
- MySQL init SQL: `ops/mysql/init` (read on first start)
- Redis data: `ops/redis/data`

`down -v` removes volumes and **erases** MySQL/Redis data. Use with care.

If you have stray directories **outside** the project (e.g., `/home/zh2701/mysql`, `/home/zh2701/redis`, `/home/zh2701/sqlfile`) and they are not used, you can remove them:
```bash
sudo rm -rf /home/zh2701/mysql
sudo rm -rf /home/zh2701/redis
sudo rm -rf /home/zh2701/sqlfile
```

---

## One-Click Scripts

You can save and run these helpers (ensure execute permission with `chmod +x <file>`). They assume the project path above.

**stop-only.sh** — stop containers, keep everything else:
```bash
#!/usr/bin/env bash
set -euo pipefail
cd /home/zh2701/code/flyingcloud-4156-project
docker compose --env-file .env-flyingcloud -f docker-compose.yml stop
```

**down-keep-volumes.sh** — remove containers/network, keep volumes:
```bash
#!/usr/bin/env bash
set -euo pipefail
cd /home/zh2701/code/flyingcloud-4156-project
docker compose --env-file .env-flyingcloud -f docker-compose.yml down --remove-orphans
```

**down-wipe-volumes.sh** — remove everything including volumes (**data wipe**):
```bash
#!/usr/bin/env bash
set -euo pipefail
cd /home/zh2701/code/flyingcloud-4156-project
docker compose --env-file .env-flyingcloud -f docker-compose.yml down -v --remove-orphans
```

---

## Appendix: Compose & Container Names

From your `docker-compose.yml` and `.env-flyingcloud`:

- **Project/stack name**: `flyingcloud-stack` (Compose v3.9)
- **Network**: `${NET}` → default name `zh2701-net`
- **Containers**:
  - App: `${APP_CTN}` → `flyingcloud-4156`
  - MySQL: `${MYSQL_CTN}` → `mysql`
  - Redis: `${REDIS_CTN}` → `redis`
- **Ports**:
  - App: `${SERVER_PORT}` → `8081:8081`
  - MySQL: `3306:3306`
  - Redis: `6379:6379`
- **Healthchecks** configured for all three services.
