# StockLock

## 1. Project Overview

StockLock is a generic, quantity-based resource reservation engine. It exposes a versioned REST API for creating resources, checking their currently available quantity, creating idempotent reservations, retrieving reservations, and cancelling reservations. It is a backend reservation engine, not a complete booking application.

## 2. Key Features

- Resource creation, details, and current availability APIs.
- Reservation creation that atomically decrements available quantity.
- Idempotent reservation retries using the `Idempotency-Key` header.
- Reservation retrieval and cancellation.
- Transactional rollback when reservation persistence fails.
- Consistent success and error response envelopes.
- Flyway-managed schema migrations and Hibernate schema validation.
- PostgreSQL concurrency coverage using Testcontainers.

## 3. Technology Stack

- Java 21
- Spring Boot 3.5.x, Spring Web MVC, Spring Data JPA, Hibernate, and Jakarta Bean Validation
- PostgreSQL for runtime persistence
- Flyway for schema migrations
- H2 for regular service and API tests
- JUnit 5, Mockito, AssertJ, MockMvc, and Testcontainers
- Gradle (Groovy DSL)

## 4. Architecture Overview

The application is organized as a modular monolith. Resource and reservation functionality have separate `api`, `service`, `persistence`, `domain`, and `dto` packages. Controllers handle HTTP requests and response envelopes, services hold business and transaction logic, repositories use Spring Data JPA, and DTOs define the API contract. Shared API and exception response types live under `common` and `exception`.

## 5. Prerequisites

- Java 21 (the Gradle toolchain targets Java 21).
- PostgreSQL available locally for running the application.
- Docker for the PostgreSQL 16 Testcontainers tests.
- Git and a terminal with Gradle wrapper support.

H2 is started in memory for the regular test profile; it does not replace the PostgreSQL runtime database.

## 6. Database Configuration

The runtime application reads its PostgreSQL connection settings from environment variables:

| Variable | Purpose | Example |
|---|---|---|
| `DB_URL` | JDBC connection URL | `jdbc:postgresql://localhost:5432/stocklock` |
| `DB_USERNAME` | Database username | `stocklock_user` |
| `DB_PASSWORD` | Database password | `change-me` |

The values below are placeholders for a local development database. Replace them with credentials configured in your own PostgreSQL instance. Do not commit credentials or secrets.

Unix shell:

```bash
export DB_URL='jdbc:postgresql://localhost:5432/stocklock'
export DB_USERNAME='stocklock_user'
export DB_PASSWORD='change-me'
```

