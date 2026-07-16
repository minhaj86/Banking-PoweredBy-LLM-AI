#!/usr/bin/env bash
set -euo pipefail
# Requires curl and jq. Each run creates an independent demo ledger.
base="${1:-http://localhost:8080}"
base="${base%/}"
command -v jq >/dev/null || { echo "Install jq to run this demo." >&2; exit 1; }
request() {
  local method="$1" path="$2" body="${3:-}" key="${4:-}"
  local args=(--fail-with-body --silent --show-error -X "$method" "$base/api/v1$path")
  if [[ -n "$body" ]]; then args+=(-H 'Content-Type: application/json' --data "$body"); fi
  if [[ -n "$key" ]]; then args+=(-H "Idempotency-Key: $key"); fi
  curl "${args[@]}"
}
create_account() {
  request POST "/ledgers/$ledger/accounts" "$(jq -nc --arg code "$1" --arg name "$2" --arg type "$3" \
    '{code:$code,name:$name,type:$type,enforceNonNegativeBalance:true}')" | jq -er .id
}
posting() {
  jq -nc --arg debit "$1" --arg credit "$2" --arg amount "$3" --arg description "$4" \
    '{description:$description,entries:[{accountId:$debit,side:"DEBIT",amount:$amount},{accountId:$credit,side:"CREDIT",amount:$amount}]}'
}
assert_balance() {
  local result actual
  result="$(request GET "/ledgers/$ledger/accounts/$1/balance")"
  actual="$(jq -er .balance <<< "$result")"
  [[ "$actual" == "$2" ]] || { echo "Expected $2, got $actual" >&2; exit 1; }
  echo "$3: $actual SEK ($(jq -r .normalBalanceSide <<< "$result")-normal)"
}

ledger="$(request POST /ledgers "$(jq -nc --arg code "DEMO-$(date +%s)-$RANDOM-$RANDOM" \
  '{code:$code,name:"Demo bank SEK ledger",currency:"SEK"}')" | jq -er .id)"
cash="$(create_account CASH 'Bank Cash' ASSET)"
a="$(create_account CUSTOMER-A 'Customer A Deposits' LIABILITY)"
b="$(create_account CUSTOMER-B 'Customer B Deposits' LIABILITY)"
revenue="$(create_account FEE-REVENUE 'Fee Revenue' REVENUE)"
deposit_body="$(posting "$cash" "$a" 1000.00 'Customer deposits 1000 SEK')"
deposit="$(request POST "/ledgers/$ledger/transactions" "$deposit_body" demo-deposit)"
replay="$(request POST "/ledgers/$ledger/transactions" "$deposit_body" demo-deposit)"
[[ "$(jq -cS . <<< "$deposit")" == "$(jq -cS . <<< "$replay")" ]]
request POST "/ledgers/$ledger/transactions" "$(posting "$a" "$b" 200.00 'Customer A transfers to Customer B')" demo-transfer >/dev/null
fee="$(request POST "/ledgers/$ledger/transactions" "$(posting "$a" "$revenue" 10.00 'Charge service fee')" demo-fee | jq -er .id)"
echo "Ledger $ledger - before reversal"
assert_balance "$cash" 1000.00 'Bank Cash'
assert_balance "$a" 790.00 'Customer A Deposits'
assert_balance "$b" 200.00 'Customer B Deposits'
assert_balance "$revenue" 10.00 'Fee Revenue'
reversal="$(request POST "/ledgers/$ledger/transactions/$fee/reversals" '{"reason":"Fee charged in error"}' demo-fee-reversal)"
[[ "$(jq -r .reversalOfTransactionId <<< "$reversal")" == "$fee" ]]
echo 'After fee reversal'
assert_balance "$a" 800.00 'Customer A Deposits'
assert_balance "$revenue" 0.00 'Fee Revenue'
history="$(request GET "/ledgers/$ledger/accounts/$revenue/entries?size=100")"
[[ "$(jq '.entries | length' <<< "$history")" == "2" ]]
jq .entries <<< "$history"
trial="$(request GET "/ledgers/$ledger/trial-balance")"
jq -e '.totalDebits == .totalCredits and .closingDebits == .closingCredits' <<< "$trial" >/dev/null
echo "Demo passed. $base/api/v1/ledgers/$ledger/trial-balance"
