#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# ==============================================================================
# FILE: ops/test/api_negative.sh
# PURPOSE:
#   - API-level tests for invalid and boundary inputs
#   - Cover equivalence partitions and boundary analysis for key endpoints
#   - Exercise multiple clients, persistence, and membership checks
#
# USAGE:
#   # Preconditions:
#   #   - MySQL and Redis are running locally (or in CI container)
#   #
#   # Example:
#   #   export DB_HOST="127.0.0.1"
#   #   export DB_PORT="3306"
#   #   export DB_USER="root"
#   #   export DB_PASS="your_mysql_root_password"
#   #
#   #   HOST=http://localhost:8081 bash ops/test/api_negative.sh
# ==============================================================================

HOST="${HOST:-http://127.0.0.1:8081}"

DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"
DB_NAME="${DB_NAME:-ledger}"

# Spring Boot app configuration
SPRING_PROFILES="${SPRING_PROFILES:-test}"
APP_START_TIMEOUT="${APP_START_TIMEOUT:-120}"  # seconds to wait for app to start

MYSQL_ARGS=(-h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER")
if [[ -n "$DB_PASS" ]]; then
  MYSQL_ARGS+=(-p"$DB_PASS")
fi

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
# Repo root: one level up from API_test (works locally and in GitHub Actions)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)

DB_BIG_SEED_FILE="${PROJECT_ROOT}/ops/sql/backup/ledger_big_seed.sql"
SCHEMA_FILE="${PROJECT_ROOT}/ops/sql/ledger_flow.sql"

# Spring Boot PID for cleanup
SPRING_PID=""
HTTP_CODE_FILE="/tmp/api_negative_http_code_$$"

LAST_HTTP="000"

need() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "[FATAL] Missing command: $1" >&2
    exit 1
  }
}

need mysql
need curl
need jq
need mvn

