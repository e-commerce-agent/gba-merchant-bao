# Design

## Context

动机与范围见 proposal.md，行为契约见 specs/agent-runtime/spec.md 和 specs/agent-conversation/spec.md。当前工程为 JDK 21、Spring Boot 3.5、Spring MVC、MyBatis、MySQL 和 Flyway；认证 Actor 已提供租户、账号和角色，认证登录会话依赖 Redis。当前结构迁移为 V1–V4，尚未引入 LangChain4j。

本次是“Agent 基础与对话接口”一个 change。验收目标是能够调用兼容模型、流式回答、保存本人会话和消息，为四个技术亮点及未来前后端分离、多实例部署提供基础。设计不以复杂运行框架或提前搭建后续模块为目标。

## Goals / Non-Goals

**Goals:**

- 使用 LangChain4j 的 OpenAI 集成完成 Chat Completions 兼容交互，以 baseUrl/apiKey/model 切换服务。
- 使用 SseEmitter 提供可供独立前端调用的 POST 流式接口。
- 保存完整历史、归属、幂等和运行终态；限制上下文规模，正确处理断开、超时和错误。
- 通过共享数据库维护运行权，让将来的 Nginx 负载均衡不依赖粘性会话。
- 按需求/API → Flyway → 分项 TDD → Apifox → 验收交付。

**Non-Goals:**

- Redis ChatMemory、历史摘要、缓存 TTL/恢复、知识库/RAG、实际 Function Calling、RabbitMQ 文档任务均由后续 change 实现。
- 不做匿名问答、客服查他人会话/接管、自动多供应商切换、SSE 事件重放或跨实例接续生成。
- 不引入 WebFlux、WebSocket、通用工作流引擎、完整监控平台；不在本 change 部署完整前端和 Nginx 集群。
- 现有认证继续使用 Redis；“暂不做 ChatMemory”不代表移除认证 Redis 或让全链路测试脱离 Redis。

## Decisions

### 1. 六项运行时子技术与后续亮点

| 子技术 | 本期交付 | 后续接入关系 |
| --- | --- | --- |
| LangChain4j 模型调用 | OpenAI Chat Completions 兼容普通/流式模型适配、配置和确定性测试替身 | RAG 和 Function Calling 复用模型边界 |
| Prompt 与消息组装 | system/user/assistant、最近完整轮次、输入预算 | RAG 注入检索内容；FC 接工具消息；ChatMemory 替换历史选择策略 |
| MySQL 会话与消息持久化 | 完整历史、归属、状态和幂等 | 为审计及未来 ChatMemory 过期恢复提供事实来源 |
| 对话执行控制 | 串行、幂等、deadline、重试、断开取消和错误 | 后续检索/Tool 执行加入同一次受控 run |
| SseEmitter 流式输出 | start/token/done/error、心跳和断开监听 | 后续扩展 citation/tool_call 事件 |
| 认证与数据隔离 | Actor、商户+账号+角色归属校验 | 后续知识可见性和只读 Tool 身份注入 |

上述顺序表达职责，开发顺序以 tasks.md 为准。Flyway 是演进工具，TDD 是开发方法，OpenAPI 是契约，Apifox 是联调工具，验收是交付关卡，不另外称作运行时子技术。

后续 RAG change 完成商品资料/政策/规则的切分、Embedding、Query 改写、混合检索、Rerank、来源引用和评测；Function Calling change 接真实商品/库存/订单/物流 Service，负责身份注入、参数校验、脱敏和受控调用；RabbitMQ change 完成文档异步导入、确认/ACK、幂等、重试/死信及重投；Redis ChatMemory change 完成窗口、token 管理、摘要、隔离和过期恢复。RabbitMQ 消费者更新文档导入状态，不进入本期 HTTP 聊天事务。

### 2. 模型协议由 LangChain4j 处理，业务转换由应用处理

依赖集中管理 LangChain4j 及其 OpenAI 集成，实际 SDK 类型和取消接口在实现时按锁定版本验证；OpenAICompatibleChatModel 是统一适配职责的称呼，不宣称它是官方类名。不要分别实现 DeepSeekAdapter、QwenAdapter，不手写供应商 HTTP/SSE 协议解析。

```text
可信 Actor + MySQL 历史 + 当前输入
  → 应用选择上下文并映射为 LangChain4j 消息
  → LangChain4j OpenAI 集成序列化 Chat Completions 请求、解析上游流
  → 应用汇总模型回调、维护 run 状态
  → SseEmitter 转换为对外 SSE 事件
```

