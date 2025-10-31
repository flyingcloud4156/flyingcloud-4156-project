#!/bin/bash
# =======================================================================================
# API TEST: EXPENSE TRANSACTIONS (PERCENT SPLIT) - MULTI TX + NETTING + ROUNDING (EN)
# =======================================================================================
# Scenario:
#   - Ledger: "Investment Club" (GROUP_BALANCE)
#   - Users: Alice, Bob, Charlie
#   - Txn #1: Alice pays 1000.00, PERCENT: Alice 50%, Bob 30%, Charlie 20%
#   - Txn #2: Bob   pays 255.55, PERCENT: Alice 20%, Bob 40%, Charlie 40% (forces decimals)
#   - Txn #3: Charlie pays 100.00, PERCENT: Alice 33.33%, Bob 33.33%, Charlie 33.34% (tail split)
#
# We test:
#   1) Per-transaction split amounts (computed_amount on transaction_splits).
#   2) Cross-transaction netting of debts using debt_edges across the same ledger.
#   3) User net positions = (as creditor) - (as debtor), which should match Paid - OwedShare.
#
# Expected pairwise net (positive means "second owes first"):
#   - (Alice, Bob)     = 248.89  => Bob owes Alice 248.89
#   - (Alice, Charlie) = 166.67  => Charlie owes Alice 166.67
#   - (Bob,   Charlie) =  68.89  => Charlie owes Bob 68.89
#
# Expected user net positions (positive means others owe this user):
#   - Alice   : +415.56
#   - Bob     : -180.00
#   - Charlie : -235.56
#
# Assumptions:
#   - MySQL runs in Docker container named "mysql" (root/root).
#   - API service is reachable at http://localhost:8081 .
#   - Schema and seed SQL exist at: ops/sql/ledger_flow.sql and ops/sql/backup/ledger.sql
#   - jq and curl are installed.
# =======================================================================================

set -euo pipefail
IFS=$'\n\t'

# --- Config ---------------------------------------------------------------------------
HOST="http://localhost:8081"

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)
DB_SCHEMA_FILE="${PROJECT_ROOT}/ops/sql/ledger_flow.sql"
DB_SEED_FILE="${PROJECT_ROOT}/ops/sql/backup/ledger.sql"

# --- MySQL helper (Docker) ------------------------------------------------------------
mysql_exec() { docker exec -i mysql mysql -uroot -proot "$@"; }

# --- Pretty printing ------------------------------------------------------------------
echo_title() {
  echo ""
  echo "======================================================================================="
  echo "=> $1"
  echo "======================================================================================="
}
echo_subtitle() { echo -e "\n--- $1"; }
echo_api_call() {
  echo "" >&2
  echo "┌─────────────────────────────────────────────────────────────────────────────────────┐" >&2
  echo "│ API REQUEST                                                                         │" >&2
  echo "└─────────────────────────────────────────────────────────────────────────────────────┘" >&2
}
echo_request_details() {
  local method="$1"; local path="$2"; local payload="$3"; local token="${4:-}"
  echo "METHOD: $method" >&2
  echo "URL:    $HOST$path" >&2
  [[ -n "$token" ]] && echo "TOKEN:  ${token:0:20}... truncated" >&2 || echo "TOKEN:  none" >&2
  echo "" >&2
  echo "📦 REQUEST BODY:" >&2
  echo "$payload" | jq '.' >&2
  echo "" >&2
}

# --- General helpers ------------------------------------------------------------------
fail_if_false() {
  local resp="$1"
  local ok
  ok=$(echo "$resp" | jq -er '.success') || ok="false"
  if [[ "$ok" != "true" ]]; then
    echo "[ERROR] API call failed. Response:"
    echo "$resp" | jq .
    exit 1
  fi
}
api_post() {
  local path="$1"; local payload="$2"; local token="${3:-}"
  echo_api_call; echo_request_details "POST" "$path" "$payload" "$token"
  local headers=(-H "Content-Type: application/json")
  [[ -n "$token" ]] && headers+=(-H "X-Auth-Token: $token")
  echo "Sending request..." >&2
  local response
  response=$(curl -sS -X POST "$HOST$path" "${headers[@]}" -d "$payload" 2>&1)
  echo "[OK] Response received (RAW):" >&2
  echo "$response" >&2
  echo "" >&2
  if echo "$response" | jq '.' >/dev/null 2>&1; then
    echo "📋 Response (formatted):" >&2
    echo "$response" | jq '.' >&2
  else
    echo "⚠️  Response is not valid JSON (network error or server error?)" >&2
  fi
  echo "$response"
}
assert_not_null() {
  if [[ -z "${2:-""}" || "${2:-""}" == "null" ]]; then
    echo "[ERROR] Expected '$1' to be non-empty, but it was null/empty."
    exit 1
  fi
}
# Float closeness assertion (|a-b| <= tolerance; default 0.01)
assert_float_close() {
  local label="$1"; local got="$2"; local expect="$3"; local tol="${4:-0.01}"
  awk -v g="$got" -v e="$expect" -v t="$tol" -v L="$label" 'BEGIN{
    diff=(g-e); if (diff<0) diff=-diff;
    if (diff>t) { printf("[ASSERT FAIL] %s: got=%.2f expect=%.2f (tol=%.2f)\n", L, g, e, t); exit 1 }
    else        { printf("[ASSERT OK]    %s: got=%.2f ≈ %.2f\n", L, g, e) }
  }'
}

