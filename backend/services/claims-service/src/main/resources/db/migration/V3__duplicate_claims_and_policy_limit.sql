-- Duplicate-claim detection at FNOL and a payout limit across all claims on a policy.

-- Set when a paid claim already exists for the same policy and incident date (shown to staff only).
ALTER TABLE claim ADD COLUMN possible_duplicate_of VARCHAR(30);

-- The policy's full sum insured, so approvals can be capped across every claim on the policy.
ALTER TABLE claim ADD COLUMN policy_sum_insured NUMERIC(19, 2);

-- Existing rows: the snapshot is what remained at FNOL, so add back what earlier claims on the
-- policy had already paid by then (policy-service's paid-to-date is only ever raised by ClaimSettled).
UPDATE claim c
SET policy_sum_insured = c.sum_insured_snapshot + COALESCE((
        SELECT SUM(o.paid_amount)
        FROM claim o
        WHERE o.policy_number = c.policy_number
          AND o.id <> c.id
          AND o.status = 'SETTLED'
          AND o.closed_at <= c.submitted_at), 0);

ALTER TABLE claim ALTER COLUMN policy_sum_insured SET NOT NULL;

-- The duplicate check looks claims up by policy and incident date.
CREATE INDEX ix_claim_policy_incident ON claim (policy_number, incident_date);
