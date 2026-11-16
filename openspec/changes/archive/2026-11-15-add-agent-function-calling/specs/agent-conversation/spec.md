# Spec Delta

## ADDED Requirements

### Requirement: Completed conversation responses shall expose persisted tool summaries

现有聊天消息 API MUST 在助手消息和 done 事件中返回 `toolCalls` 数组。该数组 MUST 与已完成消息一同持久化；未调用工具时 MUST 为空数组，且 MUST NOT 暴露原始工具参数或订单隐私字段。

#### Scenario: 订单工具调用完成
- **WHEN** 模型使用已授权的订单查询并完成回答
- **THEN** SSE done 事件及后续分页历史包含相同且已持久化的有界工具摘要

#### Scenario: 既有消息没有工具摘要
- **WHEN** 历史中包含本能力启用前创建的消息，或未成功完成的运行消息
- **THEN** API 为该消息返回 `toolCalls: []`
