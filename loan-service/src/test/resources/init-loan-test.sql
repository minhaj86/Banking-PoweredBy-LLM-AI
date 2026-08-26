CREATE ROLE loan_migrator LOGIN PASSWORD 'test-migration-only';
CREATE ROLE loan_app LOGIN PASSWORD 'test-app-only';
CREATE SCHEMA origination AUTHORIZATION loan_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;

