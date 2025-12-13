#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# DEMO SCRIPT (Shrunk & Showy)
#
# What this script demonstrates (service-level, not just a "backend"):
#   1) Auth + token-based calls (multi-user identity)
#   2) Multi-tenant resource provisioning (create a brand-new ledger per run)
#   3) Membership lifecycle (add/list/remove)
#   4) Transaction engine (EXPENSE + INCOME, EXACT + PERCENT splits)
#   5) Analytics & settlement algorithms (parameterized settlement plan)
#   6) Budgeting + alert scenario (budget threshold exceeded)
#
# Key guarantee:
#   - All mutations happen ONLY inside the newly created ledger in this run.
#   - No DB reset, no app startup. Backend must be running with seeded users.
#
# Requirements:
#   - curl, jq
#   - seeded users:
#       alice@gmail.com / bob@gmail.com / charlie@gmail.com / Passw0rd!
# ==============================================================================

# ------------------------------------------------------------------------------
# CONFIG
# ------------------------------------------------------------------------------
export API_HOST="${API_HOST:-http://136.114.83.248:8081}"
export PASS="${PASS:-Passw0rd!}"
export LEDGER_NAME="${LEDGER_NAME:-demoday_$(date '+%Y%m%d_%H%M%S')}"

ALICE_EMAIL="alice@gmail.com"
BOB_EMAIL="bob@gmail.com"
CHARLIE_EMAIL="charlie@gmail.com"

# ------------------------------------------------------------------------------
# Small helpers (reduce repetitive curl boilerplate, keep output readable)
# ------------------------------------------------------------------------------
need() { command -v "$1" >/dev/null 2>&1 || { echo "Missing dependency: $1" >&2; exit 1; }; }
need curl
need jq

banner() {
  echo
  echo "================================================================================"
  echo "$1"
  echo "================================================================================"
}

# curl_json METHOD PATH [TOKEN] [JSON_BODY]
curl_json() {
  local method="$1"
  local path="$2"
  local token="${3:-}"
  local body="${4:-}"

  local url="${API_HOST}${path}"
  local args=(-sS -X "$method" "$url" -H "Accept: application/json")

  if [[ -n "$token" ]]; then
    args+=(-H "X-Auth-Token: $token")
  fi

  if [[ -n "$body" ]]; then
    args+=(-H "Content-Type: application/json" -d "$body")
  fi

  curl "${args[@]}"
}

pretty() { echo "$1" | jq .; }

get_id() {
  # Usage: get_id "$json" ".data.id"
  local json="$1"
  local jq_expr="$2"
  echo "$json" | jq -r "$jq_expr"
}

echo "Using API_HOST=${API_HOST}"
echo "Using LEDGER_NAME=${LEDGER_NAME}"

# ==============================================================================
# SECTION 1 — AUTH & IDENTITY (Service entry point)
#
# This block shows that every downstream capability is protected by auth.
# We perform a login, obtain an access token, and then call /users/me to prove
# token-scoped identity. This is the foundation of a real service (not a scripty
# backend): identity, permissions, and user-scoped resources.
# ==============================================================================
banner "SECTION 1 — AUTH & IDENTITY"

echo "[1.1] Login as Alice"
login_body="$(curl_json POST "/api/v1/auth/login" "" "$(jq -n --arg email "$ALICE_EMAIL" --arg pass "$PASS" '{email:$email,password:$pass}')")"
pretty "$login_body"

ALICE_TOKEN="$(get_id "$login_body" '.data.access_token')"
ALICE_REFRESH="$(get_id "$login_body" '.data.refresh_token')"

if [[ -z "$ALICE_TOKEN" || "$ALICE_TOKEN" == "null" ]]; then
  echo "ERROR: Failed to get access token." >&2
  exit 1
fi

echo "[1.2] Who am I? GET /users/me"
me_body="$(curl_json GET "/api/v1/users/me" "$ALICE_TOKEN")"
pretty "$me_body"
ALICE_ID="$(get_id "$me_body" '.data.id')"

# ==============================================================================
# SECTION 2 — USER DISCOVERY (Collaboration / multi-user service)
#
# A collaboration product typically needs: "invite by email -> resolve to user_id".
# We lookup Bob/Charlie by email, then fetch Bob's profile as an example read.
# This block makes it obvious we are dealing with a multi-user service.
# ==============================================================================
banner "SECTION 2 — USER DISCOVERY (COLLAB)"

echo "[2.1] Lookup Bob by email"
lookup_bob="$(curl_json GET "/api/v1/user-lookup?email=${BOB_EMAIL}" "$ALICE_TOKEN")"
pretty "$lookup_bob"
BOB_ID="$(get_id "$lookup_bob" '.data.user_id')"

