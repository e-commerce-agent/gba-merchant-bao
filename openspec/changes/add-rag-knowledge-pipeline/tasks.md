# Tasks

只实现基础 RAG，按以下顺序完成；每项包含必要验证，不再拆分复杂业务场景或评测平台。

## 1. 资料入库

- [ ] 1.1 接入兼容现有 LangChain4j 的 Embedding/Milvus 依赖、独立配置和 Compose；验证 RAG 关闭时原应用可启动，启用时能连接正确维度的 collection，配置说明写明实际模型与版本。
- [ ] 1.2 新增文档、切片表及 chat_message.sources_json 的 Flyway 迁移；隔离 MySQL 验证迁移、商户复合归属约束和旧消息 sources=[]。
- [ ] 1.3 实现员工 TXT/MD 同步上传、简单段落切分、Embedding 和 Milvus 入库，及文档分页列表；用有效/非法文件和角色请求验证限制、READY发布及失败资料不可检索，并记录上传接口用法。

## 2. 现有聊天接入 RAG

- [ ] 2.1 实现当前问题向量化、本商户 topK 检索及 MySQL READY 回源；验证相关资料命中、跨商户资料不返回，真实无匹配与依赖失败分别处理。
- [ ] 2.2 在已接收消息的执行阶段加入检索及有界证据上下文，复用现有 LLM/SSE；验证原问题完整、无匹配提示、失败/超时/断线终止，以及重复 clientMessageId 不重跑检索或生成。
- [ ] 2.3 将实际入模片段保存为 sources，与成功正文同事务提交，在 done 和本人历史返回；验证来源标题/片段与输入一致、旧消息/无匹配/失败消息为空，并更新聊天增量响应说明。

## 3. 联调与前端交付

- [ ] 3.1 为新增上传/列表和聊天 sources 完善 Springdoc schema、鉴权与 SSE 示例；导出运行时 OpenAPI JSON，检查前端能获得 multipart、Source 与 done 数据结构。
- [ ] 3.2 用商品/售后/店铺规则三个合成文件和约5个问题跑真实 Embedding→Milvus→现有LLM 问答，记录命中、无匹配及简单 Badcase 修正结果；没有真实服务的项目明确记为未测，不虚构指标。
- [ ] 3.3 执行相关认证/会话回归与 RAG 集成验证，交付 Springdoc 导出文件及一份简短联调说明（配置、上传、提问、sources、关闭RAG）；确认未引入匿名、FC、RabbitMQ或ChatMemory实现。
