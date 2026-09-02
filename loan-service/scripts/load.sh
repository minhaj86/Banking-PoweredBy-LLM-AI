#!/usr/bin/env bash
# Portable wrapper: Windows and Unix run the same PowerShell load generator.
set -euo pipefail
cd "$(dirname "$0")/../.."
# Host prerequisites are PowerShell 7, curl, jq, openssl. No Python dependency.
if ! command -v pwsh >/dev/null; then
  printf '%s\n' 'PowerShell 7 (pwsh) is required for this shared load generator. See loan-service/README.md.' >&2
  exit 2
fi
pwsh -NoProfile -File loan-service/scripts/load.ps1 "$@"

