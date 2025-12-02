#!/bin/bash

# =======================================================================================
# Scenario A: USER IDENTITY LIFECYCLE
# =======================================================================================

set -euo pipefail
IFS=$'\n\t'

# --- Configuration ---
HOST="http://localhost:8081/api/v1"

# Dynamically set paths based on script location
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" &>/dev/null && pwd)
PROJECT_ROOT=$(cd "${SCRIPT_DIR}/.." && pwd)
DB_SCHEMA_FILE="${PROJECT_ROOT}/ops/sql/ledger_flow.sql"
DB_SEED_FILE="${PROJECT_ROOT}/ops/sql/backup/ledger.sql"

# --- MySQL Command Helper ---
# Function to execute mysql commands inside the running docker container
mysql_exec() {
    # -i is required to pass stdin for file redirection
    docker exec -i mysql mysql -uroot -proot "$@"
}

# --- Helper Functions ---
echo_title() {
  echo ""
  echo "======================================================================================="
  echo "=> $1"
  echo "======================================================================================="
}

echo_subtitle() { echo ""; echo "--- $1"; }

fail_if_false() {
  local resp="$1"
  local ok
  ok=$(echo "$resp" | jq -er '.success') || ok="false"
  if [[ "$ok" != "true" ]]; then
    echo "❌ API failed:"; echo "$resp" | jq .; exit 1
  fi
}

assert_not_null() {
  if [[ -z "${2:-}" || "${2:-}" == "null" ]]; then
    echo "❌ $1 is empty/null"; exit 1
  fi
}

fail_if_empty() {
  if [[ -z "${1:-}" || "${1:-}" == "null" ]]; then
    echo "❌ ERROR: $2 is empty!"; exit 1
  fi
}

api_post() {
  local path="$1"; local payload="$2"; local token="${3:-}"
  local headers=(-H "Content-Type: application/json")
  if [[ -n "$token" ]]; then headers+=(-H "X-Auth-Token: $token"); fi
  curl -sS -X POST "$HOST$path" "${headers[@]}" -d "$payload"
}

api_get() {
  local path="$1"; local token="${2:-}"; local headers=()
  if [[ -n "$token" ]]; then headers+=(-H "X-Auth-Token: $token"); fi
  curl -sS "$HOST$path" "${headers[@]}"
}

# --- Global Vars ---
ALICE_TOKEN=""
NEW_ACCESS_TOKEN=""
ALICE_ID=""
BOB_ID=""

# ----------------------------------------
# 0. Reset DB
# ----------------------------------------
echo_title "0. RESET DATABASE"
mysql_exec -e "DROP DATABASE IF EXISTS ledger;" 2>/dev/null || true
mysql_exec -e "CREATE DATABASE ledger;"
mysql_exec ledger < "$DB_SCHEMA_FILE"
mysql_exec ledger < "$DB_SEED_FILE"
echo "✅ Database reset"

# ----------------------------------------
# 1. Register three users
# ----------------------------------------
echo ""
echo "------ STEP 1: Register Users (Alice, Bob, Charlie) ------"

echo "API: POST /api/v1/auth/register (Alice)"
curl -sS -X POST "$HOST/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"pass123","name":"Alice"}' | jq .
echo ""

echo "API: POST /api/v1/auth/register (Bob)"
curl -sS -X POST "$HOST/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"bob@test.com","password":"pass123","name":"Bob"}' | jq .
echo ""

echo "API: POST /api/v1/auth/register (Charlie)"
curl -sS -X POST "$HOST/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"charlie@test.com","password":"pass123","name":"Charlie"}' | jq .
echo ""

# ----------------------------------------
# 2. Login Alice
# ----------------------------------------
echo "------ STEP 2: Login Alice ------"
echo "API: POST /api/v1/auth/login"

