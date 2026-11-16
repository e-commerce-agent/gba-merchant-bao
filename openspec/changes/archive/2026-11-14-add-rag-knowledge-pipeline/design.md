# Design

## Context

现有后端已具备 LangChain4j 1.9.1、本人登录会话、MySQL 历史和 start/token/done/error SSE。此次按用户确认将原提案收缩为基础 RAG 演示，按“需求及实现顺序 → API → 数据库”说明方案；不实现业务代码。

## Goals / Non-Goals

**Goals:** 员工导入资料后，登录用户从现有聊天提问，后端通过 Milvus 找到资料并辅助模型回答；前端拿到 Springdoc 导出契约即可对接。

**Non-Goals:** 本次不做 Query 改写、混合检索、Rerank、PDF/Word、文档版本/撤回、知识库分组、内部受众、后台任务、评测平台。匿名、Function Calling、RabbitMQ 异步上传和 Redis ChatMemory 留在后续各自 change；后续同样以最小可运行功能为目标。

## Decisions

### 1. 需求与实现顺序

| 顺序 | 功能 | 最小交付 |
| --- | --- | --- |
| 1 | 资料接入 | 员工同步上传 UTF-8 TXT/Markdown，商品资料/售后政策/店铺规则作为示例内容 |
| 2 | 切分 | 按段落与句界切分，长段落有界回退，记录 chunkNo；不增加语义边界模型 |
| 3 | 向量化 | LangChain4j EmbeddingModel 生成向量，写入 Milvus，MySQL 保存文档及片段 |
| 4 | 检索 | 将当前原始问题向量化，按可信 merchantId 检索 top3，再回源限定 READY 文档 |
| 5 | 回答 | 将预算内片段加入现有 LLM 上下文，复用原 SSE；无匹配返回资料不足 |
| 6 | 前端对接 | done 和历史增加 sources，Springdoc 导出新增接口及增量响应 |
| 7 | 验证 | 少量样例证明上传、命中、无匹配、商户隔离与来源展示可用 |

#### 1.1 同步导入

首版只支持单个小文件：最大 1 MiB、解码后最多 5 万 Unicode 码点、最多 200 片；title 最多 200 码点。拒绝空白、非法 UTF-8 和非 TXT/MD 文件。文件只在请求内读取，不保存原文件；MySQL 保存切分后的有效正文，足够支撑检索与参考资料展示。

分段优先保留完整段落/句子，每片最多 300 个 Unicode 码点；超长句按码点切开，最多保留前片末尾 30 码点重叠且计入上限。保留自然段边界是简单切分，不声称实现基于句向量的语义分段。Embedding 前仍校验实际服务输入上限。

流程为：校验员工与文件 → 写入 PROCESSING 文档和切片 → 事务外调用 Embedding 并写 Milvus → 确认向量可查询 → 将文档改为 READY → 返回 201。外部调用不占数据库长事务。失败标为 FAILED，已写入的部分向量不参与问答；用户需要时重新上传。不引入 taskId、租约、自动重试或队列。同步导入总上限 60 秒，外部调用使用剩余时长；进程中断的 PROCESSING 文档不自动恢复，也不会变成可查询资料。

同一文档不提供修改/删除接口，全部 READY 内容可供本商户登录用户问答。重复上传按新文档处理，没有上传幂等平台。此限制使版本、撤权、历史遮蔽等管理流程不成为本轮前置。

#### 1.2 Milvus 与模型

使用现有 LangChain4j 版本的兼容 Embedding/Milvus 适配，实施时核实依赖版本。Milvus 采用单一固定 collection、COSINE；文档与问题使用同一 Embedding 模型和维度。配置包含 enabled、embedding 服务地址/密钥/模型/维度、Milvus 地址/collection、topK（默认3、上限5）和 minScore。minScore 按少量演示样例配置，不设复杂多路阈值或量化达标门槛。

密钥保存在既有本地忽略配置或环境变量中；不要假设当前 Chat Completions 服务必然支持 Embedding。Milvus 的镜像和 SDK 版本在实施时固定，校验 collection 维度；本次不支持在线更换 Embedding 模型，测试资料需要换模型时使用独立 collection 重新导入。

Milvus 及其所需依赖按仓库惯例加入独立 dev/prod Compose、持久卷及健康检查，dev 端口限 loopback。RAG 默认关闭，关闭时不要求新凭证或向量服务就绪；测试使用隔离实例。

#### 1.3 沿现有聊天接入

`已有登录/本人会话校验与幂等 → 提交消息占位和运行权 → start → 问题 Embedding → Milvus 检索 → MySQL 核验商户和 READY → 上下文组装 → 原模型流式回答 → 保存正文及 sources → done`。

在 ChatService 的 accept 事务提交后增加检索调用，AgentModel 仍只负责模型协议。Milvus scalar filter 固定取 Actor 的 merchantId；回源只按 merchantId+chunkId+READY 文档取正文，不能直接信任向量返回内容。没有内部受众或跨商户共享。

AgentContext 为 system、当前原始输入、输出以及检索片段共同计算预算；低排名片段放不下就丢弃，历史按既有完整成功轮次选取，原始问题不截断且只出现一次。资料用独立参考文本区域包裹，不能成为 system 指令；system 提示只据资料回答商品政策问题，缺少证据则说明不确定。

未命中或全部低于门槛时直接返回“当前资料中没有找到相关信息，请补充说明或联系店铺确认”，sources=[]，不调用自由生成来补造政策。Embedding/Milvus/数据库故障使用现有 DEPENDENCY_ERROR，不能当作无匹配成功；超时沿用 TIMEOUT。检索与生成共享已保存的总 deadline，不重新计时，断线/取消后不再启动生成。生成重试复用已检索片段；原 clientMessageId 重复请求不重做检索。

