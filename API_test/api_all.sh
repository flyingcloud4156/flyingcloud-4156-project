#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# =======================================================================================
# FILE: ops/test/api_all.sh
# PURPOSE:
#   - Reset local MySQL DB (no docker)
#   - Auto-start Spring Boot app in background
#   - (optional) load big seed for UI charts
#   - Test ALL API endpoints (auth / user / ledger / txn / settlement / analytics)
#   - Auto-stop Spring Boot app when done
#
# USAGE:
#   # Start mysql / redis
#   #   brew services start mysql
#   #   brew services start redis
#   #
#   # Configure DB environment variables
#   #   export DB_HOST="127.0.0.1"
#   #   export DB_PORT="3306"
#   #   export DB_USER="root"
#   #   export DB_PASS="your_mysql_root_password"
#   #
#   # Run script (LOAD_BIG_SEED=1 expresses intention to load big seed; script always
#   # loads the big seed file to keep behavior backward compatible)
#   #   LOAD_BIG_SEED=1 HOST=http://localhost:8081 bash ops/test/api_all.sh
# =======================================================================================

HOST="${HOST:-http://127.0.0.1:8081}"

# Whether to load the large seed SQL file (currently we always load the big seed file,
# this flag is kept only for compatibility / future extension)
LOAD_BIG_SEED="${LOAD_BIG_SEED:-0}"

# Database configuration (can be overridden by environment variables)
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"
DB_NAME="${DB_NAME:-ledger}"

# Spring Boot app configuration
SPRING_PROFILES="${SPRING_PROFILES:-test}"
APP_START_TIMEOUT="${APP_START_TIMEOUT:-120}"  # seconds to wait for app to be ready

# API logging: 0 = no logs, 1 = log requests and responses
VERBOSE="${VERBOSE:-1}"

log_debug() {
  # All debug logs go to stderr to avoid polluting stdout (stdout is used for JSON
  # responses that are captured and parsed with jq).
  if [[ "${VERBOSE}" == "1" ]]; then
    echo "$@" >&2
  fi
}

# Construct MySQL argument array. Only add -p if DB_PASS is non-empty.
MYSQL_ARGS=(-h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER")
if [[ -n "$DB_PASS" ]]; then
  MYSQL_ARGS+=(-p"$DB_PASS")
fi

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
# Repo root: one level up from API_test (works locally and in GitHub Actions)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)

DB_BIG_SEED_FILE="${PROJECT_ROOT}/ops/sql/backup/ledger_big_seed.sql"
SCHEMA_FILE="${PROJECT_ROOT}/ops/sql/ledger_flow.sql"

# Spring Boot process PID for cleanup
SPRING_PID=""

# ---------- command checks ----------
need() { command -v "$1" >/dev/null 2>&1 || { echo "[FATAL] Missing command: $1"; exit 1; }; }
need mysql
need curl
need jq
need mvn

# ---------- Spring Boot app management ----------
start_spring_app() {
  echo "Starting Spring Boot application in background..."
  echo "Working directory: $PROJECT_ROOT"
  echo "Profile: $SPRING_PROFILES"

  # [IMPORTANT] Avoid legacy SPRING_PROFILES property (invalid in Boot 3)
  unset SPRING_PROFILES

  # Set environment variables for Spring Boot (use standard Spring props)
  export SPRING_PROFILES_ACTIVE="$SPRING_PROFILES"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=false&serverTimezone=America/New_York&characterEncoding=utf8&allowPublicKeyRetrieval=true}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$DB_USER}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$DB_PASS}"
  export SPRING_REDIS_HOST="${SPRING_REDIS_HOST:-${REDIS_HOST:-localhost}}"
  export SPRING_REDIS_PORT="${SPRING_REDIS_PORT:-${REDIS_PORT:-6379}}"
  export SPRING_REDIS_PASSWORD="${SPRING_REDIS_PASSWORD:-${REDIS_PASSWORD:-}}"

  # Start the app in background (skip frontend plugins to speed up startup)
  cd "$PROJECT_ROOT"
  mvn spring-boot:run -Dskip.npm -Dskip.installnodenpm -DskipTests > spring-boot.log 2>&1 &
  SPRING_PID=$!

  echo "Spring Boot started in background with PID: $SPRING_PID"
  echo "Waiting for app to be ready..."

  # Poll login endpoint until we see HTTP 405 (used as readiness signal)
  local count=0
  while [[ $count -lt $APP_START_TIMEOUT ]]; do
    local http_code
    http_code=$(curl -s -o /dev/null -w "%{http_code}" "$HOST/api/v1/auth/login" 2>/dev/null || echo "000")
    if [[ "$http_code" == "405" ]]; then
      echo "[OK] Spring Boot app is ready!"
      return 0
    fi
    sleep 2
    count=$((count + 2))
    echo "Waiting... ($count/$APP_START_TIMEOUT seconds) - HTTP code: $http_code"
  done

  echo "[ERROR] Spring Boot app did not become ready within $APP_START_TIMEOUT seconds"
  echo "Check spring-boot.log for details"
  tail -n 200 spring-boot.log || true
  kill_spring_app
  exit 1
}

