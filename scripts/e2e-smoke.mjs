#!/usr/bin/env node
// End-to-end smoke test of the running stack, through the API gateway only (no direct service calls).
// Exercises sync REST, the Kafka outbox flow, idempotent consumers and the security rules.
// Usage: node scripts/e2e-smoke.mjs [gatewayUrl]     (default http://localhost:8080)

const GW = process.argv[2] ?? process.env.GATEWAY_URL ?? 'http://localhost:8080';
const PASSWORD = 'Password1!';
let passed = 0;

function ok(message) {
  passed++;
  console.log(`  ✔ ${message}`);
}

function fail(message, detail) {
  console.error(`  ✘ ${message}`);
  if (detail !== undefined) console.error('    ', typeof detail === 'string' ? detail : JSON.stringify(detail));
  process.exit(1);
}

function expect(condition, message, detail) {
  condition ? ok(message) : fail(message, detail);
}

async function call(method, path, token, body) {
  const res = await fetch(GW + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
    },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  });
  const text = await res.text();
  let json;
  try { json = text ? JSON.parse(text) : undefined; } catch { json = text; }
  return { status: res.status, body: json, headers: res.headers };
}

async function login(email) {
  const r = await call('POST', '/api/auth/login', undefined, { email, password: PASSWORD });
  if (r.status !== 200) fail(`login ${email}`, r);
  return r.body.accessToken;
}

async function waitFor(description, probe, timeoutMs = 20000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const result = await probe();
    if (result) return result;
    await new Promise((r) => setTimeout(r, 500));
  }
  fail(`timed out waiting for: ${description}`);
}

async function notificationFor(token, claimNumber, title) {
  return waitFor(`notification "${title}" for ${claimNumber}`, async () => {
    const r = await call('GET', '/api/notifications?size=50', token);
    return r.body?.content?.find((n) => n.claimNumber === claimNumber && n.title.startsWith(title));
  });
}

async function waitForGateway() {
  // services register with Eureka a few seconds after they start; wait until every route answers
  await waitFor('all gateway routes to be ready', async () => {
    try {
      const r = await call('POST', '/api/auth/login', undefined, { email: 'alice@claimant.test', password: PASSWORD });
      if (r.status !== 200) return false;
      const token = r.body.accessToken;
      for (const path of ['/api/policies/mine', '/api/claims/mine', '/api/notifications/unread-count']) {
        if ((await call('GET', path, token)).status !== 200) return false;
      }
      return true;
    } catch { return false; }
  }, 240000);
}

console.log(`E2E smoke test against ${GW}\n`);
await waitForGateway();

console.log('Authentication and edge security');
const alice = await login('alice@claimant.test');
const olivia = await login('olivia@staff.test');
const omar = await login('omar@staff.test');
const maya = await login('maya@staff.test');
ok('claimant, two officers and a manager can log in');
expect((await call('POST', '/api/auth/login', undefined, { email: 'alice@claimant.test', password: 'wrong' })).status === 401,
  'wrong password is rejected with 401');
expect((await call('GET', '/api/claims/mine')).status === 401, 'request without token is rejected at the gateway (401)');
expect([401, 403].includes((await call('GET', '/internal/users/11111111-1111-1111-1111-111111111111', maya)).status),
  'internal service-to-service routes are blocked at the gateway');

console.log('\nClaimant reports an incident (sync coverage check, then Kafka)');
const policiesBefore = await call('GET', '/api/policies/mine', alice);
expect(policiesBefore.status === 200 && policiesBefore.body.length >= 2, `claimant sees own policies (${policiesBefore.body.length})`);
const paidBefore = Number(policiesBefore.body.find((p) => p.policyNumber === 'SG-MOT-1001').paidToDate);

const today = new Date();
// 3 days ago: the seed data already has an open claim on SG-MOT-1001 for 2 days ago (duplicate rule → 409)
const incidentDate = new Date(today.getTime() - 3 * 86400000).toISOString().slice(0, 10);
const fnol = await call('POST', '/api/claims', alice, {
  policyNumber: 'SG-MOT-1001', claimType: 'MOTOR', incidentDate, incidentLocation: 'Orchard Road, Singapore',
  description: 'Rear-ended at the traffic lights on Orchard Road. Bumper and boot lid damaged.',
  injuryReported: false, estimatedLoss: 8000,
});
expect(fnol.status === 201, `claim created (201) as ${fnol.body?.claimNumber}`, fnol.body);
const claim = fnol.body;
expect(claim.status === 'SUBMITTED' && claim.reserveAmount === undefined, 'claimant view is SUBMITTED and hides the reserve');

