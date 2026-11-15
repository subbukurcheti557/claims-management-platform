-- Demo claims across all six markets and every status, so queues and dashboards are meaningful on first launch.
-- claimant/officer ids match identity-service's seeded users; policy numbers match policy-service's seed.
-- Dates are relative to when the database is created. SLA: HIGH 7 days, MEDIUM 14, LOW 21 from submission.

INSERT INTO claim (id, claim_number, policy_number, claimant_id, claimant_name, claim_type, market, currency,
                   incident_date, incident_location, description, injury_reported, estimated_loss,
                   sum_insured_snapshot, deductible_snapshot, insured_item, reserve_amount, approved_amount,
                   paid_amount, payment_reference, status, priority, assigned_officer_id, assigned_officer_name,
                   rejection_reason_code, decision_note, sla_due_at, submitted_at, closed_at, updated_at, version)
VALUES
('c0000000-0000-0000-0000-000000000001', 'CLM-SG-2026-000001', 'SG-MOT-1001',
 '11111111-1111-1111-1111-111111111111', 'Alice Tan', 'MOTOR', 'SG', 'SGD', CAST(CURRENT_DATE - INTERVAL '2' DAY AS DATE),
 'Car park, Tampines Mall', 'Rear bumper damaged by a reversing van in the mall car park. The other driver exchanged details.',
 FALSE, 4200.00, 56800.00, 500.00, 'Toyota Corolla Altis, SGX 1234 A', 3700.00, NULL, NULL, NULL,
 'SUBMITTED', 'LOW', NULL, NULL, NULL, NULL,
 CURRENT_TIMESTAMP + INTERVAL '20' DAY, CURRENT_TIMESTAMP - INTERVAL '1' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '1' DAY, 0),

('c0000000-0000-0000-0000-000000000002', 'CLM-HK-2026-000002', 'HK-MOT-1002',
 '22222222-2222-2222-2222-222222222222', 'Bob Wong', 'MOTOR', 'HK', 'HKD', CAST(CURRENT_DATE - INTERVAL '3' DAY AS DATE),
 'Tolo Highway near Sha Tin', 'Rear-ended at low speed in heavy traffic. The driver has neck pain and visited A&E.',
 TRUE, 85000.00, 300000.00, 3000.00, 'Honda Civic, HK AB 1234', 82000.00, NULL, NULL, NULL,
 'SUBMITTED', 'HIGH', NULL, NULL, NULL, NULL,
 CURRENT_TIMESTAMP + INTERVAL '5' DAY, CURRENT_TIMESTAMP - INTERVAL '2' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '2' DAY, 0),

('c0000000-0000-0000-0000-000000000003', 'CLM-AU-2026-000003', 'AU-PRP-2002',
 '66666666-6666-6666-6666-666666666666', 'Carol Smith', 'PROPERTY', 'AU', 'AUD', CAST(CURRENT_DATE - INTERVAL '10' DAY AS DATE),
 '45 Harbour Street, Sydney NSW', 'Storm blew tiles off the roof and water came through the ceiling of two bedrooms.',
 FALSE, 32000.00, 781500.00, 1000.00, '45 Harbour Street, Sydney NSW 2000', 31000.00, NULL, NULL, NULL,
 'SUBMITTED', 'MEDIUM', NULL, NULL, NULL, NULL,
 CURRENT_TIMESTAMP + INTERVAL '5' DAY, CURRENT_TIMESTAMP - INTERVAL '9' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '9' DAY, 0),

('c0000000-0000-0000-0000-000000000004', 'CLM-MY-2026-000004', 'MY-MOT-1004',
 '77777777-7777-7777-7777-777777777777', 'Dev Kumar', 'MOTOR', 'MY', 'MYR', CAST(CURRENT_DATE - INTERVAL '7' DAY AS DATE),
 'Jalan Ampang, Kuala Lumpur', 'Side mirror and front door scraped by a motorcycle while stopped at traffic lights.',
 FALSE, 9800.00, 120000.00, 1000.00, 'Perodua Myvi, WXY 1234', 8800.00, NULL, NULL, NULL,
 'UNDER_REVIEW', 'LOW', '44444444-4444-4444-4444-444444444444', 'Omar Rahman', NULL, NULL,
 CURRENT_TIMESTAMP + INTERVAL '15' DAY, CURRENT_TIMESTAMP - INTERVAL '6' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '5' DAY, 1),