echo "[2.2] Lookup Charlie by email"
lookup_charlie="$(curl_json GET "/api/v1/user-lookup?email=${CHARLIE_EMAIL}" "$ALICE_TOKEN")"
pretty "$lookup_charlie"
CHARLIE_ID="$(get_id "$lookup_charlie" '.data.user_id')"

echo "[2.3] Fetch Bob profile (example of user resource read)"
bob_profile="$(curl_json GET "/api/v1/users/${BOB_ID}" "$ALICE_TOKEN")"
pretty "$bob_profile"

# ==============================================================================
# SECTION 3 — LEDGER PROVISIONING (Multi-tenant resource lifecycle)
#
# Here we create a brand-new ledger (isolated namespace) for this demo run.
# This is crucial for self-consistency: every mutation stays within this ledger,
# so we never pollute or affect other ledgers.
#
# We also showcase:
#   - ledger type
#   - base currency
#   - categories
# ==============================================================================
banner "SECTION 3 — LEDGER CREATE"

echo "[3.1] Create a brand-new ledger"
ledger_body="$(
  curl_json POST "/api/v1/ledgers" "$ALICE_TOKEN" "$(
    jq -n --arg name "$LEDGER_NAME" '
    {
      name: $name,
      ledger_type: "GROUP_BALANCE",
      base_currency: "USD",
      categories: [
        { name: "Gas",  kind: "EXPENSE" },
        { name: "Food", kind: "EXPENSE" }
      ]
    }'
  )"
)"
pretty "$ledger_body"
LEDGER_ID="$(get_id "$ledger_body" '.data.ledger_id')"
echo "LEDGER_ID=${LEDGER_ID}"

#echo "[3.2] List my ledgers (proof of user-scoped resources)"
#curl_json GET "/api/v1/ledgers/mine" "$ALICE_TOKEN" | jq .

echo "[3.3] Read ledger detail (the newly created tenant space)"
curl_json GET "/api/v1/ledgers/${LEDGER_ID}" "$ALICE_TOKEN" | jq .

# ==============================================================================
# SECTION 4 — MEMBERSHIP LIFECYCLE (Role-based collaboration)
#
# We demonstrate that a ledger is not single-user:
#   - Add members (Bob + Charlie)
#   - List members
#   - Remove a member (Charlie) to show lifecycle controls
#   - Re-add to continue the demo with 3 participants
# ==============================================================================
banner "SECTION 4 — MEMBERSHIP LIFECYCLE"

echo "[4.1] Add Bob as EDITOR"
curl_json POST "/api/v1/ledgers/${LEDGER_ID}/members" "$ALICE_TOKEN" \
  "$(jq -n --arg uid "$BOB_ID" '{user_id:$uid, role:"EDITOR"}')" | jq .

echo "[4.2] Add Charlie as EDITOR"
curl_json POST "/api/v1/ledgers/${LEDGER_ID}/members" "$ALICE_TOKEN" \
  "$(jq -n --arg uid "$CHARLIE_ID" '{user_id:$uid, role:"EDITOR"}')" | jq .

echo "[4.3] List members"
curl_json GET "/api/v1/ledgers/${LEDGER_ID}/members" "$ALICE_TOKEN" | jq .

echo "[4.4] Remove Charlie (membership lifecycle demo)"
curl_json DELETE "/api/v1/ledgers/${LEDGER_ID}/members/${CHARLIE_ID}" "$ALICE_TOKEN" | jq .

echo "[4.5] Re-add Charlie (continue demo with 3 people)"
curl_json POST "/api/v1/ledgers/${LEDGER_ID}/members" "$ALICE_TOKEN" \
  "$(jq -n --arg uid "$CHARLIE_ID" '{user_id:$uid, role:"EDITOR"}')" | jq .

# ==============================================================================
# SECTION 5 — TRANSACTION ENGINE (Core product value)
#
# This is the "real service" part:
#   - EXPENSE + INCOME
#   - EXACT splits (deterministic)
#   - PERCENT splits (rule-based)
#   - Read-by-id, list pagination, delete
#
# These operations create the financial graph that later feeds analytics and
# settlement algorithms.
# ==============================================================================
banner "SECTION 5 — TRANSACTION"

