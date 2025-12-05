# API Test Equivalence Partitions - Complete Documentation

## Overview

This document details all equivalence partitions tested for each API endpoint in the Ledger Application. Each partition includes test ID, input characteristics, expected result, and which test file covers it.



## 1. Authentication & User APIs

### 1.1 POST /api/v1/auth/register

**Endpoint**: Register a new user

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| AUTH-REG-V1 | Valid (Typical) | Complete valid registration: valid email, name, password | 1.1 | HTTP 200, user created | api_tests_iteration1.sh |
| AUTH-REG-V2 | Valid (Boundary) | Minimal valid data: short name "AI" | 1.2 | HTTP 200, user created | api_tests_iteration1.sh |
| AUTH-REG-I1 | Invalid | Missing required field: email | 1.3 | HTTP 4xx, error message | api_tests_iteration1.sh |
| AUTH-REG-I2 | Invalid | Invalid email format: "invalid-email-format" | 1.4 | HTTP 4xx, validation error | api_tests_iteration1.sh |
| AUTH-REG-I3 | Invalid | Duplicate email: existing user email | AUTH-REG-DUPLICATE-EMAIL | HTTP 4xx, duplicate error | api_negative.sh |
| AUTH-REG-I4 | Invalid | Empty password: password="" | AUTH-REG-INVALID-PASS-EMPTY | HTTP 4xx, validation error | api_negative.sh |

**Total Partitions**: 6 (2 valid, 4 invalid)

---

### 1.2 POST /api/v1/auth/login

**Endpoint**: User authentication

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| AUTH-LOGIN-V1 | Valid (Typical) | Correct email and password | 2.1 | HTTP 200, tokens returned | api_tests_iteration1.sh |
| AUTH-LOGIN-V2 | Valid (Typical) | Different valid user login | 2.2 | HTTP 200, tokens returned | api_tests_iteration1.sh |
| AUTH-LOGIN-I1 | Invalid | Wrong password for existing user | 2.3 | HTTP 401, authentication failed | api_tests_iteration1.sh |
| AUTH-LOGIN-I2 | Invalid | Non-existent user email | 2.4 | HTTP 404, user not found | api_tests_iteration1.sh |

**Total Partitions**: 4 (2 valid, 2 invalid)

---

### 1.3 POST /api/v1/auth/refresh

**Endpoint**: Refresh access token

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| AUTH-REFRESH-V1 | Valid (Typical) | Valid refresh token | 3.1 | HTTP 200, new tokens | api_tests_iteration1.sh |
| AUTH-REFRESH-V2 | Valid (Atypical) | Multiple refresh in sequence | 3.2 | HTTP 200, new tokens | api_tests_iteration1.sh |
| AUTH-REFRESH-I1 | Invalid | Invalid refresh token | 3.3 | HTTP 401, invalid token | api_tests_iteration1.sh |

**Total Partitions**: 3 (2 valid, 1 invalid)

---

### 1.4 POST /api/v1/auth/logout

**Endpoint**: User logout

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| AUTH-LOGOUT-V1 | Valid (Typical) | Valid refresh token | 4.1 | HTTP 200, success | api_tests_iteration1.sh |
| AUTH-LOGOUT-V2 | Valid (Atypical) | Empty/null token (no-op) | 4.2 | HTTP 200, handled gracefully | api_tests_iteration1.sh |
| AUTH-LOGOUT-I1 | Invalid | Invalid logout token | 4.3 | HTTP 4xx, handled | api_tests_iteration1.sh |

**Total Partitions**: 3 (2 valid, 1 invalid)

---

### 1.5 GET /api/v1/users/me

**Endpoint**: Get current user information

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| USER-ME-V1 | Valid | With valid auth token | 5.1 | HTTP 200, user data | api_tests_iteration1.sh |
| USER-ME-I1 | Invalid | Without auth token | USERS-ME-NO-TOKEN | HTTP 401, unauthorized | api_negative.sh |

