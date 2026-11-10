# Spec Delta

## Purpose

提供面向消费者和商户客服的商户隔离对话入口，保存可审计的完整会话历史并以 SSE 交付实时回答。该能力以 MySQL 为历史事实来源，暂不引入 Redis ChatMemory，后续记忆优化必须兼容既有消息契约。

## ADDED Requirements

### Requirement: Users shall create and access only their authorized sessions

系统 MUST 提供创建、分页查询、详情查询和归档会话的 `/api/v1/chat/sessions` 资源；会话绑定商户、创建者账号与角色。CONSUMER 和 MERCHANT_STAFF 均 MUST 仅访问本商户本人创建的会话。所有对话接口 MUST 要求登录，本期不提供匿名问答、客服查他人历史或接管。

#### Scenario: A consumer creates a session
- **WHEN** 有效消费者在自己的商户上下文 POST `/api/v1/chat/sessions`
- **THEN** 服务创建 ACTIVE 会话并返回 sessionId、merchantId、title、status、createdAt 和 updatedAt，拒绝请求体中的主体覆盖字段；ID 为十进制字符串，时间为 UTC ISO-8601

#### Scenario: Staff tries to read a consumer or colleague session
- **WHEN** 已登录员工访问同商户消费者或其他员工创建的会话
- **THEN** 服务返回与不存在相同的 404，员工角色不能绕过会话本人归属

#### Scenario: A user attempts cross-tenant access
- **WHEN** 有效 Actor 使用另一商户或他人 sessionId 查询、归档或发送消息
- **THEN** 服务不泄露会话存在性，按资源契约返回 404；角色不允许的入口返回 403

### Requirement: Conversation messages shall be persisted before and after execution

用户消息和对应助手占位记录 MUST 在模型调用前可靠写入 MySQL；记录包含 messageId、runId、客户端幂等键和有序序号。运行终止时 MUST 保存助手最终文本或已收集的有限部分文本、终止状态、脱敏错误码和可得用量。历史查询 MUST 从 MySQL 返回，不以 SSE 连接或实例内存作为事实来源；进程崩溃前尚未提交的部分 token 不保证恢复。

#### Scenario: A message is submitted with an idempotency key
- **WHEN** 同一 sessionId、sender 和 clientMessageId 重复提交相同消息
- **THEN** 服务核验归属后返回原 run/message 标识与状态，不创建第二条用户消息或新模型调用；已存在记录的识别先于 busy 校验，不重新播放或订阅原 token 流

#### Scenario: An idempotency key is reused with different content
- **WHEN** 同一会话的 clientMessageId 已存在，但本次 content 不同
- **THEN** 服务返回 409/CHAT_IDEMPOTENCY_CONFLICT，不修改原消息，也不调用模型

#### Scenario: A previous run ended abnormally
- **WHEN** 客户端查询包含 timeout 或 cancelled 助手消息的会话历史
- **THEN** 历史保留用户消息、助手终止状态和可展示的错误码/摘要，后续消息可以在同一会话继续执行

### Requirement: The send-message endpoint shall expose an authenticated SSE contract

系统 MUST 提供 `POST /api/v1/chat/sessions/{sessionId}/messages`，仅接受 content 和 clientMessageId。新消息通过校验后返回 `text/event-stream`，响应头与事件关联 requestId。流建立前的错误 MUST 使用统一 JSON envelope；相同消息的合法幂等重试 MUST 返回 200 JSON 的原运行视图，不重新调用模型。

#### Scenario: A valid message opens a stream
- **WHEN** 会话属于当前 Actor 且请求通过校验
- **THEN** 服务返回 200、`Content-Type: text/event-stream`，发送 start 和后续终止事件，历史中可通过 messageId 查询本次结果

#### Scenario: Invalid input is rejected before streaming
- **WHEN** content 为空、超过配置长度、clientMessageId 重复但内容不一致，或请求缺少凭证
- **THEN** 服务返回 400/401/409 中适用的普通 JSON 响应，不建立 SSE、不调用模型

#### Scenario: Browser submits a message with bearer authentication
- **WHEN** 前端通过 POST 携带 Authorization Bearer、JSON 消息和 Accept 头请求 SSE
- **THEN** 服务返回约定的 JSON 或 SSE Content-Type，前端可用 fetch 区分解析；凭证不出现在 URL 中