LOGIN_RESP=$(curl -sS -X POST "$HOST/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"alice@test.com","password":"pass123"}')

echo "$LOGIN_RESP" | jq .

ALICE_TOKEN=$(echo "$LOGIN_RESP" | jq -r '.data.access_token')
REFRESH_TOKEN=$(echo "$LOGIN_RESP" | jq -r '.data.refresh_token')

fail_if_empty "$ALICE_TOKEN" "access_token"
fail_if_empty "$REFRESH_TOKEN" "refresh_token"
echo "✅ Alice login success"

# ----------------------------------------
# 3. Refresh token
# ----------------------------------------
echo ""
echo "------ STEP 3: Refresh token ------"
echo "API: POST /api/v1/auth/refresh?refreshToken={token}"

REFRESH_RESP=$(curl -sS -X POST "$HOST/auth/refresh?refreshToken=$REFRESH_TOKEN" \
  -H "Content-Type: application/json" -d '{}')
echo "$REFRESH_RESP" | jq .

NEW_ACCESS_TOKEN=$(echo "$REFRESH_RESP" | jq -r '.data.access_token')
NEW_REFRESH_TOKEN=$(echo "$REFRESH_RESP" | jq -r '.data.refresh_token')

fail_if_empty "$NEW_ACCESS_TOKEN" "new access_token"
fail_if_empty "$NEW_REFRESH_TOKEN" "new refresh_token"
echo "✅ Token refreshed"

# ----------------------------------------
# 4. Logout and verify refresh fails
# ----------------------------------------
echo ""
echo "------ STEP 4: Logout and Validate Token Blacklist ------"
echo "API: POST /api/v1/auth/logout?refreshToken={token}"

LOGOUT_RESP=$( 
  curl -sS -X POST "$HOST/auth/logout?refreshToken=$NEW_REFRESH_TOKEN" \
    -H "X-Auth-Token: $NEW_ACCESS_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{}'
)
echo "$LOGOUT_RESP" | jq .

# ✅ 必须成功，否则退出
if echo "$LOGOUT_RESP" | jq -e '.success == true' > /dev/null 2>&1; then
  echo "✅ Logout success"
else
  echo "❌ Logout failed — backend response above"; exit 1
fi

echo ""
echo "API: POST /api/v1/auth/refresh after logout (should fail)"
REFRESH_AFTER_LOGOUT=$( 
  curl -sS -X POST "$HOST/auth/refresh?refreshToken=$NEW_REFRESH_TOKEN" \
    -H "Content-Type: application/json" \
    -d '{}'
)
echo "$REFRESH_AFTER_LOGOUT" | jq .

# ✅ 刷新应失败（不能返回 success:true）
if echo "$REFRESH_AFTER_LOGOUT" | jq -e '.success == true' > /dev/null 2>&1; then
  echo "❌ ERROR: Refresh succeeded after logout — refresh token NOT revoked!"; exit 1
else
  echo "✅ Refresh blocked after logout"
fi

# ----------------------------------------
# 5. Get current user
# ----------------------------------------
echo ""
echo "------ STEP 5: Get Current User ------"
echo "API: GET /api/v1/users/me"

ME_RESP=$(curl -sS -X GET "$HOST/users/me" -H "X-Auth-Token: $NEW_ACCESS_TOKEN")
echo "$ME_RESP" | jq .
assert_not_null "current user name" "$(echo "$ME_RESP" | jq -r '.data.name')"
echo "✅ /users/me returned Alice"

# ----------------------------------------
# 6. Lookup Bob & fetch profile
# ----------------------------------------
echo ""
echo "------ STEP 6: Lookup Bob & Fetch Profile ------"

echo "API: GET /api/v1/users:lookup?email=bob@test.com"
LOOKUP_RESP=$(curl -sS -X GET "$HOST/lookup?email=bob@test.com" \
  -H "X-Auth-Token: $NEW_ACCESS_TOKEN")
echo "$LOOKUP_RESP" | jq .

BOB_ID=$(echo "$LOOKUP_RESP" | jq -r '.data.user_id')
fail_if_empty "$BOB_ID" "Bob id"
echo "✅ Found Bob, ID = $BOB_ID"

echo "API: GET /api/v1/users/$BOB_ID"
PROFILE_RESP=$(curl -sS -X GET "$HOST/users/$BOB_ID" -H "X-Auth-Token: $NEW_ACCESS_TOKEN")
echo "$PROFILE_RESP" | jq .
assert_not_null "Bob profile name" "$(echo "$PROFILE_RESP" | jq -r '.data.name')"
echo "✅ Bob profile retrieved"

echo ""
echo "🎉 Scenario A Passed: User identity lifecycle & lookup works!"
