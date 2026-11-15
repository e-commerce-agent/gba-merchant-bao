# Spec Delta

## MODIFIED Requirements

### Requirement: Infrastructure responsibilities shall be explicit

Redis、RabbitMQ、对象存储、向量数据库和关系数据库必须（MUST）有明确职责。中间件依赖必须由第一个使用它的技术模块引入，不能作为尚未实现模块的全局前置条件。dev/prod 必须使用两份独立 Compose，dev 默认启动 MySQL/Redis 和 Nginx，后端由宿主机 IDEA/Maven 运行，数据使用命名卷；dev 端口必须可配置并限 loopback，两环境必须有健康检查和独立项目身份。本地联调状态必须跨服务重启保留；自动化测试必须使用隔离且可销毁的实例。

#### Scenario: Local integration services are started
- **WHEN** 开发者启动文档规定的 `dev` 环境
- **THEN** 根 Compose 在可覆盖的固定端口启动健康的 MySQL/Redis，IDEA 运行的 dev API 连接它们；MySQL 行和 Redis 键在重启或不删卷的容器重建后保留

#### Scenario: Development Redis has no password
- **WHEN** 启动 dev 的 Redis 并连接 IDEA 应用或本地客户端
- **THEN** dev `.env` 不包含 Redis 密码项，Compose 不设置 requirepass，应用不发送密码，健康检查使用无密码 PING；prod 的认证配置不受此规则影响

#### Scenario: Production Redis uses its runtime password
- **WHEN** prod Compose 启动 Redis 和 app
- **THEN** prod 实际 `.env` 的 `REDIS_PASSWORD` 初始值为 `123456`，Redis 读取该值并通过 requirepass 启用认证；健康检查通过 `REDISCLI_AUTH`、app 通过环境变量占位符读取相同密码，不沿用 dev 的无密码连接方式

#### Scenario: Existing middleware volumes are reused
- **WHEN** 修改 `.env` 或 MySQL 初始化脚本后重启已有数据卷
- **THEN** 不把该操作视为已更新数据库账号，需管理员显式变更并同步应用配置；正常更新不删除命名卷，dev/prod 不共用业务卷

#### Scenario: Automated tests run while local services are running
- **WHEN** 开发环境的服务运行在固定端口并使用持久数据卷
- **THEN** 自动化集成测试使用自己的 Testcontainers 实例，不读取或修改开发者的联调数据

#### Scenario: A dependency is unavailable
- **WHEN** Redis、RabbitMQ、向量存储或对象存储暂时不可用
- **THEN** 所属模块遵循已声明的降级或重试约定，并返回可识别的错误；不得把中间件当作唯一业务事实来源

#### Scenario: Agent dependencies integrate with the baseline
- **WHEN** 后续集成既有的四个 Agent 技术亮点
- **THEN** RAG、Function Calling、RabbitMQ 文档任务和 Redis ChatMemory 使用各自领域接口与数据隔离规则，不改变已经确定的业务场景

#### Scenario: A module introduces a new middleware
- **WHEN** 后续技术模块首次需要 Redis 登录会话等中间件
- **THEN** 该模块的 change 声明自己的 Maven 依赖、配置、数据键约定、测试和运维检查；本基础 change 不创建尚未实现模块的数据表或行为

## ADDED Requirements

### Requirement: 开发 Nginx 应代理宿主机双后端

根 Compose MUST 默认启动 Nginx 并只在本机 `127.0.0.1:8081` 提供代理入口。Nginx MUST 将 `/api` 原路径轮询转发到宿主机 `8080`、`8082` 的两个 dev 后端，且支持后端晚于 Nginx 启动。该能力 MUST NOT 要求将后端放入容器或改变生产配置。

#### Scenario: 先启动 Docker 再启动 IDEA 后端
- **WHEN** 两个后端尚未启动，开发者运行根目录 `docker compose up -d`
- **THEN** Nginx 能正常启动；此时 API 暂不可用，两个 IDEA 后端启动后无需重建 Nginx 即可访问 API

