CREATE SEQUENCE claim_number_seq START WITH 100 INCREMENT BY 1;

CREATE TABLE claim (
    id                     UUID           PRIMARY KEY,
    claim_number           VARCHAR(30)    NOT NULL,
    policy_number          VARCHAR(30)    NOT NULL,
    claimant_id            UUID           NOT NULL,
    claimant_name          VARCHAR(200)   NOT NULL,
    claim_type             VARCHAR(20)    NOT NULL,
    market                 VARCHAR(2)     NOT NULL,
    currency               VARCHAR(3)     NOT NULL,
    incident_date          DATE           NOT NULL,
    incident_location      VARCHAR(300)   NOT NULL,
    description            VARCHAR(4000)  NOT NULL,
    injury_reported        BOOLEAN        NOT NULL DEFAULT FALSE,
    estimated_loss         NUMERIC(19, 2) NOT NULL,
    sum_insured_snapshot   NUMERIC(19, 2) NOT NULL,
    deductible_snapshot    NUMERIC(19, 2) NOT NULL,
    insured_item           VARCHAR(300),
    reserve_amount         NUMERIC(19, 2) NOT NULL,
    approved_amount        NUMERIC(19, 2),
    paid_amount            NUMERIC(19, 2),
    payment_reference      VARCHAR(60),
    status                 VARCHAR(20)    NOT NULL,
    priority               VARCHAR(10)    NOT NULL,
    assigned_officer_id    UUID,
    assigned_officer_name  VARCHAR(200),
    rejection_reason_code  VARCHAR(30),
    decision_note          VARCHAR(2000),
    sla_due_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    submitted_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    closed_at              TIMESTAMP WITH TIME ZONE,
    updated_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    version                BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uk_claim_number UNIQUE (claim_number),
    CONSTRAINT ck_claim_status CHECK (status IN ('SUBMITTED', 'UNDER_REVIEW', 'INFO_REQUESTED', 'ASSESSED',
                                                  'APPROVED', 'SETTLED', 'REJECTED', 'WITHDRAWN')),
    CONSTRAINT ck_claim_type CHECK (claim_type IN ('MOTOR', 'PROPERTY')),
    CONSTRAINT ck_claim_priority CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_claim_amounts CHECK (estimated_loss > 0 AND reserve_amount >= 0)
);

CREATE INDEX ix_claim_claimant ON claim (claimant_id, submitted_at DESC);
CREATE INDEX ix_claim_officer_status ON claim (assigned_officer_id, status);
CREATE INDEX ix_claim_status_sla ON claim (status, sla_due_at);
CREATE INDEX ix_claim_market_status ON claim (market, status);

CREATE TABLE claim_status_history (
    id           UUID          PRIMARY KEY,
    claim_id     UUID          NOT NULL REFERENCES claim (id),
    from_status  VARCHAR(20),
    to_status    VARCHAR(20)   NOT NULL,
    actor_id     UUID          NOT NULL,
    actor_name   VARCHAR(200),
    actor_role   VARCHAR(30)   NOT NULL,
    comment      VARCHAR(2000),
    occurred_at  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_history_claim ON claim_status_history (claim_id, occurred_at);

CREATE TABLE info_request (
    id                 UUID           PRIMARY KEY,
    claim_id           UUID           NOT NULL REFERENCES claim (id),
    question           VARCHAR(2000)  NOT NULL,
    response           VARCHAR(4000),
    status             VARCHAR(20)    NOT NULL,
    requested_by       UUID           NOT NULL,
    requested_by_name  VARCHAR(200),
    requested_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    responded_at       TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_info_request_status CHECK (status IN ('OPEN', 'ANSWERED', 'CANCELLED'))
);

CREATE INDEX ix_info_request_claim ON info_request (claim_id);

CREATE TABLE assessment (
    id                  UUID           PRIMARY KEY,
    claim_id            UUID           NOT NULL REFERENCES claim (id),
    liability           VARCHAR(20)    NOT NULL,
    recommended_amount  NUMERIC(19, 2) NOT NULL,
    reserve_amount      NUMERIC(19, 2) NOT NULL,
    findings            VARCHAR(4000)  NOT NULL,
    assessor_id         UUID           NOT NULL,
    assessor_name       VARCHAR(200),
    assessed_at         TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_assessment_claim ON assessment (claim_id);

CREATE TABLE claim_note (
    id           UUID           PRIMARY KEY,
    claim_id     UUID           NOT NULL REFERENCES claim (id),
    author_id    UUID           NOT NULL,
    author_name  VARCHAR(200),
    body         VARCHAR(4000)  NOT NULL,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_note_claim ON claim_note (claim_id, created_at DESC);

CREATE TABLE outbox_event (
    id            UUID          PRIMARY KEY,
    aggregate_id  UUID          NOT NULL,
    event_type    VARCHAR(50)   NOT NULL,
    payload       JSONB         NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE,
    attempts      INT           NOT NULL DEFAULT 0,
    last_error    VARCHAR(1000)
);

-- the relay only ever scans unpublished rows
CREATE INDEX ix_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;
