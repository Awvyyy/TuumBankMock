# TuumBankMock

`TuumBankMock` is a small core banking application built as a test assignment.

The application supports:
- account creation
- balance tracking by currency
- transaction processing
- transaction history retrieval
- RabbitMQ event publishing through a transactional outbox

The solution focuses on correctness, consistency, and safe event publication.

A more detailed explanation of my reasoning, trade-offs, and design thinking is available in [`Thoughts.md`](./Thoughts.md).

---

## Repository

Source code is available here:

```text
https://github.com/Awvyyy/TuumBankMock
```

---

## Tech stack

- Java 17
- Spring Boot
- MyBatis
- Gradle
- PostgreSQL
- RabbitMQ
- JUnit
- Testcontainers
- k6

---

## Assignment requirements covered

The assignment asks for:
- account, balances, and transaction history
- RabbitMQ publishing
- integration tests
- Docker-based execution
- instructions to build and run
- explanation of important choices
- throughput estimate
- horizontal scaling considerations
- explanation of AI usage

This project includes all of the above, plus:
- concurrency-safe balance updates
- transaction idempotency
- transactional outbox
- layered automated tests

---

## Features

### Account
- Create account with one or more balances in supported currencies
- Get account with current balances

Supported currencies:
- EUR
- SEK
- GBP
- USD

### Transactions
- Create `IN` transaction
- Create `OUT` transaction
- Prevent overdraft
- Get transaction history by account

### Reliability and consistency
- Row-level locking for safe concurrent balance updates
- Idempotency for transaction creation
- Transactional outbox for RabbitMQ publishing
- Integration tests for DB-backed scenarios
- Concurrency tests for withdrawal race conditions

### RabbitMQ events
The application writes events to the outbox and publishes them to RabbitMQ for downstream consumers.

Published event types:
- `ACCOUNT_CREATED`
- `BALANCE_CREATED`
- `TRANSACTION_CREATED`
- `BALANCE_UPDATED`

This is intended to cover insert/update operations in the account service in line with the assignment wording.

---

## Project structure

```text
src/main/java/org/example/tuumbankmock
├── config
├── controller
├── dto
│   ├── event
│   ├── request
│   └── response
├── exception
├── mapper
├── model
└── service

src/main/resources
├── application.properties
└── schema.sql

src/test/java/org/example/tuumbankmock
├── controller tests
├── service tests
├── integration tests
├── concurrency tests
├── idempotency tests
└── outbox tests
```

---

## Architecture overview

### PostgreSQL as source of truth
All critical business state is stored in PostgreSQL:
- accounts
- balances
- transactions
- outbox events

### Atomic transaction processing
Creating a transaction is performed inside a database transaction:
1. validate input
2. check account existence
3. lock the balance row
4. apply business rules
5. insert transaction
6. update balance
7. insert outbox event(s)

This prevents partial updates.

### Concurrency control
Balance rows are accessed with locking to prevent race conditions on concurrent withdrawals.

Example:
- balance = `100 EUR`
- two concurrent `OUT 80`
- only one succeeds
- the second fails with insufficient funds

### Idempotency
Transaction creation uses an `idempotencyKey`.

Behavior:
- same key + same payload → returns existing transaction
- same key + different payload → returns conflict
- prevents duplicate money movement on retries

### Transactional outbox
RabbitMQ publishing is not done directly from the business transaction.

Instead:
- business state is committed to PostgreSQL
- outbox event is stored in the same transaction
- a scheduled publisher reads pending outbox rows and publishes them
- after successful publish, event status is updated

This prevents the failure mode where DB changes succeed but RabbitMQ publish fails.

---

## REST API

### 1. Create account

**POST** `/accounts`

#### Request
```json
{
  "customerId": 123,
  "country": "EE",
  "currencies": ["EUR", "USD"]
}
```

#### Response
```json
{
  "accountId": 1,
  "customerId": 123,
  "balances": [
    {
      "availableAmount": 0.00,
      "currency": "EUR"
    },
    {
      "availableAmount": 0.00,
      "currency": "USD"
    }
  ]
}
```