echo "[5.1] Create txn1: EXPENSE (EXACT splits) - payer Alice"
txn1="$(
  curl_json POST "/api/v1/ledgers/${LEDGER_ID}/transactions" "$ALICE_TOKEN" "$(
    jq -n \
      --arg txn_at "2025-09-10T08:00:00" \
      --arg payer "$ALICE_ID" \
      --arg a "$ALICE_ID" --arg b "$BOB_ID" --arg c "$CHARLIE_ID" \
      '{
        txn_at: $txn_at,
        type: "EXPENSE",
        payer_id: $payer,
        amount_total: 150.00,
        currency: "USD",
        note: "Gas and tolls (demo)",
        is_private: false,
        rounding_strategy: "NONE",
        tail_allocation: "PAYER",
        splits: [
          { user_id: $a, split_method: "EXACT", share_value: 75.00, included: true },
          { user_id: $b, split_method: "EXACT", share_value: 50.00, included: true },
          { user_id: $c, split_method: "EXACT", share_value: 25.00, included: true }
        ]
      }'
  )"
)"
pretty "$txn1"
TXN1_ID="$(get_id "$txn1" '.data.transaction_id')"

echo "[5.2] Create txn2: INCOME (EXACT splits) - payer Alice (we will delete it later)"
txn2="$(
  curl_json POST "/api/v1/ledgers/${LEDGER_ID}/transactions" "$ALICE_TOKEN" "$(
    jq -n \
      --arg txn_at "2025-09-11T08:00:00" \
      --arg payer "$ALICE_ID" \
      --arg a "$ALICE_ID" --arg b "$BOB_ID" --arg c "$CHARLIE_ID" \
      '{
        txn_at: $txn_at,
        type: "INCOME",
        payer_id: $payer,
        amount_total: 100.00,
        currency: "USD",
        note: "Refund (demo, will delete)",
        is_private: false,
        rounding_strategy: "NONE",
        tail_allocation: "PAYER",
        splits: [
          { user_id: $a, split_method: "EXACT", share_value: 34.00, included: true },
          { user_id: $b, split_method: "EXACT", share_value: 33.00, included: true },
          { user_id: $c, split_method: "EXACT", share_value: 33.00, included: true }
        ]
      }'
  )"
)"
pretty "$txn2"
TXN2_ID="$(get_id "$txn2" '.data.transaction_id')"

echo "[5.3] Create txn3: EXPENSE (trend data) - payer Bob"
txn3="$(
  curl_json POST "/api/v1/ledgers/${LEDGER_ID}/transactions" "$ALICE_TOKEN" "$(
    jq -n \
      --arg txn_at "2025-10-05T18:00:00" \
      --arg payer "$BOB_ID" \
      --arg a "$ALICE_ID" --arg b "$BOB_ID" --arg c "$CHARLIE_ID" \
      '{
        txn_at: $txn_at,
        type: "EXPENSE",
        payer_id: $payer,
        amount_total: 240.00,
        currency: "USD",
        note: "Groceries (trend demo)",
        is_private: false,
        rounding_strategy: "NONE",
        tail_allocation: "PAYER",
        splits: [
          { user_id: $a, split_method: "EXACT", share_value: 80.00, included: true },
          { user_id: $b, split_method: "EXACT", share_value: 80.00, included: true },
          { user_id: $c, split_method: "EXACT", share_value: 80.00, included: true }
        ]
      }'
  )"
)"
pretty "$txn3"
TXN3_ID="$(get_id "$txn3" '.data.transaction_id')"

echo "[5.4] Create txn4: INCOME (PERCENT splits 50/30/20) - payer Alice"
txn4="$(
  curl_json POST "/api/v1/ledgers/${LEDGER_ID}/transactions" "$ALICE_TOKEN" "$(
    jq -n \
      --arg txn_at "2025-10-20T12:00:00" \
      --arg payer "$ALICE_ID" \
      --arg a "$ALICE_ID" --arg b "$BOB_ID" --arg c "$CHARLIE_ID" \
      '{
        txn_at: $txn_at,
        type: "INCOME",
        payer_id: $payer,
        amount_total: 10.00,
        currency: "USD",
        note: "Income percent demo",
        is_private: false,
        rounding_strategy: "NONE",
        tail_allocation: "PAYER",
        splits: [
          { user_id: $a, split_method: "PERCENT", share_value: 50, included: true },
          { user_id: $b, split_method: "PERCENT", share_value: 30, included: true },
          { user_id: $c, split_method: "PERCENT", share_value: 20, included: true }
        ]
      }'
  )"
)"
pretty "$txn4"
TXN4_ID="$(get_id "$txn4" '.data.transaction_id')"

echo "[5.5] Read txn1 by id (detail view)"
curl_json GET "/api/v1/ledgers/${LEDGER_ID}/transactions/${TXN1_ID}" "$ALICE_TOKEN" | jq .

echo "[5.6] List transactions"
curl_json GET "/api/v1/ledgers/${LEDGER_ID}/transactions?page=1&size=20" "$ALICE_TOKEN" | jq .

echo "[5.7] Delete txn2"
curl_json DELETE "/api/v1/ledgers/${LEDGER_ID}/transactions/${TXN2_ID}" "$ALICE_TOKEN" | jq .

