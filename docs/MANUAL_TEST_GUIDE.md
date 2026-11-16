# Manual Test Guide

Use this guide to test every function by hand and to understand what happens underneath. Each test has four parts:

1. **Do:** what you click.
2. **See:** what the screen should show.
3. **Check:** the database rows (DBeaver) and Kafka messages (Kafka UI) that prove it.
4. **Why:** what happened inside the system, and whether it was synchronous (REST) or asynchronous (Kafka).

Follow the tests in order. Later tests use the claim you create in test 3.

---

## Part A — Setup

### A1. Start the stack with clean demo data

Run these commands in the repository root:

```bash
docker compose --profile frontend --profile tools down -v       # -v wipes all data back to the demo seed
docker compose --profile frontend --profile tools up -d
```

Wait about 1–2 minutes until every service shows as registered at http://localhost:8761. You should see
API-GATEWAY, IDENTITY-SERVICE, POLICY-SERVICE, CLAIMS-SERVICE and NOTIFICATION-SERVICE.

| URL | What |
|---|---|
| http://localhost:4200 | The application |
| http://localhost:8080 | API gateway (all browser API calls go here) |
| http://localhost:8761 | Eureka: which services are registered |
| http://localhost:8089 | Kafka UI: topics, messages, consumer groups |
| `localhost:5432` | PostgreSQL (connect with DBeaver) |

**Demo users** all use the password `Password1!`. The sign-in page also has one-click buttons.

| User | Role |
|---|---|
| alice@claimant.test | Claimant, Singapore (policies SG-MOT-1001, SG-PRP-2001, lapsed SG-MOT-0901) |
| bob@claimant.test | Claimant, Hong Kong |
| olivia@staff.test | Claims officer (approval limit USD 25,000) |
| omar@staff.test | Claims officer |
| maya@staff.test | Claims manager (no limit, can reassign) |

**Tip:** the login is stored per browser **tab**, because it uses sessionStorage. You can therefore have Alice in one
tab and Olivia in another, side by side.

---

### A2. Connect PostgreSQL in DBeaver

The system has **one PostgreSQL server with four separate databases**, one per microservice.

**Option 1: one admin connection that shows all four databases (easiest)**

1. In DBeaver, choose **Database → New Database Connection → PostgreSQL → Next**.
2. Fill in:
   - Host: `localhost`
   - Port: `5432`
   - Database: `postgres`
   - Username: `postgres`
   - Password: `postgres`
3. Open the **PostgreSQL** tab, *in the same dialog*, and tick **Show all databases**.
4. Click **Test Connection**. If DBeaver offers to download the driver, accept. Then click **Finish**.
5. Expand **Databases**. You should see `identity_db`, `policy_db`, `claims_db` and `notification_db`. Tables are under
   `<db> → Schemas → public → Tables`.

**Option 2: one connection per service, as each service sees it.** This option proves database-per-service.

| Connection name | Database | Username | Password | Tables |
|---|---|---|---|---|
| identity | `identity_db` | `identity_user` | `identity_pass` | `app_user` |
| policy | `policy_db` | `policy_user` | `policy_pass` | `policy`, `processed_event` |
| claims | `claims_db` | `claims_user` | `claims_pass` | `claim`, `claim_status_history`, `info_request`, `assessment`, `claim_note`, `outbox_event` |
| notification | `notification_db` | `notification_user` | `notification_pass` | `notification`, `processed_event` |

To demonstrate the isolation: edit the *claims* connection and set Database to `policy_db`. **Test Connection fails**
with "permission denied for database policy_db". Each service can reach only its own data.
`flyway_schema_history` in every database shows which migration scripts Flyway ran.

**Useful setting:** in the SQL editor, press **Ctrl+Enter** to run the statement under the cursor. Re-run it after each
test step.

---

### A3. Look at Kafka

**Option 1: Kafka UI in the browser.** Open http://localhost:8089 and choose cluster **icmp-local**.

