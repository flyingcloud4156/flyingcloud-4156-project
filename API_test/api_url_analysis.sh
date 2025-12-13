#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# FILE: API_test/api_all_curl_only.sh
# PURPOSE:
#   - Call the same APIs as api_all.sh using curl (and jq for parsing) only
#   - No DB reset / no app startup; assumes backend is running with seed data
#   - Every curl command is an independent HTTP request (easy to run step by step)
# REQUIREMENTS:
#   - curl, jq installed
#   - Existing seeded users:
#       alice@gmail.com / bob@gmail.com / charlie@gmail.com / Passw0rd!
# ==============================================================================
export API_HOST=http://136.114.83.248:8081
export PASS=Passw0rd!
export LEDGER_NAME=demoday_$(date '+%Y%m%d_%H%M%S')
echo "Using API_HOST=${API_HOST}"
echo "Using LEDGER_NAME=${LEDGER_NAME}"
# ------------------------------------------------------------------------------
# Step 0: Login as Alice
# ------------------------------------------------------------------------------
echo "Step 0: Login (Alice)"
login_body=$(
  curl -sS -X POST "$API_HOST/api/v1/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"alice@gmail.com\",\"password\":\"$PASS\"}"
)
echo "$login_body" | jq .

ALICE_TOKEN=$(echo "$login_body"   | jq -r '.data.access_token')
ALICE_REFRESH=$(echo "$login_body" | jq -r '.data.refresh_token')

# ------------------------------------------------------------------------------
# Step 1: /users/me  (also capture Alice's user_id)
# ------------------------------------------------------------------------------
echo "Step 1: GET /users/me"
me_body=$(
  curl -sS -X GET "$API_HOST/api/v1/users/me" \
    -H "X-Auth-Token: $ALICE_TOKEN"
)
echo "$me_body" | jq .

ALICE_ID=$(echo "$me_body" | jq -r '.data.id')

# ------------------------------------------------------------------------------
# Step 2: /user-lookup (Bob)
# ------------------------------------------------------------------------------
echo "Step 2: GET /user-lookup for Bob"
lookup_bob=$(
  curl -sS -X GET "$API_HOST/api/v1/user-lookup?email=bob@gmail.com" \
    -H "X-Auth-Token: $ALICE_TOKEN"
)
echo "$lookup_bob" | jq .
BOB_ID=$(echo "$lookup_bob" | jq -r '.data.user_id')

# ------------------------------------------------------------------------------
# Step 3: /user-lookup (Charlie)
# ------------------------------------------------------------------------------
echo "Step 3: GET /user-lookup for Charlie"
lookup_charlie=$(
  curl -sS -X GET "$API_HOST/api/v1/user-lookup?email=charlie@gmail.com" \
    -H "X-Auth-Token: $ALICE_TOKEN"
)
echo "$lookup_charlie" | jq .
CHARLIE_ID=$(echo "$lookup_charlie" | jq -r '.data.user_id')

# ------------------------------------------------------------------------------
# Step 4: /users/{id} (Bob profile)
# ------------------------------------------------------------------------------
echo "Step 4: GET /users/{id} for Bob"
curl -sS -X GET "$API_HOST/api/v1/users/$BOB_ID" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 5: Create Ledger
# ------------------------------------------------------------------------------
echo "Step 5: POST /ledgers (create ledger)"
ledger_body=$(
  curl -sS -X POST "$API_HOST/api/v1/ledgers" \
    -H "Content-Type: application/json" \
    -H "X-Auth-Token: $ALICE_TOKEN" \
    -d @- <<EOF
{
  "name": "$LEDGER_NAME",
  "ledger_type": "GROUP_BALANCE",
  "base_currency": "USD",
  "categories": [
    { "name": "Gas",  "kind": "EXPENSE" },
    { "name": "Food", "kind": "EXPENSE" }
  ]
}
EOF
)

echo "$ledger_body" | jq .
LEDGER_ID=$(echo "$ledger_body" | jq -r '.data.ledger_id')
echo "LEDGER_ID=${LEDGER_ID}"

