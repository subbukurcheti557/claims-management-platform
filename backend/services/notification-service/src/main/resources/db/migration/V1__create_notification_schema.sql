CREATE TABLE notification (
    id                 UUID           PRIMARY KEY,
    recipient_user_id  UUID           NOT NULL,
    event_id           UUID           NOT NULL,
    event_type         VARCHAR(50)    NOT NULL,
    category           VARCHAR(20)    NOT NULL,
    claim_id           UUID           NOT NULL,
    claim_number       VARCHAR(30)    NOT NULL,
    title              VARCHAR(200)   NOT NULL,
    message            VARCHAR(2000)  NOT NULL,
    created_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    read_at            TIMESTAMP WITH TIME ZONE,
    -- second line of defence against duplicates, after processed_event
    CONSTRAINT uk_notification_event_recipient UNIQUE (event_id, recipient_user_id),
    CONSTRAINT ck_notification_category CHECK (category IN ('INFO', 'ACTION_REQUIRED', 'DECISION'))
);

CREATE INDEX ix_notification_recipient ON notification (recipient_user_id, created_at DESC);
CREATE INDEX ix_notification_unread ON notification (recipient_user_id) WHERE read_at IS NULL;

CREATE TABLE processed_event (
    event_id      UUID         PRIMARY KEY,
    event_type    VARCHAR(50)  NOT NULL,
    processed_at  TIMESTAMP WITH TIME ZONE NOT NULL
);