应用使用一个小的模型适配边界支持确定性替身，不预建插件系统。本期不强制使用高层 AI Services，也不通过框架隐式维护 ChatMemory；后续接 RAG/FC 时按真实需要选择编排方式。tool/tool_calls 属于后续模型消息扩展点，不开放客户端自定义 system、role、tools 或身份字段，也不提前发送空工具定义。

```yaml
merchant:
  agent:
    enabled: false
    base-url: https://note3-prev-api.askdiandian.com/v1
    api-key: ""
    api-key-header: api-key
    model: dots3-note-prev
```

base-url 定义为 SDK 使用的 API 根地址（是否包含 /v1 以服务商实际路径为准），不填写 /chat/completions 终端路径；用本地 HTTP stub 验证最终请求路径，避免重复追加路径。仅允许应用配置指定地址，客户端不能指定模型 URL。兼容协议不保证所有供应商扩展能力一致，本期不依赖 reasoning_content 等专有字段。

温度、输出上限、Prompt 版本、连接/首 token/总超时、最多重试次数、输入预算和分页上限也纳入配置。启用时校验配置，禁用时已有认证/基础功能不要求模型凭证、Agent 接口返回稳定的不可用错误。dev 真实配置直接放忽略的 application-dev.yml，根 .env 只供 Compose 使用，不要求 IDEA 设置额外环境变量；prod 沿用独立部署配置；密钥、完整配置和消息正文不得出现在日志中。鉴权头默认 Authorization Bearer；dots 等兼容服务按文档设 api-key-header: api-key，由同一 SDK 的 customHeaders 发送原始 key。部署切换 base-url/api-key/model 及所需鉴权头后重启生效；单次部署只有一个活动供应商。

### 3. 上下文只做必要的有界组装

MySQL 保留完整历史，模型输入只含服务端 system、当前用户消息和同会话最近成功完成的轮次。设置历史条数与输入预算上限，先预留输出额度，再从最近完整轮次向前选取，避免孤立 assistant 或半轮消息。失败、取消、超时的旧轮次不进入成功上下文；当前用户输入不重复加入。

优先使用选定模型可用的 token 估算；国产模型 tokenizer 不匹配时采用保守估算并明确为近似，不宣称跨模型精确。system+当前输入已超预算时返回 400/AGENT_CONTEXT_TOO_LARGE，不截断用户输入。首期不做 Query 改写、摘要或 Redis 窗口，也不为尚未实现的记忆模块预建空类。

### 4. 对话接口与前后端边界

JSON 使用 code/message/data/requestId，ID 为十进制字符串、时间为 UTC ISO-8601。所有对话接口要求 Bearer，消费者与员工都只操作本商户本人创建的会话；SQL 条件必须包含 merchant_id、owner_account_id、owner_role。角色不允许 403；跨商户/他人/不存在统一 404；未声明 DTO 字段包括 merchantId/accountId/role/system 均 400。

| 方法与路径 | 输入/结果 |
| --- | --- |
| POST /api/v1/chat/sessions | {title?} → 201 + Location + 会话摘要；未填 title 用固定默认值，不额外调用模型生成标题 |
| GET /api/v1/chat/sessions | cursor?、size? → 200 本人会话分页 |
| GET /api/v1/chat/sessions/{sessionId} | 200 会话摘要、activeRunId/当前运行状态（可空） |
| POST /api/v1/chat/sessions/{sessionId}/archive | 无业务 body → 200 归档摘要；重复幂等；运行中返回 409 busy |
| GET /api/v1/chat/sessions/{sessionId}/messages | cursor?、size? → 200 完整消息分页及终止状态 |
| POST /api/v1/chat/sessions/{sessionId}/messages | {content,clientMessageId} → 新消息 200 SSE；合法重复 200 JSON 原运行视图 |

会话默认按 updatedAt/id 倒序分页，消息按 messageSeq 升序分页；游标为相应排序值，size 有上限。摘要包含 sessionId/merchantId/title/status/createdAt/updatedAt；消息包含 messageId/messageSeq/runId/senderType/content/status/errorCode/createdAt/completedAt 以及可得 usage。客户端仅能提交用户正文和 UUID clientMessageId，不能上传任意上游角色。

浏览器使用 fetch POST，携带 Authorization、Content-Type: application/json、Accept: text/event-stream, application/json，先判断响应 Content-Type 再处理 SSE 或幂等 JSON。凭证沿用现有按店铺/类型选择约定，不放 URL；不使用无法直接设置 POST/Bearer 的原生 EventSource。前后端不同源时只允许显式配置的 origin、必要方法及 Authorization/Content-Type/X-Request-Id，放行合法预检并暴露 X-Request-Id/Location；不使用任意来源放行。也可由未来 Nginx 统一域名转发 /api。

