param([ValidateSet('APPLICANT','LOAN_OFFICER','OPERATIONS','COMPLIANCE_REVIEWER')][string]$Role='APPLICANT',[string]$Subject,[string]$Tenant='demo')
$ErrorActionPreference='Stop'
if(-not $Subject) { $Subject='synthetic-'+$Role.ToLowerInvariant() }
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$settings=@{}
Get-Content (Join-Path $root '.env') | Where-Object { $_ -match '^[A-Z0-9_]+=' } | ForEach-Object { $parts=$_ -split '=',2; $settings[$parts[0]]=$parts[1] }
function Base64Url([byte[]]$Bytes) { [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+','-').Replace('/','_') }
$now=[DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$header=Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
$payload=@{iss='loan-local';aud='loan-service';sub=$Subject;tenant=$Tenant;roles=@($Role);iat=$now;exp=$now+3600}|ConvertTo-Json -Compress
$body=$header+'.'+(Base64Url ([Text.Encoding]::UTF8.GetBytes($payload)))
$hmac=New-Object Security.Cryptography.HMACSHA256
$hmac.Key=[Convert]::FromBase64String($settings['LOAN_LOCAL_JWT_SECRET'])
$signature=Base64Url ($hmac.ComputeHash([Text.Encoding]::ASCII.GetBytes($body)))
$hmac.Dispose()
Write-Output ($body+'.'+$signature)

