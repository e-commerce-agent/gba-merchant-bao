# Design

## Context

范围见 proposal.md；本次修订按用户确认改为 dev，沿用 `add-nginx-load-balancing`，生产部署后续单独规划。当前根 Compose 使用项目名 `gba-dev`，管理 MySQL 和 Redis；IDEA/Maven 在 `api` 工作目录运行 Spring Boot。Vite 已支持用 `VITE_PROXY_TARGET` 配置 `/api` 代理，当前开发示例直连后端 `8080`，且默认启用 Mock。

应用已有 `/api/v1/health`、worker ID 覆盖项、文件日志目录参数和 dev Flyway 迁移。仓库没有 Maven Wrapper，命令行备用入口使用已安装的 `mvn`。

## Goals / Non-Goals

**Goals:**

- 根 Compose 先启动 Nginx，IDEA Services 随后分别运行或调试两个宿主机 dev 后端。
- Nginx 提供本机 API 入口、双后端轮询、故障后的只读访问与 Agent SSE 转发。
- 共享现有开发数据库、Redis、认证配置及可选 Milvus，不打断原有 Flyway 与数据持久化流程。

**Non-Goals:**

- 修改生产 profile、生产 Compose、生产手册或发布脚本；生产集群不列入本 change 的待办。
- 后端容器化、静态前端托管、跨服务器调度、Nginx 双机高可用、TLS 和压测容量承诺。
- 新增 Maven Wrapper、Milvus 分布式部署、迁移版本或 API 契约。

## Decisions

### 1. 根 Compose 默认启动 Nginx

在根 `docker-compose.yml` 增加无可选 profile 的 `nginx` 服务，复用 `gba-dev` 项目，端口固定映射 `127.0.0.1:8081:80`。Nginx 使用固定版本的官方开源镜像，配置文件放在根 `docker/nginx/dev.conf` 并只读挂载；沿用 `json-file`、20MB×5 的日志轮转。

Nginx 不依赖任何应用容器，也不以两个后端已健康作为启动条件。上游使用 Docker Desktop 的宿主机名称，可在后端尚未启动时解析该名称并启动 Nginx；两个后端都未运行时 API 返回 502，启动后端后无需重建 Nginx。Nginx 自身健康检查只代表配置和代理进程可运行，不代表后端已启动或依赖全部可用。

选择直接加入根 Compose，以保持用户确认的 `docker compose up -d` 顺序，不增加另一套 Compose 项目或必须记忆的 profile。

### 2. 容器访问的是同一台 Windows 上的 IDEA 进程

请求路径如下：

```text
浏览器 127.0.0.1:5173
  -> Vite /api 代理
  -> localhost:8081（Docker 映射到 Nginx :80）
  -> host.docker.internal:8080 或 host.docker.internal:8082
  -> Windows 上 IDEA/Maven 启动的 dev 应用
```

`host.docker.internal` 是 Docker Desktop 提供的宿主机访问名称。Nginx 容器内的 `localhost` 指向 Nginx 自身，因此 upstream 不能写 `localhost:8080`。在真实 Docker Desktop 验证两个端口均可从容器访问，保留当前适合容器回连的后端监听配置；如遇监听或防火墙问题，定向检查 Docker 到宿主机的连接，不扩大 Nginx 的本机发布范围。

浏览器页面入口继续为 `http://127.0.0.1:5173`；`http://localhost:8081` 是 API 代理入口，不是前端首页。Apifox 可直接访问 `http://localhost:8081/api/v1/...`。Nginx 仅对 `/api` 和 `/api/` 下路径转发，保留原始 URI，其他路径返回明确的 404。

### 3. 在 IDEA Services 分别管理两个实例

两个 Run/Debug Configuration 均选择同一应用、JDK 21、工作目录 `api`、Active profiles=`dev`。不要求 IDEA 读取根 `.env`，不复制包含密钥的个人配置。

| 配置 | Program arguments | 文件日志目录 |
| --- | --- | --- |
| dev-8080 | 可留空，使用默认端口 8080、worker ID 0 | `api/.runtime/logs/` |
| dev-8082 | `--server.port=8082 --merchant.id.worker-id=1 --logging.file.path=.runtime/logs/dev-8082` | `api/.runtime/logs/dev-8082/` |

实例一可继续使用已有配置，不必显式填写 `--server.port=8080 --merchant.id.worker-id=0`；启动前确认本地 YAML、环境变量（如 `SERVER_PORT`、`MERCHANT_ID_WORKER_ID`）及 JVM 参数没有覆盖这些默认值。第二个配置保留用户指定的 `--server.port=8082 --merchant.id.worker-id=1`，仅补充日志目录。独立目录防止两个 JVM 同时轮转同一个 application/audit 日志。IDEA 使用各自 Debug 会话，无需另设远程 JDWP 端口。

两个实例读取同一被忽略的 `application-dev.yml`，共享 MySQL、Redis 和 JWT 密钥，以现有共享状态支持跨实例认证、聊天运行权及历史查询，不启用粘性会话。先启动 dev-8080 并等待 `/api/v1/health` 成功，再启动 dev-8082：两个实例均保留 dev 自动 Flyway；启用 RAG 时，该顺序还避免空 collection 同时创建。Milvus 继续由可选 `docker-compose.rag.yml` 启动，宿主机后端按现有配置连接 `127.0.0.1:19530`，不使用只有容器内部可解析的 `milvus` 服务名。