kill_spring_app() {
  if [[ -n "$SPRING_PID" ]]; then
    echo "Stopping Spring Boot application (PID: $SPRING_PID)..."
    kill "$SPRING_PID" 2>/dev/null || true
    wait "$SPRING_PID" 2>/dev/null || true
    SPRING_PID=""
  fi
}

# Cleanup function executed on script exit
cleanup() {
  echo "Cleaning up..."
  kill_spring_app
  [[ -f "$HTTP_CODE_FILE" ]] && rm -f "$HTTP_CODE_FILE"
}
trap cleanup EXIT

# ---------- MySQL helpers (non-interactive) ----------
mysql_server_exec() {
  local sql="$1"
  mysql "${MYSQL_ARGS[@]}" -e "$sql"
}

mysql_db_query() {
  local sql="$1"
  mysql "${MYSQL_ARGS[@]}" -N -B "$DB_NAME" -e "$sql"
}

# ---------- pretty printing helpers ----------
title() {
  echo ""
  echo "======================================================================================="
  echo "=> $1"
  echo "======================================================================================="
}
sub() {
  echo ""
  echo "--- $1"
}

# ---------- API helpers ----------
LAST_HTTP="000"
HTTP_CODE_FILE="/tmp/api_test_http_code_$$"

fail_on_non2xx() {
  local code
  if [[ -f "$HTTP_CODE_FILE" ]]; then
    code="$(cat "$HTTP_CODE_FILE")"
  else
    code="000"
  fi
  if [[ "$code" -lt 200 || "$code" -ge 300 ]]; then
    echo "[ERROR] HTTP $code"
    exit 1
  fi
}

fail_if_success_false() {
  local body="$1"
  # Some endpoints may return empty body, treat that as success for our purposes
  if [[ -z "${body// }" ]]; then
    return 0
  fi
  local ok
  ok="$(echo "$body" | jq -er '.success' 2>/dev/null || echo "false")"
  if [[ "$ok" != "true" ]]; then
    echo "[ERROR] API returned success=false"
    echo "$body" | jq '.' || echo "$body"
    exit 1
  fi
}

