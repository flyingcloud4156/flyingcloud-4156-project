#!/bin/bash
set -euo pipefail
IFS=$'\n\t'

# =======================================================================================
# FILE: ops/test/api_all.sh
# PURPOSE:
#   - Reset local MySQL DB (no docker)
#   - (optional) load big seed for UI charts
#   - Test EVERY API endpoint (auth/user/ledger/txn/settlement/analytics)
#
# USAGE:
#   # 启动 mysql / redis (你已经有了)
#   #   brew services start mysql
#   #   brew services start redis
#   #
#   # 配置数据库环境变量（按你自己的密码改）：
#   #   export DB_HOST="127.0.0.1"
#   #   export DB_PORT="3306"
#   #   export DB_USER="root"
#   #   export DB_PASS="your_mysql_root_password"
#   #
#   # 跑脚本（LOAD_BIG_SEED=1 会加载大量测试数据）
#   #   LOAD_BIG_SEED=1 HOST=http://localhost:8081 bash ops/test/api_all.sh
# =======================================================================================

HOST="${HOST:-http://localhost:8081}"

# 是否加载大号 seed SQL
LOAD_BIG_SEED="${LOAD_BIG_SEED:-0}"

# 数据库配置（默认值可以被环境变量覆盖）
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-}"
DB_NAME="${DB_NAME:-ledger}"

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/../.." && pwd)

DB_SCHEMA_FILE="${PROJECT_ROOT}/ops/sql/ledger_flow.sql"
DB_BIG_SEED_FILE="${PROJECT_ROOT}/ops/sql/backup/ledger_big_seed.sql"

# ---------- command checks ----------
need() { command -v "$1" >/dev/null 2>&1 || { echo "[FATAL] Missing command: $1"; exit 1; }; }
need mysql
need curl
need jq

# ---------- MySQL helpers (本机) ----------
mysql_server_exec() {
  local sql="$1"
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -e "$sql"
}

mysql_db_query() {
  local sql="$1"
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" -N -B "$DB_NAME" -e "$sql"
}

# ---------- pretty prints ----------
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

# ---------- API helper ----------
LAST_HTTP="000"

api_request() {
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
    out=$(curl -sS -X "$method" "$url" "${headers[@]}" -d "$payload" -w $'\n%{http_code}')
  else
    out=$(curl -sS -X "$method" "$url" "${headers[@]}" -w $'\n%{http_code}')
  fi

  LAST_HTTP="$(echo "$out" | tail -n1)"
  echo "$out" | sed '$d'
}

fail_on_non2xx() {
  local code="$LAST_HTTP"
  if [[ "$code" -lt 200 || "$code" -ge 300 ]]; then
    echo "[ERROR] HTTP $code"
    exit 1
  fi
}

