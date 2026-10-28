--liquibase formatted sql

--changeset creditsai:006-create-credit-payments
CREATE TABLE credit_payments (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    credit_id         BIGINT        NOT NULL REFERENCES credits (id),
    payment_number    INTEGER       NOT NULL CHECK (payment_number >= 1),
    payment_date      DATE          NOT NULL,
    payment           NUMERIC(12,2) NOT NULL,
    principal         NUMERIC(12,2) NOT NULL,
    interest          NUMERIC(12,2) NOT NULL,
    remaining_balance NUMERIC(12,2) NOT NULL,
    CONSTRAINT uq_credit_payments_credit_number UNIQUE (credit_id, payment_number)
);
-- The unique constraint's index (credit_id, payment_number) also serves lookups by credit_id.
--rollback DROP TABLE credit_payments;