('c0000000-0000-0000-0000-000000000005', 'CLM-TH-2026-000005', 'TH-PRP-2003',
 '88888888-8888-8888-8888-888888888888', 'Tara Suksan', 'PROPERTY', 'TH', 'THB', CAST(CURRENT_DATE - INTERVAL '13' DAY AS DATE),
 '88 Sukhumvit Soi 21, Bangkok', 'Flood water entered the ground floor after heavy rain, damaging the flooring and furniture.',
 FALSE, 450000.00, 5000000.00, 20000.00, '88 Sukhumvit Soi 21, Bangkok 10110', 430000.00, NULL, NULL, NULL,
 'INFO_REQUESTED', 'MEDIUM', '33333333-3333-3333-3333-333333333333', 'Olivia Lim', NULL, NULL,
 CURRENT_TIMESTAMP + INTERVAL '2' DAY, CURRENT_TIMESTAMP - INTERVAL '12' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '4' DAY, 2),

('c0000000-0000-0000-0000-000000000006', 'CLM-ID-2026-000006', 'ID-MOT-1005',
 '99999999-9999-9999-9999-999999999999', 'Indra Wijaya', 'MOTOR', 'ID', 'IDR', CAST(CURRENT_DATE - INTERVAL '16' DAY AS DATE),
 'Jalan Sudirman, Jakarta', 'Windscreen cracked by debris from a truck on the toll road, with scratches on the bonnet.',
 FALSE, 28000000.00, 400000000.00, 2500000.00, 'Toyota Avanza, B 1234 XYZ', 24000000.00, NULL, NULL, NULL,
 'ASSESSED', 'LOW', '44444444-4444-4444-4444-444444444444', 'Omar Rahman', NULL, NULL,
 CURRENT_TIMESTAMP + INTERVAL '6' DAY, CURRENT_TIMESTAMP - INTERVAL '15' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '3' DAY, 2),

('c0000000-0000-0000-0000-000000000007', 'CLM-SG-2026-000007', 'SG-PRP-2001',
 '11111111-1111-1111-1111-111111111111', 'Alice Tan', 'PROPERTY', 'SG', 'SGD', CAST(CURRENT_DATE - INTERVAL '21' DAY AS DATE),
 '12 Tampines Avenue 5, #08-12', 'A burst pipe under the kitchen sink caused a flood in the kitchen and damaged the cabinets.',
 FALSE, 18500.00, 400000.00, 1000.00, '12 Tampines Avenue 5, #08-12, Singapore 529653', 16000.00, NULL, NULL, NULL,
 'ASSESSED', 'MEDIUM', '33333333-3333-3333-3333-333333333333', 'Olivia Lim', NULL, NULL,
 CURRENT_TIMESTAMP - INTERVAL '6' DAY, CURRENT_TIMESTAMP - INTERVAL '20' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '2' DAY, 2),

('c0000000-0000-0000-0000-000000000008', 'CLM-AU-2026-000008', 'AU-MOT-1003',
 '66666666-6666-6666-6666-666666666666', 'Carol Smith', 'MOTOR', 'AU', 'AUD', CAST(CURRENT_DATE - INTERVAL '11' DAY AS DATE),
 'Pacific Highway, Chatswood', 'Hit a kangaroo at dusk. The front grille and a headlight are broken.',
 FALSE, 7000.00, 45000.00, 800.00, 'Mazda CX-5, NSW CS 22 KL', 6200.00, 6200.00, NULL, NULL,
 'APPROVED', 'LOW', '33333333-3333-3333-3333-333333333333', 'Olivia Lim', NULL, NULL,
 CURRENT_TIMESTAMP + INTERVAL '11' DAY, CURRENT_TIMESTAMP - INTERVAL '10' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '1' DAY, 3),

('c0000000-0000-0000-0000-000000000009', 'CLM-SG-2026-000009', 'SG-MOT-1001',
 '11111111-1111-1111-1111-111111111111', 'Alice Tan', 'MOTOR', 'SG', 'SGD', CAST(CURRENT_DATE - INTERVAL '61' DAY AS DATE),
 'PIE towards Changi', 'Minor collision when another car changed lanes without signalling. The rear door panel is dented.',
 FALSE, 3700.00, 60000.00, 500.00, 'Toyota Corolla Altis, SGX 1234 A', 0.00, 3200.00, 3200.00, 'PAY-SG-55120',
 'SETTLED', 'LOW', '33333333-3333-3333-3333-333333333333', 'Olivia Lim', NULL, NULL,
 CURRENT_TIMESTAMP - INTERVAL '39' DAY, CURRENT_TIMESTAMP - INTERVAL '60' DAY, CURRENT_TIMESTAMP - INTERVAL '40' DAY,
 CURRENT_TIMESTAMP - INTERVAL '40' DAY, 4),