# ------------------------------------------------------------------------------
# Step 6: /ledgers/mine
# ------------------------------------------------------------------------------
echo "Step 6: GET /ledgers/mine"
curl -sS -X GET "$API_HOST/api/v1/ledgers/mine" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 7: /ledgers/{id}
# ------------------------------------------------------------------------------
echo "Step 7: GET /ledgers/{id}"
curl -sS -X GET "$API_HOST/api/v1/ledgers/$LEDGER_ID" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 8: Add Bob as member
# ------------------------------------------------------------------------------
echo "Step 8: POST /ledgers/{id}/members (add Bob)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/members" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "user_id": "$BOB_ID",
  "role": "EDITOR"
}
EOF

# ------------------------------------------------------------------------------
# Step 9: Add Charlie as member
# ------------------------------------------------------------------------------
echo "Step 9: POST /ledgers/{id}/members (add Charlie)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/members" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "user_id": "$CHARLIE_ID",
  "role": "EDITOR"
}
EOF

# ------------------------------------------------------------------------------
# Step 10: List members
# ------------------------------------------------------------------------------
echo "Step 10: GET /ledgers/{id}/members (list members)"
curl -sS -X GET "$API_HOST/api/v1/ledgers/$LEDGER_ID/members" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 11: Remove Charlie then add again
# ------------------------------------------------------------------------------
echo "Step 11: DELETE /ledgers/{id}/members/{user_id} (remove Charlie)"
curl -sS -X DELETE "$API_HOST/api/v1/ledgers/$LEDGER_ID/members/$CHARLIE_ID" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

echo "Step 11b: POST /ledgers/{id}/members (re-add Charlie)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/members" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "user_id": "$CHARLIE_ID",
  "role": "EDITOR"
}
EOF

# ------------------------------------------------------------------------------
# Step 12: Create EXPENSE transaction (txn1)
# ------------------------------------------------------------------------------
echo "Step 12: POST /transactions (txn1: EXPENSE)"
txn1=$(
  curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/transactions" \
    -H "Content-Type: application/json" \
    -H "X-Auth-Token: $ALICE_TOKEN" \
    -d @- <<EOF
{
  "txn_at": "2025-09-10T08:00:00",
  "type": "EXPENSE",
  "payer_id": "$ALICE_ID",
  "amount_total": 150.00,
  "currency": "USD",
  "note": "Gas and tolls (test)",
  "is_private": false,
  "rounding_strategy": "NONE",
  "tail_allocation": "PAYER",
  "splits": [
    { "user_id": "$ALICE_ID",   "split_method": "EXACT", "share_value": 75.00, "included": true },
    { "user_id": "$BOB_ID",     "split_method": "EXACT", "share_value": 50.00, "included": true },
    { "user_id": "$CHARLIE_ID", "split_method": "EXACT", "share_value": 25.00, "included": true }
  ]
}
EOF
)
echo "$txn1" | jq .
TXN1_ID=$(echo "$txn1" | jq -r '.data.transaction_id')

# ------------------------------------------------------------------------------
# Step 13: Create INCOME transaction (txn2)
# ------------------------------------------------------------------------------
echo "Step 13: POST /transactions (txn2: INCOME)"
txn2=$(
  curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/transactions" \
    -H "Content-Type: application/json" \
    -H "X-Auth-Token: $ALICE_TOKEN" \
    -d @- <<EOF
{
  "txn_at": "2025-09-11T08:00:00",
  "type": "INCOME",
  "payer_id": "$ALICE_ID",
  "amount_total": 100.00,
  "currency": "USD",
  "note": "Refund (test)",
  "is_private": false,
  "rounding_strategy": "NONE",
  "tail_allocation": "PAYER",
  "splits": [
    { "user_id": "$ALICE_ID",   "split_method": "EXACT", "share_value": 34.00, "included": true },
    { "user_id": "$BOB_ID",     "split_method": "EXACT", "share_value": 33.00, "included": true },
    { "user_id": "$CHARLIE_ID", "split_method": "EXACT", "share_value": 33.00, "included": true }
  ]
}
EOF
)
echo "$txn2" | jq .
TXN2_ID=$(echo "$txn2" | jq -r '.data.transaction_id')

