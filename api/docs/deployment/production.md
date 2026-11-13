# 生产配置与 ECS 手动运行

当前只完成配置准备和隔离测试，未在 ECS 上线。下文是未来操作步骤，命令中的 ECS_IP 要替换为目标地址。

## 1. 本机配置位置

生产文件只维护在 `api/docs/prod-profile/opt/gba/`，不再保留 deployment/examples/prod 副本。

| 文件 | Git | 用途 |
| --- | --- | --- |
| docker-compose.yml、.env.example | 提交 | 无实际凭据的生产定义和环境变量清单 |
| docker/mysql/init/01-create-users.sh | 提交 | MySQL 空卷首次初始化账号 |
| app/Dockerfile、app/.dockerignore | 提交 | 将 Java 21 JAR 构建进镜像 |
| .env | 忽略 | 本机维护、上传到 ECS 的实际配置 |
| app/app.jar、db/、backups/ | 忽略 | 未来发布产物和本机数据，不在当前准备阶段填充 |

dev/prod YAML 占位示例位于 `api/src/main/resources/application-*.yml.example`；运行时的 application-prod.yml 本身只含环境占位符，可随 JAR 打包。

新克隆的开发者从仓库根执行一次；已有 .env 不要覆盖：

```powershell
$productionRoot = 'api/docs/prod-profile/opt/gba'
if (-not (Test-Path "$productionRoot/.env")) {
    Copy-Item "$productionRoot/.env.example" "$productionRoot/.env"
}
# 填写实际值后校验。此操作不启动服务。
docker compose -f "$productionRoot/docker-compose.yml" --env-file "$productionRoot/.env" --profile deploy config --quiet
```

实际 .env 中 MYSQL_ROOT_PASSWORD、FLYWAY_PASSWORD、APP_DB_PASSWORD、REDIS_PASSWORD 按当前约定初始均填 `123456`。JWT 的 ADMIN_JWT_SECRET / USER_JWT_SECRET 分别填写两次 `openssl rand -base64 32` 生成的独立值（各解码至少 32 字节）；ADMIN 指 MERCHANT_STAFF，与 MySQL root 无关。prod 不回落 dev 密钥；缺失、占位、非法编码、过短或相同都会启动失败，更换后新旧会话需重新登录。示例保留占位符；实际 .env 继续忽略。APP_VERSION 使用本次唯一版本，避免覆盖旧镜像，未来实际对外上线前处理凭据轮换。

MySQL 空卷首次创建库 gba、flyway_deploy（仅 gba.* 迁移权限）和 app（仅 SELECT/INSERT/UPDATE/DELETE）。已有数据卷不会重跑 init，也不会因更改 .env 自动修改 MySQL 账号；需管理员单独变更并同步配置。
Redis 开启 AOF 和 requirepass，健康检查 REDISCLI_AUTH、app 读取同一 REDIS_PASSWORD。中间件不发布主机端口，应用连接 mysql:3306、redis:6379。

初始化脚本保持 LF 和可读的 0644 模式，官方 MySQL entrypoint 会 source 它。**不要执行 `chmod +x docker/mysql/init/01-create-users.sh`**：脚本使用 entrypoint 提供的 docker_process_sql 函数，设为可执行后会改走独立执行分支。若曾执行过 +x，在 ECS 用 `chmod 644 docker/mysql/init/01-create-users.sh` 恢复；这与 .env 权限无关。

prod app 激活 Spring prod、禁用启动 Flyway，只使用 DML 账号；独立 Flyway CLI 11.7.2 只使用迁移账号，加载 migration/common。Dockerfile 仅 COPY JAR，构建上下文 ./app，不挂载运行期 JAR，不把 .env 放进镜像。

## 2. 首次上线时上传

先在 ECS 终端创建目录（Linux shell）：

```sh
mkdir -p /opt/gba/docker/mysql/init /opt/gba/app /opt/gba/db/migration \
  /opt/gba/db/seed/common /opt/gba/logs/app /opt/gba/backups/mysql
```

开发电脑 PowerShell 建议始终保持在仓库根；这样每条路径都以仓库为基准，避免在生产准备目录和源码目录之间切换。当前无需复制 JAR/SQL 到本地准备目录，未来直接逐项上传同一版本构建结果和源码 SQL：

```powershell
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
```

上传结果应为 `/opt/gba/db/migration/V*__*.sql`、`/opt/gba/db/seed/common/`，不要形成 migration/migration 或 common/common。只上传 migration/common，不上传 nonprod/devdata/fixture，不改写已应用迁移。

也可以从 `api/docs/prod-profile/opt/gba` 目录逐条执行前五条上传命令，再返回仓库根执行 JAR 和 SQL 上传；但发布手册统一以仓库根为工作目录，降低相对路径出错的概率。实际 `.env` 必须上传，`.env.example` 只用于生成本机配置，不能替代生产环境文件。