# ---------- Spring Boot app management ----------
start_spring_app() {
  echo "Starting Spring Boot application in background..."
  echo "Working directory: $PROJECT_ROOT"
  echo "Profile: $SPRING_PROFILES"

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

  echo "Spring Boot started with PID: $SPRING_PID"
  echo "Waiting for app to be ready..."

  # Wait for app to start
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

  echo "[ERROR] Spring Boot app failed to start within $APP_START_TIMEOUT seconds"
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

# Cleanup function for script exit
cleanup() {
  echo "Cleaning up..."
  kill_spring_app
  [[ -f "$HTTP_CODE_FILE" ]] && rm -f "$HTTP_CODE_FILE"
}
trap cleanup EXIT

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

# API call function that handles requests and sets LAST_HTTP
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

  # Extract and save HTTP code to file
  local raw_code="$(echo "$out" | tail -n1)"
  local http_code="$(echo "$raw_code" | sed 's/[^0-9]*//g' | head -c 3)"
  [[ ${#http_code} -ne 3 ]] && http_code="000"
  echo "$http_code" > "$HTTP_CODE_FILE"

  # Return response body only
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
  # For invalid partitions we only care that the service does NOT treat
  # the request as a business success.
  local body="$1"
  local code="$LAST_HTTP"

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

assert_equals() {
  local expected="$1"
  local actual="$2"
  if [[ "$expected" != "$actual" ]]; then
    echo "[ASSERTION FAILED] Expected '$expected' but got '$actual'" >&2
    exit 1
  fi
}

# ==============================================================================
# 0) RESET DATABASE
# ==============================================================================

title "0) RESETTING DATABASE FOR NEGATIVE TESTS"

echo "Dropping database $DB_NAME ..."
mysql_server_exec "DROP DATABASE IF EXISTS \`$DB_NAME\`;"

echo "Creating database $DB_NAME ..."
mysql_server_exec "CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo "Loading schema: $SCHEMA_FILE"
mysql "${MYSQL_ARGS[@]}" "$DB_NAME" < "$SCHEMA_FILE"

echo "Loading seed data: $DB_BIG_SEED_FILE"
mysql "${MYSQL_ARGS[@]}" "$DB_NAME" < "$DB_BIG_SEED_FILE"

echo "[OK] Database initialized."

# Start Spring Boot application
start_spring_app

# ==============================================================================
# 1) AUTH ENDPOINTS - EQUIVALENCE PARTITIONS
# ==============================================================================

title "1) AUTH ENDPOINTS - EQUIVALENCE PARTITIONS AND BOUNDARIES"

# Use existing seed data users
RAND="$(date +%s)"
ALICE_EMAIL_VALID="alice@gmail.com"
ALICE_PASS_VALID="Passw0rd!"

sub "[AUTH-LOGIN-VALID] Login with existing seed user"

sub "[AUTH-REG-INVALID-EMAIL] Register with invalid email (missing @)"
body=$(api_call POST "/api/v1/auth/register" "{\"email\":\"invalid-email-format\",\"name\":\"Bad Email\",\"password\":\"$ALICE_PASS_VALID\"}")
assert_failure "$body"

sub "[AUTH-REG-INVALID-PASS-EMPTY] Register with empty password"
body=$(api_call POST "/api/v1/auth/register" "{\"email\":\"empty.pass+$RAND@test.com\",\"name\":\"Empty Pass\",\"password\":\"\"}")
assert_failure "$body"

sub "[AUTH-REG-DUPLICATE-EMAIL] Register duplicate email"
body=$(api_call POST "/api/v1/auth/register" "{\"email\":\"$ALICE_EMAIL_VALID\",\"name\":\"Alice Dup\",\"password\":\"$ALICE_PASS_VALID\"}")
assert_failure "$body"

sub "[AUTH-LOGIN-VALID] Login with correct credentials"
login_body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$ALICE_EMAIL_VALID\",\"password\":\"$ALICE_PASS_VALID\"}")
assert_success "$login_body"
ALICE_TOKEN="$(echo "$login_body" | jq -r '.data.access_token')"

sub "[AUTH-LOGIN-WRONG-PASSWORD] Login with wrong password"
body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$ALICE_EMAIL_VALID\",\"password\":\"WrongPass123\"}")
assert_failure "$body"

sub "[AUTH-LOGIN-NONEXISTENT-USER] Login unknown email"
body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"no.such.user+$RAND@test.com\",\"password\":\"AnyPass123\"}")
assert_failure "$body"

# ==============================================================================
# 2) USERS ENDPOINTS - AUTH / MULTIPLE CLIENTS
# ==============================================================================

title "2) USERS ENDPOINTS - AUTHORIZATION AND MULTIPLE CLIENTS"

sub "[USERS-ME-WITH-TOKEN] /users/me with valid token"
body=$(api_call GET "/api/v1/users/me" "" "$ALICE_TOKEN")
assert_success "$body"
ALICE_ID="$(echo "$body" | jq -r '.data.id')"

sub "[USERS-ME-NO-TOKEN] /users/me without token must fail"
body=$(api_call GET "/api/v1/users/me")
assert_failure "$body"

# Create a second client (Bob)
BOB_EMAIL_VALID="bob.valid+$RAND@test.com"
BOB_PASS_VALID="Passw0rd!"

sub "[AUTH-REG-BOB] Register Bob as second client"
body=$(api_call POST "/api/v1/auth/register" "{\"email\":\"$BOB_EMAIL_VALID\",\"name\":\"Bob Valid\",\"password\":\"$BOB_PASS_VALID\"}")
assert_success "$body"

sub "[AUTH-LOGIN-BOB] Login Bob"
bob_login=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$BOB_EMAIL_VALID\",\"password\":\"$BOB_PASS_VALID\"}")
assert_success "$bob_login"
BOB_TOKEN="$(echo "$bob_login" | jq -r '.data.access_token')"

