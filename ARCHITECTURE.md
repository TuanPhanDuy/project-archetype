# Architecture & structure conventions

How this service is organized and the rules every change follows.

## Package-by-layer

Code is grouped by **technical layer** — all controllers together, all services together,
and so on. Each class name is prefixed by its domain (`Product*`, `Order*`) so related
classes are easy to find across layers.

```
com.onemount.archetype
├── Application.java                 # entry point
├── package-info.java                # @NullMarked (JSpecify) for the whole tree
│
├── controller/                      # HTTP boundary
│   ├── ProductController.java
│   ├── CategoryController.java
│   ├── OrderController.java
│   └── JobController.java
├── service/                         # business logic + transactions
│   ├── ProductService.java
│   ├── CategoryService.java
│   ├── OrderService.java
│   ├── FulfillmentService.java      # @Async fulfillment
│   └── JobService.java
├── repository/                      # persistence boundary (Spring Data)
│   ├── ProductRepository.java
│   ├── CategoryRepository.java
│   ├── OrderRepository.java
│   └── JobRepository.java
├── domain/                          # JPA entities + enums (the model)
│   ├── Product.java  Category.java  Order.java  OrderItem.java  ProcessingJob.java
│   └── OrderStatus.java  JobStatus.java  JobType.java
├── dto/                             # request/response records (the wire contract)
│   ├── ProductRequest.java  ProductResponse.java
│   ├── CategoryRequest.java CategoryResponse.java
│   ├── CreateOrderRequest.java OrderResponse.java OrderSummaryResponse.java
│   └── JobResponse.java
│
├── common/error/                    # GlobalExceptionHandler, domain exceptions
└── config/                          # @Configuration + beans (Clock, AsyncConfig, OpenApiConfig)
```

## The layers and their responsibilities

Request flow — each layer talks only to the one below it:

```
HTTP ─▶ controller ─▶ service ─▶ repository ─▶ domain ─▶ Database
              │            │
            dto      @Transactional
```

| Layer          | Package      | Does                                                                 | Must NOT |
|----------------|--------------|----------------------------------------------------------------------|----------|
| **Controller** | `controller` | Map HTTP ↔ DTOs, validate input (`@Valid`), delegate, set status/`Location`. Thin. | Hold business logic; touch a repository; return a domain entity. |
| **Service**    | `service`    | Business rules, orchestration, **transaction boundaries** (`@Transactional`). | Know about HTTP (no servlet/`ResponseEntity`). |
| **Repository** | `repository` | Persistence only — Spring Data interfaces, queries, `@EntityGraph` fetch plans. | Contain business logic; be called from a controller. |
| **Domain**     | `domain`     | Entities + enums; invariants; JPA mapping. Aggregate roots own their children. | Be serialized to the wire; carry request-shaped fields. |
| **DTO**        | `dto`        | The API contract (records); validation constraints on requests. | Leak persistence concerns; be reused as entities. |
| **Error**      | `common/error` | `@RestControllerAdvice` → RFC 9457 `ProblemDetail`; domain exceptions. | Be caught-and-formatted inside controllers. |
| **Config**     | `config`     | `@Configuration`, bean wiring (`Clock`, async executor, OpenAPI). | Contain behaviour that belongs in a service. |

## Rules of the road

1. **Controllers are thin.** Validate, delegate, map. No `if`-heavy logic.
2. **Services own transactions.** Class-level `@Transactional(readOnly = true)`; annotate
   writes with `@Transactional`. Never in controllers or repositories.
3. **Repositories never escape the service.** Controllers depend on services only.
4. **Domain entities never cross the wire.** Map to/from `dto` records at the boundary, so
   the API stays stable while the schema evolves.
5. **The schema is owned by Flyway.** Every change is a new `V<n>__*.sql`; Hibernate runs
   `ddl-auto: validate`. Never edit an applied migration.
6. **Time is injected.** Depend on `Clock`, never call `Instant.now()` directly.
7. **Null-safety on.** Packages are `@NullMarked`; mark exceptions with `@Nullable`.
8. **Lazy by default, fetch on purpose.** Associations are `LAZY`; reads that need them use
   `@EntityGraph` finders (open-in-view is disabled).
9. **Native-image safe.** Avoid runtime reflection / classpath scanning without registered
   `RuntimeHints`.

## DTO mapping

Mapping lives in the DTO record as a static factory (`ProductResponse.from(entity)`) — no
extra framework, native-friendly, easy to read. For large/complex mappings, introduce a
`mapper` package (plain classes or MapStruct) rather than spreading mapping logic around.
Map **inside the transaction** when an aggregate has lazy collections, or fetch them with an
`@EntityGraph` finder first (see `OrderResponse` + `OrderRepository.findWithItemsById`).

## Error handling

One place: `common/error/GlobalExceptionHandler` (a `@RestControllerAdvice` extending
`ResponseEntityExceptionHandler`). It returns RFC 9457 `ProblemDetail` for everything —
Spring's built-in exceptions plus our own:

| Exception                              | Status | `type` URN                |
|----------------------------------------|--------|---------------------------|
| `ResourceNotFoundException`            | 404    | `urn:problem:resource-not-found` |
| `MethodArgumentNotValidException` (body)| 400   | `urn:problem:validation`  |
| `ConstraintViolationException` (params)| 400    | `urn:problem:validation`  |
| `DataIntegrityViolationException`      | 409    | `urn:problem:data-integrity` |
| `OptimisticLockingFailureException`    | 409    | `urn:problem:optimistic-lock` |
| anything else                          | 500    | `urn:problem:internal`    |

Throw a domain exception from the service; add a new `@ExceptionHandler` here for a new
failure mode. Never build error JSON in a controller.

## Naming conventions

- Packages: layer name (`controller`, `service`, `repository`, `domain`, `dto`).
- Classes: `Product`, `ProductController`, `ProductService`, `ProductRepository`,
  `ProductRequest`, `ProductResponse`. Enums in `domain` (`OrderStatus`, `JobType`).
- Endpoints: plural, versioned — `/api/v1/products`. Tables: singular (`product`),
  reserved words renamed (`orders`).
- Tests mirror the class under test's package: `*Test` = unit (no Spring/DB),
  `*IT` = integration (`@SpringBootTest` + Testcontainers).

## Adding a feature (checklist)

1. Add the classes to their layer packages: `domain/<Entity>`, `repository/<Entity>Repository`,
   `service/<Entity>Service`, `controller/<Entity>Controller`, `dto/<Entity>Request|Response`.
2. New Flyway migration for the schema.
3. `*ServiceTest` (unit) + `*ControllerIT` (Testcontainers) — one assertion per acceptance
   criterion.
4. `./mvnw verify` green.

See [`README.md`](README.md) for the runnable examples (sync/async APIs, relation model)
and [`GETTING_STARTED.md`](GETTING_STARTED.md) to run it locally.
