# 1. Project Overview

（原章节保留，扩展以下要点）

- 服务简介（现有内容保留）
- 本服务如何满足课程五大前提要求（useful computation、多客户端、persistent datastore、API、logging）
- 第二次迭代新增/变更的功能说明（若有）
- 若某些第一迭代提案内容被删减，需在本节解释原因（课程要求）

------

# 2. Building and Running Instructions

（原节保留，但需新增 CI 相关说明）

## 2.1 Local Development Setup

- JDK17、Maven、IDE 说明（保留原文字）
- 如何在本地运行服务：`mvn clean install`, `mvn spring-boot:run`
- 本地运行数据库（MySQL/PostgreSQL）的说明
- 配置环境变量（如有）

## 2.2 Running a Macbook Based Instance (Iteration 1)

（保留原链接）

## 2.3 Running a Cloud Based Instance (Iteration 2)

（必须扩展：课程要求）

内容必须包括：

- GCP 部署方式（Cloud Run / Cloud SQL / VPC Connector）
- 可访问的 service URL
- 如何验证部署在云端的实例正在运行
- 导师访问方式（无需账号则说明）

------

# 3. API Documentation

#### POST /api/v1/auth/register
* Expected Input Parameters: JSON object (RegisterRequest) containing the following fields
 * email (string): the user’s email address
 * password (string): the user’s password
 * name (string): the user’s display name
 * Expected Output: A JSON object (Result<Void>) indicating registration success
* Upon Success: HTTP 200 Status Code returned with success message in JSON
* Upon Failure:
  * HTTP 400 Status Code with “Invalid input.”
  * HTTP 409 Status Code with “User already exists.”

#### POST /api/v1/auth/login
* Expected Input Parameters: JSON object (LoginRequest) containing the following fields
  * email (string): the user’s email address
  * password (string): the user’s password
* Expected Output: A JSON object (TokenPair) containing the following fields
  * accessToken (string): short-lived access token
  * refreshToken (string): long-lived refresh token
* Upon Success: HTTP 200 Status Code returned along with token pair in JSON
* Upon Failure:
  * HTTP 401 Status Code with “Invalid email or password.”
  * HTTP 500 Status Code with “Error occurred during login.”

#### POST /api/v1/auth/refresh
* Expected Input Parameters:
  * refreshToken (string): existing refresh token
* Expected Output: A JSON object (TokenPair) containing the following fields
  * accessToken (string): new short-lived token
  * refreshToken (string): new long-lived token
* Upon Success: HTTP 200 Status Code returned with refreshed tokens
* Upon Failure:
  * HTTP 401 Status Code with “Invalid or expired refresh token.”
  * HTTP 500 Status Code with “Error occurred while refreshing token.”

#### POST /api/v1/auth/logout
* Expected Input Parameters:
  * refreshToken (string): refresh token to invalidate
* Expected Output: A JSON object (Result<Void>) confirming logout
* Upon Success: HTTP 200 Status Code with “Logout successful.”
* Upon Failure:
  * HTTP 400 Status Code with “Invalid refresh token.”
  * HTTP 500 Status Code with “Error occurred during logout.”

#### GET /api/v1/user-lookup
* Expected Input Parameters:
  * email (string): user’s email address
* Expected Output: A JSON object (UserLookupResponse) containing the following fields
  * id (long): user ID
  * name (string): user display name
  * email (string): user email
  * exists (boolean): whether the user exists
* Upon Success: HTTP 200 Status Code returned with user lookup data in JSON
* Upon Failure:
  * HTTP 404 Status Code with “User not found.”
  * HTTP 500 Status Code with “Error occurred during user lookup.”

#### GET /api/v1/users/me
* Expected Input Parameters:
  * Header:
  * X-Auth-Token (string): access token
* Expected Output: A JSON object (UserView) containing the following fields
  * id (long): current user ID
  * name (string): user name
* Upon Success: HTTP 200 Status Code returned with user info in JSON
* Upon Failure:
  * HTTP 401 Status Code with “Unauthorized or expired token.”

#### GET /api/v1/users/{id}
* Expected Input Parameters:
  * id (long): unique user ID
* Expected Output: A JSON object (UserView) containing the following fields
  * id (long): user ID
  * name (string): user display name
  * email (string): user email
* Upon Success: HTTP 200 Status Code returned with user profile in JSON
* Upon Failure:
  * HTTP 404 Status Code with “User not found.”
  * HTTP 500 Status Code with “Error occurred while retrieving user profile.”