**Total Partitions**: 2 (1 valid, 1 invalid)

---

### 1.6 GET /api/v1/user-lookup

**Endpoint**: Lookup user by email

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| USER-LOOKUP-V1 | Valid (Typical) | Existing user email | 5.2 | HTTP 200, user ID | api_tests_iteration1.sh |
| USER-LOOKUP-V2 | Valid (Boundary) | Email with special characters | 5.3 | HTTP 200, user ID | api_tests_iteration1.sh |
| USER-LOOKUP-I1 | Invalid | Non-existent email | 5.4 | HTTP 404, not found | api_tests_iteration1.sh |
| USER-LOOKUP-I2 | Invalid | Without auth token | 5.5 | HTTP 401, unauthorized | api_tests_iteration1.sh |

**Total Partitions**: 4 (2 valid, 2 invalid)

---

### 1.7 GET /api/v1/users/{id}

**Endpoint**: Get user profile by ID

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| USER-PROFILE-V1 | Valid | Own profile with auth | 6.1 | HTTP 200, profile data | api_tests_iteration1.sh |
| USER-PROFILE-V2 | Valid | Another user's profile | 6.2 | HTTP 200, profile data | api_tests_iteration1.sh |
| USER-PROFILE-I1 | Invalid | Without authentication | 6.3 | HTTP 401, unauthorized | api_tests_iteration1.sh |

**Total Partitions**: 3 (2 valid, 1 invalid)

---

## 2. Ledger APIs

### 2.1 POST /api/v1/ledgers

**Endpoint**: Create a new ledger

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| LEDGER-CREATE-V1 | Valid (Typical) | Standard group balance ledger | 7.1 | HTTP 200, ledger created | api_tests_iteration1.sh |
| LEDGER-CREATE-V2 | Valid (Atypical) | With start date | 7.2 | HTTP 200, ledger created | api_tests_iteration1.sh |
| LEDGER-CREATE-I1 | Invalid | Without auth token | 7.3 | HTTP 401, unauthorized | api_tests_iteration1.sh |
| LEDGER-CREATE-I2 | Invalid | Invalid ledger type | 7.4, LEDGER-CREATE-INVALID-TYPE | HTTP 4xx, validation error | api_tests_iteration1.sh, api_negative.sh |
| LEDGER-CREATE-I3 | Invalid (Boundary) | Empty name | 7.5, LEDGER-CREATE-MISSING-NAME | HTTP 4xx, validation error | api_tests_iteration1.sh, api_negative.sh |
| LEDGER-CREATE-I4 | Invalid | Unknown currency | LEDGER-CREATE-INVALID-CURRENCY | HTTP 4xx, validation error | api_negative.sh |

**Total Partitions**: 6 (2 valid, 4 invalid)

---

### 2.2 GET /api/v1/ledgers/mine

**Endpoint**: Get user's ledgers

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| LEDGER-MINE-V1 | Valid (Typical) | User with ledgers | 8.1 | HTTP 200, ledger list | api_tests_iteration1.sh |
| LEDGER-MINE-V2 | Valid (Boundary) | User with no ledgers | 8.2 | HTTP 200, empty list | api_tests_iteration1.sh |
| LEDGER-MINE-I1 | Invalid | Without authentication | 8.3 | HTTP 401, unauthorized | api_tests_iteration1.sh |

**Total Partitions**: 3 (2 valid, 1 invalid)

---

### 2.3 GET /api/v1/ledgers/{ledgerId}

**Endpoint**: Get ledger details

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| LEDGER-GET-V1 | Valid | As owner/member | 9.1, LEDGER-GET-AS-OWNER | HTTP 200, ledger details | api_tests_iteration1.sh, api_negative.sh |
| LEDGER-GET-I1 | Invalid | As non-member | 9.2, LEDGER-GET-AS-NON-MEMBER | HTTP 403, forbidden | api_tests_iteration1.sh, api_negative.sh |
| LEDGER-GET-I2 | Invalid | Non-existent ledger | 9.3 | HTTP 404, not found | api_tests_iteration1.sh |

