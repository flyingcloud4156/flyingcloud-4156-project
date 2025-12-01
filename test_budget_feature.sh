#!/bin/bash

# =======================================================================================
# API TEST: BUDGET MANAGEMENT & ALERTS
# =======================================================================================

set -euo pipefail
IFS=$'\n\t'

# --- Configuration ---
HOST="http://localhost:8081"
DB_SCHEMA_FILE="/Users/jinyiwang/Desktop/4156project-final/flyingcloud-4156-project/ops/sql/ledger_flow.sql"
DB_SEED_FILE="/Users/jinyiwang/Desktop/4156project-final/flyingcloud-4156-project/ops/sql/backup/ledger.sql"

# --- Helper Functions ---

echo_title() {
    echo ""
    echo "======================================================================================="
    echo "=> $1"
    echo "======================================================================================="
}

echo_subtitle() {
    echo ""
    echo "--- $1"
}

fail_if_false() {
    local resp="$1"
    local ok
    ok=$(echo "$resp" | jq -er '.success') || ok="false"
    if [[ "$ok" != "true" ]]; then
        echo " API call failed. Response:"
        echo "$resp" | jq .
        exit 1
    fi
}

api_post() {
    local path="$1"
    local payload="$2"
    local token="${3:-}"
    local headers=(-H "Content-Type: application/json")
    if [[ -n "$token" ]]; then
        headers+=(-H "X-Auth-Token: $token")
    fi
    curl -sS -X POST "$HOST$path" "${headers[@]}" -d "$payload"
}

api_get() {
    local path="$1"
    local token="${2:-}"
    local headers=()
    if [[ -n "$token" ]]; then
        headers+=(-H "X-Auth-Token: $token")
    fi
    curl -sS -X GET "$HOST$path" "${headers[@]}"
}

assert_not_null() {
    if [[ -z "${2:-}" || "${2:-}" == "null" ]]; then
        echo " FATAL: Expected '$1' to have a value, but it was null or empty."
        exit 1
    fi
}

# --- Global Variables ---
ALICE_TOKEN=""
ALICE_ID=""
LEDGER_ID=""

# =======================================================================================
# 0. RESETTING DATABASE TO CLEAN STATE
# =======================================================================================
echo_title "0. RESETTING DATABASE TO CLEAN STATE"

echo " Dropping existing database..."
mysql -u root -e "DROP DATABASE IF EXISTS ledger;" 2>/dev/null || true

echo " Creating new database..."
mysql -u root -e "CREATE DATABASE ledger;"

echo " Loading schema..."
mysql -u root ledger < "$DB_SCHEMA_FILE"

echo " Loading seed data..."
mysql -u root ledger < "$DB_SEED_FILE"

echo " Database reset successfully!"

# =======================================================================================
# 1. SETUP: REGISTER USER, LOGIN, AND CREATE LEDGER
# =======================================================================================
echo_title "1. SETUP: REGISTER USER, LOGIN, AND CREATE LEDGER"

# Register Alice
echo_subtitle "1.1 Registering Alice"
api_post "/api/v1/auth/register" '{"email":"alice@test.com", "name":"Alice", "password":"password"}' > /dev/null
echo " Alice registered."

# Login as Alice
echo_subtitle "1.2 Logging in as Alice"
login_resp=$(api_post "/api/v1/auth/login" '{"email":"alice@test.com", "password":"password"}')
fail_if_false "$login_resp"
ALICE_TOKEN=$(echo "$login_resp" | jq -r '.data.access_token')
assert_not_null "ALICE_TOKEN" "$ALICE_TOKEN"
echo " Alice logged in successfully. Token retrieved."

# Get Alice's user ID from database
ALICE_ID=$(mysql -u root -D ledger -ss -e "SELECT id FROM users WHERE email='alice@test.com';")
assert_not_null "ALICE_ID" "$ALICE_ID"
echo " User ID retrieved: Alice=$ALICE_ID"

# Create a new Ledger
echo_subtitle "1.3 Creating 'Budget Test Ledger'"
ledger_payload=$(jq -n --arg name "Budget Test Ledger" '{name:$name, ledger_type:"GROUP_BALANCE", base_currency:"USD"}')
ledger_resp=$(api_post "/api/v1/ledgers" "$ledger_payload" "$ALICE_TOKEN")
fail_if_false "$ledger_resp"
LEDGER_ID=$(echo "$ledger_resp" | jq -r '.data.ledger_id')
assert_not_null "LEDGER_ID" "$LEDGER_ID"
echo " Ledger created with ID: $LEDGER_ID"

# =======================================================================================
# 2. SET BUDGETS
# =======================================================================================
echo_title "2. SET BUDGETS"

# Set ledger-level budget
echo_subtitle "2.1 Setting ledger-level budget (1000 USD for Dec 2025)"
budget1_payload=$(jq -n '{category_id:null, year:2025, month:12, limit_amount:1000.00}')
budget1_resp=$(api_post "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget1_payload" "$ALICE_TOKEN")
fail_if_false "$budget1_resp"
echo " Ledger-level budget set."

