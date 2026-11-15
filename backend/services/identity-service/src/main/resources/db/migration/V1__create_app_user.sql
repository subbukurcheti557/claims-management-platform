CREATE TABLE app_user (
    id             UUID PRIMARY KEY,
    email          VARCHAR(255) NOT NULL,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(200) NOT NULL,
    role           VARCHAR(30)  NOT NULL,
    market         VARCHAR(2)   NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_app_user_email UNIQUE (email),
    CONSTRAINT ck_app_user_role CHECK (role IN ('CLAIMANT', 'CLAIMS_OFFICER', 'CLAIMS_MANAGER'))
);

CREATE INDEX ix_app_user_role ON app_user (role);