('c0000000-0000-0000-0000-000000000010', 'CLM-AU-2026-000010', 'AU-PRP-2002',
 '66666666-6666-6666-6666-666666666666', 'Carol Smith', 'PROPERTY', 'AU', 'AUD', CAST(CURRENT_DATE - INTERVAL '81' DAY AS DATE),
 '45 Harbour Street, Sydney NSW', 'A kitchen fire from a faulty oven damaged the cabinets and the range hood.',
 FALSE, 21000.00, 800000.00, 1000.00, '45 Harbour Street, Sydney NSW 2000', 0.00, 18500.00, 18500.00, 'PAY-AU-90311',
 'SETTLED', 'MEDIUM', '44444444-4444-4444-4444-444444444444', 'Omar Rahman', NULL, NULL,
 CURRENT_TIMESTAMP - INTERVAL '66' DAY, CURRENT_TIMESTAMP - INTERVAL '80' DAY, CURRENT_TIMESTAMP - INTERVAL '50' DAY,
 CURRENT_TIMESTAMP - INTERVAL '50' DAY, 4),

('c0000000-0000-0000-0000-000000000011', 'CLM-HK-2026-000011', 'HK-MOT-1002',
 '22222222-2222-2222-2222-222222222222', 'Bob Wong', 'MOTOR', 'HK', 'HKD', CAST(CURRENT_DATE - INTERVAL '80' DAY AS DATE),
 'Nathan Road, Kowloon', 'A scratch along the passenger side, found after parking overnight and reported weeks later.',
 FALSE, 12000.00, 300000.00, 3000.00, 'Honda Civic, HK AB 1234', 0.00, NULL, NULL, NULL,
 'REJECTED', 'LOW', '33333333-3333-3333-3333-333333333333', 'Olivia Lim', 'LATE_NOTIFICATION',
 'The damage was reported more than 30 days after it happened, which is outside the notification period in section 7 of your policy.',
 CURRENT_TIMESTAMP - INTERVAL '24' DAY, CURRENT_TIMESTAMP - INTERVAL '45' DAY, CURRENT_TIMESTAMP - INTERVAL '30' DAY,
 CURRENT_TIMESTAMP - INTERVAL '30' DAY, 3),

('c0000000-0000-0000-0000-000000000012', 'CLM-MY-2026-000012', 'MY-MOT-1004',
 '77777777-7777-7777-7777-777777777777', 'Dev Kumar', 'MOTOR', 'MY', 'MYR', CAST(CURRENT_DATE - INTERVAL '26' DAY AS DATE),
 'Persiaran KLCC, Kuala Lumpur', 'Cracked rear light after a parking knock. The other party offered to pay for it.',
 FALSE, 1500.00, 120000.00, 1000.00, 'Perodua Myvi, WXY 1234', 0.00, NULL, NULL, NULL,
 'WITHDRAWN', 'LOW', NULL, NULL, NULL, 'The other driver paid for the repair directly.',
 CURRENT_TIMESTAMP - INTERVAL '4' DAY, CURRENT_TIMESTAMP - INTERVAL '25' DAY, CURRENT_TIMESTAMP - INTERVAL '22' DAY,
 CURRENT_TIMESTAMP - INTERVAL '22' DAY, 1),

('c0000000-0000-0000-0000-000000000013', 'CLM-TH-2026-000013', 'TH-PRP-2003',
 '88888888-8888-8888-8888-888888888888', 'Tara Suksan', 'PROPERTY', 'TH', 'THB', CAST(CURRENT_DATE - INTERVAL '31' DAY AS DATE),
 '88 Sukhumvit Soi 21, Bangkok', 'An electrical fire in the living room. A neighbour was treated for smoke inhalation.',
 TRUE, 1200000.00, 5000000.00, 20000.00, '88 Sukhumvit Soi 21, Bangkok 10110', 1180000.00, NULL, NULL, NULL,
 'UNDER_REVIEW', 'HIGH', '44444444-4444-4444-4444-444444444444', 'Omar Rahman', NULL, NULL,
 CURRENT_TIMESTAMP - INTERVAL '23' DAY, CURRENT_TIMESTAMP - INTERVAL '30' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '28' DAY, 1),

('c0000000-0000-0000-0000-000000000014', 'CLM-HK-2026-000014', 'HK-MOT-1002',
 '22222222-2222-2222-2222-222222222222', 'Bob Wong', 'MOTOR', 'HK', 'HKD', CAST(CURRENT_DATE - INTERVAL '19' DAY AS DATE),
 'Western Harbour Crossing', 'Multi-car collision in the tunnel. The front end and radiator are damaged.',
 FALSE, 160000.00, 300000.00, 3000.00, 'Honda Civic, HK AB 1234', 157000.00, NULL, NULL, NULL,
 'UNDER_REVIEW', 'MEDIUM', '33333333-3333-3333-3333-333333333333', 'Olivia Lim', NULL, NULL,
 CURRENT_TIMESTAMP - INTERVAL '4' DAY, CURRENT_TIMESTAMP - INTERVAL '18' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '16' DAY, 1),

