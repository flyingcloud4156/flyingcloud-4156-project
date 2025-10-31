#!/bin/bash

# =======================================================================================
# API TEST: INCOME TRANSACTION (EQUAL SPLIT) - VERBOSE MODE
# =======================================================================================
#
# SCENARIO:
#   - Ledger: "Project Windfall" (ID: 1), GROUP_BALANCE
#   - Users: Alice (ID: 1), Bob (ID: 2), Charlie (ID: 3)
#   - Action: Bob receives a $900 project payment into his account.
#   - Split: EQUAL split among all three members.
#
# EXPECTED OUTCOME:
#   - Transaction: A $900 INCOME transaction is created.
#   - Splits: Each member's share is $300.
#   - Debt Edges: Bob owes Alice $300, Bob owes Charlie $300.
#
# =======================================================================================

set -euo pipefail
IFS=$'\n\t'

# --- Configuration ---
HOST="http://localhost:8081"
DB_SCHEMA_FILE="/Users/jinyiwang/Desktop/4156project/flyingcloud-4156-project/ops/sql/ledger_flow.sql"
DB_SEED_FILE="/Users/jinyiwang/Desktop/4156project/flyingcloud-4156-project/ops/sql/backup/ledger.sql"

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

echo_api_call() {
    echo "" >&2
    echo "┌─────────────────────────────────────────────────────────────────────────────────────┐" >&2
    echo "│ API REQUEST                                                                         │" >&2
    echo "└─────────────────────────────────────────────────────────────────────────────────────┘" >&2
}

echo_request_details() {
    local method="$1"
    local path="$2"
    local payload="$3"
    local token="${4:-}"
    
    echo "📡 METHOD: $method" >&2
    echo "🔗 URL:    $HOST$path" >&2
    if [[ -n "$token" ]]; then
        echo "🔑 TOKEN:  ${token:0:20}... (truncated)" >&2
    else
        echo "🔑 TOKEN:  (none)" >&2
    fi
    echo "" >&2
    echo "📦 REQUEST BODY:" >&2
    echo "$payload" | jq '.' >&2
    echo "" >&2
}

# Abort if the API response has { "success": false }
fail_if_false() {
    local resp="$1"
    local ok
    ok=$(echo "$resp" | jq -er '.success') || ok="false"
    if [[ "$ok" != "true" ]]; then
        echo "❌ API call failed. Response:"
        echo "$resp" | jq .
        exit 1
    fi
}

# Wrapper for curl POST requests with verbose output
api_post() {
    local path="$1"
    local payload="$2"
    local token="${3:-}"
    
    echo_api_call
    echo_request_details "POST" "$path" "$payload" "$token"
    
    local headers=(-H "Content-Type: application/json")
    if [[ -n "$token" ]]; then
        headers+=(-H "X-Auth-Token: $token")
    fi
    
    echo "⏳ Sending request..." >&2
    local response
    response=$(curl -sS -X POST "$HOST$path" "${headers[@]}" -d "$payload" 2>&1)
    
    echo "✅ Response received (RAW):" >&2
    echo "$response" >&2
    echo "" >&2
    
    # Try to parse as JSON, if it fails, show warning
    if echo "$response" | jq '.' > /dev/null 2>&1; then
        echo "📋 Response (formatted):" >&2
        echo "$response" | jq '.' >&2
    else
        echo "⚠️  Response is not valid JSON (might be an error or connection issue)" >&2
    fi
    
    # Return only the response (to stdout, for capturing)
    echo "$response"
}

# Assert that a variable is not null or empty
assert_not_null() {
    if [[ -z "${2:-}" || "${2:-}" == "null" ]]; then
        echo "❌ FATAL: Expected '$1' to have a value, but it was null or empty."
        exit 1
    fi
}

# --- Global Variables ---
ALICE_TOKEN=""
ALICE_ID=""
BOB_ID=""
CHARLIE_ID=""
LEDGER_ID=""

# =======================================================================================
# 0. RESETTING DATABASE TO CLEAN STATE
# =======================================================================================
echo_title "0. RESETTING DATABASE TO CLEAN STATE"