fail_if_success_false() {
  local body="$1"
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

assert_not_null() {
  local name="$1"
  local val="${2:-}"
  if [[ -z "$val" || "$val" == "null" ]]; then
    echo "[FATAL] $name is null/empty"
    exit 1
  fi
}

# =======================================================================================
# 0) RESET DB
# =======================================================================================
title "0) RESETTING DATABASE (local MySQL)"

echo "Dropping database $DB_NAME ..."
mysql_server_exec "DROP DATABASE IF EXISTS \`$DB_NAME\`;"

echo "Creating database $DB_NAME ..."
mysql_server_exec "CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

echo "Loading schema: $DB_SCHEMA_FILE"
mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$DB_SCHEMA_FILE"

if [[ "$LOAD_BIG_SEED" == "1" ]]; then
  echo "Loading BIG seed: $DB_BIG_SEED_FILE"
  mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS" "$DB_NAME" < "$DB_BIG_SEED_FILE"
else
  echo "Skipping big seed (LOAD_BIG_SEED=0)"
fi

echo "[OK] DB ready."

# =======================================================================================
# 1) AUTH + USER APIs
# =======================================================================================
title "1) AUTH + USER APIs"

RAND="$(date +%s)"
ALICE_EMAIL="alice+$RAND@test.com"
BOB_EMAIL="bob+$RAND@test.com"
CHARLIE_EMAIL="charlie+$RAND@test.com"
PASS="password"

sub "1.1 Register Alice/Bob/Charlie"
body=$(api_request POST "/api/v1/auth/register" "{\"email\":\"$ALICE_EMAIL\",\"name\":\"Alice-Test\",\"password\":\"$PASS\"}")
fail_on_non2xx; fail_if_success_false "$body"

body=$(api_request POST "/api/v1/auth/register" "{\"email\":\"$BOB_EMAIL\",\"name\":\"Bob-Test\",\"password\":\"$PASS\"}")
fail_on_non2xx; fail_if_success_false "$body"

body=$(api_request POST "/api/v1/auth/register" "{\"email\":\"$CHARLIE_EMAIL\",\"name\":\"Charlie-Test\",\"password\":\"$PASS\"}")
fail_on_non2xx; fail_if_success_false "$body"

sub "1.2 Login Alice"
login_body=$(api_request POST "/api/v1/auth/login" "{\"email\":\"$ALICE_EMAIL\",\"password\":\"$PASS\"}")
fail_on_non2xx; fail_if_success_false "$login_body"
ALICE_TOKEN="$(echo "$login_body" | jq -r '.data.access_token')"
ALICE_REFRESH="$(echo "$login_body" | jq -r '.data.refresh_token')"
assert_not_null "ALICE_TOKEN" "$ALICE_TOKEN"
assert_not_null "ALICE_REFRESH" "$ALICE_REFRESH"

sub "1.3 /users/me"
me_body=$(api_request GET "/api/v1/users/me" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$me_body"
ALICE_ID="$(echo "$me_body" | jq -r '.data.id')"
assert_not_null "ALICE_ID" "$ALICE_ID"

sub "1.4 /users:lookup (lookup Bob by email)"
lookup_body=$(api_request GET "/api/v1/users:lookup?email=$BOB_EMAIL" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$lookup_body"
BOB_ID="$(echo "$lookup_body" | jq -r '.data.user_id')"
assert_not_null "BOB_ID" "$BOB_ID"

sub "1.5 /users/{id} (Bob profile)"
profile_body=$(api_request GET "/api/v1/users/$BOB_ID" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$profile_body"

sub "1.6 refresh token"
refresh_body=$(api_request POST "/api/v1/auth/refresh?refreshToken=$ALICE_REFRESH" "")
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
  base_currency: "USD"
}')
ledger_body=$(api_request POST "/api/v1/ledgers" "$ledger_payload" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$ledger_body"
LEDGER_ID="$(echo "$ledger_body" | jq -r '.data.ledger_id')"
assert_not_null "LEDGER_ID" "$LEDGER_ID"

sub "2.2 Get my ledgers (/ledgers:mine)"
mine_body=$(api_request GET "/api/v1/ledgers:mine" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$mine_body"

sub "2.3 Get ledger details (/ledgers/{id})"
detail_body=$(api_request GET "/api/v1/ledgers/$LEDGER_ID" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$detail_body"

sub "2.4 Add Bob + Charlie as members"
CHARLIE_ID="$(mysql_db_query "SELECT id FROM users WHERE email='$CHARLIE_EMAIL';")"
assert_not_null "CHARLIE_ID" "$CHARLIE_ID"

add_bob=$(jq -n --argjson uid "$BOB_ID" '{user_id:$uid, role:"EDITOR"}')
body=$(api_request POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_bob" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$body"

add_charlie=$(jq -n --argjson uid "$CHARLIE_ID" '{user_id:$uid, role:"EDITOR"}')
body=$(api_request POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_charlie" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$body"

sub "2.5 List members"
members_body=$(api_request GET "/api/v1/ledgers/$LEDGER_ID/members" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$members_body"

sub "2.6 Remove Charlie then add back"
del_body=$(api_request DELETE "/api/v1/ledgers/$LEDGER_ID/members/$CHARLIE_ID" "" "$ALICE_TOKEN")
fail_on_non2xx

add_charlie=$(jq -n --argjson uid "$CHARLIE_ID" '{user_id:$uid, role:"EDITOR"}')
body=$(api_request POST "/api/v1/ledgers/$LEDGER_ID/members" "$add_charlie" "$ALICE_TOKEN")
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
txn_body=$(api_request POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload" "$ALICE_TOKEN")
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
txn_body=$(api_request POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$txn_body"
TXN2_ID="$(echo "$txn_body" | jq -r '.data.transaction_id')"
assert_not_null "TXN2_ID" "$TXN2_ID"

sub "3.3 Create another EXPENSE in Oct (for trend)"
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
txn_body=$(api_request POST "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$txn_body"
TXN3_ID="$(echo "$txn_body" | jq -r '.data.transaction_id')"
assert_not_null "TXN3_ID" "$TXN3_ID"

sub "3.4 Get transaction detail"
get_body=$(api_request GET "/api/v1/ledgers/$LEDGER_ID/transactions/$TXN1_ID" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$get_body"

sub "3.5 List transactions"
list_body=$(api_request GET "/api/v1/ledgers/$LEDGER_ID/transactions?page=1&size=20" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$list_body"

sub "3.6 Delete TXN2 and list again"
del_body=$(api_request DELETE "/api/v1/ledgers/$LEDGER_ID/transactions/$TXN2_ID" "" "$ALICE_TOKEN")
fail_on_non2xx
list_body=$(api_request GET "/api/v1/ledgers/$LEDGER_ID/transactions?page=1&size=50" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$list_body"

# =======================================================================================
# 4) SETTLEMENT PLAN
# =======================================================================================
title "4) SETTLEMENT PLAN API"

sub "4.1 /settlement-plan"
settle_body=$(api_request GET "/api/v1/ledgers/$LEDGER_ID/settlement-plan" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$settle_body"

# =======================================================================================
# 5) ANALYTICS OVERVIEW
# =======================================================================================
title "5) ANALYTICS API"

sub "5.1 /analytics/overview?months=3"
ana_body=$(api_request GET "/api/v1/ledgers/$LEDGER_ID/analytics/overview?months=3" "" "$ALICE_TOKEN")
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
logout_body=$(api_request POST "/api/v1/auth/logout?refreshToken=$ALICE_REFRESH" "" "$ALICE_TOKEN")
fail_on_non2xx; fail_if_success_false "$logout_body"

echo ""
echo "[DONE] ALL API TESTS PASSED ✅"
