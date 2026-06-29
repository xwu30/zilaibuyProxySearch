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

### 增值服务 / Add-on Services (VAS)

`VasRequestEntity` (table `vas_requests`) drives per-parcel/order add-on services. Status flow: `PENDING → PROCESSING → DONE → PAID → COMPLETED` (+ `CANCELLED`). On the frontend "DONE" displays as "Awaiting Payment".

Two kinds of VAS requests, distinguished by the `services` column:

- **Standard fixed services** — `services` is a comma list of codes (`item_inspect`/`photo`/`special_pack`). Per-parcel inspection/photo/packaging. **Charged 200 / 300 / 300 JPY** respectively. Customer applies (entry: "Apply for Add-on Service" or a parcel's "Add-ons" button), warehouse fills per-service photos/notes and marks DONE, customer pays standalone, then results un-blur. These same fees can alternatively be bundled into the freight bill at order time (ShippingPaymentModal, path X) — both paths use the same 200/300.
- **Custom / negotiate-price tasks** — `services == "custom"`. Customer describes a task + budget; admin quotes `adminQuoteJpy`; customer may counter (`POST /api/parcels/custom-vas/{id}/counter-offer` → bounces DONE→PROCESSING, stores `customerCounterNote`, emails warehouse, shows in admin "Negotiating" tab); final charge = `adminQuoteJpy`.

**Critical — the fee map is duplicated in FOUR places and must stay in sync** (200/300/300): `PaymentController`, `PaypalPaymentController`, `OttPayController` (all `create-vas`/`create-intent`), and `TransactionHistoryController`. Each branches `"custom".equals(services)` → use `adminQuoteJpy`, else sum the fee map. (The values were once 4300/6400 — a stale CNY→JPY leftover that overcharged ~21×; fixed June 2026.) Frontend mirrors 200/300 in `VasPaymentModal`, `OrderList`, `VasApplyModal`, and the receipt generator (`downloadVasReceipt`).

### FedEx 打单 / Ship label (`FedExService` + `FedExController`, admin-only `/api/fedex/**`)

Quote → Ship → (re)download label → void. Default shipper is the Japan warehouse
**HBT TRADING CO LTD**, so most labels are international (JP→CA etc.). Creds come from
EB env vars `FEDEX_CLIENT_ID/SECRET/ACCOUNT_NUMBER`; prod runs `FEDEX_SANDBOX=false`
(**real, billable labels** — base URL `apis.fedex.com`). `getRates` omits `serviceType`
so FedEx returns all services; the UI lets the user pick. **Customer/dimensions in CM
(converted to IN), weight in KG (converted to LB); duties payer = SENDER/RECIPIENT/THIRD_PARTY.**

**Hard-won gotchas (FedEx returns a useless generic `INVALID.INPUT.EXCEPTION: Invalid field
value` with NO field name in prod — diagnose by replaying the exact request body, see below):**
- **Never send `totalWeight`** on the Ship request — it alone triggers the generic 422 (string OR
  number). FedEx derives the total from per-package weights. `totalPackageCount` is fine. This was
  *the* bug that blocked all 出单.
- **`stateOrProvinceCode` only for US/CA/MX/PR** — a JP shipper state like "Shiga" (or any >2-char /
  non-state-code country) is rejected. `normalizeState()` omits it elsewhere.
- **Phone numbers must be digits only** (`normalizePhone()` strips `+`/spaces/hyphens) and a real
  length — a 6-digit junk phone is rejected.
- **Customs commodity needs `numberOfPieces`**; **`harmonizedCode` is optional**.
- **Customs `description` must be specific** — "test"/"gift"/vague text → `CUSTOMS.DESCRIPTION.INCOMPLETE`
  (this is operator data, not a code bug). Use e.g. "Plastic phone case".
- Account is **not authorized for FEDEX_GROUND** — use international/express services only.
- `getRates` (no contact/customs/`totalWeight`) succeeding while `createShipment` 422s is the tell
  that the bad field is ship-only. Reproduce by reading the request body the service logs and POSTing
  variants straight to `apis.fedex.com/ship/v1/shipments` with an OAuth token (error bodies are gzipped).

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
