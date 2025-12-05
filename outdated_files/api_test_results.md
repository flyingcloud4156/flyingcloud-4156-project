# API Test Results
The results are copied and pasted from terminal after running the bash final_api_tests_complete.sh

---

## 0. Database Reset

**Status:**  SUCCESS

- Dropped existing database
- Created new database
- Loaded schema
- Loaded seed data

---

## 1. User Registration API Tests

### 1.1 Typical Valid: Complete valid registration
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": null,
  "total": null
}
```

### 1.2 Atypical Valid: Minimum valid data (short name)
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": null,
  "total": null
}
```

### 1.3 Invalid: Missing email field
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Invalid request: Field 'email' rejected - must not be blank"
}
```

### 1.4 Invalid: Invalid email format
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Invalid request: Field 'email' rejected - invalid email format"
}
```

**Result:**  All registration tests completed!

---

## 2. User Login API Tests

### 2.1 Typical Valid: Login with registered user
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "access_token": "Te-t8oA5YDqv1_uIxz5td3yDHexw0OYIMxigZoGo4Vo",
    "refresh_token": "qw3qfxOy44LX6oe78gb1OnUHHjc8yKgtuf88PK45crE"
}
}
```

### 2.2 Atypical Valid: Login with different user
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "access_token": "JsscPtrgh_6aAlR3mNHHJ1Ut2pmsLr74DPTNG67MYys",
    "refresh_token": "bp5sUIr4CQ6J9IKxFad07BRhWhepnoXvNcnwvIoJW3M"
}
}
```

### 2.3 Invalid: Wrong password
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Wrong credentials"
}
```

### 2.4 Invalid: Non-existent user
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "User not found"
}
```

**Result:**  All login tests completed!

---

## 3. Token Refresh API Tests

### 3.1 Typical Valid: Refresh with valid refresh token
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "access_token": "c37taAenqkzCtKqTN50yPXWWMfd0iPwu1NHMk5DeUUM",
    "refresh_token": "y6jzzR07MKl3DrSr3k5MNSbKx50qwbtXHM2X9uYsqp4"
}
}
```

### 3.2 Atypical Valid: Refresh multiple times
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "access_token": "q8uk0AY1hvfPiH79n7pzl1WY1_XHEL1TkeF_e1OrY1Y",
    "refresh_token": "VV5-7dafq-cWbaWLQ6bY8hBJr7KPaJCoS4V2f48LMIQ"
  }
}
```

### 3.3 Invalid: Refresh with invalid token
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Invalid or expired refresh token"
}
```

**Result:**  All token refresh tests completed!

---

## 4. User Logout API Tests

### 4.1 Typical Valid: Logout with valid refresh token
**Status:**  PASSED

```json
{
  "success": false,
  "message": "AUTH_REQUIRED"
}
```

### 4.2 Atypical Valid: Logout with empty/null token
**Status:**  PASSED

### 4.3 Invalid: Logout with invalid token
**Status:**  PASSED

**Result:**  All logout tests completed!

---

## 5. User Lookup API Tests

### 5.1 Typical Valid: Get current user info
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 8,
    "name": "Alex Chen"
}
}
```
**User1 ID:** 8

### 5.2 Typical Valid: Lookup user by email
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "user_id": 10,
    "name": "Sarah Johnson"
}
}
```
**User2 ID:** 10

### 5.3 Atypical Valid: Lookup with special characters in email
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "user_id": 9,
    "name": "AI"
}
}
```

### 5.4 Invalid: Lookup non-existent user
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "USER_NOT_FOUND"
}
```

### 5.5 Invalid: Lookup without auth token
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "AUTH_REQUIRED"
}
```

**Result:**  All user lookup tests completed!

---

## 6. User Profile API Tests

### 6.1 Typical Valid: Get own profile by ID
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 8,
    "name": "Alex Chen"
}
}
```

### 6.2 Atypical Valid: Get another user's profile
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "id": 10,
    "name": "Sarah Johnson"
}
}
```

