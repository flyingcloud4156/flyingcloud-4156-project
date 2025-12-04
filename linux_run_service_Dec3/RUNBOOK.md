Below is the **precise and faithful English translation** of your complete document.

---

# FlyingCloud 4156 Project – Linux Direct Deployment Guide (No Docker)

# Updated December 3, 2025

This document explains how to directly deploy and run the project on a Linux server **without using Docker containers**. It covers two scenarios:

* **Scenario A**: Redeployment (delete existing database and reinitialize)
* **Scenario B**: First-time deployment on a new server

**Notes**:

* All commands assume your project path is `/home/zh2701/flyingcloud-4156-project`
* Root privileges are required to install system software and services
* Ensure that ports 8081, 3306, and 6379 are available
* Back up important data before performing cleanup operations

---

## System Requirements

* Ubuntu/Debian/CentOS/RHEL Linux distributions
* Root or sudo privileges
* Internet connection (to download packages)
* Node.js 16+ and npm (for frontend)
* Java 17+ (for backend)
* Maven 3.6+ (for building backend)

## Health Check Endpoints and URLs

* **Backend API**: `http://<HOST>:8081`
* **Swagger UI**: `http://<HOST>:8081/swagger-ui.html`
* **OpenAPI JSON**: `http://<HOST>:8081/v3/api-docs`
* **Frontend**: `http://<HOST>:3000` (when started)

---

# Scenario A — Redeployment (Existing MySQL/Redis/Application Running)

Use this scenario when you want to delete the existing database and reinitialize it.

## Step 1: Stop Services

```bash
# Stop the Spring Boot application (if running)
sudo systemctl stop flyingcloud-app 2>/dev/null || true

# Stop MySQL and Redis services
sudo systemctl stop mysql
sudo systemctl stop redis-server
```

## Step 2: Delete and Recreate the Database

```bash
# Delete the existing database
sudo mysql -u root -p -e "DROP DATABASE IF EXISTS ledger;"

# Create a new database
sudo mysql -u root -p -e "CREATE DATABASE ledger CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# Or if no root password is set:
# sudo mysql -e "DROP DATABASE IF EXISTS ledger;"
# sudo mysql -e "CREATE DATABASE ledger CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
```

## Step 3: Initialize the Database Schema

```bash
cd /home/zh2701/flyingcloud-4156-project

# Import database schema
sudo mysql -u root -p ledger < ops/sql/ledger_flow.sql

# Or if no root password:
# sudo mysql ledger < ops/sql/ledger_flow.sql
```

## Step 4: Load Seed Data (Optional, for demo)

```bash
# Load big seed data (includes demo data)
sudo mysql -u root -p ledger < ops/sql/backup/ledger_big_seed.sql

# Or without root password:
# sudo mysql ledger < ops/sql/backup/ledger_big_seed.sql
```

## Step 5: Start Services

```bash
# Start MySQL and Redis
sudo systemctl start mysql
sudo systemctl start redis-server

# Start the Spring Boot application
sudo systemctl start flyingcloud-app
```

## Step 6: Verify Service Status

```bash
# Check service status
sudo systemctl status mysql
sudo systemctl status redis-server
sudo systemctl status flyingcloud-app

# Check port listening
netstat -tlnp | grep -E ':(3306|6379|8081)'

# Check application logs
sudo journalctl -u flyingcloud-app -f
```

## Step 7: Test API

```bash
# Register user example
curl -sS -X POST "http://localhost:8081/api/v1/auth/register" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","name":"TestUser","password":"SecurePass123!"}'

# Login to obtain token
curl -sS -X POST "http://localhost:8081/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","password":"SecurePass123!"}'

# Call protected API using token (replace TOKEN with actual token)
curl -sS -X GET "http://localhost:8081/api/v1/users/me" \
  -H "X-Auth-Token: TOKEN"
```

---

# Scenario B — First-Time Deployment on a New Server

## Step 1: Update System and Install Basic Tools

```bash
# Update package list
sudo apt update  # Ubuntu/Debian
# Or CentOS/RHEL: sudo yum update or sudo dnf update

# Install basic utilities
sudo apt install -y wget curl vim htop net-tools  # Ubuntu/Debian
# Or CentOS/RHEL: sudo yum install -y wget curl vim htop net-tools
```

## Step 1.5: Install Node.js (for Frontend)

```bash
# Ubuntu/Debian
curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
sudo apt-get install -y nodejs

# CentOS/RHEL 8+
curl -fsSL https://rpm.nodesource.com/setup_18.x | sudo bash -
sudo dnf install -y nodejs

# Verify installation
node --version
npm --version
```

## Step 2: Install Java 17+ (For Spring Boot)

```bash
# Ubuntu/Debian
sudo apt install -y openjdk-17-jdk

# CentOS/RHEL 8+
sudo dnf install -y java-17-openjdk-devel

# Verify installation
java -version
javac -version
```

## Step 3: Install MySQL 8.0

```bash
# Ubuntu/Debian
sudo apt install -y mysql-server-8.0

# CentOS/RHEL
sudo dnf install -y mysql-server

# Start and enable MySQL
sudo systemctl start mysql
sudo systemctl enable mysql

# Security configuration (set root password, etc.)
sudo mysql_secure_installation
```

