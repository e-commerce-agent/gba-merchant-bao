# 本地持久联调

开发环境统一叫 dev；test 专用于自动化测试，prod 用于未来生产。Spring profile 与 Compose 的 deploy profile 是两种概念。

## 首次配置（仓库根 PowerShell）

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
if (-not (Test-Path api/src/main/resources/application-dev.yml)) {
    Copy-Item api/src/main/resources/application-dev.yml.example api/src/main/resources/application-dev.yml
}
```

编辑被忽略的实际文件：根 `.env` 中 MYSQL_ROOT_PASSWORD、MYSQL_PASSWORD 初始均填 `123456`；dev YAML 的 merchant.datasource.password 同样填此值。
两处保持数据库 gba_dev、用户 dev_admin、MySQL 端口 3307、Redis 端口 6380 一致。端口被占用时，修改 .env 的 MYSQL_PORT/REDIS_PORT 并同步 YAML。
dev Redis 无密码；.env 不添加 REDIS_PASSWORD，YAML 不添加 Redis password。AOF 与命名卷负责持久化。
`.env` 只给 Compose 插值，Spring Boot 不会读取它；IDEA 不需要加载 .env 或注入额外环境变量。

日常启动只需要在仓库根执行下面的命令；`docker compose up` 会读取并解析 Compose 配置。

```powershell
docker compose up -d
docker compose ps
```

首次创建 Compose 文件、修改 Compose 配置或排查配置问题时，可以单独执行
`docker compose config --quiet`。它是配置检查命令，不是每天启动 dev 的前置步骤。

当前 dev Compose 只包含 MySQL 和 Redis，没有需要显式激活的 Compose profile。
登录会话复用现有 Redis，不需要 login profile 或额外 Redis 服务。后续引入其他中间件时，
按对应模块说明启动；不要使用 `docker compose --profile "*" up -d`。

## 本地 JWT 密钥（首次配置）

在本机 Git Bash 等有 OpenSSL 的终端运行两次，各生成一把独立密钥：

```bash
openssl rand -base64 32
openssl rand -base64 32
```

将结果分别写入被忽略的实际 `api/src/main/resources/application-dev.yml`，合并到已有
`merchant` 配置下，不要覆盖数据库等其他设置：

```yaml
merchant:
  auth:
    jwt:
      merchant-staff-secret-base64: <第一次生成的员工密钥>
      consumer-secret-base64: <第二次生成的消费者密钥>