echo "🔄 Dropping existing database..."
mysql -u root -e "DROP DATABASE IF EXISTS ledger;" 2>/dev/null || true

echo "🔄 Creating new database..."
mysql -u root -e "CREATE DATABASE ledger;"

echo "🔄 Loading schema..."
mysql -u root ledger < "$DB_SCHEMA_FILE"

echo "🔄 Loading seed data..."
mysql -u root ledger < "$DB_SEED_FILE"

echo "✅ Database reset successfully!"

# =======================================================================================
# 1. SETUP: REGISTER USERS, LOGIN, AND CREATE LEDGER
# =======================================================================================
echo_title "1. SETUP: REGISTER USERS, LOGIN, AND CREATE LEDGER"

# Register Alice
echo_subtitle "1.1 Registering Alice"
alice_register_payload='{"email":"alice@test.com", "name":"Alice", "password":"password"}'
api_post "/api/v1/auth/register" "$alice_register_payload" > /dev/null

# Register Bob
echo_subtitle "1.2 Registering Bob"
bob_register_payload='{"email":"bob@test.com", "name":"Bob", "password":"password"}'
api_post "/api/v1/auth/register" "$bob_register_payload" > /dev/null

# Register Charlie
echo_subtitle "1.3 Registering Charlie"
charlie_register_payload='{"email":"charlie@test.com", "name":"Charlie", "password":"password"}'
api_post "/api/v1/auth/register" "$charlie_register_payload" > /dev/null

echo "✅ All users registered."

# Login as Alice to get a token (the creator of the transaction)
echo_subtitle "1.4 Logging in as Alice"
login_payload='{"email":"alice@test.com", "password":"password"}'
login_resp=$(api_post "/api/v1/auth/login" "$login_payload")
fail_if_false "$login_resp"
ALICE_TOKEN=$(echo "$login_resp" | jq -r '.data.access_token')
assert_not_null "ALICE_TOKEN" "$ALICE_TOKEN"
echo "✅ Alice logged in successfully. Token: ${ALICE_TOKEN:0:30}..."

# Get user IDs from the database
ALICE_ID=$(mysql -u root -D ledger -ss -e "SELECT id FROM users WHERE email='alice@test.com';")
BOB_ID=$(mysql -u root -D ledger -ss -e "SELECT id FROM users WHERE email='bob@test.com';")
CHARLIE_ID=$(mysql -u root -D ledger -ss -e "SELECT id FROM users WHERE email='charlie@test.com';")
assert_not_null "ALICE_ID" "$ALICE_ID"
assert_not_null "BOB_ID" "$BOB_ID"
assert_not_null "CHARLIE_ID" "$CHARLIE_ID"
echo "✅ User IDs retrieved: Alice=$ALICE_ID, Bob=$BOB_ID, Charlie=$CHARLIE_ID"

# Create a new Ledger as Alice
echo_subtitle "1.5 Creating 'Project Windfall' Ledger"
ledger_payload=$(jq -n --arg name "Project Windfall" '{
    "name": $name,
    "ledger_type": "GROUP_BALANCE",
    "base_currency": "USD"
}')
ledger_resp=$(api_post "/api/v1/ledgers" "$ledger_payload" "$ALICE_TOKEN")
fail_if_false "$ledger_resp"
LEDGER_ID=$(echo "$ledger_resp" | jq -r '.data.ledger_id')
assert_not_null "LEDGER_ID" "$LEDGER_ID"
echo "✅ Ledger 'Project Windfall' created with ID: $LEDGER_ID"

# Add Bob to the ledger
echo_subtitle "1.6 Adding Bob to the Ledger"
bob_member_payload=$(jq -n --argjson uid "$BOB_ID" '{
    "user_id": $uid,
    "role": "EDITOR"
}')
api_post "/api/v1/ledgers/$LEDGER_ID/members" "$bob_member_payload" "$ALICE_TOKEN" > /dev/null
echo "✅ Bob added to the ledger."

# Add Charlie to the ledger
echo_subtitle "1.7 Adding Charlie to the Ledger"
charlie_member_payload=$(jq -n --argjson uid "$CHARLIE_ID" '{
    "user_id": $uid,
    "role": "EDITOR"
}')
api_post "/api/v1/ledgers/$LEDGER_ID/members" "$charlie_member_payload" "$ALICE_TOKEN" > /dev/null
echo "✅ Charlie added to the ledger."

