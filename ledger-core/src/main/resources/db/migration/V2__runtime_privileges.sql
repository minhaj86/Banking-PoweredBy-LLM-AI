-- runtimeRole is an operator-controlled SQL identifier, never an API input.
REVOKE ALL ON SCHEMA ledger FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA ledger FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA ledger FROM PUBLIC;
GRANT USAGE ON SCHEMA ledger TO "${runtimeRole}";
GRANT SELECT, INSERT ON ledger, account, journal_transaction, journal_entry, idempotency_record TO "${runtimeRole}";
GRANT UPDATE (status) ON account TO "${runtimeRole}";
GRANT UPDATE (state) ON journal_transaction TO "${runtimeRole}";
-- Entry guard SELECT FOR UPDATE on the header needs the above UPDATE privilege.
-- Functions execute as invoker. Runtime cannot alter tables, disable triggers, truncate, or delete.