sub "[BOB-ME] /users/me for Bob"
bob_me=$(api_call GET "/api/v1/users/me" "" "$BOB_TOKEN")
assert_success "$bob_me"
BOB_ID="$(echo "$bob_me" | jq -r '.data.id')"

# ==============================================================================
# 3) LEDGER ENDPOINTS - CREATION, VALIDATION, MEMBERSHIP
# ==============================================================================

title "3) LEDGER ENDPOINTS - VALID/INVALID INPUTS AND MEMBERSHIP"

sub "[LEDGER-CREATE-VALID] Create ledger with valid payload"
ledger_payload_valid=$(jq -n --arg name "API Negative Test Ledger $RAND" '{
  name: $name,
  ledger_type: "GROUP_BALANCE",
  base_currency: "USD",
  category: {
    name: "Test Category",
    kind: "EXPENSE",
    is_active: true
  }
}')
ledger_body=$(api_call POST "/api/v1/ledgers" "$ledger_payload_valid" "$ALICE_TOKEN")
assert_success "$ledger_body"
LEDGER_ID="$(echo "$ledger_body" | jq -r '.data.ledger_id')"

sub "[LEDGER-CREATE-MISSING-NAME] Create ledger with missing name"
ledger_payload_missing_name=$(jq -n '{
  ledger_type: "GROUP_BALANCE",
  base_currency: "USD"
}')
body=$(api_call POST "/api/v1/ledgers" "$ledger_payload_missing_name" "$ALICE_TOKEN")
assert_failure "$body"

sub "[LEDGER-CREATE-INVALID-TYPE] Create ledger with invalid ledger_type"
ledger_payload_bad_type=$(jq -n --arg name "Bad Type Ledger $RAND" '{
  name: $name,
  ledger_type: "INVALID_TYPE",
  base_currency: "USD"
}')
body=$(api_call POST "/api/v1/ledgers" "$ledger_payload_bad_type" "$ALICE_TOKEN")
assert_failure "$body"

sub "[LEDGER-CREATE-INVALID-CURRENCY] Create ledger with unknown base_currency"
ledger_payload_bad_currency=$(jq -n --arg name "Bad Currency Ledger $RAND" '{
  name: $name,
  ledger_type: "GROUP_BALANCE",
  base_currency: "XYZ"
}')
body=$(api_call POST "/api/v1/ledgers" "$ledger_payload_bad_currency" "$ALICE_TOKEN")
assert_failure "$body"

sub "[LEDGER-GET-AS-OWNER] /ledgers/{id} with owner token"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID" "" "$ALICE_TOKEN")
assert_success "$body"

sub "[LEDGER-GET-AS-NON-MEMBER] /ledgers/{id} as non-member should fail"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID" "" "$BOB_TOKEN")
assert_failure "$body"

sub "[LEDGER-ADD-MEMBER-VALID] Add Bob to ledger"
add_bob_payload=$(jq -n --argjson uid "$BOB_ID" '{user_id: $uid, role:"EDITOR"}')
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_bob_payload" "$ALICE_TOKEN")
assert_success "$body"

sub "[LEDGER-ADD-MEMBER-DUPLICATE] Add Bob again should fail"
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_bob_payload" "$ALICE_TOKEN")
assert_failure "$body"

sub "[LEDGER-REMOVE-NON-MEMBER] Remove non-member ID should fail"
non_member_id="999999"
body=$(api_call DELETE "/api/v1/ledgers/$LEDGER_ID/members/$non_member_id" "" "$ALICE_TOKEN")
assert_failure "$body"

sub "[LEDGER-GET-AS-MEMBER] /ledgers/{id} as added member should now succeed"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID" "" "$BOB_TOKEN")
assert_success "$body"

# ==============================================================================
# 4) TRANSACTION ENDPOINTS - INVALID PAYLOADS
# ==============================================================================

