# Function Calling 验证记录

## 环境与范围

验证日期：2026-11-14（Asia/Shanghai）。使用 JDK 21、Maven 和 Docker Desktop。自动化测试通过独立 MySQL 8.0.34 / Redis 容器执行，不连接开发或生产数据库，不要求真实模型凭证。

`ChatFunctionCallingIT` 使用真实 `OpenAiAgentModel`、LangChain4j 协议序列化/解析、聊天运行编排、`AgentOrderLookup`、`OrderQueryService`、MyBatis、终态事务及 Servlet SSE。本地 HTTP 桩发送 OpenAI 兼容的结构化 tool call，并从下一轮真实 tool result 中读取状态生成确定性回答。仅认证会话解析、Embedding 和 Milvus 使用测试替身；完整登录/JWT/Redis 会话行为由全量验收中的 `ChatAcceptanceIT` 等测试验证。

## 5.1 定向验证

从 `api/` 执行：

```powershell
mvn '-Dtest=ChatFunctionCallingIT,OrderPersistenceIT' test
mvn '-Dtest=ChatFunctionCallingIT' test
```

首轮新增测试因 BEFORE_TEST_CLASS 的 SQL fixture 提前初始化 Spring，容器尚未启动而失败；订单授权集成 6 项通过。修正测试的动态属性初始化顺序后重跑，新增端到端 7 项全部通过。未改变生产业务逻辑。

| 覆盖 | 证据 |
| --- | --- |
| 消费者本人订单，RAG 命中/无匹配 | 真实 SQL 查询当前订单状态；工具结果交回模型生成回答 |
| 员工本商户订单，RAG 命中/无匹配 | 员工可读取本商户其他消费者的订单 |
| 消费者读取他人/跨商户/不存在订单 | 统一 DEPENDENCY_ERROR，无订单结果、无最终模型回合、无 done |
| SSE / 历史 / 数据库一致性 | 正文、sources、toolCalls 与落库记录一致；失败摘要为空 |
| 协议与幂等 | 首轮只声明 lookup_order，最终轮不声明工具；相同请求重复提交返回原 run，不重做模型或检索 |
| 摘要隐私 | 仅固定 name/status；不含订单号、参数或原始结果 |

成功用例在请求前修改隔离数据库中的订单状态，验证传给模型的是当前业务数据。RAG 命中时同时保留原问题和来源，无匹配时来源为空且仍完成订单查询闭环。已有 `OrderPersistenceIT` 另核对消费者、员工、跨租户同号订单及商品交易快照的真实 SQL 边界。

## 5.2 全量验收

```powershell
mvn verify
```

首轮 `mvn verify`：单元测试 183 项全部通过；集成测试 210 项中 209 项通过、1 项启动错误，断言失败 0 项。唯一错误为 `AuthDevManualIT` 同时启用 dev/test 时继承开发 RAG 配置并连接本机 Milvus，导致认证验收无法启动。该测试显式增加 `merchant.rag.enabled=false` 和 `merchant.agent.enabled=false`，使认证验收仅使用其专属 MySQL/Redis；未改变开发或生产配置，也未删除或跳过测试。

首轮日志：`api/target/function-calling-verify.log`。修正后的第二轮日志为 `api/target/function-calling-verify-final.log`：单元测试和 `AuthDevManualIT` 已通过，但随后 `AuthPersistenceIT` 在 Flyway 初始化时发生 MySQL 连接中断，4 项出现上下文启动错误；该轮之后因关机中断，没有完整构建结果，不计为通过。连接中断的具体原因未确认。

重启后完整复跑 `mvn verify`，日志为 `api/target/function-calling-verify-resumed.log`。最终于 2026-11-14 23:08:02（Asia/Shanghai）得到 **BUILD SUCCESS**，耗时 **24 分 23 秒**。`AuthPersistenceIT` 4 项均通过，未重现上一轮连接中断；本轮没有再修改业务实现或放宽测试条件。

| 测试阶段 | 执行数 | 断言失败 | 错误 | 跳过 |
| --- | --- | --- | --- | --- |
| Surefire 单元测试 | 183 | 0 | 0 | 0 |
| Failsafe 集成测试 | 210 | 0 | 0 | 0 |
| 合计 | 393 | 0 | 0 | 0 |

全量结果核对 `api/target/failsafe-reports/failsafe-summary.xml` 及 Maven 日志。相关回归证据包括：

- `AuthDevManualIT` 1 项、`AuthFlowIT` 15 项、`AuthPersistenceIT` 4 项：认证脚本、登录会话和授权持久化。
- `ChatAcceptanceIT` 4 项、`ChatHttpIT` 4 项、`ChatServiceIT` 9 项：普通聊天、SSE 契约、幂等、断线取消及超时终态。
- `ChatFunctionCallingIT` 7 项、`RagChatIT` 11 项、`RagPipelineIT` 1 项：真实订单工具闭环、RAG 来源与无匹配路径、检索管道。
- `ChatPersistenceIT` 3 项、`ChatSchemaIT` 2 项、`ChatToolSummaryMigrationIT` 1 项：历史与终态持久化、表结构及旧消息摘要兼容。
- `OrderPersistenceIT` 6 项和 `OrderFlowIT` 5 项：真实订单查询授权与既有订单流程；其他基础设施、库存、商品和物流回归同轮全部通过。

OpenSpec 验证：`openspec validate add-agent-function-calling --strict` 已通过；收尾同时核对任务进度与 `git diff --check`。测试导出造成的知识接口示例字段排序变化及临时 JSON 导出已清理。

## 限制

本地确定性协议桩验证应用协议和业务闭环，不评价真实模型的工具选择准确率或自然语言回答质量。未执行真实模型联调、开发库迁移、部署、commit、push 或 change 归档。
