[CmdletBinding()]
param(
    [Parameter(Position=0)][ValidateSet('help','doctor','start','stop','restart','build','test','deps','status','logs','clean')][string]$Command='help',
    [ValidateSet('iam','backend','dev')][string]$Stack='iam',
    [ValidateSet('docker','native')][string]$Workflow='docker',
    [ValidateSet('unit','integration','frontend','e2e','all')][string]$Suite='unit',
    [string[]]$Service=@(), [string]$ProjectName='', [string]$EnvFile='.env',
    [ValidateRange(1,3600)][int]$Timeout=600,
    [ValidateRange(1,10000)][int]$Tail=100,
    [switch]$Build, [switch]$Follow
)
$ErrorActionPreference='Stop'
$Root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$script:Failures=0

function Stop-Bank([string]$Message,[int]$Code=2) {
    $failure=New-Object InvalidOperationException $Message
    $failure.Data['BankExitCode']=$Code
    throw $failure
}
function Write-Check([string]$Level,[string]$Message) {
    Write-Host ('{0,-4} {1}' -f $Level,$Message)
    if($Level -eq 'FAIL') {$script:Failures++}
}
function Invoke-Captured([string]$Program,[string[]]$Arguments) {
    $previous=$ErrorActionPreference
    try {
        $ErrorActionPreference='Continue'
        $output=@(& $Program @Arguments 2>$null)
        $code=$LASTEXITCODE
    } finally {$ErrorActionPreference=$previous}
    [pscustomobject]@{Code=$code;Text=($output -join "`n")}
}
function Invoke-Required([string]$Program,[string[]]$Arguments) {
    $previous=$ErrorActionPreference
    try {$ErrorActionPreference='Continue'; & $Program @Arguments; $code=$LASTEXITCODE}
    finally {$ErrorActionPreference=$previous}
    if($code -ne 0) {Stop-Bank "Subprocess failed (exit $code). No later step was run." $code}
}
function Show-Help {
    @'
Placeholder Bank developer commands (PowerShell 5.1+)
  bank.ps1 help | doctor | start | stop | restart | build | test | deps | status | logs | clean

  -Stack iam|backend|dev       Existing Compose definitions; default iam.
  -Workflow docker|native     doctor/build workflow; default docker.
  -Service NAME[,NAME...]     Known Compose services for lifecycle/status/deps/logs.
  -ProjectName NAME           Explicit isolated project; default Compose/.env identity.
  -EnvFile PATH               Compose environment file, relative to repository root.
  -Build                     start: explicitly build images before starting.
  -Timeout SECONDS            Readiness deadline, default 600.
  -Suite unit|integration|frontend|e2e|all   test selection, default unit.
  -Tail LINES -Follow         logs controls, default 100 lines without following.

Examples (work from any current directory):
  .\scripts\bank.ps1 doctor
  .\scripts\bank.ps1 build
  .\scripts\bank.ps1 start
  .\scripts\bank.ps1 deps -Service loans
  .\scripts\bank.ps1 logs -Service loans -Tail 50 -Follow
  .\scripts\bank.ps1 build -Workflow native
  .\scripts\bank.ps1 test -Suite all

Docker workflows require Git, Docker Engine/Desktop and Compose with --wait/!reset support;
no host JDK, Maven or Node is required. Initialize private config with iam/init.ps1.
Native build/test requires JDK 21 / Maven Wrapper and Node >=24 / npm as selected.
Integration tests also require Docker; e2e requires the ready IAM stack and Chromium.
deps checks container readiness and the Compose dependency graph; it performs no package audit.

build, start -Build, native build/test and first start may download pinned images/dependencies.
Build omits tests; use test explicitly. e2e installs the pinned Playwright Chromium browser.
start/restart affect services; stop preserves containers and every volume. No image pull policy
upgrades, volume deletion, prune or database reset is performed. logs may follow until Ctrl+C.
clean removes only paths in scripts/clean-paths.txt; it keeps dependencies, .env, .iam and volumes.
Exit: 0 success; 1 stack not ready; 2 prerequisites/usage; subprocess failures keep their exit code.
'@ | Write-Host
}
function Check-Tool([string]$Name,[string]$Hint) {
    if(Get-Command $Name -ErrorAction SilentlyContinue) {Write-Check PASS "$Name is available"; return $true}
    Write-Check FAIL "$Name is missing. $Hint"; return $false
}
function Check-Native([bool]$Java,[bool]$Node,[bool]$NeedDocker=$false) {
    $null=Check-Tool git 'Install Git and open a new shell.'
    if($Java) {
        foreach($file in @('mvnw','mvnw.cmd','.mvn/wrapper/maven-wrapper.properties')) {
            if(Test-Path -LiteralPath (Join-Path $Root $file)) {Write-Check PASS "Wrapper file $file"}
            else {Write-Check FAIL "Missing $file; restore the shared Maven Wrapper."}
        }
        $javaCommand='java'
        if($env:JAVA_HOME) {$javaCommand=Join-Path $env:JAVA_HOME 'bin/java.exe'; if($env:OS -ne 'Windows_NT') {$javaCommand=Join-Path $env:JAVA_HOME 'bin/java'}}
        if(Get-Command $javaCommand -ErrorAction SilentlyContinue) {
            $previous=$ErrorActionPreference
            try {$ErrorActionPreference='Continue'; $version=(& $javaCommand -version 2>&1 | Out-String)} finally {$ErrorActionPreference=$previous}
            if($version -match 'version "21[.\"]') {Write-Check PASS 'Java 21 is selected'}
            else {Write-Check FAIL 'Select a JDK 21 with JAVA_HOME and PATH.'}
        } else {Write-Check FAIL 'Java is missing. Set JAVA_HOME to an installed JDK 21.'}
    }
    if($Node) {
        if(Check-Tool node 'Install Node 24 LTS or newer.') {
            $nodeVersion=Invoke-Captured node @('--version')
            if($nodeVersion.Code -eq 0 -and $nodeVersion.Text -match '^v(\d+)' -and [int]$Matches[1] -ge 24) {Write-Check PASS "Node $($nodeVersion.Text.Trim()) satisfies package engines"}
            else {Write-Check FAIL 'Node >=24 is required by loan-web/package.json.'}
        }
        $null=Check-Tool npm.cmd 'Install Node with npm.'
        if(-not (Test-Path -LiteralPath (Join-Path $Root 'loan-web/package-lock.json'))) {Write-Check FAIL 'Restore loan-web/package-lock.json for npm ci.'}
    }
    if($NeedDocker) {Check-DockerEngine}
}
function Check-DockerEngine {
    if(Check-Tool docker 'Install Docker and start its daemon.') {
        $engine=Invoke-Captured docker @('info','--format','{{.ServerVersion}}')
        if($engine.Code -eq 0) {Write-Check PASS "Docker daemon $($engine.Text.Trim())"} else {Write-Check FAIL 'Docker daemon unavailable. Start Docker Desktop/Engine.'}
        $compose=Invoke-Captured docker @('compose','version','--short')
        if($compose.Code -eq 0) {Write-Check PASS "Compose $($compose.Text.Trim())"} else {Write-Check FAIL 'Install a current Docker Compose with --wait and !reset support.'}
    }
}
function Set-ComposeContext {
    $script:ComposeFiles=@((Join-Path $Root 'compose.yaml'))
    if($Stack -ne 'backend') {$script:ComposeFiles+=Join-Path $Root 'compose.iam.yaml'}
    if($Stack -eq 'dev') {$script:ComposeFiles+=Join-Path $Root 'compose.dev.yaml'}
    $script:EnvironmentPath=if([IO.Path]::IsPathRooted($EnvFile)) {$EnvFile} else {Join-Path $Root $EnvFile}
    $script:ComposeArguments=@('compose','--project-directory',$Root,'--env-file',$script:EnvironmentPath)
    foreach($file in $script:ComposeFiles) {$script:ComposeArguments+=@('-f',$file)}
    if($ProjectName) {
        if($ProjectName -cnotmatch '^[a-z0-9][a-z0-9_-]*$') {Stop-Bank 'ProjectName must use lowercase letters, digits, hyphens or underscores.'}
        $script:ComposeArguments+=@('--project-name',$ProjectName)
    }
}
function Read-Configuration {
    $values=@{}
    if(Test-Path -LiteralPath $script:EnvironmentPath) {
        foreach($line in [IO.File]::ReadAllLines($script:EnvironmentPath)) {
            if($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
                $value=$Matches[2].Trim()
                if($value.Length -ge 2 -and (($value.StartsWith('"') -and $value.EndsWith('"')) -or ($value.StartsWith("'") -and $value.EndsWith("'")))) {$value=$value.Substring(1,$value.Length-2)}
                $values[$Matches[1]]=$value
            }
        }
        Write-Check PASS 'Compose environment file exists; values are never printed'
    } else {Write-Check FAIL "Missing environment file. Run iam/init.ps1 (or loan-service/scripts/init-local.ps1 for backend)."}
    $required=@()
    foreach($file in $script:ComposeFiles) {
        if(-not (Test-Path -LiteralPath $file)) {Write-Check FAIL "Missing Compose file: $file"; continue}
        foreach($match in [regex]::Matches([IO.File]::ReadAllText($file),'(?<!\$)\$\{([A-Z][A-Z0-9_]*)(?::\?[^}]*)?\}')) {$required+=$match.Groups[1].Value}
    }
    foreach($name in ($required | Sort-Object -Unique)) {
        $value=[Environment]::GetEnvironmentVariable($name)
        if([string]::IsNullOrWhiteSpace($value)) {$value=$values[$name]}
        if([string]::IsNullOrWhiteSpace($value)) {Write-Check FAIL "Missing variable $name; initialize/supply it without replacing existing keys."}
    }
    if($script:Failures -gt 0) {return}
    $config=Invoke-Captured docker ($script:ComposeArguments+@('config','--format','json'))
    if($config.Code -ne 0) {Write-Check FAIL 'Compose configuration is invalid. Check file syntax, variables and Compose version; expanded values are suppressed.'; return}
    try {$script:Model=$config.Text | ConvertFrom-Json} catch {Write-Check FAIL 'Compose JSON could not be read; update Docker Compose.'; return}
    $services=Invoke-Captured docker ($script:ComposeArguments+@('config','--services'))
    if($services.Code -ne 0) {Write-Check FAIL 'Cannot resolve expected Compose services.'; return}
    $script:Expected=@($services.Text -split '\r?\n' | Where-Object {$_})
    $script:Edges=@()
    foreach($name in $script:Expected) {
        foreach($dependency in $script:Model.services.$name.depends_on.PSObject.Properties) {
            $script:Edges+=[pscustomobject]@{Consumer=$name;Dependency=$dependency.Name;Condition=$dependency.Value.condition}
        }
    }
    foreach($name in $Service) {if($script:Expected -cnotcontains $name) {Stop-Bank "Unknown service '$name'. Use a service from the selected Compose stack."}}
    Write-Check PASS "Compose project $($script:Model.name); $($script:Expected.Count) expected services ($Stack)"
}
function Get-Targets([bool]$Dependencies=$false) {
    $targets=if($Service.Count) {@($Service)} else {@($script:Expected)}
    if($Dependencies) {
        $pending=New-Object 'System.Collections.Generic.Queue[string]'
        foreach($name in $targets) {$pending.Enqueue($name)}
        $found=New-Object 'System.Collections.Generic.HashSet[string]'
        if($Service.Count) {foreach($name in $Service) {$null=$found.Add($name)}}
        while($pending.Count) {
            $consumer=$pending.Dequeue()
            foreach($edge in ($script:Edges | Where-Object Consumer -eq $consumer)) {
                if($found.Add($edge.Dependency)) {$pending.Enqueue($edge.Dependency)}
            }
        }
        return @($found | Sort-Object)
    }
    return $targets
}
function Read-Service([string]$Name) {
    $result=Invoke-Captured docker ($script:ComposeArguments+@('ps','-a','--quiet',$Name))
    if($result.Code -ne 0) {Stop-Bank 'Cannot inspect Compose containers; Docker may be unavailable.'}
    $ids=@($result.Text -split '\r?\n' | Where-Object {$_})
    if(-not $ids.Count) {return [pscustomobject]@{Service=$Name;State='not created';Readiness='not ready';Endpoint='-';Detail='Run start';Ready=$false}}
    $states=@();$endpoints=@();$ready=$true;$details=@();$health=@()
    foreach($containerId in $ids) {
        $inspection=Invoke-Captured docker @('inspect',$containerId)
        if($inspection.Code -ne 0) {Stop-Bank 'Docker inspection failed; retry status after checking the daemon.'}
        try {$container=($inspection.Text | ConvertFrom-Json)[0]} catch {Stop-Bank 'Invalid Docker inspection response.'}
        $state=$container.State.Status;$states+=$state
        $complete=@($script:Edges | Where-Object { $_.Dependency -eq $Name -and $_.Condition -eq 'service_completed_successfully' }).Count -gt 0
        if($complete -and $state -eq 'exited' -and $container.State.ExitCode -eq 0) {$health+='completed'}
        elseif($state -ne 'running') {$ready=$false;$health+=if($state -eq 'exited' -and $container.State.ExitCode -ne 0){'failed'}else{'stopped'};$details+="exit $($container.State.ExitCode)"}
        elseif($container.State.Health) {$health+=$container.State.Health.Status;if($container.State.Health.Status -ne 'healthy') {$ready=$false;$details+='health check pending/failed'}}
        elseif($Name -in @('storage','mailpit')) {
            # Older containers do not yet have the new healthcheck mount; use the same probe via stdin.
            $previous=$ErrorActionPreference
            try {$ErrorActionPreference='Continue'; Get-Content -LiteralPath (Join-Path $PSScriptRoot 'container-health.sh') -Raw | & docker @script:ComposeArguments exec -T $Name sh -s -- $Name *> $null; $probe=$LASTEXITCODE} finally {$ErrorActionPreference=$previous}
            if($probe -eq 0) {$health+='healthy (live probe)'} else {$health+='unhealthy';$ready=$false;$details+='live readiness probe failed'}
        } else {$health+='unverified';$ready=$false;$details+='Add a meaningful Compose/image healthcheck'}
        foreach($binding in $container.NetworkSettings.Ports.PSObject.Properties) {
            foreach($port in $binding.Value) {if($port.HostPort) {$endpoints+="http://$($port.HostIp):$($port.HostPort)"}}
        }
    }
    [pscustomobject]@{Service=$Name;State=($states -join ',');Readiness=($health -join ',');Endpoint=if($endpoints.Count){($endpoints | Sort-Object -Unique) -join ','}else{'internal'};Detail=($details -join '; ');Ready=$ready}
}
function Show-Status([bool]$Dependencies=$false) {
    $targets=@(Get-Targets $Dependencies)
    if($Dependencies) {
        foreach($edge in $script:Edges) {if(($targets -contains $edge.Dependency) -and (-not $Service.Count -or $targets -contains $edge.Consumer)) {Write-Host "  $($edge.Consumer) -> $($edge.Dependency) [$($edge.Condition)]"}}
    }
    $rows=@(foreach($name in $targets) {Read-Service $name})
    $rows | Select-Object Service,State,Readiness,Endpoint,Detail | Format-Table -AutoSize | Out-Host
    if(@($rows | Where-Object {-not $_.Ready}).Count) {Write-Check FAIL 'Expected services are not ready; use logs -Service NAME.'; return $false}
    Write-Check PASS $(if($Dependencies){'All selected container dependencies are ready.'}else{'The selected stack is ready.'})
    return $true
}
function Check-Ports {
    $owned=@()
    $ids=Invoke-Captured docker ($script:ComposeArguments+@('ps','--quiet'))
    foreach($containerId in ($ids.Text -split '\r?\n' | Where-Object {$_})) {
        $ports=Invoke-Captured docker @('port',$containerId)
        foreach($line in ($ports.Text -split '\r?\n')) {if($line -match ':(\d+)\s*$') {$owned+=[int]$Matches[1]}}
    }
    foreach($name in $script:Expected) {
        foreach($port in $script:Model.services.$name.ports) {
            if(-not $port.published -or [int]$port.published -eq 0) {continue}
            $number=[int]$port.published
            if($owned -contains $number) {Write-Check PASS "Port $number belongs to this running Compose project"; continue}
            $listener=New-Object Net.Sockets.TcpListener ([Net.IPAddress]::Loopback),$number
            try {$listener.Start();Write-Check PASS "Loopback port $number is available"}
            catch {Write-Check FAIL "Port $number is occupied outside this project. Resolve the conflict before start."}
            finally {$listener.Stop()}
        }
    }
}
function Require-Docker([bool]$Ports=$false) {
    $null=Check-Tool git 'Install Git.'
    Check-DockerEngine
    if($script:Failures) {Stop-Bank 'Docker prerequisites failed; no services were changed.'}
    Set-ComposeContext; Read-Configuration
    if($script:Failures) {Stop-Bank 'Configuration checks failed; no services were changed.'}
    if($Ports) {Check-Ports;if($script:Failures) {Stop-Bank 'Port checks failed; no services were changed.'}}
}
function Invoke-Maven([string[]]$Goals) {Invoke-Required (Join-Path $Root 'mvnw.cmd') (@('-B','-ntp')+$Goals)}
function Invoke-Web([string[]]$NpmArguments) {
    Push-Location (Join-Path $Root 'loan-web')
    try {Invoke-Required npm.cmd $NpmArguments} finally {Pop-Location}
}
function Clean-Generated {
    foreach($relative in Get-Content -LiteralPath (Join-Path $PSScriptRoot 'clean-paths.txt')) {
        if(-not $relative -or $relative.StartsWith('#')) {continue}
        $target=[IO.Path]::GetFullPath((Join-Path $Root $relative))
        $prefix=$Root.TrimEnd([IO.Path]::DirectorySeparatorChar)+[IO.Path]::DirectorySeparatorChar
        if(-not $target.StartsWith($prefix,[StringComparison]::OrdinalIgnoreCase)) {Stop-Bank "Cleanup path leaves the repository: $relative"}
        $ancestor=$target
        while($ancestor -and $ancestor -ne $Root) {
            if(Test-Path -LiteralPath $ancestor) {
                $item=Get-Item -LiteralPath $ancestor -Force
                if($item.Attributes -band [IO.FileAttributes]::ReparsePoint) {Stop-Bank "Cleanup refuses a link/junction: $relative"}
            }
            $ancestor=Split-Path $ancestor -Parent
        }
        if(Test-Path -LiteralPath $target) {Remove-Item -LiteralPath $target -Force -Recurse;Write-Host "Removed generated $relative"}
    }
    Write-Check PASS 'Generated output cleaned; .env, .iam, source, dependencies and volumes preserved.'
}

