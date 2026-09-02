param([string]$BaseUrl='http://localhost:18081',[ValidateRange(1,64)][int]$Concurrency=4,[ValidateRange(1,100000)][int]$Applications=100,[switch]$Submit)
$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Net.Http
$applicant=& "$PSScriptRoot/token.ps1" -Role APPLICANT
$operations=& "$PSScriptRoot/token.ps1" -Role OPERATIONS
$body=Get-Content "$PSScriptRoot/application.json" -Raw | ConvertFrom-Json
$body.kyc.answeredAt=[DateTime]::UtcNow.AddSeconds(-5).ToString('o')
$body.privacy.acknowledgedAt=$body.kyc.answeredAt
$body.income.periodStart=[DateTime]::UtcNow.AddDays(-30).ToString('yyyy-MM-dd')
$body.income.periodEnd=[DateTime]::UtcNow.AddDays(-1).ToString('yyyy-MM-dd')
$payload=$body|ConvertTo-Json -Depth 20 -Compress
$headers=@{Authorization="Bearer $operations"}
$before=Invoke-RestMethod "$BaseUrl/api/v1/operations/metrics" -Headers $headers
$results=[Collections.Concurrent.ConcurrentBag[object]]::new()
$pool=[RunspaceFactory]::CreateRunspacePool(1,$Concurrency);$pool.Open()
$workers=@()
$script={
    param($Base,$Token,$Payload,$Count,$Submit,$Bag)
    Add-Type -AssemblyName System.Net.Http
    $http=[Net.Http.HttpClient]::new()
    $http.DefaultRequestHeaders.Authorization=[Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer',$Token)
    function Send([string]$Method,[string]$Path,[string]$Body,[string]$Operation,[string]$Version='') {
        $request=[Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method),"$Base/api/v1$Path")
        if($Method -eq 'POST'){$null=$request.Headers.TryAddWithoutValidation('Idempotency-Key',[Guid]::NewGuid().ToString())}
        if($Version){$null=$request.Headers.TryAddWithoutValidation('If-Match',$Version)}
        if($Body){$request.Content=[Net.Http.StringContent]::new($Body,[Text.Encoding]::UTF8,'application/json')}
        $watch=[Diagnostics.Stopwatch]::StartNew()
        try {
            $response=$http.SendAsync($request).GetAwaiter().GetResult()
            $text=$response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            $watch.Stop()
            $Bag.Add([pscustomobject]@{operation=$Operation;milliseconds=$watch.Elapsed.TotalMilliseconds;status=[int]$response.StatusCode})
            $ok=$response.IsSuccessStatusCode
            $response.Dispose()
            if($ok -and $text){return ($text|ConvertFrom-Json)}
        } catch {
            $watch.Stop();$Bag.Add([pscustomobject]@{operation=$Operation;milliseconds=$watch.Elapsed.TotalMilliseconds;status=0})
        } finally {$request.Dispose()}
    }
    try {
        for($i=0;$i -lt $Count;$i++){
            $app=Send POST '/loan-applications' $Payload 'intake'
            if($app -and $app.id){
                if($Submit){$null=Send POST "/loan-applications/$($app.id)/submit" '' 'submit' '0'}
                $null=Send GET "/loan-applications/$($app.id)/processing-status" '' 'status'
            }
        }
    } finally {$http.Dispose()}
}
$elapsed=[Diagnostics.Stopwatch]::StartNew()
for($i=0;$i -lt $Concurrency;$i++){
    $count=[Math]::Floor($Applications/$Concurrency)
    if($i -lt ($Applications % $Concurrency)){$count++}
    if($count -eq 0){continue}
    $ps=[PowerShell]::Create();$ps.RunspacePool=$pool
    $null=$ps.AddScript($script).AddArgument($BaseUrl).AddArgument($applicant).AddArgument($payload).AddArgument($count).AddArgument([bool]$Submit).AddArgument($results)
    $workers+=@{shell=$ps;handle=$ps.BeginInvoke()}
}
$peakHeap=[long]$before.heapUsedBytes;$maxBacklog=[long]$before.backlog
while(@($workers|Where-Object{-not $_.handle.IsCompleted}).Count -gt 0){
    Start-Sleep -Milliseconds 250
    $sample=Invoke-RestMethod "$BaseUrl/api/v1/operations/metrics" -Headers $headers
    $peakHeap=[Math]::Max($peakHeap,[long]$sample.heapUsedBytes)
    $maxBacklog=[Math]::Max($maxBacklog,[long]$sample.backlog)
}
foreach($item in $workers){$null=$item.shell.EndInvoke($item.handle);if($item.shell.HadErrors){throw ($item.shell.Streams.Error|Out-String)};$item.shell.Dispose()}
$elapsed.Stop();$pool.Close();$pool.Dispose()
$after=Invoke-RestMethod "$BaseUrl/api/v1/operations/metrics" -Headers $headers
function Latency($Values) {
    $sorted=@($Values|Sort-Object);if($sorted.Count -eq 0){return @{}}
    @{samples=$sorted.Count;p50Ms=$sorted[[Math]::Max(0,[Math]::Ceiling($sorted.Count*0.50)-1)];p95Ms=$sorted[[Math]::Max(0,[Math]::Ceiling($sorted.Count*0.95)-1)];p99Ms=$sorted[[Math]::Max(0,[Math]::Ceiling($sorted.Count*0.99)-1)]}
}
$report=[ordered]@{
    scope='Synthetic application intake and status APIs using local mock providers; excludes documents and real AI throughput'
    utc=[DateTime]::UtcNow.ToString('o');concurrency=$Concurrency;applicationsRequested=$Applications;submitted=[bool]$Submit
    elapsedSeconds=$elapsed.Elapsed.TotalSeconds;completedRequests=$results.Count;requestsPerSecond=$results.Count/$elapsed.Elapsed.TotalSeconds
    errors=@($results|Where-Object{$_.status -lt 200 -or $_.status -ge 300}).Count
    intakeLatency=(Latency @($results|Where-Object{$_.operation -eq 'intake'}|ForEach-Object{$_.milliseconds}))
    statusLatency=(Latency @($results|Where-Object{$_.operation -eq 'status'}|ForEach-Object{$_.milliseconds}))
    backlogBefore=$before.backlog;backlogAfter=$after.backlog;backlogGrowth=([long]$after.backlog-[long]$before.backlog);maximumSampledBacklog=$maxBacklog
    maximumSampledJvmHeapBytes=$peakHeap;finalJvmHeapBytes=$after.heapUsedBytes
}
$report|ConvertTo-Json -Depth 10
if($report.errors -gt 0){exit 1}