### 5. SSE 与生命周期

使用现有 Spring MVC 的 SseEmitter，模型异步回调在有界执行器上运行；把可信 Actor 和 requestId 显式传入异步流程，不依赖已被过滤器清除的线程上下文。串行写事件并限制输出大小/待发送数据，慢客户端或写失败时终止，避免无限线程和无界队列。

事件公共字段为 runId、sessionId、messageId（助手消息）、requestId，id 使用 runId:seq，仅用于排序诊断，不支持重放。start 额外返回 userMessageId；token 包含 delta；done 包含 COMPLETED、可得 usage（缺失为 null）；error 包含稳定 code、message、terminalStatus。完成前先提交助手终态，再发送 done。citation/tool_call 只在契约中记录后续扩展语义，本期不发虚假事件，也不预建引用/工具结果表。

STARTED/STREAMING 可转为 COMPLETED、CANCELLED、TIMED_OUT、FAILED，终态不可覆盖。连接可写时最多一个 done/error；断开无法保证送达终止事件，最终以 MySQL 为准。发送 SSE 注释心跳，不产生模型 token，也不续期认证登录会话。取消由前端 AbortController 关闭连接触发，不新增跨实例取消 API。

只对正文输出前的瞬时网络错误、可重试 429/5xx 进行有限退避重试，遵守原总 deadline，晚于正文输出的错误不从头生成。密钥、参数和授权失败不重试。即使 token 尚未送达，也不能保证供应商未计费，重试不是上游 exactly-once。

### 6. 表结构、幂等与共享运行权

结构迁移按当前 V4 向后，暂定 V5__agent_conversation.sql。先按现有编号开发，不等商品/订单模块；模块汇合时只协调尚未执行的迁移，已执行迁移不得改名或改 checksum，冲突通过后续前向迁移处理。

使用现有 Snowflake ID、MySQL InnoDB、UTC DATETIME(3) 和审计约定，新增两表即可：

| 表 | 主要字段与约束 |
| --- | --- |
| chat_session | id、merchant_id、owner_account_id、owner_role、title VARCHAR(200)、status ACTIVE/ARCHIVED、created_at、updated_at、archived_at、archived_by、next_message_seq、active_run_id、run_deadline_at；PK(id)，UNIQUE(merchant_id,id)，索引(merchant_id,owner_account_id,owner_role,updated_at,id) |
| chat_message | id、merchant_id、session_id、message_seq、sender_type USER/ASSISTANT、sender_account_id、client_message_id、content LONGTEXT、status、run_id、error_code、prompt_version、model_service_label、model_name、input_tokens/output_tokens（可空）、created_at、completed_at；FK(merchant_id,session_id)→chat_session(merchant_id,id)，UNIQUE(merchant_id,session_id,message_seq)，UNIQUE(merchant_id,session_id,client_message_id)，UNIQUE(merchant_id,session_id,run_id,sender_type) |

USER 必须有 sender_account_id/client_message_id，ASSISTANT 的这两个字段为空；MySQL 唯一键允许多个 NULL，不用含空 sender_account_id 的联合键证明用户消息幂等。记录号全局唯一不能替代租户授权；索引均以 merchant_id 为首列，角色/所有者关系由 Service 根据真实账号表核验。内容长度在 DTO 与服务端按配置限制，LONGTEXT 不代表允许无界输入；消息状态和 sender_type 用 CHECK 或等价约束保持合法。

提交事务按归属锁定会话行，先按 clientMessageId 查已存在消息并核对正文，再检查是否归档/busy；取得运行权、分配 user/assistant 序号并插入两条记录后提交。运行不持有长事务。相同 key+内容返回 JSON {sessionId,runId,userMessageId,messageId,status}；相同 key+不同内容返回 CHAT_IDEMPOTENCY_CONFLICT。数据库唯一键兜底并发，不把异常转为重复模型调用。

终态事务核对 active_run_id 与原 runId 后更新助手状态、释放运行权；回调也检查自身 deadline，过期回调不能提交成功。下次发送或状态查询发现 deadline 已过时，可以在数据库条件更新中终结旧运行并释放，防止实例崩溃后永久 busy；旧 run 不得覆盖后继 run。运行状态/终态写入失败不发送 done，返回可写的 error 并留待过期恢复，不假报已持久化。

ACTIVE/ARCHIVED 表示会话生命周期，与运行状态分开；归档运行中的会话返回 busy。完整历史指已提交消息和终态，不承诺进程崩溃前逐 token 保存；正常结束时保存最终或有限部分正文，避免每个 token 都写库。