- **Topics**:
  - `claims.lifecycle.v1` is the main topic, with 3 partitions. Click it, then **Messages** to see each event: its
    key (the claimId), headers (`eventType`, `eventId`) and JSON value.
  - `claims.lifecycle.v1.DLT` is the dead-letter topic, which receives messages that could not be processed.
  - `__consumer_offsets` is Kafka's own bookkeeping topic; ignore it.
- **Consumers**: two groups, `notification-service` and `policy-service`. Each reads every event independently. **Lag
  0** means it has processed everything.

**Option 2: command line.** Use PowerShell or CMD. In Git Bash, put `MSYS_NO_PATHCONV=1` in front of each command.

```bash
# list topics
docker exec icmp-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

# topic details: 3 partitions
docker exec icmp-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic claims.lifecycle.v1

# read all events with key and headers (Ctrl+C to stop)
docker exec -it icmp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic claims.lifecycle.v1 --from-beginning --property print.key=true --property print.headers=true

# watch new events live while you click in the app (leave this running in its own terminal)
docker exec -it icmp-kafka /opt/kafka/bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic claims.lifecycle.v1 --property print.headers=true

# consumer group positions and lag
docker exec icmp-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group notification-service
docker exec icmp-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group policy-service
```

---

### A4. Saved queries (paste into DBeaver)

You will re-run these throughout the tests.

**Q1 — latest claims** (claims_db)
```sql
SELECT claim_number, status, priority, policy_number, estimated_loss, reserve_amount, approved_amount, paid_amount,
       assigned_officer_name, version, sla_due_at, submitted_at
FROM claim ORDER BY submitted_at DESC LIMIT 5;
```

**Q2 — history of one claim** (claims_db). Replace the claim number.
```sql
SELECT h.from_status, h.to_status, h.actor_name, h.actor_role, h.comment, h.occurred_at
FROM claim_status_history h JOIN claim c ON c.id = h.claim_id
WHERE c.claim_number = 'CLM-SG-2026-000100' ORDER BY h.occurred_at;
```

**Q3 — outbox** (claims_db): events waiting for, or already sent to, Kafka.
```sql
SELECT event_type, aggregate_id, created_at, published_at, attempts, last_error, payload
FROM outbox_event ORDER BY created_at DESC LIMIT 10;
```

**Q4 — notifications** (notification_db)
```sql
SELECT claim_number, event_type, category, title, recipient_user_id, created_at, read_at
FROM notification ORDER BY created_at DESC LIMIT 10;
```

**Q5 — processed events** (run in notification_db *and* policy_db): the consumers' "already handled" list.
```sql
SELECT event_type, event_id, processed_at FROM processed_event ORDER BY processed_at DESC LIMIT 10;
```

**Q6 — policies** (policy_db)
```sql
SELECT policy_number, holder_name, status, sum_insured, deductible, paid_to_date, version FROM policy ORDER BY policy_number;
```

**User IDs**, to read `recipient_user_id`:

| ID starts with | User |
|---|---|
| `1111…` | alice |
| `2222…` | bob |
| `3333…` | olivia |
| `4444…` | omar |
| `5555…` | maya |

---

## Part B — Functional tests

### Test 1 — Sign in (JWT)

**Do:**
1. Open http://localhost:4200.
2. Sign in as `alice@claimant.test` with the **wrong** password.
3. Sign in again with `Password1!`.

**See:**
- The wrong password gives "Invalid email or password". The message is the same for an unknown email, so an attacker
  cannot tell which emails exist.
- The correct password opens **My claims**.

**Check:**
- `identity_db.app_user` holds `password_hash` starting with `$2a$`. That is BCrypt; the password itself is never
  stored.
- In the browser, press F12 and open **Application → Session Storage → localhost:4200**. You will see the stored
  token.

**Why:**
- **Synchronous.** The request goes browser → gateway → identity-service, which checks the BCrypt hash and returns a
  JWT signed with HS256. The JWT holds the user id, role, name and market, and is valid for 60 minutes.
