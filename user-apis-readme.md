# User APIs (Password Only)

**Base URL:** `http://35.188.69.160:8081`  
**Prefix:** All endpoints are under `/api`  
**Auth:** Protected endpoints require an access token in header `X-Auth-Token: <accessToken>`  
**Response Envelope (`Result<T>`):**
```json
{
  "success": true,
  "message": "string",
  "data": {},
  "total": 0
}
```

> Tip: In the examples below, headers use `accept: */*` and cURL uses the `-X GET|POST|PUT` style you requested.

> Warning:

You must enable access to your own machine in the Google Cloud Console firewall settings to reach the API.
First, find your public IP address by running:
```
curl -4 ifconfig.me
```
Then go to:
https://console.cloud.google.com/net-security/firewall-manager/firewall-policies/details/allow8081?project=ase-flyingcloud
and update the rule to include your computer’s IP address.
> warning: Replace placeholder tokens like `<accessToken>` with actual values from your workflow.


> Tip: Open Swagger UI
1.	Visit: http://35.188.69.160:8081/swagger-ui/index.html
2.	You’ll see a list of endpoints grouped by tag. Expand User APIs (password only).
3.	Click /login to see request schema and responses.
---

## Table of Contents
- [Authentication](#authentication)
  - [Register](#post-apiauthregister)
  - [Login](#post-apiauthlogin)
  - [Refresh Token](#post-apiauthrefresh)
  - [Logout](#post-apiauthlogout)
- [Users](#users)
  - [Get Current User](#get-apiusersme)
  - [Get User by ID](#get-apiusersid)
  - [Update Current User](#put-apiusersme)
  - [Change Password](#post-apiusersmechange-password)
- [Data Models](#data-models)
- [Common Errors](#common-errors)

---

## Authentication

### `POST /api/auth/register`
Register with email + password.

**Request Body**
```json
{ "email": "user@gmail.com", "name": "testU", "password": "S3cure!1" }
```

**cURL**
```bash
curl -X POST   'http://35.188.69.160:8081/api/auth/register'   -H 'accept: */*'   -H 'Content-Type: application/json'   -d '{
    "email": "user@gmail.com",
    "name": "testU",
    "password": "S3cure!1"
  }'
```

---

### `POST /api/auth/login`
Login with email + password; returns `accessToken` & `refreshToken`.

**Request Body**
```json
{ "email": "user@gmail.com", "password": "S3cure!1" }
```

**cURL**
```bash
curl -X POST   'http://35.188.69.160:8081/api/auth/login'   -H 'accept: */*'   -H 'Content-Type: application/json'   -d '{
    "email": "user@gmail.com",
    "password": "S3cure!1"
  }'
```

**Success Response (`data`)**
```json
{ "accessToken": "string", "refreshToken": "string" }
```

---

### `POST /api/auth/refresh`
Refresh an access token (rotate refresh token). Supply `refreshToken` as a **query param**.

**cURL**
```bash
curl -X POST   'http://35.188.69.160:8081/api/auth/refresh?refreshToken=YOUR_REFRESH_TOKEN'   -H 'accept: */*'
```

**Success Response (`data`)**
```json
{ "accessToken": "string", "refreshToken": "string" }
```

---

### `POST /api/auth/logout`
Logout by invalidating refresh token. Supply `refreshToken` as a **query param**.

**cURL**
```bash
curl -X POST   'http://35.188.69.160:8081/api/auth/logout?refreshToken=YOUR_REFRESH_TOKEN'   -H 'accept: */*'
```

---

## Users

> All endpoints in this section require header `X-Auth-Token: <accessToken>`.

### `GET /api/users/me`
Get current user (`id`, `name`).

**cURL (using the exact style & token you provided)**  
```bash
curl -X GET   'http://35.188.69.160:8081/api/users/me'   -H 'accept: */*'   -H 'X-Auth-Token: UhGM4B2g0nxpszrG6FKZxJl0P6lFE10sin32QvLZPsk'
```

**Example Success (`data`)**
```json
{ "id": 123, "name": "Alice" }
```

**Example Not Logged In**
```json
{ "success": false, "message": "Not logged in" }
```

---

### `GET /api/users/{id}`
Get user profile by id (no timestamps).

**Path Param**
- `id` (int64) — user id

**cURL**
```bash
curl -X GET   'http://35.188.69.160:8081/api/users/2'  -H 'accept: */*'   -H 'X-Auth-Token: sXlpyuA5qZzuXn-i71Nk5gwZl58boVps6zTQ-xh-8Yk'
```

**Example Success (`data`)**
```json
{ "id": 123, "name": "Alice" }
```

---

### `PUT /api/users/me`
Update current user's profile (`name`, `timezone`).

**Request Body**
```json
{ "name": "Alice Zhang", "timezone": "America/New_York" }
```

**cURL**
```bash
curl -X PUT   'http://35.188.69.160:8081/api/users/me'   -H 'accept: */*'   -H 'Content-Type: application/json'   -H 'X-Auth-Token: sXlpyuA5qZzuXn-i71Nk5gwZl58boVps6zTQ-xh-8Yk'   -d '{
    "name": "testUchange",
    "timezone": "America/New_York"
  }'
```

---

### `POST /api/users/me/change-password`
Change password (requires `oldPassword` + `newPassword`).

**Request Body**
```json
{ "oldPassword": "oldPass123!", "newPassword": "NewPass456!" }
```

**cURL**
```bash
curl -X POST   'http://35.188.69.160:8081/api/users/me/change-password'   -H 'accept: */*'   -H 'Content-Type: application/json'   -H 'X-Auth-Token: sXlpyuA5qZzuXn-i71Nk5gwZl58boVps6zTQ-xh-8Yk'   -d '{
    "oldPassword": "oldPass123!",
    "newPassword": "NewPass456!"
  }'
```

---

## Data Models

- **RegisterRequest**
  ```json
  { "email": "user@gmail.com", "name": "testU", "password": "S3cure!1" }
  ```
- **LoginRequest**
  ```json
  { "email": "user@gmail.com", "password": "S3cure!1" }
  ```
- **TokenPair**
  ```json
  { "accessToken": "string", "refreshToken": "string" }
  ```
- **UpdateProfileRequest**
  ```json
  { "name": "Alice", "timezone": "America/New_York" }
  ```
- **ChangePasswordRequest**
  ```json
  { "oldPassword": "string", "newPassword": "string" }
  ```
- **UserView**
  ```json
  { "id": 0, "name": "string" }
  ```

---

## Common Errors

- **401/403 Unauthorized** — Missing or invalid `X-Auth-Token`.
  ```bash
  curl -X GET     'http://35.188.69.160:8081/api/users/me'     -H 'accept: */*'
  ```
- **400 Bad Request** — Validation failed (e.g., empty `name`, invalid `timezone`, wrong `oldPassword`, weak `newPassword`).
- **Token Rotation** — After `/auth/refresh`, use the **new** tokens returned in response.

---

## Quick Smoke Test (optional)

```bash
EMAIL="user$RANDOM@gmail.com"
PASS="S3cure!1"

# Register
curl -X POST   'http://35.188.69.160:8081/api/auth/register'   -H 'accept: */*' -H 'Content-Type: application/json'   -d "{"email":"$EMAIL","name":"testU","password":"$PASS"}"

# Login
LOGIN=$(curl -s -X POST   'http://35.188.69.160:8081/api/auth/login'   -H 'accept: */*' -H 'Content-Type: application/json'   -d "{"email":"$EMAIL","password":"$PASS"}")

ACCESS=$(echo "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*//p')
REFRESH=$(echo "$LOGIN" | sed -n 's/.*"refreshToken":"\([^"]*\)".*//p')

# /users/me
curl -X GET   "http://35.188.69.160:8081/api/users/me"   -H 'accept: */*'   -H "X-Auth-Token: $ACCESS"
```

---

## Controller Reference

Source: `dev.coms4156.project.groupproject.controller.UserController`

- `POST /api/auth/register` — Register with email + password  
- `POST /api/auth/login` — Login with email + password; returns accessToken & refreshToken  
- `POST /api/auth/refresh` — Refresh an access token (rotate refresh token)  
- `POST /api/auth/logout` — Logout by invalidating refresh token  
- `GET /api/users/me` — Get current user (id, name) **(requires X-Auth-Token)**  
- `GET /api/users/{id}` — Get user profile by id (no timestamps) **(requires X-Auth-Token)**  
- `PUT /api/users/me` — Update current user's profile (name/timezone) **(requires X-Auth-Token)**  
- `POST /api/users/me/change-password` — Change password (requires oldPassword + newPassword) **(requires X-Auth-Token)**

