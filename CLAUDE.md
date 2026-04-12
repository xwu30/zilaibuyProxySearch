# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Deploy

```bash
# Requires Java 17 (Temurin). Always set JAVA_HOME explicitly:
export JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home
export PATH="$JAVA_HOME/bin:$HOME/Library/Python/3.8/bin:$PATH"

# Build (skip tests)
mvn clean package -DskipTests

# Deploy to AWS Elastic Beanstalk (builds + uploads to S3 + deploys)
bash deploy.sh

# Compile check only
mvn compile -q
```

AWS CLI is at `~/Library/Python/3.8/bin/aws`. EB environment name: `Zilaibuy-backend-env`, app name: `zilaibuy-backend`, region: `us-east-1`.

## Architecture

Spring Boot 3.3.2 / Java 17 backend for the ZilaiBuy Japan-to-China proxy shopping service. Runs on port 5000 on AWS Elastic Beanstalk (Corretto 17).

### Package layout

- **`api/`** — Thin controllers for Rakuten live-search proxying (`RakutenController`)
- **`controller/`** — All other REST endpoints (auth, orders, parcels, products, books, items, etc.)
- **`service/`** — Business logic; notably `RakutenSyncService` / `RakutenBookSyncService` for DB sync
- **`rakuten/`** — Rakuten API clients (`RakutenClient` for Ichiba, `RakutenBooksClient` for Books), mapper, DTOs, and `RakutenProperties`
- **`entity/`** — JPA entities; key ones: `RakutenItemEntity` (Ichiba cache), `RakutenBookEntity` (Books cache), `UserEntity`, `OrderEntity`, `ForwardingParcelEntity`
- **`repository/`** — Spring Data JPA repos
- **`scheduler/`** — `RakutenSyncScheduler` runs nightly at 03:00 (Ichiba) and 03:30 (Books) America/Toronto
- **`security/`** — JWT auth (`JwtAuthFilter`, `JwtUtil`), `SecurityConfig` with per-endpoint rules
- **`config/`** — `WebClientConfig`, `DataInitializer` (creates admin users + triggers first-run Books sync if table empty)

### Rakuten API integration — critical notes

Two separate Rakuten platforms with different auth:

| Client | Base URL | Auth | Used for |
|--------|----------|------|---------|
| `RakutenClient` | `https://openapi.rakuten.co.jp` (from `rakuten.base-url`) | UUID `applicationId` + `accessKey` | Ichiba marketplace search |
| `RakutenBooksClient` | `https://openapi.rakuten.co.jp/services/api/BooksBook/Search/20170404` (hardcoded) | UUID `applicationId` + `accessKey` | Books search |

**`RakutenBooksClient` uses `WebClient.Builder` directly (not the `rakutenWebClient` bean)** — this is intentional to avoid the `baseUrl=openapi.rakuten.co.jp` set on the shared bean conflicting with path resolution. If you add more Books-style clients, follow the same pattern.

`app.rakuten.co.jp` rejects UUID applicationIds with `specify valid applicationId` — always use `openapi.rakuten.co.jp` for both Ichiba and Books.

Local curl tests from dev machine will return `CLIENT_IP_NOT_ALLOWED` — this is expected; only the EB server IP is whitelisted by Rakuten.

### DB sync pattern (Ichiba & Books)

Both sync services follow the same pattern:
1. Fetch pages from Rakuten API with retry on 429
2. Batch-load existing entities by unique key (`itemCode` / `isbn`)
3. Translate only new/changed titles via `GoogleTranslateService.translateBatch()`
4. `saveAll()` with upsert semantics
5. After full keyword sweep, call `deactivateStaleItems()` to soft-delete unseen records

Books DB endpoint: `GET /api/books/search` — the frontend searches DB first, falls back to live API if 0 results.

### Security

`SecurityConfig` controls per-endpoint access. Public GET endpoints are explicitly listed with `.permitAll()`. Admin-only endpoints use `.hasRole("ADMIN")`. When adding new public endpoints, add them to SecurityConfig explicitly — the default is authenticated.

JWT secret, DB credentials, Rakuten keys, and all other secrets are injected via EB environment variables (never hardcoded).

### Key environment variables

```
RAKUTEN_APPLICATION_ID          # UUID, Ichiba app
RAKUTEN_ACCESS_KEY              # pk_... , Ichiba app
RAKUTEN_BOOKS_APPLICATION_ID    # UUID, Books app
RAKUTEN_BOOKS_ACCESS_KEY        # pk_... , Books app
SPRING_DATASOURCE_URL / USERNAME / PASSWORD
JWT_SECRET
GOOGLE_TRANSLATE_API_KEY
```
