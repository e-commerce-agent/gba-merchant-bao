# Agent Runtime Spec Delta

## MODIFIED Requirements

### Requirement: Context selection shall remain bounded without ChatMemory

模型输入 MUST 由服务端 system Prompt、当前用户输入、本人会话的有限最近历史，以及启用 RAG 时本商户检索到的资料片段构成，并同时受消息数量及输入预算限制。旧历史 MUST 按完整轮次淘汰，仅成功完成的轮次进入后续上下文；系统 MUST 保留当前输入与 system Prompt，二者单独超限时明确拒绝。资料片段 MUST 作为参考内容而非系统指令。系统 MUST NOT 在本期生成历史摘要或使用 Redis ChatMemory。

#### Scenario: A long conversation exceeds the input budget
- **WHEN** 历史消息超过条数或输入预算
- **THEN** 服务从最近的完整成功轮次选取可容纳的历史，为输出及检索片段预留预算，不静默截断当前输入；MySQL 完整历史仍可分页读取

#### Scenario: A failed answer exists in history
- **WHEN** 最近一次回答取消、超时或失败后用户再次发送消息
- **THEN** 失败轮次仍可在历史查询中看到，但其未完成助手文本不作为成功回答注入新请求

#### Scenario: Retrieved text is added to a model request
- **WHEN** RAG 找到当前商户的相关片段
- **THEN** 仅将预算内的片段作为参考资料注入，原始用户输入保持完整且只出现一次
