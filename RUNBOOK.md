## FlyingCloud 4156 Project - Terminal Runbook (Docker Compose)

This document explains how to run and maintain the project entirely from the terminal on a Linux server. It covers two scenarios:

- Scenario A: You already have MySQL + Redis + the app running from this project, and you want a clean re-deploy (wipe old MySQL data, re-init the schema, and restart everything).
- Scenario B: A clean machine (no containers running yet) and you want to deploy from scratch.

Notes:
- Do NOT delete `src/main/resources/application.yaml`. The app uses environment variables from `.env-flyingcloud` and falls back to defaults defined there.
- All commands assume your project path is `/home/zh2701/code/flyingcloud-4156-project` and Docker is installed.
- Compose file reads variables from `.env-flyingcloud`.

### Directory layout (important volumes)
- MySQL data: `ops/mysql/data` (persistent)
- MySQL init SQL: `ops/mysql/init` (read at first container start)
- Redis data: `ops/redis/data` (persistent)

### Health endpoints and URLs
- App: `http://<HOST>:8081` (port comes from `.env-flyingcloud` `SERVER_PORT`)
- Swagger UI: `http://<HOST>:8081/swagger-ui.html`
- OpenAPI JSON: `http://<HOST>:8081/v3/api-docs`

---

## Prerequisites

1) Docker and Docker Compose installed.
2) User can run `docker` commands (or prefix with `sudo`).
3) Port 8081, 3306, 6379 are available on the host.

---

## Scenario A — Clean re-deploy when MySQL/Redis/app already exist

Use this when you want to wipe the database and reinitialize from the SQL in `ops/sql/ledger_flow.sql`, then restart Redis and the app.

Step 1: Run the cleanup script
```bash
cd /home/zh2701/code/flyingcloud-4156-project
sudo ./scripts/cleanup.sh
```
What it does:
- Stops the app container (keeps Redis and MySQL managed separately).
- Removes the MySQL container and deletes `ops/mysql/data`.
- Recreates MySQL directories and fixes permissions for the official image.
- Starts MySQL only and waits until healthy.
- Starts Redis + app after MySQL is healthy.

Step 2: Verify containers
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml ps
```
Expect MySQL and Redis to be `healthy`, app to be `healthy` after a short while.

Step 3: Test endpoints
```bash
# Register (example)
curl -sS -X POST "http://<HOST>:8081/api/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@gmail.com","name":"testU","password":"S3cure!1"}'

# Login, copy the accessToken from the response
curl -sS -X POST "http://<HOST>:8081/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"user@gmail.com","password":"S3cure!1"}'

# Example of calling a protected API (replace TOKEN)
curl -sS -X GET "http://<HOST>:8081/api/users/me" \
  -H "X-Auth-Token: <accessToken>"
```

---

## Scenario B — Fresh server (first-time deployment)

Step 1: Clone or copy the project
```bash
mkdir -p /home/zh2701/code
cd /home/zh2701/code
git clone <your-repo-url> flyingcloud-4156-project  # or copy the directory here
cd flyingcloud-4156-project
```

Step 2: Build and start everything
Option 1 — one-step bootstrap (recommended)
```bash
sudo ./scripts/bootstrap.sh
```
What it does:
- Loads `.env-flyingcloud`.
- `docker compose down -v` to ensure a clean stack.
- Creates MySQL/Redis data directories and applies permissions.
- Builds and starts all services: MySQL, Redis, app.
- Waits for health checks.

Option 2 — manual compose commands
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d --build
```

Step 3: Verify and test
Same as Scenario A step 2 and 3.

---

## Operational commands

Show logs
```bash
docker logs -f flyingcloud-4156            # app
docker logs -f mysql                       # mysql
docker logs -f redis                       # redis
```

Rebuild app image only
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml build app --no-cache
docker compose --env-file .env-flyingcloud -f docker-compose.yml up -d app
```

Stop and remove everything (keep volumes)
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml down --remove-orphans
```

Stop and remove everything (including volumes)
```bash
docker compose --env-file .env-flyingcloud -f docker-compose.yml down -v --remove-orphans
```

Check container health status
```bash
docker inspect -f '{{.State.Health.Status}}' flyingcloud-4156
docker inspect -f '{{.State.Health.Status}}' mysql
docker inspect -f '{{.State.Health.Status}}' redis
```

MySQL CLI quick checks
```bash
mysql -h 127.0.0.1 -P 3306 -u appuser -p'AppUser#P@ssw0rd' -e 'SHOW DATABASES; USE ledger; SHOW TABLES;'
```

---

## Authentication in API calls

- Login returns a JSON with `accessToken` and `refreshToken`.
- Use header `X-Auth-Token: <accessToken>` when calling protected APIs like `/api/users/me`.
- Access token TTL: 2 hours. Refresh token TTL: 14 days. Refresh via `/api/auth/refresh?refreshToken=...`.

---

## Removing duplicate/extra MySQL and Redis directories outside the project

You mentioned extra directories at host root (outside `code/`): `mysql` and `redis`. If these are duplicates and not used, you can remove them safely with the following commands. Double-check paths before running.

```bash
# Remove duplicate MySQL directory outside the project (adjust path if needed)
sudo rm -rf /home/zh2701/mysql

# Remove duplicate Redis directory outside the project (adjust path if needed)
sudo rm -rf /home/zh2701/redis

# If you also want to remove a separate SQL dump folder outside the project
sudo rm -rf /home/zh2701/sqlfile
```

These removals do NOT affect the project volumes under `/home/zh2701/code/flyingcloud-4156-project/ops/...`.

---

## Troubleshooting

- Could not open JDBC Connection for transaction
  - Ensure MySQL is healthy: `docker inspect -f '{{.State.Health.Status}}' mysql`
  - Ensure the app configured DB user exists. We create it using `MYSQL_USER`/`MYSQL_PASSWORD` in `docker-compose.yml` under the `mysql` service. If you wiped data, the first start may take longer.
  - Check app logs for the exact datasource URL and credentials being used.

- Redis auth failures
  - Confirm `REDIS_PASS` in `.env-flyingcloud` and that the container started healthy.

- Swagger shows "Not logged in"
  - Click Authorize in Swagger UI and paste your `accessToken` into the `X-Auth-Token` security scheme.

---

## Where configuration lives

- `.env-flyingcloud`: central place for environment values used by Docker Compose.
- `src/main/resources/application.yaml`: Spring Boot defaults; values can be overridden by environment variables (`DB_URL`, `DB_USER`, `DB_PASS`, `REDIS_*`, etc.) passed from Compose.