Push-Location $Root
try {
    switch($Command) {
        help {Show-Help}
        clean {Clean-Generated}
        doctor {
            if($Workflow -eq 'native') {Check-Native $true $true}
            else {Require-Docker $true}
            if($script:Failures) {Stop-Bank 'Prerequisites failed.'}
        }
        build {
            if($Service.Count) {Stop-Bank 'build builds the complete configured project; use Service for lifecycle commands.'}
            if($Workflow -eq 'native') {
                Check-Native $true $true;if($script:Failures) {Stop-Bank 'Native build prerequisites failed.'}
                Write-Host 'Building Java reactor and frontend; tests are omitted. Use test to run them.'
                Invoke-Maven @('-DskipTests','package');Invoke-Web @('ci','--no-fund');Invoke-Web @('run','build')
            } else {Require-Docker;Write-Host 'Building configured images; Dockerfiles omit tests. Use test separately.';Invoke-Required docker ($script:ComposeArguments+@('build'))}
        }
        test {
            $java=$Suite -in @('unit','integration','all');$node=$Suite -in @('frontend','e2e','all')
            Check-Native $java $node ($Suite -in @('integration','all'))
            if($script:Failures) {Stop-Bank 'Selected test prerequisites failed; tests were not executed.'}
            if($Suite -in @('e2e','all')) {
                if($Stack -eq 'backend') {Stop-Bank 'Browser tests require Stack iam or dev.'}
                Require-Docker;if(-not (Show-Status)) {Stop-Bank 'Start the IAM stack before browser tests.' 1}
            }
            if($Suite -eq 'unit') {Invoke-Maven @('test')}
            if($Suite -in @('integration','all')) {Invoke-Maven @('verify')}
            if($node) {Invoke-Web @('ci','--no-fund')}
            if($Suite -in @('frontend','all')) {foreach($step in @('typecheck','lint','test','build')) {if($step -eq 'test'){Invoke-Web @('test')}else{Invoke-Web @('run',$step)}}}
            if($Suite -in @('e2e','all')) {
                Push-Location (Join-Path $Root 'loan-web')
                try {Invoke-Required npx.cmd @('--no-install','playwright','install','chromium');Invoke-Required npm.cmd @('run','test:e2e')} finally {Pop-Location}
            }
            Write-Check PASS "Executed test selection '$Suite' completed successfully. Other suites were not run unless included by this selection."
        }
        default {
            Require-Docker ($Command -eq 'start')
            switch($Command) {
                status {if(-not (Show-Status)) {Stop-Bank 'Stack is not ready.' 1}}
                deps {if(-not (Show-Status $true)) {Stop-Bank 'Container dependencies are not ready.' 1}}
                logs {
                    $options=@('logs','--tail',"$Tail");if($Follow){$options+='--follow'}
                    Invoke-Required docker ($script:ComposeArguments+$options+$Service)
                }
                stop {Invoke-Required docker ($script:ComposeArguments+@('stop','--timeout','30')+$Service);Write-Check PASS 'Services stopped. All containers and volumes are preserved.'}
                start {
                    $options=@('up','-d','--wait','--wait-timeout',"$Timeout")
                    if($Build){$options+='--build'}else{$options+='--no-build'}
                    try {Invoke-Required docker ($script:ComposeArguments+$options+$Service)} catch {
                        $null=Show-Status;Write-Host 'Startup failed. Run scripts/bank.ps1 logs -Service NAME -Tail 80 with the same Stack/ProjectName/EnvFile options.';throw
                    }
                    if(-not (Show-Status)) {Stop-Bank 'Readiness checks failed.' 1}
                }
                restart {
                    Invoke-Required docker ($script:ComposeArguments+@('restart','--timeout','30')+$Service)
                    Invoke-Required docker ($script:ComposeArguments+@('up','-d','--no-recreate','--no-build','--wait','--wait-timeout',"$Timeout")+$Service)
                    if(-not (Show-Status)) {Stop-Bank 'Restart readiness checks failed.' 1}
                }
            }
        }
    }
} catch {
    Write-Host "FAIL $($_.Exception.Message)"
    $exitCode=2;if($_.Exception.Data.Contains('BankExitCode')) {$exitCode=[int]$_.Exception.Data['BankExitCode']}
    exit $exitCode
} finally {Pop-Location}
