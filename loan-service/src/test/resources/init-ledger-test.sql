CREATE ROLE ledger_migrator LOGIN PASSWORD 'test-migration-only';
CREATE ROLE ledger_app LOGIN PASSWORD 'test-app-only';
CREATE SCHEMA ledger AUTHORIZATION ledger_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

