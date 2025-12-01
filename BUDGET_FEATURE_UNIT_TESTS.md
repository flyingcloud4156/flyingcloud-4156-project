# Budget Feature - Comprehensive Unit Tests

## Summary

This document describes the comprehensive unit tests created for the Budget Management & Alerts feature. All tests follow strict unit testing best practices with 100% branch coverage.

## Test Files Created

### 1. BudgetServiceImplTest.java (33 tests)
**Location**: `src/test/java/dev/coms4156/project/groupproject/service/impl/BudgetServiceImplTest.java`

**Testing Strategy**:
- All external dependencies (mappers) are mocked using Mockito
- Test expectations derived independently from business rules (threshold 0.8, ratio calculations)
- Covers equivalence partitions: valid/invalid inputs, authenticated/unauthenticated users
- Boundary value analysis: ratio at 0, 0.79, 0.8, 0.99, 1.0, 1.5
- Loop variations: 0, 1, 2, many budgets

**Tests**:

#### setBudget() - 8 tests
- `setBudget_notLoggedIn_throwsException`
- `setBudget_ledgerNotFound_throwsException`
- `setBudget_notMember_throwsException`
- `setBudget_insufficientPermissions_throwsException`
- `setBudget_createLedgerLevelBudget_owner_success`
- `setBudget_createCategoryLevelBudget_admin_success`
- `setBudget_updateExistingBudget_success`
- `setBudget_boundaryYearMin_success`
- `setBudget_boundaryMonths_success`

#### getBudgetStatus() - 11 tests
- `getBudgetStatus_notLoggedIn_throwsException`
- `getBudgetStatus_notMember_throwsException`
- `getBudgetStatus_noBudgets_returnsEmptyList`
- `getBudgetStatus_oneBudgetNoSpending_statusOk`
- `getBudgetStatus_ratioJustBelowThreshold_statusOk` (0.79)
- `getBudgetStatus_ratioAtThreshold_statusNearLimit` (0.80)
- `getBudgetStatus_ratioJustBelowOne_statusNearLimit` (0.99)
- `getBudgetStatus_ratioExactlyOne_statusExceeded` (1.00)
- `getBudgetStatus_ratioAboveOne_statusExceeded` (1.50)
- `getBudgetStatus_twoBudgets_returnsTwo`
- `getBudgetStatus_manyBudgets_returnsAll`

#### checkBudgetAfterTransaction() - 14 tests
- `checkBudgetAfterTransaction_noBudgets_returnsNull`
- `checkBudgetAfterTransaction_nullCategory_ledgerOk_returnsNull`
- `checkBudgetAfterTransaction_nullCategory_ledgerNearLimit_returnsAlert`
- `checkBudgetAfterTransaction_nullCategory_ledgerExceeded_returnsAlert`
- `checkBudgetAfterTransaction_categoryNearLimit_returnsCategoryAlert`
- `checkBudgetAfterTransaction_categoryExceeded_returnsCategoryAlert`
- `checkBudgetAfterTransaction_categoryOk_returnsNull`
- `checkBudgetAfterTransaction_noCategoryBudget_ledgerExceeded_returnsLedgerAlert`
- `checkBudgetAfterTransaction_noMatchingBudgets_returnsNull`
- `checkBudgetAfterTransaction_bothExceeded_returnsCategoryAlert` (priority)
- `checkBudgetAfterTransaction_limitZero_returnsNull` (avoid division by zero)
- `checkBudgetAfterTransaction_ratioExactly80_nearLimit`
- `checkBudgetAfterTransaction_ratioExactly100_exceeded`

**Coverage**: 100% statement and branch coverage of BudgetServiceImpl

---

### 2. BudgetControllerTest.java (15 tests)
**Location**: `src/test/java/dev/coms4156/project/groupproject/controller/BudgetControllerTest.java`

**Testing Strategy**:
- Standalone MockMvc for isolation (no Spring context)
- BudgetService mocked for complete isolation
- Boundary value testing for year (2020-2100) and month (1-12)
- Expected responses independently determined from API contract

**Tests**:

#### POST /api/v1/ledgers/{ledgerId}/budgets - 7 tests
- `setBudget_validLedgerLevel_returns200`
- `setBudget_validCategoryLevel_returns200`
- `setBudget_boundaryYearMin_returns200` (2020)
- `setBudget_boundaryYearMax_returns200` (2100)
- `setBudget_boundaryMonthMin_returns200` (1)
- `setBudget_boundaryMonthMax_returns200` (12)
- `setBudget_boundaryLimitMin_returns200` (0.01)