# --- Debt querying helpers -------------------------------------------------------------
# pair_net_amount(ledger_id, creditor_id, debtor_id) returns:
#   SUM(creditor->debtor) - SUM(debtor->creditor) over all transactions in the ledger,
#   rounded to 2 decimals. Positive => debtor owes creditor net.
pair_net_amount() {
  local ledger_id="$1"; local creditor="$2"; local debtor="$3"
  mysql_exec -N -D ledger -e "
    SELECT ROUND(
      IFNULL(SUM(CASE WHEN de.from_user_id=${creditor} AND de.to_user_id=${debtor} THEN de.amount END),0)
      -
      IFNULL(SUM(CASE WHEN de.from_user_id=${debtor}   AND de.to_user_id=${creditor} THEN de.amount END),0)
    , 2) AS net_amt
    FROM debt_edges de
    JOIN transactions t ON t.id = de.transaction_id
    WHERE t.ledger_id=${ledger_id};
  "
}

# user_net_position(ledger_id, user_id) returns:
#   SUM(as creditor) - SUM(as debtor) across the ledger, rounded to 2 decimals.
user_net_position() {
  local ledger_id="$1"; local uid="$2"
  mysql_exec -N -D ledger -e "
    SELECT ROUND(
      IFNULL(SUM(CASE WHEN de.from_user_id=${uid} THEN de.amount END),0)
      -
      IFNULL(SUM(CASE WHEN de.to_user_id=${uid}   THEN de.amount END),0)
    , 2) AS net_pos
    FROM debt_edges de
    JOIN transactions t ON t.id = de.transaction_id
    WHERE t.ledger_id=${ledger_id};
  "
}

# --- Globals --------------------------------------------------------------------------
ALICE_TOKEN=""
ALICE_ID=""; BOB_ID=""; CHARLIE_ID=""
LEDGER_ID=""
TXN1_ID=""; TXN2_ID=""; TXN3_ID=""

# =======================================================================================
# 0) RESET DB
# =======================================================================================
echo_title "0. RESETTING DATABASE"
mysql_exec -e "DROP DATABASE IF EXISTS ledger;" 2>/dev/null || true
mysql_exec -e "CREATE DATABASE ledger;"
mysql_exec ledger < "$DB_SCHEMA_FILE"
mysql_exec ledger < "$DB_SEED_FILE"
echo "[OK] Database reset."

# =======================================================================================
# 1) SETUP: USERS, LOGIN, LEDGER, MEMBERS
# =======================================================================================
echo_title "1. SETUP: USERS, LOGIN, LEDGER & MEMBERS"

echo_subtitle "1.1 Register users"
api_post "/api/v1/auth/register" '{"email":"alice@test.com","name":"Alice","password":"password"}' >/dev/null
api_post "/api/v1/auth/register" '{"email":"bob@test.com","name":"Bob","password":"password"}'       >/dev/null
api_post "/api/v1/auth/register" '{"email":"charlie@test.com","name":"Charlie","password":"password"}' >/dev/null

echo_subtitle "1.2 Login as Alice"
login_resp=$(api_post "/api/v1/auth/login" '{"email":"alice@test.com","password":"password"}')
fail_if_false "$login_resp"
ALICE_TOKEN=$(echo "$login_resp" | jq -r '.data.access_token'); assert_not_null "ALICE_TOKEN" "$ALICE_TOKEN"

ALICE_ID=$(mysql_exec -N -D ledger -e "SELECT id FROM users WHERE email='alice@test.com';")
BOB_ID=$(mysql_exec   -N -D ledger -e "SELECT id FROM users WHERE email='bob@test.com';")
CHARLIE_ID=$(mysql_exec -N -D ledger -e "SELECT id FROM users WHERE email='charlie@test.com';")
assert_not_null "ALICE_ID" "$ALICE_ID"
assert_not_null "BOB_ID" "$BOB_ID"
assert_not_null "CHARLIE_ID" "$CHARLIE_ID"
echo "[OK] User IDs: Alice=$ALICE_ID, Bob=$BOB_ID, Charlie=$CHARLIE_ID"

