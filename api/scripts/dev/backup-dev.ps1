param([string]$DockerExecutable = 'docker')
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
$backupDirectory = Join-Path $repositoryRoot 'backups'
New-Item -ItemType Directory -Path $backupDirectory -Force | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
$destination = Join-Path $backupDirectory "gba_dev-data-$stamp.sql"
$containerFile = "/tmp/gba_dev-data-$stamp.sql"
Push-Location $repositoryRoot
try {
    $container = & $DockerExecutable compose ps -q mysql
    if ($LASTEXITCODE -ne 0 -or -not $container) { throw 'dev MySQL 未运行' }
    & $DockerExecutable compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysqldump -u"$MYSQL_USER" --single-transaction --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE" --result-file="$1"' sh $containerFile
    if ($LASTEXITCODE -ne 0) { throw 'MySQL 导出失败，不能将残留文件当成备份' }
    & $DockerExecutable cp "${container}:$containerFile" $destination
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $destination) -or (Get-Item -LiteralPath $destination).Length -eq 0) {
        throw '备份复制失败或为空'
    }
    Write-Output "结构＋数据备份完成：$destination"
} finally {
    # 只删除本次在容器 /tmp 生成的独立导出文件。
    & $DockerExecutable compose exec -T mysql rm -f $containerFile
    Pop-Location
}
