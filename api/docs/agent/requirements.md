# Agent 基础与对话接口需求

状态：`add-agent-foundation-conversation-api` 的需求基线。实现与实际验收证据见 [验收记录（本目录入口）](README.md#verification)，Apifox 导入和可控模型协议验收仍待完成，不能宣称全部验收通过。运行方式见 [手工验证和运行说明（本目录入口）](README.md#manual-verification)，API 与 SSE 字段见 [接口契约（本目录入口）](README.md#api-design)。

## 1. 目标和范围

为消费者和商户员工提供登录后、本人会话范围内的流式 LLM 对话，保存可审计的完整历史，为后续四个技术亮点及前后端分离、Nginx 多实例部署提供基础。角色沿用认证模块的 `CONSUMER`、`MERCHANT_STAFF`，客服只是员工的业务称呼，不新增角色。

| 运行时子技术 | 本 change 交付 |
| --- | --- |
| LangChain4j 模型调用 | 统一 OpenAI Chat Completions 兼容请求与流式响应，使用 base-url/api-key/model 切换服务 |
| Prompt 与消息组装 | 服务端 system、当前 user、最近成功完整轮次，受消息数量及输入预算限制 |
| MySQL 持久化 | 本人会话、用户/助手消息、幂等标识、运行状态、必要模型/Prompt 元数据 |
| 对话执行控制 | 同会话串行、跨实例共享运行权、防重复提交、超时、断开取消、有限重试和错误 |
| SseEmitter 流式输出 | HTTP POST + start/token/done/error；断线后查询已提交历史 |
| 认证与隔离 | 可信 Actor 注入、商户+账号+角色本人范围、隐私保护 |

OpenAPI 是契约，Flyway 是迁移工具，TDD 是开发方法，Apifox 是联调工具，验收是交付关卡，不将它们另算作运行时子技术。

## 2. 用户与授权

- 消费者和员工均可创建、查询、发送消息和归档本人会话。员工不能因为同商户就查看消费者或同事的会话。
- 所有对话请求要求有效 Bearer access；复用认证对 JWT、Redis 登录会话和当前账号/商户状态的验证，不提供匿名入口。
- 商户、账号、角色只取可信 Actor。请求不能覆盖身份、system Prompt、上游消息角色、模型服务地址、模型 key 或工具定义。
- 未认证 401；有效身份的明确角色越权 403；跨租户、他人和不存在资源统一 404，不能先全局查资源再决定是否拒绝。
- Service 和 SQL 同时执行商户与必要本人账号/角色条件。聊天 sessionId 与认证 sid 是两种标识，不能互相替代。

## 3. 会话、消息和上下文

1. 会话有 `ACTIVE`、`ARCHIVED` 两种生命周期状态。归档为逻辑操作，重复归档幂等，历史仍可读；运行中归档返回 busy。
2. 新消息必须先可靠保存用户消息和助手占位，再启动模型。每轮关联一个 runId、两个 messageId 及有序消息序号。
3. `clientMessageId` 标识一次用户发送意图。同会话相同 key+相同正文返回原运行视图；不同正文返回幂等冲突。此检查先于 busy/归档判断，但始终先校验身份和归属。
4. 同会话最多一个有效运行，不同会话独立执行。竞争失败不能写入第二条用户消息或启动第二次调用，包含请求命中不同实例的情况。
5. 助手状态区分 `STARTED`、`STREAMING`、`COMPLETED`、`CANCELLED`、`TIMED_OUT`、`FAILED`；完成、取消、超时和失败均为不可改写的终态。
6. MySQL 是已提交历史与状态的事实来源。正常结束保存最终/有限部分文本、终态、脱敏错误码及可得用量；进程崩溃前未提交的 token 不保证恢复。
7. 上下文固定保留服务端 system 与本轮用户输入，只从最近成功完成的完整轮次向前选取历史，为输出预留预算。失败/取消/超时轮次仍可查询，但不作为成功上下文注入。
8. 条数或预算超限时淘汰旧完整轮次，不静默截断当前用户输入；system+当前输入单独超限时明确拒绝。供应商分词不匹配时采用保守估算，不声称跨模型精确 token 计数。

## 4. 模型、SSE 和故障

- LangChain4j 负责 OpenAI Chat Completions 协议转换和上游流解析；应用负责业务消息映射、授权、上下文、终态及对外 SSE。当前只发送 system/user/assistant，不开放客户端任意 role/tools。
- 通过 base-url、api-key、model 切换兼容服务；单部署仅一套活动配置，不依赖 Anthropic Messages 或供应商专有字段，不自动故障切换。
- Agent 启用时校验配置，非法配置拒绝启动；禁用时已有认证等功能不要求模型凭证。调用使用的 Prompt 版本、服务标签和模型名可归因，key 仅在忽略的本地配置或环境注入。
- 新发送建立 SSE，顺序为 start、零至多个 token、done 或 error。done 表示最终状态已成功保存；用量未知为 null，不能用零伪装为实际用量。citation/tool_call 只保留未来扩展，不发送伪造内容。
- 断开后停止转发并请求上游取消；首 token/总超时有上限。迟到回调不能覆盖终态或后继 run。无法保证 error 送达断开的连接，也无法撤销供应商已产生的费用。
- 仅正文输出前的瞬时网络错误或可重试 429/5xx 可在同一总 deadline 内有限重试。输出后故障、密钥/参数错误不重试；重试不保证供应商只计费一次。
- 运行权保存 deadline，原实例崩溃后存活实例可按需终结过期运行，防止永久 busy；不能自动重生成旧回答或误释放后继运行。
- 响应、事件及常规日志不泄露密钥、token、完整私密 Prompt、内部堆栈或原始手机号；日志只保留必要关联字段、模型标签、耗时和终止原因，不记录正文。用户自己输入的消息内容属于本人历史，不能据此把隐私发送给他人或写入日志。

## 5. 场景验收矩阵

下列行逐一对应本 change 两份 delta spec 的全部 32 个 `Scenario`。英文名称是 spec 中的精确场景名，用于追踪；本表定义验收标准，逐项证据和实际状态以 [验收记录（本目录入口）](README.md#verification) 为准。

### 5.1 agent-runtime（14 个场景）

来源：[agent-runtime spec（本目录入口）](README.md#agent-runtime-spec)。

| 编号 | Spec Scenario | 验收标准 | 后续证据 |
| --- | --- | --- | --- |
| RT-01 | Trusted context is used for a run | merchantId/accountId 等覆盖字段 400；模型不收到伪造身份 | DTO/HTTP 用例、替身输入 |
| RT-02 | Runtime returns a typed terminal result | runId 关联成功/取消/超时/失败；只完成输出用 done，其余为相应 error，不标假成功 | 状态机、流事件、历史 |
| RT-03 | Invalid model configuration is detected | 启用且配置非法时拒绝启动、不回显密钥；禁用不阻塞认证 | 配置校验用例 |
| RT-04 | Prompt version is traceable | 每轮保存当次 Prompt 版本、脱敏服务标签及模型名，后续配置变化不改历史 | 按 runId 的归因断言 |
| RT-05 | A compatible service is selected by configuration | 配置可切换兼容服务且 HTTP/SSE 不变；不暴露 key | 本地 HTTP stub、真实服务联调 |
| RT-06 | A long conversation exceeds the input budget | 只保留预算内最近成功完整轮次、system 和当前输入，为输出预留；完整历史仍可读 | 上下文替身、分页历史 |
| RT-07 | A failed answer exists in history | 失败轮次可查但其未完成回答不注入新请求 | 两轮连续用例 |
| RT-08 | A successful streamed response is assembled | delta 有序组装，done 前已落库，用量未知为 null | SSE 契约、消息正文比对 |
| RT-09 | A stream reports a dependency failure | start 后故障仅一个安全 error，不发 done、不泄露原始错误 | 上游故障替身 |
| RT-10 | Client disconnects during generation | 停止转发、请求取消、保存取消状态，部分文本不标完成 | 断开/取消观察点、历史 |
| RT-11 | Request exceeds the deadline | 超时后终结运行并释放资源，可写时发 TIMEOUT，迟到结果无效 | 可控时钟/延迟替身 |
| RT-12 | A retryable upstream error is retried once | 正文输出前、同 run/deadline 内有限重试，无重复用户消息或供应商切换 | 调用次数、关联 ID |
| RT-13 | Upstream fails after tokens have been forwarded | 已发送正文后不自动重生成，保存失败并结束流 | token 后故障用例 |
| RT-14 | An authorization error is rejected before model access | 401/403/404 在模型前返回，不创建助手消息或模型费用 | HTTP 拒绝及零调用断言 |

### 5.2 agent-conversation（18 个场景）

来源：[agent-conversation spec（本目录入口）](README.md#agent-conversation-spec)。

| 编号 | Spec Scenario | 验收标准 | 后续证据 |
| --- | --- | --- | --- |
| CV-01 | A consumer creates a session | 201+Location，身份来自 Actor，字符串 ID、UTC 时间，覆盖字段被拒绝 | 创建接口契约 |
| CV-02 | Staff tries to read a consumer or colleague session | 同商户员工查消费者/同事会话与不存在同为 404 | 多账号隔离用例 |
| CV-03 | A user attempts cross-tenant access | 详情、归档、历史、发送越界均无存在性泄露 | 两租户 fixture |
| CV-04 | A message is submitted with an idempotency key | 同 key/正文返回原标识和状态，先于 busy 判断，无新消息/调用/流重放 | 按 key 定位、调用计数 |
| CV-05 | An idempotency key is reused with different content | 409 CHAT_IDEMPOTENCY_CONFLICT，不改原消息 | 幂等冲突用例 |
| CV-06 | A previous run ended abnormally | 保留原用户消息、助手终态和错误，允许新 key 继续问答 | 历史与后续 run |
| CV-07 | A valid message opens a stream | 本人新消息返回 200 SSE、start 和终止事件，可通过消息 ID 查结果 | HTTP 流、落库结果 |
| CV-08 | Invalid input is rejected before streaming | 空/超长/冲突/缺凭证返回相应 JSON 错误，不建流或调模型 | 输入/鉴权契约 |
| CV-09 | Browser submits a message with bearer authentication | POST+Bearer 可返回 SSE 或幂等 JSON，凭证不放 URL | fetch 示例、浏览器联调 |
| CV-10 | A client reconnects through a different application instance | 另一实例读取已提交历史/状态，不要求原实例或重启原 run | 两上下文共享库用例 |
| CV-11 | History is returned with pagination | 会话/消息排序明确、分页有上限、返回 nextCursor/hasMore，无无界全量读取 | 分页契约/边界用例 |
| CV-12 | Sensitive model data is present internally | 不输出内部私密 Prompt、模型 key、异常细节或系统附带隐私，不写正文日志 | 响应白名单、日志检查 |
| CV-13 | A session is archived twice | 重复归档幂等，原创建者和历史保持 | 归档重复用例 |
| CV-14 | A message targets an archived session | 新 key 返回 409 CHAT_SESSION_ARCHIVED，无新消息或模型调用 | 归档拒绝用例 |
| CV-15 | Two messages race in one session | 不同 key 竞争只有一个获得运行权，其余 busy，不交错回答 | 并发事务用例 |
| CV-16 | Different sessions run independently | 两会话并行，一个失败/取消不改另一个状态 | 独立会话用例 |
| CV-17 | Concurrent sends arrive at separate instances | 两实例同会话竞争只有一个有效 run，另一 busy | 两执行上下文共享库 |
| CV-18 | A process crashes while generating | deadline 后存活实例可终结旧 run、处理新消息；不永久 busy、重生成或接受旧回调 | 原执行者消失及过期恢复用例 |

补充协议边界：输入/标题/分页上限、同 key 在归档后重查、CORS 预检、缺失用量、流未终止即 EOF 等用例见接口契约。Service/HTTP/模型协议的证据见对应测试；Apifox 未通过项单独保留，不以自动化结果代替。

## 6. 断言与验证原则

| 测试类型 | 断言什么 |
| --- | --- |
| 确定性模型替身 | 精确断言预设 delta 拼接文本、上下文、调用次数和取消/超时行为 |
| 协议/HTTP/SSE | 状态、MIME、字段类型、事件顺序及 run/message/request 关联；按一个 run 统计事件次数是合理的 |
| 数据库/隔离 | 按商户业务编码、sessionId、clientMessageId、runId 定位记录，断言唯一性、归属、内容和终态；不用共享整表总数或空表假设 |
| 本地兼容协议 stub | 请求路径、Bearer、model、roles、stream、错误映射和分片解析，不使用外部 key |
| 真实模型 Apifox 联调 | 协议连通、非空回答、终态与历史一致；不精确匹配随机自然语言，不声称完成 RAG/Tool 事实质量评测 |

MySQL/Redis 完整验证使用独立 Testcontainers，不访问 dev Compose 卷；Redis 仍用于既有认证，不新增 ChatMemory 状态。Docker 不可用时完整验证明确失败。演示数据与测试 fixture 分离。本文不创建 fixture、迁移或测试代码。

## 7. 后续边界与最终集成

| 后续工作 | 本模块保留的接入条件 | 本模块不声称完成的部分 |
| --- | --- | --- |
| RAG | 可替换上下文组装、模型调用、citation 事件扩展 | 文档库、向量检索、重排、引用内容、评测与 Badcase |
| Function Calling | 可信 Actor 和受控 run，tool/tool_calls 消息扩展 | 实际业务工具、参数/授权规则、结果脱敏与降级 |
| RabbitMQ | 文档导入独立于聊天事务，结果可供 RAG 使用 | 队列、任务表、ACK/确认/重试/死信与人工重投 |
| Redis ChatMemory | MySQL 全量历史及有限历史策略 | Redis 窗口/摘要、TTL、过期恢复和记忆层 token 管理 |
| 独立前端 | 六个 HTTP 操作、fetch SSE、Bearer、CORS 及断流恢复契约 | 完整聊天页面、登录 UI、浏览器实测 |
| Nginx 多实例 | 共享历史、幂等与运行 deadline，无单机唯一锁、无需粘性会话 | 真实 Nginx 配置部署、多进程负载均衡和端到端压测 |

前端只持有现有认证 access/refresh，不能持有模型 API key。未来 Nginx 需检查关闭 SSE 缓冲/缓存、心跳与代理超时；本 change 只验证共享存储边界，不把文档或两个执行上下文测试当作已部署集群。第 6、7 组逐项状态见 tasks.md 和验收记录。
## 8. Service 实施补充

会话 Service 由可信 Actor 的 merchantId/accountId/role 限定本人范围，员工无额外查看权限。
跨商户、他人和不存在资源统一 404；重复归档保留首次 archivedAt；有效运行中归档返回 CHAT_SESSION_BUSY。
`ChatServiceIT` 通过独立 MySQL 的 Service 接口验证以上规则。
