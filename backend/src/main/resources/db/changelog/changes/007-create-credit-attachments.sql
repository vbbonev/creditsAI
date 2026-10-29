--liquibase formatted sql

--changeset creditsai:007-create-credit-attachments
CREATE TABLE credit_attachments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credit_id    BIGINT       NOT NULL REFERENCES credits (id),
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL CHECK (size_bytes >= 0),
    sha256       VARCHAR(64)  NOT NULL,
    uploaded_by  VARCHAR(255) NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_credit_attachments_credit_file UNIQUE (credit_id, file_name)
);
-- The unique constraint's index (credit_id, file_name) also serves lookups by credit_id.
--rollback DROP TABLE credit_attachments;
