# Spec Delta

## MODIFIED Requirements

### Requirement: Chat answers use tenant-scoped retrieved text

RAG 启用时系统 MUST 在现有本人聊天生成前检索本商户已就绪资料，将有界片段加入上下文。无匹配时 MUST 保留原始问题并继续调用模型，以允许模型使用已授权的实时业务查询工具；不得编造资料引用或店铺承诺。检索依赖失败 MUST 按错误处理，不能伪装成正常无匹配或成功回答。

#### Scenario: A question matches uploaded text
- **WHEN** 登录用户在本人会话询问已导入的商品或政策信息
- **THEN** 系统使用相关片段辅助原有模型回答，提供给模型的参考资料与返回来源对应

#### Scenario: No matching text exists
- **WHEN** 本商户没有匹配的已就绪资料
- **THEN** 系统以空参考资料保留原始问题并继续调用模型，不返回预设的资料不足终结回答；模型可按需调用已有授权的实时只读业务工具，且不得编造资料引用或店铺承诺

#### Scenario: Another merchant has the answer
- **WHEN** 只有另一商户的资料包含所问内容
- **THEN** 系统不读取或返回该商户的片段、标题和来源，并以空参考资料继续处理当前问题