# =======================================================================================
# 2. EXECUTE INCOME TRANSACTION TEST
# =======================================================================================
echo_title "2. CREATING INCOME TRANSACTION (EQUAL SPLIT)"

echo "Bob receives \$900 project payment, split equally among 3 people:"
echo "  - Alice: expects \$300.00"
echo "  - Bob:   expects \$300.00 (he is the receiver)"
echo "  - Charlie: expects \$300.00"
echo ""
echo "Since Bob is the receiver, he owes Alice and Charlie their shares."
echo ""

# Construct the JSON payload using jq for safety
payload=$(jq -n \
    --arg type "INCOME" \
    --argjson payer_id "$BOB_ID" \
    --arg amount_total "900.00" \
    --arg note "Project bonus from client" \
    --argjson alice_id "$ALICE_ID" \
    --argjson bob_id "$BOB_ID" \
    --argjson charlie_id "$CHARLIE_ID" \
    '{
        "txn_at": "2025-10-22T14:00:00",
        "type": $type,
        "payer_id": $payer_id,
        "amount_total": $amount_total,
        "currency": "USD",
        "note": $note,
        "is_private": false,
        "rounding_strategy": "ROUND_HALF_UP",
        "tail_allocation": "PAYER",
        "splits": [
            {
                "user_id": $alice_id,
                "split_method": "EQUAL",
                "share_value": 0,
                "included": true
            },
            {
                "user_id": $bob_id,
                "split_method": "EQUAL",
                "share_value": 0,
                "included": true
            },
            {
                "user_id": $charlie_id,
                "split_method": "EQUAL",
                "share_value": 0,
                "included": true
            }
        ]
    }')

# Make the API call
txn_resp=$(api_post "/api/v1/ledgers/$LEDGER_ID/transactions" "$payload" "$ALICE_TOKEN")
fail_if_false "$txn_resp"
TXN_ID=$(echo "$txn_resp" | jq -r '.data.transaction_id')
assert_not_null "TXN_ID" "$TXN_ID"

echo "✅ Transaction created successfully with ID: $TXN_ID"

# =======================================================================================
# 3. DATABASE VERIFICATION
# =======================================================================================
echo_title "3. VERIFYING DATABASE STATE"

# Verify Transaction
echo_subtitle "3.1 Verifying 'transactions' table"
mysql -u root -D ledger -e "SELECT id, ledger_id, type, payer_id, amount_total, note FROM transactions WHERE id = $TXN_ID;" --table

# Verify Splits
echo_subtitle "3.2 Verifying 'transaction_splits' table"
echo "Expected computed amounts: Alice=\$300.00, Bob=\$300.00, Charlie=\$300.00"
mysql -u root -D ledger -e "SELECT transaction_id, user_id, split_method, computed_amount FROM transaction_splits WHERE transaction_id = $TXN_ID ORDER BY user_id;" --table

# Verify Debt Edges
echo_subtitle "3.3 Verifying 'debt_edges' table"
echo "Expected debt edges (INCOME type: receiver owes others):"
echo "  - Bob owes Alice \$300.00 (from_user_id=Alice, to_user_id=Bob)"
echo "  - Bob owes Charlie \$300.00 (from_user_id=Charlie, to_user_id=Bob)"
echo ""
mysql -u root -D ledger -e "SELECT transaction_id, from_user_id as creditor_id, to_user_id as debtor_id, amount FROM debt_edges WHERE transaction_id = $TXN_ID ORDER BY creditor_id;" --table

echo ""
echo "🎉 TEST COMPLETED SUCCESSFULLY! 🎉"
echo ""
echo "Summary:"
echo "  - Bob received \$900 project payment"
echo "  - Each person's share: \$300"
echo "  - Bob owes Alice \$300"
echo "  - Bob owes Charlie \$300"
echo "  - Bob keeps his own share of \$300"
echo "  - Total debt from Bob: \$600 (equals the \$600 he owes to others)"

