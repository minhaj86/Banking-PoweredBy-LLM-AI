# Placeholder Bank - Real Keycloak local setup

Requirements: Docker Desktop/Engine with Compose, JDK 21 and Node 24 for host verification. Start Docker before verification. Keep `.env`, the ignored `.iam` directory and all volumes private. Never remove volumes to reset a test.

From the repository root (the directory containing the parent POM and Compose files), PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File iam/init.ps1
$env:COMPOSE_PARALLEL_LIMIT='1'
docker compose -f compose.yaml -f compose.iam.yaml up -d --build --wait
Start-Process http://localhost:18082
```

Unix:

```sh
sh iam/init.sh
COMPOSE_PARALLEL_LIMIT=1 docker compose -f compose.yaml -f compose.iam.yaml up -d --build --wait
```

The explicit bootstrap constructs JSON from environment secrets; it does not assume Keycloak substitutes import templates. It creates a PostgreSQL-backed realm, confidential PKCE portal client, explicit loan-service/ledger-core audiences, role scope mappings, fixed local tenant membership, groups, mail settings and native password/OTP step-up flow. Repeated runs preserve existing users, passwords, authenticators and keys. Additive client migrations retain the standard `basic` scope for immutable access-token subjects and configure signed, principal-wide backchannel logout, invalidating every portal login for the user. Do not remove the `basic` scope.

Public portal and issuer gateway: `http://localhost:18082`. Local mail: `http://localhost:18084`. Local authorized IAM administration: `http://localhost:18085/auth/admin/`; this administrative port is not an alternate issuer. All published ports bind loopback. Container-only endpoints remain private. The root Compose file without the overlay is the separate legacy backend demo.

## Synthetic identities

Generated passwords are held in `.env`, not committed or exposed to the web bundle. Usernames and corresponding password variable names:

| Username | Role / password variable |
|---|---|
| applicant | APPLICANT / IAM_APPLICANT_PASSWORD |
| officer | LOAN_OFFICER / IAM_OFFICER_PASSWORD |
| compliance | COMPLIANCE_REVIEWER / IAM_COMPLIANCE_PASSWORD |
| operations | OPERATIONS / IAM_OPERATIONS_PASSWORD |
| configuration | PLATFORM_ADMIN / IAM_CONFIGURATION_PASSWORD |
| local-admin | Authorized local IAM bootstrap administrator / IAM_ADMIN_PASSWORD |

Staff also have applicant membership for their own journeys. Open **Staff workspace**, complete Keycloak password/OTP enrollment and use an authenticator app. Playwright enrolls a genuine TOTP authenticator and retains its synthetic test secret only in ignored `.iam/otp-state.json`. Do not delete that file and expect an already enrolled test identity to work automatically; use authorized IAM authenticator recovery if necessary. The BFF receives no administrator password or IAM admin token.

If the browser suite has already enrolled a synthetic staff account, its private Base32 seed in `.iam/otp-state.json` can be imported into your local authenticator for that demo identity. Alternatively, use authorized local Keycloak administration to reset the synthetic account's authenticator and enroll your own. Keep those seeds private and separate from real accounts; the test counter file prevents the suite from reusing the same OTP within a time window.

For registration, choose **Create an account**, enter a new synthetic identity and open the verification email in Mailpit. The link completes Keycloak's required action and returns to the portal. For password reset, choose **Forgot Password?** on Keycloak, enter the synthetic account, open the reset email in Mailpit, and set a new password. No portal password form exists. Use account settings for supported profile/password/authenticator functions. Staff roles, tenant claims and verification status are not editable profile attributes.

Keycloak is pinned to 26.4.7. The flow follows its [native step-up authentication](https://www.keycloak.org/docs/26.4.7/server_admin/#_step-up-flow) semantics. Exact callback is `http://localhost:18082/login/oauth2/code/keycloak`; post-logout callback is `http://localhost:18082/`. There are no wildcard redirect URLs.

This is loopback HTTP development configuration with a local mail catcher and explicit synthetic financial providers. Production needs real mail, TLS, a production Keycloak deployment, secret management, monitoring, backups, provider approvals and independent financial verification. It does not implement BankID, electronic agreement signing or production compliance certification.