#### Scenario: 两个实例共同处理请求
- **WHEN** 两个 dev 后端均可达，调用方连续请求代理入口的只读 API
- **THEN** 请求实际到达两个后端，上游地址和状态可从代理日志核验；Nginx 不改写 `/api/v1` 路径或应用响应契约

#### Scenario: 一个实例停止
- **WHEN** 一个后端停止接受连接而另一个仍正常运行
- **THEN** 在有限的被动失败检测窗口后，新的只读请求可经代理由存活实例处理；已发送的非幂等请求和已建立的 SSE 不自动重放

### Requirement: 开发双实例应共享状态并保持运行标识独立

两个 dev 后端 MUST 使用相同的开发数据库、Redis、JWT 密钥和可选 RAG 配置，同时使用不同的有效 Snowflake worker ID 和独立日志目录。开发者 MUST 能在 IDEA Services 中分别运行或调试两个配置，并保留 dev 自动 Flyway 迁移行为。

#### Scenario: 从 IDEA Services 启动两个配置
- **WHEN** 开发者先启动端口 `8080`、worker ID `0` 的实例，再启动端口 `8082`、worker ID `1` 的实例
- **THEN** 两者均以 dev 运行、连接同一开发库和 Redis、可分别调试，且文件日志和 Snowflake 标识互不冲突

#### Scenario: 登录请求与业务请求落在不同实例
- **WHEN** 客户端通过 Nginx 登录，随后携带相同凭证访问另一个后端处理的受保护 API
- **THEN** 认证与租户范围仍按既有共享会话契约生效，不要求粘性会话

#### Scenario: 启用共享 Milvus 的首次启动
- **WHEN** 两个 dev 配置启用 RAG，且共享 Milvus standalone 尚未完成 collection 初始化
- **THEN** 第一个后端初始化并通过健康检查后再启动第二个后端；保留 Milvus 的单实例服务、已有宿主机连接方式和持久卷

### Requirement: Vite 开发页面应通过 Nginx 访问 API

前端开发页面 MUST 继续由 `127.0.0.1:5173` 的 Vite 服务提供，其同源 `/api` 请求 MUST 转发到 `http://localhost:8081`。代理验收 MUST 关闭对应接口的 Mock。Nginx MUST 仅代理 API，不承担 `view/dist` 托管或页面入口职责。

#### Scenario: 浏览器通过开发页面发起请求
- **WHEN** 开发者启动 Vite 并在页面调用真实接口
- **THEN** 请求沿浏览器到 Vite、Vite 到 Nginx、Nginx 到某个 dev 后端的路径执行；Apifox 可直接使用 `http://localhost:8081/api/v1` 作为 API 入口

### Requirement: 开发代理应保留 SSE 行为和凭证边界

Nginx MUST 使用 HTTP/1.1 代理并关闭 SSE 响应缓冲，配置足够的读取超时，保留 Authorization、请求关联信息及既有 JSON/SSE Content-Type。代理 MUST NOT 缓存受保护响应或记录凭证和私密正文；流中断后的状态恢复 MUST 沿用现有聊天契约。

#### Scenario: 成功流逐步到达客户端
- **WHEN** 本地模型协议替身在一次调用中按时间间隔生成 token 并完成响应
- **THEN** 客户端通过 Nginx 依次收到 start、逐步到达的 token、done，不能在流结束时才一次性收到全部内容

#### Scenario: 流式调用失败
- **WHEN** 本地模型协议替身触发既有聊天错误路径
- **THEN** 客户端经 Nginx 收到 start 和 error，不出现伪造 done；合法幂等重试仍可按原契约返回 JSON

#### Scenario: 后端在流中退出
- **WHEN** 已建立 SSE 的后端退出
- **THEN** Nginx 不把该流切换到其他后端继续生成，客户端通过本人历史或原幂等键查询共享数据库中的既有状态
