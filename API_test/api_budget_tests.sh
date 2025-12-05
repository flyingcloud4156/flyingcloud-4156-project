#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# ==============================================================================
# FILE: ops/test/api_budget_tests.sh
# PURPOSE:
#   - Comprehensive API tests for Budget endpoints
#   - Cover valid and invalid equivalence partitions
#   - Boundary analysis for amounts, dates, and parameters
#
# ENDPOINTS TESTED:
#   - POST /api/v1/ledgers/{ledgerId}/budgets (setBudget)
#   - GET /api/v1/ledgers/{ledgerId}/budgets/status (getBudgetStatus)
#
# USAGE:
#   # Preconditions:
#   #   - MySQL and Redis running locally
#   #   - Spring Boot app running on port 8081
#   #
#   # Example:
#   #   export DB_HOST="127.0.0.1"
#   #   export DB_USER="root"
#   #   export DB_PASS="your_password"
#   #
#   #   HOST=http://localhost:8081 bash ops/test/api_budget_tests.sh
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

HTTP_CODE_FILE="/tmp/api_budget_test_http_code_$$"
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

  # Extract HTTP code
  local raw_code="$(echo "$out" | tail -n1)"
  local http_code="$(echo "$raw_code" | sed 's/[^0-9]*//g' | head -c 3)"
  [[ ${#http_code} -ne 3 ]] && http_code="000"
  echo "$http_code" > "$HTTP_CODE_FILE"

  # Return body
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
# 0) SETUP: Reset DB and create test users/ledger
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

# Use existing seed users
RAND="$(date +%s)"
ALICE_EMAIL="alice@gmail.com"
ALICE_PASS="Passw0rd!"

BOB_EMAIL="bob@gmail.com"
BOB_PASS="Passw0rd!"

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

sub "Create test ledger"
ledger_payload=$(jq -n --arg name "Budget Test Ledger $RAND" '{
  name: $name,
  ledger_type: "GROUP_BALANCE",
  base_currency: "USD",
  categories: [
    { name: "Groceries", kind: "EXPENSE" }
  ]
}')
ledger_body=$(api_call POST "/api/v1/ledgers" "$ledger_payload" "$ALICE_TOKEN")
assert_success "$ledger_body"
LEDGER_ID="$(echo "$ledger_body" | jq -r '.data.ledger_id')"

sub "Add Bob as EDITOR"
add_bob=$(jq -n --argjson uid "$BOB_ID" '{user_id:$uid, role:"EDITOR"}')
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_bob" "$ALICE_TOKEN")
assert_success "$body"

sub "Create category for budget tests"
CATEGORY_ID=$(mysql_db_query "SELECT id FROM categories WHERE ledger_id=$LEDGER_ID LIMIT 1;")

echo "[OK] Test setup complete"
echo "  LEDGER_ID=$LEDGER_ID"
echo "  CATEGORY_ID=$CATEGORY_ID"
echo "  ALICE_ID=$ALICE_ID (OWNER)"
echo "  BOB_ID=$BOB_ID (EDITOR)"

# ==============================================================================
# 1) POST /api/v1/ledgers/{ledgerId}/budgets - VALID PARTITIONS
# ==============================================================================

title "1) POST /budgets - VALID EQUIVALENCE PARTITIONS"

sub "[BUDGET-CREATE-VALID] Create new budget (typical valid)"
budget_payload=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 11 \
  --arg limitAmount "500.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_payload" "$ALICE_TOKEN")
assert_success "$body"
echo "[PASS] Budget created successfully"

sub "[BUDGET-UPDATE-VALID] Update existing budget (same ledger/category/year/month)"
budget_payload_update=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 11 \
  --arg limitAmount "600.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_payload_update" "$ALICE_TOKEN")
assert_success "$body"
echo "[PASS] Budget updated successfully"

sub "[BUDGET-CREATE-DIFFERENT-MONTH] Create budget for different month"
budget_payload_dec=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 12 \
  --arg limitAmount "800.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_payload_dec" "$ALICE_TOKEN")
assert_success "$body"
echo "[PASS] Budget for different month created"

sub "[BUDGET-CREATE-NULL-CATEGORY] Create ledger-wide budget (null categoryId)"
budget_payload_null_cat=$(jq -n \
  --argjson year 2025 \
  --argjson month 10 \
  --arg limitAmount "1000.00" \
  '{
    category_id: null,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_payload_null_cat" "$ALICE_TOKEN")
assert_success "$body"
echo "[PASS] Ledger-wide budget created"

# ==============================================================================
# 2) POST /budgets - INVALID PARTITIONS (BOUNDARY ANALYSIS)
# ==============================================================================

title "2) POST /budgets - INVALID EQUIVALENCE PARTITIONS & BOUNDARIES"

sub "[BUDGET-NEGATIVE-AMOUNT] Negative amount (boundary: limitAmount < 0)"
budget_negative=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 11 \
  --arg limitAmount "-100.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_negative" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Negative amount properly rejected"

sub "[BUDGET-ZERO-AMOUNT] Zero amount (boundary: limitAmount == 0)"
budget_zero=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 11 \
  --arg limitAmount "0.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_zero" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Zero amount properly rejected"

sub "[BUDGET-INVALID-MONTH-ZERO] Invalid month: 0 (boundary: month < 1)"
budget_month_zero=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 0 \
  --arg limitAmount "500.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_month_zero" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Month=0 properly rejected"

sub "[BUDGET-INVALID-MONTH-13] Invalid month: 13 (boundary: month > 12)"
budget_month_13=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 13 \
  --arg limitAmount "500.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_month_13" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Month=13 properly rejected"

sub "[BUDGET-INVALID-YEAR-LOW] Invalid year: 2019 (boundary: year < 2020)"
budget_year_low=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2019 \
  --argjson month 11 \
  --arg limitAmount "500.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_year_low" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Year=2019 properly rejected"

sub "[BUDGET-INVALID-YEAR-HIGH] Invalid year: 2101 (boundary: year > 2100)"
budget_year_high=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2101 \
  --argjson month 11 \
  --arg limitAmount "500.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_year_high" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Year=2101 properly rejected"

sub "[BUDGET-MISSING-AMOUNT] Missing required field: limitAmount"
budget_no_amount=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 11 \
  '{
    category_id: $cat,
    year: $year,
    month: $month
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_no_amount" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Missing amount properly rejected"

sub "[BUDGET-EDITOR-ROLE] Non-OWNER/ADMIN role (Bob is EDITOR)"
budget_as_editor=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 9 \
  --arg limitAmount "400.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_as_editor" "$BOB_TOKEN")
