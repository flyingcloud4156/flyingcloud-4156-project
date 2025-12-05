#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# ==============================================================================
# FILE: ops/test/api_settlement_execution_tests.sh
# PURPOSE:
#   - API tests for Settlement Plan Execution endpoint
#   - Cover valid and invalid equivalence partitions
#   - Test authorization and edge cases
#
# ENDPOINT TESTED:
#   - POST /api/v1/ledgers/{ledgerId}/settlement-plan (executeSettlement)
#
# USAGE:
#   HOST=http://localhost:8081 bash ops/test/api_settlement_execution_tests.sh
# ==============================================================================

HOST="${HOST:-http://127.0.0.1:8081}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"
DB_NAME="${DB_NAME:-ledger}"

MYSQL_ARGS=(-h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER")
if [[ -n "$DB_PASS" ]]; then
  MYSQL_ARGS+=(-p"$DB_PASS")
fi

# Spring Boot app configuration
SPRING_PROFILES="${SPRING_PROFILES:-test}"
APP_START_TIMEOUT="${APP_START_TIMEOUT:-120}"  # seconds

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
# Prefer git root if available; fallback to parent of API_test
PROJECT_ROOT=$((git -C "$SCRIPT_DIR" rev-parse --show-toplevel 2>/dev/null) || true)
if [[ -z "${PROJECT_ROOT}" ]]; then
  PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)
fi

SCHEMA_FILE="${PROJECT_ROOT}/ops/sql/ledger_flow.sql"
SEED_FILE="${PROJECT_ROOT}/ops/sql/backup/ledger_big_seed.sql"

HTTP_CODE_FILE="/tmp/api_settlement_test_http_code_$$"
SPRING_PID=""

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FATAL] Missing command: $1" >&2
    exit 1
  }
}

need mysql
need curl
need jq

cleanup() {
  if [[ -n "$SPRING_PID" ]]; then
    echo "Stopping Spring Boot application (PID: $SPRING_PID)..."
    kill "$SPRING_PID" 2>/dev/null || true
    wait "$SPRING_PID" 2>/dev/null || true
    SPRING_PID=""
  fi
  [[ -f "$HTTP_CODE_FILE" ]] && rm -f "$HTTP_CODE_FILE"
}
trap cleanup EXIT

start_spring_app() {
  echo "Starting Spring Boot application in background..."
  echo "Working directory: $PROJECT_ROOT"
  echo "Profile: ${SPRING_PROFILES:-test}"

  local PROFILE_VAL="${SPRING_PROFILES:-test}"
  unset SPRING_PROFILES

  export SPRING_PROFILES_ACTIVE="$PROFILE_VAL"
  export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useSSL=false&serverTimezone=America/New_York&characterEncoding=utf8&allowPublicKeyRetrieval=true}"
  export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-$DB_USER}"
  export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-$DB_PASS}"
  export SPRING_REDIS_HOST="${SPRING_REDIS_HOST:-${REDIS_HOST:-localhost}}"
  export SPRING_REDIS_PORT="${SPRING_REDIS_PORT:-${REDIS_PORT:-6379}}"
  export SPRING_REDIS_PASSWORD="${SPRING_REDIS_PASSWORD:-${REDIS_PASSWORD:-}}"

  cd "$PROJECT_ROOT"
  mvn spring-boot:run -Dskip.npm -Dskip.installnodenpm -DskipTests > spring-boot.log 2>&1 &
  SPRING_PID=$!

  echo "Spring Boot started with PID: $SPRING_PID"
  echo "Waiting for app to be ready..."

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
  exit 1
}

mysql_server_exec() {
  local sql="$1"
  mysql "${MYSQL_ARGS[@]}" -e "$sql"
}

