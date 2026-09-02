#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
base="${LOAN_URL:-http://localhost:18081}"
ledger="${LEDGER_DEMO_URL:-http://localhost:18080}"
applicant=$(bash loan-service/scripts/token.sh APPLICANT)
officer=$(bash loan-service/scripts/token.sh LOAN_OFFICER)
operations=$(bash loan-service/scripts/token.sh OPERATIONS)
key() { openssl rand -hex 16; }
api() {
  local method=$1 path=$2 token=$3 body=${4:-} k=${5:-} version=${6:-}
  local args=(-fsS -X "$method" -H "Authorization: Bearer $token")
  [ -z "$body" ] || args+=(-H 'Content-Type: application/json' --data-binary "$body")
  [ -z "$k" ] || args+=(-H "Idempotency-Key: $k")
  [ -z "$version" ] || args+=(-H "If-Match: $version")
  curl "${args[@]}" "$base/api/v1$path"
}
new_application() {
  local income=${1:-30000.00} expenses=${2:-4000.00} no_docs=${3:-false} body app id version category fixture submit
  local today start end timestamp
  today=$(date -u +%Y-%m-%d)
  # Keep date handling portable across GNU and BSD shells.
  local epoch
  epoch=$(date -u +%s)
  if date -u -d "@$((epoch-86400))" +%Y-%m-%d >/dev/null 2>&1; then
    start=$(date -u -d "@$((epoch-30*86400))" +%Y-%m-%d); end=$(date -u -d "@$((epoch-86400))" +%Y-%m-%d)
  else start=$(date -u -r "$((epoch-30*86400))" +%Y-%m-%d); end=$(date -u -r "$((epoch-86400))" +%Y-%m-%d); fi
  timestamp=$(date -u +%Y-%m-%dT%H:%M:%SZ)
  body=$(jq -cn --arg income "$income" --arg expenses "$expenses" --arg start "$start" --arg end "$end" --arg time "$timestamp" '{
    productId:"personal-sek",productVersion:1,requestedAmount:"100000.00",currency:"SEK",termMonths:60,purpose:"Synthetic demonstration",
    identity:{legalName:"Synthetic Applicant",dateOfBirth:"1990-01-01",identityIdentifier:"SYNTHETIC-ID",email:"synthetic@example.invalid",phone:"+460000000",address:"Synthetic Street 1",countryOfResidence:"SE"},
    employment:{type:"EMPLOYED",startDate:"2020-01-01",employer:"Synthetic Employer"},
    income:{grossAmount:"40000.00",netAmount:$income,frequency:"MONTHLY",currency:"SEK",periodStart:$start,periodEnd:$end,variable:false,otherSources:[]},
    expenses:{housing:"8000.00",otherNonDebt:$expenses,householdSize:1,currency:"SEK",excludesDebtRepayments:true},debts:[],
    kyc:{questionnaireVersion:"demo-1",answeredAt:$time,relationshipPurpose:"Personal loan",sourceOfFunds:"Employment",taxResidencies:["SE"],pepDeclared:false},
    privacy:{noticeVersion:"demo-1",acknowledgedAt:$time,financialEvidenceProcessingAcknowledged:true}}')
  app=$(api POST /loan-applications "$applicant" "$body" "$(key)") || return; id=$(jq -r .id <<<"$app")
  if [ "$no_docs" != true ]; then
    for category in PAYSLIP TAX_ASSESSMENT; do
      app=$(api GET "/loan-applications/$id" "$applicant") || return; version=$(jq -r .version <<<"$app")
      fixture=payslip.pdf; [ "$category" != TAX_ASSESSMENT ] || fixture=tax-assessment.pdf
      [ "$income" != 10000.00 ] || fixture=low-income.pdf
      curl -fsS -H "Authorization: Bearer $applicant" -H "Idempotency-Key: $(key)" -H "If-Match: $version" \
        -F "category=$category" -F "periodStart=$start" -F "periodEnd=$end" -F "file=@loan-service/scripts/fixtures/$fixture;type=application/pdf" "$base/api/v1/loan-applications/$id/documents" >/dev/null || return
    done
  fi
  app=$(api GET "/loan-applications/$id" "$applicant") || return; version=$(jq -r .version <<<"$app"); submit=$(key)
  api POST "/loan-applications/$id/submit" "$applicant" "" "$submit" "$version" >/dev/null || return
  api POST "/loan-applications/$id/submit" "$applicant" "" "$submit" "$version" >/dev/null || return
  printf '%s\n' "$id"
}
wait_assessment() {
  local id=$1 expected=$2 app state
  for ((n=0;n<180;n++)); do
    app=$(api GET "/loan-applications/$id" "$applicant") || return; state=$(jq -r .state <<<"$app")
    case "$state" in MANUAL_REVIEW|DOCUMENTS_REQUIRED|ASSESSMENT_FAILED)
      [ "$state" = "$expected" ] || { printf 'Expected %s, got %s\n' "$expected" "$state" >&2; return 1; }
      printf '%s\n' "$app"; return;; esac
    sleep 1
  done
  return 1
}
approve() {
  local app=$1 id version assessment body k decision again
  id=$(jq -r .id <<<"$app");version=$(jq -r .version <<<"$app")
  assessment=$(api GET "/loan-applications/$id/assessment" "$officer")
  jq '{affordability:.affordability,ai:.ai}' <<<"$assessment" >&2
  body=$(jq -c '{outcome:"APPROVE",reasonCodes:["EVIDENCE_REVIEWED"],explanation:"Explicit human review of synthetic financial evidence and authenticity limitations.",applicantExplanation:"Approved subject to agreement and later funding.",
    acknowledgedDocuments:.documentIds,acknowledgedFindings:([.evidenceLimitations[],.affordability.blockers[],.affordability.findings[],.ai.advisory.findings[].code,.ai.advisory.inconsistencies[].code,.ai.advisory.limitations[]]|unique),
    financialFactsConfirmed:true,declaredExpensesAndDebtsReviewed:true,authenticityReviewed:true}' <<<"$assessment")
  k=$(key);decision=$(api POST "/loan-applications/$id/decisions" "$officer" "$body" "$k" "$version")
  again=$(api POST "/loan-applications/$id/decisions" "$officer" "$body" "$k" "$version")
  [ "$(jq -r .loanId <<<"$decision")" = "$(jq -r .loanId <<<"$again")" ]
  jq -r .loanId <<<"$decision"
}
assert_provisioned() {
  local loan=$1 view mapping lid aid account balance entries trial again
  for ((n=0;n<90;n++)); do view=$(api GET "/loans/$loan" "$applicant"); [ "$(jq -r .provisioningStatus <<<"$view")" = COMPLETE ] && break; sleep 1; done
  jq -e '.lifecycle=="AWAITING_AGREEMENT" and .agreementStatus=="NOT_ACCEPTED"' <<<"$view" >/dev/null
  mapping=$(api GET "/loans/$loan/ledger-account" "$applicant");lid=$(jq -r .ledgerId <<<"$mapping");aid=$(jq -r .accountId <<<"$mapping")
  account=$(curl -fsS "$ledger/api/v1/ledgers/$lid/accounts/$aid")
  balance=$(curl -fsS "$ledger/api/v1/ledgers/$lid/accounts/$aid/balance")
  entries=$(curl -fsS "$ledger/api/v1/ledgers/$lid/accounts/$aid/entries")
  trial=$(curl -fsS "$ledger/api/v1/ledgers/$lid/trial-balance")
  jq -e '.type=="ASSET" and .normalBalanceSide=="DEBIT"' <<<"$account" >/dev/null
  jq -e '.balance=="0.00"' <<<"$balance" >/dev/null
  jq -e '.entries|length==0' <<<"$entries" >/dev/null
  jq -e --arg id "$aid" '[.accounts[]|select(.accountId==$id)]|length==1' <<<"$trial" >/dev/null
  api POST "/loans/$loan/provisioning/retry" "$operations" '{"reasonCode":"DEMO_REPLAY","explanation":"Replay completed provision"}' demo-provisioning-replay >/dev/null
  again=$(api GET "/loans/$loan/ledger-account" "$applicant"); [ "$(jq -r .accountId <<<"$again")" = "$aid" ]
  printf 'PASS: loan %s awaits agreement; one ASSET/DEBIT account; zero balance and entries.\n' "$loan"
}
id=$(new_application);app=$(wait_assessment "$id" MANUAL_REVIEW);loan=$(approve "$app");assert_provisioned "$loan"
if [[ " $* " == *" --recovery "* ]]; then
  id=$(new_application);app=$(wait_assessment "$id" MANUAL_REVIEW)
  docker compose stop ledger
  trap 'docker compose start ledger >/dev/null' EXIT
  loan=$(approve "$app")
  view=$(api GET "/loans/$loan" "$applicant"); jq -e '.lifecycle=="PENDING_LEDGER_ACCOUNT"' <<<"$view" >/dev/null
  docker compose start ledger; trap - EXIT
  api POST "/loans/$loan/provisioning/retry" "$operations" '{"reasonCode":"LEDGER_RECOVERED","explanation":"Synthetic outage ended"}' "$(key)" >/dev/null
  assert_provisioned "$loan"
fi
if [[ " $* " == *" --failure-cases "* ]]; then
  id=$(new_application 30000.00 4000.00 true);wait_assessment "$id" DOCUMENTS_REQUIRED >/dev/null;printf 'PASS: missing-document review\n'
  id=$(new_application 10000.00);wait_assessment "$id" MANUAL_REVIEW >/dev/null
  api GET "/loan-applications/$id/assessment" "$officer" | jq -e '.ai.advisory.outcome=="AFFORDABILITY_CONCERNS"' >/dev/null
  printf 'PASS: affordability concern requires human review\n'
  api POST '/operations/demo/ai-unavailable?enabled=true' "$operations" >/dev/null
  trap "api POST '/operations/demo/ai-unavailable?enabled=false' \"$operations\" >/dev/null" EXIT
  id=$(new_application);wait_assessment "$id" ASSESSMENT_FAILED >/dev/null
  api POST '/operations/demo/ai-unavailable?enabled=false' "$operations" >/dev/null;trap - EXIT
  printf 'PASS: synthetic AI outage produces technical failure, no rejection\n'
fi