**Total Partitions**: 3 (1 valid, 2 invalid)

---

### 2.4 POST /api/v1/ledgers/{ledgerId}/members

**Endpoint**: Add member to ledger

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| MEMBER-ADD-V1 | Valid (Typical) | Add as EDITOR | 10.1, LEDGER-ADD-MEMBER-VALID | HTTP 200, member added | api_tests_iteration1.sh, api_negative.sh |
| MEMBER-ADD-V2 | Valid (Atypical) | Add as VIEWER | 10.2 | HTTP 200, member added | api_tests_iteration1.sh |
| MEMBER-ADD-I1 | Invalid | Non-existent user | 10.3 | HTTP 404, user not found | api_tests_iteration1.sh |
| MEMBER-ADD-I2 | Invalid | Without proper permissions | 10.4 | HTTP 403, forbidden | api_tests_iteration1.sh |
| MEMBER-ADD-I3 | Invalid | Duplicate member | LEDGER-ADD-MEMBER-DUPLICATE | HTTP 4xx, already member | api_negative.sh |

**Total Partitions**: 5 (2 valid, 3 invalid)

---

### 2.5 GET /api/v1/ledgers/{ledgerId}/members

**Endpoint**: List ledger members

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| MEMBER-LIST-V1 | Valid | As member | 10.5 | HTTP 200, member list | api_tests_iteration1.sh |
| MEMBER-LIST-I1 | Invalid | As non-member | 10.6 | HTTP 403, forbidden | api_tests_iteration1.sh |

**Total Partitions**: 2 (1 valid, 1 invalid)

---

### 2.6 DELETE /api/v1/ledgers/{ledgerId}/members/{userId}

**Endpoint**: Remove member from ledger

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| MEMBER-REMOVE-V1 | Valid | Owner removes member | 11.1 | HTTP 200, member removed | api_tests_iteration1.sh |
| MEMBER-REMOVE-I1 | Invalid | Editor tries to remove | 11.2 | HTTP 403, forbidden | api_tests_iteration1.sh |
| MEMBER-REMOVE-I2 | Invalid | Non-existent member | 11.3, LEDGER-REMOVE-NON-MEMBER | HTTP 404, not found | api_tests_iteration1.sh, api_negative.sh |

**Total Partitions**: 3 (1 valid, 2 invalid)

---

### 2.7 GET /api/v1/ledgers/{ledgerId}/settlement-plan

**Endpoint**: Get settlement plan

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| SETTLEMENT-GET-V1 | Valid | As member | SETTLEMENT-VALID-AS-MEMBER | HTTP 200, settlement plan | api_negative.sh |
| SETTLEMENT-GET-I1 | Invalid | As non-member | SETTLEMENT-AS-NON-MEMBER | HTTP 403, forbidden | api_negative.sh |

**Total Partitions**: 2 (1 valid, 1 invalid)

---

### 2.8 POST /api/v1/ledgers/{ledgerId}/settlement-plan

**Endpoint**: Execute settlement plan

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| SETTLEMENT-EXEC-V1 | Valid | As OWNER | SETTLEMENT-EXECUTE-AS-OWNER | HTTP 200, settlement executed | api_settlement_execution_tests.sh |
| SETTLEMENT-EXEC-V2 | Valid | As EDITOR | SETTLEMENT-EXECUTE-AS-EDITOR | HTTP 200, settlement executed | api_settlement_execution_tests.sh |
| SETTLEMENT-EXEC-V3 | Valid | As VIEWER | SETTLEMENT-VIEW-AS-VIEWER | HTTP 200, can view plan | api_settlement_execution_tests.sh |
| SETTLEMENT-EXEC-I1 | Invalid | Without authentication | SETTLEMENT-NO-AUTH | HTTP 401, unauthorized | api_settlement_execution_tests.sh |
| SETTLEMENT-EXEC-I2 | Invalid | Non-existent ledger | SETTLEMENT-NONEXISTENT-LEDGER | HTTP 404, not found | api_settlement_execution_tests.sh |
| SETTLEMENT-EXEC-I3 | Invalid | As non-member | SETTLEMENT-NON-MEMBER | HTTP 403, forbidden | api_settlement_execution_tests.sh |