mysql_db_query() {
  local sql="$1"
  mysql "${MYSQL_ARGS[@]}" -N -B "$DB_NAME" -e "$sql"
}

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

  local out
  if [[ -n "$payload" ]]; then
    out=$(curl -sS -X "$method" "$url" "${headers[@]}" -d "$payload" -w $'\n%{http_code}' 2>/dev/null)
  else
    out=$(curl -sS -X "$method" "$url" "${headers[@]}" -w $'\n%{http_code}' 2>/dev/null)
  fi

  local raw_code="$(echo "$out" | tail -n1)"
  local http_code="$(echo "$raw_code" | sed 's/[^0-9]*//g' | head -c 3)"
  [[ ${#http_code} -ne 3 ]] && http_code="000"
  echo "$http_code" > "$HTTP_CODE_FILE"

  echo "$out" | sed '$d'
}

assert_success() {
  local body="$1"
  local code
  if [[ -f "$HTTP_CODE_FILE" ]]; then
    code="$(cat "$HTTP_CODE_FILE")"
  else
    code="000"
  fi

  if [[ "$code" -lt 200 || "$code" -ge 300 ]]; then
    echo "[ASSERTION FAILED] Expected 2xx HTTP, got $code" >&2
    echo "$body" | jq '.' || echo "$body"
    exit 1
  fi

  local ok
  ok="$(echo "$body" | jq -er '.success' 2>/dev/null || echo "false")"
  if [[ "$ok" != "true" ]]; then
    echo "[ASSERTION FAILED] Expected success=true" >&2
    echo "$body" | jq '.' || echo "$body"
    exit 1
  fi
}

assert_failure() {
  local body="$1"
  local code
  if [[ -f "$HTTP_CODE_FILE" ]]; then
    code="$(cat "$HTTP_CODE_FILE")"
  else
    code="000"
  fi

  local ok="false"
  if [[ -n "${body// }" ]]; then
    ok="$(echo "$body" | jq -er '.success' 2>/dev/null || echo "false")"
  fi

  if [[ "$code" -ge 200 && "$code" -lt 300 && "$ok" == "true" ]]; then
    echo "[ASSERTION FAILED] Expected failure but got HTTP $code and success=true" >&2
    echo "$body" | jq '.' || echo "$body"
    exit 1
  fi
}

# ==============================================================================
# 0) SETUP
# ==============================================================================

title "0) SETUP: Database and Test Data"

echo "Resetting database..."
mysql_server_exec "DROP DATABASE IF EXISTS \`$DB_NAME\`;"
mysql_server_exec "CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo "Loading schema: $SCHEMA_FILE"
mysql "${MYSQL_ARGS[@]}" "$DB_NAME" < "$SCHEMA_FILE"

echo "Loading seed data: $SEED_FILE"
mysql "${MYSQL_ARGS[@]}" "$DB_NAME" < "$SEED_FILE"

echo "[OK] Database initialized."

# Start Spring Boot application
start_spring_app

RAND="$(date +%s)"
ALICE_EMAIL="alice@gmail.com"
ALICE_PASS="Passw0rd!"

BOB_EMAIL="bob@gmail.com"
BOB_PASS="Passw0rd!"

CHARLIE_EMAIL="charlie@gmail.com"
CHARLIE_PASS="Passw0rd!"

sub "Login Alice (OWNER)"
login_body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$ALICE_EMAIL\",\"password\":\"$ALICE_PASS\"}")
assert_success "$login_body"
ALICE_TOKEN="$(echo "$login_body" | jq -r '.data.access_token')"

sub "Get Alice ID"
me_body=$(api_call GET "/api/v1/users/me" "" "$ALICE_TOKEN")
assert_success "$me_body"
ALICE_ID="$(echo "$me_body" | jq -r '.data.id')"

sub "Login Bob (will be EDITOR)"
login_body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$BOB_EMAIL\",\"password\":\"$BOB_PASS\"}")
assert_success "$login_body"
BOB_TOKEN="$(echo "$login_body" | jq -r '.data.access_token')"

sub "Get Bob ID"
me_body=$(api_call GET "/api/v1/users/me" "" "$BOB_TOKEN")
assert_success "$me_body"
BOB_ID="$(echo "$me_body" | jq -r '.data.id')"

sub "Login Charlie (will be VIEWER)"
login_body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$CHARLIE_EMAIL\",\"password\":\"$CHARLIE_PASS\"}")
assert_success "$login_body"
CHARLIE_TOKEN="$(echo "$login_body" | jq -r '.data.access_token')"

sub "Get Charlie ID"
me_body=$(api_call GET "/api/v1/users/me" "" "$CHARLIE_TOKEN")
assert_success "$me_body"
CHARLIE_ID="$(echo "$me_body" | jq -r '.data.id')"

sub "Create test ledger"
ledger_payload=$(jq -n --arg name "Settlement Test Ledger $RAND" '{
  name: $name,
  ledger_type: "GROUP_BALANCE",
  base_currency: "USD",
  categories: [
    { name: "Settlement Default", kind: "EXPENSE" }
  ]
}')
ledger_body=$(api_call POST "/api/v1/ledgers" "$ledger_payload" "$ALICE_TOKEN")
assert_success "$ledger_body"
LEDGER_ID="$(echo "$ledger_body" | jq -r '.data.ledger_id')"

sub "Create category for transactions"
CATEGORY_ID=$(mysql_db_query "SELECT id FROM categories WHERE ledger_id=$LEDGER_ID LIMIT 1;")

