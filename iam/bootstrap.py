"""Local IAM administrator bootstrap. Secrets come from the environment, never the BFF.

Creates the realm once; repeated runs preserve users, passwords and enrolled authenticators.
There is no implicit ${...} substitution in a Keycloak import: this code builds the JSON.
"""
import json
import os
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = os.getenv("KEYCLOAK_INTERNAL_URL", "http://keycloak:8080/auth")
ORIGIN = os.getenv("BFF_ORIGIN", "http://localhost:18082")
HUMAN_ROLES = ["APPLICANT", "LOAN_OFFICER", "COMPLIANCE_REVIEWER", "OPERATIONS", "PLATFORM_ADMIN"]
MACHINE_ROLES = ["SERVICE", "ACCOUNT_PROVISIONER", "LEDGER_READER", "LEDGER_ADMIN"]


def request(path, method="GET", body=None, token=None, form=False):
    data = None if body is None else (urllib.parse.urlencode(body).encode() if form else json.dumps(body).encode())
    headers = {"Content-Type": "application/x-www-form-urlencoded" if form else "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    with urllib.request.urlopen(urllib.request.Request(BASE + path, data, headers, method=method), timeout=15) as result:
        raw = result.read()
        return json.loads(raw) if raw else None


def mapper(name, provider, config):
    return {"name": name, "protocol": "openid-connect", "protocolMapper": provider, "config": config}


def api_mappers(audience, tenant=False):
    result = [
        mapper("API audience", "oidc-audience-mapper", {"included.custom.audience": audience, "access.token.claim": "true", "id.token.claim": "false"}),
        mapper("Application roles", "oidc-usermodel-realm-role-mapper", {"claim.name": "roles", "jsonType.label": "String", "multivalued": "true", "access.token.claim": "true", "id.token.claim": "false", "userinfo.token.claim": "false"}),
    ]
    if tenant:
        result.append(mapper("Trusted membership", "oidc-hardcoded-claim-mapper", {"claim.name": "tenant", "claim.value": "loan-demo", "jsonType.label": "String", "access.token.claim": "true", "id.token.claim": "true", "userinfo.token.claim": "false"}))
    return result


def execution(authenticator=None, flow=None, requirement="REQUIRED", priority=10, config=None):
    item = {"requirement": requirement, "priority": priority, "authenticatorFlow": bool(flow), "userSetupAllowed": False}
    item.update({"flowAlias": flow} if flow else {"authenticator": authenticator})
    if config:
        item["authenticatorConfig"] = config
    return item


def flow(alias, executions, top=False):
    return {"alias": alias, "providerId": "basic-flow", "topLevel": top, "builtIn": False, "authenticationExecutions": executions}


APP_NAME = "Placeholder Bank"


def realm():
    bff = {"clientId": "loan-bff", "name": APP_NAME + " portal", "enabled": True, "protocol": "openid-connect", "publicClient": False,
           "secret": os.environ["BFF_CLIENT_SECRET"], "standardFlowEnabled": True, "implicitFlowEnabled": False, "directAccessGrantsEnabled": False,
           "serviceAccountsEnabled": False, "fullScopeAllowed": False, "redirectUris": [ORIGIN + "/login/oauth2/code/keycloak"], "webOrigins": [],
           "attributes": {"pkce.code.challenge.method": "S256", "post.logout.redirect.uris": ORIGIN + "/", "backchannel.logout.url": "http://bff-a:8082/logout/connect/back-channel/keycloak", "backchannel.logout.session.required": "false", "acr.loa.map": json.dumps({"loan-basic": 1, "loan-staff": 2}), "default.acr.values": "loan-basic"},
           "defaultClientScopes": ["basic", "profile", "email", "acr"], "optionalClientScopes": [], "protocolMappers": api_mappers("loan-service", True)}
    clients = [bff]
    for client_id, secret_name in [("loan-ledger", "LOAN_LEDGER_CLIENT_SECRET"), ("ledger-bootstrap", "LEDGER_ADMIN_CLIENT_SECRET")]:
        clients.append({"clientId": client_id, "enabled": True, "protocol": "openid-connect", "publicClient": False, "secret": os.environ[secret_name],
                        "standardFlowEnabled": False, "implicitFlowEnabled": False, "directAccessGrantsEnabled": False, "serviceAccountsEnabled": True,
                        "fullScopeAllowed": False, "defaultClientScopes": ["basic"], "optionalClientScopes": [], "protocolMappers": api_mappers("ledger-core")})
    groups = [{"name": name.lower().replace("_", "-"), "realmRoles": [name]} for name in HUMAN_ROLES[1:]]
    users = []
    for username, role, secret in [("applicant", "APPLICANT", "IAM_APPLICANT_PASSWORD"), ("officer", "LOAN_OFFICER", "IAM_OFFICER_PASSWORD"), ("compliance", "COMPLIANCE_REVIEWER", "IAM_COMPLIANCE_PASSWORD"), ("operations", "OPERATIONS", "IAM_OPERATIONS_PASSWORD"), ("configuration", "PLATFORM_ADMIN", "IAM_CONFIGURATION_PASSWORD")]:
        users.append({"username": username, "enabled": True, "emailVerified": True, "email": username + "@example.test", "firstName": "Synthetic", "lastName": username.title(),
                      "realmRoles": ["APPLICANT"], "groups": [] if role == "APPLICANT" else ["/" + role.lower().replace("_", "-")],
                      "requiredActions": [] if role == "APPLICANT" else ["CONFIGURE_TOTP"], "credentials": [{"type": "password", "value": os.environ[secret], "temporary": False}]})
    users += [{"username": "service-account-loan-ledger", "enabled": True, "serviceAccountClientId": "loan-ledger", "realmRoles": ["SERVICE", "ACCOUNT_PROVISIONER", "LEDGER_READER"]},
              {"username": "service-account-ledger-bootstrap", "enabled": True, "serviceAccountClientId": "ledger-bootstrap", "realmRoles": ["SERVICE", "LEDGER_ADMIN"]}]
    return {"realm": "loans", "displayName": APP_NAME + " · Loan portal", "enabled": True, "sslRequired": "none", "registrationAllowed": True, "registrationEmailAsUsername": False,
            "loginWithEmailAllowed": True, "duplicateEmailsAllowed": False, "verifyEmail": True, "resetPasswordAllowed": True, "rememberMe": False,
            "editUsernameAllowed": False, "bruteForceProtected": True, "failureFactor": 5, "passwordPolicy": "length(12) and digits(1) and lowerCase(1) and upperCase(1)",
            "accessTokenLifespan": 60, "ssoSessionIdleTimeout": 1800, "ssoSessionMaxLifespan": 28800, "revokeRefreshToken": True, "refreshTokenMaxReuse": 0,
            "eventsEnabled": True, "eventsExpiration": 604800, "adminEventsEnabled": True, "adminEventsDetailsEnabled": False,
            "smtpServer": {"host": "mailpit", "port": "1025", "from": "portal@example.test", "fromDisplayName": APP_NAME + " local demo", "ssl": "false", "starttls": "false", "auth": "false"},
            "roles": {"realm": [{"name": r} for r in HUMAN_ROLES + MACHINE_ROLES]}, "defaultRoles": ["APPLICANT"], "groups": groups, "clients": clients, "users": users,
            "scopeMappings": [{"client": "loan-bff", "roles": HUMAN_ROLES}, {"client": "loan-ledger", "roles": ["SERVICE", "ACCOUNT_PROVISIONER", "LEDGER_READER"]}, {"client": "ledger-bootstrap", "roles": ["SERVICE", "LEDGER_ADMIN"]}],
            "browserFlow": "loan-browser", "authenticationFlows": [
                flow("loan-browser", [execution("auth-cookie", requirement="ALTERNATIVE"), execution(flow="loan-interactive", requirement="ALTERNATIVE", priority=20)], True),
                flow("loan-interactive", [execution(flow="loan-level-one", requirement="CONDITIONAL"), execution(flow="loan-level-two", requirement="CONDITIONAL", priority=20)]),
                flow("loan-level-one", [execution("conditional-level-of-authentication", config="loan-password-level"), execution("auth-username-password-form", priority=20)]),
                flow("loan-level-two", [execution("conditional-level-of-authentication", config="loan-otp-level"), execution("auth-otp-form", priority=20)])],
            "authenticatorConfig": [{"alias": "loan-password-level", "config": {"loa-condition-level": "1", "loa-max-age": "28800"}}, {"alias": "loan-otp-level", "config": {"loa-condition-level": "2", "loa-max-age": "0"}}],
            "otpPolicyType": "totp", "otpPolicyAlgorithm": "HmacSHA1", "otpPolicyDigits": 6, "otpPolicyPeriod": 30, "otpPolicyLookAheadWindow": 1}


def main():
    token = None
    for attempt in range(60):
        try:
            token = request("/realms/master/protocol/openid-connect/token", "POST", {"client_id": "admin-cli", "grant_type": "password", "username": "local-admin", "password": os.environ["IAM_ADMIN_PASSWORD"]}, form=True)["access_token"]
            break
        except (urllib.error.URLError, KeyError):
            time.sleep(2)
    if not token:
        raise SystemExit("IAM bootstrap could not authenticate")
    try:
        request("/admin/realms/loans", token=token)
        print("Existing loans realm preserved.")
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        try:
            request("/admin/realms", "POST", realm(), token)
        except urllib.error.HTTPError as creation:
            # Safe server error only, no realm JSON/secrets.
            print("Realm import failed:", creation.code, creation.read().decode()[:500])
            raise SystemExit(1) from None
        print("Created loans realm, confidential PKCE client, isolated service clients and native password/OTP step-up flow.")
    # Keycloak 26.4 supplies the access-token subject through its standard basic scope.
    # Additive configuration migration also repairs an existing local realm without replacing users or keys.
    current=request("/admin/realms/loans",token=token)
    smtp=dict(current.get("smtpServer", {}))
    smtp["fromDisplayName"]=APP_NAME + " local demo"
    request("/admin/realms/loans","PUT",{"displayName":APP_NAME + " · Loan portal", "smtpServer":smtp},token)
    scopes=request("/admin/realms/loans/client-scopes",token=token)
    basic=next(scope["id"] for scope in scopes if scope["name"]=="basic")
    for client in request("/admin/realms/loans/clients",token=token):
        if client["clientId"] in {"loan-bff","loan-ledger","ledger-bootstrap"}:
            request("/admin/realms/loans/clients/"+client["id"]+"/default-client-scopes/"+basic,"PUT",token=token)
        if client["clientId"]=="loan-bff":
            client["name"]=APP_NAME + " portal"
            # Principal-wide backchannel logout invalidates every browser login for the user.
            # Keep all other existing client settings and every user/authenticator intact.
            client["attributes"]["backchannel.logout.session.required"]="false"
            request("/admin/realms/loans/clients/"+client["id"],"PUT",client,token)
    print("Standard basic scope installed for immutable access-token subject claims.")


if __name__ == "__main__":
    main()
