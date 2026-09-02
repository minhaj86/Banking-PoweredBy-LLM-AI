$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$target=Join-Path $root '.env'
if(Test-Path -LiteralPath $target) { Write-Host 'Existing .env preserved.'; exit 0 }
function New-LocalSecret { $bytes=New-Object byte[] 32; $rng=[Security.Cryptography.RandomNumberGenerator]::Create(); $rng.GetBytes($bytes); $rng.Dispose(); [Convert]::ToBase64String($bytes) }
$lines=@('# Generated Placeholder Bank local credentials. Keep private; never commit.','COMPOSE_PROJECT_NAME=placeholder-bank','S3_ACCESS_KEY=loan-local','AI_PROVIDER=mock')
foreach($name in @('LEDGER_BOOTSTRAP_PASSWORD','LEDGER_MIGRATION_PASSWORD','LEDGER_DB_PASSWORD','LOAN_BOOTSTRAP_PASSWORD','LOAN_MIGRATION_PASSWORD','LOAN_DB_PASSWORD','LOAN_ENCRYPTION_KEY','LOAN_LOCAL_JWT_SECRET','S3_SECRET_KEY')) {
    $lines += "$name=$(New-LocalSecret)"
}
[IO.File]::WriteAllLines($target,$lines,[Text.UTF8Encoding]::new($false))
Write-Host 'Created .env with random local keys. Preserve it with the demo volumes.'