---

### 2. Get account

**GET** `/accounts/{accountId}`

#### Response
```json
{
  "accountId": 1,
  "customerId": 123,
  "balances": [
    {
      "availableAmount": 100.00,
      "currency": "EUR"
    },
    {
      "availableAmount": 0.00,
      "currency": "USD"
    }
  ]
}
```

---

### 3. Create transaction

**POST** `/transactions`

#### Request
```json
{
  "accountId": 1,
  "idempotencyKey": "txn-001",
  "amount": 50.00,
  "currency": "EUR",
  "direction": "OUT",
  "description": "ATM withdrawal"
}
```

#### Response
```json
{
  "transactionId": 10,
  "accountId": 1,
  "idempotencyKey": "txn-001",
  "amount": 50.00,
  "currency": "EUR",
  "direction": "OUT",
  "description": "ATM withdrawal",
  "balanceAfterTransaction": 50.00
}
```

---

### 4. Get transactions

**GET** `/transactions/account/{accountId}`

#### Response
```json
{
  "accountId": 1,
  "transactions": [
    {
      "transactionId": 1,
      "amount": 100.00,
      "currency": "EUR",
      "direction": "IN",
      "description": "Initial deposit",
      "balanceAfterTransaction": 100.00
    },
    {
      "transactionId": 2,
      "amount": 25.00,
      "currency": "EUR",
      "direction": "OUT",
      "description": "Coffee",
      "balanceAfterTransaction": 75.00
    }
  ]
}
```

---

## Error handling

Examples of handled error cases:
- invalid currency
- invalid direction
- invalid amount
- description missing
- account not found
- balance not found
- insufficient funds
- idempotency conflict

Errors are returned as structured JSON responses with:
- HTTP status
- error name
- message
- request path

---

## Running the application

### Prerequisites
- Java 17+
- Docker Desktop
- Gradle wrapper included in the repository

### 1. Start infrastructure
```bash
docker compose up -d
```

This starts:
- PostgreSQL
- RabbitMQ

### 2. Run the application locally
```bash
./gradlew bootRun
```

Application:
- API: `http://localhost:8080`
- RabbitMQ AMQP: `localhost:5672`

If RabbitMQ management UI is enabled in compose:
- RabbitMQ UI: `http://localhost:15672`

### 3. Run the application fully in Docker
```bash
docker compose up --build
```

### 4. Stop infrastructure
```bash
docker compose down
```

To remove volumes and reset the local database:
```bash
docker compose down -v
```

---

## Running tests

```bash
./gradlew clean test
```

This runs:
- unit tests
- controller tests
- integration tests
- concurrency tests
- idempotency tests
- outbox tests

---

## Coverage

The assignment requires integration tests and at least 80% test coverage.

This project includes automated tests across:
- service layer
- controller layer
- integration layer
- concurrency scenarios
- idempotency scenarios
- outbox publishing scenarios

JaCoCo result:
- overall instruction coverage: **88%**
- branch coverage: **65%**

This satisfies the assignment requirement of at least **80% test coverage**.

---

## Docker handover notes

The assignment requires the application to be executable using Docker and runnable on the reviewer’s machine without local configuration changes.

This project is structured so that:
- PostgreSQL is started through Docker
- RabbitMQ is started through Docker
- schema is initialized automatically
- the application uses standard configuration from `application.properties`

---

## Testing strategy

### Unit tests
Focus on service-level business logic:
- balance increase/decrease
- insufficient funds
- account/balance existence checks

### Controller tests
Verify:
- request/response mapping
- HTTP status codes
- error responses

### Integration tests
Verify:
- database writes
- API behavior with real PostgreSQL
- account and transaction lifecycle

### Concurrency tests
Verify:
- multiple simultaneous withdrawals do not corrupt balance state

### Idempotency tests
Verify:
- repeated same request does not create duplicate transaction
- repeated same key with different payload returns conflict

### Outbox tests
Verify:
- outbox row creation
- successful publish marks event as `PUBLISHED`
- failed publish marks event as `FAILED`
- retry metadata is updated