```

员工指商户员工 MERCHANT_STAFF，与 MySQL root 或 dev_admin 无关。已有合规、不同的两把
密钥可以保留；示例占位符、非法 Base64、解码不足 32 字节或两把相同都会导致启动失败。
不要把真实值写回 `.example`、根 `.env`、Compose、截图或提交；test profile 的公开测试值
仅供自动化测试。prod 从 `ADMIN_JWT_SECRET` / `USER_JWT_SECRET` 注入独立 Base64 密钥，
其中 ADMIN 也是员工含义。旧 `merchant.jwt` 自动引用新配置，不再重复填写。

每次修改实际 dev YAML 后重启 IDEA 中的 API，才能加载配置；更换密钥后已有新旧 token
需要重新登录。JWT 密钥只由 API 使用：根 Compose 没有 app 服务，因此仅改 JWT 时不需要
Docker 命令，也不用改根 `.env`。本次 Base64 语义替代旧短口令补长，旧会话同样需重新登录。

IDEA 运行 MerchantApplication，JDK 21，工作目录设为仓库的 api，Active profiles 只填 dev。
也可从 api 目录运行：

```powershell
mvn spring-boot:run '-Dspring-boot.run.arguments=--spring.profiles.active=dev'
```

首次启动空库自动执行 V1→V9 和适用种子，无需 baseline。填好实际 dev YAML 后仅激活 dev 即可启动，无需个人旧库或云端 OSS/微信凭据；占位外部配置不表示相应业务可用。
前端及 Apifox 的 API base URL 为 http://localhost:8080，健康检查 GET /api/v1/health；文档 /swagger-ui.html、/v3/api-docs.yaml。
数据库工具连接 127.0.0.1:3307/gba_dev，dev_admin；Redis 工具连接 127.0.0.1:6380，无认证。前端调用 API，不直接连接数据库。
多个前端/后端开发者各自建立空库；共享联调如需 API 远程可达，单独约定主机地址。中间件保持 loopback。

## 日常启动和已有卷

```powershell
docker compose up -d
# 可选：等待健康检查
docker compose up -d --wait
# 停止时保留数据
docker compose down
```

已有 gba-dev 使用 `up -d` 会复用命名卷；它也会读取 `.env` 和 Compose，并在服务配置变化时
按需重建容器。`docker compose restart mysql redis` 只重启现有容器进程，不重新应用环境变量、
端口或挂载配置；仅在配置未变且需要重启进程时使用。以上操作都不要求重建或删除数据卷。

如果需要等待健康检查完成，可在确认配置后使用 `docker compose up -d --wait`；它是可选的
等待步骤。Docker Desktop 启动后，推荐顺序是：启动 Compose 中间件、在 IDEA 激活 Spring
`dev`、再启动后端。后端通过 `dev_admin` 连接数据库，Flyway 会自动执行待执行迁移，随后即可
进行断点调试。前端和 Apifox 使用同一 dev API，不连接 Testcontainers。

项目名 gba-dev，对应 gba-dev_mysql_data / gba-dev_redis_data，与 prod 的 gba 分离。
正常流程不使用 down -v。改变 .env 或初始化脚本不会修改已有 MySQL 卷账号；管理员连接正确实例显式 ALTER USER/授权，再同步实际 YAML 和 .env，不能删卷来改密码。
例：先用管理员会话执行 `ALTER USER 'dev_admin'@'%' IDENTIFIED BY '<新密码>';`，确认新连接可用再更新应用。不要把口令写到命令历史、日志或提交中。
改数据库名也不会自动为已有卷创建新库，需先规划迁移并由管理员处理。

持久化验收应在明确的 dev 目标内创建独立临时表/键，重启和重建后读取，最后仅定向清理自己的表/键；不要对联调业务表 TRUNCATE。
自动化集成测试使用动态端口的 Testcontainers，可与 dev 同时运行；它不挂载这些卷。

## 配置与凭据边界

根 .env、实际 dev YAML、backups/、api/.runtime/ 和实际生产 .env 均忽略 Git。
dev YAML 示例位于 `api/src/main/resources/application-dev.yml.example`，主资源 application-prod.yml 仅保留环境变量占位符，可以提交并进入 JAR。
生产文件统一维护在 `api/docs/prod-profile/opt/gba/`：Compose、.env.example、Dockerfile、.dockerignore 和账号初始化脚本可提交；实际 .env、app.jar、db/ 下的发布 SQL 和备份仍被忽略。`api/.gitignore` 按文件放行，不再维护 deployment/examples/prod 生产环境副本。
`api/docs/prod-profile/Docker开发和生产环境.txt` 是可提交的操作手稿。任何含实际凭据的额外配置不得放入放行列表。

```powershell
git check-ignore .env api/src/main/resources/application-dev.yml api/docs/prod-profile/opt/gba/.env backups/check.sql
git check-ignore api/docs/prod-profile/opt/gba/app/app.jar api/docs/prod-profile/opt/gba/db/migration/V1__legacy_schema_baseline.sql
# 上述两条命令应输出被忽略路径，发布产物即使存在也不进入 Git。
git check-ignore api/docs/prod-profile/opt/gba/docker-compose.yml api/docs/prod-profile/opt/gba/.env.example api/docs/prod-profile/opt/gba/app/Dockerfile api/docs/prod-profile/opt/gba/app/.dockerignore api/docs/prod-profile/opt/gba/docker/mysql/init/01-create-users.sh api/docs/prod-profile/Docker开发和生产环境.txt
# 最后一条预期无输出、退出码 1，说明这些文件可提交。
```

历史配置中曾出现的真实数据库、OSS、微信等凭据，应到各服务提供方轮换；删除当前文件中的旧值不会让历史凭据失效。
开发日志 api/.runtime/logs，Maven 测试只写 api/target/test-logs，详见 [日志（本目录入口）](README.md#logging)、[测试（本目录入口）](README.md#testing)。

## 种子改名后的启动

已执行旧名 R 的现有 Docker Compose 开发库，先完成一次 [开发种子受控升级（本目录入口）](README.md#dev-seed-upgrade)，保留命名卷与真实历史；新空库无需该维护。日常先在仓库根 `docker compose up -d --wait`，再在 IDEA 激活 dev 启动，或在 api 下 `mvn spring-boot:run '-Dspring-boot.run.profiles=dev'`。Flyway 自动执行待迁移，不自动 repair、不通过回调重跑种子。当前没有 Maven Wrapper，使用已安装 Maven。

R 仅首次或校验和变化时执行，显式补种见升级说明；不要靠重启恢复人工删除的演示数据。prod 保持独立迁移成功后启动关闭 Flyway 的应用，与本次开发库维护分离。

## V5 开发库升级到商品、库存、订单、物流 V9

本流程只适用于 `align-commerce-module-integration` 的原 V5 基线。先完成隔离迁移测试与
`mvn -f api/pom.xml clean verify`，再操作本地库。执行记录见
[电商整合验收（本目录入口）](README.md#testing-commerce-integration-verification)。

1. 停止所有连接该库的后端及维护程序。在仓库根运行 `docker compose up -d --wait`，核对
   Compose project 为 `gba-dev`、MySQL 为 `gba_dev`、应用账号为 `dev_admin`，容器端口映射
   `127.0.0.1:3307`。检查应用端口和 MySQL 活跃连接，防止旧后端并存。
2. 只读导出 `flyway_schema_history` 的全部列，核对 V1–V5 的 script、checksum、success，最高
   版本必须为 V5，且不存在商品/库存/订单/物流业务表。冻结文件摘要由
   `MigrationScriptContractTest` 核对；用同版本 Flyway 对 V5 资源副本执行 validate，核对数据库
   checksum。若已应用 V6/V7 等新版本，或目标/校验不符，立即停止，另行评审向前迁移。
3. 使用容器内 `mysqldump --single-transaction --routines --events --triggers --hex-blob
   --no-tablespaces --set-gtid-purged=OFF` 导出结构及数据到忽略 Git 的 `backups/`，记录 SHA-256。
   口令从容器环境传给 `MYSQL_PWD`，不写入命令、日志或提交。备份期间保持应用停止。
4. 在独立 MySQL 8.0.34 容器恢复备份，不挂载开发卷、不使用固定宿主端口。比较全部业务表
   的结构、排序后的完整行数据和完整 Flyway history，确认恢复成功后保留备份、核验摘要及日志。
   未完成恢复核验不得启动升级。
5. 在 IDEA 的 `MerchantApplication` 配置使用 JDK 21、工作目录 `api`、Active profiles `dev`。
   使用 clean 后的 classpath，经 `dev_admin` 自动执行 V6 商品、V7 库存、V8 订单、V9 物流及
   适用 R040/R050/R060/R070。保留 validate-on-migrate=true、clean-disabled=true、
   out-of-order=false、baseline-on-migrate=false；不执行 repair 或修改 history/checksum。
6. 检查 `/api/v1/health`、Flyway validate、实际迁移清单、三个库存表主键无自增，以及旧账号、
   会话和人工值完整保留。启动成功后从 `/v3/api-docs.yaml/{product,inventory,order,shipment}-v1`
   导出四份实际接口文档到 `docs/api/`，核对路由、字段及权限；SOAP 契约另见物流文档。
7. 用两个租户执行商品库存布尔、员工库存调整、雪花 ID、本人订单快照、物流授权及无运单检查。
   二次启动后比较完整 history 和人工值，验证无新增迁移和演示覆盖。多实例独立配置
   `MERCHANT_ID_WORKER_ID`；本次本地单实例验证不代替集群验收。

失败时立即停止后端，保留备份、history、迁移和应用日志。MySQL DDL 不能假定事务回滚；不得
删卷、清 history、改 checksum、重编号主键或以 repair 掩盖失败。根据隔离恢复结果另行选择
完整备份恢复或向前修复，执行前核对目标和影响；代码回退前先验证数据库兼容性。

2026-11-11 R040 分类外键失败的隔离复现、修复及 2026-11-12 授权恢复结果见
[本地电商开发库恢复方案（本目录入口）](README.md#commerce-dev-recovery)。该方案不是常规启动操作，不自动执行。

建议断点入口：`ProductQueryService.publicSku/withAvailability`、
`InventoryAvailabilityQueryService.inStock`、`InventoryService.execute`、
`InventoryStockTxService.applyDelta`、`OrderQueryService.locateForActor/detail`、
`ShipmentQueryService.getForActor`。沿可信 Actor 的 merchantId 观察授权和跨域服务调用，避免
在日志中输出 token、口令或消费者手机号。