const lapsed = await call('POST', '/api/claims', alice, {
  policyNumber: 'SG-MOT-0901', claimType: 'MOTOR', incidentDate, incidentLocation: 'Somewhere',
  description: 'Testing a claim against a lapsed policy that must be refused.', injuryReported: false, estimatedLoss: 500,
});
expect(lapsed.status === 422, `claim on a lapsed policy is refused with 422: "${lapsed.body?.detail}"`);
const foreign = await call('POST', '/api/claims', alice, {
  policyNumber: 'HK-MOT-1002', claimType: 'MOTOR', incidentDate, incidentLocation: 'Somewhere',
  description: 'Testing a claim against somebody else’s policy that must be refused.', injuryReported: false, estimatedLoss: 500,
});
expect(foreign.status === 404, 'claim on another person’s policy is refused with 404');
const repeatWhileOpen = await call('POST', '/api/claims', alice, {
  policyNumber: 'SG-MOT-1001', claimType: 'MOTOR', incidentDate, incidentLocation: 'Orchard Road, Singapore',
  description: 'Reporting the same rear-end collision a second time by mistake.', injuryReported: false, estimatedLoss: 8000,
});
expect(repeatWhileOpen.status === 409 && repeatWhileOpen.body?.detail?.includes(claim.claimNumber),
  `same incident while ${claim.claimNumber} is open is refused with 409 "Duplicate claim"`, repeatWhileOpen.body);

await notificationFor(alice, claim.claimNumber, 'Claim received');
ok('Kafka: ClaimSubmitted reached notification-service → claimant notified');

console.log('\nOfficer picks up the claim (optimistic locking)');
const queue = await call('GET', '/api/claims/queue?scope=UNASSIGNED&size=100', olivia);
expect(queue.body.content.some((c) => c.id === claim.id), 'new claim is in the unassigned queue');
const staffView = await call('GET', `/api/claims/${claim.id}`, olivia);
expect(Number(staffView.body.reserveAmount) === 7500, `staff view shows reserve SGD ${staffView.body.reserveAmount} (8000 - 500 deductible)`);

const picked = await call('POST', `/api/claims/${claim.id}/assign`, olivia, { expectedVersion: staffView.body.version });
expect(picked.status === 200 && picked.body.status === 'UNDER_REVIEW', 'Olivia picks it up → UNDER_REVIEW');
const race = await call('POST', `/api/claims/${claim.id}/assign`, omar, { expectedVersion: staffView.body.version });
expect(race.status === 409, 'Omar, with the stale version, gets 409 Conflict');
await notificationFor(alice, claim.claimNumber, 'Claims officer assigned');
ok('Kafka: ClaimAssigned → claimant notified');

console.log('\nInformation request round trip');
const asked = await call('POST', `/api/claims/${claim.id}/info-requests`, olivia, {
  expectedVersion: picked.body.version, question: 'Please send the police report number.',
});
expect(asked.body.status === 'INFO_REQUESTED', 'officer asks a question → INFO_REQUESTED');
await notificationFor(alice, claim.claimNumber, 'Action required');
ok('Kafka: InfoRequested → claimant sees "Action required"');
const requestId = asked.body.infoRequests[0].id;
const answered = await call('POST', `/api/claims/${claim.id}/info-requests/${requestId}/response`, alice,
  { response: 'Police report PR-2026-77812' });
expect(answered.body.status === 'UNDER_REVIEW', 'claimant answers → back to UNDER_REVIEW');
await notificationFor(olivia, claim.claimNumber, 'Claimant responded');
ok('Kafka: InfoProvided → officer notified');

console.log('\nAssessment and decision');
const assessed = await call('POST', `/api/claims/${claim.id}/assessment`, olivia, {
  liability: 'ACCEPTED', recommendedAmount: 7200, reserveAmount: 7200, findings: 'Police report and photos match the description.',
});
expect(assessed.body.status === 'ASSESSED' && Number(assessed.body.reserveAmount) === 7200, 'assessed, reserve now 7200');
const tooEarly = await call('POST', `/api/claims/${claim.id}/settle`, olivia, { paymentReference: 'PAY-X' });
expect(tooEarly.status === 409, 'settling before approval is refused (409, state machine)');
const approved = await call('POST', `/api/claims/${claim.id}/approve`, olivia, { amount: 7200 });
expect(approved.body.status === 'APPROVED', 'approved within the officer authority limit');
await notificationFor(alice, claim.claimNumber, 'Claim approved');
ok('Kafka: ClaimApproved → claimant notified');
const reference = `PAY-E2E-${Date.now()}`;
const settled = await call('POST', `/api/claims/${claim.id}/settle`, olivia, { paymentReference: reference });
expect(settled.body.status === 'SETTLED' && Number(settled.body.reserveAmount) === 0, 'settled, reserve released to 0');

