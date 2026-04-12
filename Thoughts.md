# Thoughts

## Goal

I want to build this project not just as a CRUD app, but as a small financial system where correctness matters more than convenience.

The application should support:
- creating accounts
- storing balances per currency
- creating transactions
- publishing insert/update events to RabbitMQ

My main focus is to keep the internal financial state correct, especially around validation, retries, and concurrent requests.

---

## Domain model

I see the system as 3 main entities:

### Account
Stores the basic account data:
- accountId
- customerId
- country

### Balance
Stores the current available amount for a specific currency in an account:
- accountId
- currency
- availableAmount

I treat this as the current materialized state of the account.

### Transaction
Stores the history of money movements:
- transactionId
- accountId
- amount
- currency
- direction
- description
- balanceAfterTransaction

I see transactions as the history of changes, and balances as the current snapshot.

---

## Source of truth

For me, PostgreSQL is the internal source of truth.

- `transactions` store the history of successful financial changes
- `balances` store the current state for fast reads

RabbitMQ is not the source of truth.  
I use RabbitMQ only to publish events for external consumers.

---

## Why I want both balances and transactions

I think both are useful.

I want `balances` because:
- reading current account state is fast
- I do not need to recalculate the balance from full history every time

I want `transactions` because:
- they give me audit/history
- they show how the system reached the current balance
- they make reasoning about changes easier

The main risk is that balances and transactions can diverge.  
Because of that, I want transaction creation and balance update to behave like one consistent state change.

---

## Main invariants

These are the most important rules in the system:

1. A successful `OUT` transaction must never make balance negative.
2. Currency must be one of:
    - EUR
    - SEK
    - GBP
    - USD
3. Direction must be one of:
    - IN
    - OUT
4. Amount must be positive.
5. Description must not be empty.
6. A transaction must belong to an existing account.
7. A transaction must affect an existing balance in the same currency.
8. One account must not have duplicate balances for the same currency.

---

## Validation thinking

I want to think about validation in 3 layers:

### Request validation
Things like:
- missing fields
- invalid currency
- invalid direction
- negative amount
- empty description

### Data validation
Things like:
- account exists
- balance exists for requested currency

### Business validation
Things like:
- insufficient funds on `OUT`
- balance must not become negative

---

## Consistency

The most important consistency rule for me is this:

A successful transaction must update both:
- transaction history
- current balance

I do not want the system to save:
- a transaction without updating the balance
- a balance update without saving the transaction

So I treat transaction creation as one logical all-or-nothing state change inside the database boundary.

PostgreSQL stores the internal truth.  
RabbitMQ publishing is an external side effect and should not be treated as the source of truth.

---

## Atomicity

I understand atomicity as: either the full logical change happens, or nothing happens.

For `Create transaction`, I see these steps as one logical unit:
- check account
- check balance
- validate business rules
- save transaction
- update balance

If one of these steps fails, I do not want the system to remain in a partially updated financial state.

---

## Concurrency

I think concurrency is one of the hardest parts of this task.

Example:
- balance is 100 EUR
- two `OUT 80` requests arrive nearly at the same time
- both see the same old balance
- both decide the operation is valid

Without proper protection, this can break the financial state.

So for me, correct business logic alone is not enough.  
I also need to protect shared balance state under concurrent access.

---

## Horizontal scaling

I also want to keep in mind that this problem is not only about threads inside one application instance.

If the app runs in multiple instances, local in-memory protection is not enough.  
For example, `synchronized` only works inside one process and does not protect shared state across all running instances.

Because of that, I need to think about concurrency in a way that still works when the application is scaled horizontally.

---

## Idempotency

I see idempotency as a separate problem from concurrency.

For me, idempotency means:
the same business request may arrive more than once, but it should not be applied more than once.

Example:
- a client sends `OUT 20`
- the request is processed
- the response is lost
- the client retries the same request

In that case, I do not want the same money movement to happen twice.

I also think time-based cooldown is not a good solution, because:
- it can block valid transactions
- it does not identify the real business intent
- it only reacts to timing, not to the operation identity

---

## RabbitMQ

I use RabbitMQ to notify other consumers that something changed.

Examples:
- account created
- transaction created
- balance updated

I do not treat RabbitMQ as internal truth.  
I treat it as a way to publish changes to the outside.

This also means I need to think about failure cases such as:
- database update succeeds but event publishing fails
- event is delivered more than once
- consumer processes the same event more than once

---

## Delivery thinking

I understand the idea of `at-least-once delivery` like this:

A message should arrive at least once, but in some cases it may arrive more than once.

Because of that, duplicate event delivery should be treated as a normal scenario, and consumers should be safe against duplicates.

---

## Difference between state and events

Right now I think about the system like this:

- PostgreSQL stores the real internal state
- `balances` store the current materialized state
- `transactions` store the history of successful changes
- RabbitMQ events notify external systems that something changed

This distinction is important for me because I do not want to mix internal truth with external notifications.

---

## API thinking

### Create account
Should create:
- account
- one balance for each requested currency

### Get account
Should return:
- account data
- current balances

### Create transaction
This is the most important API.

It should:
- validate input
- check account
- check balance
- validate business rules
- save transaction
- update balance
- publish event

### Get transaction
Should return the transaction history for the account.

---

## Testing strategy

I do not want to treat test coverage as just a number.

I want tests to prove that the system behaves correctly.

Important cases:
- create account successfully
- create balances for requested currencies
- reject invalid currency
- get existing account
- return account not found
- create `IN` transaction and increase balance
- create `OUT` transaction and decrease balance
- reject insufficient funds
- reject invalid amount
- reject missing description
- reject invalid direction
- return transaction history
- verify RabbitMQ publishing
- think about concurrent withdrawals

---

## Scaling considerations

If I want this application to scale horizontally, I need to think about:
- concurrent access to the same balance
- consistency between balances and transactions
- duplicate event delivery
- keeping financial state correct across multiple instances

For me, scaling is not only about running more instances.  
It is mainly about preserving correctness under more concurrency.

---

## Priorities

If I need to make trade-offs, my priorities are:

1. correctness of financial state
2. consistency between balance and transaction history
3. safe handling of concurrency
4. reliable event publishing
5. performance

In this kind of system, correctness comes first.

---

## AI usage

I use AI as a thinking assistant:
- to reason about architecture
- to understand trade-offs
- to improve documentation

But I still need to understand and defend all important design decisions myself.