## Step 4: Install Redis

```bash
# Ubuntu/Debian
sudo apt install -y redis-server

# CentOS/RHEL
sudo dnf install -y redis

# Start and enable Redis
sudo systemctl start redis-server
sudo systemctl enable redis-server
```

## Step 5: Clone Project Code

```bash
# Create project directory
mkdir -p /home/user
cd /home/user

# Clone project (replace with actual repo URL)
git clone <your-repo-url> flyingcloud-4156-project
cd  /home/zh2701/flyingcloud-4156-project

# Or directly copy project files to this directory
```

## Step 6:  Create Database

```bash
# Create database
sudo mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS ledger CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# Import schema
sudo mysql -u root -p ledger < ops/sql/ledger_flow.sql

# Import seed data (optional)
sudo mysql -u root -p ledger < ops/sql/backup/ledger_big_seed.sql
```

## Step 7:Build Project

```bash
# Ensure Maven is available (if not installed)
sudo apt install -y maven  # Ubuntu/Debian

# Build project
./mvnw clean package -DskipTests

# Or use system Maven
mvn clean package -DskipTests
```

## Step 8: Create System Service (Optional, for easier management)

```bash
# Create systemd service file for Spring Boot app
sudo tee /etc/systemd/system/flyingcloud-app.service > /dev/null <<EOF
[Unit]
Description=FlyingCloud 4156 Spring Boot Application
After=network.target mysqld.service redis-server.service

[Service]
Type=simple
User=zh2701
Group=zh2701
WorkingDirectory=/home/zh2701/flyingcloud-4156-project
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DB_URL=jdbc:mysql://localhost:3306/ledger?useSSL=false&serverTimezone=America/New_York&characterEncoding=utf8&allowPublicKeyRetrieval=true
Environment=DB_USER=root
Environment=DB_PASS=
Environment=REDIS_HOST=localhost
Environment=REDIS_PORT=6379
Environment=REDIS_PASSWORD=
ExecStart=/usr/bin/java -jar /home/zh2701/flyingcloud-4156-project/target/flyingcloud.jar
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
EOF

# Reload systemd
sudo systemctl daemon-reload

# Start service
sudo systemctl start flyingcloud-app
sudo systemctl enable flyingcloud-app
```

## Step 9: Configure Firewall (If Needed)

```bash

allow 8081
allow 3306
allow 6379

```

## Step 10: Verify Deployment

```bash
# Check service status
sudo systemctl status mysql
sudo systemctl status redis-server
sudo systemctl status flyingcloud-app

# Check ports
netstat -tlnp | grep -E ':(3306|6379|8081)'

# Test API
curl -s "http://localhost:8081/api/v1/auth/login" | head -5

# View application logs
sudo journalctl -u flyingcloud-app -f
```

## Step 11: Start Frontend (Optional)

```bash
cd /home/zh2701/flyingcloud-4156-project/frontend
npm start
# Install frontend dependencies
npm install

# Start frontend development server
npm start
```

The frontend will be available at `http://localhost:3000` (default serve port).

**Note**: The frontend is a static HTML/CSS/JS application that connects to the backend API at `http://localhost:8081`.

---

# Manually Start Application (Without systemd)

If you do not want to create a system service, you can run the applications manually:

## Backend (Spring Boot)

```bash
cd /home/zh2701/flyingcloud-4156-project

# Set environment variables
export DB_URL="jdbc:mysql://localhost:3306/ledger?useSSL=false&serverTimezone=America/New_York&characterEncoding=utf8&allowPublicKeyRetrieval=true"
export DB_USER="root"
export DB_PASS=""
export REDIS_HOST="localhost"
export REDIS_PORT="6379"
export REDIS_PASSWORD=""
export SPRING_PROFILES_ACTIVE="prod"

# Run the application in background
nohup ./mvnw spring-boot:run > app.log 2>&1 &

# Or using system Maven
# nohup mvn spring-boot:run > app.log 2>&1 &

# Check process
ps aux | grep java

# View logs
tail -f app.log
```

## Frontend (Static Server)

```bash
cd /home/zh2701/flyingcloud-4156-project/frontend

# Install dependencies
npm install

# Start frontend server
npm start
```

**Note**: Frontend runs on port 3000, backend runs on port 8081.

---

# Operations Commands

## Service Management

```bash
# Start services
sudo systemctl start flyingcloud-app
sudo systemctl start mysql
sudo systemctl start redis-server

# Stop services
sudo systemctl stop flyingcloud-app
sudo systemctl stop mysql
sudo systemctl stop redis-server

# Restart service
sudo systemctl restart flyingcloud-app

# Check service status
sudo systemctl status flyingcloud-app
sudo systemctl status mysql
sudo systemctl status redis-server
```

## Log Viewing

```bash
# Spring Boot application logs
sudo journalctl -u flyingcloud-app -f

# If running manually, view application logs
tail -f /home/zh2701/flyingcloud-4156-project/app.log

# MySQL logs
sudo tail -f /var/log/mysql/mysql.log

# Redis logs
sudo tail -f /var/log/redis/redis-server.log
```

