--liquibase formatted sql

--changeset creditsai:003-attachment-file-name-case-insensitive-unique
ALTER TABLE client_attachments DROP CONSTRAINT uq_client_attachments_client_file;
CREATE UNIQUE INDEX uq_client_attachments_client_file_ci ON client_attachments (client_id, lower(file_name));
--rollback DROP INDEX uq_client_attachments_client_file_ci;
--rollback ALTER TABLE client_attachments ADD CONSTRAINT uq_client_attachments_client_file UNIQUE (client_id, file_name);
