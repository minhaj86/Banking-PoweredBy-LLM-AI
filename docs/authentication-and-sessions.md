# Placeholder Bank - Authentication and shared sessions

```mermaid
sequenceDiagram
  participant U as Browser
  participant B as BFF cluster
  participant R as Redis
  participant K as Keycloak
  participant L as Loan API
  U->>B: Intentional login / safe return path
  B->>R: Encrypted authorization request, state, nonce, PKCE verifier
  B-->>U: Authorization redirect / S256 challenge
  U->>K: Password and, for staff, native OTP
  K-->>U: Authorization code and state
  U->>B: Exact registered callback
  B->>K: Code + confidential client authentication + verifier
  B->>B: Framework state, nonce, issuer and token validation
  B->>R: Encrypted shared session, tokens and OIDC session index
  B-->>U: Opaque HttpOnly session cookie
  U->>B: API request / CSRF header for mutation
  B->>R: Load session; lock and rotate refresh token if needed
  B->>L: Valid user access token
  L->>L: Tenant, ownership, assurance, roles and business gates
```

Spring Security implements authorization-code OIDC with explicit PKCE S256 for the confidential BFF. Passwords are entered only on Keycloak pages. State, nonce, callbacks and token exchange use the framework. Registration uses Keycloak's registration endpoint with the same protected code flow. Direct-access and implicit grants are disabled on portal and machine clients.

`LOAN_SESSION` is HttpOnly, path `/`, SameSite=Lax and Secure by default. Only the local Compose overlay disables Secure for loopback HTTP. Sessions have a 30-minute idle timeout and an eight-hour maximum checked on authenticated requests. Login changes the session ID. Redis session attributes use authenticated AES-256-GCM encryption with an external key. The shared authorized-client store and OIDC provider-session registry are also encrypted and expire after eight hours. Redis uses authentication and no eviction. Losing Redis fails authentication closed and can require login again.

The browser receives no OAuth tokens. `/bff/me` returns the stable identity key, tenant, display name, verified-email status and effective capabilities. The local instance identifier supports cluster testing. CSRF tokens come from `/bff/csrf`; they are session security tokens, not OAuth credentials. All cookie mutations, upload and logout require CSRF. The only exemption is the framework-validated signed OIDC backchannel logout callback. The central browser client renews CSRF state on session changes and 403s, and never automatically replays financial or decision mutations.

Refresh uses a shared Redis lock, rereads the authorized client after acquiring the lock, and persists rotated tokens centrally. The lock lease is 30 seconds, lock acquisition waits at most ten seconds, and token HTTP calls have three-second connection and ten-second read bounds. A single-use refresh-token policy is configured in Keycloak. Atomic Redis revocation markers prevent an in-flight refresh from restoring a logged-out client. Client records are bound to the authenticated subject, which must also match the validated access token. Concurrent requests in the integration test use two independent BFF objects and a barrier with real Redis; browser tests also exercise two running replicas. A separate latch-based test races logout against refresh, and maximum-lifetime expiry removes token material.

## Assurance and authorization freshness

Keycloak's native conditional Level-of-Authentication flow maps `loan-basic` to LoA 1 (password) and `loan-staff` to LoA 2 (password plus OTP). LoA 1 is reusable for eight hours. LoA 2 has max-age zero and is requested afresh on staff login. Staff are provisioned with the OTP enrollment required action. The ACR protocol mapper derives the issued claim from the executed flow. There is no hardcoded assurance claim. Both BFF and loan-service check the **access-token** `acr` for privileged access. An existing password-only staff session has no effective staff capabilities until it completes step-up.

Access tokens last 60 seconds. BFF refresh begins 15 seconds before expiry and capabilities come from the validated current access token, not the original ID-token roles. BFF and loan-service use five seconds of timestamp skew. Role changes are not instantaneous; conservatively allow up to **65 seconds** for a still-valid token. The browser refreshes `/bff/me` every 45 seconds while visible; each API request still validates on the server.

Keycloak backchannel logout is deliberately principal-wide (`backchannel.logout.session.required=false`): a signed subject logout invalidates **all portal browser sessions for that user**, across replicas. The shared registry also supports SID-scoped callbacks from providers that send them. This configuration matters when the provider consolidates a user logout into one client callback; a SID-only callback left another browser login active in the two-context regression test. The additive bootstrap migration preserves users and authenticators while setting the principal-wide policy. Spring validates the signature, issuer, audience and logout-token claims before registry removal; forged callbacks are rejected. A failed/unreachable callback cannot guarantee immediate revocation; token/refresh expiry and maximum lifetime remain the bounds.

Local logout invalidates the server session and client store, then uses Spring's OIDC logout success integration with the `client_id` variant. This keeps ID tokens out of URLs. Keycloak may show its logout confirmation page; complete it to end the provider SSO session. UI caches and sensitive in-memory forms clear on logout, invalidation, identity/tenant changes and changed effective permissions.

For another provider, discovery, private endpoint resolution, role/tenant mappings, assurance semantics and backchannel behavior require review. Provider migration is not merely changing an issuer string. Production needs HTTPS, a canonical public issuer, exact callbacks, Secure cookies, private encrypted Redis connectivity and managed key rotation.