# Set category-specific budget
echo_subtitle "2.2 Setting category-specific budget (500 USD for category 1, Dec 2025)"
budget2_payload=$(jq -n '{category_id:1, year:2025, month:12, limit_amount:500.00}')
budget2_resp=$(api_post "/api/v1/ledgers/$LEDGER_ID/budgets" "$budget2_payload" "$ALICE_TOKEN")
fail_if_false "$budget2_resp"
echo " Category-specific budget set."

# =======================================================================================
# 3. QUERY BUDGET STATUS (NO SPENDING YET)
# =======================================================================================
echo_title "3. QUERY BUDGET STATUS (NO SPENDING YET)"

status_resp=$(api_get "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=12" "$ALICE_TOKEN")
fail_if_false "$status_resp"
echo "Budget status:"
echo "$status_resp" | jq '.data.items[] | {budget_id, category_id, limit_amount, spent_amount, status}'

# =======================================================================================
# 4. CREATE TRANSACTIONS AND CHECK ALERTS
# =======================================================================================
echo_title "4. CREATE TRANSACTIONS AND CHECK ALERTS"

# Transaction 1: Small expense (should be OK, 20%)
echo_subtitle "4.1 Creating small expense (100 USD)"
txn1_payload=$(jq -n \
    --argjson payer_id "$ALICE_ID" \
    '{
        txn_at: "2025-12-01T10:00:00",
        type: "EXPENSE",
        category_id: 1,
        payer_id: $payer_id,
        amount_total: 100.00,
        currency: "USD",
        note: "Small expense",
        is_private: false,
        rounding_strategy: "ROUND_HALF_UP",
        tail_allocation: "PAYER",
        splits: [{user_id: $payer_id, split_method: "EQUAL", share_value: 0, included: true}]
    }')
txn1_resp=$(api_post "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn1_payload" "$ALICE_TOKEN")
fail_if_false "$txn1_resp"
txn1_alert=$(echo "$txn1_resp" | jq -r '.data.budget_alert // "No alert"')
echo " Transaction 1 created. Budget alert: $txn1_alert"

# Transaction 2: Large expense (should trigger NEAR_LIMIT, 90%)
echo_subtitle "4.2 Creating large expense (350 USD)"
txn2_payload=$(jq -n \
    --argjson payer_id "$ALICE_ID" \
    '{
        txn_at: "2025-12-02T10:00:00",
        type: "EXPENSE",
        category_id: 1,
        payer_id: $payer_id,
        amount_total: 350.00,
        currency: "USD",
        note: "Large expense approaching limit",
        is_private: false,
        rounding_strategy: "ROUND_HALF_UP",
        tail_allocation: "PAYER",
        splits: [{user_id: $payer_id, split_method: "EQUAL", share_value: 0, included: true}]
    }')
txn2_resp=$(api_post "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn2_payload" "$ALICE_TOKEN")
fail_if_false "$txn2_resp"
txn2_alert=$(echo "$txn2_resp" | jq -r '.data.budget_alert // "No alert"')
echo " Transaction 2 created. Budget alert: $txn2_alert"

# Transaction 3: Exceeding budget (should trigger EXCEEDED, 130%)
echo_subtitle "4.3 Creating expense exceeding budget (200 USD)"
txn3_payload=$(jq -n \
    --argjson payer_id "$ALICE_ID" \
    '{
        txn_at: "2025-12-03T10:00:00",
        type: "EXPENSE",
        category_id: 1,
        payer_id: $payer_id,
        amount_total: 200.00,
        currency: "USD",
        note: "Exceeding budget",
        is_private: false,
        rounding_strategy: "ROUND_HALF_UP",
        tail_allocation: "PAYER",
        splits: [{user_id: $payer_id, split_method: "EQUAL", share_value: 0, included: true}]
    }')
txn3_resp=$(api_post "/api/v1/ledgers/$LEDGER_ID/transactions" "$txn3_payload" "$ALICE_TOKEN")
fail_if_false "$txn3_resp"
txn3_alert=$(echo "$txn3_resp" | jq -r '.data.budget_alert // "No alert"')
echo " Transaction 3 created. Budget alert: $txn3_alert"

# =======================================================================================
# 5. QUERY FINAL BUDGET STATUS
# =======================================================================================
echo_title "5. QUERY FINAL BUDGET STATUS"

final_status_resp=$(api_get "/api/v1/ledgers/$LEDGER_ID/budgets/status?year=2025&month=12" "$ALICE_TOKEN")
fail_if_false "$final_status_resp"
echo "Final budget status:"
echo "$final_status_resp" | jq '.data.items[] | {budget_id, category_id, category_name, limit_amount, spent_amount, ratio, status}'

# =======================================================================================
# 6. DATABASE VERIFICATION
# =======================================================================================
echo_title "6. VERIFYING DATABASE STATE"

echo_subtitle "6.1 Verifying 'budgets' table"
mysql -u root -D ledger -e "SELECT id, ledger_id, category_id, year, month, limit_amount FROM budgets ORDER BY id;" --table

echo_subtitle "6.2 Verifying transactions"
mysql -u root -D ledger -e "SELECT id, type, category_id, amount_total, note FROM transactions WHERE ledger_id = $LEDGER_ID ORDER BY id;" --table

echo ""
echo "======================================================================================="
echo " BUDGET FEATURE TEST COMPLETED SUCCESSFULLY"
echo "======================================================================================="
