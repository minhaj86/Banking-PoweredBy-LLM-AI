# Placeholder Bank - UI integration plan

The existing Maven services use Java 21 / Spring Boot 3.5.16, immutable API DTOs, PostgreSQL-backed workflow jobs, JWT ownership/tenant checks and HTTP ledger provisioning. There is no existing frontend. Existing local HMAC demos and all ledger invariants remain supported.

Add loan-web (React/TypeScript/Vite) and loan-bff (Spring Security OIDC client plus shared encrypted Redis sessions and authorised clients). The browser uses only the same-origin BFF. The BFF relays the user's access token through an explicit route/permission allowlist; it does not replace the user with a service identity or perform underwriting.

Use real Keycloak in an optional Compose overlay, with its own database, a confidential PKCE client, applicant registration, email actions via Mailpit, a configured LoA-2 OTP step-up flow for staff, and a separate least-privileged ledger machine client. Enforce verified email on submission and staff assurance in both BFF and loan-service. Identity is issuer plus subject; legacy local identities retain their existing ownership keys. Reject self-review.

Required additive backend contracts: partial encrypted drafts, current product/questionnaire metadata, scoped server-side queues/filters and counts, applicant-safe information-request/timeline views, staff audit/reconsideration views and an authorised financial-balance projection from the ledger. Business transitions and final approval stay in loan-service.

Implementation sequence: establish OIDC/PKCE/shared sessions and /bff/me; complete applicant draft/upload/submit journey; add scoped staff review/decisions; display loan/account status; exercise real Keycloak registration/email/OTP, CSRF, logout, clustered sessions/refresh and backend races; finish accessibility, responsive QA and documentation.
