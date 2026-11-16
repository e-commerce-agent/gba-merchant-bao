# Agent Conversation Spec Delta

## ADDED Requirements

### Requirement: Completed messages expose retrieved reference sources

系统 MUST 在现有 SSE done 和本人消息历史中返回 sources 数组，包含本次实际注入上下文的资料标识、标题、片段序号和有界摘要。成功正文和 sources MUST 一致保存后再发送 done；sources 是检索参考资料，不代表已逐句核验模型回答。旧消息、失败回答及无资料时 MUST 返回空数组。合法幂等重试 MUST 返回原运行，不重新检索或生成。

#### Scenario: Frontend displays a RAG answer
- **WHEN** 一次使用资料的回答成功结束
- **THEN** 前端从 done 获得参考资料，之后读取本人历史仍能取得相同已保存的 sources；原有 start/token/done/error 事件名称保持不变

#### Scenario: The same client message is submitted again
- **WHEN** 用户使用原 clientMessageId 和原正文重复提交
- **THEN** 返回原 ChatRunView，不新增检索或生成；参考资料可从本人历史读取
