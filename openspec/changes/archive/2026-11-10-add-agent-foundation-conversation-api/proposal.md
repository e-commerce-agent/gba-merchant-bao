# Proposal

## Why

认证已经提供可信的消费者/员工 Actor，但尚无统一的 LLM 对话服务。本 change 先建立能够接前端、承接后续 RAG、Function Calling、Redis ChatMemory，并适配未来 Nginx 多实例部署的基础对话能力，避免一次混入四个技术亮点的完整实现。

## What Changes

本期按六项运行时子技术组织：

1. **LangChain4j 模型调用**：统一使用 OpenAI Chat Completions 兼容协议，通过 `base-url`、`api-key`、`model` 切换 DeepSeek、Qwen 等兼容服务；单次部署仅激活一套配置。
2. **Prompt 与消息组装**：组装 system/user/assistant，按最近消息数量和输入预算读取 MySQL 历史；不做摘要，不实现 ChatMemory。
3. **MySQL 会话与消息持久化**：保存完整会话历史、用户与助手消息、幂等标识、生成状态和必要调用元数据。
4. **对话执行控制**：实现防重复提交、同会话串行、超时、断开取消、有限重试和稳定错误；共享数据库维护运行状态，允许未来多实例共享历史。
5. **SseEmitter 流式输出**：提供登录后的 HTTP/SSE 入口，输出 start/token/done/error，并保留 citation/tool_call 事件扩展约定；不实现事件重放。
6. **认证与数据隔离**：复用可信 Actor，消费者和员工均只能操作本商户本人创建的会话；不新增匿名问答或客服接管。

交付流程独立于上述子技术：需求与验收标准 → API/SSE 契约 → 表/索引/Flyway 设计 → 各子技术 TDD 实现 → Apifox 联调 → 整体验收。OpenAPI 是接口契约，Flyway 是数据库演进工具，TDD 是开发方法，Apifox 是测试工具，均不另算运行时子技术。

后续 change 分别实现 RAG（切分、向量化、Query 改写、混合检索、Rerank、引用及评测）、只读业务 Function Calling、RabbitMQ 异步文档导入和 Redis ChatMemory。RabbitMQ 是为 RAG 准备文档的异步链路，不进入本期聊天请求处理链。

## Capabilities

### New Capabilities

- `agent-runtime`: OpenAI 兼容模型交互、有限上下文、流式事件、取消、超时、重试和安全日志行为。
- `agent-conversation`: 登录用户的本人会话、MySQL 完整历史、SSE 接口、幂等和跨实例一致性行为。

### Modified Capabilities

无。现有认证能力仅作为可信 Actor 和授权前置，不改变其需求契约。

## Impact

- 后端新增 `com.gba.merchantbao.agent` 与 `com.gba.merchantbao.chat` 业务包，沿用 controller/service/mapper/entity/dto/vo/config 约定。
- `api/pom.xml` 增加 LangChain4j OpenAI 集成，配置提供 `base-url/api-key/model`、消息上限和超时；真实密钥只放忽略的本地配置或环境变量。
- `api/src/main/resources/db/migration` 按现有 V4 继续向后新增迁移，当前暂按 V5 设计；商品、订单等模块汇合时再协调未执行编号，不等待统一编号才开发，不修改已执行迁移。
- 新增会话创建/列表/详情/归档、消息历史和 POST SSE 接口及 OpenAPI 快照。前端通过 Bearer + fetch 读取流；本期交付接口与对接约定，完整前端和 Nginx 集群部署后续集成。
- 新增隔离 MySQL/Redis Testcontainers 验证：Redis 仅用于既有认证；不新增 ChatMemory 键、摘要或 TTL 机制。演示数据与测试 fixture 分离。
- 增加有界执行、幂等、跨实例共享状态及断流恢复约定，未来无需依赖 Nginx 粘性会话；本期不承诺跨实例接续生成。
