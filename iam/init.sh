#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
sh loan-service/scripts/init-local.sh
umask 077
for name in IAM_DB_PASSWORD IAM_ADMIN_PASSWORD BFF_CLIENT_SECRET BFF_ENCRYPTION_KEY REDIS_PASSWORD LOAN_LEDGER_CLIENT_SECRET LEDGER_ADMIN_CLIENT_SECRET IAM_APPLICANT_PASSWORD IAM_OFFICER_PASSWORD IAM_COMPLIANCE_PASSWORD IAM_OPERATIONS_PASSWORD IAM_CONFIGURATION_PASSWORD; do
  if ! grep -q "^${name}=" .env; then printf '\n%s=%s\n' "$name" "$(openssl rand -base64 32)" >> .env; fi
done
printf '%s\n' 'IAM secrets are ready in .env. Existing keys and users are preserved.'
