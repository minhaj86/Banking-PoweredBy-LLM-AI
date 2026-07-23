CREATE TABLE application (
    id uuid PRIMARY KEY, tenant varchar(80) NOT NULL, applicant varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0, state varchar(30) NOT NULL CHECK(state IN
    ('DRAFT','SUBMITTED','ASSESSMENT_PENDING','DOCUMENTS_REQUIRED','MANUAL_REVIEW','ASSESSMENT_FAILED','APPROVED','REJECTED','WITHDRAWN')),
    current_revision integer NOT NULL DEFAULT 0, legal_hold boolean NOT NULL DEFAULT false,
    draft text, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL, purged_at timestamptz
);
CREATE INDEX application_owner_idx ON application(tenant,applicant,created_at DESC,id);
CREATE INDEX application_backlog_idx ON application(tenant,state,updated_at);
CREATE TABLE application_revision (
    id uuid PRIMARY KEY, application_id uuid NOT NULL REFERENCES application(id), revision integer NOT NULL,
    product_id varchar(60) NOT NULL, product_version integer NOT NULL, policy_version varchar(80) NOT NULL,
    payload text, document_ids text NOT NULL, policy_snapshot text NOT NULL, submitted_at timestamptz NOT NULL,
    UNIQUE(application_id,revision)
);
CREATE TABLE document (
    id uuid PRIMARY KEY, application_id uuid NOT NULL REFERENCES application(id), uploaded_for_revision integer NOT NULL,
    category varchar(40) NOT NULL CHECK(category IN ('PAYSLIP','TAX_ASSESSMENT','EMPLOYMENT_EVIDENCE','EXISTING_LOAN_STATEMENT','CREDIT_CARD_STATEMENT','OTHER')),
    object_key varchar(200) NOT NULL UNIQUE, checksum varchar(64) NOT NULL, media_type varchar(40) NOT NULL,
    size_bytes bigint NOT NULL CHECK(size_bytes>0), period_start date NOT NULL, period_end date NOT NULL CHECK(period_end>=period_start),
    supersedes uuid REFERENCES document(id), uploaded_at timestamptz NOT NULL, expires_at timestamptz NOT NULL, purged_at timestamptz,
    upload_status varchar(20) NOT NULL, scan_status varchar(30) NOT NULL, processing_status varchar(30) NOT NULL,
    extraction_version varchar(80), evidence text, version bigint NOT NULL DEFAULT 0,
    UNIQUE(application_id,supersedes)
);
CREATE INDEX document_application_idx ON document(application_id,uploaded_for_revision,uploaded_at);
CREATE INDEX document_expiry_idx ON document(expires_at) WHERE purged_at IS NULL;
CREATE TABLE assessment (
    id uuid PRIMARY KEY, application_id uuid NOT NULL, revision integer NOT NULL,
    policy_version varchar(80) NOT NULL, product_version integer NOT NULL, payload text,
    created_at timestamptz NOT NULL, UNIQUE(application_id,revision),
    FOREIGN KEY(application_id,revision) REFERENCES application_revision(application_id,revision)
);
CREATE TABLE credit_decision (
    id uuid PRIMARY KEY, application_id uuid NOT NULL, revision integer NOT NULL, assessment_id uuid REFERENCES assessment(id),
    reviewer varchar(200) NOT NULL, outcome varchar(30) NOT NULL CHECK(outcome IN ('APPROVE','REJECT','REQUEST_INFORMATION')),
    payload text, applicant_explanation text, created_at timestamptz NOT NULL,
    FOREIGN KEY(application_id,revision) REFERENCES application_revision(application_id,revision)
);
CREATE UNIQUE INDEX final_decision_unique ON credit_decision(application_id) WHERE outcome IN ('APPROVE','REJECT');
CREATE TABLE loan (
    id uuid PRIMARY KEY, public_reference varchar(60) NOT NULL UNIQUE,
    application_id uuid NOT NULL UNIQUE, approved_revision integer NOT NULL, tenant varchar(80) NOT NULL,
    applicant varchar(200) NOT NULL, decision_id uuid NOT NULL UNIQUE REFERENCES credit_decision(id), approved_at timestamptz NOT NULL,
    lifecycle varchar(30) NOT NULL CHECK(lifecycle IN ('PENDING_LEDGER_ACCOUNT','AWAITING_AGREEMENT','PROVISIONING_FAILED')),
    provisioning_status varchar(20) NOT NULL CHECK(provisioning_status IN ('PENDING','COMPLETE','FAILED')),
    agreement_status varchar(20) NOT NULL CHECK(agreement_status='NOT_ACCEPTED'), version bigint NOT NULL DEFAULT 0,
    FOREIGN KEY(application_id,approved_revision) REFERENCES application_revision(application_id,revision)
);
CREATE INDEX loan_owner_idx ON loan(tenant,applicant,id);
CREATE INDEX loan_provisioning_idx ON loan(provisioning_status,approved_at);
CREATE TABLE loan_terms (
    loan_id uuid PRIMARY KEY REFERENCES loan(id), product_id varchar(60) NOT NULL, product_version integer NOT NULL,
    policy_version varchar(80) NOT NULL, principal numeric NOT NULL CHECK(principal>0 AND principal=trunc(principal,2)),
    currency varchar(3) NOT NULL CHECK(currency='SEK'), term_months integer NOT NULL CHECK(term_months BETWEEN 1 AND 600),
    nominal_annual_rate numeric NOT NULL CHECK(nominal_annual_rate BETWEEN 0 AND 1 AND nominal_annual_rate=trunc(nominal_annual_rate,6)),
    repayment_frequency varchar(10) NOT NULL CHECK(repayment_frequency='MONTHLY'), indicative_schedule text NOT NULL
);
CREATE TABLE ledger_account_mapping (
    loan_id uuid PRIMARY KEY REFERENCES loan(id), purpose varchar(30) NOT NULL CHECK(purpose='PRINCIPAL_RECEIVABLE'),
    ledger_id uuid NOT NULL, account_id uuid NOT NULL, external_reference varchar(200) NOT NULL,
    currency varchar(3) NOT NULL CHECK(currency='SEK'), created_at timestamptz NOT NULL,
    UNIQUE(loan_id,purpose), UNIQUE(ledger_id,account_id), UNIQUE(ledger_id,external_reference)
);
CREATE TABLE durable_job (
    id uuid PRIMARY KEY, kind varchar(20) NOT NULL CHECK(kind IN ('DOCUMENT','ASSESS','PROVISION','PURGE')),
    target_id uuid NOT NULL, revision integer NOT NULL DEFAULT 0,
    state varchar(20) NOT NULL CHECK(state IN ('READY','RUNNING','DONE','FAILED')),
    attempts integer NOT NULL DEFAULT 0, fence bigint NOT NULL DEFAULT 0,
    available_at timestamptz NOT NULL DEFAULT now(), lease_until timestamptz,
    last_error varchar(80), created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE(kind,target_id,revision)
);
CREATE INDEX job_claim_idx ON durable_job(kind,available_at,created_at) WHERE state IN ('READY','RUNNING');
CREATE TABLE request_claim (
    tenant varchar(80) NOT NULL, caller varchar(200) NOT NULL, operation varchar(40) NOT NULL,
    target varchar(100) NOT NULL, key varchar(128) NOT NULL CHECK(key ~ '^[A-Za-z0-9._:-]{1,128}$'),
    fingerprint varchar(64) NOT NULL, result_id uuid, created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY(tenant,caller,operation,target,key)
);
CREATE TABLE audit_event (
    id uuid PRIMARY KEY, tenant varchar(80) NOT NULL, actor varchar(200) NOT NULL, action varchar(60) NOT NULL,
    application_id uuid REFERENCES application(id), revision integer, resource_id uuid, occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_application_idx ON audit_event(tenant,application_id,occurred_at);
CREATE TABLE review_request (
    id uuid PRIMARY KEY, application_id uuid NOT NULL REFERENCES application(id), revision integer NOT NULL,
    requester varchar(200) NOT NULL, payload text NOT NULL, created_at timestamptz NOT NULL DEFAULT now()
);

CREATE FUNCTION immutable_record() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    -- Erasure is a narrow privacy operation; references, financial terms and audit history remain immutable.
    IF TG_OP='UPDATE' AND TG_TABLE_NAME IN ('application_revision','assessment','credit_decision')
       AND current_setting('origination.retention_purge',true)='on'
       AND (to_jsonb(NEW)-'payload'-'applicant_explanation')=(to_jsonb(OLD)-'payload'-'applicant_explanation')
       AND NEW.payload IS NULL THEN RETURN NEW; END IF;
    RAISE EXCEPTION 'Record is append only' USING ERRCODE='23514';
END $$;
CREATE TRIGGER revision_immutable BEFORE UPDATE OR DELETE ON application_revision FOR EACH ROW EXECUTE FUNCTION immutable_record();
CREATE TRIGGER assessment_immutable BEFORE UPDATE OR DELETE ON assessment FOR EACH ROW EXECUTE FUNCTION immutable_record();
CREATE TRIGGER decision_immutable BEFORE UPDATE OR DELETE ON credit_decision FOR EACH ROW EXECUTE FUNCTION immutable_record();
CREATE TRIGGER terms_immutable BEFORE UPDATE OR DELETE ON loan_terms FOR EACH ROW EXECUTE FUNCTION immutable_record();
CREATE TRIGGER mapping_immutable BEFORE UPDATE OR DELETE ON ledger_account_mapping FOR EACH ROW EXECUTE FUNCTION immutable_record();
CREATE TRIGGER audit_immutable BEFORE UPDATE OR DELETE ON audit_event FOR EACH ROW EXECUTE FUNCTION immutable_record();
CREATE TRIGGER review_request_immutable BEFORE UPDATE OR DELETE ON review_request FOR EACH ROW EXECUTE FUNCTION immutable_record();

CREATE FUNCTION guard_loan() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' OR (to_jsonb(NEW)-'lifecycle'-'provisioning_status'-'version') IS DISTINCT FROM
       (to_jsonb(OLD)-'lifecycle'-'provisioning_status'-'version') THEN
       RAISE EXCEPTION 'Approved loan identity is immutable' USING ERRCODE='23514'; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER loan_guard BEFORE UPDATE OR DELETE ON loan FOR EACH ROW EXECUTE FUNCTION guard_loan();

