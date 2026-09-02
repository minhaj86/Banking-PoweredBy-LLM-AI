#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/../.."
if [ -e .env ]; then printf '%s\n' 'Existing .env preserved.'; exit 0; fi
umask 077
{
  printf '%s\n' '# Generated Placeholder Bank local credentials; never commit.' 'COMPOSE_PROJECT_NAME=placeholder-bank' 'S3_ACCESS_KEY=loan-local' 'AI_PROVIDER=mock'
  for name in LEDGER_BOOTSTRAP_PASSWORD LEDGER_MIGRATION_PASSWORD LEDGER_DB_PASSWORD LOAN_BOOTSTRAP_PASSWORD LOAN_MIGRATION_PASSWORD LOAN_DB_PASSWORD LOAN_ENCRYPTION_KEY LOAN_LOCAL_JWT_SECRET S3_SECRET_KEY; do
    printf '%s=%s\n' "$name" "$(openssl rand -base64 32)"
  done
} > .env
printf '%s\n' 'Created .env; preserve it with the demo volumes.'

