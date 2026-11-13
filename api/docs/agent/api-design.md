# Agent 基础与对话接口契约

状态：`add-agent-foundation-conversation-api` 的接口设计基线，需求和场景矩阵见 [需求文档（本目录入口）](README.md#requirements)。Service 已按可信 Actor 实现本人会话与幂等归档：员工、同租户他人和跨租户资源同为 404，运行中归档为 busy。实际 HTTP/SSE 与快照以对应实现任务及测试为准；本文不表示通过 Apifox 验收。

## 1. 公共协议

### 1.1 认证、归属与字段校验

- 所有业务操作要求 `Authorization: Bearer <accessToken>`，只接受现有认证模块建立的可信 Actor。认证 Redis 或数据库失败仍沿用认证错误契约，不能只验 JWT 放行。
- CONSUMER、MERCHANT_STAFF 均只操作自己创建的会话；商户+账号+角色取 Actor，不取页面所在店铺、自定义商户头或请求字段。消费者、同事、同商户员工都没有查看他人会话的权限。
- 缺失/失效凭证先返回 401；有效身份的明确角色越权为 403；他人、跨商户和不存在资源统一 404。此六操作对两种现有角色都开放，403 不是同商户他人资源的替代错误。
- JSON DTO 严格拒绝未知字段，尤其是 merchantId/accountId/customerId/role/system/messages/tools/baseUrl/apiKey/model。服务端不能静默接受身份或模型覆盖。
- `sessionId`、`messageId`、`runId`、`merchantId` 为正十进制字符串，范围不超过有符号 BIGINT，不能用 JSON number 传递以免 JavaScript 丢精度。时间统一 UTC ISO-8601，带 `Z`，允许毫秒。
- `X-Request-Id` 遵循现有过滤器的接收/生成规则，响应头与当次 JSON 或 SSE 的 requestId 相同；HTTP 重试可使用新 requestId，不能把它作为消息幂等键。
- JSON 和流响应均 `Cache-Control: no-store`；不设置模型凭证 Cookie，不返回模型 API key 或完整私密 Prompt，不把正文/凭证写常规日志。

### 1.2 JSON envelope 与错误码

普通成功使用 `{code:0,message:"success",data,requestId}`；普通错误的 data 为 null。新消息的流响应使用 SSE，不再外包 envelope。幂等成功也是 code=0/message=success，通过响应 MIME 区分。

公共/认证错误码复用当前 `CommonErrorCode`。以下 13xxx 为本文冻结的 Agent/Chat 分配，已写入 `CommonErrorCode`；SSE 的字符串 code 与 JSON 数字 code 的映射在本表定义。

| HTTP（流前） | JSON code | 语义 / SSE code | 触发条件 |
| --- | --- | --- | --- |
| 400 | 10001 | INVALID_ARGUMENT | 非法 ID/UUID/JSON/游标、未知字段、空正文、长度或 size 超限 |
| 401 | 10002 | UNAUTHORIZED | access 缺失、无效、过期或不再活跃 |
| 403 | 10003 | FORBIDDEN | 有效身份的明确角色越权 |
| 404 | 10004 | RESOURCE_NOT_FOUND | 会话不存在、跨商户或非本人，同一消息 |
| 500 | 10500 | INTERNAL_ERROR | 未分类内部异常，不暴露堆栈 |
| 503 | 11501 | AUTH_DEPENDENCY_UNAVAILABLE | 既有认证依赖 MySQL/Redis 不可用 |
| 400 | 13001 | AGENT_CONTEXT_TOO_LARGE | system+当前输入超过有效输入预算，不能靠淘汰历史解决 |
| 409 | 13002 | CHAT_SESSION_BUSY | 同一会话已有有效运行，或试图归档运行中的会话 |
| 409 | 13003 | CHAT_IDEMPOTENCY_CONFLICT | 原 clientMessageId 已用于不同正文 |
| 409 | 13004 | CHAT_SESSION_ARCHIVED | 归档会话接收未曾提交过的新消息 |
| 503 | 13501 | AGENT_DISABLED | Agent 生成能力未启用；不会阻止本人已有历史查询 |
| 503 | 13502 | DEPENDENCY_ERROR | 模型连接/协议/上游错误，或本次运行资源不可用 |
| 504 | 13503 | TIMEOUT | 流前出现的首 token/总超时；流后以 error 表达 |
| 503 | 13504 | CHAT_DEPENDENCY_UNAVAILABLE | 聊天历史、消息接收或终态存储不可用 |
| 不适用 | 不适用 | CANCELLED | 仅流内 error/历史终态使用；不新增 HTTP 取消操作 |

上游的 401/403 不转为浏览器登录 401/403，映射 DEPENDENCY_ERROR，避免触发前端错误刷新；上游 429/5xx 可在正文输出前有限重试，耗尽后同样是 503/13502。本期不新增匿名/聊天限流器；已有认证限流如实际命中，保留原 429/11003/Retry-After。415（不支持的请求 MIME）和 406（不能协商任一支持的成功 MIME）使用 10001，未建流前仍返回安全 JSON 错误。

JSON 错误示例（所有 404 不透露到底是跨租户、他人还是不存在）：

```json
{
  "code": 10004,
  "message": "resource not found",
  "data": null,
  "requestId": "chat-denied-001"
}
```

### 1.3 输入、正文及分页上限

| 字段 | 契约 |
| --- | --- |
| title | 可选字符串；省略或 trim 后为空使用固定“新对话”，不调用模型；非空 trim 后最多 200 个 Unicode 码点；显式 null/非字符串 400 |
| content | 必填字符串，非空白；默认上限 4000 个 Unicode 码点，部署可配置更低的有效上限并在联调配置注明；不得超过 4000；空白检验不修改实际正文 |
| clientMessageId | 必填标准小写 UUID 字符串（36 个 ASCII 字符，形如样例）；标识一次发送意图，与身份、requestId 和 runId 不同 |
| size | 可选整数，默认 20，范围 1–100；越界 400，不静默放大/截断；拒绝重复值及非法格式 |
| cursor | 可选服务端生成的不透明游标，首请求不传；非空、最多 512 个 ASCII 字符，非法/用于错误资源种类时 400；无末页游标 |

幂等比较解码后的 content 原字符串，保留换行和首尾空白，不做大小写/Unicode 归一或 trim。相同 UUID 搭配不同空白/正文即冲突。用户修正内容或主动重问必须创建新 UUID，网络重试必须复用原 UUID 和正文。当前输入即使未超 4000，仍可能因 system+输入预算超限返回 AGENT_CONTEXT_TOO_LARGE。

会话列表同时包含 ACTIVE/ARCHIVED，按 `updatedAt DESC, sessionId DESC`；消息按 `messageSeq ASC`，初始页从最早消息开始。两种分页均返回 `{items,nextCursor,hasMore}`；末页 nextCursor=null、hasMore=false，无记录返回空数组。游标仅表达排序位置，不是授权凭证，后端每次都重新限定 Actor 范围。会话 updatedAt 可因新消息更新，跨页不承诺快照一致性；前端按 sessionId 去重并按需刷新首页。消息终态更新不改变消息序号。

## 2. 公共输出模型

### 2.1 ChatSessionSummary

```json
{
  "sessionId": "300000000000000001",
  "merchantId": "100000000000000001",
  "title": "充电器咨询",
  "status": "ACTIVE",
  "createdAt": "2026-11-08T08:00:00Z",
  "updatedAt": "2026-11-08T08:03:12Z",
  "archivedAt": null,
  "activeRunId": null,
  "activeRunStatus": null
}
```

上述字段均返回。status 仅 ACTIVE/ARCHIVED；没有有效运行时 activeRunId/activeRunStatus 均 null，有运行时状态仅 STARTED/STREAMING。归档时 archivedAt 为时间且 activeRunId 为空。只返回本人会话，不额外返回账号手机号、认证 sid 或模型配置。

### 2.2 ChatMessageView

```json
{
  "messageId": "400000000000000002",
  "messageSeq": 2,
  "runId": "500000000000000001",
  "clientMessageId": null,
  "senderType": "ASSISTANT",
  "content": "你好，请问有什么可以帮你？",
  "status": "COMPLETED",
  "errorCode": null,
  "inputTokens": null,
  "outputTokens": null,
  "createdAt": "2026-11-08T08:03:12Z",
  "completedAt": "2026-11-08T08:03:14Z"
}
```

messageSeq 是会话内递增的正整数，USER 和 ASSISTANT 各占一条；两条共享同一个 runId。USER 的 clientMessageId 为提交 UUID，状态 COMPLETED 表示“已可靠接收”，不表示助手完成，completedAt 是接收完成时间；USER 无用量或错误。ASSISTANT 的 clientMessageId=null，状态 STARTED/STREAMING/COMPLETED/CANCELLED/TIMED_OUT/FAILED；开始时 content 可为空、completedAt=null，终态后 completedAt 为时间。errorCode 为第 1.2 节的字符串标识或 null。token 用量为可空的非负整数，未提供时 null，不能估算成供应商计费用量。输出不包含 system 消息、内部异常和模型 key。

### 2.3 ChatRunView（幂等结果）

```json
{
  "sessionId": "300000000000000001",
  "runId": "500000000000000001",
  "userMessageId": "400000000000000001",
  "messageId": "400000000000000002",
  "status": "STREAMING"
}
```

messageId 固定指向助手，status 为该助手当前已提交状态，不保证反映毫秒级上游进度。客户端按此标识在消息历史中定位正文；不返回可再次生成的凭证或 SSE 订阅地址。

## 3. 六个操作

| # | 方法与路径 | 请求 | 成功 | 主要业务失败 |
| --- | --- | --- | --- | --- |
| 1 | POST /api/v1/chat/sessions | `{title?}` | 201 JSON + Location，会话摘要 | 输入 400，存储 503 |
| 2 | GET /api/v1/chat/sessions | cursor?/size? | 200 JSON，本人会话分页 | 游标/size 400，存储 503 |
| 3 | GET /api/v1/chat/sessions/{sessionId} | 无业务 body | 200 JSON，会话摘要 | 非本人/不存在 404，存储 503 |
| 4 | POST /api/v1/chat/sessions/{sessionId}/archive | 无业务 body | 200 JSON，归档摘要 | 404，运行中 busy 409 |
| 5 | GET /api/v1/chat/sessions/{sessionId}/messages | cursor?/size? | 200 JSON，消息分页 | 404，分页 400，存储 503 |
| 6 | POST /api/v1/chat/sessions/{sessionId}/messages | `{content,clientMessageId}` | 新消息 200 SSE；合法重复 200 JSON | 404，busy/冲突/归档 409，禁用/依赖 503 |

各操作共用第 1 节的 400/401/403/404/500/503 及认证错误。查询不接受 GET body；未声明的业务查询参数 400，不接受客户端指定排序或商户。

### 3.1 创建会话

```http
POST /api/v1/chat/sessions
Authorization: Bearer <accessToken>
Content-Type: application/json
X-Request-Id: chat-create-001

{"title":"充电器咨询"}
```

无 body 或 `{}` 使用默认标题；JSON 字面量 null、数组及未知字段均 400。成功响应头 `Location: /api/v1/chat/sessions/300000000000000001`、`X-Request-Id: chat-create-001`；JSON data 为第 2.1 节摘要，code=0、message=success、requestId=chat-create-001。会话创建不调用模型，不保证创建请求本身幂等。

### 3.2 会话列表和详情

```http
GET /api/v1/chat/sessions?size=20
Authorization: Bearer <accessToken>
```

成功 200，data={items:[ChatSessionSummary],nextCursor:null,hasMore:false}，items 中每个元素返回第 2.1 节完整字段；非末页 nextCursor 为实际游标。详情 GET `/api/v1/chat/sessions/{sessionId}` 的 data 是单个 ChatSessionSummary。两种读取均只读本人范围，无用户指定账号参数。

### 3.3 归档

POST `/api/v1/chat/sessions/{sessionId}/archive` 不携带 body（允许 `{}`，拒绝未知字段或 null）。成功 200 返回 status=ARCHIVED、archivedAt 为首次归档时间、activeRunId/activeRunStatus=null 的摘要；重复归档保持原 archivedAt，不改原始创建者和消息。运行中返回 409/13002，等待终止后重试归档；没有恢复/删除操作。

### 3.4 历史

GET `/api/v1/chat/sessions/{sessionId}/messages?size=20` 返回 data={items:[ChatMessageView],nextCursor,hasMore}，严格按 messageSeq 升序。归档和异常轮次均可读，历史不只返回成功文本。部分 token 在本次运行未结束前可能只在 SSE 中，不承诺每个 token 立刻出现在数据库；进程崩溃可能丢失未提交部分。

RAG 增量：每条历史消息追加 `sources` 数组，仅成功助手回答包含本次实际入模资料的快照；旧消息、USER、失败/取消/超时、无资料均为 `[]`。字段和聊天接入行为见 [RAG 聊天增量](../rag/README.md#现有聊天增量)。合法重复提交仍返回原 ChatRunView，来源从本人历史读取。

### 3.5 提交新消息和重复请求

```http
POST /api/v1/chat/sessions/300000000000000001/messages
Authorization: Bearer <accessToken>
Content-Type: application/json
Accept: text/event-stream, application/json
X-Request-Id: chat-send-001

{"content":"你好","clientMessageId":"b4d5f4e0-7440-4ed9-8f6e-cd6a79d4d4d1"}
```

判定顺序：认证 → 基本格式/长度与本人资源校验 → 找原 key 并比较 content → 对新消息检查归档、有效运行权、Agent 可用/上下文预算 → 可靠接收 → 生成。语法与格式错误不要求先查询资源；不能向合法但无权访问的资源返回 busy/归档/原 key 等信息。

新消息通过校验后返回 200 `text/event-stream`，`messageId` 等标识由 start 事件给出。原请求相同正文的合法重复，即使原 run 已结束或会话后来已归档，仍返回 200 `application/json`：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "sessionId": "300000000000000001",
    "runId": "500000000000000001",
    "userMessageId": "400000000000000001",
    "messageId": "400000000000000002",
    "status": "STREAMING"
  },
  "requestId": "chat-send-retry-001"
}
```

上述重查不要求重新启用模型，但仍必须通过认证、输入校验和归属校验；不接续/重放原 SSE，不重复建消息或调用模型。失败终态不能使用同 key 再生成，主动重问需要新 UUID。初次调用中发生内部有限重试属于同一 run，与客户端新发送不同。

两个实例共享 MySQL 时须执行相同判断；运行权过期可按需恢复，恢复不得误释放后继 run。done 之前最终落库失败必须报错，不能宣称已经完成；客户端从历史看到的已提交状态才是事实。

## 4. 契约走查用例

| 条件/请求 | 预期协议与内容 | 新消息/新模型调用 |
| --- | --- | --- |
| 本人 ACTIVE、新 UUID、正常回答 | 200 SSE，start/token/done；助手 COMPLETED 且已落库 | 一轮 |
| 缺 Bearer，即使 body 非法 | 401/10002 JSON | 无 |
| 有效 Actor 提交 merchantId/role/system | 400/10001 JSON，不采纳覆盖 | 无 |
| 查询或操作其他商户会话 | 404/10004 JSON，与不存在一致 | 无 |
| 员工查询同商户消费者/同事会话 | 同一 404/10004 JSON，不因员工身份放行 | 无 |
| 同一 key、相同内容，原 run 进行中 | 200 JSON ChatRunView，不转成 busy | 无 |
| 同一 key、不同内容 | 409/13003 JSON，不修改原文 | 无 |
| 本人会话有效 run 进行中，另一新 key | 409/13002 JSON，不写入竞争消息 | 无 |
| 本人 ACTIVE 空闲，归档两次 | 两次 200 JSON，均 ARCHIVED，archivedAt 不变 | 无 |
| 本人会话运行中归档 | 409/13002 JSON，保持 ACTIVE | 无 |
| 已归档会话发新 key | 409/13004 JSON | 无 |
| 已归档会话重交原 key+原文 | 200 JSON 原 ChatRunView，历史仍可读 | 无 |
| system+当前输入预算超限 | 400/13001 JSON，当前正文不静默截断 | 无 |
| Agent 禁用后发新消息 | 503/13501 JSON；本人历史仍可查询 | 无 |
| start 后模型故障/超时 | HTTP 已是 200，只能 error 终止，不能再发 envelope/改 HTTP 状态 | 原 run 终结 |
| 输出 token 后模型故障 | 一次 error、保存 FAILED，不从头重生成 | 无新 run |
| 断线后请求命中另一实例 | GET 历史或原 key JSON 返回共享已提交状态，无重放 | 无 |

以上契约由 Service/MySQL、MockMvc、真实 Servlet HTTP 与本地模型协议 stub 定向测试覆盖。实际 Springdoc 分组及导出方式见 [运行说明（本目录入口）](README.md#manual-verification)；Apifox 导入仍属后续任务。

## 5. SSE 事件协议（v1）

### 5.1 帧、公共字段和顺序

成功建立流时 HTTP 200，`Content-Type: text/event-stream;charset=UTF-8`、`Cache-Control: no-store`，`X-Request-Id` 与事件相同。每帧按 SSE 的 event/id/data 和空行分隔，服务使用 LF 或 CRLF。上游 Chat Completions 的 SSE 不直接透传：前端只解析本节的应用协议，不解析供应商 `[DONE]`、choices 或专有 reasoning 字段。

| 项目 | 类型/语义 |
| --- | --- |
| event | start/token/done/error；未来可能新增 citation/tool_call，客户端应忽略未识别的非终止扩展事件 |
| id | `runId:seq`，seq 从 1 递增的十进制事件序号；仅供流内关联，不是消息序号或可重放游标 |
| data.version | 固定字符串 v1 |
| data.runId | 此轮运行 ID，十进制字符串；同一流不改变 |
| data.sessionId | 本人会话 ID，十进制字符串 |
| data.messageId | 助手消息 ID，十进制字符串；用户消息 ID 仅 start 额外给出 |
| data.requestId | 当前 HTTP 关联 ID；与响应 X-Request-Id 一致 |

上述 data 公共字段在四种事件上均必需。本期不存在单独 cancelled/done-success 等事件；取消、超时、失败均用 error。注释心跳如 `: heartbeat` 不是业务事件，没有 id，也不推进模型或认证状态。

| event | 额外 data 字段 | 是否终止 |
| --- | --- | --- |
| start | userMessageId：用户消息 ID；两条消息已可靠接收 | 否，恰好一次且第一个 |
| token | delta：非空文本片段，可包含换行；一次 delta 不等于一个 tokenizer token | 否，零至多个，有序拼接 |
| done | status=COMPLETED；usage：null 或可得用量对象；sources：实际入模参考片段数组，无资料为 [] | 是，正文及 sources 同事务提交 |
| error | code：第 1.2 节字符串标识；message：安全摘要；terminalStatus=CANCELLED/TIMED_OUT/FAILED | 是，不再发 done |

`usage` 未获得任何实际用量时整体 null；部分获得时允许 `{inputTokens:null,outputTokens:18}`。不得用估算值冒充供应商计费。历史用量字段与 run 对应，未知仍为 null。

有正常结束的流为 `start → token* → done` 或 `start → token* → error`，终止事件之后不发任何新业务事件。错误发生在开始之前用 JSON envelope；流一旦提交 HTTP 200，后续失败不能改 HTTP 状态或插入普通 JSON envelope。断开或写失败时终止事件可能无法送达，EOF 本身不等于成功。同一 run 的迟到 token/done 不能覆盖终态。

### 5.2 完整成功样例

下列文本取自确定性替身，用于验证拼接，不表示真实模型必须逐字回答；第一段 token 和第二段 token 组装成“你好，请问有什么可以帮你？”。消息内容已经在 done 前保存。

```text
event: start
id: 500000000000000001:1
data: {"version":"v1","runId":"500000000000000001","sessionId":"300000000000000001","messageId":"400000000000000002","requestId":"chat-send-001","userMessageId":"400000000000000001"}

