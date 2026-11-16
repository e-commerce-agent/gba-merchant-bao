$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../../..')).Path
Push-Location $repositoryRoot
try {
    mvn -f api/pom.xml clean verify
    if ($LASTEXITCODE -ne 0) { throw '验证失败，停止发布' }
    $productionRoot = 'api/docs/prod-profile/opt/gba'
    # 先在实际 .env 中设置本次 APP_VERSION，再上传。
    scp "$productionRoot/docker-compose.yml" root@ECS_IP:/opt/gba/
    if ($LASTEXITCODE -ne 0) { throw '上传生产配置失败' }
    scp "$productionRoot/.env" root@ECS_IP:/opt/gba/
    if ($LASTEXITCODE -ne 0) { throw '上传生产环境文件失败' }
    scp "$productionRoot/docker/mysql/init/01-create-users.sh" root@ECS_IP:/opt/gba/docker/mysql/init/
    if ($LASTEXITCODE -ne 0) { throw '上传初始化脚本失败' }
    scp "$productionRoot/app/Dockerfile" root@ECS_IP:/opt/gba/app/
    if ($LASTEXITCODE -ne 0) { throw '上传 Dockerfile 失败' }
    scp "$productionRoot/app/.dockerignore" root@ECS_IP:/opt/gba/app/
    if ($LASTEXITCODE -ne 0) { throw '上传构建定义失败' }
    scp api/target/gba-merchant-bao-1.0-SNAPSHOT.jar root@ECS_IP:/opt/gba/app/app.jar
    if ($LASTEXITCODE -ne 0) { throw '上传 JAR 失败' }
    scp -r api/src/main/resources/db/migration/. root@ECS_IP:/opt/gba/db/migration/
    if ($LASTEXITCODE -ne 0) { throw '上传 migration 失败' }
    scp -r api/src/main/resources/db/seed/common/. root@ECS_IP:/opt/gba/db/seed/common/
    if ($LASTEXITCODE -ne 0) { throw '上传 common seed 失败' }
} finally {
    Pop-Location
}
