# RAG 联调与回归记录

日期：2026-11-13。对应 change：`add-rag-knowledge-pipeline`，任务3。

## 样例与 Flyway 的对应关系

- 商户取 `db/seed/nonprod/R__010_local_test_tenants.sql` 的 `M_3C_DEMO`（1000000000000001，CNY）；`M_ISOLATION_TEST` 只用于隔离验证。
- 商品结构对应 V6 的 product/product_sku/product_attribute；[商品文件](examples/product.md) 摘取 `db/devdata/R__040_product_demo.sql` 中已发布的充电器、数据线、耳机及 ACTIVE SKU。充电器 `SKU-CHG-65W-CN` 为65W/国标/99.00元；数据线 `SKU-CBL-USBC-1M-BK` 为1米/黑色/19.90元/480Mbps；耳机为主动降噪/蓝牙5.3。
- 不导入 DRAFT、UNPUBLISHED、DISCONTINUED 或隔离商户资料。文件是开发数据快照，不自动同步商品状态；本期没有资料撤回功能。库存来自 R__050，订单成交快照来自 R__060，不能用目录价代替历史成交价，也不把快照当作实时库存。
- [售后文件](examples/after-sales.txt) 与 [店铺规则](examples/store-rules.md) 是针对同一3C商户的合成规则。Flyway未定义这些承诺，因此文件显式标注合成来源；它们不创建退货、运费计算或订单操作。
- 不修改已经发布的 V 迁移、冻结种子或既有演示数据。资料通过现有上传端点进入 V10 knowledge_document/knowledge_chunk，来源快照进入 chat_message.sources_json。

## 五个问题的实际验证范围

环境：隔离 MySQL 8.0.34、Milvus 2.5.9；真实 Spring MVC HTTP、LangChain4j 1.9.1 Embedding/流式生成 SDK、Milvus SDK 2.5.9、MyBatis 和 SSE。只有认证身份与外部模型 HTTP 协议由测试控制，未替换内部检索/生成实现。

`RagPipelineIT` 使用4维确定性协议向量与回显证据的生成协议。它证明端到端数据流、来源与输入一致及幂等行为，不能证明真实模型语义质量。保存的原始结果：[protocol-results.json](validation/protocol-results.json)。这些标识来自已销毁的隔离数据库，不是开发库资源。

| 问题 | 期望 | 受控协议链路实际结果 | 真实外部模型 |
| --- | --- | --- | --- |
| SKU-CHG-65W-CN的功率、插头和目录价 | 商品资料，65W/国标/99.00元 | 命中商品片段，原样入模，done与历史快照一致 | 未测 |
| SKU-CBL-USBC-1M-BK的规格、价格和速率 | 商品资料，1米/黑色/19.90元/480Mbps | 命中商品片段，来源与正文一致 | 未测 |
| 未拆封充电器退货时间与运费 | 合成售后政策，七日内/非质量问题买家承担 | 命中售后片段，来源与正文一致 | 未测 |
| 3C店铺包邮门槛与运费 | 合成店铺规则，199元/8元 | 命中店铺片段，来源与正文一致 | 未测 |
| 路由器R9000固件更新周期 | 资料不足，空来源，不调用生成 | 固定资料不足提示、sources=[]、无生成调用 | 未测 |

额外检查：同问题另一商户无匹配；相同 clientMessageId 不重做 Embedding/生成；关闭 RAG 后普通聊天成功且 sources=[]。五个问题每次使用新会话，避免历史影响。