event: token
id: 500000000000000001:2
data: {"version":"v1","runId":"500000000000000001","sessionId":"300000000000000001","messageId":"400000000000000002","requestId":"chat-send-001","delta":"你好，"}

: heartbeat

event: token
id: 500000000000000001:3
data: {"version":"v1","runId":"500000000000000001","sessionId":"300000000000000001","messageId":"400000000000000002","requestId":"chat-send-001","delta":"请问有什么可以帮你？"}

event: done
id: 500000000000000001:4
data: {"version":"v1","runId":"500000000000000001","sessionId":"300000000000000001","messageId":"400000000000000002","requestId":"chat-send-001","status":"COMPLETED","usage":null,"sources":[]}

```

### 5.3 错误、取消及超时

以下是上述流在发送首段 token 后超时的**替代分支**；实际同一 run 不会同时有成功 done 和本 error：

```text
event: error
id: 500000000000000001:3
data: {"version":"v1","runId":"500000000000000001","sessionId":"300000000000000001","messageId":"400000000000000002","requestId":"chat-send-001","code":"TIMEOUT","message":"模型响应超时","terminalStatus":"TIMED_OUT"}

```

| 原因 | error.code / terminalStatus | 历史与后续动作 |
| --- | --- | --- |
| 首 token 或总 deadline 到期 | TIMEOUT / TIMED_OUT | 保存已收集的有限部分文本并释放运行权，客户端可查历史或用新 UUID 重问 |
| 检测客户端断开 | CANCELLED / CANCELLED | 停止写流、请求上游取消；连接已断开，不保证能送此 error |
| 模型连接/协议/上游错误或慢客户端资源限制 | DEPENDENCY_ERROR / FAILED | 输出前可有限重试；输出后直接失败，不重生成正文 |
| 助手终态无法落库 | CHAT_DEPENDENCY_UNAVAILABLE / FAILED | 若可写则告知失败，不能发 done；这里是本次尝试终止声明，数据库可能仍为旧状态，稍后按 deadline 恢复 |
| 未分类异常 | INTERNAL_ERROR / FAILED | 安全摘要、关联 requestId；无堆栈、模型 key 或原始上游错误 |

正常终止时先提交助手状态再发终止事件。唯一例外是存储本身不可用：error 不能保证状态已写入，客户端必须以之后恢复的历史为准。注释心跳不能给登录会话续期；HTTP 连接初次鉴权成功后沿用已有“授权在途请求不承诺随退出取消”的语义，未来请求仍重新鉴权。

不提供独立取消 API；浏览器 AbortController 断开触发服务端取消请求。网络、SDK 与供应商可能不能立即停止，服务端用有界 deadline 释放资源，不承诺退还已消耗的 token。

### 5.4 后续 citation/tool_call

这两种名称只保留扩展约定，不属于本期实际发出的事件。后续事件继续包含 v1 公共关联字段；citation 用于经过可见性校验的来源引用，tool_call 用于脱敏的执行摘要，均不是终止事件。具体 payload/schema 在 RAG/FC change 冻结，不在本期设计假引用、开放任意工具执行或泄露原始工具参数/返回值。未知非终止扩展事件不能被前端当作 done。

## 6. 浏览器 fetch、CORS 与断线恢复

### 6.1 fetch 示例（接口消费示意，不是前端交付）

调用前从现有认证存储选取当前店铺/账号类型对应的 accessToken，并核对 principal。`clientMessageId` 在用户点击发送时创建一次，HTTP 重试传入原值，不在每次 fetch 内重新生成；`X-Request-Id` 每次 HTTP 可不同。整个浏览器接口仅需要应用 accessToken，从来不需要模型 API key。

```javascript
async function sendChat({ apiOrigin = "", sessionId, accessToken,
  content, clientMessageId, signal, onEvent }) {
  const response = await fetch(
    `${apiOrigin}/api/v1/chat/sessions/${encodeURIComponent(sessionId)}/messages`, {
      method: "POST",
      credentials: "omit",
      headers: {
        Authorization: `Bearer ${accessToken}`,
        "Content-Type": "application/json",
        Accept: "text/event-stream, application/json",
        "X-Request-Id": crypto.randomUUID()
      },
      body: JSON.stringify({ content, clientMessageId }),
      signal
    });
  const mime = response.headers.get("content-type") || "";
  if (mime.includes("application/json")) {
    const body = await response.json();
    if (!response.ok || body.code !== 0) {
      throw Object.assign(new Error(body.message), {
        code: body.code, requestId: body.requestId, httpStatus: response.status
      });
    }
    return { kind: "existing", run: body.data }; // 合法幂等成功，不是错误
  }
  if (!response.ok || !mime.includes("text/event-stream") || !response.body) {
    throw new Error("unexpected chat response");
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";
  let terminal = null;
  try {
    while (!terminal) {
      const chunk = await reader.read();
      buffer += decoder.decode(chunk.value, { stream: !chunk.done });
      let separator;
      while (!terminal && (separator = /\r?\n\r?\n/.exec(buffer))) {
        const frame = buffer.slice(0, separator.index);
        buffer = buffer.slice(separator.index + separator[0].length);
        let event = "message";
        let id = "";
        const data = [];
        for (const line of frame.split(/\r?\n/)) {
          if (line.startsWith("event:")) event = line.slice(6).replace(/^ /, "");
          else if (line.startsWith("id:")) id = line.slice(3).replace(/^ /, "");
          else if (line.startsWith("data:")) data.push(line.slice(5).replace(/^ /, ""));
        }
        if (!data.length) continue; // 空帧/注释心跳
        const payload = JSON.parse(data.join("\n"));
        if (["start", "token", "done", "error"].includes(event)) {
          onEvent?.({ event, id, data: payload });
        }
        if (event === "done" || event === "error") terminal = { event, data: payload };
      }
      if (chunk.done) break;
    }
    return terminal ? { kind: "terminal", ...terminal }
      : { kind: "interrupted", reason: "EOF_WITHOUT_TERMINAL" };
  } finally {
    try { await reader.cancel(); } catch { /* 已关闭或被中止 */ }
    reader.releaseLock();
  }
}
```

此示例将 UTF-8 解码和完整帧解析分开，不能假设一次 read 就是一个 token/一帧；无终止事件的 EOF 不报告成功。网络错误或 AbortError 会抛给调用方；调用方须保留原 UUID、正文和已知 messageId，进入状态核对，不自动新建 UUID 再发一次。服务端禁用/鉴权 JSON 错误也不能被当作 SSE 解析。浏览器 UI 和完整解析器测试留待后续任务；CORS 预检与真实 Servlet 流由 ChatHttpIT 验证。

### 6.2 跨域预检

若前后端使用不同 origin，允许列表由部署配置明确指定；同源 Nginx `/api` 转发则不需要跨源。合法预检不携带 Bearer，不要求先登录，实际业务请求仍必须带 Bearer。

```http
OPTIONS /api/v1/chat/sessions/300000000000000001/messages
Origin: https://frontend.example
Access-Control-Request-Method: POST
Access-Control-Request-Headers: authorization,content-type,x-request-id
```

被允许 origin 的预检响应为 200，允许 GET/POST/OPTIONS 和 Authorization/Content-Type/Accept/X-Request-Id，暴露 X-Request-Id/Location，动态返回允许的具体 Origin 并设置 Vary: Origin。使用请求显式 Bearer，不依赖 Cookie，示例 credentials=omit；不开放任意来源。非法来源预检拒绝或不返回允许头，不能开放匿名聊天路由。已接入现有安全过滤链，显式来源由 merchant.agent.allowed-origins 配置；初始请求鉴权后，异步分派使用已传入的 Actor/requestId。

### 6.3 恢复步骤

1. 收到 start 时保存 runId、userMessageId、messageId。若没收到 start，原 clientMessageId 和正文仍能用于核对。
2. 收到 done 表示助手成功提交；收到 error 表示本次流失败/取消/超时，按终态展示，部分文本不能显示为完整成功答案。
3. 异常 EOF、AbortError 或网络错误时，调用本人 `GET .../messages` 查状态；若未知标识，可用原 clientMessageId+原文 POST 获取原运行 JSON。若初次请求根本未提交，相同 POST 可能被当作首个请求并开始新流，客户端仍需按 MIME 处理；不能声称它是纯状态查询。
4. 原 run 仍 STARTED/STREAMING 时仅在用户当前查看且需要结果时短暂查询，不做无条件后台保活或重新订阅原 token。超出持久化 deadline 后按需恢复到超时/失败；原实例崩溃不应永久 busy。
5. 原 run 已终止，显示历史终态和已保存文本；用户主动再次提问才生成新 UUID。归档会话原 key 仍可重查，新 key 被拒绝。

`Last-Event-ID` 不支持事件重放，即使收到该头也不启动恢复流、不把 id 解释为生成指令；客户端不发送此头。不同实例共享 MySQL 历史、幂等和 deadline，Redis 保留现有认证状态，不新增 ChatMemory。SSE 连接仍归接收它的实例，不跨实例搬迁或重做回答。

未来 Nginx 集成检查关闭流代理缓冲和缓存、确认心跳间隔小于代理读取超时及总调用时限有界；完整前端/Nginx 集群不属于本轮 Tasks 3–5 的交付。