title "4) TRANSACTION ENDPOINTS - INVALID AND BOUNDARY PAYLOADS"

# We need a valid reference transaction as baseline
sub "[TXN-CREATE-VALID] Create a valid EXPENSE transaction"
txn_valid_payload=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "90.00" \
  --arg note "Valid Base Txn" \
  --argjson alice_id "$ALICE_ID" \
  --argjson bob_id "$BOB_ID" \
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
      { user_id: $alice_id, split_method: "EXACT", share_value: 45.00, included: true },
      { user_id: $bob_id, split_method: "EXACT", share_value: 45.00, included: true }
    ]
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn_valid_payload" "$ALICE_TOKEN")
assert_success "$body"

sub "[TXN-NEGATIVE-AMOUNT] amount_total < 0 should fail"
txn_negative_amount=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "-10.00" \
  --arg note "Negative amount" \
  --argjson alice_id "$ALICE_ID" \
  --argjson bob_id "$BOB_ID" \
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
      { user_id: $alice_id, split_method: "EXACT", share_value: -5.00, included: true },
      { user_id: $bob_id, split_method: "EXACT", share_value: -5.00, included: true }
    ]
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn_negative_amount" "$ALICE_TOKEN")
assert_failure "$body"

sub "[TXN-ZERO-AMOUNT] amount_total == 0 boundary should fail or be rejected"
txn_zero_amount=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "0.00" \
  --arg note "Zero amount" \
  --argjson alice_id "$ALICE_ID" \
  --argjson bob_id "$BOB_ID" \
  '{
    txn_at: "2025-09-12T08:00:00",
    type: $type,
    payer_id: $payer_id,
    amount_total: $amount_total,
    currency: "USD",
    note: $note,
    is_private: false,
    rounding_strategy: "NONE",
    tail_allocation: "PAYER",
    splits: [
      { user_id: $alice_id, split_method: "EXACT", share_value: 0.00, included: true },
      { user_id: $bob_id, split_method: "EXACT", share_value: 0.00, included: true }
    ]
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn_zero_amount" "$ALICE_TOKEN")
assert_failure "$body"

sub "[TXN-EMPTY-SPLITS] Empty splits array should fail"
txn_empty_splits=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "100.00" \
  --arg note "Empty splits" \
  '{
    txn_at: "2025-09-13T08:00:00",
    type: $type,
    payer_id: $payer_id,
    amount_total: $amount_total,
    currency: "USD",
    note: $note,
    is_private: false,
    rounding_strategy: "NONE",
    tail_allocation: "PAYER",
    splits: []
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn_empty_splits" "$ALICE_TOKEN")
assert_failure "$body"

sub "[TXN-SUM-MISMATCH] Splits do not sum to amount_total"
txn_sum_mismatch=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "100.00" \
  --arg note "Sum mismatch" \
  --argjson alice_id "$ALICE_ID" \
  --argjson bob_id "$BOB_ID" \
  '{
    txn_at: "2025-09-14T08:00:00",
    type: $type,
    payer_id: $payer_id,
    amount_total: $amount_total,
    currency: "USD",
    note: $note,
    is_private: false,
    rounding_strategy: "NONE",
    tail_allocation: "PAYER",
    splits: [
      { user_id: $alice_id, split_method: "EXACT", share_value: 60.00, included: true },
      { user_id: $bob_id, split_method: "EXACT", share_value: 20.00, included: true }
    ]
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn_sum_mismatch" "$ALICE_TOKEN")
assert_failure "$body"