ECS 首次空库发布，在 /opt/gba 执行成功链：

```sh
cd /opt/gba
docker compose config --quiet &&
docker compose up -d --wait mysql redis &&
docker compose --profile deploy build app &&
docker compose --profile deploy run --rm migrate &&
docker compose --profile deploy up -d --no-build app
```

这里的 deploy 是 Compose profile。任何一步失败都不继续；空库从 V1 开始，已有旧库先执行 [核验与显式接管（本目录入口）](README.md#flyway-baseline)，不得直接套首次空库流程。
检查 `docker compose --profile deploy ps`、`docker compose logs --tail=100 app`、Flyway history，以及 `curl -fsS http://127.0.0.1:8080/api/v1/health`（端口随 APP_PORT 调整）。up 成功只说明容器已启动，健康接口通过后才完成验收。

## 3. 上线后按场景操作

以下都先 `cd /opt/gba`，使用已发布版本对应的 .env、镜像和 SQL。**仅打开 SSH 终端不需要启动命令**，先查看状态：

```sh
docker compose --profile deploy ps
```

| 场景 | 命令或流程 | 是否 build/migrate |
| --- | --- | --- |
| 服务正常，只是第二次/第三次登录 ECS | ps，必要时 logs | 都不需要 |
| app 已存在，只想重启进程，配置未改 | `docker compose --profile deploy restart app` | 都不需要 |
| 整机重启，服务尚未恢复，或之前 down 过 | 使用下方“恢复原版本” | 都不需要 |
| 只改 app 环境变量，数据库结构未变 | 使用下方“配置生效” | 都不需要 |
| 更新 JAR、Dockerfile 或数据库迁移 | 使用第 4 节完整更新流程 | build；发布时 migrate 校验并应用待执行项 |
| 修改 MySQL/Redis 密码或初始化脚本 | 单独安排维护，核对持久账号/客户端配置 | 不能仅靠 restart 改账号，也不删卷 |

恢复原版本：restart: unless-stopped 通常会在 Docker 引擎启动后恢复服务；若曾手动停止或容器已删除，用已有本机镜像启动，不重建镜像、不重新迁移：

```sh
docker compose config --quiet &&
docker compose up -d --wait mysql redis &&
docker compose --profile deploy up -d --no-build app
```

如果旧镜像已被清理，--no-build 会失败，按第 4 节重新准备对应版本产物，不静默构建不同版本。

只让 app 配置生效（例如 .env 中 JWT 配置变化，或与服务器已完成的凭据变更同步），保留 APP_VERSION；restart 不重新读取环境配置，使用：

```sh
docker compose config --quiet &&
docker compose --profile deploy up -d --no-build --no-deps --force-recreate app
```

不借此自动重启数据库；若配置涉及中间件账号、连接地址，先完成并核验对应维护。变更需重建镜像时走更新发布流程。

## 4. 第二次及后续更新发布

每次发布先在开发电脑完成同版本 clean verify；更新唯一 APP_VERSION，按第 2 节从仓库根上传 JAR、完整且不可变的 migration 和 common seed，按需上传已变更的 Compose/Dockerfile/.env。SQL 已执行的部分会校验后跳过，无新增项的 migrate 仅校验，不会重新执行 V1/V2/V3。

ECS 先保留旧镜像版本及上一版配置，完成新版镜像构建再进入维护：

```sh
cd /opt/gba
docker compose config --quiet &&
docker compose up -d --wait mysql redis &&
docker compose --profile deploy build app
```

构建成功后，停止外部写入入口和 app：

```sh
docker compose --profile deploy stop app
```

执行 [备份恢复手册（本目录入口）](README.md#backup-recovery) 中结构＋数据完整导出、文件核验和笔记本异机保存。备份失败则停止发布，不自动启动新版。确认迁移风险和备份后：

```sh
docker compose --profile deploy run --rm migrate &&
docker compose --profile deploy up -d --no-build app
```

健康接口、日志、history 和必要业务验收通过才解除维护。此为单实例维护窗口发布，不承诺无停机；若需更新中间件镜像或账号，应将停写和备份前置到中间件变更前，不能直接套上述应用更新步骤。

不要使用省略备份的“一条 SSH 命令”执行已有数据的结构升级。构建或迁移失败不继续下一步；应用回退先核验旧镜像与当前结构兼容性，数据库回退走隔离恢复或评审后的向前修复。正常流程不执行 baseline/clean/repair，不用 down -v。

这套方式是 ECS 本机 Dockerfile 构建；镜像仓库、CI/tag 发布和 RDS 留待后续 change。
