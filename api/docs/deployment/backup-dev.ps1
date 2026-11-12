# Compatibility entry for the protected production manual; implementation lives in scripts/dev.
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot '../../scripts/dev/backup-dev.ps1') @args
exit $LASTEXITCODE
