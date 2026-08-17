CREATE TABLE verification_review (
    id uuid PRIMARY KEY, application_id uuid NOT NULL, revision integer NOT NULL,
    gate varchar(30) NOT NULL CHECK(gate='COMPLIANCE'), outcome varchar(20) NOT NULL CHECK(outcome IN ('SATISFIED','BLOCKED')),
    reviewer varchar(200) NOT NULL, evidence_reference varchar(200) NOT NULL, explanation text,
    valid_until timestamptz NOT NULL, created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY(application_id,revision) REFERENCES application_revision(application_id,revision)
);
CREATE INDEX verification_review_revision_idx ON verification_review(application_id,revision,created_at DESC);
CREATE TABLE manual_extraction_review (
    id uuid PRIMARY KEY, application_id uuid NOT NULL REFERENCES application(id), revision integer NOT NULL,
    source_document uuid NOT NULL REFERENCES document(id), output_document uuid NOT NULL REFERENCES document(id) UNIQUE,
    reviewer varchar(200) NOT NULL, explanation text, created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER verification_review_immutable BEFORE UPDATE OR DELETE ON verification_review FOR EACH ROW EXECUTE FUNCTION immutable_record();
CREATE TRIGGER extraction_review_immutable BEFORE UPDATE OR DELETE ON manual_extraction_review FOR EACH ROW EXECUTE FUNCTION immutable_record();
REVOKE ALL ON verification_review,manual_extraction_review FROM PUBLIC;
GRANT SELECT,INSERT ON verification_review,manual_extraction_review TO "${runtimeRole}";
-- No free text survives erasure: reviewer identity, outcome and revision metadata remain.
CREATE OR REPLACE FUNCTION immutable_record() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='UPDATE' AND current_setting('origination.retention_purge',true)='on' THEN
        IF TG_TABLE_NAME IN ('application_revision','assessment','credit_decision')
           AND (to_jsonb(NEW)-'payload'-'applicant_explanation')=(to_jsonb(OLD)-'payload'-'applicant_explanation')
           AND (to_jsonb(NEW)->>'payload') IS NULL THEN RETURN NEW; END IF;
        IF TG_TABLE_NAME IN ('verification_review','manual_extraction_review','review_request')
           AND (to_jsonb(NEW)-'explanation'-'payload')=(to_jsonb(OLD)-'explanation'-'payload')
           AND (to_jsonb(NEW)->>'explanation') IS NULL AND (to_jsonb(NEW)->>'payload') IS NULL THEN RETURN NEW; END IF;
    END IF;
    RAISE EXCEPTION 'Record is append only' USING ERRCODE='23514';
END $$;
ALTER TABLE review_request ALTER COLUMN payload DROP NOT NULL;
CREATE OR REPLACE FUNCTION purge_application_payload(p_id uuid,p_cutoff timestamptz) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path=origination,pg_temp AS $$
DECLARE a application%ROWTYPE;
BEGIN
    SELECT * INTO a FROM application WHERE id=p_id FOR UPDATE;
    IF NOT FOUND OR a.legal_hold OR a.state NOT IN ('REJECTED','WITHDRAWN') OR a.updated_at>=p_cutoff THEN RETURN false; END IF;
    IF EXISTS(SELECT 1 FROM document WHERE application_id=p_id AND purged_at IS NULL) THEN RETURN false; END IF;
    PERFORM set_config('origination.retention_purge','on',true);
    UPDATE application_revision SET payload=NULL WHERE application_id=p_id;
    UPDATE assessment SET payload=NULL WHERE application_id=p_id;
    UPDATE credit_decision SET payload=NULL,applicant_explanation=NULL WHERE application_id=p_id;
    UPDATE verification_review SET explanation=NULL WHERE application_id=p_id;
    UPDATE manual_extraction_review SET explanation=NULL WHERE application_id=p_id;
    UPDATE review_request SET payload=NULL WHERE application_id=p_id;
    UPDATE application SET draft=NULL,purged_at=now(),version=version+1 WHERE id=p_id;
    RETURN true;
END $$;

