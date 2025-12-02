# Integration Tests Documentation

## Overview
This package contains Integration Tests for the Ledger Application. These tests verify that multiple components work together correctly.

## Test Categories

### 1. Internal Integration Tests
Test how different service classes work together:
- **BudgetTransactionIntegrationTest**: Budget and Transaction services integration
- **TransactionDebtEdgeIntegrationTest**: Transaction and DebtEdge creation
- **LedgerMemberIntegrationTest**: Ledger and Member management
- **AnalyticsDataFlowIntegrationTest**: Analytics data flow across services

### 2. External Integration Tests
Test integration with external resources:
- **BudgetMapperDatabaseIntegrationTest**: Budget Mapper with MySQL database
- **TransactionMapperDatabaseIntegrationTest**: Transaction Mapper with MySQL
- **RedisTokenIntegrationTest**: Redis token storage integration
- **DatabaseConstraintIntegrationTest**: Database constraints enforcement

### 3. Class Tests
Test complete workflows within a single service class:
- **LedgerServiceClassTest**: Settlement algorithm complete flow
- **AnalyticsServiceClassTest**: Analytics report generation flow

## How to Run

Run all integration tests:
```bash
mvn test -Dtest="*IntegrationTest"
```

Run specific test:
```bash
mvn test -Dtest="BudgetTransactionIntegrationTest"
```

Run with coverage:
```bash
mvn clean verify
```

## Test Requirements
- MySQL database running locally
- Redis server running locally
- Spring Boot application context
- @Transactional for automatic rollback

## Note on Simplifications
Some tests have been simplified to work with the actual project structure:
- No Category entity exists, so categoryId is used directly (value 1L or null)
- Exception classes may not exist, tests focus on happy paths
- AddMemberRequest is actually AddLedgerMemberRequest in the codebase

