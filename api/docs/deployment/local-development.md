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

当前 dev Compose 默认包含 MySQL、Redis 和 Nginx，没有需要显式激活的 Compose profile。
Nginx 仅发布 `127.0.0.1:8081:80`，后端仍由宿主机 IDEA/Maven 运行，不包含应用容器。
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

IDEA 双实例配置及 Maven 双终端备用命令见下文。

首次启动空库自动执行 V1→V9 和适用种子，无需 baseline。填好实际 dev YAML 后仅激活 dev 即可启动，无需个人旧库或云端 OSS/微信凭据；占位外部配置不表示相应业务可用。
Apifox 可使用代理 API 入口 `http://localhost:8081/api/v1`，健康检查为
`http://localhost:8081/api/v1/health`。需要定点访问宿主机后端时仍可直连 `http://localhost:8080`；
Swagger `/swagger-ui.html` 和 `/v3/api-docs.yaml` 也通过后端直连访问。
前端页面继续由 Vite 的 `http://127.0.0.1:5173` 提供，`/api` 默认代理到 Nginx 8081；
真实联调需先按 [前端说明](../../../view/README.md#mock-与真实接口切换) 在本地覆盖中关闭两个 Mock 开关并重启 Vite。
若浏览器 POST 返回 `403 Invalid CORS request`，检查 Vite `/api` 代理使用 `changeOrigin: false`，
并由 Nginx 保留原 Host；否则 Host 被改为 8081、Origin 仍为页面 5173，会被 Spring 当作跨域拒绝。
合法同源未登录请求应得到 JSON 401，恶意 Origin 仍应被拒绝，不需要放宽后端 CORS。
数据库工具连接 127.0.0.1:3307/gba_dev，dev_admin；Redis 工具连接 127.0.0.1:6380，无认证。前端调用 API，不直接连接数据库。
多个前端/后端开发者各自建立空库；共享联调如需 API 远程可达，单独约定主机地址。中间件保持 loopback。

## IDEA Services 双实例

在 **Run → Edit Configurations** 保留已有 `MerchantApplication` 作为实例一，复制一份命名为
`MerchantApplication-8082`。两份均使用 Spring Boot 类型、主类 `com.gba.merchantbao.MerchantApplication`、
模块 `gba-merchant-bao`、JDK 21，Active profiles 只填 `dev`，Working directory 指向仓库 `api`
（IDEA 打开的项目根为 api 时可填 `$PROJECT_DIR$`）。

| 配置 | Program arguments | 有效端口 / worker ID | 日志目录（相对 api） |
| --- | --- | --- | --- |
| MerchantApplication（实例一） | 留空 | 8080 / 0 | `.runtime/logs` |
| MerchantApplication-8082（实例二） | `--server.port=8082 --merchant.id.worker-id=1 --logging.file.path=.runtime/logs/dev-8082` | 8082 / 1 | `.runtime/logs/dev-8082` |

实例一使用 `application.yml` 的默认值，启动前检查实际 `application-dev.yml`、IDEA 的
Environment variables、VM options 及继承的进程环境，确保没有 `SERVER_PORT`、
`MERCHANT_ID_WORKER_ID`、`LOGGING_FILE_PATH`、`LOG_DIR`、`SPRING_APPLICATION_JSON` 或
`-Dserver.port` / `-Dmerchant.id.worker-id` / `-Dlogging.file.path` 等覆盖这些值。
也要检查 `JAVA_TOOL_OPTIONS`、`JDK_JAVA_OPTIONS` 和 `_JAVA_OPTIONS`；不要把旧实例的参数复制进实例一。
两份配置均读取同一被忽略的 dev YAML，共享 MySQL、无密码 Redis、两把 JWT 密钥及启用时的
RAG/Milvus；根 `.env` 只供 Compose 插值，不向 IDEA 注入，也不把个人密钥写入可提交的运行配置。

先启动 Docker Desktop，在仓库根运行 `docker compose up -d --wait`；若本地 YAML 启用了 RAG，
先按 [RAG 文档](../rag/README.md) 启动原有 Milvus standalone。运行实例一，等待
`http://127.0.0.1:8080/api/v1/health` 成功及启动日志完成后再运行实例二，检查 8082 的相同健康路径。
保留 dev 自动 Flyway（不要添加 `--spring.flyway.enabled=false`）；顺序启动也避免首次 RAG
collection 初始化竞争。Milvus 仍通过宿主机 `127.0.0.1:19530` 共享，不增加第二套卷或容器。

在 **View → Tool Windows → Services** 展开 Spring Boot；若配置未显示，使用 Services 的
Add Service / Run Configuration Type 添加 Spring Boot。分别选择配置后使用 **Run** 或
**Debug**，在对应条目查看控制台；**Stop** 只停止选中实例，需再次启动时使用该条目的
**Run/Debug**，或用 **Rerun** 重启它。不要把两个实例一起启动来绕过首次初始化顺序。

每个实例都有独立 Debug 会话，无需手填远程 JDWP 端口。可在 `FoundationController.health`
的可执行行设置临时断点，先分别直连 8080/8082 验证两个会话，再通过 8081 或 Vite 请求观察命中节点。
Nginx 轮询时断点只在收到该请求的实例触发；暂停整个 JVM 可能导致等待或超时，调试期间不保证严格
逐个交替。需要定点调试时直连相应端口；验证后 Resume 并移除自己的临时断点。

检查两个日志目录中的 `application.json` 和 `audit.json`，确认各自启动信息和请求 ID；不要让两个
JVM 轮转同一文件。8081 是 API 入口，页面仍在 5173。Maven 运行成功不能代替 IDEA Debug 的断点验证。

## Maven 双终端备用入口

使用已安装 Maven，在两个终端分别进入仓库 `api` 目录，不引入 Maven Wrapper。
先执行实例一，等健康检查成功后再执行实例二；与 IDEA 配置使用相同 dev YAML 和默认值，
且不要与同端口的 IDEA 实例同时运行。

```powershell
# 终端一，工作目录 api：默认 8080、worker ID 0、.runtime/logs
mvn spring-boot:run '-Dspring-boot.run.profiles=dev'
```

```powershell
# 终端二，工作目录 api：覆盖 8082、worker ID 1 及独立日志目录
mvn spring-boot:run '-Dspring-boot.run.profiles=dev' '-Dspring-boot.run.arguments=--server.port=8082 --merchant.id.worker-id=1 --logging.file.path=.runtime/logs/dev-8082'
```

各终端用 Ctrl+C 停止相应实例；重新执行同一命令即可重启。命令没有密钥参数，仍保留自动 Flyway。

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
等待步骤。Docker Desktop 启动后，推荐顺序是：启动 Compose 中间件和 Nginx、在 IDEA 激活 Spring
`dev`、再启动后端。后端通过 `dev_admin` 连接数据库，Flyway 会自动执行待执行迁移，随后即可
进行断点调试。前端和 Apifox 使用同一 dev API，不连接 Testcontainers。

项目名 gba-dev，对应 gba-dev_mysql_data / gba-dev_redis_data，与 prod 的 gba 分离。
正常流程不使用 down -v。改变 .env 或初始化脚本不会修改已有 MySQL 卷账号；管理员连接正确实例显式 ALTER USER/授权，再同步实际 YAML 和 .env，不能删卷来改密码。
例：先用管理员会话执行 `ALTER USER 'dev_admin'@'%' IDENTIFIED BY '<新密码>';`，确认新连接可用再更新应用。不要把口令写到命令历史、日志或提交中。
改数据库名也不会自动为已有卷创建新库，需先规划迁移并由管理员处理。

持久化验收应在明确的 dev 目标内创建独立临时表/键，重启和重建后读取，最后仅定向清理自己的表/键；不要对联调业务表 TRUNCATE。
自动化集成测试使用动态端口的 Testcontainers，可与 dev 同时运行；它不挂载这些卷。

## dev Nginx 入口与排查

根 Compose 使用固定镜像 `nginx:1.28.0-alpine`，只读挂载根目录的 `docker/nginx/dev.conf`，
并由容器启动脚本生成活动配置。容器将 `/api` 和 `/api/` 下的原始路径及查询参数等权轮询到
Docker `host-gateway` 的 IPv4 地址上 8080、8082 端口。容器里的 `localhost` 指向容器自身，
不能用来访问 IDEA 进程。
Nginx 不托管前端页面，`/`、`/apiary`、Swagger 等非 API 路径返回 404。

先运行根目录的 `docker compose up -d`，再启动宿主机 dev 后端。
即使两个后端都未启动，Nginx 也能启动，此时 API 返回 502。后端可达后无需重建 Nginx，
被动失败检测窗口为 5 秒，恢复后再次请求即可；连接超时为 3 秒，每个请求最多尝试两个 upstream。
`docker compose up -d --wait` 中 Nginx 的健康检查仅检查其配置；容器健康不代表后端健康。

```powershell
# 仓库根目录：配置检查、状态和后端健康请求
docker compose config --quiet
docker compose exec -T nginx nginx -t
docker compose ps nginx
curl.exe -i http://127.0.0.1:8081/api/v1/health
# 直接检查宿主机两个端口；尚未运行的实例会连接失败
curl.exe -i http://127.0.0.1:8080/api/v1/health
curl.exe -i http://127.0.0.1:8082/api/v1/health
docker compose logs --tail 50 nginx
```

502 先检查后端是否已启动、端口是否正确，再从 Nginx 容器检查宿主机解析及 HTTP 连接：

```powershell
docker compose exec -T nginx grep -E 'server [0-9].*:808[02]' /etc/nginx/conf.d/default.conf
```

从输出的 IPv4 地址分别检查 8080 和 8082 的 `/api/v1/health`；Nginx access log 中的
`upstream_addr` 可确认实际连接地址及失败节点。

开发代理在 Compose 中将专用别名 `host.docker.internal.v4` 映射到 Docker 的 `host-gateway`。
容器启动脚本只提取 `/etc/hosts` 中该别名的 IPv4 地址并生成 Nginx upstream，因为 IDEA/Maven
启动的后端监听 IPv4。若 Nginx 日志中的 `upstream_addr` 出现 IPv6 地址，检查容器是否由当前
Compose 配置重建。

宿主机直连成功但容器连接失败时，检查后端监听地址和 Docker Desktop 到宿主机的防火墙规则；
不要扩大 Nginx 的 loopback 发布范围。504 表示代理等待上游超时，检查 IDEA 断点、后端日志及
依赖响应。读取和发送超时均为 300 秒，读取超时约束相邻读取的间隔；调整 Agent deadline 时须
一起复核。已发出的 POST 和已建立的 SSE 不自动重放或跨实例接续。

代理使用 HTTP/1.1，关闭响应缓冲、缓存及 gzip；2 MiB 请求体上限为现有 1 MiB 知识文件预留
multipart 开销，业务限制仍由应用执行。凭证头与响应 Content-Type 保留，合法的 X-Request-Id
原样转发，缺失或非法时生成请求 ID。JSON 访问日志只记录方法、路径、请求 ID、状态、总耗时及
upstream 地址/状态/耗时，不记录查询参数、Authorization、Cookie 或正文。
Nginx 生成的 502/504 响应也带 `X-Request-Id`；聊天页会显示该请求号，便于在
`docker compose logs nginx` 中按 `request_id` 查到实际 `upstream_addr`、`upstream_status`
及耗时。502 表示连接/上游错误，可据此区分宿主机服务不可达或上游提前断开；504 表示超时。
默认请求错误日志因包含完整 URI 而关闭，502/504 从上述访问日志检查；容器日志沿用 20 MB × 5 轮转。

修改代理配置后，先检查再重载（宿主机后端启动/停止无需重载）：

```powershell
docker compose exec -T nginx nginx -t
docker compose exec -T nginx nginx -s reload
```

临时回退可在根目录执行 `docker compose stop nginx`，让 Apifox 或本地调用方恢复直连
`http://localhost:8080`；若 Vite 本地代理目标已改为 8081，将其改回 8080 并重启 Vite。
永久撤销时再移除根 Compose 的 nginx 服务及 `docker/nginx/` 下的代理配置和启动脚本。
不需要删除 MySQL/Redis/Milvus 卷或回退数据库；下次执行未移除 nginx 的根 `up -d` 会再次启动它。

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
