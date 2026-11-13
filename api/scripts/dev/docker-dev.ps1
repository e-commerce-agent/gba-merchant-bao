$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Push-Location $repositoryRoot
try {
    docker compose config --quiet
    if ($LASTEXITCODE -ne 0) { throw 'Docker compose 配置失败' }
    docker compose up -d --wait
    docker compose ps
} finally {
    Pop-Location
}
