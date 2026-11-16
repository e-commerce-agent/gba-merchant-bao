# RAG 知识检索面试卡片

更新：2026-11-14。RAG 实现由 [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) 合入；本次 dev 联调记录由 `e30b5fc` 提交。实现代码对应 `bd26680`（检索接入）、`5eadf90`（员工资料入库）。真实环境参数、逐题结果和限制见 [RAG 验证记录](../../api/docs/rag/validation.md)；功能接口见 [RAG 联调说明](../../api/docs/rag/README.md)。

OpenSpec：`add-rag-knowledge-pipeline`（spec-driven，9/9任务完成），已同步三个主规格并归档到 [2026-11-14-add-rag-knowledge-pipeline](../../openspec/changes/archive/2026-11-14-add-rag-knowledge-pipeline/proposal.md)。本次文档mini PR不重复声明功能实现。

## 一、业务梳理

商户员工上传商品资料、售后政策和店铺规则，消费者或员工沿用已有登录会话提问。服务按可信商户身份找相关资料，将有限参考内容交给现有模型生成答案，并在 SSE 完成事件及历史消息中保存实际使用的来源，便于前端展示和事后核对。

本模块实现同步 TXT/Markdown 导入、段落切片、向量化、Milvus 检索、MySQL 回源和既有聊天接入。来源表示模型本次收到的参考片段，不等于系统已经逐句验证答案。当前未实现异步队列、文档编辑/撤回、混合检索、重排、评测平台或 Function Calling。

可直接讲的开场：

> 我给已经有持久化和 SSE 生命周期的 Agent 对话增加了基础 RAG。上传时按段落和句界切片，生成向量写进 Milvus，正文和 READY 状态保存在 MySQL。提问时先按可信商户检索，再从 MySQL 读取 READY 片段，连同有限历史送入原模型；成功答案和来源一起提交后，仍通过原有 SSE done 返回。我重点讲检索放置和租户边界，以及切片长度和边界规则。

## 二、技术实现

**关键问题：** 对话在开始 SSE 前保存用户消息、助手占位和运行权，RAG 检索要在 LLM 生成前完成。网络检索若占用数据库事务，会延长事务和锁的持有时间；直接使用向量库返回正文，又缺少 MySQL 的商户归属和资料发布状态核验。

**主方案与数据流：**

1. `ChatController#send` 取得已认证 Actor；`ChatService#accept` 校验会话归属和 `clientMessageId`，在短 MySQL 事务中保存用户消息、助手占位与共享运行权/deadline 后提交。相同幂等键和正文返回已有运行，不再次启动检索或生成。
2. 接受请求后建立原有 SSE 并发送 start。有界 Agent 执行器调用 `KnowledgeRetrieval#retrieve`：把原始问题交给 Embedding，使用可信 `merchantId` 在 Milvus 做 topK 向量搜索，然后按命中 chunk ID 从 MySQL 读取同商户且文档状态为 READY 的正文。向量库只提供候选标识/分数，不作为最终正文来源。
3. `AgentContext#withEvidence` 在原问题和 system prompt 保留、输出额度预留的预算内放入来源片段及完整成功历史轮次。参考材料放在独立区域，system prompt 明确它是待参考数据，不是新指令。没有可用片段时返回固定资料不足提示，不调用自由生成。
4. 命中时沿用已有 `AgentAttempts`、`AgentModel` 和流式生命周期生成。`ChatRunExecution` 收集正文和实际注入的 sources，`ChatService#finish` 在一个终态事务中保存二者并释放运行权，事务提交后才发送 done。历史读取保存的快照，不重新检索。

因此 RAG 是已接受运行后的上下文准备阶段，位于持久化接收和原模型流之间；`AgentModel` 仍只负责模型协议。Embedding/Milvus 错误按依赖错误处理，超时复用原 run deadline，断线或取消后不启动生成。商户过滤同时发生在向量搜索和 MySQL 回源，避免依赖单一存储边界。

**持久化时序要区分：** 用户消息与助手占位先落库；生成中的 token 在进程内累积后转发，并非每个 token 先写 MySQL。首个 delta 只先更新 STREAMING 状态；终止时保存最终或有限的部分正文，成功时再一起保存 sources，提交后才发 done。进程崩溃前未提交的部分 token 不保证恢复。

**资料导入链路：** 员工身份/文件校验 → `KnowledgeText` 解码并切片 → 短事务写 PROCESSING 文档和 chunk 正文 → 事务外 Embedding → Milvus 写入与强一致查询确认全部 chunk ID 可见 → 短事务改 READY → 上传返回 201。失败时标 FAILED；标记失败本身写库异常则仍为 PROCESSING，两者均不能通过检索回源的 READY 条件。MySQL 和 Milvus 没有分布式事务，本期用发布门禁保证半成品不参与回答。