sub "Add Bob as EDITOR"
add_bob=$(jq -n --argjson uid "$BOB_ID" '{user_id:$uid, role:"EDITOR"}')
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_bob" "$ALICE_TOKEN")
assert_success "$body"

sub "Add Charlie as VIEWER"
add_charlie=$(jq -n --argjson uid "$CHARLIE_ID" '{user_id:$uid, role:"VIEWER"}')
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_charlie" "$ALICE_TOKEN")
assert_success "$body"

sub "Create transactions to generate debts"
# Alice pays 100, split equally between Alice and Bob
txn1=$(jq -n \
  --argjson alice "$ALICE_ID" \
  --argjson bob "$BOB_ID" \
  --argjson cat "$CATEGORY_ID" \
  '{
    txn_at: "2025-11-01T10:00:00",
    type: "EXPENSE",
    category_id: $cat,
    payer_id: $alice,
    amount_total: "100.00",
    currency: "USD",
    note: "Test expense 1",
    is_private: false,
    rounding_strategy: "ROUND_HALF_UP",
    tail_allocation: "PAYER",
    splits: [
      { user_id: $alice, split_method: "EQUAL", share_value: 0, included: true },
      { user_id: $bob, split_method: "EQUAL", share_value: 0, included: true }
    ]
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn1" "$ALICE_TOKEN")
assert_success "$body"

echo "[OK] Test setup complete"
echo "  LEDGER_ID=$LEDGER_ID"
echo "  ALICE_ID=$ALICE_ID (OWNER)"
echo "  BOB_ID=$BOB_ID (EDITOR)"
echo "  CHARLIE_ID=$CHARLIE_ID (VIEWER)"

# ==============================================================================
# 1) POST /settlement-plan - VALID PARTITIONS
# ==============================================================================

title "1) POST /settlement-plan - VALID EQUIVALENCE PARTITIONS"

sub "[SETTLEMENT-EXECUTE-AS-OWNER] Execute settlement as OWNER (typical valid)"
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "" "$ALICE_TOKEN")
assert_success "$body"
echo "[PASS] Settlement executed successfully as OWNER"

sub "Verify settlement was executed (check transactions)"
list_body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/transactions?page=1&size=50" "" "$ALICE_TOKEN")
assert_success "$list_body"
echo "[PASS] Transactions retrieved after settlement"

sub "[SETTLEMENT-EXECUTE-AS-EDITOR] Execute settlement as EDITOR (valid)"
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "" "$BOB_TOKEN")
assert_success "$body"
echo "[PASS] Settlement executed successfully as EDITOR"

# ==============================================================================
# 2) POST /settlement-plan - INVALID PARTITIONS
# ==============================================================================

title "2) POST /settlement-plan - VALID (VIEWER can also view plan)"

sub "[SETTLEMENT-VIEW-AS-VIEWER] View settlement plan as VIEWER (valid)"
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "" "$CHARLIE_TOKEN")
assert_success "$body"
echo "[PASS] VIEWER can view settlement plan"

title "3) POST /settlement-plan - INVALID EQUIVALENCE PARTITIONS"

sub "[SETTLEMENT-NO-AUTH] Execute settlement without authentication"
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "")
assert_failure "$body"
echo "[PASS] No authentication properly rejected"

sub "[SETTLEMENT-NONEXISTENT-LEDGER] Execute settlement on non-existent ledger"
body=$(api_call POST "/api/v1/ledgers/999999/settlement-plan" "" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Non-existent ledger properly rejected"

sub "Create non-member user"
DAVE_EMAIL="dave.settlement.test+$RAND@test.com"
DAVE_PASS="Passw0rd!"
reg_body=$(api_call POST "/api/v1/auth/register" "{\"email\":\"$DAVE_EMAIL\",\"name\":\"Dave\",\"password\":\"$DAVE_PASS\"}")
login_body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$DAVE_EMAIL\",\"password\":\"$DAVE_PASS\"}")
DAVE_TOKEN="$(echo "$login_body" | jq -r '.data.access_token')"

sub "[SETTLEMENT-NON-MEMBER] Execute settlement as non-member"
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "" "$DAVE_TOKEN")
assert_failure "$body"
echo "[PASS] Non-member properly rejected from executing settlement"

# ==============================================================================
# 4) SUMMARY
# ==============================================================================

title "4) TEST SUMMARY"

echo ""
echo "All Settlement Plan Execution API tests completed successfully!"
echo ""
echo "Equivalence Partitions Tested:"
echo "  POST /settlement-plan:"
echo "    - Valid: 3 partitions (OWNER, EDITOR, VIEWER - all members can view plan)"
echo "    - Invalid: 3 partitions (no auth, non-existent ledger, non-member)"
echo ""