echo "[5.8] List transactions again (proof of deletion)"
curl_json GET "/api/v1/ledgers/${LEDGER_ID}/transactions?page=1&size=50" "$ALICE_TOKEN" | jq .

# ==============================================================================
# SECTION 6 — ANALYTICS & SETTLEMENT (Service intelligence layer)
#
# This block elevates the demo from "CRUD backend" to an actual financial service:
#   - Analytics overview: aggregated insights over time (months parameter)
#   - Settlement plan: algorithmic output driven by constraints/options
#
# We keep only representative settlement scenarios (avoid repetitive variants):
#   A) Default GET (baseline)
#   B) POST with rounding strategy (business rule)
#   C) POST with max transfer cap (constraint optimization)
#   D) POST with payment channels + min-cost flow (complex constraints)
# ==============================================================================
banner "SECTION 6 — ANALYTICS & SETTLEMENT (ALGORITHMIC SERVICE)"

echo "[6.1] Analytics overview (months=3)"
curl_json GET "/api/v1/ledgers/${LEDGER_ID}/analytics/overview?months=3" "$ALICE_TOKEN" | jq .

echo "[6.2] Settlement plan (baseline GET)"
curl_json GET "/api/v1/ledgers/${LEDGER_ID}/settlement-plan" "$ALICE_TOKEN" | jq .

echo "[6.3] Settlement plan (ROUND_HALF_UP rounding rule)"
curl_json POST "/api/v1/ledgers/${LEDGER_ID}/settlement-plan" "$ALICE_TOKEN" \
  "$(jq -n '{rounding_strategy:"ROUND_HALF_UP"}')" | jq .

echo "[6.4] Settlement plan (ROUND_HALF_UP + max_transfer_amount cap)"
curl_json POST "/api/v1/ledgers/${LEDGER_ID}/settlement-plan" "$ALICE_TOKEN" \
  "$(jq -n '{rounding_strategy:"ROUND_HALF_UP", max_transfer_amount:50.00}')" | jq .

echo "[6.5] Settlement plan (payment channels + min-cost-flow threshold)"
curl_json POST "/api/v1/ledgers/${LEDGER_ID}/settlement-plan" "$ALICE_TOKEN" "$(
  jq -n \
    --arg bob "$BOB_ID" \
    --arg alice "$ALICE_ID" \
    --arg charlie "$CHARLIE_ID" \
    '{
      rounding_strategy:"ROUND_HALF_UP",
      min_cost_flow_threshold: 5,
      payment_channels: {
        ($bob + "-" + $alice): ["VENMO","PAYPAL"],
        ($charlie + "-" + $alice): ["CASH","BANK_TRANSFER"]
      }
    }'
)" | jq .

# ==============================================================================
# SECTION 7 — BUDGETING & ALERT (Policy + monitoring)
#
# A real service has policy enforcement / monitoring. We:
#   - Set a monthly budget (ledger-level)
#   - Query budget status
#   - Create an expense that exceeds the limit to trigger alert logic
# ==============================================================================
banner "SECTION 7 — BUDGETING & ALERT (POLICY & MONITORING)"

echo "[7.1] Set budget for 2025-12 (ledger-level)"
curl_json POST "/api/v1/ledgers/${LEDGER_ID}/budgets" "$ALICE_TOKEN" \
  "$(jq -n '{category_id:null, year:2025, month:12, limit_amount:2000.00}')" | jq .

echo "[7.2] Check budget status for 2025-12"
curl_json GET "/api/v1/ledgers/${LEDGER_ID}/budgets/status?year=2025&month=12" "$ALICE_TOKEN" | jq .

echo "[7.3] Create an EXPENSE to exceed the budget (alert scenario)"
txn_alert="$(
  curl_json POST "/api/v1/ledgers/${LEDGER_ID}/transactions" "$ALICE_TOKEN" "$(
    jq -n \
      --arg txn_at "2025-12-15T12:00:00" \
      --arg payer "$ALICE_ID" \
      '{
        txn_at: $txn_at,
        type: "EXPENSE",
        payer_id: $payer,
        amount_total: 2500.00,
        currency: "USD",
        note: "Expensive dinner (Budget Alert Demo)",
        splits: [
          { user_id: $payer, split_method: "EXACT", share_value: 2500.00, included: true }
        ]
      }'
  )"
)"
pretty "$txn_alert"

echo
echo "✅ Demo completed."
echo "   Created isolated ledger:"
echo "   - LEDGER_NAME=${LEDGER_NAME}"
echo "   - LEDGER_ID=${LEDGER_ID}"
echo
echo "Note: refresh-token-based logout is intentionally skipped (deprecated in this demo context)."