真实服务未测原因：本次执行环境未提供独立 Embedding 服务地址/凭证，不能假设现有 Chat Completions 服务支持 Embedding；因此没有进行真实 Embedding→Milvus→现有LLM 的问答，也没有真实模型准确率或阈值优化结果。任务3.2按其“没有真实服务明确记为未测”的分支交付三份资料、五问、运行入口和记录。真实服务就绪后的步骤见 [README](README.md#前端交付与演示)。

## Badcase 与修正

- 初始样例选用了项目外的保温杯，经核对改为3C配件数据；最终五问已在受控协议链路重新跑通。商品事实取 R__040，合成政策单独标注，不引入新商品或数据库承诺。
- 原 `MigrationScriptContractTest.commerceMigrationVersionsAreUniqueDdlOnlyAndOrdered` 第54行禁止V10及以上，与已存在的V10冲突。改为校验数字版本1..N连续且唯一，并保留 DDL-only 和冻结文件摘要验证；没有改动迁移SQL。
- Springdoc JSON/YAML 对比中，SnakeYAML会把ISO日期示例解析成Date并重新按时区序列化。验证改为直接比较线格式schema与关键结构，导出仍是HTTP原始响应，没有改写日期或手写OpenAPI。
- 没有真实语义 Badcase 结果；不能据受控向量的五问通过推断默认min-score适合真实服务。真实联调脚本记录实际答案/来源，预期来源或无匹配不符会报错，调整后重跑并保留每次结果。

## 前端契约

运行中的 Springdoc 已生成 `docs/api/knowledge-v1.openapi.{json,yaml}` 与 `docs/api/agent-conversation-v1.openapi.{json,yaml}`。`ChatHttpIT` 验证员工Bearer、401/403、multipart二字段和binary file、字符串ID、Source摘要边界、ChatMessage.sources、ChatDone.sources、JSON幂等与SSE成功MIME、命中/无匹配/超时示例。

接口配置、上传、提问、来源展示、关闭RAG及重新导出步骤统一见 [README](README.md)。本 change 未新增匿名问答、Function Calling、RabbitMQ 或 Redis ChatMemory 实现。

## 回归命令与报告

```powershell
mvn -f api/pom.xml '-Dtest=MigrationScriptContractTest,ChatHttpIT,RagPipelineIT' test
mvn -f api/pom.xml '-Dtest=*Test,AuthFlowIT,AuthPersistenceIT,ChatServiceIT,ChatPersistenceIT,RagChatIT,KnowledgeIngestionIT,MilvusKnowledgeStoreIT' test
```

第一组11项通过（迁移6项、HTTP/契约4项、完整协议链路1项）。第二组161项通过，失败/错误/跳过均为0：全部单元118项、认证流程15项、认证持久化4项、会话服务9项、会话持久化3项、RAG聊天8项、资料导入3项、Milvus1项。认证使用隔离MySQL/Redis，其他集成使用各自隔离容器。先前失败的迁移契约测试6项全通过，冻结摘要校验也通过；最后一组结束时间为2026-11-13 23:16（Asia/Shanghai）。

上面的历史回归记录（2026-11-13）仅完成真实服务联调脚本的语法检查；未运行整个仓库所有领域的 `clean verify`，上述数量仅指实际执行的定向回归。本页后面的2026-11-14章节记录真实 dev 服务执行结果。

JUnit断言与堆栈在 `api/target/surefire-reports/*.txt` / `TEST-*.xml`；`api/target/test-logs/application.json` 是应用日志，不是测试断言报告。协议五问结果同时输出到 `api/target/rag-pipeline/results.json`。本次命令日志分别为 `api/target/rag-contract-tests.log`、`api/target/rag-regression-tests.log`。

## 真实 dev 服务联调（2026-11-14）

使用 IDEA 的 `MerchantApplication`，启动参数为 `--spring.profiles.active=dev`。运行日志确认 active profile 为 `dev`，连接 `127.0.0.1:3307/gba_dev`，Flyway 验证19个迁移并将schema保持在V10，应用成功启动。MySQL、Redis、Milvus 的开发端口（3307、6380、19530）均可连接；本次没有修改 Compose 文件或启动拓扑。

### 配置与服务检查步骤

1. 在 `api/src/main/resources/application-dev.yml` 查看 `merchant.agent.enabled`、聊天 `base-url` 和 `model`；只确认密钥已配置，不打印或提交密钥。RAG 独立查看 `merchant.rag.enabled`、`embedding-base-url`、`embedding-model`、`embedding-dimension`、`milvus-uri`、`collection`、`top-k` 和 `min-score`。`RagProperties` 是这些 `merchant.rag` 配置的绑定与校验类；使用YAML配置即可，无需把密钥或开发值硬编码进Java类。
2. IDEA 启动前重新构建/同步 `api` 模块资源，并确认 `api/target/classes/application-dev.yml` 与源配置一致。本次此前发现 target 副本陈旧且不含RAG设置；同步后两份文件SHA-256一致，随后以显式 `dev` profile 重启，避免仅凭源YAML误判运行时配置。
3. 在 `api/.runtime/logs/application.json` 确认 active profile、数据库schema/Flyway版本和 `Started MerchantApplication`。从 `localhost:8080/v3/api-docs/agent-conversation-v1` 与 `.../knowledge-v1` 收到有效OpenAPI响应，可同时确认HTTP服务已就绪。仅看 `api/target/test-logs/application.json` 不能代替这份dev进程日志或Surefire断言报告。
4. 核对聊天服务连接时同时看三项证据：启用项为true、base URL/model配置与预期相同、真实聊天请求得到成功终态及非空回答。若要在上传前单独探测，可对配置的OpenAI-compatible `/chat/completions` 发一个最小请求并检查2xx/choices；这会产生一次模型调用费用。本次未额外发送独立探测请求，而由下述四条真实RAG回答验证聊天连接。
5. 真实上传 `READY` 可验证RAG开关、Embedding连接/维数、Milvus连接及collection写入查询链路；五问的done和本人历史快照再验证真实聊天连接及来源持久化。当前开发collection为 `knowledge_qwen37_1536`，聊天模型为 `dots3-note-prev`，Embedding模型为 `qwen3.7-text-embedding`、1536维；这些是本次dev设置，不应复制到生产默认值。

### 本次结果

通过 `api/scripts/tools/verify-rag-live.ps1` 对真实dev应用执行，模式为 `real configured application; no model substitutes`。以 `M_3C_DEMO` 员工登录；三份3C资料均成功成为 `READY`，每份1个chunk。脚本共完成五个新会话、五个问题和相同请求的幂等重试；四个有资料的问题调用现有LLM，无匹配问题未调用生成。结果文件为 `api/target/rag-live/20261114T025025663Z/results.json`，不含访问令牌。

| 问题 | 实际回答核对 | 来源/结果 |
| --- | --- | --- |
| `SKU-CHG-65W-CN` 功率、插头、目录价 | 65W、国标、99.00元，正确 | 商品资料；done与历史来源一致 |
| `SKU-CBL-USBC-1M-BK` 长度、颜色、目录价、速率 | 1米、黑色、19.90元、480Mbps，正确 | 商品资料；done与历史来源一致 |
| 未拆封充电器退货期限及非质量退货运费 | 七日内且配件齐全可申请，运费由买家承担，正确 | 售后政策；done与历史来源一致 |
| `M_3C_DEMO` 包邮门槛及未达门槛运费 | 满199元包邮，否则8元，正确 | 命中店铺规则；另带回售后和商品两个来源，但回答没有据此添加业务承诺。记录为额外召回观察；API不暴露分数，因此没有无依据地调整min-score |
| 路由器 `R9000` 固件周期 | 返回固定资料不足提示，正确 | 空来源；没有调用生成模型 |

五问均通过脚本的预期来源/无匹配断言、幂等runId检查、正文与历史一致性及sources快照一致性。未发现需要修改资料、提示词或阈值的回答Badcase；额外召回如上记录。没有把该小样本结果解释为检索准确率保证。重新执行会新增资料及会话，dev数据没有自动清理。

本次从正在运行的 `localhost` 后端重新导出并检查了 `docs/api/agent-conversation-v1.openapi.yaml`、`docs/api/knowledge-v1.openapi.yaml` 与 `docs/api/shipment.wsdl`。前两份为有效OpenAPI 3.1，含各自JSON定义的全部路径；知识上传包含multipart契约，聊天包含 `Source`、`ChatDone` 和SSE done扩展。WSDL XML解析成功，SOAP地址为 `http://localhost:8080/services/shipment`，schema已内嵌，无外部import。
