# PRD: Idempotent Order Creation

Adds idempotency-key support to the synchronous order-creation endpoint, closing a real
correctness gap: a retried `POST /api/v1/orders` (client timeout, proxy retry, at-least-once
delivery) can currently create a duplicate order and double-charge/double-fulfill a
customer. This is a single, narrowly-scoped story sized to go through design, implementation,
test, and review in one sitting.

## Epic: Safe retries for order creation

**Description:** Let clients safely retry `POST /api/v1/orders` without risking duplicate
orders, using a client-supplied `Idempotency-Key` header — a standard convention this
archetype should demonstrate alongside its existing pagination and optimistic-locking
patterns.

### Story: Idempotency key on order creation

As an API client integrating with the order-creation endpoint, I want to supply an
`Idempotency-Key` header on `POST /api/v1/orders`, so that retrying the same request after a
timeout or network error returns the original order instead of creating a duplicate one.

**Acceptance Criteria:**
- Given a client sends `POST /api/v1/orders` with header `Idempotency-Key: abc123` and a
  valid body, When the order is created successfully, Then the response is `201 Created` and
  the key, a hash of the request body, and the resulting order id are recorded so the same
  key can be replayed later.
- Given a client re-sends the identical request (same `Idempotency-Key: abc123`, same body)
  a second time, When the server processes it, Then it returns `201 Created` with the
  **same** order id and body as the first response, and no second order row is created.
- Given a client sends a **different** request body under a previously-used
  `Idempotency-Key`, When the server processes it, Then it returns `409 Conflict` as an RFC
  9457 `ProblemDetail` (`urn:problem:idempotency-key-reuse`) and creates no order.
- Given two concurrent requests race with the same new `Idempotency-Key`, When both attempt
  to create the order, Then a uniqueness constraint ensures only one order is created and the
  losing request receives the winner's stored response rather than an error or a duplicate.
- Given a client sends `POST /api/v1/orders` with **no** `Idempotency-Key` header, When the
  server processes it, Then behavior is unchanged from today (the header is optional —
  backward compatible with existing clients).

**Subtasks:**
- [x] Flyway migration `V3__order_idempotency.sql`: add an `idempotency_key` table (key,
      request_hash, order_id, created_at) with a unique constraint on the key.
- [x] `OrderController.create`: accept an optional `Idempotency-Key` request header and pass
      it through to the service.
- [x] `OrderService.create`: on a present key, look up an existing record — matching hash
      returns the stored order (no new insert); mismatched hash throws a new
      `IdempotencyKeyConflictException`; no record inserts the order and the idempotency row in
      the same transaction, relying on the unique constraint to arbitrate concurrent races.
- [x] `GlobalExceptionHandler`: map `IdempotencyKeyConflictException` → `409 Conflict`
      `ProblemDetail` (`urn:problem:idempotency-key-reuse`), and map the constraint-race
      case (`DataIntegrityViolationException` on the idempotency table) to re-fetch and
      return the winning response instead of a generic 409.
- [x] `OrderServiceTest` (unit): new key creates+records; replay with matching hash returns
      the same order without a second insert; replay with mismatched hash throws.
- [x] `OrderIdempotencyIT`/`OrderControllerIT` (Testcontainers): replay returns identical
      `201` body with one order row in the database; mismatched-body replay returns `409`;
      missing header behaves as before; genuine concurrent race yields exactly one order.
- [x] Document the `Idempotency-Key` header on `POST /api/v1/orders` in the OpenAPI
      annotation (`@Parameter`) and in the README's order-creation example.

**Status: Done.** Went through the full SDLC (requirements → design → implementation →
testing → code review → security review → team-lead approval). Testing found and fixed a
real concurrency bug (`IdempotencyKey` needed `Persistable<String>` to avoid a silent
merge-vs-persist race); security review found and fixed a hash-canonicalization collision,
and flagged an accepted, now-documented risk (unscoped key/squatting — see README's "Known
gap" section). Team-lead verdict: **APPROVED FOR MERGE**.
