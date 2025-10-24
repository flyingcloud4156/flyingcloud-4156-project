# API Documentation

This document provides detailed information about the Ledger API, including authentication, available endpoints, and usage examples.

## Base URL

All API endpoints are prefixed with `/api/v1`.

## Authentication

Most endpoints require authentication using an access token. The token must be provided in the `X-Auth-Token` header of your request.

To obtain an access token, you must first register a user and then log in.

### Testing Order

The endpoints are documented in a logical order for testing. It is recommended to follow this order to obtain necessary data (like tokens and IDs) for subsequent requests.

---

## 1. Authentication

### 1.1 Register a New User

Creates a new user account.

- **Method**: `POST`
- **Endpoint**: `/api/v1/auth/register`
- **Permissions**: Public

**Request Body:**

```json
{
  "email": "user@example.com",
  "name": "Test User",
  "password": "S3cure!1"
}
```

**cURL Example:**

```bash
curl -X POST 'http://localhost:8081/api/v1/auth/register' \
-H 'Content-Type: application/json' \
-d '{
  "email": "user@example.com",
  "name": "Test User",
  "password": "S3cure!1"
}'
```

### 1.2 Log In to Get Access Token

Authenticates a user and returns an `accessToken` and `refreshToken`.

- **Method**: `POST`
- **Endpoint**: `/api/v1/auth/login`
- **Permissions**: Public

**Request Body:**

```json
{
  "email": "user@example.com",
  "password": "S3cure!1"
}
```

**Successful Response (200 OK):**

```json
{
    "success": true,
    "message": "OK",
    "data": {
        "accessToken": "your-access-token",
        "refreshToken": "your-refresh-token"
    },
    "total": null
}
```

**cURL Example:**

```bash
# Replace with your actual server address
# The accessToken returned here will be used in the X-Auth-Token header for subsequent requests
curl -X POST 'http://localhost:8081/api/v1/auth/login' \
-H 'Content-Type: application/json' \
-d '{
  "email": "user@example.com",
  "password": "S3cure!1"
}'
```

---

## 2. Currencies

### 2.1 Get Supported Currencies

Retrieves a list of all supported currencies.

- **Method**: `GET`
- **Endpoint**: `/api/v1/currencies`
- **Permissions**: Authenticated User

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN> with the token from the login step
curl -X GET 'http://localhost:8081/api/v1/currencies' \
-H 'accept: */*' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>'
```

---

## 3. Users

### 3.1 Look Up User by Email

Finds a user\'s ID and name by their email address. This is useful for adding members to a ledger.

- **Method**: `GET`
- **Endpoint**: `/api/v1/users:lookup`
- **Permissions**: Authenticated User

**Request Parameters:**

- `email` (string, required): The email of the user to look up.

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN> and the email with actual values
curl -X GET 'http://localhost:8081/api/v1/users:lookup?email=user@example.com' \
-H 'accept: */*' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>'
```

---

## 4. Ledgers

### 4.1 Create a Ledger

Creates a new ledger. The creator automatically becomes the `OWNER`.

- **Method**: `POST`
- **Endpoint**: `/api/v1/ledgers`
- **Permissions**: Authenticated User

**Request Body:**

```json
{
  "name": "Family Ledger 2025",
  "ledgerType": "GROUP_BALANCE",
  "baseCurrency": "USD",
  "shareStartDate": "2025-10-17"
}
```

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN>
curl -X POST 'http://localhost:8081/api/v1/ledgers' \
-H 'Content-Type: application/json' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>' \
-d '{
  "name": "Family Ledger 2025",
  "ledgerType": "GROUP_BALANCE",
  "baseCurrency": "USD",
  "shareStartDate": "2025-10-17"
}'
```

### 4.2 Get My Ledgers

Retrieves a list of all ledgers the current user is a member of.

- **Method**: `GET`
- **Endpoint**: `/api/v1/ledgers:mine`
- **Permissions**: Authenticated User

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN>
curl -X GET 'http://localhost:8081/api/v1/ledgers:mine' \
-H 'accept: */*' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>'
```

### 4.3 Get Ledger Details

Gets the details of a specific ledger.

- **Method**: `GET`
- **Endpoint**: `/api/v1/ledgers/{ledger_id}`
- **Permissions**: Ledger Member

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN> and <LEDGER_ID>
curl -X GET 'http://localhost:8081/api/v1/ledgers/1' \
-H 'accept: */*' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>'
```

---

## 5. Ledger Members

### 5.1 Add a Member to a Ledger

Adds a user to a ledger with a specific role.

- **Method**: `POST`
- **Endpoint**: `/api/v1/ledgers/{ledger_id}/members`
- **Permissions**: `OWNER` or `ADMIN` of the ledger

**Request Body:**

```json
{
  "userId": 2,
  "role": "EDITOR"
}
```

**cURL Example:**

```bash
# First, register a second user (e.g., user2@example.com) and look up their user_id
# Replace <YOUR_ACCESS_TOKEN>, <LEDGER_ID>, and the user_id in the body
curl -X POST 'http://localhost:8081/api/v1/ledgers/1/members' \
-H 'Content-Type: application/json' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>' \
-d '{
  "userId": 2,
  "role": "EDITOR"
}'
```

### 5.2 List Ledger Members

Retrieves a list of all members in a specific ledger.

- **Method**: `GET`
- **Endpoint**: `/api/v1/ledgers/{ledger_id}/members`
- **Permissions**: Ledger Member

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN> and <LEDGER_ID>
curl -X GET 'http://localhost:8081/api/v1/ledgers/1/members' \
-H 'accept: */*' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>'
```

### 5.3 Remove a Member from a Ledger

Removes a user from a ledger.

- **Method**: `DELETE`
- **Endpoint**: `/api/v1/ledgers/{ledger_id}/members/{user_id}`
- **Permissions**: `OWNER` or `ADMIN` of the ledger

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN>, <LEDGER_ID>, and <USER_ID>
curl -X DELETE 'http://localhost:8081/api/v1/ledgers/1/members/2' \
-H 'accept: */*' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>'
```

---

## 6. Categories

### 6.1 Create a Category

Creates a new category within a ledger.

- **Method**: `POST`
- **Endpoint**: `/api/v1/ledgers/{ledger_id}/categories`
- **Permissions**: `EDITOR`, `ADMIN`, or `OWNER` of the ledger

**Request Body:**

```json
{
  "name": "Groceries",
  "kind": "EXPENSE",
  "sortOrder": 1
}
```

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN> and <LEDGER_ID>
curl -X POST 'http://localhost:80_81/api/v1/ledgers/1/categories' \
-H 'Content-Type: application/json' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>' \
-d '{
  "name": "Groceries",
  "kind": "EXPENSE",
  "sortOrder": 1
}'
```

### 6.2 List Categories

Retrieves a list of categories for a specific ledger.

- **Method**: `GET`
- **Endpoint**: `/api/v1/ledgers/{ledger_id}/categories`
- **Permissions**: Ledger Member

**Request Parameters:**

- `active` (boolean, optional): Filter categories by their active status.

**cURL Example:**

```bash
# Replace <YOUR_ACCESS_TOKEN> and <LEDGER_ID>
curl -X GET 'http://localhost:8081/api/v1/ledgers/1/categories?active=true' \
-H 'accept: */*' \
-H 'X-Auth-Token: <YOUR_ACCESS_TOKEN>'
```
