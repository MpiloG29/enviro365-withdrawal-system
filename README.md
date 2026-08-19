# Enviro365 Withdrawal System

Investor portfolio and withdrawal management system built for the eTalente Junior Developer Assessment
(Enviro365 Investments, 2026). Spring Boot 3 backend with an H2 in-memory database, plus a vanilla HTML/CSS/JS
frontend served by the same application — one artifact, no separate dev server, no build step for the UI.

Package root: `com.enviro.assessment.junior.gumede`

## Tech stack

- Java 17, Spring Boot 3.5.13 (Web, Data JPA, Validation)
- H2 (in-memory), seeded via `data.sql` on every startup
- Plain HTML / CSS / vanilla JavaScript frontend, no framework, no CDN dependencies
- JUnit 5 + Mockito + AssertJ for unit tests

## Setup & run

Requires a JDK 17+ and Maven 3.9+ on the `PATH`.

```bash
mvn spring-boot:run
```

The application starts on **http://localhost:8080**:

- **http://localhost:8080/** — the frontend (dashboard, withdrawal form, history, CSV export)
- **http://localhost:8080/api/...** — the REST API
- **http://localhost:8080/h2-console** — H2 console. JDBC URL `jdbc:h2:mem:withdrawaldb`, user `sa`, blank
  password. (Copy only the URL itself into the console's JDBC URL field, not the whole
  `spring.datasource.url=...` line from `application.properties`.)

To run the tests:

```bash
mvn test
```

## Seeded data

`data.sql` loads three investors on every startup (the in-memory database is recreated each run), covering the
three cases the retirement-age rule needs to be tested against:

| Id | Name | Age (as of 2026) | Products |
|---|---|---|---|
| 1 | Thandiwe Nkosi | 71 | Retirement Annuity (RETIREMENT) — R850,000.00 |
| 2 | Sipho Mahlangu | 46 | Retirement Annuity (RETIREMENT) — R320,000.00 — too young to withdraw from it |
| 3 | Lerato Dube | 35 | Flexible Savings Account, Tax-Free Savings Account (SAVINGS) |

## Business rules

Enforced in `WithdrawalService`, in this order, each with its own error message:

1. Withdrawal amount must be greater than zero.
2. Retirement (`RETIREMENT`) withdrawals are only permitted if the investor's age is **strictly greater than
   65**.
