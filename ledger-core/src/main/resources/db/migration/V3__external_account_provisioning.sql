-- Stable business identity and request replay are independent of journal idempotency.
ALTER TABLE account ADD COLUMN external_reference varchar(200);
ALTER TABLE account ADD CONSTRAINT account_external_reference_unique UNIQUE (ledger_id, external_reference);
ALTER TABLE account ADD CONSTRAINT account_external_reference_valid
    CHECK (external_reference IS NULL OR length(btrim(external_reference)) BETWEEN 1 AND 200);

CREATE TABLE account_provisioning_request (
    ledger_id uuid NOT NULL REFERENCES ledger(id),
    idempotency_key varchar(128) NOT NULL CHECK (idempotency_key ~ '^[A-Za-z0-9._:-]{1,128}$'),
    external_reference varchar(200) NOT NULL,
    code varchar(50) NOT NULL,
    name varchar(200) NOT NULL,
    type varchar(10) NOT NULL,
    enforce_non_negative_balance boolean NOT NULL,
    PRIMARY KEY (ledger_id, idempotency_key),
    FOREIGN KEY (ledger_id, external_reference) REFERENCES account(ledger_id, external_reference)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE TRIGGER immutable_account_provisioning_request BEFORE UPDATE OR DELETE ON account_provisioning_request
    FOR EACH ROW EXECUTE FUNCTION reject_journal_mutation();
REVOKE ALL ON account_provisioning_request FROM PUBLIC;
GRANT SELECT, INSERT ON account_provisioning_request TO "${runtimeRole}";