await notificationFor(alice, claim.claimNumber, 'Payment made');
ok('Kafka: ClaimSettled → notification-service notified the claimant');
await waitFor('policy paid-to-date to include the payment', async () => {
  const p = await call('GET', '/api/policies/SG-MOT-1001', alice);
  return Number(p.body.paidToDate) === paidBefore + 7200;
});
ok(`Kafka: ClaimSettled → policy-service raised paid-to-date ${paidBefore} → ${paidBefore + 7200}`);

const repeatAfterPaid = await call('POST', '/api/claims', alice, {
  policyNumber: 'SG-MOT-1001', claimType: 'MOTOR', incidentDate, incidentLocation: 'Orchard Road, Singapore',
  description: 'A second collision on the same day, in the car park after the first one.', injuryReported: false, estimatedLoss: 1500,
});
expect(repeatAfterPaid.status === 201, `same incident date after payment is accepted as ${repeatAfterPaid.body?.claimNumber}`);
const repeatStaff = await call('GET', `/api/claims/${repeatAfterPaid.body.id}`, olivia);
expect(repeatStaff.body.possibleDuplicateOf === claim.claimNumber && repeatStaff.body.priority === 'HIGH',
  `…but flagged for staff as a possible duplicate of ${claim.claimNumber} and triaged HIGH`, repeatStaff.body);
await call('POST', `/api/claims/${repeatAfterPaid.body.id}/withdraw`, alice, { reason: 'Smoke test clean-up' });

const timeline = await call('GET', `/api/claims/${claim.id}/timeline`, alice);
expect(timeline.body.map((t) => t.toStatus).join('>') === 'SUBMITTED>UNDER_REVIEW>INFO_REQUESTED>UNDER_REVIEW>ASSESSED>APPROVED>SETTLED',
  'audit timeline has every status change in order', timeline.body.map((t) => t.toStatus));

console.log('\nAuthority limit and access rules');
const big = await call('POST', '/api/claims', alice, {
  policyNumber: 'SG-PRP-2001', claimType: 'PROPERTY', incidentDate, incidentLocation: '12 Tampines Avenue 5',
  description: 'Fire in the kitchen spread to the living room. Extensive damage to fittings.', injuryReported: false, estimatedLoss: 60000,
});
expect(big.status === 201, `large property claim ${big.body.claimNumber} created`);
const bigStaff = await call('GET', `/api/claims/${big.body.id}`, olivia);
expect(bigStaff.body.priority === 'MEDIUM' || bigStaff.body.priority === 'HIGH', `triaged as ${bigStaff.body.priority}`);
await call('POST', `/api/claims/${big.body.id}/assign`, olivia, {});
await call('POST', `/api/claims/${big.body.id}/assessment`, olivia, {
  liability: 'ACCEPTED', recommendedAmount: 55000, reserveAmount: 55000, findings: 'Loss adjuster confirmed the scope of damage.',
});
const overLimit = await call('POST', `/api/claims/${big.body.id}/approve`, olivia, { amount: 55000 });
expect(overLimit.status === 403, 'officer cannot approve SGD 55,000 (above USD 25k limit) → 403');
const managerApproval = await call('POST', `/api/claims/${big.body.id}/approve`, maya, { amount: 55000 });
expect(managerApproval.body.status === 'APPROVED', 'manager approves it');
expect((await call('GET', '/api/claims/c0000000-0000-0000-0000-000000000002', alice)).status === 404,
  'claimant cannot see another claimant’s claim (404)');
expect((await call('GET', '/api/claims/queue', alice)).status === 403, 'claimant cannot open the staff queue (403)');

console.log('\nManager dashboards');
const exposure = await call('GET', '/api/claims/stats/exposure', maya);
const t = exposure.body.totals;
expect(exposure.status === 200 && Math.abs(t.incurredUsd - (t.outstandingUsd + t.paidUsd)) < 0.01,
  `exposure: ${t.openClaims} open, outstanding USD ${t.outstandingUsd}, paid USD ${t.paidUsd}, ${t.overdueClaims} overdue`);
const workload = await call('GET', '/api/claims/stats/workload', maya);
expect(workload.status === 200 && workload.body.officers.length >= 2,
  `workload: ${workload.body.officers.map((o) => `${o.officerName} ${o.openClaims} open`).join(', ')}; ${workload.body.unassignedClaims} unassigned`);

console.log(`\nAll ${passed} checks passed.`);
