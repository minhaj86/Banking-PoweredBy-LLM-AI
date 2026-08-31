#!/bin/sh
set -eu
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  -v migration_password="$LOAN_MIGRATION_PASSWORD" -v app_password="$LOAN_DB_PASSWORD" <<'SQL'
CREATE ROLE loan_migrator LOGIN PASSWORD :'migration_password';
CREATE ROLE loan_app LOGIN PASSWORD :'app_password';
CREATE SCHEMA origination AUTHORIZATION loan_migrator;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
GRANT CONNECT ON DATABASE loans TO loan_app,loan_migrator;
SQL