#### POST /api/v1/ledgers
* Expected Input Parameters: JSON object containing the following fields
  * name (string): the name of the ledger
  * description (string): the description of the ledger
* Expected Output: A JSON object (LedgerResponse) containing the created ledger information
* Upon Success: HTTP 201 Status Code returned along with ledger object in JSON
* Upon Failure:
  * HTTP 400 Status Code with "Invalid input."
  * HTTP 500 Status Code with "Error occurred while creating ledger."


#### GET /api/v1/ledgers/mine
* Expected Input Parameters: N/A
* Expected Output: A JSON object (MyLedgersResponse) containing the list of ledgers owned or joined by the current user
* Upon Success: HTTP 200 Status Code returned with ledger list in JSON
* Upon Failure:
  * HTTP 500 Status Code with "Error occurred while retrieving user ledgers."

#### GET /api/v1/ledgers/{ledgerId}
* Expected Input Parameters:
  * ledgerId (long): the unique identifier of the ledger
* Expected Output: A JSON object (LedgerResponse) containing the details of the ledger
* Upon Success: HTTP 200 Status Code returned along with ledger details in JSON
* Upon Failure:
  * HTTP 404 Status Code with "Ledger not found."
  * HTTP 500 Status Code with "Error occurred while retrieving ledger details."

#### POST /api/v1/ledgers/{ledgerId}/members
* Expected Input Parameters: JSON object (AddLedgerMemberRequest) containing the following fields
  * userId (long): the ID of the user to add
  * role (string): the role of the member in the ledger
* Expected Output: A JSON object (LedgerMemberResponse) containing the added member information
* Upon Success: HTTP 201 Status Code returned along with member object in JSON
* Upon Failure:
  * HTTP 400 Status Code with "Invalid input."
  * HTTP 404 Status Code with "Ledger not found."
  * HTTP 500 Status Code with "Error occurred while adding member."

#### GET /api/v1/ledgers/{ledgerId}/members
* Expected Input Parameters:
  * ledgerId (long): the unique identifier of the ledger
* Expected Output: A JSON object (ListLedgerMembersResponse) containing the list of all members in the ledger
* Upon Success: HTTP 200 Status Code returned along with member list in JSON
* Upon Failure:
  * HTTP 404 Status Code with "Ledger not found."
  * HTTP 500 Status Code with "Error occurred while retrieving members."

#### DELETE /api/v1/ledgers/{ledgerId}/members/{userId}
* Expected Input Parameters:
  * ledgerId (long): the unique identifier of the ledger
  * userId (long): the unique identifier of the user to remove
* Expected Output: A JSON object (Result<Void>) indicating the removal result
* Upon Success: HTTP 204 Status Code returned with no content
* Upon Failure:
  * HTTP 404 Status Code with "Ledger or user not found."
  * HTTP 500 Status Code with "Error occurred while removing member."

#### POST /api/v1/ledgers/{ledgerId}/transactions
* Expected Input Parameters: JSON object (CreateTransactionRequest) containing the following fields
  * type (string): transaction type (EXPENSE, INCOME, LOAN)
  * title (string): title or description of the transaction
  * totalAmount (decimal): total amount of the transaction
  * splits (list): list of participant split details
  * createdBy (long): ID of the user who created the transaction
  * categoryId (long, optional): category ID for expense categorization
* Expected Output: A JSON object (CreateTransactionResponse) containing:
  * transactionId (long): ID of the created transaction
  * budgetAlert (string, optional): budget warning/alert message if spending approaches or exceeds budget limit (only for EXPENSE transactions)
* Upon Success: HTTP 201 Status Code returned along with transaction object in JSON
* Upon Failure:
  * HTTP 400 Status Code with "Invalid input."
  * HTTP 404 Status Code with "Ledger not found."
  * HTTP 500 Status Code with "Error occurred while creating transaction."

#### GET /api/v1/ledgers/{ledgerId}/transactions/{transactionId}
* Expected Input Parameters:
  * ledgerId (long): the unique identifier of the ledger
  * transactionId (long): the unique identifier of the transaction
* Expected Output: A JSON object (TransactionResponse) containing the detailed transaction information including splits and debt edges
* Upon Success: HTTP 200 Status Code returned along with transaction details in JSON
* Upon Failure:
  * HTTP 404 Status Code with "Transaction not found."
  * HTTP 500 Status Code with "Error occurred while retrieving transaction details."