### 6.3 Invalid: Get profile without authentication
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "AUTH_REQUIRED"
}
```

**Result:**  All user profile tests completed!

---

## 7. Ledger Creation API Tests

### 7.1 Typical Valid: Create standard group balance ledger
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "ledger_id": 7,
    "name": "Tech Team Road Trip 2025",
    "ledger_type": "GROUP_BALANCE",
    "base_currency": "USD",
    "share_start_date": null,
    "role": "OWNER"
}
}
```
**Main Ledger ID:** 7

### 7.2 Atypical Valid: Create ledger with start date
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "ledger_id": 8,
    "name": "Family Vacation Fund 2025",
    "ledger_type": "GROUP_BALANCE",
    "base_currency": "USD",
    "share_start_date": [2025, 1, 1],
    "role": "OWNER"
}
}
```
**Secondary Ledger ID:** 8

### 7.3 Invalid: Create ledger without auth token
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "AUTH_REQUIRED"
}
```

### 7.4 Invalid: Create ledger with invalid type
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Data truncated for column 'ledger_type' at row 1"
}
```

### 7.5 Invalid: Create ledger with empty name
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Invalid request: Field 'name' must not be blank"
}
```

**Result:**  All ledger creation tests completed!

---


## 9. Ledger Details API Tests

### 9.1 Typical Valid: Get ledger details as owner
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "ledger_id": 7,
    "name": "Tech Team Road Trip 2025",
    "ledger_type": "GROUP_BALANCE",
    "base_currency": "USD",
    "share_start_date": null,
    "role": "OWNER"
}
}
```

### 9.2 Invalid: Get ledger details as non-member
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "FORBIDDEN: You are not a member of this ledger"
}
```

### 9.3 Invalid: Get non-existent ledger
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "LEDGER_NOT_FOUND"
}
```

**Result:**  All ledger details tests completed!

---

## 10. Ledger Member Management API Tests

### 10.1 Typical Valid: Add member as editor
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "ledger_id": 7,
    "user_id": 10,
    "role": "EDITOR"
}
}
```

### 10.2 Atypical Valid: Add member with different role
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "ledger_id": 7,
    "user_id": 11,
    "role": "VIEWER"
}
}
```
**User3 ID:** 11

### 10.3 Invalid: Add non-existent user
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Cannot add user: Foreign key constraint fails (user does not exist)"
}
```

### 10.4 Invalid: Add member without proper permissions
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "ROLE_INSUFFICIENT: You do not have the required role"
}
```

### 10.5 Typical Valid: List ledger members
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "items": [
      {
        "user_id": 8,
        "name": "Alex Chen",
        "role": "OWNER"
      },
      {
        "user_id": 10,
        "name": "Sarah Johnson",
        "role": "EDITOR"
      },
      {
        "user_id": 11,
        "name": "Mike Wilson",
        "role": "VIEWER"
      }
    ]
  }
}
```

### 10.6 Invalid: List members as non-member
**Status:** ️ UNEXPECTED (Should reject but allowed)

**Result:**  All member management tests completed!

---

## 11. Remove Member API Tests

### 11.1 Typical Valid: Owner removes a member
**Status:**  PASSED

### 11.2 Invalid: Editor tries to remove a member
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "ROLE_INSUFFICIENT: You do not have the required role"
}
```

### 11.3 Invalid: Remove non-existent member
**Status:**  PASSED

**Result:**  All remove member tests completed!

---

## 12. Transaction Creation API Tests

### 12.1 Typical Valid: Create simple expense transaction
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "transaction_id": 4
}
}
```
**Transaction ID:** 4

### 12.2 Atypical Valid: Create income transaction with percentage split
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "transaction_id": 5
}
}
```

### 12.3 Invalid: Create transaction with negative amount
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Invalid request: Amount must be positive"
}
```

### 12.4 Invalid: Create transaction with non-member in split
**Status:** ️ UNEXPECTED (Should reject but allowed)

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "transaction_id": 6
}
}
```

### 12.5 Invalid: Create transaction without auth token
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "AUTH_REQUIRED"
}
```

**Result:**  All transaction creation tests completed!

---

## 13. Transaction Details API Tests