## Database Management

```bash
# Connect to MySQL
mysql -u root -p -h localhost

# View databases
mysql -u root -p -e "SHOW DATABASES;"

# View tables
mysql -u root -p -e "USE ledger; SHOW TABLES;"

# View data
mysql -u root -p -e "USE ledger; SELECT COUNT(*) FROM users; SELECT COUNT(*) FROM transactions;"
```

## Application Restart

```bash
cd /home/zh2701/flyingcloud-4156-project

# Rebuild
./mvnw clean package -DskipTests

# Restart service
sudo systemctl restart flyingcloud-app
```

---

# API Authentication Details

* Login API returns JSON containing `accessToken` and `refreshToken`
* Protected APIs require header: `X-Auth-Token: <accessToken>`
* Access Token validity: **2 hours**
* Refresh Token validity: **14 days**
* Refresh token using `/api/v1/auth/refresh?refreshToken=...`

---

# Troubleshooting

## Application Cannot Connect to Database

```bash
# Check if MySQL is running
sudo systemctl status mysql

# Check MySQL port
netstat -tlnp | grep 3306

# Test database connection
mysql -u root -p -e "SELECT 1;"

# Check DB URL in app logs
sudo journalctl -u flyingcloud-app | grep -i "jdbc\|datasource"
```

## Redis Connection Failure

```bash
# Check Redis status
sudo systemctl status redis-server

# Check Redis port
netstat -tlnp | grep 6379

# Test Redis connection
redis-cli ping
```

## Port Conflicts

```bash
# Check port usage
netstat -tlnp | grep -E ':(8081|3306|6379)'

# Kill the process occupying port
sudo kill -9 <PID>
```

## Java Version Issue

```bash
# Check Java version
java -version

# Switch Java version if needed
sudo update-alternatives --config java
```

## Insufficient Memory

```bash
# Check system memory
free -h

# Check application usage
ps aux | grep java

# To configure JVM parameters:
# Edit /etc/systemd/system/flyingcloud-app.service
# Add: Environment=JAVA_OPTS="-Xmx1g -Xms512m"
```

---

# Backup and Restore

## Database Backup

```bash
# Backup database
mysqldump -u root -p ledger > ledger_backup_$(date +%Y%m%d_%H%M%S).sql

# Compress backup
gzip ledger_backup_*.sql
```

## Database Restore

```bash
# Restore database
gunzip ledger_backup_20231203_120000.sql.gz
mysql -u root -p ledger < ledger_backup_20231203_120000.sql
```

---

# Performance Monitoring

## System Resource Monitoring

```bash
# CPU & memory usage
top
htop

# Disk usage
df -h

# Network connections
netstat -tlnp
```

## Application Performance Monitoring

```bash
# JVM thread details
jps -l
jstack <PID>

# App metrics (if Actuator is enabled)
curl http://localhost:8081/actuator/health
curl http://localhost:8081/actuator/metrics
```

---

# Security Recommendations

1. **Change default passwords** — never use default root passwords
2. **Firewall configuration** — only open necessary ports
3. **SSL certificate** — configure HTTPS for production
4. **Database users** — create a dedicated DB user for the app; avoid using root
5. **Regular backups** — set up automated backup scripts
6. **Log rotation** — configure rotation to prevent disk from filling up

---

# Frontend Deployment

## Frontend Overview

The frontend is a static HTML/CSS/JavaScript application that serves as the user interface for the Ledger application.

## Configure Frontend API URL

Before starting the frontend, configure the API URL to point to your backend server:

```bash
# Option 1: Automatic configuration (recommended)
cd /home/zh2701/flyingcloud-4156-project
./linux_run_service_Dec3/configure_frontend.sh

# Option 2: Manual configuration
# Edit frontend/index.html and change the baseUrl input value
# from: value="http://localhost:8081"
# to:   value="http://YOUR_SERVER_IP:8081"
```

## Start Frontend

```bash
cd /home/zh2701/flyingcloud-4156-project/frontend

# Install dependencies (if not already installed)
npm install

# Start the development server
npm start
```

The frontend will be available at `http://YOUR_SERVER_IP:3000` (default serve port).

## Frontend Files

- `index.html` - Login page
- `dashboard.html` - Main dashboard (may not be used in current implementation)
- `login.js` - Login page logic
- `dashboard.js` - Dashboard logic (may not be used)
- `style.css` - Application styles

## Configuration

The frontend connects to the backend API. By default it uses `http://localhost:8081`. You can change this in the login form.

---

# Deployment Updates

When updating the code:

```bash
cd /home/zh2701/flyingcloud-4156-project

# Pull latest code
git pull

# Rebuild backend
./mvnw clean package -DskipTests

# Restart backend service
sudo systemctl restart flyingcloud-app

# Update frontend (if needed)
cd frontend
npm install
npm start

# Check status
sudo systemctl status flyingcloud-app
```

---

If you'd like, I can **format this as a PDF**, **improve the English wording**, or **convert it into a Markdown guide**.
