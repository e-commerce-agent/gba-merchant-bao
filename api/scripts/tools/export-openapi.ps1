param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [ValidateRange(1, 300)][int]$TimeoutSeconds = 30
)
$ErrorActionPreference = 'Stop'
$baseUri = [Uri]$BaseUrl
if (-not $baseUri.IsAbsoluteUri -or $baseUri.Scheme -notin @('http', 'https') -or
    $baseUri.UserInfo -or $baseUri.Query -or $baseUri.Fragment) {
    throw 'BaseUrl must be an absolute HTTP(S) application URL without credentials, query or fragment.'
}
$BaseUrl = $BaseUrl.TrimEnd('/')
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$groups = @('auth-v1', 'agent-conversation-v1', 'product-v1', 'inventory-v1', 'order-v1', 'shipment-v1')
$snapshotDirectory = Join-Path $repositoryRoot 'docs/api'
$stageDirectory = Join-Path $repositoryRoot ('api/target/openapi-export/' + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $stageDirectory -Force | Out-Null
$utf8 = New-Object System.Text.UTF8Encoding($false)

# Fetch every group first. Failed/empty responses never reach the snapshot directory.
foreach ($group in $groups) {
    $jsonResponse = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/v3/api-docs/$group" -TimeoutSec $TimeoutSeconds
    $document = ConvertFrom-Json -InputObject $jsonResponse.Content
    $paths = @($document.paths.PSObject.Properties | Where-Object { $_.MemberType -eq 'NoteProperty' })
    if ($document.openapi -notmatch '^3\.' -or $paths.Count -eq 0) {
        throw "Missing OpenAPI 3 document or empty group: $group"
    }
    $yamlResponse = Invoke-WebRequest -UseBasicParsing -Uri "$BaseUrl/v3/api-docs.yaml/$group" -TimeoutSec $TimeoutSeconds
    $yaml = [Text.Encoding]::UTF8.GetString($yamlResponse.RawContentStream.ToArray())
    if ($yaml -notmatch '(?m)^openapi:\s*["'']?3\.' -or $yaml -notmatch '(?m)^paths:\s*\r?$') {
        throw "Missing Springdoc YAML paths: $group"
    }
    foreach ($path in $paths.Name) {
        if ($path -notlike '/*' -or $yaml -notmatch ('(?m)^  ["'']?' + [regex]::Escape($path) + '["'']?:\s*\r?$')) {
            throw "JSON/YAML path mismatch: $group"
        }
    }
    [IO.File]::WriteAllText((Join-Path $stageDirectory "$group.openapi.json"), [string]$jsonResponse.Content, $utf8)
    [IO.File]::WriteAllText((Join-Path $stageDirectory "$group.openapi.yaml"), $yaml, $utf8)
}

# Keep prior bytes for rollback if publishing a fully staged batch fails.
New-Item -ItemType Directory -Path $snapshotDirectory -Force | Out-Null
$previous = @{}
foreach ($group in $groups) {
    $destination = Join-Path $snapshotDirectory "$group.openapi.yaml"
    if (Test-Path -LiteralPath $destination) {
        $backup = Join-Path $stageDirectory "$group.previous.yaml"
        Copy-Item -LiteralPath $destination -Destination $backup
        $previous[$group] = $backup
    }
}
$attempted = [Collections.Generic.List[string]]::new()
try {
    foreach ($group in $groups) {
        $attempted.Add($group)
        Copy-Item -LiteralPath (Join-Path $stageDirectory "$group.openapi.yaml") -Destination (Join-Path $snapshotDirectory "$group.openapi.yaml") -Force
    }
} catch {
    foreach ($group in $attempted) {
        $destination = Join-Path $snapshotDirectory "$group.openapi.yaml"
        if ($previous.ContainsKey($group)) {
            Copy-Item -LiteralPath $previous[$group] -Destination $destination -Force
        } elseif (Test-Path -LiteralPath $destination) {
            # Only the exact new snapshot attempted by this invocation is removed.
            Remove-Item -LiteralPath $destination
        }
    }
    throw
}
Write-Output "Exported $($groups.Count) Springdoc groups to docs/api; staging retained in $stageDirectory"