**Total Partitions**: 6 (3 valid, 3 invalid)

---

## 3. Transaction APIs

### 3.1 POST /api/v1/ledgers/{ledgerId}/transactions

**Endpoint**: Create transaction

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| TXN-CREATE-V1 | Valid (Typical) | EXPENSE with EQUAL split | 12.1, TXN-CREATE-VALID | HTTP 200, transaction created | api_tests_iteration1.sh, api_negative.sh |
| TXN-CREATE-V2 | Valid (Atypical) | INCOME with PERCENT split | 12.2 | HTTP 200, transaction created | api_tests_iteration1.sh |
| TXN-CREATE-V3 | Valid | EXPENSE with EXACT split | TXN-CREATE-VALID | HTTP 200, transaction created | api_negative.sh |
| TXN-CREATE-I1 | Invalid (Boundary) | Negative amount: amount < 0 | 12.3, TXN-NEGATIVE-AMOUNT | HTTP 4xx, validation error | api_tests_iteration1.sh, api_negative.sh |
| TXN-CREATE-I2 | Invalid (Boundary) | Zero amount: amount == 0 | TXN-ZERO-AMOUNT | HTTP 4xx, validation error | api_negative.sh |
| TXN-CREATE-I3 | Invalid | Non-member in split | 12.4 | HTTP 403, forbidden | api_tests_iteration1.sh |
| TXN-CREATE-I4 | Invalid | Without auth token | 12.5 | HTTP 401, unauthorized | api_tests_iteration1.sh |
| TXN-CREATE-I5 | Invalid | Empty splits array | TXN-EMPTY-SPLITS | HTTP 4xx, validation error | api_negative.sh |
| TXN-CREATE-I6 | Invalid | Splits sum mismatch | TXN-SUM-MISMATCH | HTTP 4xx, validation error | api_negative.sh |
| TXN-CREATE-I7 | Invalid | Invalid transaction type | TXN-INVALID-TYPE | HTTP 4xx, validation error | api_negative.sh |
| TXN-CREATE-I8 | Invalid | Non-member creates transaction | TXN-AS-NON-MEMBER | HTTP 403, forbidden | api_negative.sh |

**Total Partitions**: 11 (3 valid, 8 invalid)

---

### 3.2 GET /api/v1/ledgers/{ledgerId}/transactions/{transactionId}

**Endpoint**: Get transaction details

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| TXN-GET-V1 | Valid | As member | 13.1 | HTTP 200, transaction details | api_tests_iteration1.sh |
| TXN-GET-I1 | Invalid | As non-member | 13.2 | HTTP 403, forbidden | api_tests_iteration1.sh |
| TXN-GET-I2 | Invalid | Non-existent transaction | 13.3 | HTTP 404, not found | api_tests_iteration1.sh |

**Total Partitions**: 3 (1 valid, 2 invalid)

---

### 3.3 GET /api/v1/ledgers/{ledgerId}/transactions

**Endpoint**: Query/list transactions

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| TXN-LIST-V1 | Valid (Typical) | List all transactions | 14.1 | HTTP 200, transaction list | api_tests_iteration1.sh |
| TXN-LIST-V2 | Valid (Atypical) | With pagination: page, size | 14.2 | HTTP 200, paginated list | api_tests_iteration1.sh |
| TXN-LIST-V3 | Valid (Atypical) | With date filter: from, to | 14.3 | HTTP 200, filtered list | api_tests_iteration1.sh |
| TXN-LIST-V4 | Valid (Atypical) | With type filter: type=EXPENSE | 14.6 | HTTP 200, filtered list | api_tests_iteration1.sh |
| TXN-LIST-I1 | Invalid | Without auth token | 14.4 | HTTP 401, unauthorized | api_tests_iteration1.sh |
| TXN-LIST-I2 | Invalid | Non-existent ledger | 14.5 | HTTP 404, not found | api_tests_iteration1.sh |

