# AI Working Journal

Running log of what was asked of the AI (Claude Code), what was accepted, challenged and overridden, with reasoning.
Entries are in order and not polished on purpose. "Developer" means me; "AI" means the assistant.

---

## Entry 1 — Requirements extraction and architecture plan

**Asked:** Read the assessment brief (.docx), the drive deck (.pptx) and a video. Act as solution architect for Java 17 /
Spring Boot microservices (3 business services, gateway, Eureka, Kafka, PostgreSQL, JWT) and Angular. Produce the
complete plan and flow before writing code.

**AI did:**
- Extracted the text from the .docx and .pptx. It said plainly that it could not process the .mp4 instead of guessing.
- Noticed that the scoring slide gives *MFE architecture* 15 points and *AI collaboration* 25, and proposed Native
  Federation (shell + claimant + staff remotes) instead of a single SPA, plus this journal from day one.
- Proposed claims / policy / notification as the business services, identity as a platform service, REST for the
  coverage check, Kafka for lifecycle events, and a transactional outbox.

**Accepted:** the service split, REST vs Kafka rule, outbox, and the MFE approach.
**Developer's notes:** The service split (claims / policy / notification, with identity as a platform service) and the
rule "REST when the caller needs an answer now, Kafka when it is a fact others react to" made sense to me. But when I
reviewed the plan's folder structure I saw a parent pom with shared jars, which is a Maven multi-module build, not
independent microservices. I challenged it (entry 2). I also asked for PostgreSQL instead of H2 in tests (entry 4), and
I asked for a step-by-step flow (entry 3) so I could explain and defend every step myself.

---

## Entry 2 — Challenged: "Maven multi-module" is not microservices

**Developer challenged:** the plan put all services under a parent `pom.xml` with shared `platform-commons` and
`event-contracts` jars. "It's not Maven multi-module, it's microservice architecture, right?"

**AI's answer:** agreed it was a build choice that made services look and become coupled. A shared jar means one change
forces every service to be rebuilt and redeployed, and consumers compile against the producer's Java class.

**Overrode the plan:** each service became an independent Maven project. The small cross-cutting code (JWT config,
error handler, correlation filter) is copied into each service. The Kafka event is shared as a JSON Schema
(`contracts/events/claims.lifecycle.v1.schema.json`), and each consumer keeps its own small DTO that ignores unknown
fields (tolerant reader). Recorded as ADR-010.

**Trade-off accepted:** about 300 lines duplicated per service, in exchange for independent build and release.

---

## Entry 3 — Developer asked for a step-by-step flow for the presentation

**Asked:** a clear explanation of each step: where Kafka is involved, what is synchronous, which tables change.
**AI produced:** `docs/claims-flow.html` (15 steps, sync vs Kafka per step, then a table-by-table view of which columns
change in which step, failure scenarios and a project overview).
**Developer pushed back twice:** first that "when which tables update" was not clear enough, which led to the
table-by-table section and the "one claim row over time" table; then asked for "what kind of project it is and what it
handles", which led to the overview section. Both were added.

---

## Entry 4 — Developer correction: "we need to use PostgreSQL"

**Context:** the AI had started writing integration tests against H2 in PostgreSQL mode, to avoid depending on Docker.
**Developer said:** use PostgreSQL.
**AI changed course:** runtime was already PostgreSQL, but tests now also run on real PostgreSQL 16 through
Testcontainers. That allowed PostgreSQL features H2 would have hidden (`jsonb` outbox payload, sequences for claim
numbers, partial indexes). Recorded as ADR-011.
**Problem the AI hit and solved:** the Testcontainers version managed by Spring Boot 3.4.5 is too old for Docker Engine
29 (API 1.44+), so it was overridden to 1.21.4.

---

## Entry 5 — Bugs and wrong assumptions found by running things (AI side)

Things the AI got wrong first and fixed only because it compiled, ran tests or drove the real app.