3. The amount must not exceed the product's current balance.
4. The amount must not exceed **90%** of the balance (computed with `RoundingMode.DOWN`, so the cap never
   rounds in the investor's favour).

Balance and the 90% cap are checked in that order deliberately: exceeding the full balance always also means
exceeding 90% of it, so checking the balance first means whichever error fires is the one that actually
explains the problem.

## API reference

All error responses share one shape (`timestamp`, `status`, `error`, `message`, `path`, optional
`fieldErrors`), produced by a global `@RestControllerAdvice`.

### `GET /api/investors/{id}/portfolio`

Returns the investor's details and their products.

**200 OK**
```json
{
  "id": 1,
  "firstName": "Thandiwe",
  "lastName": "Nkosi",
  "email": "thandiwe.nkosi@example.com",
  "dateOfBirth": "1955-03-14",
  "age": 71,
  "products": [
    { "id": 1, "name": "Retirement Annuity", "type": "RETIREMENT", "balance": 850000.00 }
  ]
}
```

**404 Not Found** (unknown investor id)
```json
{
  "timestamp": "2026-08-19T14:00:00Z",
  "status": 404,
  "error": "Not Found",
  "message": "Investor not found with id 999",
  "path": "/api/investors/999/portfolio"
}
```

### `POST /api/withdrawals`

Submits a withdrawal against a product.

**Request**
```json
{ "productId": 1, "amount": 1000.00 }
```

**201 Created**
```json
{
  "id": 1,
  "productId": 1,
  "productName": "Retirement Annuity",
  "amount": 1000.00,
  "balanceAfter": 849000.00,
  "requestedAt": "2026-08-19T16:07:34.033771"
}
```

**422 Unprocessable Entity** (a business rule was broken — the request was well-formed)
```json
{
  "timestamp": "2026-08-19T14:00:00Z",
  "status": 422,
  "error": "Unprocessable Entity",
  "message": "Retirement withdrawals are only permitted for investors older than 65. Investor is 46 years old.",
  "path": "/api/withdrawals"
}
```

**400 Bad Request** (missing/invalid field)
```json
{
  "timestamp": "2026-08-19T14:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed for one or more fields.",
  "path": "/api/withdrawals",
  "fieldErrors": { "amount": "Withdrawal amount must be greater than zero" }
}
```

**404 Not Found** (unknown product id)

### `GET /api/investors/{id}/withdrawals`

Returns the investor's withdrawal history, newest first.

**200 OK**
```json
[
  {
    "id": 1,
    "productId": 1,
    "productName": "Retirement Annuity",
    "amount": 1000.00,
    "balanceAfter": 849000.00,
    "requestedAt": "2026-08-19T16:07:34.033771"
  }
]
```

### `GET /api/investors/{id}/statement.csv?from=YYYY-MM-DD&to=YYYY-MM-DD`

Downloads the withdrawal history as a CSV file (`Content-Type: text/csv`,
`Content-Disposition: attachment; filename="statement-{id}.csv"`). `from`/`to` are optional whole calendar
days; when both are absent, every withdrawal is returned. `from` after `to` returns **400 Bad Request**
rather than a silently empty file.

```csv
Withdrawal ID,Product ID,Product Name,Amount,Balance After,Requested At
1,1,Retirement Annuity,1000.00,849000.00,2026-08-19T16:07:34.033771
```

## Screenshots

**Portfolio dashboard**

![Portfolio dashboard](screenshots/dashboard.png)

**422 error state** (withdrawal amount exceeding the balance — the real rule text is shown, not a generic
message)

![422 error state](screenshots/withdrawal-error-422.png)

**Withdrawal submitted, dashboard and history updated without a page reload**

![Withdrawal history](screenshots/withdrawal-history.png)

## AI usage disclosure

Built with **Claude (Anthropic), via Claude Code**, used across the whole system: initial entity/repository
scaffolding, the service layer and its four business rules, the DTO layer, the global exception handler, REST
controllers, the vanilla JS frontend, and the unit tests below. This wasn't a single unreviewed generation —
every step was explained back afterward in plain language (what the class does, why it's structured that way)
and reviewed before moving on, and several AI-authored decisions were caught and corrected during that review
rather than accepted as given, for example:

- An initial argument for *not* validating `amount` client-side via Bean Validation (reasoning: it would make
  the service's own check "dead code") was wrong and was corrected — a unit test calling the service directly
  never goes through Bean Validation at all, so the service check is never dead. Both layers now validate it,
  deliberately, for different reasons.
- A `LazyInitializationException` risk was flagged and fixed *before* it could occur at runtime: entity→DTO
  mapping was moved inside the `@Transactional` service methods rather than left to controllers.
- Age was originally computed in three places (the retirement-age rule, the portfolio DTO, and a
  client-side JS reimplementation) — flagged as the same "two sources of truth" risk already avoided for the
  90% rule, and consolidated into one method (`Investor.getAge()`).
- A missing `from > to` validation on the CSV date range (which would have silently returned an empty file
  instead of a 400) was identified and fixed.
- Before writing the unit tests below, `mvn test` was actually run against zero tests to confirm Surefire
  executes in this project (no `skipTests`/`maven.test.skip` anywhere) rather than assuming it.

I can explain and defend every class in this codebase, including the reasoning behind each of the corrections
above.

## Unit tests

`WithdrawalServiceTest` (`mvn test`) uses Mockito to test `WithdrawalService` against mocked repositories —
no Spring context, no database:

- one test per business rule (positive amount, retirement age, balance, 90% cap), each asserting the specific
  `WithdrawalRuleException` message and that neither repository's `save` was called, and
- one happy-path test asserting the balance was actually debited on the persisted entity and the notice was
  saved with the correct amount/balance-after/product.
