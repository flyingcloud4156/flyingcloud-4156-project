# Ledger Application Testing Guide

## Overview

This is a comprehensive ledger management application that supports multi-user, multi-ledger financial tracking and analytics. The application consists of:

- **Backend**: Spring Boot REST API with MySQL database
- **Frontend**: JavaScript application with Chart.js for analytics visualization
- **Testing**: Comprehensive test suite including unit tests, integration tests, and API tests

## Prerequisites

### System Requirements
- Java 17 or higher
- Maven 3.6 or higher
- MySQL 8.0 or higher
- Node.js 20.11.1 (automatically downloaded by Maven plugin)
- Redis (for session management)

### Development Tools
- Git
- curl (for API testing)
- jq (for JSON processing)

## Installation and Setup

### 1. Clone the Repository
```bash
git clone <repository-url>
cd flyingcloud-4156-project
```

### 2. Build the Project
```bash
mvn clean compile
```

### 3. Database Setup

#### Start MySQL Service
```bash
# macOS with Homebrew
brew services start mysql

# Or start manually
mysql.server start
```

#### Create Database
```bash
mysql -u root -p
CREATE DATABASE ledger;
exit;
```

#### Load Schema and Test Data
```bash
# Load database schema
mysql -u root -p ledger < ops/sql/ledger_flow.sql

# Load demo data for UI testing and API tests
mysql -u root -p ledger < ops/sql/backup/ledger_big_seed.sql
```

**Note**: The API test scripts (`ops/test/api_all.sh`) automatically reset the database and load fresh test data, so manual loading is only needed for manual testing.

## Running the Application

### Start Backend API Server
```bash
# Set environment variables for database connection
export DB_URL="jdbc:mysql://localhost:3306/ledger?useSSL=false&serverTimezone=America/New_York&characterEncoding=utf8&allowPublicKeyRetrieval=true"
export DB_USER="root"
export DB_PASS=""
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD=""

# Start the application
mvn spring-boot:run
```
The API server will start on `http://localhost:8081`

### Start Frontend Development Server
```bash
cd frontend
python3 -m http.server 3000
```
Or using Node.js:
```bash
npx http-server frontend -p 3000
```
The frontend will be available at `http://localhost:3000`

## Testing Steps

### Automated Test Suite

#### Run Complete Test Suite (Recommended)
```bash
mvn clean verify
```

This command runs:
- Backend unit tests (JUnit 5)
- Frontend unit tests (Jest)
- Code quality checks (Checkstyle, PMD, Spotless)
- Test coverage report (JaCoCo)

#### Run Backend Tests Only
```bash
mvn test
```

#### Run Frontend Tests Only
```bash
cd frontend
npm test
```

### API Integration Tests

#### Test All API Endpoints
```bash
# Start MySQL and Redis services first
brew services start mysql
brew services start redis

# Set environment variables
export DB_PASS=""

# Run comprehensive API tests
bash ops/test/api_all.sh
```

#### Test Negative/Boundary Cases
```bash
# Set environment variables
export DB_PASS=""

# Run negative/boundary API tests
bash ops/test/api_negative.sh
```

#### Manual API Testing with curl

##### User Registration
```bash
curl -X POST http://localhost:8081/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "name": "Test User",
    "password": "Passw0rd!"
  }'
```

##### User Login
```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "password": "Passw0rd!"
  }'
```

##### Get User Ledgers
```bash
curl -X GET "http://localhost:8081/api/v1/ledgers/mine" \
  -H "X-Auth-Token: YOUR_ACCESS_TOKEN"
```

### Manual Frontend Testing

#### 1. Open Frontend Application
Navigate to `http://localhost:3000` in your web browser.

#### 2. Login with Test Account
Use one of the pre-configured test accounts from seed data:
- **Email**: `alice@gmail.com`
- **Password**: `Passw0rd!`

#### 3. Test Ledger Operations
- Select a ledger from the dropdown
- Click "Load Analytics" to view financial data
- Verify the following components load correctly:
  - Total income/expense/net balance
  - Trend chart (income vs expense over time)
  - Expense breakdown by category (pie chart)
  - AR/AP debt summary table
  - Top merchants list

#### 4. Test Different Ledgers
The system includes two demo ledgers:
- **Road Trip Demo**: Travel expenses for 4 users
- **Apartment Demo**: Shared living expenses for 4 users

### Database Verification

#### Check Transaction Data
```bash
mysql -u root -p ledger -e "SELECT id, type, amount_total, note FROM transactions LIMIT 10;"
```

#### Check User Balances
```bash
mysql -u root -p ledger -e "SELECT * FROM ledger_user_balances;"
```

#### Check Debt Relationships
```bash
mysql -u root -p ledger -e "SELECT from_user_id, to_user_id, amount FROM debt_edges LIMIT 10;"
```

