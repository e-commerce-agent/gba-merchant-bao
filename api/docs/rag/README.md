# RAG 资料入库（任务 1）

当前仅实现员工同步导入及列表，聊天检索和 sources 响应留在任务 2，Springdoc 导出和完整问答联调留在任务 3。

## 配置

默认 `merchant.rag.enabled=false`，不连接 Milvus，也不要求 Embedding 凭证。启用后启动时创建/核验固定 collection；维度、主键、标量字段或 COSINE 不匹配则拒绝启动，不覆盖原数据。

实现使用 LangChain4j OpenAI Embedding 1.9.1、Milvus Java SDK 2.5.9（显式 BIGINT schema）、Milvus server v2.5.9。Compose 采用该版本支持的 standalone 内嵌 etcd、本地存储和 RocksMQ，依赖随 server 镜像固定；无需外置 MinIO/etcd。适合本次小规模单实例演示。

默认模型配置为 OpenAI `text-embedding-3-small`、1536 维；填写该模型的真实 Embeddings API 根地址和密钥，不能直接复用不支持 Embedding 的聊天服务。模型供应方未提供不可变版本号；换模型时换 collection 并重新导入。测试用确定性本地协议桩，不代表真实模型已联调。

环境变量：

```text
MERCHANT_RAG_ENABLED=true
RAG_EMBEDDING_BASE_URL=https://api.openai.com/v1
RAG_EMBEDDING_API_KEY=<在本地环境设置，勿提交>
RAG_EMBEDDING_MODEL=text-embedding-3-small
RAG_EMBEDDING_DIMENSION=1536
RAG_MILVUS_URI=http://127.0.0.1:19530
RAG_COLLECTION=knowledge_text_embedding_3_small
```

可选 `RAG_MILVUS_TOKEN`；`merchant.rag.top-k=3`（最多5）和 `min-score=0.5` 预留给后续检索。每片最多300码点，批次16片；需选择支持至少1200 UTF-8字节/片、16输入/批的服务。所有外部阶段共享60秒预算、不自动重试。

开发基础设施：

```shell
docker compose -f docker-compose.yml -f docker-compose.rag.yml --profile rag up -d milvus
```

Milvus 仅绑定 loopback，一个独立持久卷保存向量、内嵌元数据和日志队列。生产使用 `api/docs/prod-profile/opt/gba/` 下同名 overlay，设置 Embedding 凭证；叠加后启用 `rag`、`deploy` profiles，无向量端口暴露。关闭 RAG 时移除 overlay 并重建 app；保留资料和持久卷。

## 员工接口

使用现有员工登录取得 Bearer token；两个知识接口均仅员工可访问，merchantId/accountId 从可信登录身份获取。

```shell
curl -X POST http://localhost:8080/api/v1/knowledge/documents \
  -H "Authorization: Bearer <员工accessToken>" \
  -F "title=售后政策" -F "file=@policy.md"

curl 'http://localhost:8080/api/v1/knowledge/documents?size=20' \
  -H "Authorization: Bearer <员工accessToken>"
```

单文件 UTF-8 `.txt`/`.md`（可带 BOM），最大1 MiB、5万码点、200片；title去首尾空白后1–200码点。每片最多300码点，优先段落/句界，长句按码点回退，重叠最多30码点。非文本格式415，非法UTF-8/空内容/非法分页400，超限413，依赖失败503；沿用ApiResponse和X-Request-Id，响应no-store。

上传成功201的 `data` 示例：

```json
{"documentId":"610000000000000001","title":"售后政策","status":"READY","chunkCount":2,"createdAt":"2026-11-13T10:00:00Z"}
```

列表200的 `data` 为 `{"items":[上述摘要],"nextCursor":null,"hasMore":false}`；size为1–100，下一页原样传回nextCursor。按createdAt/id倒序，显示本商户PROCESSING/READY/FAILED摘要，不返回全文。重复上传创建新文档，不提供删除/修改/重试任务。

MySQL短事务先写PROCESSING和切片，事务外Embedding→Milvus→强一致查询确认全部ID，再短事务改READY。失败标FAILED；即使向量部分写入，READY回源门禁也拒绝使用；崩溃遗留PROCESSING同样不可用，无自动恢复。原文件不落盘到业务存储；Servlet临时multipart文件由容器管理。

V10新增两表和可空`sources_json`；旧值NULL在存储兼容测试中按空数组处理。实际聊天历史/done的sources字段与持久化映射在任务2接入，本次不改变聊天契约。
