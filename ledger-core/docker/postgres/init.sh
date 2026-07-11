#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<'SQL'
\getenv migration_password LEDGER_MIGRATION_PASSWORD
\getenv app_password LEDGER_APP_PASSWORD
CREATE ROLE ledger_migrator LOGIN PASSWORD :'migration_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE ROLE ledger_app LOGIN PASSWORD :'app_password' NOSUPERUSER NOCREATEDB NOCREATEROLE;
CREATE SCHEMA ledger AUTHORIZATION ledger_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE ledger TO ledger_migrator, ledger_app;
SQL