**Total Partitions**: 6 (4 valid, 2 invalid)

---

### 3.4 DELETE /api/v1/ledgers/{ledgerId}/transactions/{transactionId}

**Endpoint**: Delete transaction

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| TXN-DELETE-V1 | Valid | Delete own transaction | 15.1 | HTTP 200, deleted | api_tests_iteration1.sh |
| TXN-DELETE-I1 | Invalid | As non-owner/non-admin | 15.2 | HTTP 403, forbidden | api_tests_iteration1.sh |
| TXN-DELETE-I2 | Invalid | Non-existent transaction | 15.3 | HTTP 404, not found | api_tests_iteration1.sh |

**Total Partitions**: 3 (1 valid, 2 invalid)

---

## 4. Analytics APIs

### 4.1 GET /api/v1/ledgers/{ledgerId}/analytics/overview

**Endpoint**: Get analytics overview

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| ANALYTICS-V1 | Valid | Valid months (1-24) | ANALYTICS-OVERVIEW-VALID | HTTP 200, analytics data | api_negative.sh |
| ANALYTICS-B1 | Boundary | months=0 (clamp to 3) | ANALYTICS-MONTHS-ZERO | HTTP 200, 3 periods | api_negative.sh |
| ANALYTICS-B2 | Boundary | months>24 (clamp to 24) | ANALYTICS-MONTHS-ABOVE-MAX | HTTP 200, 24 periods | api_negative.sh |
| ANALYTICS-I1 | Invalid | Non-member access | ANALYTICS-AS-NON-MEMBER | HTTP 403, forbidden | api_negative.sh |
| ANALYTICS-I2 | Invalid | Unknown ledger | ANALYTICS-UNKNOWN-LEDGER | HTTP 404, not found | api_negative.sh |

**Total Partitions**: 5 (1 valid, 2 boundary, 2 invalid)

---

## 5. Budget APIs

### 5.1 POST /api/v1/ledgers/{ledgerId}/budgets

**Endpoint**: Set or update budget

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| BUDGET-CREATE-V1 | Valid (Typical) | Create new budget | BUDGET-CREATE-VALID | HTTP 200, budget created | api_budget_tests.sh |
| BUDGET-CREATE-V2 | Valid (Typical) | Update existing budget | BUDGET-UPDATE-VALID | HTTP 200, budget updated | api_budget_tests.sh |
| BUDGET-CREATE-V3 | Valid (Atypical) | Different month | BUDGET-CREATE-DIFFERENT-MONTH | HTTP 200, budget created | api_budget_tests.sh |
| BUDGET-CREATE-V4 | Valid (Atypical) | Null category (ledger-wide) | BUDGET-CREATE-NULL-CATEGORY | HTTP 200, budget created | api_budget_tests.sh |
| BUDGET-CREATE-I1 | Invalid (Boundary) | Negative amount: amount < 0 | BUDGET-NEGATIVE-AMOUNT | HTTP 4xx, validation error | api_budget_tests.sh |
| BUDGET-CREATE-I2 | Invalid (Boundary) | Zero amount: amount == 0 | BUDGET-ZERO-AMOUNT | HTTP 4xx, validation error | api_budget_tests.sh |
| BUDGET-CREATE-I3 | Invalid (Boundary) | Invalid month: 0 | BUDGET-INVALID-MONTH-ZERO | HTTP 4xx, validation error | api_budget_tests.sh |
| BUDGET-CREATE-I4 | Invalid (Boundary) | Invalid month: 13 | BUDGET-INVALID-MONTH-13 | HTTP 4xx, validation error | api_budget_tests.sh |
| BUDGET-CREATE-I5 | Invalid (Boundary) | Invalid year: 2019 (< 2020) | BUDGET-INVALID-YEAR-LOW | HTTP 4xx, validation error | api_budget_tests.sh |
| BUDGET-CREATE-I6 | Invalid (Boundary) | Invalid year: 2101 (> 2100) | BUDGET-INVALID-YEAR-HIGH | HTTP 4xx, validation error | api_budget_tests.sh |
| BUDGET-CREATE-I7 | Invalid | Missing required field: limitAmount | BUDGET-MISSING-AMOUNT | HTTP 4xx, validation error | api_budget_tests.sh |
| BUDGET-CREATE-I8 | Invalid | Non-OWNER/ADMIN role (EDITOR) | BUDGET-EDITOR-ROLE | HTTP 403, forbidden | api_budget_tests.sh |
| BUDGET-CREATE-I9 | Invalid | No authentication | BUDGET-NO-AUTH | HTTP 401, unauthorized | api_budget_tests.sh |
| BUDGET-CREATE-I10 | Invalid | Non-existent ledger | BUDGET-NONEXISTENT-LEDGER | HTTP 404, not found | api_budget_tests.sh |