| What went wrong | How it was caught | Fix |
|---|---|---|
| Maven could not download anything (`PKIX path building failed`) | first build | Java does not trust the antivirus/proxy root certificate. Use the Windows trust store (`-Djavax.net.ssl.trustStoreType=Windows-ROOT`). |
| Gateway CORS preflight returned 403 | a gateway test written for exactly this | Two attempts (`@Value` list parsing, then `globalcors`) did not help. Debug logging showed the test client's request had no absolute URL ("origin is malformed"), so it was a test artifact. Kept an explicit `CorsWebFilter`. |
| First smoke run: FNOL returned 503 | `scripts/e2e-smoke.mjs` | claims-service had not registered with Eureka yet. The script now waits until every route answers. |
| Native Federation build failed on `node:fs` | first `ng build` | Material and CDK ship Node-only schematics that federation tried to bundle for the browser. Excluded with `skip`. |
| Playwright could not install a browser | `playwright install` | Playwright 1.56 uses an `fs.rmdir` option removed in Node 26. Upgraded to 1.65. |
| Several e2e and unit tests failed | running them | All test-side problems: the vertical stepper hides other steps' buttons, ambiguous labels, a race between sign-in and navigation, earlier tests using up the policy's sum insured, and a manager's first form being "Reassign" rather than "Approve". The application code did not need changes. |

No application bug was found by the browser tests. The backend tests did catch real design issues during writing:
for example "approve" must require a non-DENIED assessment, and a claim must leave INFO_REQUESTED only when *all*
questions are answered.

---

## Entry 6 — Design decisions the AI made during implementation (review these)

The developer should be able to defend or override each of these:

1. **State machine refined:** ASSESSED can go to INFO_REQUESTED or be re-assessed (ASSESSED → ASSESSED). The first plan
   had ASSESSED → UNDER_REVIEW ("re-open").
2. **SLA = target resolution date** (7/14/21 days by priority), not a first-response deadline. Simpler single clock.
3. **Second synchronous call:** when a manager assigns a claim to someone else, claims-service checks the assignee with
   identity-service. Self pick-up uses the name in the JWT, so it needs no call.
4. **Managers may act on any claim**, officers only on their own.
5. **Frontend API types written by hand** to mirror the exported OpenAPI specs, not generated (planned, not done).
6. **Notifications by polling** every 30 s and on tab focus, not Server-Sent Events.
7. **Policy-service outage refuses FNOL (503)** rather than creating an unverified claim.
8. **Session in sessionStorage**, 60-minute token, no refresh token.

---

## Entry 7 — Developer asked for a clearer repository layout

**Developer asked:** before pushing to GitHub, all six services sat next to `frontend/` and `docs/` at the root; I
wanted the repository grouped as backend / frontend / docs.
**AI suggested** three options and recommended `backend/platform/` (Eureka, gateway) and `backend/services/`
(identity, policy, claims, notification), keeping `contracts/`, `infra/`, `scripts/` and `docker-compose.yml` at
the root because they belong to the whole system. I chose that one.
**Important point the AI raised:** `backend/` must stay a plain folder with **no parent pom**, otherwise it would bring
back the multi-module coupling I rejected in entry 2.
**Done:** `git mv` (history kept), then updated the compose build paths, `build-all.sh`, README and architecture docs.
Re-verified by rebuilding the images and re-running the smoke test.

---

## Entry 8 — Developer found a missing business rule: the same incident claimed twice

**Developer asked:** "For one vehicle a claim was approved and the payment processed. If the same vehicle is claimed
again on the same day, are we handling that?"
**AI checked the code and answered honestly: only partly.** The remaining sum insured went down after each payment,
and each approval was capped by the claim's own FNOL snapshot. But nothing compared a new claim with earlier ones.
The AI also found a second gap I hadn't asked about: two claims open at the same time each saw the full remaining
cover, so together they could pay out more than the sum insured.
**AI recommended, and I agreed, not to hard-block "same vehicle, same day"**, because a car can genuinely be hit
twice. The rule is: same policy and incident date while a claim is open → 409 Duplicate claim; after one was paid →
accept, flag `possibleDuplicateOf` for staff and triage HIGH; rejected or withdrawn claims are ignored. At approval,
amounts on all claims on the policy are capped by its full sum insured. Both checks run under a per-policy
PostgreSQL advisory lock, so concurrent requests cannot both pass (ADR-012).
**Side effect the AI caught:** several existing tests, the smoke script and the Playwright helper filed claims on
the same policy and date, and one collided with a seeded open claim. They now use separate dates.

---

## Verification evidence

- Backend: 131 tests pass (gateway 5, identity 9, policy 15, claims 88, notification 14), on real PostgreSQL and
  embedded Kafka.
- Frontend: 37 Vitest unit tests pass; 6 Playwright browser tests pass against the full Docker stack.
- `scripts/e2e-smoke.mjs`: 38 checks pass through the gateway, including both Kafka consumers (notifications and the
  policy's paid-to-date).
- Screenshots of every main screen are in `docs/screenshots/`.
