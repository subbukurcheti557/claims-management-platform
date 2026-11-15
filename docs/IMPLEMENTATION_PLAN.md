# Implementation Plan & Verification Strategy

Backend first (brief: "majority of your time"), then frontend. Each phase ends with **compile + tests green + a commit**.

## Status (15 Nov 2026)

All phases 0–14 are done and verified, with these changes from the plan below:

- **Repository layout:** independent Maven projects (no parent pom, no shared jars, AI journal entry 2), grouped in
  plain folders `backend/platform/` and `backend/services/` next to `frontend/` and `docs/` (AI journal entry 7).
- **Integration tests:** Testcontainers PostgreSQL instead of H2.
- **Frontend API types:** hand-written, not generated. Generating them from the specs is the open follow-up.
- **Remote names:** `claimant` and `staff` (not `claimant-mfe` / `staff-mfe`).

| Phase | Result |
|---|---|
| 1–8 backend + Docker | 121 tests pass; `docker compose up` brings all services up healthy |
| 9 backend E2E | `scripts/e2e-smoke.mjs`: 35/35 checks |
| 10–12 frontend | shell + 2 remotes build and load via federation; 37 unit tests pass |
| 13 FE E2E | Playwright: 6/6 against the full stack |
| 14 docs | README, ARCHITECTURE, claims-flow.html, AI journal, OpenAPI + event schema exported, screenshots |

## Ports & runtime

| Component | Port | Component | Port |
|---|---|---|---|
| discovery-server | 8761 | shell | 4200 |
| api-gateway | 8080 | claimant-mfe | 4201 |
| identity-service | 8081 | staff-mfe | 4202 |
| policy-service | 8082 | PostgreSQL 16 | 5432 |
| claims-service | 8083 | Kafka (KRaft, apache/kafka) | 9092 |
| notification-service | 8084 | Kafka UI (optional) | 8089 |

Run modes:
* `docker compose --profile frontend up -d --build` (from the repository root) — everything.
* Dev: `docker compose up postgres kafka` + `mvn spring-boot:run` per service + `npm start` in `frontend/`.

## Phases

| # | Phase | Output | Done when |
|---|---|---|---|
| 0 | Repo bootstrap | git init, parent pom, `.gitignore`, docs, AI journal | `mvn -q validate` |
| 1 | Platform | discovery-server, api-gateway (routes, JWT, CORS, `/internal` block, correlation id), `platform-commons`, `event-contracts` | services register in Eureka; gateway 401 without token |
| 2 | identity-service | users table + seed, BCrypt, login, `/me`, `/users` | WebMvc tests for login success/failure/role |
| 3 | policy-service | policy schema + seed, `/mine`, `/{no}`, `/internal/.../coverage`, Kafka consumer `ClaimSettled` (idempotent) | unit tests coverage rules; consumer idempotency test |
| 4 | claims-service core | schema, `Claim` aggregate + state machine, triage, FNOL with coverage adapter + circuit breaker, outbox + relay | state-machine unit tests (every legal/illegal transition), FNOL integration test |
| 5 | claims-service workflow | assign, info request/response, assessment, approve (authority), settle, reject, withdraw, notes, timeline, queue specs | WebMvc security tests per role; 409 concurrency test |
| 6 | claims-service stats | workload, exposure, my stats | aggregate query tests on seeded data |
| 7 | notification-service | consumer + templates, REST, read/unread | consumer + controller tests |
| 8 | Docker | Dockerfiles (multi-stage, Temurin 17, non-root), compose with healthchecks, postgres init | `docker compose up` → all healthy |
| 9 | Backend E2E | scripted smoke (`scripts/e2e-smoke.sh`): login claimant → FNOL → officer pickup → info req → answer → assess → approve → settle → notifications present → policy paid_to_date updated | script exits 0 |
| 10 | Frontend foundation | workspace, Native Federation shell + 2 remotes + shared lib, Material theme, auth, interceptor, guards, generated API types | `ng build` all projects; login works via gateway |
| 11 | Claimant MFE | My claims, FNOL wizard, claim detail, answer info, withdraw | Vitest specs for stores/components |
| 12 | Staff MFE | Queue, workspace & actions, dashboard | Vitest specs |
| 13 | FE E2E | Playwright: claimant FNOL path; officer decision path | green against compose stack |
| 14 | Polish | README run guide, contracts export, ADR files, journal, known gaps | reviewer can run in < 5 min |

Priority if time runs short (in order): 0–9 (backend fully working) → 10–11 → 12 → 13 → 14. Phases 9, 13 are the proof of "working, not just generated".

## Test strategy

| Layer | Tooling | What |
|---|---|---|
| Domain unit | JUnit 5, AssertJ | state transitions, authority limits, triage rules, reserve calc, coverage rules |
| Application | Mockito | command services with ports mocked (FNOL coverage failure, outbox written) |
| Web slice | `@WebMvcTest` + `spring-security-test` (`jwt()`) | validation → 400 problem detail, role matrix 401/403/404, 409 |
| Persistence/integration | `@SpringBootTest` + Testcontainers Postgres (auto-skipped if Docker absent) | Flyway migrations, specifications, stats aggregates |
| Messaging | `spring-kafka-test` `@EmbeddedKafka` | outbox relay publishes; consumers idempotent; DLT on poison message |
| System | `scripts/e2e-smoke.sh` (curl + jq) through gateway | full business flow |
| Frontend unit | Vitest (Angular default) | stores, guards, interceptor, key components |
| Frontend e2e | Playwright | two golden paths |

## Seed data (local profile)

| User | Password | Role |
|---|---|---|
| alice@claimant.test | Password1! | CLAIMANT (SG motor + property policies) |
| bob@claimant.test | Password1! | CLAIMANT (HK motor policy) |
| olivia@staff.test | Password1! | CLAIMS_OFFICER |
| omar@staff.test | Password1! | CLAIMS_OFFICER |
| maya@staff.test | Password1! | CLAIMS_MANAGER |

Plus ~15 historical claims across markets/statuses so the dashboard is meaningful on first launch.

## Risks & mitigations

| Risk | Mitigation |
|---|---|
| Docker daemon not running → no Postgres/Kafka/Testcontainers | Ask user to start Docker Desktop; Testcontainers tests auto-skip; unit/slice/EmbeddedKafka tests still run |
| Native Federation incompatibility with Angular 22 | Pin matching `@angular-architects/native-federation` 22.x; fallback ADR-003 |
| Only JDK 24 locally, Java 17 required | `maven.compiler.release=17`; Lombok ≥ 1.18.38 (Boot-managed) |
| 6 JVMs + Kafka memory | `-Xmx256m` per service in compose |
| Time box (brief 2–3h, cap 5h) | phase ordering above; journal records what was cut and why |