sources 只包含最终实际放入上下文的片段，最多 topK 条，摘要最多 300 码点。它表示“本次提供给模型的参考资料”，不等于逐句核验的引用；本版不要求正文标记解析、模型输出 JSON 头或额外 citation SSE 事件。

### 2. API 接口设计

沿用 Bearer、ApiResponse、X-Request-Id、UTC 时间及字符串 ID；请求不接受 merchantId/accountId/role/model 等覆盖字段。知识接口仅 MERCHANT_STAFF，缺少认证401、消费者403，角色校验先于文件解析。聊天继续沿用本人会话规则。

| 方法与路径 | 请求 | 响应 |
| --- | --- | --- |
| POST /api/v1/knowledge/documents | multipart：file、title | 201 JSON：documentId、title、status=READY、chunkCount、createdAt；同步完成后返回 |
| GET /api/v1/knowledge/documents | size 默认20、1–100；cursor 可选 | 200 JSON：items、nextCursor、hasMore；仅本商户文档摘要，不返回全文 |
| POST /api/v1/chat/sessions/{sessionId}/messages | 原有 content、clientMessageId | 保留原 SSE/幂等 JSON，成功 done 追加 sources |
| GET /api/v1/chat/sessions/{sessionId}/messages | 原有分页参数 | 原消息字段追加 sources；旧消息/USER/失败回答为 [] |

知识列表按 createdAt DESC、id DESC 排序；返回 documentId、title、status、chunkCount、createdAt，不另做详情/下载/检索预览接口。输入非法400、超限413、不支持格式415、依赖错误503，使用现有统一业务码与安全错误摘要；不单独建立一组平台错误码。

Source schema：

```json
{"documentId":"610000000000000001","chunkId":"620000000000000001","title":"售后政策","chunkNo":1,"excerpt":"购买后七日内，符合资料所列条件的商品……"}
```

done 原公共字段、status、usage 保留，增加 `sources:[Source]`。正文与 sources 保存成功后才发送 done。幂等重试仍返回 ChatRunView，前端从历史读取来源；无需改成新的聊天端点。Springdoc 必须声明 Source、multipart、鉴权、JSON 与 SSE 两种成功返回及 done 示例，导出给前端的文件由运行中的 Springdoc 生成，不手写另一套 OpenAPI。

### 3. 数据库表设计

仅新增两表、一列。沿用雪花 BIGINT、merchant_id 归属、InnoDB、UTC DATETIME(3)，下一空闲 Flyway 版本实施时协调，已发布迁移不变。

| 表/变更 | 字段 | 约束与索引 |
| --- | --- | --- |
| knowledge_document | id、merchant_id、title VARCHAR(200)、status VARCHAR(16)、chunk_count INT、created_by BIGINT、created_at DATETIME(3) | PK(id)，UQ(merchant_id,id)，FK merchant；状态 PROCESSING/READY/FAILED；索引(merchant_id,created_at,id) |
| knowledge_chunk | id、merchant_id、document_id、chunk_no INT、content TEXT、created_at DATETIME(3) | PK(id)，UQ(merchant_id,document_id,chunk_no)，复合FK(merchant_id,document_id)；chunk_no从1开始 |
| chat_message 增量 | sources_json JSON NULL | 成功助手正文与最多5条Source快照同事务保存；历史将NULL映射为[] |

Milvus collection 字段为 chunk_id INT64（主键、autoID=false）、merchant_id INT64、document_id INT64、embedding FLOAT_VECTOR（维度来自固定配置）；只返回标识及分数，MySQL 提供最终正文。写入就绪后才将文档置 READY；任何 PROCESSING/FAILED 片段均被回源过滤。失败残留不要求本轮实现自动清理系统。

不建立 knowledge_base、document_version、index_job、rag_run、candidate、citation、evaluation、feedback 或 badcase 表。sources 是小型消息快照，足够前端展示且无需额外来源查询接口。

### 4. 验证与取舍

准备商品资料、售后政策、店铺规则三个合成 TXT/MD 示例；用约5个问题检查命中、无匹配和答案依据，另验证跨商户不召回、失败资料不可检索、重复发送不重跑以及原聊天可用。测试对关键授权/存储行为断言，真实联调证明 LangChain4j Embedding → Milvus → 原LLM → done.sources 完整工作。

评测与 Badcase 只在验证说明中记录问题、期望、实际结果、简单失败原因及修正后重试，不规定120条样本、准确率目标、消融实验或审核状态机；未实际调用真实服务时明确标注未测。

选同步文本导入是为尽快跑通；后续 RabbitMQ change 可复用解析、切分、向量化方法，将调用从HTTP迁移到消息消费者。选纯向量检索与 sources 快照，是为直接复用 Milvus 和现有聊天；复杂检索算法及逐句引用有明确需求后再加。

## Risks / Trade-offs

- [同步导入受请求时间和模型延迟影响] → 限制小文件与60秒总时长，失败重传；异步处理留给RabbitMQ模块。
- [纯向量检索可能漏掉精确型号，基础生成也可能答错] → 用合成资料验证代表性问题、调整简单门槛；无匹配不自由补答，不承诺检索质量指标。
- [导入中断留下部分向量] → MySQL READY 条件控制可见性，半成品不参与回答；暂不做自动恢复/清理。

## Migration Plan

新增两表和sources_json、配置Milvus及Embedding、导入合成资料、接入现有聊天并导出Springdoc。验证关闭RAG后旧链路可用，启用后实际问答和sources可展示。关闭RAG即可回到原生成路径，历史sources仍可读取；新增表列保留，不回退已发布迁移。本轮只更新规划，实施由后续apply开始。
