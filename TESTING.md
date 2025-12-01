# Ledger 应用测试指南

## 概述

这个项目是一个账目管理应用，支持多用户、多账本的账目跟踪和分析。包含后端API、前端界面和数据库。

## 前置要求

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Node.js (可选，用于前端开发)
- Git

## 快速开始

### 1. 克隆和构建项目

```bash
git clone <repository-url>
cd flyingcloud-4156-project
mvn clean compile
```

### 2. 启动MySQL数据库

确保MySQL服务运行，并创建数据库：

```bash
mysql -u root -p
CREATE DATABASE ledger;
# 退出MySQL
```

### 3. 配置数据库连接

编辑 `application.yml` 或 `application.yaml` 中的数据库配置：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/ledger
    username: your_username
    password: your_password
```

### 4. 初始化数据库结构

运行数据库初始化脚本：

```bash
mysql -u root -p ledger < ops/sql/ledger_flow.sql
```

### 5. 加载测试数据

加载演示数据：

```bash
mysql -u root -p ledger < ops/sql/backup/ledger_big_seed.sql
```

## 启动服务

### 启动后端服务

```bash
mvn spring-boot:run
```

后端将在 `http://localhost:8081` 启动。

### 启动前端服务

在另一个终端中：

```bash
cd frontend
python3 -m http.server 3000
```

或者使用Node.js：

```bash
npx http-server frontend -p 3000
```

前端将在 `http://localhost:3000` 提供服务。

## 测试用户账户

系统预置了以下测试账户：

| 用户名 | 邮箱 | 密码 |
|--------|------|------|
| Alice Seed | alice.seed@example.com | Passw0rd! |
| Bob Seed | bob.seed@example.com | Passw0rd! |
| Charlie Seed | charlie.seed@example.com | Passw0rd! |
| Diana Seed | diana.seed@example.com | Passw0rd! |
| Evan Seed | evan.seed@example.com | Passw0rd! |
| Fay Seed | fay.seed@example.com | Passw0rd! |
| Gina Seed | gina.seed@example.com | Passw0rd! |

## 测试场景

### 1. 用户认证测试

1. 打开浏览器访问 `http://localhost:3000`
2. 输入邮箱：`alice.seed@example.com`
3. 输入密码：`Passw0rd!`
4. 点击"Login"按钮
5. 验证登录成功，界面显示账本选择

### 2. 账本数据查看

1. 登录后，从下拉菜单选择账本
2. 点击"Load Analytics"按钮
3. 验证显示：
   - 总收入和支出
   - 收入支出趋势图
   - 按类别支出饼图
   - AR/AP债务汇总表
   - 热门商户列表

### 3. API接口测试

#### 登录接口

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"alice.seed@example.com","password":"Passw0rd!"}'
```

#### 获取用户账本

```bash
curl -X GET "http://localhost:8081/api/v1/ledgers/mine" \
  -H "X-Auth-Token: YOUR_ACCESS_TOKEN"
```

#### 获取分析数据

```bash
curl -X GET "http://localhost:8081/api/v1/ledgers/1/analytics/overview?months=3" \
  -H "X-Auth-Token: YOUR_ACCESS_TOKEN"
```

## 演示账本数据

系统包含两个演示账本：

### Road Trip Demo (ID: 1)
- 用户：Alice, Bob, Charlie, Diana
- 包含9笔交易：加油、食物、住宿、娱乐、用品
- 涵盖2025年9-11月的旅行支出

### Apartment Demo (ID: 2)
- 用户：Diana, Evan, Fay, Gina
- 包含10笔交易：房租、水电费、杂货、餐饮
- 涵盖2025年9-11月的公寓开支

## 故障排除

### 常见问题

#### 1. 登录失败
- 检查后端服务是否运行在8081端口
- 验证数据库中是否加载了seed数据
- 确认密码输入正确（包含感叹号）

#### 2. 前端无法加载
- 确保前端通过HTTP服务器运行（不要直接打开HTML文件）
- 检查前端服务器端口（默认3000）

#### 3. CORS错误
- 前端必须通过HTTP服务器访问，不能直接file://协议
- 后端CORS配置允许localhost和127.0.0.1

#### 4. 数据库连接失败
- 检查MySQL服务是否运行
- 验证数据库配置正确
- 确认数据库已创建并加载数据

### 日志查看

后端日志会显示详细的错误信息：

```bash
# 查看应用启动日志
mvn spring-boot:run

# 或查看已运行的日志
tail -f logs/application.log
```

## 自动化测试

运行单元测试：

```bash
mvn test
```

运行集成测试：

```bash
mvn verify
```

## 生产部署

### 使用Docker

```bash
# 构建镜像
docker build -t ledger-app .

# 运行容器
docker run -p 8081:8081 -e SPRING_PROFILES_ACTIVE=prod ledger-app
```

### 使用Docker Compose

```bash
docker-compose up -d
```

## 更多信息

- API文档：启动服务后访问 `http://localhost:8081/swagger-ui.html`
- 项目文档：查看 `README.md`
- 数据库文档：查看 `ops/sql/ledger_flow.sql` 中的注释
