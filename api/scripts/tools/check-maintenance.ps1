[CmdletBinding()]
param(
    [string]$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
$manifestPath = Join-Path $Root 'docs\archive\manifest.json'
$manifestRelative = 'docs/archive/manifest.json'
$expectedSourceCommit = 'c3cf4cebc183bec7347356e3aaeb47cdebed7093'
$errors = [System.Collections.Generic.List[string]]::new()

function Add-CheckError([string]$Message) {
    $script:errors.Add($Message)
}

function Get-Sha256([string]$Path) {
    $stream = [System.IO.File]::OpenRead($Path)
    try {
        $algorithm = [System.Security.Cryptography.SHA256]::Create()
        try { $hash = $algorithm.ComputeHash($stream) }
        finally { $algorithm.Dispose() }
        return ([BitConverter]::ToString($hash)).Replace('-', '').ToLowerInvariant()
    }
    finally {
        $stream.Dispose()
    }
}

function Get-GitText([string[]]$Arguments) {
    $output = & git -C $Root @Arguments 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    return ($output -join "`n").Trim()
}

if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
    Write-Error "Archive manifest is missing: $manifestRelative"
    exit 1
}

try {
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
}
catch {
    Write-Error "Archive manifest is invalid JSON: $($_.Exception.Message)"
    exit 1
}

if ($manifest.schemaVersion -ne 1) { Add-CheckError 'Unsupported manifest schemaVersion.' }
if ($manifest.sourceCommit -ne $expectedSourceCommit) {
    Add-CheckError "Manifest sourceCommit must remain the recorded source commit $expectedSourceCommit."
}
$commitCheck = Get-GitText @('cat-file', '-e', "$($manifest.sourceCommit)^{commit}")
if ($null -eq $commitCheck -and $LASTEXITCODE -ne 0) { Add-CheckError 'Manifest sourceCommit is not available in this Git repository.' }

$requiredModules = @('auth', 'product', 'inventory', 'order', 'shipment')
$expectedFileCounts = @{ auth = 5; product = 2; inventory = 3; order = 2; shipment = 6 }
$seenModules = @{}
$seenArchivePaths = @{}
$seenOriginalPaths = @{}
foreach ($module in $manifest.modules) {
    if ($module.name -notin $requiredModules) { Add-CheckError "Unknown archive module '$($module.name)'."; continue }
    if ($seenModules.ContainsKey($module.name)) { Add-CheckError "Duplicate module '$($module.name)'." }
    $seenModules[$module.name] = $true
    if ($module.date -notmatch '^\d{8}$') { Add-CheckError "Invalid freeze date for $($module.name)." }
    $expectedDirectory = "docs/archive/$($module.date)-$($module.name)"
    if ($module.directory -ne $expectedDirectory) { Add-CheckError "Unexpected archive directory for $($module.name): $($module.directory)." }
}
foreach ($moduleName in $requiredModules) {
    if (-not $seenModules.ContainsKey($moduleName)) { Add-CheckError "Missing archive module '$moduleName'." }
}