- Every later request carries `Authorization: Bearer <token>`.
- The gateway **and** each service verify the signature themselves; they never call identity-service to do it.

---

### Test 2 — Claimant sees own policies

**Do:** as Alice, click **Report an incident**. The policy list loads.

**See:** SG-MOT-1001 (motor), SG-PRP-2001 (property) and SG-MOT-0901 (lapsed).

**Check:** run Q6. Alice's three policies carry her `holder_user_id`, `1111…`.

**Why:** a synchronous `GET /api/policies/mine` call to policy-service. The service takes the user id from the JWT,
not from the URL, so nobody can ask for someone else's policies.

---

### Test 3 — Report a claim (FNOL): the main flow ⭐

**Before you start:** open Kafka UI → Topics → `claims.lifecycle.v1` → Messages, or start the live console consumer
from A3.

**Do:** as Alice, go to **Report an incident**:
1. Policy: **SG-MOT-1001**.
2. Incident date: **3 days ago**. Location: `Orchard Road`. (Not 2 days ago: seeded claim CLM-SG-2026-000001 is already open for that date, see test 4b.)
3. Description: `Rear-ended at the traffic lights, bumper damaged`.
4. Injury: **No**. Estimated loss: **8000**.
5. Review, then submit.

**See:**
- A claim number like `CLM-SG-2026-000100`, with status **Submitted**.
- Within about 2 seconds the 🔔 bell shows **"Claim received"**.

**Check:**

| Where | What changed |
|---|---|
| claims_db `claim` (Q1) | New row. `status=SUBMITTED`, `version=0`, `priority=LOW`, `reserve_amount=7500` (8000 minus the 500 deductible), `sla_due_at` = now + 21 days |
| claims_db `claim_status_history` (Q2) | One row: `null → SUBMITTED`, actor Alice |
| claims_db `outbox_event` (Q3) | One row `ClaimSubmitted`. `published_at` is filled within about 1 s |
| Kafka `claims.lifecycle.v1` | One message. Key = claim id; headers `eventType=ClaimSubmitted`, `eventId=…` |
| notification_db `notification` (Q4) | "Claim received" for `1111…` (Alice), category INFO |
| notification_db `processed_event` (Q5) | The event id |
| policy_db | **No change**. policy-service reads the event but only acts on `ClaimSettled` |

**Why**, step by step:
1. **Synchronous:** claims-service calls policy-service `GET /internal/policies/SG-MOT-1001/coverage`. It checks that
   the policy is Alice's, active on the incident date, for a motor claim, and that some sum insured is left.
2. claims-service sets the triage priority and the reserve, then, in **one database transaction**, inserts `claim`,
   `claim_status_history` and `outbox_event`.
3. The response goes back to the browser. Alice sees her claim at once, without waiting for Kafka.
4. **Asynchronous:** the outbox relay, which runs every 1 s, sends the outbox row to Kafka and sets `published_at`.
5. notification-service consumes the event, writes `notification` and `processed_event`, and the bell polls the new
   notification.

> **Why an outbox?** If claims-service saved the claim and then crashed before sending to Kafka, the event would be
> lost. The outbox row is saved in the same transaction as the claim, so the event is guaranteed to go out
> eventually.

---

### Test 4 — Claims that are refused

**Do:** as Alice, try to report a claim on policy **SG-MOT-0901**, which is lapsed.

**See:** "Policy SG-MOT-0901 is not active (status LAPSED)." The claim is not created.

The other coverage rules can't be reached through the UI, because the wizard sets the claim type from the policy.
They are:
- incident date outside the policy period
- claim type doesn't match the policy
- sum insured fully used
- somebody else's policy, which returns 404

They are covered by `PolicyTest`, the integration tests and `scripts/e2e-smoke.mjs`.

**Check:** Q1 and Q3 show **no new rows**, and Kafka shows no new message.