主要组件使用 LangChain4j 1.9.1 的 Embedding/Streaming Chat API、Milvus SDK/server 2.5.9 和 COSINE 检索。文档和问题使用同一 Embedding 模型与维度；默认 topK=3、最大5、minScore=0.5，这些是配置值，并非测出的质量指标。MySQL 的 V10 增加 `knowledge_document`、`knowledge_chunk` 和 `chat_message.sources_json`。RAG 默认关闭，启用开关不影响历史来源快照读取。

Embedding把文本转换为同一向量空间中的定长浮点向量，让问题和片段可以按相似度比较；Milvus用COSINE选候选，LLM则读取回源后的文本生成回答。这条链路给模型提供本次上下文，不更新LLM权重。当前查询只向量化原始问题，没有查询改写、关键词混合或Rerank，因此多轮指代和精确型号召回仍有边界。

### 核心代码位点

| 类#方法 | 职责 | 功能提交 / PR |
| --- | --- | --- |
| `ChatController#send` | 校验请求并区分新 SSE 运行和幂等重试 JSON；本次复用的原有入口 | `abc38b2` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `ChatService#accept` | 在检索前提交可信会话校验、消息占位和运行权；幂等重试不重复执行 | `bd26680` / [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `ChatService#execute` | 在已接受的 run 中限时检索，再组装证据并复用原 Agent 模型调用 | `bd26680` / [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `KnowledgeService#upload` | 员工上传校验、PROCESSING/READY 状态推进、事务外向量化和失败隔离 | `5eadf90` / [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `KnowledgeText#parse/#split` | UTF-8 文本限制、段落/句界优先切分、码点上限与重叠 | `5eadf90` / [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `KnowledgeEmbedding#embed` | 分批生成并校验向量维度、有限数值及非零向量 | `5eadf90` / [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `MilvusKnowledgeStore#insertAndConfirm/#search` | 写向量并确认可见；按可信商户检索候选 ID | `5eadf90`（导入）、`bd26680`（检索）/ [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `KnowledgeRetrieval#retrieve` | topK、分数阈值及 MySQL 同商户 READY 回源 | `bd26680` / [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `AgentContext#withEvidence` | 预算内拼接来源，保留原问题和最近完整成功历史 | `bd26680` / [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `ChatRunExecution#end`、`ChatService#finish` | 保存终态正文，成功时同事务保存 sources，提交后才发 done | `bd26680` / [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |
| `RagChatIT`、`RagPipelineIT` | RAG 运行边界与 HTTP/Embedding/Milvus/生成协议闭环验证 | `bd26680`（RagChatIT）、`ef88281`（RagPipelineIT）/ [PR #27](https://github.com/e-commerce-agent/gba-merchant-bao/pull/27) |

源码入口：[ChatService](../../api/src/main/java/com/gba/merchantbao/chat/service/ChatService.java)、[ChatRunExecution](../../api/src/main/java/com/gba/merchantbao/chat/service/ChatRunExecution.java)、[AgentContext](../../api/src/main/java/com/gba/merchantbao/agent/service/AgentContext.java)、[KnowledgeService](../../api/src/main/java/com/gba/merchantbao/knowledge/service/KnowledgeService.java)、[KnowledgeText](../../api/src/main/java/com/gba/merchantbao/knowledge/service/KnowledgeText.java)、[KnowledgeEmbedding](../../api/src/main/java/com/gba/merchantbao/knowledge/service/KnowledgeEmbedding.java)、[KnowledgeRetrieval](../../api/src/main/java/com/gba/merchantbao/knowledge/service/KnowledgeRetrieval.java)、[MilvusKnowledgeStore](../../api/src/main/java/com/gba/merchantbao/knowledge/service/MilvusKnowledgeStore.java)。dev 联调记录提交为 `e30b5fc`，未核实其独立 PR；本次文档 commit 和 mini PR 待作者手动创建，不预填编号。

## 三、技术深挖：两个主讲难点

### 难点一：RAG 加在已有 MySQL + SSE 对话的哪里？

不能先检索再接受消息，因为会话/权限/幂等拒绝的请求不应访问模型依赖，也无法先建立可恢复的 run 状态；也不把向量检索塞进接收事务，否则网络调用会延长事务。实际顺序是：

> 认证与本人会话校验 → 幂等检查 → 短事务保存 USER、ASSISTANT 占位和 run 权并提交 → SSE start → 问题 Embedding → Milvus 按可信 merchantId 查 topK → MySQL 核对同商户 READY 并取正文 → 有界上下文组装 → 复用原 LLM 流式生成 → 正文与 sources 同事务提交、释放 run 权 → SSE done。

Milvus 负责向量近邻候选，不保存最终展示正文；MySQL 负责文档/片段事实和 READY 发布门禁。来源只记录最终实际进入模型上下文的片段。失败/取消/超时回答、无匹配和旧消息返回空来源。相同 `clientMessageId` 与相同正文重试返回旧运行视图，不重复启动检索或生成；这不代表外部供应商有 exactly-once 计费保证。

预算先预留最大输出，扣 system 和完整当前问题，再按排名接纳完整参考片段；第一个片段放不下时停止，不截断片段，随后用剩余预算重新选取完整成功历史轮次。当前估算采用 UTF-8 字节数加每条消息4的开销，不是模型精确 tokenizer。来源只从这次实际接纳的片段产生，不能把全部候选都当作引用展示。资料中的命令通过参考区域与 system 规则约束，但这不等于对任意提示注入已提供完备防护。

面试话术：

> 我把 RAG 放在消息已被 MySQL 接受并提交之后、原模型生成之前。接收事务只处理会话归属、幂等、用户消息、助手占位和运行权；事务外在共享 deadline 内做问题向量化和检索。Milvus 用可信 Actor 的商户 ID 限定候选，MySQL 再按商户和 READY 状态回源，最后把预算允许的证据交给原来的 AgentContext 和流式模型。模型协议和 SSE 生命周期没有另起一套。最终正文与实际使用的来源同事务提交，提交之后才发 done。

证据：`ChatService#accept/#execute/#finish`、`KnowledgeRetrieval#retrieve`、`AgentContext#withEvidence`、`ChatRunExecution#end`；`RagPipelineIT` 覆盖协议链路，2026-11-14 dev 记录覆盖真实 Embedding、Milvus、LLM 和来源历史快照。具体限制见下方真实数字。

### 难点二：文档怎么切，chunk size 怎么定？

`KnowledgeText#split` 是确定性规则切片，使用 Unicode 码点，避免切断 emoji 的 UTF-16 代理对；300 不是 Java 字符串长度、字节数或精确 token 数。具体步骤为：

1. 统一 CRLF/CR 为 LF，去 UTF-8 BOM 与整体首尾空白；拒绝非法 UTF-8、空白内容或 NUL。设本片起点为 start，窗口终点为 `min(start + 300, 总码点数)`。
2. 若剩余文本不超过300码点，直接作为最后一片，内部短段落不会被强制拆开。
3. 若窗口后还有文本，从 `start + 30` 扫描至窗口最后一个码点。优先采用扫描范围内最后一处连续两个 LF 的结束位置；没有段落边界时采用最后一处 `。！？.!?；;` 或 LF 的结束位置；都没有则按300码点硬切。因此是窗口内段落/句界回退，不是 Markdown 标题树解析或语义模型分段。
4. 片段 strip 后非空才保存。非最后一片的下一个起点为 `end - min(30, floor((end-start)/2))`，即最多重复30码点，短片至多重复原窗口长度的一半，保证向前推进。重叠计入下一片300码点上限；去空白后的可见重叠可能更少。超过200片拒绝，chunkNo 从1开始。

300 码点是针对本期小型商品/售后/店铺文本的工程起点，当前在源码中固定，尚未配置化。较小片段便于把具体商品事实和政策送入有限上下文；过大容易混入无关信息、挤占历史和输出空间，过小则容易丢掉条件及指代关系。保留最多30码点重叠是为了减轻边界信息丢失；硬切时相当于最大窗口的10%，会增加向量数量和重复召回。这些是设计动机，不是测得的质量收益。

300 码点最多占1200 UTF-8字节；`KnowledgeEmbedding#embed` 每批最多16片，部署说明要求选择可承受该输入量的服务。1200是编码长度上界，不能当成供应商已测最大输入或1200个精确token；当前没有跨模型token容量实测。文件另外受1 MiB、解码后5万码点和200片三个独立限制，重叠或短段落会使片数先达上限。

选择后还需在真实业务语料上看实际召回和来源，而不能只凭长度参数推断质量。本次真实开发库三份 3C 资料各生成 1 个 chunk；五问中四个问题得到预期资料，一题固定返回资料不足。店铺规则问题还带回另外两个来源，记录为额外召回观察；API 未暴露相似度分数，没有据此猜测或调整阈值。没有足够证据证明 300/30 是全局最佳设置。

面试话术：

> 我们先做小型商品和政策文本，所以选最多300个Unicode码点，在窗口内优先找段落和句界，找不到才硬切，相邻片段最多重叠30码点。这样控制了单片输入和上下文占用，也给跨界条件留一点连续性；代价是重复片段和信息断裂仍可能发生。300/30是首版工程起点，未做参数消融，不能说最优。下一步应固定模型和代表性问题集，比较事实是否完整、正确资料是否进入topK、额外召回、上下文占用、向量数量及真实延迟，再决定是否调整。当前五题样本不足以证明这些收益。

证据：`KnowledgeTextTest#validUtf8MarkdownKeepsUnicodeBoundariesAndLimitsOverlap` 使用750个emoji得到3片，计入重叠共810码点，校验每片不超过300且码点未损坏；短段落示例保持为1片。`#rejectsWrongFormatEmptyMalformedAndOversizedInputs` 验证输入及片数限制。`KnowledgeEmbeddingTest#usesIndependentEmbeddingProtocolAndRejectsWrongDimension` 验证独立协议及维度错误。750/810是人工构造测试的输入/输出长度，不能当成业务效果；真实资料各1片，没有验证长业务文档的切片召回质量，也没有参数对比评测。

## 四、高频八股

题目围绕已实现的数据流和验证范围；项目回答不把演示样本扩大成线上质量结论。

| 追问 | 项目回答 |
| --- | --- |
| 向量库和关系库各保存什么？ | Milvus 保存向量及商户/文档/chunk 标识并做近邻搜索；MySQL 保存文档状态和 chunk 正文。按 MySQL READY 与商户归属回源，向量结果不是答案正文。 |
| 如何防止跨商户召回？ | 商户 ID 来自认证 Actor，不接受请求体覆盖；Milvus 搜索加 merchant filter，MySQL 再用 merchantId + chunkId + READY 取来源。 |
| COSINE、topK和阈值各控制什么？ | COSINE比较向量方向；topK限制返回候选数量，minScore排除低相似度候选。分数不是答案正确概率，阈值不能直接跨模型照搬；当前没有混合检索、重排或参数对比结论。 |
| 无匹配与依赖失败如何区分？ | 空候选或低于 min-score 返回固定资料不足提示及空来源，不调用 LLM 补答；Embedding/Milvus 异常映射依赖错误，deadline 到期按超时终止。 |
| 用户上传失败但向量已部分写入怎么办？ | 文档仍为 FAILED 或 PROCESSING，检索回源只允许 READY，因此部分向量不能成为可用资料；当前不自动清理残留向量。 |
| sources 是否等于模型引用正确？ | 否。它只证明哪些片段实际进入模型上下文，不验证答案逐句由它们推出。 |
| 上传为什么同步？ | 首版限定小 TXT/MD、200 chunks、60 秒总预算，方便端到端跑通；失败资料不可见。异步队列、自动恢复和撤回留给后续设计。 |
| 真实验收有多少题，代表什么？ | 2026-11-14 一次真实 dev 联调五题，四条有资料问题调用真实 LLM，一条无匹配不调用生成；仅是小样本闭环，不是准确率或压测。此前 11/13 的 161 项是隔离环境定向回归，也不能替代生产数据。 |

## 五、测试条件与真实数字

以下数字对应仓库验证记录，不补估计值：

| 验证 | 实际结果与边界 |
| --- | --- |
| RAG 定向 Maven 回归（2026-11-13） | 第一组11项、第二组161项分别通过，失败/错误/跳过均为0，不累加为去重用例数。协议闭环环境为隔离MySQL 8.0.34、Milvus 2.5.9、LangChain4j 1.9.1与受控Embedding/生成服务；认证回归另用隔离Redis，不能证明真实模型语义质量。 |
| 真实 dev 服务（2026-11-14） | IDEA以dev profile启动，连接`127.0.0.1:3307/gba_dev`，Flyway验证19个迁移、schema为V10。开发配置使用`qwen3.7-text-embedding` 1536维、collection `knowledge_qwen37_1536`与`dots3-note-prev`。M_3C_DEMO员工上传商品/售后/店铺规则三份资料，各1 chunk；跑五个新会话与五问。 |
| 五问结果 | 四条有资料问题答案人工核对正确且来源/历史一致；路由器 R9000 无资料，返回固定提示、空来源且未调用生成。店铺规则题额外返回售后和商品两个来源，作为额外召回观察；未虚构相似度分数。 |
| 重复请求与 OpenAPI | 相同请求幂等 runId 检查通过；done、本人历史来源快照一致；导出并检查聊天和知识接口的 OpenAPI 3.1 JSON/YAML。 |
| 未测与限制 | 11月13日记录未执行全仓 `clean verify`，上述11/161仅为定向回归；真实五问没有 P95、QPS、成本、召回率或生产部署数据。本次仅改文档及规格，未重新调用模型或重跑应用回归。 |

细节、单题观察和复现条件见 [RAG 验证记录](../../api/docs/rag/validation.md)。资料以开发数据快照及显式合成的售后/店铺规则为基础，不是线上实时库存、订单或商户承诺。