Windows PowerShell:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/stocklock'
$env:DB_USERNAME = 'stocklock_user'
$env:DB_PASSWORD = 'change-me'
```

Flyway automatically runs migrations from `classpath:db/migration` at startup. Hibernate uses `ddl-auto: validate`, so it validates mappings against the migrated schema instead of creating or changing tables.

## 7. Getting Started

1. Install Java 21 and PostgreSQL, then create a local database and user.
2. Set `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` as shown above.
3. Start the application from the repository root:

```bash
./gradlew bootRun
```

On Windows, use `./gradlew.bat bootRun` in PowerShell. The service uses port `8080` by default. Flyway applies any pending migrations during startup.

## 8. API Documentation

The OpenAPI document and Swagger UI are served by the running application:

- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

All application endpoints are versioned under `/api/v1`.

## 9. API Reference

All API responses use the common envelope described below. Example IDs are illustrative.

### Create a resource

- **Method and URL:** `POST /api/v1/resources`
- **Purpose:** Create a resource with its initial available quantity.
- **Headers:** `Content-Type: application/json`
- **Request:**

```json
{
  "name": "Conference Room",
  "description": "Meeting room on floor 2",
  "quantity": 10
}
```

- **Success:** `201 Created`
- **Example:** `{"success":true,"data":{"id":1,"name":"Conference Room","description":"Meeting room on floor 2","quantity":10},"error":null}`
- **Errors:** `400 Bad Request` for invalid fields.

`name` is required, nonblank, and at most 120 characters. `description` is optional and at most 500 characters. `quantity` is required and nonnegative; zero is valid.

### Get resource details

- **Method and URL:** `GET /api/v1/resources/{id}`
- **Purpose:** Get the resource name, description, and current available quantity.
- **Headers:** None required.
- **Success:** `200 OK`
- **Example:** `{"success":true,"data":{"id":1,"name":"Conference Room","description":"Meeting room on floor 2","quantity":10},"error":null}`
- **Errors:** `400 Bad Request` for an invalid ID; `404 Not Found` when the resource does not exist.

### Get resource availability

- **Method and URL:** `GET /api/v1/resources/{id}/availability`
- **Purpose:** Get the currently available quantity independently of the resource details.
- **Headers:** None required.
- **Success:** `200 OK`
- **Example:** `{"success":true,"data":{"resourceId":1,"availableQuantity":10},"error":null}`
- **Errors:** `400 Bad Request` for an invalid ID; `404 Not Found` when the resource does not exist.

### Create a reservation

- **Method and URL:** `POST /api/v1/reservations`
- **Purpose:** Reserve a positive quantity when enough availability remains.
- **Headers:** `Content-Type: application/json`; required `Idempotency-Key` (nonblank, maximum 255 characters).
- **Request:**

```json
{
  "resourceId": 1,
  "quantity": 3
}
```

- **Success:** `201 Created`
- **Example:** `{"success":true,"data":{"id":101,"resourceId":1,"quantity":3},"error":null}`
- **Errors:** `400 Bad Request` for invalid fields/header; `404 Not Found` if the resource is missing; `409 Conflict` for insufficient availability or reuse of a key with different request parameters.

### Get reservation details

- **Method and URL:** `GET /api/v1/reservations/{id}`
- **Purpose:** Get a reservation and its current status.
- **Headers:** None required.
- **Success:** `200 OK`
- **Example:** `{"success":true,"data":{"id":101,"resourceId":1,"quantity":3,"status":"ACTIVE"},"error":null}`
- **Errors:** `400 Bad Request` for an invalid ID; `404 Not Found` when the reservation does not exist.

### Cancel a reservation

- **Method and URL:** `DELETE /api/v1/reservations/{id}`
- **Purpose:** Cancel an active reservation and restore its quantity to availability.
- **Headers:** None required.
- **Success:** `200 OK`
- **Example:** `{"success":true,"data":{"id":101,"resourceId":1,"quantity":3,"status":"CANCELLED"},"error":null}`
- **Errors:** `400 Bad Request` for an invalid ID; `404 Not Found` when the reservation does not exist.

## 10. Response Structure

Successful responses use this envelope:

```json
{
  "success": true,
  "data": {},
  "error": null
}
```

Errors use a nested error object:

```json
{
  "success": false,
  "data": null,
  "error": {
    "timestamp": "2026-01-01T12:00:00Z",
    "status": 400,
    "error": "Bad Request",
    "message": "Request validation failed",
    "path": "/api/v1/resources",
    "fieldErrors": {
      "name": "must not be blank"
    }
  }
}
```

`fieldErrors` is an empty map when an error is not related to field validation. Error responses contain safe application-facing messages rather than internal database or stack details.

## 11. Reservation Workflow and Concurrency

`Resource.quantity` is the current available quantity; there is no separate total-quantity field. Reservation creation uses a conditional database update that subtracts the requested quantity only when enough is available. The update and reservation insert run in one transaction. Requests that cannot reserve enough quantity receive `409 Conflict`, and concurrent attempts cannot make availability negative.

If reservation persistence fails after the decrement, the transaction rolls back the quantity change. PostgreSQL Testcontainers tests exercise the concurrency and persistence behavior against PostgreSQL 16; regular service and API tests use H2.

## 12. Idempotency

Reservation creation requires an `Idempotency-Key`. Repeating the same resource and quantity with the same key returns the original reservation without decrementing availability or inserting another row. Reusing a key with a different resource or quantity returns `409 Conflict`. The key is stored under a unique database constraint.

## 13. Cancellation Behavior

Cancellation changes an `ACTIVE` reservation to `CANCELLED` and restores the quantity recorded on that reservation. The conditional status update ensures only one concurrent cancellation transitions the reservation and restores availability. Repeating cancellation returns the existing cancelled reservation without restoring quantity a second time. Status change and quantity restoration share a transaction, so a failed restoration rolls the status change back.

Reservation statuses are `ACTIVE` and `CANCELLED`.

## 14. Running Tests

Run the complete build and test suite:

```bash
./gradlew clean build --no-daemon
```

On Windows PowerShell, use `./gradlew.bat clean build --no-daemon`.

Regular service, persistence, and API tests use the in-memory H2 test profile with Flyway migrations. PostgreSQL concurrency and rollback tests use PostgreSQL 16 through Testcontainers and require Docker to be installed and running. The PostgreSQL tests are part of the normal Gradle test task and do not silently substitute H2 when Docker is unavailable.

## 15. Swagger UI

With the application running and its PostgreSQL environment variables configured, open <http://localhost:8080/swagger-ui.html>. The UI lets you inspect and try the six versioned resource and reservation endpoints. The raw OpenAPI JSON is available at <http://localhost:8080/v3/api-docs>.
