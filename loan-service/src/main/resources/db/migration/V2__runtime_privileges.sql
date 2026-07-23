REVOKE ALL ON SCHEMA origination FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA origination FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA origination FROM PUBLIC;
GRANT USAGE ON SCHEMA origination TO "${runtimeRole}";
GRANT SELECT,INSERT ON ALL TABLES IN SCHEMA origination TO "${runtimeRole}";
GRANT UPDATE ON application,document,loan,durable_job,request_claim TO "${runtimeRole}";
-- Sensitive payload erasure uses this separately guarded function, never arbitrary UPDATE permissions.
CREATE FUNCTION purge_application_payload(p_id uuid,p_cutoff timestamptz) RETURNS boolean
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
    UPDATE application SET draft=NULL,purged_at=now(),version=version+1 WHERE id=p_id;
    RETURN true;
END $$;
REVOKE ALL ON FUNCTION purge_application_payload(uuid,timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION purge_application_payload(uuid,timestamptz) TO "${runtimeRole}";