**Why:**
- The coverage check is synchronous **because** the claimant needs the answer immediately.
- The API returns **422** (business rule failed) as a ProblemDetail JSON body. Press F12 and open **Network** to see it.
- No transaction is opened, so nothing is saved and no event is sent.

---

### Test 4b — The same incident reported twice (409 Duplicate claim)

**Do:** as Alice, report a claim on **SG-MOT-1001** with incident date **2 days ago**, any location and description,
estimated loss 2000.

**See:** "Claim CLM-SG-2026-000001 for policy SG-MOT-1001 and incident date … is already being handled (status
SUBMITTED). Please follow that claim instead of reporting the incident again." The claim is not created.

**Check:** Q1 and Q3 show no new rows. In F12 → Network the response is **409** with title `Duplicate claim`.

**Why:**
- The rule is "same policy + same incident date". The policy identifies the vehicle (or property), and only its
  holder can claim on it.
- While a claim for that incident is still **open** (Submitted to Approved), a second one is refused, because the
  first claim is already handling it.
- If the earlier claim was **rejected or withdrawn**, the incident can be reported again.
- If the earlier claim was **paid**, a new claim is accepted but flagged (test 11b), because a car can genuinely be
  damaged twice in one day.
- The check runs inside the FNOL transaction under a per-policy lock (`pg_advisory_xact_lock`), so a double-click
  cannot create two claims.

---

### Test 5 — Triage priority

**Do:** as Alice, report three more claims:
- Motor claim, loss 3000, injury **Yes**.
- Property claim on **SG-PRP-2001**, loss 60000. *Keep this one for test 12.*
- Property claim, loss 20000, injury No.

**Check:** run Q1.

| Rule | Priority | SLA |
|---|---|---|
| Injury reported | HIGH | 7 days |
| Loss over 50,000 | HIGH | 7 days |
| Loss over 15,000 | MEDIUM | 14 days |
| Description mentions a catastrophe (flood, typhoon, …) | MEDIUM | 14 days |
| Otherwise | LOW | 21 days |

**Why:** Strategy pattern. Each rule is a class, and the highest priority wins. A new rule is a new class, with no
`if/else` changes.

---

### Test 6 — Officer picks up a claim

**Do:**
1. In a **new tab**, sign in as **olivia@staff.test**.
2. Open **Work queue → Unassigned**, then open the claim from test 3.
3. Click **Pick up**.

**See:** the status becomes **Under review**, assigned to Olivia Lim. The staff view also shows the reserve, which the
claimant never sees.

**Check:**
- Q1: `status=UNDER_REVIEW`, `assigned_officer_name=Olivia Lim`, **`version` went from 0 to 1**.
- Q2: a new history row.
- Kafka: a `ClaimAssigned` message.
- Q4: Alice gets "Claims officer assigned".

**Why:**
- **Synchronous** for the update itself.
- **Kafka** for the notification.
- Picking up a claim yourself needs no call to identity-service, because Olivia's name comes from her JWT.

---

### Test 7 — Two officers pick up the same claim at the same time (409)

**Do:**
1. Use tab 1 as Olivia and tab 2 as **omar@staff.test**.
2. In **both** tabs, open the **same unassigned** claim from test 5.
3. Click **Pick up** in Olivia's tab, then in Omar's tab, without refreshing it.

**See:** Omar gets a conflict message: someone else changed the claim, so reload it.

**Check:** Q1 shows the claim is Olivia's, with only one history row for the pickup.

**Why:**
- This is **optimistic locking**. Every request carries `expectedVersion`.
- Omar's tab still had the old version, so claims-service answered **409 Conflict** and did not overwrite anything.
- The `@Version` column also blocks the rare case where both requests arrive in the same millisecond.

---

### Test 8 — Ask the claimant for information, and the claimant answers