foreach ($entry in $manifest.files) {
    $originalPath = [string]$entry.originalPath
    $archivePath = [string]$entry.archivePath
    $moduleName = [string]$entry.module
    if ($moduleName -notin $requiredModules) { Add-CheckError "Unknown file module '$moduleName' for '$archivePath'."; continue }
    $module = $manifest.modules | Where-Object { $_.name -eq $moduleName } | Select-Object -First 1
    if ($null -eq $module) { Add-CheckError "Missing module record for '$moduleName'."; continue }
    $expectedSourcePrefix = "api/docs/$moduleName/"
    $expectedArchivePrefix = "$($module.directory)/"
    if (-not $originalPath.StartsWith($expectedSourcePrefix, [StringComparison]::Ordinal) -or $originalPath.Contains('..')) {
        Add-CheckError "Source path is outside module '$moduleName': '$originalPath'."
    }
    if (-not $archivePath.StartsWith($expectedArchivePrefix, [StringComparison]::Ordinal) -or $archivePath.Contains('..')) {
        Add-CheckError "Archive path is outside module '$moduleName': '$archivePath'."
    }
    if ([string]::IsNullOrWhiteSpace($entry.sourceBlob)) { Add-CheckError "Missing Git source blob for '$originalPath'." }
    if ([string]::IsNullOrWhiteSpace($entry.originalHash)) { Add-CheckError "Missing original hash for '$originalPath'." }
    if ([string]::IsNullOrWhiteSpace($entry.frozenHash)) { Add-CheckError "Missing frozen hash for '$archivePath'." }
    if ($seenOriginalPaths.ContainsKey($originalPath)) { Add-CheckError "Duplicate original path '$originalPath'." }
    if ($seenArchivePaths.ContainsKey($archivePath)) { Add-CheckError "Duplicate archive path '$archivePath'." }
    $seenOriginalPaths[$originalPath] = $true
    $seenArchivePaths[$archivePath] = $true

    $sourceObject = Get-GitText @('rev-parse', "$($manifest.sourceCommit):$originalPath")
    if ($LASTEXITCODE -ne 0 -or $sourceObject -ne $entry.sourceBlob -or $entry.originalHash -ne $entry.sourceBlob) {
        Add-CheckError "Git source does not match manifest for '$originalPath'."
    }
    $targetPath = Join-Path $Root ($archivePath.Replace('/', '\'))
    if (-not (Test-Path -LiteralPath $targetPath -PathType Leaf)) {
        Add-CheckError "Frozen file is missing: $archivePath"
        continue
    }
    if ((Get-Sha256 $targetPath) -ne $entry.frozenHash) { Add-CheckError "Frozen hash changed: $archivePath" }
}

foreach ($moduleName in $requiredModules) {
    $moduleEntries = @($manifest.files | Where-Object { $_.module -eq $moduleName })
    if ($moduleEntries.Count -ne $expectedFileCounts[$moduleName]) {
        Add-CheckError "Unexpected frozen file count for '$moduleName': $($moduleEntries.Count)."
    }
    $moduleRecord = $manifest.modules | Where-Object { $_.name -eq $moduleName } | Select-Object -First 1
    if ($null -eq $moduleRecord) { continue }
    $expectedPaths = @($moduleEntries | ForEach-Object { [string]$_.archivePath } | Sort-Object)
    $actualDirectory = Join-Path $Root ($moduleRecord.directory.Replace('/', '\'))
    if (Test-Path -LiteralPath $actualDirectory -PathType Container) {
        $actualPaths = @(Get-ChildItem -LiteralPath $actualDirectory -File -Recurse | ForEach-Object {
            $_.FullName.Substring($Root.Length + 1).Replace('\', '/')
        } | Sort-Object)
        if (Compare-Object -ReferenceObject $expectedPaths -DifferenceObject $actualPaths) {
            Add-CheckError "Archive directory contents do not match the manifest for '$moduleName'."
        }
    }
}

foreach ($module in $manifest.modules) {
    $readme = Join-Path $Root ($module.directory.Replace('/', '\') + '\README.md')
    if (-not (Test-Path -LiteralPath $readme -PathType Leaf)) { Add-CheckError "Archive README is missing: $($module.directory)/README.md"; continue }
    $content = Get-Content -LiteralPath $readme -Raw
    foreach ($entry in $manifest.files | Where-Object { $_.module -eq $module.name }) {
        $name = [System.IO.Path]::GetFileName([string]$entry.archivePath)
        if ($name -ne 'README.md' -and $content -notmatch [regex]::Escape("]($name)")) {
            Add-CheckError "README does not link to $($entry.archivePath)."
        }
    }
    $markdownLinks = [regex]::Matches($content, '\[[^\]]+\]\(([^)#]+)(?:#[^)]*)?\)')
    foreach ($link in $markdownLinks) {
        $relativeTarget = $link.Groups[1].Value
        if ($relativeTarget -match '^(https?:|mailto:|#)' ) { continue }
        $resolved = [System.IO.Path]::GetFullPath((Join-Path (Split-Path $readme) $relativeTarget))
        if (-not (Test-Path -LiteralPath $resolved)) { Add-CheckError "Broken README link in $($module.directory)/README.md: $relativeTarget" }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    exit 1
}
if (-not $Quiet) { Write-Output "Maintenance check passed: $($manifest.files.Count) frozen files across $($manifest.modules.Count) modules." }