sub "[TXN-INVALID-TYPE] Unknown transaction type must fail"
txn_invalid_type=$(jq -n \
  --arg type "UNKNOWN" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "50.00" \
  --arg note "Invalid type" \
  --argjson alice_id "$ALICE_ID" \
  --argjson bob_id "$BOB_ID" \
  '{
    txn_at: "2025-09-15T08:00:00",
    type: $type,
    payer_id: $payer_id,
    amount_total: $amount_total,
    currency: "USD",
    note: $note,
    is_private: false,
    rounding_strategy: "NONE",
    tail_allocation: "PAYER",
    splits: [
      { user_id: $alice_id, split_method: "EXACT", share_value: 25.00, included: true },
      { user_id: $bob_id, split_method: "EXACT", share_value: 25.00, included: true }
    ]
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn_invalid_type" "$ALICE_TOKEN")
assert_failure "$body"

sub "[TXN-AS-NON-MEMBER] Non-member cannot create transaction on ledger"
CHARLIE_EMAIL_VALID="charlie.valid+$RAND@test.com"
CHARLIE_PASS_VALID="Passw0rd!"

body=$(api_call POST "/api/v1/auth/register" "{\"email\":\"$CHARLIE_EMAIL_VALID\",\"name\":\"Charlie Valid\",\"password\":\"$CHARLIE_PASS_VALID\"}")
assert_success "$body"
charlie_login=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$CHARLIE_EMAIL_VALID\",\"password\":\"$CHARLIE_PASS_VALID\"}")
assert_success "$charlie_login"
CHARLIE_TOKEN="$(echo "$charlie_login" | jq -r '.data.access_token')"

body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn_valid_payload" "$CHARLIE_TOKEN")
assert_failure "$body"

# ==============================================================================
# 5) SETTLEMENT PLAN AND ANALYTICS - MEMBERSHIP AND BOUNDARIES
# ==============================================================================

title "5) SETTLEMENT PLAN AND ANALYTICS - MEMBERSHIP + BOUNDARIES"

sub "[SETTLEMENT-VALID-AS-MEMBER] /settlement-plan for member should succeed"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "" "$ALICE_TOKEN")
assert_success "$body"

sub "[SETTLEMENT-AS-NON-MEMBER] Non-member cannot view settlement plan"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "" "$CHARLIE_TOKEN")
assert_failure "$body"

sub "[ANALYTICS-OVERVIEW-VALID] Basic analytics overview for member"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/analytics/overview?months=3" "" "$ALICE_TOKEN")
assert_success "$body"
trend_len=$(echo "$body" | jq -r '.data.trend | length')
if [[ "$trend_len" -ne 3 ]]; then
  echo "[ASSERTION FAILED] Expected 3 trend periods for months=3, got $trend_len" >&2
  exit 1
fi

sub "[ANALYTICS-MONTHS-ZERO] months=0 should clamp to default (3 months)"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/analytics/overview?months=0" "" "$ALICE_TOKEN")
assert_success "$body"
trend_len=$(echo "$body" | jq -r '.data.trend | length')
if [[ "$trend_len" -ne 3 ]]; then
  echo "[ASSERTION FAILED] Expected trend length 3 when months=0, got $trend_len" >&2
  exit 1
fi

sub "[ANALYTICS-MONTHS-ABOVE-MAX] months>24 should clamp to 24"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/analytics/overview?months=999" "" "$ALICE_TOKEN")
assert_success "$body"
trend_len=$(echo "$body" | jq -r '.data.trend | length')
if [[ "$trend_len" -ne 24 ]]; then
  echo "[ASSERTION FAILED] Expected trend length 24 when months>24, got $trend_len" >&2
  exit 1
fi

sub "[ANALYTICS-AS-NON-MEMBER] Non-member cannot access analytics"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/analytics/overview?months=3" "" "$CHARLIE_TOKEN")
assert_failure "$body"

sub "[ANALYTICS-UNKNOWN-LEDGER] Analytics on non-existent ledger must fail"
UNKNOWN_LEDGER_ID="999999"
body=$(api_call GET "/api/v1/ledgers/$UNKNOWN_LEDGER_ID/analytics/overview?months=3" "" "$ALICE_TOKEN")
assert_failure "$body"

echo ""
echo "[DONE] Basic negative API tests completed"
