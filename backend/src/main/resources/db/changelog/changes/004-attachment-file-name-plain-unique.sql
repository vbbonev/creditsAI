--liquibase formatted sql

--changeset creditsai:004-attachment-file-name-plain-unique
--preconditions onFail:HALT onFailMessage:Cannot migrate: client_attachments contains file names that are not lower case. Files on disk cannot be renamed by Liquibase; rename the files and rows to lower case first.
--precondition-sql-check expectedResult:0 SELECT count(*) FROM client_attachments WHERE file_name <> lower(file_name)
DROP INDEX uq_client_attachments_client_file_ci;
ALTER TABLE client_attachments ADD CONSTRAINT uq_client_attachments_client_file UNIQUE (client_id, file_name);
--rollback ALTER TABLE client_attachments DROP CONSTRAINT uq_client_attachments_client_file;
--rollback CREATE UNIQUE INDEX uq_client_attachments_client_file_ci ON client_attachments (client_id, lower(file_name));