**Do:**
1. As Olivia, open the test 3 claim and choose **Ask claimant**.
2. Enter `Please send the police report number` and send it.
3. In Alice's tab, open the claim. Refresh if needed; the bell shows **"Action required"**.
4. Type `PR-2026-77812` and send.

**See:**
- After step 2 the status is **Info requested**.
- After Alice answers, it is back to **Under review**.
- Olivia's bell shows **"Claimant responded"**.

**Check:**

| Where | After the question | After the answer |
|---|---|---|
| `claim` | INFO_REQUESTED, version +1 | UNDER_REVIEW, version +1 |
| `info_request` | New row `status=OPEN` | `status=ANSWERED`, `response`, `responded_at` |
| Kafka | `InfoRequested` | `InfoProvided` |
| `notification` | Alice: ACTION_REQUIRED | Olivia: "Claimant responded" |

**Why:** the claim leaves INFO_REQUESTED only when **all** open questions are answered. That rule lives in the domain
object `Claim`, not in the controller.

---

### Test 9 — Assessment

**Do:** as Olivia, choose **Assess** and enter:
- Liability **Accepted**.
- Recommended **7200**, reserve **7200**.
- Findings: `Police report and photos match`.

**See:** status **Assessed**. You can also re-assess.

**Check:**
- `assessment` has a new row.
- `claim.reserve_amount` is now 7200.
- Kafka has `ClaimAssessed`.
- No notification is sent, because there is no template for this event; the claimant doesn't need to know yet.

**Why:** the reserve is the money the insurer sets aside for this claim. It feeds the manager's exposure dashboard.

---

### Test 10 — Approve within the officer's limit

**Do:** as Olivia, choose **Approve**, enter amount **7200** and confirm.

**See:** status **Approved**. Alice's bell shows "Claim approved".

**Check:**
- `claim.approved_amount = 7200`.
- Kafka has `ClaimApproved`.
- Q4 has the new notification.

**Why:**
- The authority check converts SGD 7200 to USD, which is about 5.4k and under Olivia's USD 25k limit.
- Approval also requires an assessment whose liability is not DENIED.

---

### Test 11 — Settle: payment made, the policy is updated by Kafka ⭐

**Before:** run Q6 and note the `paid_to_date` of SG-MOT-1001.

**Do:** as Olivia, choose **Record payment**, enter reference `PAY-0001` and confirm.

**See:** status **Settled**. Alice's bell shows "Payment made".

**Check:**

| Where | What changed |
|---|---|
| `claim` | `SETTLED`, `paid_amount=7200`, `payment_reference=PAY-0001`, `reserve_amount=0`, `closed_at` set |
| Kafka | `ClaimSettled` |
| policy_db `policy` (Q6) | `paid_to_date` **increased by 7200**, `version` +1 |
| policy_db `processed_event` | The ClaimSettled event id |
| notification_db | "Payment made" for Alice |

**Why:**
- **One Kafka event, two independent consumers.** policy-service and notification-service each have their own
  consumer group, so each gets its own copy.
- claims-service doesn't know or care who listens.
- If policy-service is down, the event waits in Kafka and is applied when the service comes back (test 20).

---

### Test 11b — A new claim for an incident that was already paid is flagged

**Do:** as Alice, report another claim on **SG-MOT-1001** with the **same incident date as test 3** (3 days ago),
estimated loss 1500. Then sign in as Olivia and open the work queue.

**See:**
- Alice: the claim is accepted as usual. She is not told about the flag.
- Olivia: the new claim shows **Possible duplicate** in the queue and has priority **High**. On the claim page, a
  warning banner names the paid claim from test 3.

**Check:** Q1. The new row has `possible_duplicate_of` = the test 3 claim number, `priority=HIGH`, and `sla_due_at` =
now + 7 days.

**Why:**
- A second accident on the same day is possible, so the claim is not refused. Instead, an officer must check it
  before paying.
- The flag is staff-only, so the API leaves it out of the claimant's view.

