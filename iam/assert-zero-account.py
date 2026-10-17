"""Read-only real-ledger E2E assertion, executed outside the browser with a machine identity."""
import json
import os
import sys
import urllib.parse
import urllib.request
import urllib.error
import uuid

loan, ledger, account = sys.argv[1:]
token_request=urllib.request.Request("http://keycloak:8080/auth/realms/loans/protocol/openid-connect/token",urllib.parse.urlencode({"grant_type":"client_credentials","client_id":"loan-ledger","client_secret":os.environ["LOAN_LEDGER_CLIENT_SECRET"]}).encode())
with urllib.request.urlopen(token_request,timeout=15) as response:
    token=json.load(response)["access_token"]
def get(path):
    with urllib.request.urlopen(urllib.request.Request("http://ledger:8080/api/v1/ledgers/"+ledger+path,headers={"Authorization":"Bearer "+token}),timeout=15) as response:
        return json.load(response)
balance=get("/accounts/"+account+"/balance")
assert balance["balance"]==balance["totalDebits"]==balance["totalCredits"]=="0.00", "Approval must not fund or post entries"
by_reference=get("/accounts/by-external-reference?"+urllib.parse.urlencode({"externalReference":"loan:"+loan+":principal"}))
assert by_reference["id"]==account and by_reference["type"]=="ASSET" and by_reference["normalBalanceSide"]=="DEBIT"
history=get("/accounts/"+account+"/entries")
assert history["entries"]==[] and not history["hasNext"], "Provisioning must not create any journal line"
attempt=urllib.request.Request("http://ledger:8080/api/v1/ledgers/"+ledger+"/transactions",b"{}",{"Authorization":"Bearer "+token,"Content-Type":"application/json","Idempotency-Key":str(uuid.uuid4())})
try:
    urllib.request.urlopen(attempt,timeout=15)
    raise AssertionError("Loan machine identity must not post journals")
except urllib.error.HTTPError as denied:
    assert denied.code==403,"Journal access must be rejected at authorization"
print("Verified one stable ASSET/DEBIT account, zero balance, zero debits/credits and no journal entries.")