备用 Maven 命令在 `api` 目录运行：实例一用 `mvn spring-boot:run '-Dspring-boot.run.profiles=dev'`；实例二用 `mvn spring-boot:run '-Dspring-boot.run.profiles=dev' '-Dspring-boot.run.arguments=--server.port=8082 --merchant.id.worker-id=1 --logging.file.path=.runtime/logs/dev-8082'`。命令行需分别使用两个终端，IDEA Services 仍是主要入口。

### 4. 轮询、故障窗口和调试行为

Nginx upstream 显式包含 `host.docker.internal:8080` 与 `host.docker.internal:8082`，采用默认等权轮询、共享 upstream zone 和有限的被动失败检测；不设置 `ip_hash`。启动和停止宿主机 JVM 不改变宿主机地址，无需引入服务发现平台。

为演示连接拒绝后的故障转移设置短连接超时；保留 Nginx 默认对非幂等重试的限制，不启用 `non_idempotent`，不重放已经发送的 POST 或已经建立的 SSE。容器状态不能反映宿主机 IDEA 进程健康，真实可达性通过请求和 upstream 日志确认。健康探测和连接复用会影响请求分布，不把严格逐个交替作为断言；以一批只读请求中两个 upstream 均出现作为负载均衡证据。

在某个实例打断点只会暂停命中该实例的请求；若 IDEA 暂停整个 JVM，Nginx 可能等待或在超时后判定失败。需要定点调试时可直接访问 8080/8082，但负载均衡验收必须经过 8081，不承诺长时间断点暂停时代理永不超时。

### 5. Vite 和 SSE

更新开发环境默认代理目标及示例为 `http://localhost:8081`，保持 `VITE_API_BASE_URL=/api`。真实联调通过忽略的本地覆盖关闭 `VITE_USE_MOCK`，代理验收同时关闭 `VITE_MOCK_UNIMPLEMENTED`，不改动现有离线 Mock 能力。说明修改 Vite 环境变量后需要重启 Vite；API 请求经其代理到 Nginx，不依赖新增 CORS 配置。

API 使用 HTTP/1.1 转发，保留 Authorization、X-Request-Id 和响应 Content-Type，禁用代理缓冲、代理缓存及 gzip；读取/发送超时初始取 300 秒，覆盖示例中 Agent 的 180 秒总 deadline。`proxy_read_timeout` 约束相邻读取间隔，不充当 Agent 总 deadline，修改模型运行时限时须复核代理参数。请求体上限留出 multipart 开销，不能让现有有效的 1 MiB 知识文件被代理先行拒绝；实际业务限制仍由应用执行。

访问日志仅记录必要路径、请求关联标识、upstream 地址、状态及耗时，不记录 Authorization、Cookie、查询参数或消息正文。认证、租户和 JSON 错误保持现有后端契约，Nginx 自身的 502/504 不伪装成应用成功。

### 6. 验证方式

配置检查覆盖 Compose 默认服务、本机端口和 Nginx 语法；重点是实际代理行为，而非堆叠配置字符串断言。隔离测试使用 Testcontainers MySQL/Redis、两个真实应用上下文、本地模型协议替身和真实 Nginx，使用随机端口及 target 下临时文件，不连接 dev 业务卷。通过可控延迟确认 token 在 done 之前逐步到达，并用错误场景确认 error；成功和失败是不同 run，不要求一次流同时出现 done 和 error。

Docker Desktop + IDEA Services 的 dev 联调单独验证固定的 8080/8082/8081 链路、容器回连宿主机、轮询、跨实例凭证和断开恢复。若未实际在 IDEA Debug 模式运行，不将 Maven 启动的成功冒充 IDEA 断点验证。已配置真实模型时可追加一次成功流 smoke test；它是可选补充，缺少凭证时明确未测，不影响协议替身的必需验收。

## Risks / Trade-offs

- [两个 JVM 配置相同 worker ID 或日志目录] → 为两个 Run Configuration 显式写明独立值，并在联调前核对有效配置。
- [IDEA 单独停止或暂停某个实例] → 验证新只读请求的有限故障转移；不承诺正在执行的 SSE 无缝切换。
- [Docker Desktop 无法回连宿主机端口] → 在真实容器中检查 `host.docker.internal`、监听地址和定向防火墙规则；保持 Nginx 的 loopback 发布。
- [共享数据库和 Milvus 的首次初始化竞争] → 人工顺序启动两个实例，保留 Flyway 校验和 RAG schema 检查，不通过删卷或关闭迁移规避问题。
- [单机双实例被误写为生产高可用] → 验收记录明确这是 dev 应用集群演示，单台电脑、Nginx 和 Milvus 仍是单点。

## Migration Plan

1. 实施时加入根 Compose Nginx 服务和配置，校验后执行 `docker compose up -d`，保留 MySQL/Redis/Milvus 卷。
2. 在 IDEA Services 准备两个 dev Run/Debug Configuration；先启动 8080 并等待健康，再启动 8082。需要 RAG 时按原 overlay 命令启动 Milvus。
3. 设置 Vite 的代理目标和真实接口开关并运行 `npm run dev`；页面访问 5173，API 与 Apifox 验收走 8081。
4. 执行隔离协议验收和本机链路检查，必要时停止第二实例后再恢复；仅用测试数据验证写入，不清空或重置现有业务数据。
5. 回退时移除新增 Nginx 配置、将 Vite 代理恢复为 8080，并停止额外的 dev 实例；不删除持久卷、不回退数据库。