('c0000000-0000-0000-0000-000000000015', 'CLM-ID-2026-000015', 'ID-MOT-1005',
 '99999999-9999-9999-9999-999999999999', 'Indra Wijaya', 'MOTOR', 'ID', 'IDR', CAST(CURRENT_DATE - INTERVAL '26' DAY AS DATE),
 'Jalan Gatot Subroto, Jakarta', 'The parked car was hit by a falling branch during strong wind. The roof is dented.',
 FALSE, 15000000.00, 400000000.00, 2500000.00, 'Toyota Avanza, B 1234 XYZ', 12500000.00, NULL, NULL, NULL,
 'SUBMITTED', 'LOW', NULL, NULL, NULL, NULL,
 CURRENT_TIMESTAMP - INTERVAL '4' DAY, CURRENT_TIMESTAMP - INTERVAL '25' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '25' DAY, 0),

('c0000000-0000-0000-0000-000000000016', 'CLM-AU-2026-000016', 'AU-PRP-2002',
 '66666666-6666-6666-6666-666666666666', 'Carol Smith', 'PROPERTY', 'AU', 'AUD', CAST(CURRENT_DATE - INTERVAL '4' DAY AS DATE),
 '45 Harbour Street, Sydney NSW', 'Bushfire embers set the garage roof alight. The garage and its contents are badly damaged.',
 FALSE, 120000.00, 781500.00, 1000.00, '45 Harbour Street, Sydney NSW 2000', 119000.00, NULL, NULL, NULL,
 'SUBMITTED', 'HIGH', NULL, NULL, NULL, NULL,
 CURRENT_TIMESTAMP + INTERVAL '4' DAY, CURRENT_TIMESTAMP - INTERVAL '3' DAY, NULL, CURRENT_TIMESTAMP - INTERVAL '3' DAY, 0);

-- Timeline: reported
INSERT INTO claim_status_history (id, claim_id, from_status, to_status, actor_id, actor_name, actor_role, comment, occurred_at)
SELECT gen_random_uuid(), id, NULL, 'SUBMITTED', claimant_id, claimant_name, 'CLAIMANT', 'Claim reported online', submitted_at
FROM claim;

-- Timeline: picked up
INSERT INTO claim_status_history (id, claim_id, from_status, to_status, actor_id, actor_name, actor_role, comment, occurred_at)
SELECT gen_random_uuid(), id, 'SUBMITTED', 'UNDER_REVIEW', assigned_officer_id, assigned_officer_name, 'CLAIMS_OFFICER',
       'Assigned to ' || assigned_officer_name, submitted_at + INTERVAL '1' DAY
FROM claim WHERE assigned_officer_id IS NOT NULL;

-- Timeline: assessed (for claims that went past assessment)
INSERT INTO claim_status_history (id, claim_id, from_status, to_status, actor_id, actor_name, actor_role, comment, occurred_at)
SELECT gen_random_uuid(), id, 'UNDER_REVIEW', 'ASSESSED', assigned_officer_id, assigned_officer_name, 'CLAIMS_OFFICER',
       'Assessed: ACCEPTED', submitted_at + INTERVAL '3' DAY
FROM claim WHERE status IN ('ASSESSED', 'APPROVED', 'SETTLED');

-- Timeline: approved
INSERT INTO claim_status_history (id, claim_id, from_status, to_status, actor_id, actor_name, actor_role, comment, occurred_at)
SELECT gen_random_uuid(), id, 'ASSESSED', 'APPROVED', assigned_officer_id, assigned_officer_name, 'CLAIMS_OFFICER',
       'Approved for ' || currency || ' ' || approved_amount, COALESCE(closed_at - INTERVAL '2' DAY, updated_at)
FROM claim WHERE status IN ('APPROVED', 'SETTLED');

