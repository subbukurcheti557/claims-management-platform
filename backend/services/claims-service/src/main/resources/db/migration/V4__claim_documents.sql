-- Accident photos and supporting documents (ADR-013).
-- The bytes are in object storage (MinIO / S3) under storage_key; this table holds the metadata and a SHA-256
-- of the content. Rows are never updated or deleted through the API: documents are evidence.
CREATE TABLE claim_document (
    id                UUID PRIMARY KEY,
    claim_id          UUID         NOT NULL REFERENCES claim (id),
    category          VARCHAR(30)  NOT NULL
        CHECK (category IN ('DAMAGE_PHOTO', 'POLICE_REPORT', 'REPAIR_ESTIMATE', 'MEDICAL_REPORT', 'OTHER')),
    file_name         VARCHAR(200) NOT NULL,
    content_type      VARCHAR(100) NOT NULL
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'application/pdf')),
    size_bytes        BIGINT       NOT NULL CHECK (size_bytes > 0),
    sha256            VARCHAR(64)  NOT NULL,
    storage_key       VARCHAR(300) NOT NULL UNIQUE,
    uploaded_by_id    UUID         NOT NULL,
    uploaded_by_name  VARCHAR(200),
    uploaded_by_role  VARCHAR(30)  NOT NULL,
    uploaded_at       TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_document_claim ON claim_document (claim_id, uploaded_at);
-- the same file on several claims is a fraud signal worth looking up later
CREATE INDEX ix_document_sha256 ON claim_document (sha256);