**Total Partitions**: 14 (4 valid, 10 invalid including 6 boundary)

---

### 5.2 GET /api/v1/ledgers/{ledgerId}/budgets/status

**Endpoint**: Get budget status for a month

| Partition ID | Partition Type | Input Description | Test Case | Expected Result | Test File |
|--------------|----------------|-------------------|-----------|-----------------|-----------|
| BUDGET-STATUS-V1 | Valid (Typical) | With existing budgets | BUDGET-STATUS-WITH-DATA | HTTP 200, budget status | api_budget_tests.sh |
| BUDGET-STATUS-V2 | Valid (Boundary) | No budgets (empty list) | BUDGET-STATUS-EMPTY | HTTP 200, empty list | api_budget_tests.sh |
| BUDGET-STATUS-V3 | Valid (Atypical) | As member (not owner) | BUDGET-STATUS-AS-MEMBER | HTTP 200, budget status | api_budget_tests.sh |
| BUDGET-STATUS-B1 | Boundary | Invalid month: 0 (returns empty) | BUDGET-STATUS-INVALID-MONTH-ZERO | HTTP 200, empty result | api_budget_tests.sh |
| BUDGET-STATUS-B2 | Boundary | Invalid month: 13 (returns empty) | BUDGET-STATUS-INVALID-MONTH-13 | HTTP 200, empty result | api_budget_tests.sh |
| BUDGET-STATUS-I1 | Invalid | No authentication | BUDGET-STATUS-NO-AUTH | HTTP 401, unauthorized | api_budget_tests.sh |
| BUDGET-STATUS-I2 | Invalid | Non-existent ledger | BUDGET-STATUS-NONEXISTENT-LEDGER | HTTP 404, not found | api_budget_tests.sh |
| BUDGET-STATUS-I3 | Invalid | Non-member access | BUDGET-STATUS-NON-MEMBER | HTTP 403, forbidden | api_budget_tests.sh |

**Total Partitions**: 8 (3 valid, 2 boundary, 3 invalid)

---



### Test Execution

All tests can be run with:

```bash
# Test Suite for first iteration. Other 4 tests files are for second iterations.
bash ops/test/api_tests_iteration1.sh

# Negative and boundary tests
HOST=http://localhost:8081 bash ops/test/api_negative.sh

# Budget API tests
HOST=http://localhost:8081 bash ops/test/api_budget_tests.sh

# Settlement execution tests
HOST=http://localhost:8081 bash ops/test/api_settlement_execution_tests.sh

# Automated suite with DB reset
HOST=http://localhost:8081 bash ops/test/api_all.sh
```