**Payout limit across claims:** when a claim is approved, the amounts already approved or paid on the policy's
other claims, plus this one, may not exceed the policy's full sum insured. If they would, the API returns 422 with
how much is left. This matters when two claims on one policy are open at the same time, because both saw the same
remaining cover when they were reported. It is hard to set up by hand, so it is covered by
`ClaimsApiIntegrationTest.approvalsAcrossClaimsCannotTakeThePolicyOverItsSumInsured`.

Withdraw this claim as Alice afterwards, if you want the later tests to match the numbers in this guide.

---

### Test 12 — Authority limit: an officer cannot approve a large claim

**Do:**
1. As Olivia, pick up the **60000 property claim** from test 5.
2. Assess it with Accepted, recommended **55000**, reserve 55000.
3. Try to **Approve** 55000.

**See:**
- Olivia's Approve is **refused**: SGD 55,000 is about USD 41k, above her USD 25k limit.
- Sign in as **maya@staff.test** (manager), open the claim and approve it. That works.

**Check:**
- After Olivia's attempt, the claim is unchanged and still ASSESSED.
- After Maya's approval it is APPROVED, and the history row shows actor Maya, role CLAIMS_MANAGER.

**Why:** the API returns **403** with a ProblemDetail. The limit lives in the `AuthorityPolicy` domain class, so it
cannot be bypassed by calling the API directly.

---

### Test 13 — Reject a claim

**Do:** as Olivia, open another claim that is under review, choose **Reject**, pick reason **Insufficient evidence** and
write an explanation of at least 10 characters.

**See:**
- Status **Rejected**.
- The claimant gets "Claim decision" with the reason in plain words.

**Check:**
- `claim.rejection_reason_code`, `decision_note` and `closed_at` are set.
- `reserve_amount=0`.
- Kafka has `ClaimRejected`.

---

### Test 14 — Claimant withdraws a claim

**Do:** as Alice, open a claim that is still **Submitted** and choose **Withdraw claim**, giving a reason.

**See:** status **Withdrawn**. Withdrawing is allowed only while the claim is SUBMITTED or INFO_REQUESTED.

**Check:**
- `claim.status=WITHDRAWN`, `closed_at` set.
- Kafka has `ClaimWithdrawn`.
- If an officer was assigned, they get "Claim withdrawn".

**Try the negative case:** open a SETTLED claim. The Withdraw button is not shown. The UI shows only the actions in
`allowedActions`, which the backend calculates.

---

### Test 15 — Internal notes, which staff see and claimants do not

**Do:** as Olivia, add a note to a claim. Then open the same claim as Alice.

**Check:** the note is a new `claim_note` row. Alice's view has **no notes**.

**Why:** the claimant view is redacted on the server. The UI simply never receives the notes; it isn't just hiding
them.

---

### Test 16 — Manager reassigns a claim

**Do:** as Maya, open one of Olivia's claims, choose **Reassign**, pick **Omar Rahman** and confirm.

**See:** the claim is now Omar's. Omar's bell shows **"Claim assigned to you"**.

**Check:**
- `claim.assigned_officer_name = Omar Rahman`, version +1.
- Kafka has `ClaimAssigned` with `reassigned: true` in its data.
- Alice gets no "officer assigned" message for a reassignment.

**Why:** this is the **second synchronous service-to-service call**. claims-service asks identity-service
`GET /internal/users/{id}` to confirm that Omar exists and is an officer. A circuit breaker protects that call.

---

### Test 17 — Manager dashboard

**Do:** as Maya, open the **Dashboard**.

**See:** open claims, outstanding reserve and paid amount in USD, overdue claims, and claims per officer.

**Check:** compare with SQL in claims_db:
```sql
SELECT status, COUNT(*), SUM(reserve_amount) FROM claim GROUP BY status ORDER BY status;
SELECT assigned_officer_name, COUNT(*) FROM claim
WHERE status IN ('UNDER_REVIEW','INFO_REQUESTED','ASSESSED','APPROVED') GROUP BY assigned_officer_name;
```

