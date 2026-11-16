param(
    [ValidateSet('Check', 'Execute')][string]$Mode = 'Check',
    [Parameter(Mandatory = $true)][string]$ReportDirectory,
    [string]$DockerExecutable = 'docker',
    [switch]$MaintenanceConfirmed
)
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$moduleRoot = Join-Path $repositoryRoot 'api'
$reportRoot = [IO.Path]::GetFullPath($ReportDirectory)
$backupRoot = [IO.Path]::GetFullPath((Join-Path $repositoryRoot 'backups'))
if (-not $reportRoot.StartsWith($backupRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'ReportDirectory must be a dedicated child of the ignored repository backups directory'
}
function Docker-Checked([string[]]$Arguments) {
    $result = & $DockerExecutable @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'Docker operation failed; stop and retain maintenance evidence' }
    return $result
}
function Run-Tool([string]$Operation) {
    & java '-DLOG_DIR=target/test-logs' -cp $script:toolClasspath com.gba.merchantbao.maintenance.DevSeedUpgrade $Operation $reportRoot
    if ($LASTEXITCODE -ne 0) { throw 'Seed maintenance stopped; retain backup/reports; do not start old migration package' }
}
$seedVariables = @('DEV_SEED_JDBC_URL','DEV_SEED_DB_USER','DEV_SEED_DB_PASSWORD','DEV_SEED_EXPECTED_DATABASE','DEV_SEED_BACKUP_FILE','DEV_SEED_MAINTENANCE_CONFIRMED','MYSQL_ROOT_PASSWORD')
$savedEnvironment = @{}
foreach ($key in $seedVariables) { $savedEnvironment[$key] = [Environment]::GetEnvironmentVariable($key, 'Process') }
$rehearsalName = $null
Push-Location $repositoryRoot
try {
    $containerId = (Docker-Checked @('compose','ps','-q','mysql') | Out-String).Trim()
    if (-not $containerId -or $containerId -match '\s') { throw 'Exactly one running root Compose MySQL is required' }
    $container = (Docker-Checked @('inspect',$containerId) | ConvertFrom-Json)[0]
    if ($container.Config.Labels.'com.docker.compose.project' -ne 'gba-dev' -or
        $container.Config.Labels.'com.docker.compose.service' -ne 'mysql' -or
        $container.Config.Image -ne 'mysql:8.0.34' -or
        @($container.Mounts | Where-Object { $_.Name -eq 'gba-dev_mysql_data' }).Count -ne 1) {
        throw 'Target must be the existing gba-dev mysql:8.0.34 service and its expected volume'
    }
    $containerEnvironment = @{}
    foreach ($entry in $container.Config.Env) {
        $separator = $entry.IndexOf('=')
        $containerEnvironment[$entry.Substring(0,$separator)] = $entry.Substring($separator+1)
    }
    if ($containerEnvironment.MYSQL_DATABASE -ne 'gba_dev') { throw 'Expected database gba_dev; custom targets require a reviewed tool change' }
    $bindings = @($container.NetworkSettings.Ports.'3306/tcp')
    if ($bindings.Count -ne 1 -or $bindings[0].HostIp -ne '127.0.0.1') { throw 'Expected one loopback-only MySQL binding' }
    $env:DEV_SEED_EXPECTED_DATABASE = 'gba_dev'
    $env:DEV_SEED_JDBC_URL = "jdbc:mysql://127.0.0.1:$($bindings[0].HostPort)/gba_dev?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
    # Use the dev database account; do not expose passwords in process arguments or output.
    $env:DEV_SEED_DB_USER = $containerEnvironment.MYSQL_USER
    $env:DEV_SEED_DB_PASSWORD = $containerEnvironment.MYSQL_PASSWORD
    New-Item -ItemType Directory -Path $reportRoot -Force | Out-Null
    Push-Location $moduleRoot
    try {
        # Maven does not remove renamed resources. Remove only these exact obsolete build outputs.
        foreach ($relative in @('db/seed/nonprod/R__local_test_tenants.sql','db/devdata/R__auth_demo_accounts.sql',
            'db/migration/V6__shipment_tables.sql','db/migration/V7__order_tables.sql',
            'db/migration/V8__inventory_tables.sql','db/migration/V9__product_module.sql',
            'db/devdata/R__040_shipment_demo_data.sql','db/devdata/R__050_order_demo_orders.sql',
            'db/devdata/R__060_product_demo.sql',
            'com/gba/merchantbao/auth/config/AuthDevDataConfiguration.class','com/gba/merchantbao/auth/config/AuthDevDataConfiguration$1.class')) {
            $buildClasses = [IO.Path]::GetFullPath((Join-Path $moduleRoot 'target/classes'))
            $stalePath = [IO.Path]::GetFullPath((Join-Path $buildClasses $relative))
            if (-not $stalePath.StartsWith($buildClasses + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid build cleanup target' }
            if (Test-Path -LiteralPath $stalePath) { Remove-Item -LiteralPath $stalePath -Force }
        }
        & mvn -q -DskipTests compile dependency:build-classpath '-Dmdep.outputFile=target/dev-seed-classpath.txt'
        if ($LASTEXITCODE -ne 0) { throw 'Maintenance tool build failed' }
        $script:toolClasspath = (Join-Path $moduleRoot 'target/classes') + [IO.Path]::PathSeparator + [IO.File]::ReadAllText((Join-Path $moduleRoot 'target/dev-seed-classpath.txt')).Trim()
    } finally { Pop-Location }
    $env:DEV_SEED_BACKUP_FILE = Join-Path $reportRoot 'before.sql'
    if ($Mode -eq 'Check') {
        if (Test-Path -LiteralPath (Join-Path $reportRoot 'review.json')) { throw 'Use a new report directory; existing evidence is never overwritten by Check' }
        Run-Tool 'check'
        $backupInContainer = '/tmp/gba-dev-seed-' + [Guid]::NewGuid().ToString('N') + '.sql'
        try {
            Docker-Checked @('exec',$containerId,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --events --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE" --result-file="$1"','sh',$backupInContainer) | Out-Null
            Docker-Checked @('cp',"${containerId}:$backupInContainer",$env:DEV_SEED_BACKUP_FILE) | Out-Null
        } finally { Docker-Checked @('exec',$containerId,'rm','-f',$backupInContainer) | Out-Null }
        if ((Get-Item -LiteralPath $env:DEV_SEED_BACKUP_FILE).Length -eq 0) { throw 'Empty backup' }
        $rehearsalName = 'gba-seed-restore-' + [Guid]::NewGuid().ToString('N')
        $restoreDatabase = 'gba_seed_it_' + [Guid]::NewGuid().ToString('N')
        $env:MYSQL_ROOT_PASSWORD = [Guid]::NewGuid().ToString('N') + [Guid]::NewGuid().ToString('N')
        Docker-Checked @('run','-d','--name',$rehearsalName,'--label','gba.seed-upgrade.rehearsal=true',
            '-e','MYSQL_ROOT_PASSWORD','-e','MYSQL_ROOT_HOST=%','-e',"MYSQL_DATABASE=$restoreDatabase",'-p','127.0.0.1::3306','mysql:8.0.34') | Out-Null
        $ready = $false
        for ($attempt = 0; $attempt -lt 90; $attempt++) {
            & $DockerExecutable exec $rehearsalName sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h127.0.0.1 -e "SELECT 1" >/dev/null 2>&1' 2>$null
            if ($LASTEXITCODE -eq 0) { $ready = $true; break }
            Start-Sleep -Seconds 1
        }
        if (-not $ready) { throw 'Temporary restore MySQL did not become ready' }
        Docker-Checked @('cp',$env:DEV_SEED_BACKUP_FILE,"${rehearsalName}:/tmp/before.sql") | Out-Null
        Docker-Checked @('exec',$rehearsalName,'sh','-c','MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot "$MYSQL_DATABASE" < /tmp/before.sql') | Out-Null
        $restore = (Docker-Checked @('inspect',$rehearsalName) | ConvertFrom-Json)[0]
        $sourceUrl = $env:DEV_SEED_JDBC_URL; $sourceUser = $env:DEV_SEED_DB_USER; $sourcePassword = $env:DEV_SEED_DB_PASSWORD
        try {
            $env:DEV_SEED_EXPECTED_DATABASE = $restoreDatabase
            $env:DEV_SEED_JDBC_URL = "jdbc:mysql://127.0.0.1:$($restore.NetworkSettings.Ports.'3306/tcp'[0].HostPort)/${restoreDatabase}?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=UTC"
            $env:DEV_SEED_DB_USER = 'root'; $env:DEV_SEED_DB_PASSWORD = $env:MYSQL_ROOT_PASSWORD
            Run-Tool 'verify-restore'
        } finally {
            $env:DEV_SEED_EXPECTED_DATABASE = 'gba_dev'; $env:DEV_SEED_JDBC_URL = $sourceUrl
            $env:DEV_SEED_DB_USER = $sourceUser; $env:DEV_SEED_DB_PASSWORD = $sourcePassword
        }
        Write-Output "CHECK AND RESTORE PASSED. Review $reportRoot before Execute. No source database migration performed."
    } else {
        if (-not $MaintenanceConfirmed) { throw 'Execute requires -MaintenanceConfirmed after report review and stopping same-database applications' }
        $env:DEV_SEED_MAINTENANCE_CONFIRMED = 'true'
        Run-Tool 'execute'
    }
} finally {
    if ($rehearsalName) {
        $temporary = (& $DockerExecutable inspect $rehearsalName 2>$null | ConvertFrom-Json)[0]
        if ($temporary.Config.Labels.'gba.seed-upgrade.rehearsal' -eq 'true' -and $temporary.Name -eq "/$rehearsalName") {
            & $DockerExecutable rm -f -v $rehearsalName | Out-Null
        }
    }
    foreach ($key in $seedVariables) { [Environment]::SetEnvironmentVariable($key,$savedEnvironment[$key],'Process') }
    Pop-Location
}