#### GET /api/v1/ledgers/{ledgerId}/transactions
* Expected Input Parameters:
  * ledgerId (long): the unique identifier of the ledger
  * page (integer): page number (default 1)
  * size (integer): page size (max 200, default 50)
  * from (string): start date filter (ISO 8601 format)
  * to (string): end date filter (ISO 8601 format)
  * type (string): transaction type filter (EXPENSE, INCOME, LOAN)
  * created_by (long): user ID filter for transaction creator
* Expected Output: A JSON object (ListTransactionsResponse) containing paginated transaction list
* Upon Success: HTTP 200 Status Code returned with transaction list in JSON
* Upon Failure:
  * HTTP 404 Status Code with "Ledger not found."
  * HTTP 500 Status Code with "Error occurred while listing transactions."

#### POST /api/v1/ledgers/{ledgerId}/budgets
* Expected Input Parameters: JSON object (SetBudgetRequest) containing the following fields
  * categoryId (long, optional): category ID for category-specific budget (null for ledger-level budget)
  * year (integer): budget year (2020-2100)
  * month (integer): budget month (1-12)
  * limitAmount (decimal): budget limit amount (minimum 0.01)
* Expected Output: A JSON object (Result<Void>) indicating budget creation/update success
* Upon Success: HTTP 200 Status Code returned with success message in JSON
* Upon Failure:
  * HTTP 400 Status Code with "Invalid input."
  * HTTP 401 Status Code with "Not logged in."
  * HTTP 403 Status Code with "Insufficient permissions. Only OWNER or ADMIN can set budgets."
  * HTTP 404 Status Code with "Ledger not found."
  * HTTP 500 Status Code with "Error occurred while setting budget."

#### GET /api/v1/ledgers/{ledgerId}/budgets/status
* Expected Input Parameters:
  * ledgerId (long): the unique identifier of the ledger
  * year (integer): budget year to query
  * month (integer): budget month to query (1-12)
* Expected Output: A JSON object (BudgetStatusResponse) containing a list of budget status items, each with:
  * budgetId (long): unique budget ID
  * categoryId (long, optional): category ID (null for ledger-level budget)
  * categoryName (string): category display name or "Total Budget"
  * limitAmount (decimal): budget limit amount
  * spentAmount (decimal): total spent amount
  * ratio (string): usage ratio as decimal string (e.g., "0.8500" for 85%)
  * status (string): budget status - "OK" (< 80%), "NEAR_LIMIT" (80-99%), or "EXCEEDED" (≥ 100%)
* Upon Success: HTTP 200 Status Code returned with budget status list in JSON
* Upon Failure:
  * HTTP 401 Status Code with "Not logged in."
  * HTTP 403 Status Code with "Not a member of this ledger."
  * HTTP 500 Status Code with "Error occurred while retrieving budget status."


# 4. Client Application

（课程要求新加入，原 README 中没有，需要作为一级大节加入）

## 4.1 Where the Client Code Lives

- 如果客户端代码在同 repo：给出路径（如 `/frontend`）
- 如果单独 repo：提供 URL

## 4.2 What the Client Does

- 描述 UI、登录流程、ledger 选择、添加交易、预算可视化等
- 描述客户端依赖哪些 API

## 4.3 Building & Running the Client

- 如何启动前端（例如 `serve .`）
- 如何配置 base URL 指向：
  - 本地后端
  - 云端后端
- 同时运行多个客户端实例的方法（rubric 要求）

## 4.4 How Multiple Client Instances Interact with the Service

- 服务如何识别不同客户端（access token → user identity）
- 多客户端对共享 ledger 的读写方式说明

------

# 5. End-to-End Client/Service Testing

（课程要求，原 README 部分内容需迁移整理）

## 5.1 Purpose

- 客户端如何调用服务端形成 E2E 测试
- 手动/半自动均可（rubric 允许）

## 5.2 E2E Test Checklist（必须有）

例如：

1. 注册用户 A、B
2. Login
3. A 创建 ledger
4. 添加成员 B
5. A 创建一笔 expense
6. 客户端 UI 显示交易列表
7. 客户端显示预算状态
8. 客户端点击 settlement → 调用 settlement API

## 5.3 How to Run E2E Tests