**Why:** the stats are computed by claims-service from its own tables. Amounts are converted to USD so that the six
markets can be added together.

---

### Test 18 — Security rules

| Try | Expected |
|---|---|
| As Alice, change the URL to http://localhost:4200/staff/queue | Blocked by the route guard; you are sent back |
| As Alice, open http://localhost:4200/claimant/c0000000-0000-0000-0000-000000000002 (Bob's claim CLM-HK-2026-000002) | "Not found". The API returns **404**, not 403, so the claim's existence isn't revealed |
| In PowerShell: `curl.exe -i http://localhost:8080/api/claims/mine` (no token) | **401** |
| `curl.exe -i http://localhost:8080/internal/users/11111111-1111-1111-1111-111111111111` | **401/403**: internal routes are blocked at the gateway |
| Wait 60 minutes, or delete the token in Session Storage, then click anything | Sent back to sign-in |

---

### Test 19 — Notifications read and unread

**Do:** as Alice, open the bell, click a notification, then click **Mark all read**.

**Check:** Q4 shows `read_at` filled in. The unread count is `read_at IS NULL`, which a partial index supports.

---

## Part C — Resilience tests (good for the presentation)

### Test 20 — A consumer is down, and Kafka keeps the event

1. Stop notification-service: `docker stop icmp-notification`
2. As Olivia, approve or settle a claim. **It still works.** claims-service doesn't depend on notification-service.
3. In Kafka UI, open **Consumers → notification-service**. The **lag is now 1 or more**: the message is waiting.
4. Start it again: `docker start icmp-notification`. Within about 30 s the lag returns to 0, and Q4 shows the
   notification.

**Why:** Kafka decouples the services. Nothing is lost while a consumer is down.

### Test 21 — Policy service is down, so FNOL is refused (circuit breaker)

1. `docker stop icmp-policy`
2. As Alice, report a claim. You get a **503** "service unavailable" message, and nothing is saved.
3. `docker start icmp-policy`. Wait about 30 s for it to register with Eureka, then try again. It works.

**Why:** the coverage check is synchronous and required, so it is a deliberate choice to refuse rather than create an
unverified claim. The circuit breaker fails fast instead of making every user wait for a timeout.

### Test 22 — Duplicate delivery does no harm (idempotent consumer)

1. Stop notification-service: `docker stop icmp-notification`
2. Rewind its consumer group to the start of the topic:
   ```bash
   docker exec icmp-kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group notification-service --topic claims.lifecycle.v1 --reset-offsets --to-earliest --execute
   ```
3. `docker start icmp-notification`. It now receives **every event again**.
4. Q4: **no duplicate notifications**. `SELECT COUNT(*) FROM notification` is unchanged. The logs show that each event
   was already handled and skipped: `docker logs icmp-notification | findstr "already processed"`.

**Why:** Kafka guarantees *at least once* delivery, so duplicates can happen. Each consumer stores the `eventId` in
`processed_event` and skips events it has seen. The unique key `(event_id, recipient_user_id)` is a second guard.

### Test 23 — A broken message goes to the dead-letter topic

1. Send a message that is not valid JSON:
   ```bash
   docker exec -it icmp-kafka /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server localhost:9092 --topic claims.lifecycle.v1
   ```
   Type `this is not json`, press Enter, then Ctrl+C.
2. In Kafka UI, open **Topics → claims.lifecycle.v1.DLT → Messages**. There are **two** copies of the bad message,
   one from each consumer group. Their headers name the group (`kafka_dlt-original-consumer-group`) and the
   exception (`JsonParseException`).
3. Both consumer groups still have lag 0. **The broken message did not block the events after it.**

**Why:** the consumers retry a few times, then move the message to the DLT, so one bad message can't stop the
pipeline.

---

## Part D — Reset

To start again from the clean demo data:
```bash
docker compose --profile frontend --profile tools down -v
docker compose --profile frontend --profile tools up -d
```