### 7. Nginx 多实例的必要基础

每条 SSE 连接由承接实例处理，共享 MySQL 保存消息、幂等和运行权，共享 Redis 继续服务认证。后续请求可以落到任意实例；不建立以单机内存为唯一依据的会话或锁，不要求粘性会话。断线后查询历史，不将流自动迁移到其他实例。

本期通过两个独立业务执行上下文共用隔离数据库的集成测试验证运行权和幂等，而不是搭完整集群。文档列出后续 Nginx 集成检查：关闭 SSE 代理缓冲、避免缓存，proxy_read_timeout 大于心跳间隔，应用总超时有界；确认响应头、预检及流式解析正确。实际 Nginx、多后端进程和前端页面联调由后续部署集成验收，不将文档和共享库测试称为已部署集群。

### 8. 开发流程与测试断言

先将需求/验收和 API 契约写入 api/docs/agent，再完成数据设计；每个实现单元遵循红→绿→重构，同时交付对应测试和维护说明，不等全部代码写完才补测试。

| 测试层 | 断言对象 |
| --- | --- |
| 模型替身单测 | 给定输入历史和预设 delta，精确断言上下文选择、顺序、最终文本、取消与失败状态 |
| OpenAI 兼容适配契约 | 本地 HTTP stub 检查 /chat/completions 路径、默认 Bearer/可配置 api-key 头、model、roles、stream、分片解析及错误映射，不访问真实 API |
| MySQL/认证集成 | 按 merchant/session/clientMessageId/runId 定位记录，断言唯一性、归属、内容与终态；不用整表总数或空表假设 |
| HTTP/SSE | 状态码、JSON schema、Content-Type、事件顺序、公共标识、终态互斥和已落库文本；按 run 断言事件数量是合理的 |
| 真实模型 Apifox | 实际协议联通、非空回答、完成状态、历史一致和隐私；不精确匹配固定自然语言，也不宣称完成 RAG/Tool 事实评测 |

存储集成使用独立 MySQL Testcontainer；带真实认证的 HTTP/完整验证额外使用 Redis Testcontainer。无固定端口、不连接 dev Compose 卷、不开容器复用；Docker 缺失时完整验证明确失败。测试 fixture 放 src/test/resources/db/fixture，dev 演示数据单独放 db/devdata，不复用演示密码。新增最小两租户/多本人会话数据用于演示和隔离。

OpenAPI 从实际 Springdoc 导出 docs/api/agent-conversation-v1.openapi.yaml，覆盖六操作、POST 消息两种成功 MIME 和 SSE 示例。Apifox 导入后分别记录协议验收与真实兼容模型联调，手册提供客户端中止连接/历史恢复与 curl 备用验证，不假设特定 Apifox 版本支持所有长流断言。真实模型不可用就如实留待验收，不用 fake 冒充真实联调。

日志记录 requestId/runId/商户/角色、服务标签、模型、Prompt 版本、首 token/总耗时和终止原因，不记录正文、完整 Prompt、token、密钥或原始手机号；本期只用现有日志/指标设施，不另建看板或调用记录平台。

## Risks / Trade-offs

- [兼容服务的路径、用量或分词差异] → 用本地协议契约测试锁定基本行为，缺失 usage 为 null，输入估算保守并保留输出额度。
- [断开不立即被网络/SDK 检测] → 注释心跳、写失败监听、取消句柄和总 deadline 配合；不承诺供应商立即停费。
- [实例崩溃或 MySQL 终态写入失败] → 共享 deadline、runId 条件更新和按需恢复，不假报完成、不自动重做旧请求。
- [完整历史增长] → 严格消息输入/输出和分页上限；摘要、冷热归档留待后续需要时设计。
- [迁移编号与其他业务模块汇合冲突] → 当前按 V5 继续，未执行编号在汇合时协调，已执行迁移只前向演进。

## Migration Plan

1. 交付需求/验收、API/SSE 和表结构设计后，按当前迁移头新增 V5，隔离库验证从空库和 V4 升级；已发布迁移不变。
2. 逐项 TDD 实现持久化、模型适配、执行与 SSE，默认不要求启用模型功能；在 dev 显式配置兼容模型并启动联调。
3. 导出实际 OpenAPI，在 Apifox 完成协议和真实模型两类验收，保存自动测试与人工联调证据；同步后端文档入口。
4. 回退时禁用 Agent、保留新增表和历史；已执行 Flyway 不倒改。完整前端、Nginx 多实例及四个亮点分别在后续 change/集成验收落地。