echo_subtitle "1.3 Create ledger 'Investment Club'"
ledger_payload=$(jq -n --arg name "Investment Club" '{name:$name, ledger_type:"GROUP_BALANCE", base_currency:"USD"}')
ledger_resp=$(api_post "/api/v1/ledgers" "$ledger_payload" "$ALICE_TOKEN")
fail_if_false "$ledger_resp"
LEDGER_ID=$(echo "$ledger_resp" | jq -r '.data.ledger_id'); assert_not_null "LEDGER_ID" "$LEDGER_ID"
echo "[OK] Ledger created: $LEDGER_ID"

echo_subtitle "1.4 Add members Bob & Charlie"
api_post "/api/v1/ledgers/$LEDGER_ID/members" "$(jq -n --argjson uid "$BOB_ID" '{user_id:$uid, role:"EDITOR"}')" "$ALICE_TOKEN" >/dev/null
api_post "/api/v1/ledgers/$LEDGER_ID/members" "$(jq -n --argjson uid "$CHARLIE_ID" '{user_id:$uid, role:"EDITOR"}')" "$ALICE_TOKEN" >/dev/null
echo "[OK] Members added."

# =======================================================================================
# 2) CREATE MULTIPLE EXPENSE TRANSACTIONS (PERCENT + ROUNDING)
# =======================================================================================
echo_title "2. CREATE MULTI TRANSACTIONS (PERCENT + ROUNDING)"

# Txn #1: Alice pays 1000.00 (50/30/20)
echo_subtitle "2.1 Txn1: Alice pays 1000.00 (50/30/20)"
payload1=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$ALICE_ID" \
  --arg amount_total "1000.00" \
  --arg note "Group investment #1" \
  --argjson A "$ALICE_ID" --argjson B "$BOB_ID" --argjson C "$CHARLIE_ID" \
  '{
    txn_at:"2025-10-22T10:00:00", type:$type, payer_id:$payer_id,
    amount_total:$amount_total, currency:"USD", note:$note,
    is_private:false, rounding_strategy:"ROUND_HALF_UP", tail_allocation:"LARGEST_SHARE",
    splits:[
      {user_id:$A, split_method:"PERCENT", share_value:50.0, included:true},
      {user_id:$B, split_method:"PERCENT", share_value:30.0, included:true},
      {user_id:$C, split_method:"PERCENT", share_value:20.0, included:true}
    ]
  }')
resp1=$(api_post "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload1" "$ALICE_TOKEN"); fail_if_false "$resp1"
TXN1_ID=$(echo "$resp1" | jq -r '.data.transaction_id'); assert_not_null "TXN1_ID" "$TXN1_ID"

# Txn #2: Bob pays 255.55 (20/40/40)
echo_subtitle "2.2 Txn2: Bob pays 255.55 (20/40/40)"
payload2=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$BOB_ID" \
  --arg amount_total "255.55" \
  --arg note "Group investment #2" \
  --argjson A "$ALICE_ID" --argjson B "$BOB_ID" --argjson C "$CHARLIE_ID" \
  '{
    txn_at:"2025-10-22T12:00:00", type:$type, payer_id:$payer_id,
    amount_total:$amount_total, currency:"USD", note:$note,
    is_private:false, rounding_strategy:"ROUND_HALF_UP", tail_allocation:"LARGEST_SHARE",
    splits:[
      {user_id:$A, split_method:"PERCENT", share_value:20.0, included:true},
      {user_id:$B, split_method:"PERCENT", share_value:40.0, included:true},
      {user_id:$C, split_method:"PERCENT", share_value:40.0, included:true}
    ]
  }')
resp2=$(api_post "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload2" "$ALICE_TOKEN"); fail_if_false "$resp2"
TXN2_ID=$(echo "$resp2" | jq -r '.data.transaction_id'); assert_not_null "TXN2_ID" "$TXN2_ID"

# Txn #3: Charlie pays 100.00 (33.33/33.33/33.34)
echo_subtitle "2.3 Txn3: Charlie pays 100.00 (33.33/33.33/33.34)"
payload3=$(jq -n \
  --arg type "EXPENSE" \
  --argjson payer_id "$CHARLIE_ID" \
  --arg amount_total "100.00" \
  --arg note "Group investment #3" \
  --argjson A "$ALICE_ID" --argjson B "$BOB_ID" --argjson C "$CHARLIE_ID" \
  '{
    txn_at:"2025-10-22T18:00:00", type:$type, payer_id:$payer_id,
    amount_total:$amount_total, currency:"USD", note:$note,
    is_private:false, rounding_strategy:"ROUND_HALF_UP", tail_allocation:"LARGEST_SHARE",
    splits:[
      {user_id:$A, split_method:"PERCENT", share_value:33.33, included:true},
      {user_id:$B, split_method:"PERCENT", share_value:33.33, included:true},
      {user_id:$C, split_method:"PERCENT", share_value:33.34, included:true}
    ]
  }')
