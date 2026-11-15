# Nginx 反向代理与 dev 双实例面试卡片

更新：2026-11-15。实现由 [PR #31](https://github.com/e-commerce-agent/gba-merchant-bao/pull/31) 合入，合并提交 `570d7dc`。本卡片对应 `add-nginx-load-balancing`，本次归档与面试文档提交、mini PR 由作者后续创建，尚无 commit/PR 编号。

## 一、业务梳理

消费者在页面登录、创建会话、发送问题、刷新查看历史时，请求可能落到不同后端。开发环境需要一个固定 API 入口，以验证双实例下的认证、历史、幂等和 SSE 输出，同时保留 IDEA 独立调试每个 JVM 的方式。

当前交付是单台 Windows 上的 dev 双实例：浏览器页面由 Vite 提供，Nginx 只代理 API，两个后端共享原开发 MySQL/Redis。基础对话的六个 OpenAPI 操作已记录联调结果；RAG 来源和 Function Calling 工具摘要的前端开发、专属错误不在该轮验收范围。Function Calling 后端实现及其单独验收见 [模块卡片](function-calling.md)。

可直接讲的开场：

> 我用 Docker Compose 固定本地 MySQL、Redis 和 Nginx 环境，后端继续在 IDEA 启动两个 dev 实例。浏览器通过 Vite 把 API 交给 Nginx，由它轮询到两个后端。重点验证了登录和历史能跨实例使用、SSE 能逐步到达，以及一个实例停止后新的只读请求仍可处理；正在生成的对话通过共享历史和幂等键恢复查询，不承诺流无缝迁移。

## 二、技术实现

**痛点：** 单实例直连无法验证跨节点状态；新增代理层可能积攒 SSE 响应，或改变 Host 导致同源 POST 被 Spring 拒绝。容器内地址与宿主机地址不同，双 JVM 还可能冲突于 worker ID 和日志轮转。

**主方案：** 根 `docker-compose.yml` 的 `gba-dev` 项目默认管理 MySQL、Redis、Nginx；Nginx 使用 `1.28.0-alpine`，只读挂载 `docker/nginx/dev.conf`，发布 `127.0.0.1:8081:80`。两个 dev 后端由 IDEA/Maven 在宿主机运行，upstream 使用 Docker Desktop 提供的 `host.docker.internal:8080/8082`，默认等权轮询。容器的 localhost 指向自身，不能用它连接 IDEA 后端。

**递进：** 两个 JVM 共享开发库、Redis 和 JWT 密钥，分别使用 worker ID 0/1 与独立日志目录。代理保留原路径、Host、Bearer 和 requestId；关闭响应缓冲、缓存及压缩，设置有限连接/读写超时。被动失败检测允许新的只读请求转到存活实例；不启用 `non_idempotent`，防止已发送 POST 被自动重放。共享数据库承担聊天运行权和幂等，Nginx 不替应用保证业务一致性。

**数据流：**

```text
浏览器 127.0.0.1:5173
  → Vite /api（changeOrigin: false）
  → localhost:8081 → Nginx 容器 :80
  → host.docker.internal:8080 或 :8082
  → ChatController#send → ChatService#accept（事务取得 run 运行权）
  → ChatService#execute → 模型回调 → SseSession → Nginx → fetch reader
  → ChatRunExecution 终态事务提交 → SSE done；失败则 error
```

同键同正文重试可直接返回原 run 的 JSON，不重新生成；前端从历史恢复原助手消息。流中断后查询本人历史，不依赖 Nginx 或 SSE event id 重放正文。

### 核心代码位点与版本引用

| 类 / 配置#入口 | 职责 | 已核实功能 commit / PR |
| --- | --- | --- |
| `docker-compose.yml#services.nginx`、`docker/nginx/dev.conf#upstream dev_api` | 固定镜像、loopback 入口、宿主机双后端、日志与失败策略 | `895ece9` / [PR #31](https://github.com/e-commerce-agent/gba-merchant-bao/pull/31) |
| `view/vite.config.ts#/api proxy` | API 默认走 8081，保留 Host/Origin，修复浏览器 POST 403 | `f348668`、`82394d1` / PR #31 |
| `ChatController#send` | 校验身份/请求、区分幂等 JSON 与新 SSE，启动执行和写流 | `abc38b2`、`b86f2ce` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15)，沿用基础实现 |
| `SseSession#startEvent/#listener/#writeLoop/#close` | 有界队列、单写线程、start/token/done/error 与心跳，关闭时取消调用 | `abc38b2`、`b86f2ce` / PR #15，沿用基础实现 |
| `ChatService#accept/#finish`、`ChatRunExecution#complete/#end` | 共享运行权、幂等、终态事务和提交后通知 | `abc38b2`、`b86f2ce` / PR #15；工具摘要扩展 `d819be1` / [PR #29](https://github.com/e-commerce-agent/gba-merchant-bao/pull/29) |
| `view/src/api/chat.ts#sendMessage/#createSession/#listMessages`、`adapters.ts#toChatCard/#toChatBubble` | fetch 解析分片 SSE、会话字段映射、真实 sessionId、游标历史与幂等恢复 | `82394d1` / PR #31 |
| `NginxLoadBalancingIT` | 两个真实 Servlet 上下文、隔离 MySQL/Redis、真实 Nginx 和模型 HTTP 桩验证代理闭环 | `82394d1` / PR #31 |

源码分别位于 `api/src/main/java/com/gba/merchantbao/chat/`、`view/src/api/`，代理测试位于 `api/src/test/java/com/gba/merchantbao/test/nginx/NginxLoadBalancingIT.java`。详细开发步骤从 [部署入口](../../api/docs/deployment/README.md) 查找；验收证据从 [Nginx 验收入口](../../api/docs/testing/20261115-nginx-load-balancing/README.md) 查找。

## 三、技术深挖：两个主讲难点

### 难点一：加了 Nginx 反向代理和负载均衡，SSE 怎么实现？

SSE 是一次 HTTP 响应持续发送事件，Nginx 在建立请求时选定一个 upstream，这条流持续由该后端处理。轮询分配的是新请求，不会把同一条流的不同 token 分给两个 JVM。客户端需要 POST 请求体和 Bearer，因此使用 fetch 读取响应流；原生 EventSource 不适合直接承载这套 POST 契约。

应用端沿用 `SseEmitter`：`SseSession` 用有界队列和单写线程串行发送事件，以心跳注释保持活动；队列耗尽、写入超时或断开会结束流并取消上游工作。`ChatRunExecution` 先提交终态事务，再发 done，避免页面显示成功而数据库尚未保存。前端用 reader、流式 TextDecoder 和空行分帧处理 UTF-8 分片及 LF/CRLF；没有 done/error 就 EOF 时按中断处理。

代理端关键配置与作用：

| 配置 | 为什么需要 |
| --- | --- |
| `proxy_http_version 1.1`、`Connection ""` | 明确上游 HTTP 版本，清除不适合透传的 Connection 值；SSE 不需要 WebSocket Upgrade |
| `proxy_buffering off` | 不在 Nginx 聚积响应后再交付；仅加长超时不能解决缓冲 |
| `proxy_cache off`、`gzip off`、上游 `Accept-Encoding ""` | 不缓存受保护响应，避免此链路的压缩缓冲影响观察 |
| `proxy_connect_timeout 3s`、`proxy_read_timeout/proxy_send_timeout 300s`、`send_timeout 300s` | 分别限制连接及相邻读写操作的空闲等待；300 秒不是模型总运行时长 |
| 保留 Authorization、Host、requestId 和响应 Content-Type | 保持认证、同源判断、排查关联及 `text/event-stream` 契约 |
| `proxy_next_upstream error timeout`、最多 2 个 upstream 尝试，不启用 `non_idempotent` | 对连接失败有限尝试，禁止自动重放已发送 POST；已经向客户端交付的流不能换节点续写 |

模型总 deadline 仍由应用负责。心跳、代理空闲超时和 run 总 deadline 是三个不同层次；心跳不能让模型无限运行。SSE event id 也不代表实现了 Last-Event-ID 重放。

证据来自确定性模型桩：3 个 token 分别在 138/2123/4126 ms 到达，done 为 6157 ms，证明并非流结束后一起交付。真实模型样本只有一个 token，单列保留，不能用它证明持续渐进输出。

面试话术：

> 应用继续用 SseEmitter 发送事件，Nginx 只在请求建立时选后端，同一条 SSE 始终由这个后端生成。我关闭代理响应缓冲、缓存和链路压缩，保留事件类型、认证头和关联 ID，并区分代理空闲超时与应用总 deadline。受控测试证明三个 token 间隔到达；后端退出时不重放已发送 POST，也不续接原流，而是通过共享历史和幂等键查询原 run。

### 难点二：怎么部署，Docker 解决什么，有了 Nginx 还要补什么？

Docker 镜像固定服务及其运行依赖，Compose 描述服务组合、端口、网络、卷、健康检查和日志驱动，减少本机安装和版本差异。当前 dev 将中间件和代理容器化，应用仍在 IDEA 运行；这既复用固定环境，也保留断点调试。命名卷使数据跨容器重建保留，但同机卷不是异机备份，容器也不自动保证数据安全。

Nginx 增加统一 API 入口、反向代理、轮询和有限的被动失败处理。它不负责构建应用、分配唯一 worker ID、共享会话或提交事务；引入后必须补齐以下配套，才能让双实例行为正确：

| 配套 | 当前实现 / 后续边界 |
| --- | --- |
| 容器与宿主机网络 | `host.docker.internal` 回连 IDEA；8081 只发布 loopback，前端页面仍由 Vite 提供 |
| 跨实例状态 | 共享 MySQL/Redis/JWT；登录、幂等和历史不依赖粘性会话；生成中的内存执行仍归所属 JVM |
| 实例身份与文件 | 8080/worker 0/`.runtime/logs`，8082/worker 1/`.runtime/logs/dev-8082`；防止 ID 和文件轮转冲突 |
| 启动及就绪检查 | Compose → 可选 Milvus → 8080 健康 → 8082 健康 → Vite；保留 dev Flyway。Nginx 的 `nginx -t -q` 仅检查配置，不能代表后端就绪 |
| 代理协议与来源 | SSE 专项参数；Vite `changeOrigin: false`、Nginx 保留 Host，修复 Host/Origin 不一致造成的 403，恶意来源仍拒绝 |
| 可观测性与隐私 | access log 记录 `$uri`、requestId、upstream、状态和耗时，不含 query、凭证、正文；默认 error log 也可能含完整 URI，故当前配置关闭它并用 access log 排查，代价是错误细节更少 |
| 未来生产配套 | 仍需单独规划 TLS、入口冗余、实际后端就绪检测、容量/限流、长连接摘流与发布、备份恢复、监控告警和回退演练；这些不是本次已交付的生产能力 |

本地启动在根目录执行 `docker compose up -d`，后端两份配置均激活 `dev`、共用本地 YAML。第二份增加 `--server.port=8082 --merchant.id.worker-id=1 --logging.file.path=.runtime/logs/dev-8082`；启用 RAG 时顺序启动避免首次 collection 初始化竞争。前端关闭本地两个 Mock 开关并重启 Vite。根 `.env` 供 Compose 插值，Spring Boot 不自动读取它；不把密钥放进可提交运行参数。

已有生产准备材料采用“同版本 JAR 构建进镜像 → 独立迁移 → 启动 prod 应用”的受控顺序，prod app 不在启动时跑 Flyway。本 change 没有修改或实际执行这套生产发布；dev 的宿主机双实例配置不能直接当作生产拓扑。单台电脑、单 Nginx 和共享依赖仍有单点，容器化加代理本身不能消除它们。

面试话术：

> Docker 解决运行环境和服务组合的可复现，Nginx 解决统一入口与请求转发。我的本地方案是中间件和 Nginx 用 Compose，两个后端保留 IDEA 调试。加入代理后，我补了容器回连宿主机、共享状态、独立 worker 和日志、SSE 参数、Host/Origin 透传及 upstream 日志。验证了本地轮询和单节点停止后的新请求处理；生产还需要入口冗余、TLS、就绪检查和发布摘流等单独建设，不能把这次实验说成生产高可用。

## 四、高频八股

| 追问 | 项目回答 |
| --- | --- |
| 正向代理、反向代理区别？ | 前者代表客户端访问目标；本项目 Nginx 代表后端接收客户端 API，再选择 upstream。 |
| 为什么不用粘性会话？ | 登录状态在共享 Redis，聊天运行权、幂等和历史在 MySQL。已建立 SSE 自然绑定本次连接；粘性不能恢复进程内生成任务。 |
| `max_fails=1 fail_timeout=5s` 是主动健康检查吗？ | 不是，是请求触发的被动检测及暂时避让；upstream zone 共享状态。配置健康、后端就绪和业务健康要分别检查。 |
| 读超时 300 秒意味着总共可运行 300 秒吗？ | 不意味着，它约束相邻读取之间的空闲；模型总时长由 run deadline 控制。没有实际等满 300 秒做验证。 |
| 两台后端会把吞吐翻倍吗？ | 未压测；共享数据库、模型配额、长连接及单机资源都可能成为瓶颈，没有 QPS/P95 增益数字。 |
| 为什么浏览器同源 POST 还出现 403？ | Vite 曾改写 Host 为 8081，而 Origin 仍为 5173，Spring 判断不一致。保留二者后合法未登录请求恢复 JSON 401，恶意 Origin 仍 403。 |
| `docker compose restart` 能加载新环境变量吗？ | 不能；需要按配置重建对应容器。修改初始化脚本也不会改已有 MySQL 卷账号，不能用删卷替代维护。 |
| 一个后端停掉，正在回答怎么办？ | 已发送 POST 和已建立 SSE 不自动重放；客户端识别中断、查询原 run/历史。优雅退出用例观察到 CANCELLED，不能据此保证任何硬崩溃都立即取消。 |
| 为什么长连接发布要摘流？ | 需先停止分配新请求，再处理既有流的等待或有界终止；直接杀进程会中断 SSE。本次未实现生产摘流流程。 |

## 五、测试条件与真实数字

以下为 2026-11-15 已提交的历史验收记录，本次归档只做规格和文档检查，没有重新启动服务或重跑应用测试。环境：Windows、JDK 21、Maven 3.9.11、Spring Boot 3.5.16、Testcontainers 1.20.6、Docker Engine 29.8.0；Nginx 1.28.0-alpine、MySQL 8.0.34、Redis 7.4-alpine。

隔离代理测试使用两个真实 Servlet 上下文、可销毁 MySQL/Redis、真实 Nginx 和确定性模型 HTTP 桩，随机端口，产物只写 `api/target/`，不连接 dev 业务卷。

| 验证 | 记录结果及边界 |
| --- | --- |
| `NginxLoadBalancingIT` | 4 项通过；成功/错误、断开、后端退出、防 POST 重放和跨实例历史；12 次认证只读请求命中两个 upstream |
| 受控渐进 SSE | 3 token：138/2123/4126 ms；done：6157 ms。为本地桩时序，非真实模型首 token 指标 |
| 固定端口停止/恢复 | 双节点 16 次、停 8082 后 16 次、恢复后 24 次只读请求全部 200；有一条 8082→8080 重试日志。样本不能代表可用性 SLA |
| Maven 全量 | `mvn -f api/pom.xml clean verify`：183 Surefire + 214 Failsafe = 397 项，失败/错误/跳过均 0；16:33:51 +08:00 完成，耗时 24 分 44 秒 |
| 基础对话浏览器 | 登录、新建、发送真实回答、刷新历史成功；脚本覆盖创建/列表/详情/发送/历史/归档六操作及 400/401/403/409、幂等 JSON |
| 真实 dev 代理样本 | 18 条带标记 HTTP 请求，两个 upstream 各 9 次；首次 SSE 在 8080，同键 JSON 重试在 8082 |
| 真实模型单次观察 | start 73 ms、唯一 token 3767 ms、done 3798 ms；只证明该次闭环，不证明持续逐 token 交付或回答准确率 |
| 前端 | 9 项 Node tests 通过，类型检查/构建通过，构建 9.41 秒；属于基础对话联调轮次，该轮未重跑 Maven 全量 |

定向复现命令（需 Docker；执行的是隔离测试）：

```powershell
mvn -f api/pom.xml test-compile '-Dit.test=NginxLoadBalancingIT' failsafe:integration-test failsafe:verify
```

原始报告、upstream 日志和截图留在被忽略的 `api/target/nginx-load-balancing/`、`nginx-dev/`、`nginx-browser/`，仓库保存脱敏摘要。没有 300 秒等待测试、并发压测、生产发布、生产高可用或容量收益数字。全部现实问题与备查项归入 [painpoints](painpoints.md)，本模块仅上述两个难点进入主讲话术。