# Unified API call wrapper:
# - logs request and response
# - maintains HTTP_CODE_FILE / LAST_HTTP
# - returns response body on stdout
api_call() {
  local method="$1"
  local path="$2"
  local payload="${3:-}"
  local token="${4:-}"

  local url="$HOST$path"

  local headers=(-H "Accept: application/json")
  if [[ "$method" == "POST" || "$method" == "PUT" || "$method" == "PATCH" ]]; then
    headers+=(-H "Content-Type: application/json")
  fi
  if [[ -n "$token" ]]; then
    headers+=(-H "X-Auth-Token: $token")
  fi

  # ====== log request ======
  log_debug ""
  log_debug ">>> HTTP $method $url"
  if [[ -n "$token" ]]; then
    # Do not print real token to avoid leakage
    log_debug ">>> Header: X-Auth-Token: ******(hidden)******"
  fi
  if [[ -n "$payload" ]]; then
    log_debug ">>> Request Body (raw):"
    if echo "$payload" | jq . >/dev/null 2>&1; then
      echo "$payload" | jq . >&2
    else
      echo "$payload" >&2
    fi
  else
    log_debug ">>> Request Body: (empty)"
  fi

  # ====== perform HTTP request ======
  local out
  if [[ -n "$payload" ]]; then
    out=$(curl -sS -X "$method" "$url" "${headers[@]}" -d "$payload" -w $'\n%{http_code}' 2>/dev/null)
  else
    out=$(curl -sS -X "$method" "$url" "${headers[@]}" -w $'\n%{http_code}' 2>/dev/null)
  fi

  # Extract HTTP code and save to temp file for fail_on_non2xx
  local raw_code
  raw_code="$(echo "$out" | tail -n1)"
  local http_code
  http_code="$(echo "$raw_code" | sed 's/[^0-9]*//g' | head -c 3)"
  [[ ${#http_code} -ne 3 ]] && http_code="000"
  echo "$http_code" > "$HTTP_CODE_FILE"
  LAST_HTTP="$http_code"

  # Response body = everything except the last line (http_code)
  local body
  body="$(echo "$out" | sed '$d')"

  # ====== log response ======
  log_debug "<<< HTTP Code: $http_code"
  if [[ -n "${body// }" ]]; then
    log_debug "<<< Response Body:"
    if echo "$body" | jq . >/dev/null 2>&1; then
      echo "$body" | jq . >&2
    else
      echo "$body" >&2
    fi
  else
    log_debug "<<< Response Body: (empty)"
  fi
  log_debug "<<< END"

  # Keep original behavior: stdout only contains the body
  echo "$body"
}

assert_not_null() {
  local name="$1"
  local val="${2:-}"
  if [[ -z "$val" || "$val" == "null" ]]; then
    echo "[FATAL] $name is null or empty"
    exit 1
  fi
}

# =======================================================================================
# 0) RESET DB AND START SPRING BOOT APP
# =======================================================================================
title "0) Resetting database and starting Spring Boot app"

echo "Dropping database $DB_NAME ..."
mysql_server_exec "DROP DATABASE IF EXISTS \`$DB_NAME\`;"

echo "Creating database $DB_NAME ..."
mysql_server_exec "CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo "Loading schema file: $SCHEMA_FILE"
mysql "${MYSQL_ARGS[@]}" "$DB_NAME" < "$SCHEMA_FILE"

echo "Loading seed data (big file): $DB_BIG_SEED_FILE"
mysql "${MYSQL_ARGS[@]}" "$DB_NAME" < "$DB_BIG_SEED_FILE"

echo "[OK] Database is ready."

# Start Spring Boot application
start_spring_app

# =======================================================================================
# 1) AUTH + USER APIs
# =======================================================================================
title "1) AUTH + USER APIs"

# Use existing seed users
RAND="$(date +%s)"
ALICE_EMAIL="alice@gmail.com"
BOB_EMAIL="bob@gmail.com"
CHARLIE_EMAIL="charlie@gmail.com"
PASS="Passw0rd!"

sub "1.1 Using seed users (Alice / Bob / Charlie already exist)"

sub "1.2 Login Alice"
login_body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$ALICE_EMAIL\",\"password\":\"$PASS\"}")
fail_on_non2xx; fail_if_success_false "$login_body"
ALICE_TOKEN="$(echo "$login_body" | jq -r '.data.access_token')"
ALICE_REFRESH="$(echo "$login_body" | jq -r '.data.refresh_token')"
assert_not_null "ALICE_TOKEN" "$ALICE_TOKEN"
assert_not_null "ALICE_REFRESH" "$ALICE_REFRESH"

sub "1.3 /users/me"
me_body=$(api_call GET "/api/v1/users/me" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$me_body"
ALICE_ID="$(echo "$me_body" | jq -r '.data.id')"
assert_not_null "ALICE_ID" "$ALICE_ID"

sub "1.4 /user-lookup (lookup Bob by email)"
lookup_body=$(api_call GET "/api/v1/user-lookup?email=$BOB_EMAIL" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$lookup_body"
BOB_ID="$(echo "$lookup_body" | jq -r '.data.user_id')"
assert_not_null "BOB_ID" "$BOB_ID"

sub "1.5 /users/{id} (Bob profile)"
profile_body=$(api_call GET "/api/v1/users/$BOB_ID" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$profile_body"

sub "1.6 refresh token"
refresh_body=$(api_call POST "/api/v1/auth/refresh?refreshToken=$ALICE_REFRESH" "")
fail_on_non2xx; fail_if_success_false "$refresh_body"
ALICE_TOKEN2="$(echo "$refresh_body" | jq -r '.data.access_token')"
ALICE_REFRESH2="$(echo "$refresh_body" | jq -r '.data.refresh_token')"
assert_not_null "ALICE_TOKEN2" "$ALICE_TOKEN2"
assert_not_null "ALICE_REFRESH2" "$ALICE_REFRESH2"
ALICE_TOKEN="$ALICE_TOKEN2"
ALICE_REFRESH="$ALICE_REFRESH2"

# =======================================================================================
# 2) LEDGER APIs
# =======================================================================================
title "2) LEDGER APIs"

sub "2.1 Create ledger"
ledger_payload=$(jq -n --arg name "Road Trip Fund - $RAND" '{
  name: $name,
  ledger_type: "GROUP_BALANCE",
  base_currency: "USD",
  category: {
    name: "Gas",
    kind: "EXPENSE",
    is_active: true
  }
}')
ledger_body=$(api_call POST "/api/v1/ledgers" "$ledger_payload" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$ledger_body"
LEDGER_ID="$(echo "$ledger_body" | jq -r '.data.ledger_id')"
assert_not_null "LEDGER_ID" "$LEDGER_ID"

sub "2.2 Get my ledgers (/ledgers/mine)"
mine_body=$(api_call GET "/api/v1/ledgers/mine" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$mine_body"

sub "2.3 Get ledger details (/ledgers/{id})"
detail_body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$detail_body"

sub "2.4 Add Bob + Charlie as members"
CHARLIE_ID="$(mysql_db_query "SELECT id FROM users WHERE email='$CHARLIE_EMAIL';")"
assert_not_null "CHARLIE_ID" "$CHARLIE_ID"

add_bob=$(jq -n --argjson uid "$BOB_ID" '{user_id:$uid, role:"EDITOR"}')
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_bob" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$body"

add_charlie=$(jq -n --argjson uid "$CHARLIE_ID" '{user_id:$uid, role:"EDITOR"}')
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_charlie" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$body"

sub "2.5 List members"
members_body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/members" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$members_body"

sub "2.6 Remove Charlie then add back"
del_body=$(api_call DELETE "/api/v1/ledgers/$LEDGER_ID/members/$CHARLIE_ID" "" "$ALICE_TOKEN")
fail_on_non2xx

add_charlie=$(jq -n --argjson uid "$CHARLIE_ID" '{user_id:$uid, role:"EDITOR"}')
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_charlie" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$body"

# =======================================================================================
# 3) TRANSACTION APIs
# =======================================================================================
title "3) TRANSACTION APIs"

sub "3.1 Create EXPENSE (EXACT split) - 2025-09"
payload=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "150.00" \
  --arg note "Gas and tolls (test)" \
  --argjson alice_id "$ALICE_ID" \
  --argjson bob_id "$BOB_ID" \
  --argjson charlie_id "$CHARLIE_ID" \
  '{
    txn_at: "2025-09-10T08:00:00",
    type: $type,
    payer_id: $payer_id,
    amount_total: $amount_total,
    currency: "USD",
    note: $note,
    is_private: false,
    rounding_strategy: "NONE",
    tail_allocation: "PAYER",
    splits: [
      { user_id: $alice_id, split_method: "EXACT", share_value: 75.00, included: true },
      { user_id: $bob_id, split_method: "EXACT", share_value: 50.00, included: true },
      { user_id: $charlie_id, split_method: "EXACT", share_value: 25.00, included: true }
    ]
  }'
)
txn_body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$txn_body"
TXN1_ID="$(echo "$txn_body" | jq -r '.data.transaction_id')"
assert_not_null "TXN1_ID" "$TXN1_ID"

sub "3.2 Create INCOME - 2025-09"
payload=$(jq -n \
  --arg type "INCOME" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "100.00" \
  --arg note "Refund (test)" \
  --argjson alice_id "$ALICE_ID" \
  --argjson bob_id "$BOB_ID" \
  --argjson charlie_id "$CHARLIE_ID" \
  '{
    txn_at: "2025-09-11T08:00:00",
    type: $type,
    payer_id: $payer_id,
    amount_total: $amount_total,
    currency: "USD",
    note: $note,
    is_private: false,
    rounding_strategy: "NONE",
    tail_allocation: "PAYER",
    splits: [
      { user_id: $alice_id, split_method: "EXACT", share_value: 34.00, included: true },
      { user_id: $bob_id, split_method: "EXACT", share_value: 33.00, included: true },
      { user_id: $charlie_id, split_method: "EXACT", share_value: 33.00, included: true }
    ]
  }'
)
txn_body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$txn_body"
TXN2_ID="$(echo "$txn_body" | jq -r '.data.transaction_id')"
assert_not_null "TXN2_ID" "$TXN2_ID"

sub "3.3 Create another EXPENSE in October (for trend)"
payload=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$BOB_ID" \
  --arg amount_total "240.00" \
  --arg note "Groceries (test)" \
  --argjson alice_id "$ALICE_ID" \
  --argjson bob_id "$BOB_ID" \
  --argjson charlie_id "$CHARLIE_ID" \
  '{
    txn_at: "2025-10-05T18:00:00",
    type: $type,
    payer_id: $payer_id,
    amount_total: $amount_total,
    currency: "USD",
    note: $note,
    is_private: false,
    rounding_strategy: "NONE",
    tail_allocation: "PAYER",
    splits: [
      { user_id: $alice_id, split_method: "EXACT", share_value: 80.00, included: true },
      { user_id: $bob_id, split_method: "EXACT", share_value: 80.00, included: true },
      { user_id: $charlie_id, split_method: "EXACT", share_value: 80.00, included: true }
    ]
  }'
)
txn_body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$txn_body"
TXN3_ID="$(echo "$txn_body" | jq -r '.data.transaction_id')"
assert_not_null "TXN3_ID" "$TXN3_ID"

sub "3.4 Get transaction detail"
get_body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/transactions/$TXN1_ID" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$get_body"

sub "3.5 List transactions (paged)"
list_body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/transactions?page=1&size=20" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$list_body"

sub "3.6 Delete TXN2 and list again"
del_body=$(api_call DELETE "/api/v1/ledgers/$LEDGER_ID/transactions/$TXN2_ID" "" "$ALICE_TOKEN")
fail_on_non2xx
list_body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/transactions?page=1&size=50" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$list_body"

# =======================================================================================
# 4) SETTLEMENT PLAN
# =======================================================================================
title "4) SETTLEMENT PLAN API"

sub "4.1 /settlement-plan"
settle_body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$settle_body"

# =======================================================================================
# 5) ANALYTICS OVERVIEW
# =======================================================================================
title "5) ANALYTICS API"

sub "5.1 /analytics/overview?months=3"
ana_body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/analytics/overview?months=3" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$ana_body"

TOTAL_EXPENSE=$(echo "$ana_body" | jq -r '.data.total_expense')
TOTAL_INCOME=$(echo "$ana_body" | jq -r '.data.total_income')
TREND_LEN=$(echo "$ana_body" | jq -r '.data.trend | length')
REC_LEN=$(echo "$ana_body" | jq -r '.data.recommendations | length')

echo "Analytics check:"
echo "  total_income  = $TOTAL_INCOME"
echo "  total_expense = $TOTAL_EXPENSE"
echo "  trend_len     = $TREND_LEN"
echo "  rec_len       = $REC_LEN"

if [[ "$REC_LEN" -lt 1 ]]; then
  echo "[ERROR] Expected at least 1 recommendation, but got $REC_LEN"
  echo "$ana_body" | jq .
  exit 1
fi

# =======================================================================================
# 6) LOGOUT
# =======================================================================================
title "6) LOGOUT"

sub "6.1 /auth/logout"
logout_body=$(api_call POST "/api/v1/auth/logout?refreshToken=$ALICE_REFRESH" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$logout_body"

echo ""
echo "[DONE] ALL API TESTS PASSED ✅"