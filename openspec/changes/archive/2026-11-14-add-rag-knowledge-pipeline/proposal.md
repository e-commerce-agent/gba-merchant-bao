# Proposal

## Why

在现有 LangChain4j LLM 对话服务上补一条简单可运行的 RAG 流程：导入资料后，登录用户提问，后端从 Milvus 检索资料并让模型据此回答，前端通过 Springdoc 导出的接口完成对接。

## What Changes

- 员工同步上传小型 UTF-8 TXT/Markdown，按段落切分并调用 Embedding 模型写入 Milvus。
- 现有登录聊天在生成前进行本商户向量检索，将命中片段放入模型上下文；未命中时明确提示资料不足。
- 在现有 SSE done 与历史响应中增加参考资料 sources，方便前端展示文档标题和片段。
- 通过 Springdoc 导出资料上传、资料列表和聊天增量响应，提供最少联调样例。
- 验证以链路跑通为准，失败问题简单记录后重试，不建设评测或 Badcase 平台。

本 change 只做基础 RAG。匿名问答、Function Calling、RabbitMQ 异步上传、Redis ChatMemory 仍由后续 change 实现。Query 改写、混合检索、Rerank、文档版本/撤权管理、后台任务与复杂业务场景均移出本次范围。

## Capabilities

### New Capabilities

- `rag-retrieval`: 简单资料导入、按商户检索、证据注入与无资料处理；不单独建立知识管理和评测能力。

### Modified Capabilities

- `agent-runtime`: 允许在现有有界上下文中加入检索片段。
- `agent-conversation`: done 和本人历史返回本次检索使用的参考资料。

## Impact

- 后端沿用 Java 21、Spring MVC 和 LangChain4j 1.9.1，在现有聊天执行边界增加基础检索服务。
- 引入 Milvus 与 Embedding 配置；新增两个知识资料接口，聊天发送请求体保持 content/clientMessageId。
- MySQL 仅新增文档、切片两张表，chat_message 增加 sources_json；不建立任务、候选轨迹、评测或反馈表。
- 现有认证、本人会话归属、幂等、MySQL 历史和 SSE 终态规则继续复用。RAG 默认关闭，旧功能不要求新依赖可用。