# ------------------------------------------------------------------------------
# Step 14: Create EXPENSE transaction (txn3) for trend
# ------------------------------------------------------------------------------
echo "Step 14: POST /transactions (txn3: EXPENSE, for trend)"
txn3=$(
  curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/transactions" \
    -H "Content-Type: application/json" \
    -H "X-Auth-Token: $ALICE_TOKEN" \
    -d @- <<EOF
{
  "txn_at": "2025-10-05T18:00:00",
  "type": "EXPENSE",
  "payer_id": "$BOB_ID",
  "amount_total": 240.00,
  "currency": "USD",
  "note": "Groceries (test)",
  "is_private": false,
  "rounding_strategy": "NONE",
  "tail_allocation": "PAYER",
  "splits": [
    { "user_id": "$ALICE_ID",   "split_method": "EXACT", "share_value": 80.00, "included": true },
    { "user_id": "$BOB_ID",     "split_method": "EXACT", "share_value": 80.00, "included": true },
    { "user_id": "$CHARLIE_ID", "split_method": "EXACT", "share_value": 80.00, "included": true }
  ]
}
EOF
)
echo "$txn3" | jq .
TXN3_ID=$(echo "$txn3" | jq -r '.data.transaction_id')

# ------------------------------------------------------------------------------
# Step 14b: Create INCOME transaction (txn4) 10 USD, percent splits (50/30/20)
# ------------------------------------------------------------------------------
echo "Step 14b: POST /transactions (txn4: INCOME 10 USD, percent 50/30/20)"
txn4=$(
  curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/transactions" \
    -H "Content-Type: application/json" \
    -H "X-Auth-Token: $ALICE_TOKEN" \
    -d @- <<EOF
{
  "txn_at": "2025-10-20T12:00:00",
  "type": "INCOME",
  "payer_id": "$ALICE_ID",
  "amount_total": 10.00,
  "currency": "USD",
  "note": "Income percent demo",
  "is_private": false,
  "rounding_strategy": "NONE",
  "tail_allocation": "PAYER",
  "splits": [
    { "user_id": "$ALICE_ID",   "split_method": "PERCENT", "share_value": 50, "included": true },
    { "user_id": "$BOB_ID",     "split_method": "PERCENT", "share_value": 30, "included": true },
    { "user_id": "$CHARLIE_ID", "split_method": "PERCENT", "share_value": 20, "included": true }
  ]
}
EOF
)
echo "$txn4" | jq .
TXN4_ID=$(echo "$txn4" | jq -r '.data.transaction_id')

# ------------------------------------------------------------------------------
# Step 15: Transaction details for txn1
# ------------------------------------------------------------------------------
echo "Step 15: GET /transactions/{id} (txn1)"
curl -sS -X GET "$API_HOST/api/v1/ledgers/$LEDGER_ID/transactions/$TXN1_ID" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 16: List transactions page=1 size=20
# ------------------------------------------------------------------------------
echo "Step 16: GET /transactions?page=1&size=20"
curl -sS -X GET "$API_HOST/api/v1/ledgers/$LEDGER_ID/transactions?page=1&size=20" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 17: Delete txn2
# ------------------------------------------------------------------------------
echo "Step 17: DELETE /transactions/{id} (txn2)"
curl -sS -X DELETE "$API_HOST/api/v1/ledgers/$LEDGER_ID/transactions/$TXN2_ID" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 18: List transactions again page=1 size=50
# ------------------------------------------------------------------------------
echo "Step 18: GET /transactions?page=1&size=50"
curl -sS -X GET "$API_HOST/api/v1/ledgers/$LEDGER_ID/transactions?page=1&size=50" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 19: Settlement plan
# ------------------------------------------------------------------------------
echo "Step 19: GET /settlement-plan"
curl -sS -X GET "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 20: Analytics overview
# ------------------------------------------------------------------------------
echo "Step 20: GET /analytics/overview?months=3"
curl -sS -X GET "$API_HOST/api/v1/ledgers/$LEDGER_ID/analytics/overview?months=3" \
  -H "X-Auth-Token: $ALICE_TOKEN" | jq .