### 13.1 Typical Valid: Get transaction details as member
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "transaction_id": 4,
    "ledger_id": 7,
    "txn_at": [2025, 1, 15, 12, 0],
    "type": "EXPENSE",
    "currency": "USD",
    "amount_total": 120.5,
    "note": "Team lunch at restaurant",
    "payer_id": 8,
    "created_by": 8,
    "rounding_strategy": "ROUND_HALF_UP",
    "tail_allocation": "PAYER",
    "splits": [
      {
        "user_id": 8,
        "split_method": "EQUAL",
        "computed_amount": 60.25
      },
      {
        "user_id": 10,
        "split_method": "EQUAL",
        "computed_amount": 60.25
      }
    ],
    "edges_preview": [
      {
        "from_user_id": 8,
        "to_user_id": 10,
        "amount": 60.25,
        "edge_currency": "USD"
      }
    ]
  }
}
```

### 13.2 Invalid: Get transaction details as non-member
**Status:** ️ UNEXPECTED (Should reject but allowed)

### 13.3 Invalid: Get non-existent transaction
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Transaction not found"
}
```

**Result:**  All transaction details tests completed!

---

## 14. Transaction Query API Tests

### 14.1 Typical Valid: List all transactions in ledger
**Status:**  PASSED

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "page": 1,
    "size": 50,
    "total": 3,
    "items": [
      {
        "transaction_id": 6,
        "txn_at": [2025, 1, 26, 11, 0],
        "type": "EXPENSE",
        "amount_total": 75,
        "note": "Split with non-member"
      },
      {
        "transaction_id": 5,
        "txn_at": [2025, 1, 20, 9, 0],
        "type": "INCOME",
        "amount_total": 200,
        "note": "Shared project payment received"
      },
      {
        "transaction_id": 4,
        "txn_at": [2025, 1, 15, 12, 0],
        "type": "EXPENSE",
        "amount_total": 120.5,
        "note": "Team lunch at restaurant"
      }
    ]
  }
}
```

### 14.2 Atypical Valid: List transactions with pagination
**Status:**  PASSED (3 items returned)

### 14.3 Atypical Valid: List transactions with date filter
**Status:**  PASSED (3 items in date range)

### 14.4 Invalid: List transactions without auth token
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "AUTH_REQUIRED"
}
```

### 14.5 Invalid: List transactions from non-existent ledger
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Ledger not found"
}
```

### 14.6 Atypical Valid: List transactions with type filter
**Status:**  PASSED (3 items returned)

**Result:**  All transaction query tests completed!

---

## 15. Delete Transaction API Tests

### 15.1 Typical Valid: Delete own transaction
**Status:**  PASSED

### 15.2 Invalid: Delete transaction as non-owner/non-admin
**Status:**  PASSED (Correctly rejected)

### 15.3 Invalid: Delete non-existent transaction
**Status:**  PASSED (Correctly rejected)

```json
{
  "success": false,
  "message": "Transaction not found"
}
```

**Result:**  All delete transaction tests completed!

---

## 16. Final Verification

### 16.1 Token Validity Check
-  User1 token still valid
-  User2 token still valid

### 16.2 Ledger Membership Verification
**Status:**  PASSED

**Current Members:**
| User ID | Name | Role |
|---------|------|------|
| 8 | Alex Chen | OWNER |
| 10 | Sarah Johnson | EDITOR |
| 11 | Mike Wilson | VIEWER |

### 16.3 Database Final State

**Users in Database:**
| ID | Email | Name |
|----|-------|------|
| 12 | emma.davis.qa2025@gmail.com | Emma Davis |
| 11 | mike.wilson.dev2025@gmail.com | Mike Wilson |
| 10 | sarah.johnson.biz2025@gmail.com | Sarah Johnson |
| 9 | short.minimal2025@gmail.com | AI |
| 8 | alex.chen.eng2025@gmail.com | Alex Chen |

**Ledgers Created:**
| ID | Name | Owner ID | Type |
|----|------|----------|------|
| 8 | Family Vacation Fund 2025 | 8 | GROUP_BALANCE |
| 7 | Tech Team Road Trip 2025 | 8 | GROUP_BALANCE |

**Transactions in Main Ledger (ID: 7):**
| ID | Type | Amount | Note |
|----|------|--------|------|
| 6 | EXPENSE | 75.00 | Split with non-member |
| 5 | INCOME | 200.00 | Shared project payment received |
| 4 | EXPENSE | 120.50 | Team lunch at restaurant |

---


