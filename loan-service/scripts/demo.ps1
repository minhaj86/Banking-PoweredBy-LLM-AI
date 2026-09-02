param([string]$BaseUrl='http://localhost:18081',[string]$LedgerUrl='http://localhost:18080',[switch]$Recovery,[switch]$FailureCases)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$applicant=& "$PSScriptRoot/token.ps1" -Role APPLICANT
$officer=& "$PSScriptRoot/token.ps1" -Role LOAN_OFFICER
$operations=& "$PSScriptRoot/token.ps1" -Role OPERATIONS
function Call-Loan([string]$Method,[string]$Path,[string]$Token,$Body=$null,[string]$Key='', $Version=$null) {
    $headers=@{Authorization="Bearer $Token"}
    if($Key) {$headers['Idempotency-Key']=$Key}
    if($null -ne $Version) {$headers['If-Match']=[string]$Version}
    $args=@{Method=$Method;Uri="$BaseUrl/api/v1$Path";Headers=$headers}
    if($null -ne $Body){$args.ContentType='application/json';$args.Body=($Body|ConvertTo-Json -Depth 30 -Compress)}
    Invoke-RestMethod @args
}
function New-Key { [Guid]::NewGuid().ToString() }
function New-Application([string]$Income='30000.00',[string]$Expenses='4000.00',[switch]$NoDocuments) {
    $today=[DateTime]::UtcNow.Date
    $body=@{
        productId='personal-sek';productVersion=1;requestedAmount='100000.00';currency='SEK';termMonths=60;purpose='Synthetic demonstration'
        identity=@{legalName='Synthetic Applicant';dateOfBirth='1990-01-01';identityIdentifier='SYNTHETIC-ID';email='synthetic@example.invalid';phone='+460000000';address='Synthetic Street 1';countryOfResidence='SE'}
        employment=@{type='EMPLOYED';startDate='2020-01-01';employer='Synthetic Employer'}
        income=@{grossAmount='40000.00';netAmount=$Income;frequency='MONTHLY';currency='SEK';periodStart=$today.AddMonths(-1).ToString('yyyy-MM-01');periodEnd=$today.AddDays(-1).ToString('yyyy-MM-dd');variable=$false;otherSources=@()}
        expenses=@{housing='8000.00';otherNonDebt=$Expenses;householdSize=1;currency='SEK';excludesDebtRepayments=$true};debts=@()
        kyc=@{questionnaireVersion='demo-1';answeredAt=[DateTime]::UtcNow.AddSeconds(-5).ToString('o');relationshipPurpose='Personal loan';sourceOfFunds='Employment';taxResidencies=@('SE');pepDeclared=$false}
        privacy=@{noticeVersion='demo-1';acknowledgedAt=[DateTime]::UtcNow.AddSeconds(-5).ToString('o');financialEvidenceProcessingAcknowledged=$true}
    }
    $app=Call-Loan POST '/loan-applications' $applicant $body (New-Key)
    if(-not $NoDocuments) {
        foreach($category in @('PAYSLIP','TAX_ASSESSMENT')) {
            $app=Call-Loan GET "/loan-applications/$($app.id)" $applicant
            $fixture=if($Income -eq '10000.00'){'low-income.pdf'}elseif($category -eq 'PAYSLIP'){'payslip.pdf'}else{'tax-assessment.pdf'}
            $file=Join-Path $PSScriptRoot "fixtures/$fixture"
            $response=& curl.exe --fail-with-body --silent --show-error -H "Authorization: Bearer $applicant" -H "Idempotency-Key: $(New-Key)" -H "If-Match: $($app.version)" -F "category=$category" -F "periodStart=$($today.AddMonths(-1).ToString('yyyy-MM-01'))" -F "periodEnd=$($today.AddDays(-1).ToString('yyyy-MM-dd'))" -F "file=@$file;type=application/pdf" "$BaseUrl/api/v1/loan-applications/$($app.id)/documents"
            if($LASTEXITCODE -ne 0){throw "Document upload failed: $response"}
        }
    }
    $app=Call-Loan GET "/loan-applications/$($app.id)" $applicant
    $submitKey=New-Key
    $submitted=Call-Loan POST "/loan-applications/$($app.id)/submit" $applicant $null $submitKey $app.version
    $null=Call-Loan POST "/loan-applications/$($app.id)/submit" $applicant $null $submitKey $app.version
    return $submitted
}
function Wait-Assessment($Id,[string]$Expected='') {
    for($i=0;$i -lt 180;$i++){
        $app=Call-Loan GET "/loan-applications/$Id" $applicant
        if($app.state -in @('MANUAL_REVIEW','DOCUMENTS_REQUIRED','ASSESSMENT_FAILED')) {
            if($Expected -and $app.state -ne $Expected){throw "Expected $Expected but was $($app.state)"}
            return $app
        }
        Start-Sleep -Seconds 1
    }
    throw 'Assessment did not finish within 180 seconds'
}
function Approve($App) {
    $assessment=Call-Loan GET "/loan-applications/$($App.id)/assessment" $officer
    Write-Host ($assessment.affordability|ConvertTo-Json -Depth 10)
    Write-Host ($assessment.ai|ConvertTo-Json -Depth 10)
    $findings=@($assessment.evidenceLimitations)+@($assessment.affordability.blockers)+@($assessment.affordability.findings)+@($assessment.ai.advisory.findings|ForEach-Object{$_.code})+@($assessment.ai.advisory.inconsistencies|ForEach-Object{$_.code})+@($assessment.ai.advisory.limitations)
    $request=@{outcome='APPROVE';reasonCodes=@('EVIDENCE_REVIEWED');explanation='Explicit loan-officer review of synthetic evidence. Facts, authenticity limitations, expenses and debts reviewed.';applicantExplanation='Approved subject to agreement and a future funding process.';acknowledgedDocuments=@($assessment.documentIds);acknowledgedFindings=@($findings|Select-Object -Unique);financialFactsConfirmed=$true;declaredExpensesAndDebtsReviewed=$true;authenticityReviewed=$true}
    $key=New-Key
    $decision=Call-Loan POST "/loan-applications/$($App.id)/decisions" $officer $request $key $App.version
    $again=Call-Loan POST "/loan-applications/$($App.id)/decisions" $officer $request $key $App.version
    if($decision.loanId -ne $again.loanId){throw 'Duplicate approval loan'}
    return $decision.loanId
}
function Assert-Provisioned($LoanId) {
    for($i=0;$i -lt 90;$i++){
        $loan=Call-Loan GET "/loans/$LoanId" $applicant
        if($loan.provisioningStatus -eq 'COMPLETE'){break}
        Start-Sleep -Seconds 1
    }
    if($loan.lifecycle -ne 'AWAITING_AGREEMENT' -or $loan.agreementStatus -ne 'NOT_ACCEPTED'){throw 'Invalid loan lifecycle'}
    $mapping=Call-Loan GET "/loans/$LoanId/ledger-account" $applicant
    $account=Invoke-RestMethod "$LedgerUrl/api/v1/ledgers/$($mapping.ledgerId)/accounts/$($mapping.accountId)"
    $balance=Invoke-RestMethod "$LedgerUrl/api/v1/ledgers/$($mapping.ledgerId)/accounts/$($mapping.accountId)/balance"
    $history=Invoke-RestMethod "$LedgerUrl/api/v1/ledgers/$($mapping.ledgerId)/accounts/$($mapping.accountId)/entries"
    $trial=Invoke-RestMethod "$LedgerUrl/api/v1/ledgers/$($mapping.ledgerId)/trial-balance"
    if($account.type -ne 'ASSET' -or $account.normalBalanceSide -ne 'DEBIT' -or $balance.balance -ne '0.00' -or @($history.entries).Count -ne 0){throw 'Accounting assertion failed'}
    if(@($trial.accounts|Where-Object{$_.accountId -eq $mapping.accountId}).Count -ne 1){throw 'Account uniqueness assertion failed'}
    $retry=@{reasonCode='DEMO_REPLAY';explanation='Demonstrate idempotent completed provisioning retry'}
    $null=Call-Loan POST "/loans/$LoanId/provisioning/retry" $operations $retry 'demo-provisioning-replay'
    $again=Call-Loan GET "/loans/$LoanId/ledger-account" $applicant
    if($again.accountId -ne $mapping.accountId){throw 'Provisioning retry changed account'}
    Write-Host "PASS: loan $LoanId awaits agreement; one ASSET/DEBIT account; zero balance and zero entries."
}
$app=Wait-Assessment (New-Application).id 'MANUAL_REVIEW'
$loan=Approve $app
Assert-Provisioned $loan
if($Recovery) {
    $second=Wait-Assessment (New-Application).id 'MANUAL_REVIEW'
    Push-Location $root
    try {
        & docker compose stop ledger
        if($LASTEXITCODE -ne 0){throw 'Could not stop demo ledger'}
        $secondLoan=Approve $second
        Start-Sleep -Seconds 3
        $pending=Call-Loan GET "/loans/$secondLoan" $applicant
        if($pending.lifecycle -notin @('PENDING_LEDGER_ACCOUNT','PROVISIONING_FAILED')){throw 'Loan did not remain approved while ledger was down'}
    } finally { & docker compose start ledger; Pop-Location }
    $null=Call-Loan POST "/loans/$secondLoan/provisioning/retry" $operations @{reasonCode='LEDGER_RECOVERED';explanation='Synthetic outage has ended'} (New-Key)
    Assert-Provisioned $secondLoan
}
if($FailureCases) {
    $missing=Wait-Assessment (New-Application -NoDocuments).id 'DOCUMENTS_REQUIRED'
    Write-Host "PASS: missing documents require more evidence: $($missing.id)"
    $concern=Wait-Assessment (New-Application -Income '10000.00').id 'MANUAL_REVIEW'
    $assessment=Call-Loan GET "/loan-applications/$($concern.id)/assessment" $officer
    if($assessment.ai.advisory.outcome -ne 'AFFORDABILITY_CONCERNS'){throw 'Missing affordability concern'}
    Write-Host "PASS: affordability concern remains for human review: $($concern.id)"
    try {
        $null=Call-Loan POST '/operations/demo/ai-unavailable?enabled=true' $operations
        $failed=Wait-Assessment (New-Application).id 'ASSESSMENT_FAILED'
        Write-Host "PASS: synthetic AI outage caused technical failure, no credit rejection: $($failed.id)"
    } finally {$null=Call-Loan POST '/operations/demo/ai-unavailable?enabled=false' $operations}
}

