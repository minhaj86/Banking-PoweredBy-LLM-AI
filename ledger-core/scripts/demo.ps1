param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-Ledger([string]$Method, [string]$Path, $Body = $null, [string]$Key = '') {
    $parameters = @{ Uri = "$BaseUrl/api/v1$Path"; Method = $Method }
    if ($null -ne $Body) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = $Body | ConvertTo-Json -Depth 10 -Compress
    }
    if ($Key) { $parameters.Headers = @{ 'Idempotency-Key' = $Key } }
    Invoke-RestMethod @parameters
}
function New-Entry([string]$AccountId, [string]$Side, [string]$Amount) {
    @{ accountId = $AccountId; side = $Side; amount = $Amount }
}
function Assert-Balance([string]$LedgerId, $Account, [string]$Expected) {
    $result = Invoke-Ledger GET "/ledgers/$LedgerId/accounts/$($Account.id)/balance"
    if ($result.balance -ne $Expected) { throw "$($Account.name): expected $Expected, got $($result.balance)" }
    Write-Host "$($Account.name): $($result.balance) $($result.currency) ($($result.normalBalanceSide)-normal)"
}

$ledger = Invoke-Ledger POST '/ledgers' @{
    code = "DEMO-$([guid]::NewGuid().ToString('N'))"; name = 'Demo bank SEK ledger'; currency = 'SEK'
}
$ledgerId = $ledger.id
$cash = Invoke-Ledger POST "/ledgers/$ledgerId/accounts" @{
    code='CASH'; name='Bank Cash'; type='ASSET'; enforceNonNegativeBalance=$true
}
$a = Invoke-Ledger POST "/ledgers/$ledgerId/accounts" @{
    code='CUSTOMER-A'; name='Customer A Deposits'; type='LIABILITY'; enforceNonNegativeBalance=$true
}
$b = Invoke-Ledger POST "/ledgers/$ledgerId/accounts" @{
    code='CUSTOMER-B'; name='Customer B Deposits'; type='LIABILITY'; enforceNonNegativeBalance=$true
}
$revenue = Invoke-Ledger POST "/ledgers/$ledgerId/accounts" @{
    code='FEE-REVENUE'; name='Fee Revenue'; type='REVENUE'
}
$depositBody = @{
    externalReference='customer-deposit-001'; description='Customer deposits 1000 SEK'
    entries=@((New-Entry $cash.id DEBIT '1000.00'), (New-Entry $a.id CREDIT '1000.00'))
}
$deposit = Invoke-Ledger POST "/ledgers/$ledgerId/transactions" $depositBody 'demo-deposit'
$replay = Invoke-Ledger POST "/ledgers/$ledgerId/transactions" $depositBody 'demo-deposit'
if ($deposit.id -ne $replay.id -or $deposit.recordedAt -ne $replay.recordedAt) { throw 'Idempotent replay differed' }
$transfer = Invoke-Ledger POST "/ledgers/$ledgerId/transactions" @{
    description='Customer A transfers 200 SEK to Customer B'
    entries=@((New-Entry $a.id DEBIT '200.00'), (New-Entry $b.id CREDIT '200.00'))
} 'demo-transfer'
$fee = Invoke-Ledger POST "/ledgers/$ledgerId/transactions" @{
    description='Charge 10 SEK service fee'
    entries=@((New-Entry $a.id DEBIT '10.00'), (New-Entry $revenue.id CREDIT '10.00'))
} 'demo-fee'

Write-Host "Ledger $ledgerId - before reversal"
Assert-Balance $ledgerId $cash '1000.00'
Assert-Balance $ledgerId $a '790.00'
Assert-Balance $ledgerId $b '200.00'
Assert-Balance $ledgerId $revenue '10.00'
$reversal = Invoke-Ledger POST "/ledgers/$ledgerId/transactions/$($fee.id)/reversals" @{ reason='Fee charged in error' } 'demo-fee-reversal'
if ($reversal.reversalOfTransactionId -ne $fee.id) { throw 'Reversal reference missing' }
Write-Host 'After fee reversal'
Assert-Balance $ledgerId $a '800.00'
Assert-Balance $ledgerId $revenue '0.00'
$history = Invoke-Ledger GET "/ledgers/$ledgerId/accounts/$($revenue.id)/entries?size=100"
if ($history.entries.Count -ne 2) { throw 'Original and reversal must both remain visible' }
$history.entries | Select-Object transactionId, lineNumber, side, amount, reversalOfTransactionId | Format-Table
$trial = Invoke-Ledger GET "/ledgers/$ledgerId/trial-balance"
if ($trial.totalDebits -ne $trial.totalCredits -or $trial.closingDebits -ne $trial.closingCredits) { throw 'Trial balance mismatch' }
Write-Host "Demo passed. Trial totals: debits=$($trial.totalDebits), credits=$($trial.totalCredits)."
Write-Host "$BaseUrl/api/v1/ledgers/$ledgerId/trial-balance"