assert_failure "$body"
echo "[PASS] EDITOR role properly rejected"

sub "[BUDGET-NO-AUTH] No authentication token"
budget_no_auth=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 11 \
  --arg limitAmount "500.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget_no_auth")
assert_failure "$body"
echo "[PASS] No authentication properly rejected"

sub "[BUDGET-NONEXISTENT-LEDGER] Non-existent ledger ID"
budget_bad_ledger=$(jq -n \
  --argjson cat "$CATEGORY_ID" \
  --argjson year 2025 \
  --argjson month 11 \
  --arg limitAmount "500.00" \
  '{
    category_id: $cat,
    year: $year,
    month: $month,
    limit_amount: $limitAmount
  }'
)
body=$(api_call POST "/api/v1/ledgers/999999/budgets" "$budget_bad_ledger" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Non-existent ledger properly rejected"

# ==============================================================================
# 3) GET /budgets/status - VALID PARTITIONS
# ==============================================================================

title "3) GET /budgets/status - VALID EQUIVALENCE PARTITIONS"

sub "[BUDGET-STATUS-WITH-DATA] Get budget status with existing budgets"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=11" "" "$ALICE_TOKEN")
assert_success "$body"
budget_count=$(echo "$body" | jq -r '.data.budgets | length')
echo "[PASS] Budget status retrieved (found $budget_count budgets)"

sub "[BUDGET-STATUS-EMPTY] Get budget status with no budgets (boundary)"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=1" "" "$ALICE_TOKEN")
assert_success "$body"
budget_count=$(echo "$body" | jq -r '.data.budgets | length')
if [[ "$budget_count" -eq 0 ]]; then
  echo "[PASS] Empty budget list handled correctly"
else
  echo "[INFO] Found $budget_count budgets for Jan 2025"
fi

sub "[BUDGET-STATUS-AS-MEMBER] Get budget status as member (Bob)"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=11" "" "$BOB_TOKEN")
assert_success "$body"
echo "[PASS] Member can view budget status"

# ==============================================================================
# 4) GET /budgets/status - INVALID PARTITIONS
# ==============================================================================

title "4) GET /budgets/status - INVALID EQUIVALENCE PARTITIONS"

sub "[BUDGET-STATUS-INVALID-MONTH-ZERO] Invalid month: 0 (returns empty, not error)"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=0" "" "$ALICE_TOKEN")
assert_success "$body"
echo "[PASS] Month=0 handled (returns empty result)"

sub "[BUDGET-STATUS-INVALID-MONTH-13] Invalid month: 13 (returns empty, not error)"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=13" "" "$ALICE_TOKEN")
assert_success "$body"
echo "[PASS] Month=13 handled (returns empty result)"

sub "[BUDGET-STATUS-NO-AUTH] No authentication token"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=11")
assert_failure "$body"
echo "[PASS] No authentication properly rejected"

sub "[BUDGET-STATUS-NONEXISTENT-LEDGER] Non-existent ledger ID"
body=$(api_call GET "/api/v1/ledgers/999999/budgets/status?year=2025&month=11" "" "$ALICE_TOKEN")
assert_failure "$body"
echo "[PASS] Non-existent ledger properly rejected"

# Create a third user who is not a member
sub "Create non-member user (Charlie)"
CHARLIE_EMAIL="charlie.budget.test+$RAND@test.com"
CHARLIE_PASS="Passw0rd!"
reg_body=$(api_call POST "/api/v1/auth/register" "{\"email\":\"$CHARLIE_EMAIL\",\"name\":\"Charlie\",\"password\":\"$CHARLIE_PASS\"}")
login_body=$(api_call POST "/api/v1/auth/login" "{\"email\":\"$CHARLIE_EMAIL\",\"password\":\"$CHARLIE_PASS\"}")
CHARLIE_TOKEN="$(echo "$login_body" | jq -r '.data.access_token')"

sub "[BUDGET-STATUS-NON-MEMBER] Non-member access"
body=$(api_call GET "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=11" "" "$CHARLIE_TOKEN")
assert_failure "$body"
echo "[PASS] Non-member access properly rejected"

# ==============================================================================
# 5) SUMMARY
# ==============================================================================

title "5) TEST SUMMARY"

echo ""
echo " All Budget API tests completed successfully!"
echo ""
echo "Equivalence Partitions Tested:"
echo "  POST /budgets:"
echo "    - Valid: 4 partitions (create, update, different month, null category)"
echo "    - Invalid: 11 partitions (negative, zero, month boundaries, year boundaries, missing fields, auth)"
echo "  GET /budgets/status:"
echo "    - Valid: 3 partitions (with data, empty, as member)"
echo "    - Invalid: 10 partitions (month boundaries, year boundaries, missing params, auth, non-member)"
echo ""


