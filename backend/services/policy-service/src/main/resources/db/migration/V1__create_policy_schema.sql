CREATE TABLE policy (
    policy_number   VARCHAR(30)    PRIMARY KEY,
    holder_user_id  UUID           NOT NULL,
    holder_name     VARCHAR(200)   NOT NULL,
    product         VARCHAR(20)    NOT NULL,
    market          VARCHAR(2)     NOT NULL,
    currency        VARCHAR(3)     NOT NULL,
    insured_item    VARCHAR(300)   NOT NULL,
    sum_insured     NUMERIC(19, 2) NOT NULL,
    deductible      NUMERIC(19, 2) NOT NULL,
    start_date      DATE           NOT NULL,
    end_date        DATE           NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    paid_to_date    NUMERIC(19, 2) NOT NULL DEFAULT 0,
    version         BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_policy_product CHECK (product IN ('MOTOR', 'PROPERTY')),
    CONSTRAINT ck_policy_status CHECK (status IN ('ACTIVE', 'LAPSED', 'CANCELLED')),
    CONSTRAINT ck_policy_period CHECK (end_date >= start_date)
);

CREATE INDEX ix_policy_holder ON policy (holder_user_id);

CREATE TABLE processed_event (
    event_id      UUID         PRIMARY KEY,
    event_type    VARCHAR(50)  NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