## Test Data

### Pre-configured Test Users
| Name | Email | Password |
|------|-------|----------|
| Alice | alice@gmail.com | Passw0rd! |
| Bob | bob@gmail.com | Passw0rd! |
| Charlie | charlie@gmail.com | Passw0rd! |
| Diana | diana@gmail.com | Passw0rd! |
| Evan | evan@gmail.com | Passw0rd! |
| Fay | fay@gmail.com | Passw0rd! |
| Gina | gina@gmail.com | Passw0rd! |

### Demo Ledgers
- **Road Trip Demo** (ID: 1): Travel expenses across multiple months
- **Apartment Demo** (ID: 2): Shared apartment costs

## CI/CD Integration

### GitHub Actions Example
```yaml
name: CI/CD Pipeline

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest

    services:
      mysql:
        image: mysql:8.0
        env:
          MYSQL_ROOT_PASSWORD: root
          MYSQL_DATABASE: ledger
        ports:
          - 3306:3306
      redis:
        image: redis:alpine
        ports:
          - 6379:6379

    steps:
    - uses: actions/checkout@v3

    - name: Setup Java
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Setup MySQL
      run: |
        sudo apt-get update
        sudo apt-get install -y mysql-client
        mysql -h 127.0.0.1 -u root -proot -e "CREATE DATABASE ledger;"

    - name: Load Test Data
      run: |
        mysql -h 127.0.0.1 -u root -proot ledger < ops/sql/ledger_flow.sql
        mysql -h 127.0.0.1 -u root -proot ledger < ops/sql/backup/ledger_big_seed.sql

    - name: Run Tests
      run: mvn clean verify
```

## Troubleshooting

### Common Issues

#### Backend Won't Start
- **Issue**: Port 8081 already in use
- **Solution**: Kill the process using the port or change the port in `application.yaml`

#### Database Connection Failed
- **Issue**: MySQL connection refused
- **Solution**:
  ```bash
  brew services start mysql
  mysql -u root -p -e "CREATE DATABASE ledger;"
  ```

#### Frontend Tests Fail
- **Issue**: Node.js version mismatch
- **Solution**: The Maven plugin automatically downloads the correct Node.js version

#### API Tests Fail
- **Issue**: Services not running or environment variables not set
- **Solution**:
  ```bash
  # Ensure services are running
  brew services start mysql
  brew services start redis

  # Set required environment variables
  export DB_PASS=""

  # Run tests
  bash ops/test/api_all.sh
  ```

### Debug Mode

#### Enable Debug Logging
```bash
# Backend debug logging
mvn spring-boot:run -Dspring-boot.run.arguments="--logging.level.dev.coms4156.project.groupproject=DEBUG"

# Frontend debug mode
cd frontend
npm test -- --verbose
```

#### Check Service Status
```bash
# Check if backend is running
curl -s http://localhost:8081/api/v1/auth/login

# Check database connectivity
mysql -u root -p -e "SELECT 1;"

# Check Redis connectivity
redis-cli ping
```

### Performance Testing

#### Load Testing with Apache Bench
```bash
# Simple load test
ab -n 100 -c 10 http://localhost:8081/api/v1/auth/login

# API endpoint load test
ab -n 1000 -c 50 -T 'application/json' -p payload.json http://localhost:8081/api/v1/auth/login
```

## Test Coverage

### Backend Test Coverage
- **Unit Tests**: 141 JUnit 5 test cases covering service and controller layers
- **Integration Tests**: Comprehensive API endpoint testing via bash scripts
- **Database Tests**: Schema validation and data integrity checks

### Frontend Test Coverage
- **Unit Tests**: 5 Jest test cases covering UI logic and data formatting
- **DOM Testing**: JSDOM-based component interaction tests
- **API Integration**: Mock-based API call testing

### Code Quality Metrics
- **Checkstyle**: Code style validation
- **PMD**: Static code analysis
- **Spotless**: Code formatting
- **JaCoCo**: Test coverage reporting (target: >80%)

## Contributing

### Adding New Tests

#### Backend Unit Tests
```java
@Test
void testNewFeature() {
    // Arrange
    // Act
    // Assert
}
```

#### Frontend Unit Tests
```javascript
test('renders correctly', () => {
    // Test implementation
});
```

#### API Integration Tests
Add test cases to `ops/test/api_all.sh` following the existing pattern.

### Test Data Management
- Update `ops/sql/ledger_flow.sql` for schema changes
- Update `ops/sql/backup/ledger_big_seed.sql` for test data
- Ensure backward compatibility with existing tests

## Support

For issues or questions:
1. Check this testing guide
2. Review the troubleshooting section
3. Check application logs in `spring-boot.log`
4. Review test output in `target/surefire-reports/`
5. Create an issue with detailed reproduction steps