-- Timeline: final decision
INSERT INTO claim_status_history (id, claim_id, from_status, to_status, actor_id, actor_name, actor_role, comment, occurred_at)
SELECT gen_random_uuid(), id,
       CASE status WHEN 'SETTLED' THEN 'APPROVED' WHEN 'REJECTED' THEN 'UNDER_REVIEW' WHEN 'WITHDRAWN' THEN 'SUBMITTED' END,
       status,
       CASE WHEN status = 'WITHDRAWN' THEN claimant_id ELSE assigned_officer_id END,
       CASE WHEN status = 'WITHDRAWN' THEN claimant_name ELSE assigned_officer_name END,
       CASE WHEN status = 'WITHDRAWN' THEN 'CLAIMANT' ELSE 'CLAIMS_OFFICER' END,
       CASE status WHEN 'SETTLED' THEN 'Payment ' || payment_reference || ' recorded'
                   WHEN 'REJECTED' THEN 'Rejected: ' || rejection_reason_code
                   ELSE 'Withdrawn by claimant: ' || decision_note END,
       closed_at
FROM claim WHERE status IN ('SETTLED', 'REJECTED', 'WITHDRAWN');

-- Timeline: information requested on the Bangkok flood claim
INSERT INTO claim_status_history (id, claim_id, from_status, to_status, actor_id, actor_name, actor_role, comment, occurred_at)
VALUES (gen_random_uuid(), 'c0000000-0000-0000-0000-000000000005', 'UNDER_REVIEW', 'INFO_REQUESTED',
        '33333333-3333-3333-3333-333333333333', 'Olivia Lim', 'CLAIMS_OFFICER',
        'Information requested: photos and repair quotation', CURRENT_TIMESTAMP - INTERVAL '4' DAY);

INSERT INTO info_request (id, claim_id, question, response, status, requested_by, requested_by_name, requested_at, responded_at)
VALUES ('d0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000005',
        'Please upload photos of the damaged flooring and furniture, and a repair quotation from a contractor.',
        NULL, 'OPEN', '33333333-3333-3333-3333-333333333333', 'Olivia Lim', CURRENT_TIMESTAMP - INTERVAL '4' DAY, NULL);

INSERT INTO assessment (id, claim_id, liability, recommended_amount, reserve_amount, findings, assessor_id, assessor_name, assessed_at)
VALUES
('e0000000-0000-0000-0000-000000000006', 'c0000000-0000-0000-0000-000000000006', 'ACCEPTED', 24000000.00, 24000000.00,
 'Windscreen replacement and bonnet respray quoted by an approved workshop. Damage consistent with the description.',
 '44444444-4444-4444-4444-444444444444', 'Omar Rahman', CURRENT_TIMESTAMP - INTERVAL '3' DAY),
('e0000000-0000-0000-0000-000000000007', 'c0000000-0000-0000-0000-000000000007', 'ACCEPTED', 16000.00, 16000.00,
 'Plumber report confirms sudden pipe failure (covered). Cabinet replacement quote reviewed; minor wear excluded.',
 '33333333-3333-3333-3333-333333333333', 'Olivia Lim', CURRENT_TIMESTAMP - INTERVAL '2' DAY),
('e0000000-0000-0000-0000-000000000008', 'c0000000-0000-0000-0000-000000000008', 'ACCEPTED', 6200.00, 6200.00,
 'Animal collision is covered. Repair quote for grille and headlight is in line with market rates.',
 '33333333-3333-3333-3333-333333333333', 'Olivia Lim', CURRENT_TIMESTAMP - INTERVAL '7' DAY),
('e0000000-0000-0000-0000-000000000009', 'c0000000-0000-0000-0000-000000000009', 'ACCEPTED', 3200.00, 3200.00,
 'Other driver at fault; dashcam footage confirms. Repair cost agreed with workshop.',
 '33333333-3333-3333-3333-333333333333', 'Olivia Lim', CURRENT_TIMESTAMP - INTERVAL '57' DAY),
('e0000000-0000-0000-0000-000000000010', 'c0000000-0000-0000-0000-000000000010', 'ACCEPTED', 18500.00, 18500.00,
 'Fire brigade report confirms accidental fire from an appliance fault. Contents and fit-out costs verified.',
 '44444444-4444-4444-4444-444444444444', 'Omar Rahman', CURRENT_TIMESTAMP - INTERVAL '77' DAY);

INSERT INTO claim_note (id, claim_id, author_id, author_name, body, created_at)
VALUES
('f0000000-0000-0000-0000-000000000001', 'c0000000-0000-0000-0000-000000000013', '44444444-4444-4444-4444-444444444444',
 'Omar Rahman', 'Waiting for the fire department report before assessing. Chased the station on Monday.',
 CURRENT_TIMESTAMP - INTERVAL '10' DAY),
('f0000000-0000-0000-0000-000000000002', 'c0000000-0000-0000-0000-000000000014', '33333333-3333-3333-3333-333333333333',
 'Olivia Lim', 'Third-party insurer contacted about liability split. Expect a reply this week.',
 CURRENT_TIMESTAMP - INTERVAL '5' DAY);