### Requirement: Disconnected clients shall recover status through history

系统 MUST 提供 `GET /api/v1/chat/sessions/{sessionId}/messages` 分页历史，包含 messageId、runId 和生成状态。客户端断线后 MUST 能通过本人历史或原 clientMessageId 重试获取状态；本期 MUST NOT 承诺 Last-Event-ID 重放、恢复丢失 token 或跨实例接续生成。

#### Scenario: A client reconnects through a different application instance
- **WHEN** 流断开后客户端的本人历史请求被路由到另一个实例
- **THEN** 新实例从共享 MySQL 返回已提交的消息和运行状态，不要求请求命中原实例，不重复启动原 run

### Requirement: Session history shall be privacy-safe and bounded

历史响应 MUST 只返回当前 Actor 有权访问的消息字段、状态、引用/Tool 摘要占位、时间和用量；不得返回 Prompt 私密变量、模型密钥、内部堆栈、原始手机号或未脱敏的外部凭证。列表和详情 MUST 使用分页/数量上限，单条消息和会话标题 MUST 有明确长度上限。

#### Scenario: History is returned with pagination
- **WHEN** 客户端请求指定 page/size 或 cursor
- **THEN** 服务按会话更新时间和消息序稳定返回限定数量，包含 next cursor 或 hasMore，不执行无界全量读取

#### Scenario: Sensitive model data is present internally
- **WHEN** 运行上下文含系统 Prompt、API key、内部异常或用户隐私字段
- **THEN** 会话 API、SSE 和普通日志均只输出允许的脱敏摘要

### Requirement: Session lifecycle shall be explicit and idempotent

会话 MUST 支持 ACTIVE、ARCHIVED 状态；归档操作需校验 Actor、记录 archivedAt/archivedBy，并对重复归档幂等。归档会话不得接收新消息，服务返回稳定冲突错误；历史仍可读且不被物理删除。

#### Scenario: A session is archived twice
- **WHEN** 当前有权用户对同一会话重复发起归档
- **THEN** 首次归档成功，后续请求返回相同归档结果或幂等成功，不修改原始创建者和历史

#### Scenario: A message targets an archived session
- **WHEN** 客户端向 ARCHIVED 会话发送消息
- **THEN** 服务返回 409/稳定业务错误，不写入用户消息、不调用模型

### Requirement: A session shall preserve message order under concurrent sends

系统 MUST 利用共享持久状态保证同一 ACTIVE 会话最多一个有效 run，不能仅依赖单实例内存锁；不同 clientMessageId 在运行未结束时 MUST 返回 409/CHAT_SESSION_BUSY，且不写消息或调用模型。不同会话 MUST 可独立执行。归档进行中的会话 MUST 返回 busy，等待其终止后归档。

#### Scenario: Two messages race in one session
- **WHEN** 同一 Actor 几乎同时向同一会话提交两个不同的 clientMessageId
- **THEN** 只有先取得会话运行权的请求写入并执行，另一个收到 CHAT_SESSION_BUSY，不产生交错的助手消息

#### Scenario: Different sessions run independently
- **WHEN** 同一商户的两个会话同时提交消息
- **THEN** 两个会话可以分别建立 SSE 和 Agent run，任一会话的超时、取消或失败不改变另一会话状态

#### Scenario: Concurrent sends arrive at separate instances
- **WHEN** 两个共享同一数据库的后端实例同时收到同一会话的不同消息
- **THEN** 只有一个取得运行权，另一个返回 busy，不能各自创建一个有效 run

### Requirement: Abandoned runs shall not permanently block a session

运行权 MUST 有持久化 deadline，并在过期后通过条件更新将遗留助手消息置为失败或超时、释放运行权。恢复 MUST 核对原 runId 和 deadline，不能释放后继运行或接受过期 run 的迟到写入。该恢复 MUST 不依赖原执行实例仍然存活。

#### Scenario: A process crashes while generating
- **WHEN** 负责生成的实例退出，原 run 超过持久化 deadline 后用户再次发送消息
- **THEN** 存活实例可终结遗留 run 并处理新消息，保留原用户消息及失败状态，不永久返回 busy，不自动重新生成原回答
