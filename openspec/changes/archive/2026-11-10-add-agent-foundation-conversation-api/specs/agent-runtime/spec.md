# Spec Delta

## Purpose

为后续知识问答和业务工具提供一个可替换、可观测且受授权上下文约束的 Agent 执行边界。该能力只负责模型编排和对话运行协议，不把知识库、业务 Tool 或异步文档处理耦合进基础运行时。

## ADDED Requirements

### Requirement: Agent execution shall use an explicit runtime contract

系统 MUST 根据可信身份、会话、用户消息、模型配置和 Prompt 版本执行可追踪的 Agent run；客户端或模型输出的 merchantId、accountId、role MUST NOT 覆盖可信上下文。运行结果 MUST 区分完成、取消、超时和失败，输入拒绝 MUST 发生在模型调用之前。

#### Scenario: Trusted context is used for a run
- **WHEN** 已认证消费者或员工提交消息，并在请求体中附带不同的 merchantId 或 accountId
- **THEN** Agent 仅使用认证 Actor 和会话归属建立运行上下文，拒绝未声明的覆盖字段，不调用跨租户资源

#### Scenario: Runtime returns a typed terminal result
- **WHEN** 模型调用成功、被取消、超时或依赖不可用
- **THEN** 运行以唯一 runId 结束，完成使用 done 事件，其余使用带 CANCELLED、TIMEOUT 或 DEPENDENCY_ERROR 错误码的 error 事件，不把部分文本标记为完成

### Requirement: Model and prompt configuration shall be explicit and secret-safe

系统 MUST 支持 base-url、api-key、model、输出上限、超时和 Prompt 版本配置；真实 API key MUST 只存在忽略的本地配置或环境变量。Agent 启用且配置非法时 MUST 拒绝启动。日志与响应 MUST 不含密钥、完整私密 Prompt 或原始凭证。

#### Scenario: Invalid model configuration is detected
- **WHEN** Agent 已启用，而 base-url、model、超时或密钥配置缺失、非法或仍为占位值
- **THEN** 应用启动校验失败，指出配置项而不回显密钥；未启用 Agent 的已有应用和认证测试不要求模型凭证

#### Scenario: Prompt version is traceable
- **WHEN** 一次 Agent run 进入模型调用
- **THEN** run 保存 promptVersion、脱敏的模型服务标识和 modelName，后续配置变化不改变已有消息的归因信息

### Requirement: Model requests shall use the OpenAI Chat Completions compatible protocol

模型交互 MUST 使用 OpenAI Chat Completions 兼容的请求及流式响应格式；供应商切换 MUST 通过 base-url、api-key 和 model 完成，单次部署仅激活一套配置。系统 MUST NOT 依赖 Anthropic Messages 协议、供应商自动故障切换或供应商专有字段完成本期基础问答。

#### Scenario: A compatible service is selected by configuration
- **WHEN** 将配置从一个兼容服务切换到另一个 DeepSeek、Qwen 或兼容网关并重启应用
- **THEN** 消息仍按相同协议发送，应用对外的 HTTP/SSE 契约不改变，密钥不暴露给前端

### Requirement: Context selection shall remain bounded without ChatMemory

模型输入 MUST 由服务端 system Prompt、当前用户输入和本人会话的有限最近历史构成，并同时受消息数量及输入预算限制。旧历史 MUST 按完整轮次淘汰，仅成功完成的轮次进入后续上下文；系统 MUST 保留当前输入与 system Prompt，二者单独超限时明确拒绝。系统 MUST NOT 在本期生成历史摘要或使用 Redis ChatMemory。

#### Scenario: A long conversation exceeds the input budget
- **WHEN** 历史消息超过条数或输入预算
- **THEN** 服务从最近的完整成功轮次选取可容纳的历史，为输出预留预算，不静默截断当前输入；MySQL 完整历史仍可分页读取

#### Scenario: A failed answer exists in history
- **WHEN** 最近一次回答取消、超时或失败后用户再次发送消息
- **THEN** 失败轮次仍可在历史查询中看到，但其未完成助手文本不作为成功回答注入新请求

### Requirement: Streaming output shall use a stable SSE event protocol

对话运行 MUST 通过 `text/event-stream` 顺序发送带 event、id 和 JSON data 的 start、token、done 或 error。citation/tool_call 仅保留后续扩展约定，本期 MUST NOT 伪造引用或工具结果。事件 MUST 关联 runId、messageId 和 requestId；在连接可写时每个 run 最多发送一次终止事件。id MUST NOT 被解释为支持事件重放。

#### Scenario: A successful streamed response is assembled
- **WHEN** 模型连续产生多个文本片段并正常结束
- **THEN** 客户端收到 start、零个或多个 token、done，token 顺序与服务端文本顺序一致；done 表示最终消息已成功保存，提供终止状态和可得的用量，供应商不提供用量时为 null

#### Scenario: A stream reports a dependency failure
- **WHEN** 模型连接在 start 后失败
- **THEN** 服务发送一个 error 终止事件，包含稳定错误码、runId 和 requestId，不发送堆栈、密钥或未确认的 done

### Requirement: Cancellation, timeout, and disconnect shall stop work safely

系统 MUST 处理客户端断开和服务端 deadline，在检测后停止转发并请求取消上游生成；本期不提供独立取消 API。已落库用户消息 MUST 保留，助手输出 MUST 标记 CANCELLED、TIMED_OUT 或 FAILED，并拒绝迟到回调覆盖终态。上游实际停止时间受供应商和 SDK 能力限制，服务端 MUST 使用有限超时关闭连接，不承诺撤销供应商已经产生的费用。

#### Scenario: Client disconnects during generation
- **WHEN** SSE 客户端在收到部分 token 后断开连接
- **THEN** 服务停止写流并请求取消上游调用，记录终止原因并关闭资源，不把部分文本标为完成

#### Scenario: Request exceeds the deadline
- **WHEN** 模型调用超过配置的总超时或首 token 超时
- **THEN** 服务在 deadline 内发送一次 timeout error（若连接仍可写），持久化失败状态，并释放运行资源

### Requirement: Runtime failures shall be privacy-safe and observable

系统 MUST 将校验、授权、模型限流、上游错误和超时映射为稳定 HTTP/SSE 错误，并记录 requestId、runId、商户、角色、耗时和终止原因。有限重试 MUST 仅用于尚未向客户端发送正文 token 的瞬时网络错误或可重试的 429/5xx，受同一总 deadline 限制；密钥/参数错误和输出后的失败 MUST NOT 自动重试。

#### Scenario: A retryable upstream error is retried once
- **WHEN** 上游返回瞬时网络错误或可重试的 429/5xx，且尚未向客户端发送正文 token
- **THEN** 服务在次数和总时限内重试并保持同一 runId，不自动切换供应商、不重复写入用户消息；这不保证供应商端只计费一次

#### Scenario: Upstream fails after tokens have been forwarded
- **WHEN** 客户端已经收到正文 token 后上游发生故障
- **THEN** 服务终止本次流并保存失败状态，不从头自动重试生成，不重复发送已有正文

#### Scenario: An authorization error is rejected before model access
- **WHEN** Actor 不属于会话商户、角色不允许使用入口或会话不存在
- **THEN** 服务在调用模型前返回 401/403/404 中适用的稳定错误，不产生模型费用、不写入助手消息