---

## Important design choices

### Why PostgreSQL is the source of truth
Money-related state must be durable and consistent. RabbitMQ is treated as an external side effect, not as the primary state store.

### Why row locking is used
Concurrent `OUT` transactions against the same balance can otherwise lead to double spending.

### Why idempotency was added
Network retries and repeated client requests should not move money twice.

### Why transactional outbox was chosen
Publishing directly to RabbitMQ after a DB commit can lose events if the broker call fails. Outbox keeps DB state and outgoing message intent in the same transaction.

### Why MyBatis was used
The assignment requires MyBatis, and it gives direct control over SQL, which is useful for balance locking and explicit persistence behavior.

### More detail
A more detailed reasoning log is available in [`Thoughts.md`](./Thoughts.md).

---

## Throughput estimate

The assignment asks for an estimate of how many transactions the application can handle per second on the development machine.

Throughput was measured locally with the application running on Windows and PostgreSQL + RabbitMQ running in Docker.

### Test scenario
- load tool: `k6`
- endpoint: `POST /transactions`
- direction: `IN`
- amount: `1`
- currency: `EUR`
- virtual users: `20`
- duration: `30s`
- unique `idempotencyKey` per request

### Main machine
- CPU: AMD Ryzen 7 5700X (PBO OC)
- RAM: 16 GB DDR4 3600 CL16
- OS: Windows 11 25H2

Result:
- successful transactions: `162416`
- failed transactions: `0`
- estimated throughput: **~5414 transactions/second**

### Secondary machine
- CPU: Intel Core i5-12400
- RAM: 16 GB DDR4 3000 CL18
- OS: Windows 11 24H2

Result:
- successful transactions: `113803`
- failed transactions: `0`
- estimated throughput: **~3793 transactions/second**

### Notes
These measurements were taken for `IN` transactions under low contention. Throughput for `OUT` transactions can be lower because they require balance locking and funds validation. The numbers above should be treated as local single-node development-machine estimates rather than production benchmarks.

---

## Horizontal scaling considerations

The assignment asks for a description of what should be considered for horizontal scaling.

To scale the application horizontally, the following points must be considered:

### 1. Stateless application instances
Application nodes should not keep business state in memory.

### 2. Database contention
Balances are updated with locking, so high contention on the same account/currency pair can become a bottleneck.

### 3. Connection pool sizing
Multiple application instances increase pressure on PostgreSQL and RabbitMQ connections.

### 4. Idempotency
Retries from clients, load balancers, or network failures must remain safe in a distributed setup.

### 5. Outbox processing
Multiple instances may run the outbox publisher simultaneously. Event picking must therefore avoid double processing. This is handled using SQL row locking / `FOR UPDATE SKIP LOCKED`.

### 6. Consumer idempotency
RabbitMQ delivery is effectively at-least-once in this setup. Consumers must handle duplicates safely.

### 7. Read/write scaling
If traffic grows significantly, reads and writes may need different scaling strategies, and database partitioning or sharding may become necessary depending on workload.

---

## Explanation of AI usage

The assignment asks for an explanation of AI usage.

AI was used as a development assistant for:
- discussing architecture options
- reviewing trade-offs
- generating and refining test scenarios
- improving documentation structure
- helping identify edge cases around concurrency, idempotency, and outbox publishing

Additionally, the `k6` load-testing script used for TPS measurement was generated by ChatGPT and then used and validated manually as part of the throughput estimation process.

All generated suggestions were manually reviewed, adapted, and validated through implementation and automated tests.

---

## Additional notes

- Detailed design thinking and trade-offs are documented in [`Thoughts.md`](./Thoughts.md).
- The project intentionally goes beyond the minimal solution by including concurrency protection, idempotency, and transactional outbox support.

---

## Summary

This project implements a small banking backend with:
- account creation
- balance tracking
- transaction processing
- concurrency-safe balance updates
- idempotent transaction creation
- transactional outbox for RabbitMQ
- layered automated tests

The focus of the solution is correctness, consistency, and safe event publication.
