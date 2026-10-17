"""Only this one-shot container receives the ledger bootstrap client credential."""
import json
import os
import urllib.parse
import urllib.request
from pathlib import Path

token_request=urllib.request.Request("http://keycloak:8080/auth/realms/loans/protocol/openid-connect/token",urllib.parse.urlencode({"grant_type":"client_credentials","client_id":"ledger-bootstrap","client_secret":os.environ["LEDGER_ADMIN_CLIENT_SECRET"]}).encode())
with urllib.request.urlopen(token_request,timeout=15) as response:
    token=json.load(response)["access_token"]
target=Path("/config/ledger.properties")
if target.exists():
    ledger=target.read_text().strip().split("=",1)[1]
    request=urllib.request.Request("http://ledger:8080/api/v1/ledgers/"+ledger,headers={"Authorization":"Bearer "+token})
    with urllib.request.urlopen(request,timeout=15) as response:
        assert json.load(response)["id"]==ledger
else:
    request=urllib.request.Request("http://ledger:8080/api/v1/ledgers",json.dumps({"code":"LOAN-DEMO-SEK","name":"Synthetic loan demo","currency":"SEK"}).encode(),{"Authorization":"Bearer "+token,"Content-Type":"application/json"})
    with urllib.request.urlopen(request,timeout=15) as response:
        ledger=json.load(response)["id"]
    target.write_text("loan.ledger.id="+ledger+"\n")
    target.chmod(0o644)
print("Lender ledger ready; existing configuration preserved.")
