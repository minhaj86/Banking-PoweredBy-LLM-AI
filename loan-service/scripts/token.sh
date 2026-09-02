#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
role="${1:-APPLICANT}"
case "$role" in APPLICANT|LOAN_OFFICER|OPERATIONS|COMPLIANCE_REVIEWER) ;; *) exit 2;; esac
subject="${2:-synthetic-$(printf '%s' "$role" | tr '[:upper:]' '[:lower:]')}"
secret=$(sed -n 's/^LOAN_LOCAL_JWT_SECRET=//p' .env)
b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }
now=$(date +%s)
header=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64url)
payload=$(jq -cn --arg sub "$subject" --arg role "$role" --argjson now "$now" '{iss:"loan-local",aud:"loan-service",sub:$sub,tenant:"demo",roles:[$role],iat:$now,exp:($now+3600)}' | b64url)
hex=$(printf '%s' "$secret" | openssl base64 -d -A | od -An -v -tx1 | tr -d ' \n')
signature=$(printf '%s' "$header.$payload" | openssl dgst -sha256 -mac HMAC -macopt "hexkey:$hex" -binary | b64url)
printf '%s.%s.%s\n' "$header" "$payload" "$signature"

