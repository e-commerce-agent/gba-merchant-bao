[CmdletBinding()]
param(
    [string]$RuntimePath,
    [string]$BackupRoot,
    [string]$BatchName,
    [switch]$Execute
)

$ErrorActionPreference = 'Stop'

function Get-FullPath([string]$Path) {
    return [IO.Path]::GetFullPath($Path)
}

function Test-Within([string]$Child, [string]$Parent) {
    $childPath = (Get-FullPath $Child).TrimEnd('\') + '\'
    $parentPath = (Get-FullPath $Parent).TrimEnd('\') + '\'
    return $childPath.StartsWith($parentPath, [StringComparison]::OrdinalIgnoreCase)
}

function Assert-NoReparsePoint([string]$Path, [string]$Label) {
    $current = Get-FullPath $Path
    while ($true) {
        if (Test-Path -LiteralPath $current) {
            $item = Get-Item -LiteralPath $current -Force
            if (($item.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                throw "$Label contains a reparse point: $current"
            }
        }
        $parent = [IO.Directory]::GetParent($current)
        $parent = if ($null -eq $parent) { $null } else { $parent.FullName }
        if ([string]::IsNullOrEmpty($parent) -or $parent -eq $current) { break }
        $current = $parent
    }
}

function Test-FileAvailable([string]$Path) {
    $stream = $null
    try {
        $stream = [IO.File]::Open($Path, [IO.FileMode]::Open, [IO.FileAccess]::ReadWrite, [IO.FileShare]::None)
        return $true
    } catch {
        return $false
    } finally {
        if ($null -ne $stream) { $stream.Dispose() }
    }
}

function Get-Category([IO.FileInfo]$File) {
    $relative = $File.FullName.Substring($script:runtime.Length).TrimStart('\','/')
    $name = $File.Name
    if ($relative -like 'legacy-backup\*' -or $relative -like 'legacy-backup/*') { return 'legacy-backup' }
    if ($name -match '\.(sql|sql\.gz)$' -or $name -match '(?i)(backup|dump)') { return 'sql-or-backup' }
    if ($name -match '\.(java|class|mjs|cjs|py|ps1)$') { return 'one-off-code' }
    if (($relative -like 'logs\*' -or $relative -like 'logs/*') -and $name -match '(?i)\.json\.gz$') { return 'historical-log' }
    if ($name -match '\.(log|log\.gz)$') { return 'historical-log' }
    if ($relative -match '(?i)(^|[\\/])logs[\\/].+\.log\.gz$') { return 'historical-log' }
    return $null
}

if ([string]::IsNullOrWhiteSpace($RuntimePath)) {
    $RuntimePath = Join-Path (Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent) 'api/.runtime'
}
$script:runtime = Get-FullPath $RuntimePath
if (-not (Test-Path -LiteralPath $script:runtime -PathType Container)) { throw "Runtime directory does not exist: $script:runtime" }
Assert-NoReparsePoint $script:runtime 'Runtime path'

if ([string]::IsNullOrWhiteSpace($BackupRoot)) {
    $repoRoot = Split-Path (Split-Path (Split-Path $PSScriptRoot -Parent) -Parent) -Parent
    $BackupRoot = Join-Path $repoRoot ("backups/{0}-backend-maintenance" -f (Get-Date -Format 'yyyyMMdd'))
}
$backupRootFull = Get-FullPath $BackupRoot
Assert-NoReparsePoint $backupRootFull 'Backup path'
if (Test-Within $backupRootFull $script:runtime) { throw 'Backup root must not be inside runtime' }

if ([string]::IsNullOrWhiteSpace($BatchName)) {
    $BatchName = Get-Date -Format 'HHmmssfff'
}
if ($BatchName -match '[\\/:*?"<>|]' -or $BatchName -in @('.', '..')) { throw "Invalid batch name: $BatchName" }
$batchDir = Join-Path $backupRootFull $BatchName
if (-not (Test-Within $batchDir $backupRootFull)) { throw 'Batch path escapes backup root' }
if ((Test-Path -LiteralPath $batchDir) -and ((Get-Item -LiteralPath $batchDir -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Batch directory is a reparse point' }
$manifestPath = Join-Path $batchDir 'manifest.json'
if ($Execute -and (Test-Path -LiteralPath $manifestPath)) {
    Write-Output "[already-processed] Existing manifest is preserved: $manifestPath"
    return
}

$allFiles = @()
$linkedDirs = @()
$pendingDirs = New-Object 'System.Collections.Generic.Stack[string]'
$pendingDirs.Push($script:runtime)
while ($pendingDirs.Count -gt 0) {
    $directory = $pendingDirs.Pop()
    foreach ($child in Get-ChildItem -LiteralPath $directory -Force) {
        if (($child.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
            if ($child.PSIsContainer) { $linkedDirs += $child.FullName }
            continue
        }
        if ($child.PSIsContainer) { $pendingDirs.Push($child.FullName) }
        else { $allFiles += $child }
    }
}
$allFiles = $allFiles | Where-Object {
    $_.FullName -notmatch '(?i)[\\/]logs[\\/]application\.json$' -and
    $_.FullName -notmatch '(?i)[\\/]logs[\\/]audit\.json$' -and
    $_.Name -notmatch '(?i)^flyway-legacy\.env$' -and
    $_.Name -notmatch '(?i)^flyway-cli-rehearsal\.env$' -and
    $_.Extension -notmatch '(?i)^\.stop$'
}
$entries = @()
foreach ($file in $allFiles) {
    $category = Get-Category $file
    $relative = $file.FullName.Substring($script:runtime.Length).TrimStart('\','/')
    $destination = Join-Path $batchDir $relative
    if (-not (Test-Within $file.FullName $script:runtime) -or -not (Test-Within $destination $batchDir)) { throw "Path escaped its approved boundary: $relative" }
    $action = if ($null -eq $category) { 'retain-unknown' } elseif (-not $Execute) { 'preview' } else { 'pending' }
    $reason = if ($null -eq $category) { 'Unclassified runtime file; retained for review' } elseif ($relative -like 'logs\*' -or $relative -like 'logs/*') { 'Rotated historical log' } else { $category }
    $entry = [ordered]@{ source=$file.FullName; relativePath=$relative; destination=$destination; category=$category; action=$action; reason=$reason; sourceSha256=$null; destinationSha256=$null; status=$null }
    if ($null -ne $category) {
        if (-not (Test-FileAvailable $file.FullName)) {
            $entry.status = 'retained-occupied'
        } else {
            $entry.sourceSha256 = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash
            if ($Execute) {
                if (Test-Path -LiteralPath $destination) {
                Assert-NoReparsePoint $destination 'Backup destination'
                $destItem = Get-Item -LiteralPath $destination -Force
                if (($destItem.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) {
                    $entry.status = 'retained-destination-reparse-point'
                } else {
                    $existingHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
                    if ($existingHash -eq $entry.sourceSha256) {
                        Remove-Item -LiteralPath $file.FullName -Force
                        $entry.destinationSha256 = $existingHash
                        $entry.status = 'already-backed-up-removed-source'
                    } else {
                        $entry.status = 'retained-destination-collision'
                    }
                }
            } else {
                $parentInfo = [IO.Directory]::GetParent($destination)
                $parent = $parentInfo.FullName
                New-Item -ItemType Directory -Path $parent -Force | Out-Null
                Assert-NoReparsePoint $destination 'Backup destination'
                Copy-Item -LiteralPath $file.FullName -Destination $destination -Force:$false
                $copiedHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA256).Hash
                if ($copiedHash -ne $entry.sourceSha256) {
                    Remove-Item -LiteralPath $destination -Force -ErrorAction SilentlyContinue
                    throw "Hash verification failed for $relative"
                }
                Remove-Item -LiteralPath $file.FullName -Force
                $entry.destinationSha256 = $copiedHash
                $entry.status = 'moved-and-verified'
                }
            }
        }
    } else {
        $entry.status = 'retained-unknown'
    }
    $entries += [pscustomobject]$entry
    $verb = if ($Execute) { $entry.status } else { $action }
    Write-Output ("[{0}] {1} -> {2}" -f $verb, $relative, $destination)
}

$retained = @(
    'logs/application.json', 'logs/audit.json', 'flyway-legacy.env',
    'flyway-cli-rehearsal.env', 'agent-apifox.stop',
    'manual/agent/agent-apifox.stop'
) | Where-Object { Test-Path -LiteralPath (Join-Path $script:runtime $_) }
foreach ($path in $retained) { Write-Output ("[retained-active-or-compat] {0}" -f $path) }
foreach ($path in $linkedDirs) { Write-Output ("[retained-reparse-point] {0}" -f $path) }

if ($Execute) {
    New-Item -ItemType Directory -Path $batchDir -Force | Out-Null
    $manifest = [ordered]@{ generatedAt=(Get-Date).ToString('o'); runtime=$script:runtime; batch=$batchDir; entries=$entries; retained=$retained }
    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $manifestPath -Encoding UTF8 -NoNewline
    Write-Output ("Manifest: {0}" -f $manifestPath)
}