- 先启动后端
- 再启动客户端
- 手动执行 checklist

------

# 6. Unit Testing

（原章节存在，但内容不足 → 需替换为 outline）

## 6.1 Testing Framework

- JUnit + Mockito（保留原说明）

## 6.2 Equivalence Partitioning & Boundary Analysis（课程要求）

对于每个主要 unit：

- 列出输入参数
- 有效分区
- 无效分区
- 边界案例
- 对应的测试类名称

## 6.3 How to Run Unit Tests

```
mvn test
```

## 6.4 Unit Tests in CI

说明：

- CI 自动运行所有单测（rubric 要求）

------

# 7. API Testing

 

## 7.1 Automated API Test Scripts

API tests are located in flyingcloud-4156-project/ops/test, there are in total 6 api tests files.

To execute api test, after starting the service, you can simply do ./{file_name}, for example `./api_budget_tests.sh`

**Note: API tests are run manually and are not automated in the CI pipeline.** This is because API tests require:
- A running database instance (MySQL) with specific test data
- A running Spring Boot application server
- Authentication tokens that expire and need to be refreshed during test execution
- Complex state management across multiple API calls (e.g., creating users, then using their tokens)
- Manual verification of complex workflows that are difficult to automate in a CI environment

While these tests could theoretically be automated in CI, it would require setting up database containers, managing application lifecycle, and handling authentication state, which adds significant complexity. The bash scripts provide a practical way to test the complete API workflow manually while still being automated in execution.

Before using bash scripts to test,we've also used Postman to test some APIs ,you can access the test cases here: https://swjy1412-6196945.postman.co/workspace/Jinyi-Wang's-Workspace~67097b2f-bdc0-4997-8ef5-9b20805b25b5/collection/49421217-e21193f8-cfc5-4f6b-bef2-d9b136d6f83d?action=share&source=copy-link&creator=49421217
However due to authentication and database issues, this test cases are not easy to be replicated on local machines, so we switched to using bash scripts.

## 7.2 Equivalence Partitions per Endpoint

Equivalence Pratitions for each api endpoints are documented in this file: `flyingcloud-4156-project/API_EQUIVALENCE_PARTITIONS_COMPLETE.md`

------

# 8. Integration Testing

Integration tests validate the interaction between multiple components and external dependencies. Our integration tests use `@SpringBootTest` with real database connections and `@Transactional` for automatic rollback after each test.

## 8.1 Class Integration Tests

All integration tests are located in `src/test/java/dev/coms4156/project/groupproject/integration/`. The following class combinations have been tested:

### Transaction and Database Flow
**Test File:** `TransactionDatabaseIntegrationTest.java`
**Components:** `TransactionService` → `TransactionMapper` → `TransactionSplitMapper` → `DebtEdgeMapper` → MySQL Database

**Validates:**
- Transaction creation inserts records into `transactions` table
- Transaction splits are correctly written to `transaction_splits` table
- Debt edges are correctly written to `debt_edges` table
- Shared data (transaction_id, ledger_id) is maintained across tables
- Query operations return consistent data

### Transaction and Budget Integration
**Test File:** `TransactionBudgetIntegrationTest.java`
**Components:** `TransactionService` + `BudgetService` + `BudgetMapper` → MySQL Database

**Validates:**
- When an EXPENSE transaction is created, `BudgetService.checkBudgetAfterTransaction()` is automatically called
- Budget alerts are correctly generated when spending approaches or exceeds limits
- Budget status is accurately calculated based on actual transactions
- No budget checks are performed for INCOME transactions
- Integration between transaction creation and real-time budget monitoring

### Ledger and Member Management
**Test File:** `LedgerDatabaseIntegrationTest.java`
**Components:** `LedgerService` → `LedgerMapper` → `LedgerMemberMapper` → MySQL Database

**Validates:**
- Ledger creation inserts to `ledgers` table
- Owner is automatically added to `ledger_members` table with OWNER role
- Member addition creates records in `ledger_members` table
- Member listing returns consistent data
- Shared data (ledger_id, user_id) is correctly maintained

### User Authentication and Database
**Test File:** `UserDatabaseIntegrationTest.java`
**Components:** `UserService` → `UserMapper` → `PasswordUtil` → MySQL Database

**Validates:**
- User registration inserts to `users` table with hashed password
- Password hashing and verification work correctly
- User login retrieves correct user data from database
- Email uniqueness constraints are enforced

