-- A few notifications matching the seeded claims, so the bell is not empty on first login.
INSERT INTO notification (id, recipient_user_id, event_id, event_type, category, claim_id, claim_number, title, message,
                          created_at, read_at)
VALUES
('a0000000-0000-0000-0000-000000000001', '88888888-8888-8888-8888-888888888888', 'b0000000-0000-0000-0000-000000000001',
 'InfoRequested', 'ACTION_REQUIRED', 'c0000000-0000-0000-0000-000000000005', 'CLM-TH-2026-000005',
 'Action required on CLM-TH-2026-000005',
 'Your claims officer asked: Please upload photos of the damaged flooring and furniture, and a repair quotation from a contractor.',
 CURRENT_TIMESTAMP - INTERVAL '4' DAY, NULL),
('a0000000-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000002',
 'ClaimSettled', 'DECISION', 'c0000000-0000-0000-0000-000000000009', 'CLM-SG-2026-000009',
 'Payment made', 'We have paid SGD 3,200.00 for claim CLM-SG-2026-000009 (reference PAY-SG-55120).',
 CURRENT_TIMESTAMP - INTERVAL '40' DAY, CURRENT_TIMESTAMP - INTERVAL '39' DAY),
('a0000000-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111', 'b0000000-0000-0000-0000-000000000003',
 'ClaimSubmitted', 'INFO', 'c0000000-0000-0000-0000-000000000001', 'CLM-SG-2026-000001',
 'Claim received', 'We have received claim CLM-SG-2026-000001. A claims officer will review it shortly.',
 CURRENT_TIMESTAMP - INTERVAL '1' DAY, NULL),
('a0000000-0000-0000-0000-000000000004', '66666666-6666-6666-6666-666666666666', 'b0000000-0000-0000-0000-000000000004',
 'ClaimApproved', 'DECISION', 'c0000000-0000-0000-0000-000000000008', 'CLM-AU-2026-000008',
 'Claim approved', 'Claim CLM-AU-2026-000008 has been approved for AUD 6,200.00. Payment will follow shortly.',
 CURRENT_TIMESTAMP - INTERVAL '1' DAY, NULL);
