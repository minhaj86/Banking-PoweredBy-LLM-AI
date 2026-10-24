[CmdletBinding()]
param([string]$BashExecutable='')
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$workspace=Join-Path $root '.iam/lifecycle-smoke workspace'
$outside=Join-Path $root '.iam/lifecycle-outside-probe'
$hash=[BitConverter]::ToString([Security.Cryptography.SHA256]::Create().ComputeHash([Text.Encoding]::UTF8.GetBytes($root))).Replace('-','').Substring(0,8).ToLowerInvariant()
$project='placeholder-bank-lifecycle-'+$hash
$checks=0
function Assert-Probe([bool]$Condition,[string]$Message) {
    if(-not $Condition) {throw $Message}
    $script:checks++;Write-Host "PASS $Message"
}
if(-not $BashExecutable) {
    $gitCommand=Get-Command git -ErrorAction Stop
    $candidate=Join-Path (Split-Path (Split-Path $gitCommand.Source)) 'bin/bash.exe'
    if(Test-Path -LiteralPath $candidate) {$BashExecutable=$candidate}
    elseif(Get-Command bash -ErrorAction SilentlyContinue) {$BashExecutable=(Get-Command bash).Source}
    else {throw 'Install/provide Bash to exercise both entry points.'}
}
foreach($directory in @($workspace,(Join-Path $workspace 'scripts'),$outside)) {New-Item -ItemType Directory -Force -Path $directory | Out-Null}
foreach($name in @('bank.ps1','bank.sh','container-health.sh','clean-paths.txt')) {Copy-Item -LiteralPath (Join-Path $root "scripts/$name") -Destination (Join-Path $workspace "scripts/$name") -Force}
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fixtures/compose.yaml') -Destination (Join-Path $workspace 'compose.yaml') -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fixtures/Dockerfile') -Destination (Join-Path $workspace 'Dockerfile') -Force
$environmentPath=Join-Path $workspace '.env'
if(-not (Test-Path -LiteralPath $environmentPath)) {
    $bytes=New-Object byte[] 32;$random=[Security.Cryptography.RandomNumberGenerator]::Create();$random.GetBytes($bytes);$random.Dispose()
    [IO.File]::WriteAllText($environmentPath,('SMOKE_PASSWORD='+[Convert]::ToBase64String($bytes)+"`n"),[Text.UTF8Encoding]::new($false))
}
$environmentHash=(Get-FileHash -LiteralPath $environmentPath).Hash
$compose=@('compose','--project-directory',$workspace,'--env-file',$environmentPath,'-f',(Join-Path $workspace 'compose.yaml'),'--project-name',$project)
$powershell=(Get-Process -Id $PID).Path
function Invoke-Probe([string]$Shell,[string[]]$Options,[int]$ExpectedCode=0) {
    $program=$powershell
    $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File',(Join-Path $workspace 'scripts/bank.ps1'))+$Options+@('-Stack','backend','-ProjectName',$project)
    if($Shell -eq 'bash') {$program=$BashExecutable;$arguments=@((Join-Path $workspace 'scripts/bank.sh'))+$Options+@('--stack','backend','--project-name',$project)}
    $log=Join-Path $workspace ($Shell+'-'+$Options[0]+'.log')
    Push-Location ([IO.Path]::GetTempPath())
    $previous=$ErrorActionPreference
    try {$ErrorActionPreference='Continue'; & $program @arguments *> $log; $code=$LASTEXITCODE}
    finally {$ErrorActionPreference=$previous;Pop-Location}
    Assert-Probe ($code -eq $ExpectedCode) "$Shell $($Options[0]) exits $ExpectedCode (actual $code) from outside a root containing spaces; output: $log"
    return $log
}
function Read-Scalar([string[]]$Arguments) {
    $previous=$ErrorActionPreference
    try {$ErrorActionPreference='Continue';$value=@(& docker @compose @Arguments 2>$null);$code=$LASTEXITCODE}
    finally {$ErrorActionPreference=$previous}
    if($code -ne 0) {throw 'Fixture Docker command failed.'}
    return ($value -join "`n").Trim()
}
function Assert-Persisted {
    $value=Read-Scalar @('exec','-T','db','psql','-U','postgres','-tAc','SELECT count(*) FROM lifecycle_probe WHERE value=42')
    Assert-Probe ($value -eq '1') 'Synthetic PostgreSQL row survives stop/start/restart'
    Assert-Probe ((Get-FileHash -LiteralPath $environmentPath).Hash -eq $environmentHash) 'Synthetic environment file is preserved byte-for-byte'
}

