--liquibase formatted sql

--changeset creditsai:002-create-client-attachments
CREATE TABLE client_attachments (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    client_id    BIGINT       NOT NULL REFERENCES clients (id),
    file_name    VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL CHECK (size_bytes >= 0),
    sha256       VARCHAR(64)  NOT NULL,
    uploaded_by  VARCHAR(255) NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_client_attachments_client_file UNIQUE (client_id, file_name)
);
-- The unique constraint's index (client_id, file_name) also serves lookups by client_id.
--rollback DROP TABLE client_attachments;
