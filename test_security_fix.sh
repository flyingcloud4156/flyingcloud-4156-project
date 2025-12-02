#!/bin/bash

# Test script to verify security fix for user lookup endpoint

echo "🔒 Testing Security Fix for User Lookup Endpoint"
echo "============================================"

HOST="http://localhost:8081"

echo ""
echo "Test 1: Try to access user lookup WITHOUT authentication token"
echo "Expected: HTTP 401 Unauthorized (after fix) / HTTP 200 OK (before fix)"
echo "Testing..."

RESPONSE=$(curl -s -w "HTTP_CODE:%{http_code}" -X GET "$HOST/api/v1/users/lookup?email=test@example.com")

HTTP_CODE=$(echo "$RESPONSE" | grep -o "HTTP_CODE:[0-9]*" | cut -d: -f2)
BODY=$(echo "$RESPONSE" | sed 's/HTTP_CODE:[0-9]*$//')

echo "HTTP Status: $HTTP_CODE"
echo "Response Body: $BODY"

if [ "$HTTP_CODE" = "401" ]; then
    echo "✅ SECURITY FIX ACTIVE: Request properly rejected"
elif [ "$HTTP_CODE" = "200" ]; then
    if echo "$BODY" | grep -q '"success":false'; then
        echo "✅ SECURITY FIX ACTIVE: Request rejected with auth error"
    else
        echo "❌ SECURITY VULNERABILITY STILL EXISTS: Request allowed without authentication"
    fi
else
    echo "⚠️  Unexpected HTTP status: $HTTP_CODE"
fi

echo ""
echo "Test 2: Try to access user lookup WITH authentication token"
echo "Expected: HTTP 200 OK (should work with valid token)"
echo "Testing..."

# First login to get a token
LOGIN_RESPONSE=$(curl -s -X POST "$HOST/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d '{"email": "hzh@gmail.com", "password": "password"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | jq -r '.data.access_token // empty')

if [ -n "$TOKEN" ] && [ "$TOKEN" != "null" ]; then
    echo "Token obtained: ${TOKEN:0:10}..."

    AUTH_RESPONSE=$(curl -s -w "HTTP_CODE:%{http_code}" -X GET "$HOST/api/v1/users/lookup?email=hzh2@gmail.com" \
        -H "X-Auth-Token: $TOKEN")

    AUTH_HTTP_CODE=$(echo "$AUTH_RESPONSE" | grep -o "HTTP_CODE:[0-9]*" | cut -d: -f2)
    AUTH_BODY=$(echo "$AUTH_RESPONSE" | sed 's/HTTP_CODE:[0-9]*$//')

    echo "HTTP Status: $AUTH_HTTP_CODE"
    echo "Response Body: $AUTH_BODY"

    if [ "$AUTH_HTTP_CODE" = "200" ]; then
        echo "✅ AUTHENTICATED ACCESS: Works correctly"
    else
        echo "❌ AUTHENTICATED ACCESS: Failed unexpectedly"
    fi
else
    echo "❌ Could not obtain authentication token"
fi

echo ""
echo "============================================"
echo "Security testing completed!"