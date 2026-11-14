# 模型集成与运行边界

使用 LangChain4j BOM/OpenAI/JDK HTTP client **1.9.1**，应用为 Spring MVC，不引入 WebFlux。
`AgentModel` 只接受服务端组装的 SDK 消息和 delta/complete/error 回调，返回取消句柄。
`OpenAiAgentModel` 交由 SDK 完成 Chat Completions 序列化与流解析；业务不解析 choices 或供应商 SSE。
每次调用持有独立 JDK transport，取消同时请求 SDK streaming handle 和 transport.shutdownNow，覆盖首 token 前的取消。
供应商是否立即停止及已产生费用无法由本应用保证。

## 配置

公共配置默认 `merchant.agent.enabled=false`，不要求模型凭证，历史读取不受影响。
dev 直接使用已被 Git 忽略的 `api/src/main/resources/application-dev.yml`，合并到已有 `merchant` 下：

```yaml
merchant:
  agent:
    enabled: true
    base-url: https://note3-prev-api.askdiandian.com/v1
    api-key: "在本机填写 dots 平台创建的完整 API key"
    api-key-header: api-key
    model: dots3-note-prev
    service-label: dots
    first-token-timeout: 90s
    total-timeout: 180s
```

[dots 官方文档](https://dots.ai/platform/docs) 的 Chat Completions 示例使用上述地址、模型名和 `api-key` 请求头。
SDK 负责将根路径 `/v1` 拼成 `/v1/chat/completions`。不要将 dots 文档站点地址填作 base-url。
`api-key-header` 默认 `Authorization`，由 SDK 发送 `Bearer <key>`；配置为 `api-key` 时仅发送该头中的原始 key，不附加 Bearer。
项目仍使用统一 OpenAI Chat Completions 适配器，无需单独的 dots 适配器。

本地 dev 配置已预填 dots 的非密钥字段并启用，填入 `api-key` 后重启 dev 即可尝试调用；未填 key 时可暂时设 `enabled: false`。
密钥必须是平台为该账号签发且有模型访问权限的完整 key，实际额度/权限与真实流式结果需要有效 key 才能核实。
根 `.env` 继续只给 Docker Compose 使用，Spring Boot 不读取它；IDEA 无需配置额外环境变量。
修改 dev YAML 后重启 API 即生效；不用改 `.env` 或重建 MySQL/Redis。

`AgentModelProtocolTest` 使用本地 stub 验证最终路径、两种鉴权头、model、system/user/assistant、stream、输出上限和 UTF-8 分片拼接。
dots 默认开启深度思考，当前只转发最终正文，不将 `reasoning_content` 当作正文；dev 放宽首 token/总时长以允许正文前的思考等待。
切换服务时修改本地 base-url/api-key/model，按服务要求选择 api-key-header 后重启；单次部署只有一个服务，不自动切换。

可在同一个 merchant.agent 节点覆盖以下默认上限：

```yaml
prompt-version: assistant-v1
service-label: openai-compatible
max-input-chars: 4000
max-output-chars: 16000
max-output-tokens: 2048
context-tokens: 8192
history-messages: 20
connect-timeout: 5s
first-token-timeout: 20s
total-timeout: 90s
max-retries: 1
retry-backoff: 200ms
workers: 8
queue-capacity: 64
stream-queue-capacity: 64
write-timeout: 5s
heartbeat: 10s
allowed-origins: []
```

配置启动时校验，错误只指明配置项；不记录完整配置、密钥、完整 Prompt、正文或原始上游异常。
dev 的完整真实配置保存在被忽略的 application-dev.yml；可提交的 .example 只保留空 key。

## 上下文

`AgentContext` 使用服务端 system、最近完整成功的 USER/ASSISTANT 轮次、当前输入，当前输入只出现一次。
先从 context-tokens 中预留 max-output-tokens，再按最近轮次向前选取；条数上限只约束历史消息。
以 UTF-8 字节数加每条 4 个角色开销作保守 token 近似，跨模型不声称精确，更不当作计费用量。
system+当前输入超限明确拒绝，不截断用户正文；失败/取消/超时轮次保留在 MySQL 历史，不能加入成功上下文。
`AgentContextTest` 精确断言角色、顺序、完整轮次、预算及输出预留；`ScriptedAgentModel` 提供确定 delta、延迟、错误和取消。

## 订单查询的内部授权入口

`OrderQueryService.detailByOrderNo(Actor actor, String orderNo)` 提供按订单号查询详情的 Java Service 入口，供服务端使用认证得到的可信 Actor 调用，不新增 HTTP 路由。调用方不得根据模型参数或客户端字段构造身份。

- 消费者查询在 SQL 中同时限定 `merchant_id`、`customer_id` 和 `order_no`，只能读取本商户本人订单。
- 员工查询同时限定 `merchant_id` 和 `order_no`，只能读取本商户订单。
- 订单号为 1–64 个 ASCII 字符，首位为字母或数字，后续允许字母、数字、下划线、连字符；不自动去空白或改写大小写。缺少 Actor 或订单号格式无效时不访问数据库。
- 不存在、跨商户、非本人及无效输入统一抛出 `OrderError`（404 / `RESOURCE_NOT_FOUND`），不泄露订单是否存在；拒绝发生在读取商品明细之前。存储异常继续向调用方传播，不伪装为订单不存在。
- 返回复用现有 `Detail`，包含订单状态、支付状态、金额、币种、下单时间和商品交易快照；不包含地址、手机号或凭证。同一订单号在不同商户对应各自商户的订单。
- 既有 `locateForService(merchantId, orderNo)` 属于服务凭证调用边界，不具备消费者本人过滤，不能代替此 Actor 入口。

`OrderQueryServiceTest` 覆盖本人/员工读取详情、他人/跨商户/不存在统一拒绝，以及无效输入不访问数据库；从 `api/` 执行 `mvn -Dtest=OrderQueryServiceTest test`。`OrderPersistenceIT#orderNumberDetailEnforcesSqlOwnerScopeAndSeparatesSameNumberAcrossTenants` 使用隔离 MySQL 验证真实 Mapper 授权及跨商户同号订单隔离。

当前只交付订单服务入口；模型 Function Calling 协议、聊天工具编排和工具摘要仍在后续任务中。

## 后续职责

RAG 负责检索、可见性与引用注入；Function Calling 负责可信身份下的工具调用；ChatMemory 以后替换历史选择策略。
当前不建 Redis ChatMemory/摘要/空模块，不强制后续采用高层 AI Services，MySQL 始终保留完整已提交历史。

重试仅适用于正文输出前的瞬时网络错误和 429/5xx，并受原总 deadline、次数和退避限制；凭证/参数错误及输出后的错误不重试。
有限重试不保证上游只调用或计费一次。
