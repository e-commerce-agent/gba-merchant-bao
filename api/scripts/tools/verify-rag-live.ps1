param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$StaffToken = $env:RAG_DEMO_STAFF_TOKEN,
    [string]$ChatToken = $env:RAG_DEMO_CHAT_TOKEN,
    [ValidateRange(1, 300)][int]$TimeoutSeconds = 120
)
$ErrorActionPreference = 'Stop'
$uri = [Uri]$BaseUrl
if (-not $uri.IsAbsoluteUri -or $uri.Scheme -notin @('http', 'https') -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) {
    throw 'BaseUrl must be an HTTP(S) application URL without credentials, query or fragment.'
}
if (-not $StaffToken -or -not $ChatToken) { throw 'Set RAG_DEMO_STAFF_TOKEN and RAG_DEMO_CHAT_TOKEN for the same merchant first.' }
$BaseUrl = $BaseUrl.TrimEnd('/')
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$examples = Join-Path $repositoryRoot 'api/docs/rag/examples'
$output = Join-Path $repositoryRoot ('api/target/rag-live/' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ'))
New-Item -ItemType Directory -Path $output -Force | Out-Null
$client = [Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds($TimeoutSeconds)
$results = [Collections.Generic.List[object]]::new()
function Send-RagRequest([string]$Method, [string]$Path, [string]$Token, [Net.Http.HttpContent]$Content) {
    $request = [Net.Http.HttpRequestMessage]::new([Net.Http.HttpMethod]::new($Method), "$BaseUrl$Path")
    $request.Headers.Authorization = [Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token)
    $request.Headers.Add('Accept', 'application/json, text/event-stream')
    $request.Content = $Content
    try {
        $response = $client.SendAsync($request).GetAwaiter().GetResult()
        try {
            $body = $response.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $response.IsSuccessStatusCode) { throw "HTTP $([int]$response.StatusCode) at $Path; check application request logs." }
            return $body
        } finally { $response.Dispose() }
    } finally { $request.Dispose() }
}
function Json-Body($Value) { return [Net.Http.StringContent]::new(($Value | ConvertTo-Json -Depth 15 -Compress), [Text.Encoding]::UTF8, 'application/json') }
try {
    $uploads = @(
        @{file='product.md'; title='商品资料'},
        @{file='after-sales.txt'; title='售后政策'},
        @{file='store-rules.md'; title='店铺规则'}
    )
    $documents = @()
    foreach ($upload in $uploads) {
        $form = [Net.Http.MultipartFormDataContent]::new()
        $form.Add([Net.Http.StringContent]::new($upload.title, [Text.Encoding]::UTF8), 'title')
        $form.Add([Net.Http.ByteArrayContent]::new([IO.File]::ReadAllBytes((Join-Path $examples $upload.file))), 'file', $upload.file)
        $document = (Send-RagRequest 'POST' '/api/v1/knowledge/documents' $StaffToken $form | ConvertFrom-Json).data
        if ($document.status -ne 'READY') { throw 'Upload did not publish READY.' }
        $documents += $document
    }
    $page = (Send-RagRequest 'GET' '/api/v1/knowledge/documents?size=100' $StaffToken $null | ConvertFrom-Json).data
    foreach ($document in $documents) {
        if ($document.documentId -notin @($page.items.documentId)) { throw 'Uploaded document missing from tenant list.' }
    }
    $questions = Get-Content -LiteralPath (Join-Path $examples 'questions.json') -Raw -Encoding utf8 | ConvertFrom-Json
    foreach ($case in $questions) {
        $session = (Send-RagRequest 'POST' '/api/v1/chat/sessions' $ChatToken (Json-Body @{title='RAG合成资料联调'}) | ConvertFrom-Json).data
        $path = "/api/v1/chat/sessions/$($session.sessionId)/messages"
        $body = @{content=$case.question; clientMessageId=[Guid]::NewGuid().ToString()}
        $sse = Send-RagRequest 'POST' $path $ChatToken (Json-Body $body)
        $done = $null; $answer = ''; $terminalError = $false
        foreach ($frame in ($sse -split '\r?\n\r?\n')) {
            $event = ''; $dataLines = @()
            foreach ($line in ($frame -split '\r?\n')) {
                if ($line -match '^event:\s*(.+)$') { $event = $Matches[1] }
                if ($line -match '^data:\s?(.*)$') { $dataLines += $Matches[1] }
            }
            if ($dataLines.Count -eq 0) { continue }
            $data = ($dataLines -join "`n") | ConvertFrom-Json
            if ($event -eq 'token') { $answer += $data.delta }
            if ($event -eq 'done') { $done = $data }
            if ($event -eq 'error') { $terminalError = $true }
        }
        $history = (Send-RagRequest 'GET' $path $ChatToken $null | ConvertFrom-Json).data.items
        $saved = @($history | Where-Object senderType -eq 'ASSISTANT')[-1]
        $sameSnapshot = $null -ne $done -and (($done.sources | ConvertTo-Json -Depth 10 -Compress) -eq ($saved.sources | ConvertTo-Json -Depth 10 -Compress))
        $expectedSource = if ($case.noMatch) { @($done.sources).Count -eq 0 -and $answer -eq $case.expected } else { $case.title -in @($done.sources.title) }
        $duplicate = Send-RagRequest 'POST' $path $ChatToken (Json-Body $body) | ConvertFrom-Json
        $pass = -not $terminalError -and $null -ne $done -and $sameSnapshot -and $expectedSource -and $saved.content -eq $answer -and $duplicate.data.runId -eq $done.runId
        $results.Add([ordered]@{question=$case.question; expected=$case.expected; answer=$answer; sources=$done.sources; snapshotMatches=$sameSnapshot; contractPass=$pass; answerReview='manual review required'})
    }
    [IO.File]::WriteAllText((Join-Path $output 'results.json'), (@{testedAt=[DateTime]::UtcNow.ToString('o'); mode='real configured application; no model substitutes'; documents=$documents; results=$results} | ConvertTo-Json -Depth 20), [Text.UTF8Encoding]::new($false))
    if (@($results | Where-Object { -not $_.contractPass }).Count -gt 0) { throw "Badcase recorded in $output/results.json; inspect sources, configured min-score and expected answer, then rerun." }
    Write-Output "Five cases completed. Review answer wording and provider/model identity; results: $output/results.json"
} finally { $client.Dispose() }