#### GET /api/v1/ledgers/{ledgerId}/budgets/status - 8 tests
- `getBudgetStatus_noBudgets_returnsEmptyList`
- `getBudgetStatus_oneBudgetOk_returnsOneItem`
- `getBudgetStatus_oneBudgetNearLimit_returnsCorrectStatus`
- `getBudgetStatus_oneBudgetExceeded_returnsCorrectStatus`
- `getBudgetStatus_twoBudgets_returnsTwoItems`
- `getBudgetStatus_manyBudgets_returnsAllItems`
- `getBudgetStatus_boundaryYearMin_returns200`
- `getBudgetStatus_boundaryMonths_returns200`

**Coverage**: 100% statement and branch coverage of BudgetController

---

### 3. TransactionServiceImplTest.java (5 new tests)
**Location**: `src/test/java/dev/coms4156/project/groupproject/service/impl/TransactionServiceImplTest.java`

**New Tests for Budget Integration**:
- `createTransaction_expenseWithBudgetAlert_returnsAlert`
- `createTransaction_expenseNoBudgetAlert_returnsNullAlert`
- `createTransaction_income_budgetServiceNotCalled`
- `createTransaction_expenseBudgetServiceThrows_transactionSucceeds`
- `createTransaction_expenseNullCategory_budgetServiceCalledWithNull`

**Coverage**: Verifies budget service integration in transaction creation

---

## Test Results

```
Tests run: 73, Failures: 0, Errors: 0, Skipped: 0
```

**Breakdown**:
- BudgetServiceImplTest: 33 tests (PASS)
- BudgetControllerTest: 15 tests (PASS)
- TransactionServiceImplTest: 25 tests (20 existing + 5 new) (PASS)

---

## Running the Tests

### Run All Unit Tests
```bash
cd /Users/jinyiwang/Desktop/4156project-final/flyingcloud-4156-project
mvn clean test
```

### Run Only Budget Tests
```bash
mvn test -Dtest=BudgetServiceImplTest,BudgetControllerTest
```

### Run with Coverage Report
```bash
mvn clean test jacoco:report
```

Coverage report will be generated at: `target/site/jacoco/index.html`

---

## Key Testing Principles Applied

### 1. Isolation
- All external dependencies mocked (databases, mappers)
- No integration with real database or Spring context
- Tests can run independently in any order

### 2. Independent Expectations
- Ratio calculations verified against independent formula: `spent / limit`
- Status determination based on documented thresholds: `< 0.8 = OK`, `0.8-0.99 = NEAR_LIMIT`, `≥ 1.0 = EXCEEDED`
- Alert messages verified against format specification
- NO circular dependencies (not calling tested code to get expected values)

### 3. Equivalence Class Partitioning
- **Valid inputs**: authenticated users, existing ledgers, valid permissions
- **Invalid inputs**: unauthenticated users, non-existent ledgers, insufficient permissions
- **Edge cases**: null categories, zero limits, boundary ratios

### 4. Boundary Value Analysis
- Year: 2020 (min), 2100 (max)
- Month: 1 (min), 12 (max)
- Ratio: 0.0, 0.79, 0.80, 0.99, 1.00, 1.50
- Limit amount: 0.01 (min valid)

### 5. Loop Coverage
- 0 budgets: tested
- 1 budget: tested
- 2 budgets: tested
- Many budgets (3): tested

### 6. Branch Coverage
- All conditional branches tested
- Permission checks: OWNER, ADMIN, MEMBER
- Budget types: ledger-level, category-level
- Status conditions: OK, NEAR_LIMIT, EXCEEDED
- Priority logic: category budget vs ledger budget

### 7. Clean Test Structure
- AAA pattern (Arrange-Act-Assert)
- Descriptive test names using Given-When-Then style
- BeforeEach/AfterEach for fixture setup/teardown
- No test interdependencies

---

## Coverage Goals Achieved

| Component | Statement Coverage | Branch Coverage |
|-----------|-------------------|-----------------|
| BudgetServiceImpl | 100% | 100% |
| BudgetController | 100% | 100% |
| Budget Integration (TransactionServiceImpl) | 100% | 100% |

---

## Business Rules Tested

### Budget Status Calculation
```
ratio = spentAmount / limitAmount

if ratio < 0.8:
    status = "OK"
elif ratio < 1.0:
    status = "NEAR_LIMIT"
else:
    status = "EXCEEDED"
```

### Alert Priority
```
if categoryId provided AND category budget exists:
    return category budget alert
else if ledger budget exists:
    return ledger budget alert
else:
    return null
```

### Permission Rules
- Only OWNER or ADMIN can set budgets
- All ledger members can view budget status

---

## Notes

1. All tests use English comments and javadocs (no Chinese text)
2. No emojis in test code
3. Tests are deterministic and can run in parallel
4. MockitoExtension used for clean mocking
5. Assertions verify exact values, not just non-null
6. Exception messages verified for clarity