# ------------------------------------------------------------------------------
# Step 21: POST /settlement-plan with rounding strategy (ROUND_HALF_UP)
# ------------------------------------------------------------------------------
echo "Step 21: POST /settlement-plan (with ROUND_HALF_UP rounding)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "roundingStrategy": "ROUND_HALF_UP"
}
EOF

# ------------------------------------------------------------------------------
# Step 22: POST /settlement-plan with rounding strategy (TRIM_TO_UNIT)
# ------------------------------------------------------------------------------
echo "Step 22: POST /settlement-plan (with TRIM_TO_UNIT rounding)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "roundingStrategy": "TRIM_TO_UNIT"
}
EOF

# ------------------------------------------------------------------------------
# Step 23: POST /settlement-plan with max transfer amount cap
# ------------------------------------------------------------------------------
echo "Step 23: POST /settlement-plan (with max transfer amount cap: 50.00)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "roundingStrategy": "ROUND_HALF_UP",
  "maxTransferAmount": 50.00
}
EOF

# ------------------------------------------------------------------------------
# Step 24: POST /settlement-plan with force min-cost flow algorithm
# ------------------------------------------------------------------------------
echo "Step 24: POST /settlement-plan (force min-cost flow algorithm)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "roundingStrategy": "ROUND_HALF_UP",
  "forceMinCostFlow": true
}
EOF

# ------------------------------------------------------------------------------
# Step 25: POST /settlement-plan with min-cost flow threshold
# ------------------------------------------------------------------------------
echo "Step 25: POST /settlement-plan (with min-cost flow threshold: 5)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "roundingStrategy": "ROUND_HALF_UP",
  "minCostFlowThreshold": 5
}
EOF

# ------------------------------------------------------------------------------
# Step 26: POST /settlement-plan with payment channel constraints
# ------------------------------------------------------------------------------
echo "Step 26: POST /settlement-plan (with payment channel constraints)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "roundingStrategy": "ROUND_HALF_UP",
  "paymentChannels": {
    "${BOB_ID}-${ALICE_ID}": ["VENMO", "PAYPAL"],
    "${CHARLIE_ID}-${ALICE_ID}": ["CASH", "BANK_TRANSFER"]
  }
}
EOF

# ------------------------------------------------------------------------------
# Step 27: POST /settlement-plan with combined options (comprehensive test)
# ------------------------------------------------------------------------------
echo "Step 27: POST /settlement-plan (combined: rounding + cap + threshold)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "roundingStrategy": "ROUND_HALF_UP",
  "maxTransferAmount": 100.00,
  "minCostFlowThreshold": 10,
  "forceMinCostFlow": false
}
EOF

# ------------------------------------------------------------------------------
# Step 28: POST /settlement-plan with NONE rounding (no rounding)
# ------------------------------------------------------------------------------
echo "Step 28: POST /settlement-plan (with NONE rounding strategy)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d @- <<EOF | jq .
{
  "roundingStrategy": "NONE"
}
EOF

# ------------------------------------------------------------------------------
# Step 29: POST /settlement-plan with empty body (should use defaults)
# ------------------------------------------------------------------------------
echo "Step 29: POST /settlement-plan (empty body, should use defaults)"
curl -sS -X POST "$API_HOST/api/v1/ledgers/$LEDGER_ID/settlement-plan" \
  -H "Content-Type: application/json" \
  -H "X-Auth-Token: $ALICE_TOKEN" \
  -d '{}' | jq .

echo "All curl steps completed. (Refresh-token-based logout is deprecated and intentionally skipped.)"