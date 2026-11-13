# RAG 资料入库与聊天接入（任务 1–2）

已实现员工同步导入、列表，以及现有登录聊天的检索和 sources 快照。Springdoc 导出和真实模型完整问答联调留在任务 3。

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

可选 `RAG_MILVUS_TOKEN`；检索使用 `merchant.rag.top-k=3`（最多5）和 `min-score=0.5`（COSINE）。每片最多300码点，批次16片；需选择支持至少1200 UTF-8字节/片、16输入/批的服务。导入的所有外部阶段共享60秒预算、不自动重试；聊天检索与生成共享原聊天已保存的总 deadline。

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

V10新增两表和可空 `sources_json`；旧值 NULL 在聊天历史中返回 `sources:[]`。

## 现有聊天增量

沿用消费者或员工的本人会话与 Bearer token，不接受客户端覆盖 merchantId。先提交消息占位、运行权及 deadline，发送 start，再对原始问题做 Embedding → 本商户 Milvus topK → MySQL 商户与 READY 回源。只有最终预算内实际送入模型的片段进入 sources；参考资料放在独立 user 消息中，system 声明资料中的命令不作为指令。

```shell
curl -N -X POST http://localhost:8080/api/v1/chat/sessions/<sessionId>/messages \
  -H "Authorization: Bearer <accessToken>" -H "Content-Type: application/json" \
  -d '{"clientMessageId":"b57a8542-01fc-4a35-9baa-aef18da85889","content":"七日内可以退货吗？"}'
```

仍使用 start/token/done/error。成功 done 保留原公共标识、status、usage，并追加：

```json
{"sources":[{"documentId":"610000000000000001","chunkId":"620000000000000001","title":"售后政策","chunkNo":1,"excerpt":"购买后七日内，未拆封商品可申请退货。"}]}
```

sources 最多 topK 条（上限5），excerpt 最多300个 Unicode 码点，与实际入模片段一致。它表示模型参考资料，不表示逐句核验回答。成功正文和 sources 同事务提交后才发 done；GET 同一路径的本人分页历史为每条消息追加 sources，读取已保存快照，不重新查文档或向量。资料后续变化、关闭 RAG 都不影响已保存来源。

无匹配、全部低于门槛或所有片段都放不进预算时，直接返回“当前资料中没有找到相关信息，请补充说明或联系店铺确认”，sources=[]，不调用生成模型。检索依赖失败走 error/DEPENDENCY_ERROR；检索超时走 error/TIMEOUT；断线或取消后不再启动生成。旧消息、USER、失败/取消/超时、关闭 RAG 时的新回答均返回空来源。生成的预正文重试复用已检索上下文；相同 clientMessageId 与正文重复提交仍返回原 ChatRunView JSON，来源从历史读取。

任务2验证使用隔离 MySQL/Milvus 与受控外部模型替身，不代表真实 Embedding 与生成服务的完整链路已联调。
