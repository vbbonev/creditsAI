--liquibase formatted sql

--changeset creditsai:001-create-clients
CREATE TABLE clients (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    identifier  VARCHAR(50)  NOT NULL,
    address     VARCHAR(200) NOT NULL,
    description VARCHAR(500),
    archived    BOOLEAN      NOT NULL DEFAULT false,
    archived_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
--rollback DROP TABLE clients;

--changeset creditsai:001-create-clients-identifier-index
CREATE INDEX idx_clients_identifier ON clients (identifier);
--rollback DROP INDEX idx_clients_identifier;

--changeset creditsai:001-create-clients-archived-index
CREATE INDEX idx_clients_archived ON clients (archived);
--rollback DROP INDEX idx_clients_archived;