### Currency Exchange
**Test File:** `CurrencyDatabaseIntegrationTest.java`
**Components:** `CurrencyService` → `CurrencyMapper` → MySQL Database

**Validates:**
- Currency listing retrieves all currencies from `currency` table
- Exchange rate calculations use real database data
- Currency code validation works with actual database records

### Analytics Aggregation
**Test File:** `AnalyticsDatabaseIntegrationTest.java`
**Components:** `AnalyticsService` → `AnalyticsAggMapper` → MySQL Database

**Validates:**
- Complex SQL aggregation queries execute correctly
- Ledger overview analytics aggregate data from multiple tables
- Category statistics are calculated correctly
- Time-based filtering works with real transaction data

### Budget Mapper
**Test File:** `BudgetMapperDatabaseIntegrationTest.java`
**Components:** `BudgetMapper` → MySQL Database

**Validates:**
- Budget CRUD operations work correctly
- Budget queries by ledger and time period return accurate data
- Budget aggregation queries calculate spending correctly

### Ledger Member Mapper
**Test File:** `LedgerMemberDatabaseIntegrationTest.java`
**Components:** `LedgerMemberMapper` → MySQL Database

**Validates:**
- Member role queries work correctly
- Member listing by ledger returns accurate data
- Member deletion maintains database consistency

## 8.2 External Integration Tests

### MySQL Database
All integration tests connect to a real MySQL database instance. Tests use:
- **Local Development:** MySQL running on `localhost:3306`
- **GitHub CI:** MySQL 8.0 service container (see CI Integration section)
- **Schema:** Database schema is initialized from `ops/sql/ledger_flow.sql`
- **Test Data:** Initial seed data is loaded from `ops/sql/test_data_seed.sql`
- **Isolation:** Each test is wrapped in `@Transactional` annotation, ensuring automatic rollback after test completion

### Redis
Redis is used for token/session management:
- **Local Development:** Redis running on `localhost:6379`
- **GitHub CI:** Redis 7-alpine service container
- **Purpose:** Stores access tokens and refresh tokens for user authentication

### Filesystem
No filesystem integration is used in this service.

### Third-Party APIs
None. All external dependencies are limited to database and Redis.

## 8.3 Running Integration Tests

Integration tests are included in the Maven verify phase. To run all tests (unit + integration):

```bash
mvn test -Dtest="dev.coms4156.project.groupproject.integration.*Test"
```

## 8.4 CI Integration

Integration tests are automatically executed in GitHub Actions CI for every push and pull request.

------

# 9. Branch Coverage Report

You can run the command to run all the tests and check the coverage report located in target/site/jacoco/index.html. Current coverage is 80%.

<code>mvn clean test verify</code>

![branch coverage](images/coverage2.png)

# 10. Static Code Analysis


## 10.1 Running Static Analysis
Run mvn pmd:pmd to generate the static code analysis and check the report in target/site/pmd.html.
![pmd](images/pmd2.png)

## 10.2 Style Checking

We used the command <code>mvn checktyle:check</code> to check the style of our code and generate style checking. 
![check style](images/checkstyle2.png)

------

# 11. Continuous Integration

（原 README 缺乏，需要补充）

## 11.1 CI Pipeline Overview

- CI 自动执行内容：
  - Style check
  - PMD
  - Unit tests
  - API tests（若不能自动化则解释）
  - Integration tests
  - Coverage

## 11.2 CI Configuration Files

- `.github/workflows/ci.yml`

## 11.3 Sample CI Output

- 截图或文本文件（rubric 要求）

------

# 12. Project Management

（原 README 缺乏 → 需要补充）

## 12.1 Tool Used

- GitHub Projects / Jira / Trello（你们团队使用哪一个）

## 12.2 Workflow

- To-do → In progress → Done
- 每位成员负责内容（rubric 要求）

------

# 13. Third-Party Code

（原 README 没有，需要新增）

说明：

- 使用的所有第三方库均由 Maven 下载
- 若有嵌入式第三方代码（几乎没有）则必须列出

------

# 14. AI Documentation

（原 README 已有 → 保留并扩大）

必须包括：

- 使用 AI 的部分
- 你如何修改、验证、人工审查
- Prompt examples

------

# 15. Submission Notes

（新增最后小节）

- 提交的 tag 名称
- 提交的 repo URL
- 声明：只评分 main branch + tag（rubric 要求）