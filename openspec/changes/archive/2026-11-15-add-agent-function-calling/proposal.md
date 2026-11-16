# Proposal

## Why

当前 Agent 已支持登录会话、SSE 流式回答和 RAG，但不能基于当前用户问题查询实时业务状态。增加一次有界的只读订单查询工具调用，可验证模型工具选择、现有授权业务服务和最终流式回答的端到端闭环。

## What Changes

- 在现有聊天生成流程中支持 OpenAI Chat Completions 兼容的 Function Calling；模型可选择调用一个按订单号查询订单详情的只读工具，服务端执行后把结果交回模型继续生成最终回答。
- 工具使用已认证 Actor 和现有 `OrderQueryService`，保持消费者本人订单、员工本商户订单的既有访问范围；工具参数不得携带或覆盖身份、商户或角色。
- SSE 继续使用 `start/token/done/error`；成功完成事件及消息历史增加有界、脱敏的工具调用摘要，向调用方说明模型使用了订单查询工具。
- RAG 与工具可在同一轮问答中协作。无匹配知识片段时仍可调用工具；工具故障按依赖失败结束，不伪装成无订单或模型答案。
- 复用 `chat_message` 保存最终回答及工具摘要，不新增 Function Calling 专用数据库表或 API 路由；扩展现有聊天 OpenAPI 契约。
- 不实现 RabbitMQ 上传/批量任务、Redis ChatMemory、匿名访问、写操作工具、MCP 或多 Agent。

## Capabilities

### New Capabilities
- `agent-function-calling`: 在登录聊天中安全地选择并执行单个只读订单查询工具，并向 SSE 与历史暴露摘要。

### Modified Capabilities
- `agent-conversation`: 成功 SSE 完成事件和历史消息包含工具调用摘要，既有会话及消息 API 路径不变。
- `rag-retrieval`: RAG 无匹配时保留原问题进入模型生成，允许模型使用实时订单查询工具；RAG 来源行为保持不变。

## Impact

- API：扩展现有 `POST /api/v1/chat/sessions/{sessionId}/messages` SSE done 数据和 GET 消息历史；不增加独立工具 HTTP API。
- 服务：扩展 Agent 模型协议/适配器、聊天运行执行和 `OrderQueryService` 的复用；保持租户、会话和订单本人授权。
- 持久化：扩展现有 `chat_message` 记录及 Flyway 迁移以保存有界工具摘要；不创建新业务表。
- 验证：增加模型协议工具往返、授权边界、工具失败、SSE 与消息历史契约验证；不要求真实模型凭证作为自动化测试前提。