resp3=$(api_post "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload3" "$ALICE_TOKEN"); fail_if_false "$resp3"
TXN3_ID=$(echo "$resp3" | jq -r '.data.transaction_id'); assert_not_null "TXN3_ID" "$TXN3_ID"

echo "[OK] All 3 transactions created: $TXN1_ID, $TXN2_ID, $TXN3_ID"

# =======================================================================================
# 3) DB CHECKS PER TRANSACTION
# =======================================================================================
echo_title "3. PER-TRANSACTION DB CHECKS"

echo_subtitle "3.1 Txn1 splits (expect A=500.00, B=300.00, C=200.00)"
mysql_exec -D ledger -e "
  SELECT user_id, split_method, share_value, computed_amount
  FROM transaction_splits WHERE transaction_id=${TXN1_ID}
  ORDER BY user_id;" --table

echo_subtitle "3.2 Txn2 splits (expect A=51.11, B=102.22, C=102.22)"
mysql_exec -D ledger -e "
  SELECT user_id, split_method, share_value, computed_amount
  FROM transaction_splits WHERE transaction_id=${TXN2_ID}
  ORDER BY user_id;" --table

echo_subtitle "3.3 Txn3 splits (expect A=33.33, B=33.33, C=33.34)"
mysql_exec -D ledger -e "
  SELECT user_id, split_method, share_value, computed_amount
  FROM transaction_splits WHERE transaction_id=${TXN3_ID}
  ORDER BY user_id;" --table

echo_subtitle "3.4 Debt edges snapshot (all rows)"
mysql_exec -D ledger -e "
  SELECT de.transaction_id, de.from_user_id AS creditor, de.to_user_id AS debtor, de.amount
  FROM debt_edges de
  JOIN transactions t ON t.id = de.transaction_id
  WHERE t.ledger_id=${LEDGER_ID}
  ORDER BY transaction_id, creditor, debtor;" --table

# =======================================================================================
# 4) CROSS-TRANSACTION NETTING ASSERTIONS (CRITICAL)
# =======================================================================================
echo_title "4. CROSS-TRANSACTION NETTING ASSERTIONS"

# Pairwise nets: positive means "second owes first"
NET_AB=$(pair_net_amount "$LEDGER_ID" "$ALICE_ID" "$BOB_ID")      # Bob owes Alice if positive
NET_AC=$(pair_net_amount "$LEDGER_ID" "$ALICE_ID" "$CHARLIE_ID")  # Charlie owes Alice if positive
NET_BC=$(pair_net_amount "$LEDGER_ID" "$BOB_ID"   "$CHARLIE_ID")  # Charlie owes Bob if positive

assert_float_close "Net(Alice,Bob)     [Bob owes Alice]"     "$NET_AB" "248.89" "0.01"
assert_float_close "Net(Alice,Charlie) [Charlie owes Alice]" "$NET_AC" "166.67" "0.01"
assert_float_close "Net(Bob,Charlie)   [Charlie owes Bob]"   "$NET_BC" "68.89"  "0.01"

# User net positions = sum(as creditor) - sum(as debtor)
POS_A=$(user_net_position "$LEDGER_ID" "$ALICE_ID")
POS_B=$(user_net_position "$LEDGER_ID" "$BOB_ID")
POS_C=$(user_net_position "$LEDGER_ID" "$CHARLIE_ID")

assert_float_close "User Net Position - Alice"   "$POS_A" "415.56"  "0.01"
assert_float_close "User Net Position - Bob"     "$POS_B" "-180.00" "0.01"
assert_float_close "User Net Position - Charlie" "$POS_C" "-235.56" "0.01"

echo_subtitle "4.1 Summary (calculated)"
echo "  Pairwise net (positive means: second owes first):"
printf "    (Alice,  Bob)     :  \$%.2f   => Bob owes Alice\n"     "$NET_AB"
printf "    (Alice,  Charlie) :  \$%.2f   => Charlie owes Alice\n" "$NET_AC"
printf "    (Bob,    Charlie) :  \$%.2f   => Charlie owes Bob\n"   "$NET_BC"
echo "  User positions (positive means: others owe this user):"
printf "    Alice   : \$%.2f\n" "$POS_A"
printf "    Bob     : \$%.2f\n" "$POS_B"
printf "    Charlie : \$%.2f\n" "$POS_C"

echo -e "\n[DONE] MULTI-TX + NETTING + ROUNDING TEST PASSED ✅\n"