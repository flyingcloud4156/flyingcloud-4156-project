# Ledger Application - Setup and Testing Guide

This document provides a comprehensive guide to setting up the development environment, running the application, and executing the various test suites for the Ledger project.

## 1. Prerequisites

Before you begin, ensure you have the following software installed on your system (macOS is assumed):

- **Homebrew**: The Missing Package Manager for macOS. Used for installing services.
- **Java Development Kit (JDK)**: Version 17 or later.
- **MySQL**: The database used by the application.
- **Redis**: The in-memory cache used by the application.
- **Node.js & npm**: Required for running frontend tests. The build process will install a local version, but a global installation is recommended for manual test runs.

## 2. Service Installation and Setup

### 2.1. Install Services

```bash
brew install mysql
brew install redis
```

### 2.2. Start Services

```bash
brew services start mysql
brew services start redis
```

## 3. Database Initialization

1. **Connect to MySQL**:
   ```bash
   mysql -u root -p
   ```

2. **Create the Database**:
   ```sql
   CREATE DATABASE ledger;
   ```

3. **Import Seed Data**:
   ```bash
   mysql -u root -p ledger < ops/sql/backup/ledger_big_seed.sql
   ```

## 4. Environment Configuration

Export the following variables:

```bash
export DB_URL="jdbc:mysql://localhost:3306/ledger?useSSL=false&serverTimezone=America/New_York&characterEncoding=utf8&allowPublicKeyRetrieval=true"
export DB_USER="root"
export DB_PASS="your_mysql_root_password"

export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD="redisroot"
```


## 5. Running the Application

```bash
cd /Users/Shared/BackendProject/flyingcloud-4156-project
./mvnw spring-boot:run
```

for example
```bash
brew services start redis
brew services start mysql

export DB_URL="jdbc:mysql://localhost:3306/ledger?useSSL=false&serverTimezone=America/New_York&characterEnco
  ding=utf8&allowPublicKeyRetrieval=true"
  export DB_USER="root"
  export DB_PASS="your_mysql_root_password"
  export REDIS_HOST="localhost"
  export REDIS_PORT="6379"
  export REDIS_PASSWORD="redisroot"

  cd /Users/Shared/BackendProject/flyingcloud-4156-project
  ./mvnw spring-boot:run
  
```
## 6. Running Tests

### 6.1. Comprehensive Build

```bash
./mvnw clean verify
```

### 6.2. Backend Tests

Located under `src/test/java`.

### 6.3. Frontend Unit Tests

```bash
cd frontend
npm install
npm test
```

### 6.4. API E2E Tests

```bash
bash test_expense_transaction.sh
```