try {
    # A completed prior fixture is intentionally reusable; all operations are isolated.
    $null=Invoke-Probe powershell @('help')
    $null=Invoke-Probe bash @('help')
    $null=Invoke-Probe powershell @('doctor')
    $null=Invoke-Probe bash @('doctor')
    $priorJava=$env:JAVA_HOME
    try {
        $env:JAVA_HOME=Join-Path $workspace 'jdk-does-not-exist'
        $null=Invoke-Probe powershell @('doctor')
        $null=Invoke-Probe bash @('doctor')
        $null=Invoke-Probe powershell @('doctor','-Workflow','native') 2
        $null=Invoke-Probe bash @('doctor','--workflow','native') 2
    } finally {$env:JAVA_HOME=$priorJava}
    $priorHost=$env:DOCKER_HOST;$priorContext=$env:DOCKER_CONTEXT
    try {
        $env:DOCKER_HOST='tcp://127.0.0.1:1';$env:DOCKER_CONTEXT=$null
        $null=Invoke-Probe powershell @('doctor') 2
        $null=Invoke-Probe bash @('doctor') 2
    } finally {$env:DOCKER_HOST=$priorHost;$env:DOCKER_CONTEXT=$priorContext}
    $null=Invoke-Probe powershell @('doctor','-EnvFile','missing.env') 2
    $null=Invoke-Probe bash @('doctor','--env-file','missing.env') 2
    $null=Invoke-Probe powershell @('logs','-Service','unknown-service') 2
    $null=Invoke-Probe bash @('logs','--service','unknown-service') 2
    $null=Invoke-Probe powershell @('build')
    $null=Invoke-Probe powershell @('start','-Timeout','90')
    $before=Read-Scalar @('ps','-a','--quiet')
    $null=Invoke-Probe powershell @('start','-Timeout','90')
    Assert-Probe ((Read-Scalar @('ps','-a','--quiet')) -eq $before) 'Repeated start preserves existing container identities'
    $listener=New-Object Net.Sockets.TcpListener ([Net.IPAddress]::Loopback),0
    $priorPort=$env:SMOKE_PORT
    try {
        $listener.Start();$env:SMOKE_PORT=[string]$listener.LocalEndpoint.Port
        $null=Invoke-Probe powershell @('doctor') 2
        $null=Invoke-Probe bash @('doctor') 2
    } finally {$listener.Stop();$env:SMOKE_PORT=$priorPort}
    $null=Read-Scalar @('exec','-T','db','psql','-U','postgres','-c','CREATE TABLE IF NOT EXISTS lifecycle_probe(value integer PRIMARY KEY); INSERT INTO lifecycle_probe VALUES (42) ON CONFLICT DO NOTHING;')
    $null=Invoke-Probe powershell @('deps','-Service','app')
    $null=Invoke-Probe bash @('deps','--service','app')
    $null=Invoke-Probe powershell @('status')
    $null=Invoke-Probe bash @('status')
    $null=Read-Scalar @('exec','-T','app','chmod','000','/usr/share/nginx/html/index.html')
    $deadline=[DateTime]::UtcNow.AddSeconds(20)
    do {
        $applicationId=Read-Scalar @('ps','-a','--quiet','app')
        $health=(& docker inspect --format '{{.State.Health.Status}}' $applicationId).Trim()
        if($health -eq 'unhealthy') {break}
        Start-Sleep -Milliseconds 500
    } while([DateTime]::UtcNow -lt $deadline)
    Assert-Probe ($health -eq 'unhealthy') 'Fixture produces a real failing HTTP healthcheck'
    $null=Invoke-Probe powershell @('status') 1
    $null=Invoke-Probe bash @('status') 1
    $null=Read-Scalar @('exec','-T','app','chmod','644','/usr/share/nginx/html/index.html')
    $deadline=[DateTime]::UtcNow.AddSeconds(20)
    do {
        $health=(& docker inspect --format '{{.State.Health.Status}}' $applicationId).Trim()
        if($health -eq 'healthy') {break}
        Start-Sleep -Milliseconds 500
    } while([DateTime]::UtcNow -lt $deadline)
    Assert-Probe ($health -eq 'healthy') 'Fixture recovers after restoring its generated content'
    $null=Invoke-Probe powershell @('logs','-Service','app','-Tail','5')
    $null=Invoke-Probe bash @('logs','--service','app','--tail','5')
    $null=Invoke-Probe powershell @('restart','-Service','db','-Timeout','90')
    Assert-Persisted
    $null=Invoke-Probe powershell @('stop')
    $null=Invoke-Probe powershell @('status') 1
    $null=Invoke-Probe bash @('status') 1
    $null=Invoke-Probe bash @('start','--timeout','90')
    $null=Invoke-Probe bash @('start','--timeout','90')
    $null=Invoke-Probe bash @('restart','--service','cache','--timeout','90')
    Assert-Persisted

    $dockerfile=Join-Path $workspace 'Dockerfile'
    [IO.File]::WriteAllText($dockerfile,"FROM nginx:1.28.0-alpine`nRUN exit 23`n",[Text.UTF8Encoding]::new($false))
    $null=Invoke-Probe powershell @('build') 1
    $null=Invoke-Probe bash @('build') 1
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'fixtures/Dockerfile') -Destination $dockerfile -Force

    # Real cleanup against generated files, plus an escaping Windows junction.
    $keep=Join-Path $workspace 'keep.txt';[IO.File]::WriteAllText($keep,'keep')
    foreach($directory in @('loan-web/dist','loan-service/target','.reports')) {
        $path=Join-Path $workspace $directory;New-Item -ItemType Directory -Force -Path $path | Out-Null
        [IO.File]::WriteAllText((Join-Path $path 'generated.txt'),'regenerable')
    }
    $null=Invoke-Probe powershell @('clean')
    Assert-Probe ((Test-Path -LiteralPath $keep) -and -not (Test-Path -LiteralPath (Join-Path $workspace 'loan-web/dist'))) 'PowerShell clean removes only allowlisted output'
    New-Item -ItemType Directory -Force -Path (Join-Path $workspace 'loan-web/dist') | Out-Null
    $null=Invoke-Probe bash @('clean')
    Assert-Probe ((Test-Path -LiteralPath $keep) -and -not (Test-Path -LiteralPath (Join-Path $workspace 'loan-web/dist'))) 'Bash clean removes only allowlisted output'
    if($env:OS -eq 'Windows_NT') {
        [IO.File]::WriteAllText((Join-Path $outside 'keep.txt'),'outside')
        $junction=Join-Path $workspace 'loan-service/target'
        New-Item -ItemType Junction -Path $junction -Target $outside | Out-Null
        $null=Invoke-Probe powershell @('clean') 2
        $null=Invoke-Probe bash @('clean') 2
        Assert-Probe ((Get-Content -LiteralPath (Join-Path $outside 'keep.txt')) -eq 'outside') 'Both cleaners refuse a junction escaping their root'
        # Remove the junction itself without traversing its target.
        [IO.Directory]::Delete($junction)
    }
    Assert-Persisted
    $null=Invoke-Probe bash @('stop')
    $null=Invoke-Probe bash @('status') 1
    Write-Host "PASS $checks lifecycle assertions. Fixture services stopped; private config and test volumes preserved."
} finally {
    $previous=$ErrorActionPreference
    try {$ErrorActionPreference='Continue'; & docker @compose stop --timeout 15 *> $null}
    finally {$ErrorActionPreference=$previous}
    # No down, volume removal, data reset, stage, commit or push.
}
