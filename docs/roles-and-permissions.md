# Placeholder Bank - Roles and permissions

| Role | Permitted workspace | Limits |
|---|---|---|
| APPLICANT | Own applications, evidence, requests and loans | Trusted tenant + issuer/subject ownership; verified email before submit |
| LOAN_OFFICER | Tenant review queue, financial evidence, assessment, information requests and explicit decisions | Staff OTP assurance; no self-review; current revision and backend approval gates |
| COMPLIANCE_REVIEWER | Authorized evidence, verification reviews, legal hold and audit | Staff OTP assurance; separate compliance gate; no credit decision permission |
| OPERATIONS | Scoped jobs, metrics, audit and eligible retries | Staff OTP assurance; no payload editing, forced financial state or balance changes |
| PLATFORM_ADMIN | Read-only product/policy configuration | Staff OTP assurance; no automatic application/document/decision access |

Roles control navigation for usability and are independently checked in the BFF and receiving API. The API enforces every nested lookup and tenant boundary. Staff cannot assess, manually extract, verify or decide their own applications. Staff with APPLICANT membership may still use their own applicant journey.

Keycloak membership is the trusted source of tenant and human roles. The local tenant mapper is fixed to `loan-demo`; it is not a registration/profile attribute. Self-registration receives APPLICANT only. Staff groups are assigned through the authorized IAM bootstrap/administration process. The portal has no IAM role-editing or password endpoint. Email is contact information, never an ownership key. Financial KYC remains independent from authentication and email/MFA status.

`loan-ledger` is a separate confidential client-credentials principal with `SERVICE`, `ACCOUNT_PROVISIONER` and `LEDGER_READER` only, audience `ledger-core`. It can recover/provision an account by immutable reference and read its actual balance; it cannot post, reverse or alter journals. The one-shot `ledger-bootstrap` client has separate `LEDGER_ADMIN` credentials and is absent from BFF/loan runtime environments. Human loan roles do not grant ledger authority.

Legacy explicit local profiles retain their existing development behavior. The integrated IAM overlay removes backend host port bindings. Production must use authenticated profiles and network restrictions rather than relying on frontend hiding.
