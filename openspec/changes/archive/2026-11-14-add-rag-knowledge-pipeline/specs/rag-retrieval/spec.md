# RAG Retrieval Spec Delta

## Purpose

为现有登录聊天提供简单的资料增强问答：商户导入少量文本资料后，系统按当前商户检索相关片段并辅助模型回答，使前端能够完成资料接入、提问和查看参考资料的基本演示。

## ADDED Requirements

### Requirement: Staff can import simple knowledge documents

系统 MUST 允许员工同步导入本商户的小型 UTF-8 TXT/Markdown 文件，进行有界切分和向量化；只有完整入库成功的资料才能用于问答。消费者 MUST NOT 上传资料，客户端字段 MUST NOT 覆盖可信商户身份。

#### Scenario: Staff imports a policy
- **WHEN** 员工上传有效的售后政策文本
- **THEN** 系统返回已就绪的文档标识及切片数量，该商户后续提问可检索到资料

#### Scenario: Import fails or the caller is not staff
- **WHEN** 上传向量化失败或请求者不是员工
- **THEN** 系统返回明确错误，失败资料不参与回答；角色越权在资料处理前被拒绝

### Requirement: Chat answers use tenant-scoped retrieved text

RAG 启用时系统 MUST 在现有本人聊天生成前检索本商户已就绪资料，将有界片段加入上下文。无匹配时 MUST 明确提示资料不足；检索依赖失败 MUST 按错误处理，不能伪装成正常无匹配或成功回答。

#### Scenario: A question matches uploaded text
- **WHEN** 登录用户在本人会话询问已导入的商品或政策信息
- **THEN** 系统使用相关片段辅助原有模型回答，参考资料与输入模型的片段对应

#### Scenario: No matching text exists
- **WHEN** 本商户没有匹配资料
- **THEN** 返回资料不足的提示与空参考资料，不编造店铺承诺

#### Scenario: Another merchant has the answer
- **WHEN** 只有另一商户的资料包含所问内容
- **THEN** 系统不读取或返回该商户的片段、标题和来源
