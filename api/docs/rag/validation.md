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

真实服务联调脚本仅完成PowerShell语法检查，尚未对真实服务执行；外部服务就绪后运行并人工核对答案。未运行整个仓库所有领域的 `clean verify`，上述数量仅指实际执行的定向回归。

JUnit断言与堆栈在 `api/target/surefire-reports/*.txt` / `TEST-*.xml`；`api/target/test-logs/application.json` 是应用日志，不是测试断言报告。协议五问结果同时输出到 `api/target/rag-pipeline/results.json`。本次命令日志分别为 `api/target/rag-contract-tests.log`、`api/target/rag-regression-tests.log`。
