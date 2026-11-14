# Spec Delta

## Purpose

让已认证的 Agent 对话通过一次有界的只读查询获取实时订单信息，并沿用现有聊天契约流式返回模型最终回答。

## ADDED Requirements

### Requirement: The Agent shall perform one authorized read-only order lookup

系统 MUST 向模型提供一个订单详情查询函数，并且仅使用当前已认证对话 Actor 和经过校验的订单标识执行查询。调用方或模型 MUST NOT 提供或覆盖账号、商户或角色身份。

#### Scenario: 模型选择查询订单
- **WHEN** 已认证用户询问订单，且模型为已声明的查询函数返回有效订单号
- **THEN** 系统使用当前 Actor 调用现有订单查询能力，仅将该 Actor 有权访问的订单详情返回给模型，并继续生成最终回答

#### Scenario: 消费者查询其他消费者的订单
- **WHEN** 消费者选择的订单号不在该消费者的授权范围内
- **THEN** 查询不泄露订单是否存在，运行以稳定且不泄露依赖细节的错误结束，并且不向模型发送订单数据

#### Scenario: 工具参数包含身份覆盖字段或无效输入
- **WHEN** 工具调用包含商户、账号、角色字段、格式错误的参数或无效订单号
- **THEN** 系统拒绝该调用，不使用其中提供的身份，也不查询无界资源

### Requirement: A chat run shall bound tool execution and preserve streaming semantics

系统 MUST 对每条用户消息最多执行一次订单查询，并 MUST 将模型工具调用协议消息保留在内部，不改变现有 `start/token/done/error` SSE 事件流。

#### Scenario: 订单查询成功
- **WHEN** 授权查询返回订单详情，且后续模型生成成功
- **THEN** 回答和工具摘要持久化后，客户端通过 token 事件接收最终回答，并收到一个 done 事件

#### Scenario: 模型在同一轮再次请求调用工具
- **WHEN** 模型针对同一条用户消息请求第二次工具调用
- **THEN** 系统拒绝后续工具执行，并以依赖失败状态终止本轮运行

#### Scenario: 订单服务发生故障
- **WHEN** 已授权的订单查询遇到存储或服务故障
- **THEN** 系统以稳定的依赖错误终止本轮运行，不把故障伪装为订单不存在或回答成功

### Requirement: Tool summaries shall be bounded and available with completed chat messages

系统 MUST 为已完成的助手消息持久化有界的工具名称及非敏感结果摘要，并在 SSE done 和分页消息历史中返回；未调用工具时 MUST 返回空数组。摘要 MUST NOT 暴露工具参数、凭证、私人地址或联系方式，也不得暴露原始工具结果。

#### Scenario: 已完成的回答使用了订单查询
- **WHEN** 使用订单查询工具辅助生成的回答完成
- **THEN** done 和后续历史查询返回与该助手消息关联的同一份有界订单查询摘要

#### Scenario: 消息未调用工具或运行未成功完成
- **WHEN** 查询历史消息、消息为用户消息，或助手运行失败、取消或超时
- **THEN** 该消息的工具摘要为空数组
