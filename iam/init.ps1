$ErrorActionPreference='Stop'
& (Join-Path $PSScriptRoot '../loan-service/scripts/init-local.ps1')
$target=Join-Path $PSScriptRoot '../.env'
$existing=[IO.File]::ReadAllText($target)
function New-IamSecret { $bytes=New-Object byte[] 32; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose(); [Convert]::ToBase64String($bytes) }
$added=@()
foreach($name in @('IAM_DB_PASSWORD','IAM_ADMIN_PASSWORD','BFF_CLIENT_SECRET','BFF_ENCRYPTION_KEY','REDIS_PASSWORD','LOAN_LEDGER_CLIENT_SECRET','LEDGER_ADMIN_CLIENT_SECRET','IAM_APPLICANT_PASSWORD','IAM_OFFICER_PASSWORD','IAM_COMPLIANCE_PASSWORD','IAM_OPERATIONS_PASSWORD','IAM_CONFIGURATION_PASSWORD')) {
    if($existing -notmatch "(?m)^$name="){ $added+="$name=$(New-IamSecret)" }
}
if($added.Count){[IO.File]::AppendAllText($target,"`n"+($added -join "`n")+"`n",[Text.UTF8Encoding]::new($false))}
Write-Host 'IAM secrets are ready in .env. Existing keys and users are preserved.'
