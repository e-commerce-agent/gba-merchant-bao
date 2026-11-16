# Tasks

本 change 只实施 dev Nginx 和宿主机双后端联调；生产环境集群另行规划。全部任务在实施后按实际证据勾选，本次规划修订不表示已完成部署。

## 1. 根 Compose 与 dev Nginx

- [x] 1.1 在根 `docker-compose.yml` 增加默认启动的 `nginx` 服务，固定镜像版本、只读挂载 `docker/nginx/dev.conf`，发布 `127.0.0.1:8081:80` 并复用现有日志轮转；执行 `docker compose config --quiet`，确认不需额外 profile、不包含应用容器且保留原 MySQL/Redis 卷和 loopback 端口。
- [x] 1.2 配置 `/api` 原路径代理到 `host.docker.internal:8080`、`host.docker.internal:8082`，采用等权轮询与有限的被动失败检测；执行 `nginx -t`，验证两个宿主机后端都未启动时 Nginx 仍能启动，后端启动后无需重建代理即可访问。
- [x] 1.3 配置 HTTP/1.1、禁用响应缓冲/缓存/gzip、300 秒读取/发送超时及容纳现有知识文件的请求体上限，保留凭证和请求关联头；日志包含 upstream 地址/状态/耗时但不记录凭证、查询参数和正文；通过真实请求检查响应头与日志，确认非 API 路径返回 404，且未启用非幂等请求重放。
- [x] 1.4 在 `api/docs/deployment/local-development.md` 及所属 README 记录 Nginx 本机入口、`host.docker.internal` 含义、先 Docker 后后端的启动顺序、502/504 排查和回退方式；逐条核对命令与配置一致，并运行适用的既有 RuntimeConfigurationTest，确认 dev/prod 配置边界仍成立。

## 2. IDEA Services 双实例与 Vite 联调

- [x] 2.1 在本地联调说明中给出两个 IDEA Run/Debug Configuration：均激活 dev、工作目录 api；实例一可不填 Program arguments，使用默认端口 8080、worker ID 0 和日志目录 `.runtime/logs`；新增的第二份配置填写 `--server.port=8082 --merchant.id.worker-id=1 --logging.file.path=.runtime/logs/dev-8082`。核对本地 YAML、环境变量及 JVM 参数没有覆盖实例一的默认值，两者日志目录独立、共享 dev MySQL/Redis/JWT、保留自动 Flyway，并说明先等实例一健康再启动实例二以避免首次 RAG 初始化竞争。
- [x] 2.2 补充 api 目录下已安装 Maven 的双终端备用命令：实例一仅激活 dev，实例二另行覆盖端口、worker ID 和日志目录；校验命令参数和 IDEA 配置一致，不引入 `mvnw`，不提交个人密钥配置。
- [x] 2.3 更新 `view/.env.development`、`view/.env.example` 中的代理目标为 `http://localhost:8081`，保持 `/api` 前缀，保留离线 Mock 能力；在 view README 记录使用本地覆盖关闭 `VITE_USE_MOCK` 与 `VITE_MOCK_UNIMPLEMENTED` 后重启 Vite；运行前端类型检查，并用真实请求确认浏览器页面仍在 5173、API 经 Vite 到 Nginx 后再到后端。
- [x] 2.4 记录 IDEA Services 的分别 Run/Debug、停止和重启操作，以及轮询导致断点仅在命中节点触发的行为；在本机联调时检查 8080/8082 健康响应和独立日志，实际执行 IDEA Debug 时验证各自断点，不将 Maven 进程验证冒充 IDEA 调试验证。

## 3. 隔离集成与 dev 验收

- [x] 3.1 提交使用真实 Nginx、两个真实后端应用上下文、Testcontainers MySQL/Redis 和本地模型协议替身的集成验证，随机端口和日志只写 `api/target/`，不访问 dev 业务卷；验证一批只读请求命中两个 upstream、相同登录凭证跨节点可用，以及会话幂等与共享历史保持原契约；运行新增集成验证并记录结果。
- [x] 3.2 在上述隔离验证中加入有间隔的成功 token、受控错误、流中断和同幂等键 JSON 重试场景；验证成功 run 的 start/token/done、失败 run 的 start/error、token 在完成前逐步到达、代理不会提前截断长流或重放已发送的 POST；运行用例并核对事件时序及历史状态。
- [x] 3.3 在 Docker Desktop 本机链路验证 `127.0.0.1:8081` 到宿主机 8080/8082 的轮询，检查容器可解析并访问 `host.docker.internal`；停止一个后端后验证新只读请求仍可由另一个处理、恢复后再次命中两个节点，保存日志证据，不要求 SSE 跨实例接续。
- [x] 3.4 完成后运行 `mvn -f api/pom.xml clean verify`，按实际配置检查启用时的共享 Milvus standalone 和知识文件上传代理兼容性；真实模型成功流仅作为可选 smoke test，无配置时明确未测；记录配置版本、测试结果、IDEA/真实模型的实际验证边界，将报告放在 target、无凭据摘要放入实际日期的 `api/docs/testing/YYYYmmdd-nginx-load-balancing/` 并维护目录 README，确认生产文件和现有业务卷未改动。

## 4. Agent Conversation 浏览器真实联调

本节仅验收基础对话技术模块；RAG 与 Function Calling 的前端功能和专属错误不在本轮实施/验收范围。沿用后端现有 dev 配置，不以这两个模块的前端能力作为基础对话通过条件。

- [x] 4.1 复现浏览器发送消息的 `403 Invalid CORS request`，核验 Origin/Host 在 Vite→Nginx→dev 双后端的传递并修复开发代理；保留后端来源限制，增加覆盖真实 Vite 代理的回归验证，并检查合法同源 POST 与恶意跨源请求。
- [x] 4.2 按 `docs/api/agent-conversation-v1.openapi.yaml` 核对前端调用与展示，修复联调发现的会话创建字段、SSE 终态/错误及历史恢复问题；真实模式不使用演示会话或伪造成功，保留离线 Mock，运行前端类型检查、构建与针对性回归。
- [x] 4.3 在已启动的 dev 环境关闭 Mock，实际操作浏览器创建会话、发送消息、观察流式回复并刷新查看历史；通过同一 Vite/Nginx 入口验证契约全部六个操作（创建/列表/详情/归档/历史/发送）、幂等 JSON 重试及归档冲突，记录双 upstream 和真实模型的实际结果。
- [x] 4.4 将复现、根因、修复、命令与脱敏验收结果补充到本日期联调报告及本地开发说明，明确浏览器验证和自动化协议验证的边界；执行 OpenSpec strict 校验并按证据更新任务状态。
