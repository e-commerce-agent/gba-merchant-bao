# Agent Function Calling dev 实际联调

本事件对应 `add-agent-function-calling` 的 5.3，结束日期为 2026-11-14（Asia/Shanghai）。前置隔离集成与全量构建结果见 [5.1 / 5.2 验收](../20261114-agent-function-calling/verification.md)。本轮使用 PowerShell 操作现有 Compose 依赖和本地 dev 服务，调用已配置的真实模型；未使用模型协议桩替代联调。

## 启动结果

仓库根目录执行现有 Compose 配置：

```powershell
docker compose -f docker-compose.yml -f docker-compose.rag.yml --profile rag up -d --wait mysql redis milvus
docker compose -f docker-compose.yml -f docker-compose.rag.yml --profile rag ps
```

MySQL 8.0.34、Redis 7.4-alpine、Milvus 2.5.9 均为 healthy。复用现有命名卷，依赖端口分别为本机 3307、6380、19530。

从 `api/` 启动后端：

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=dev'
```

仅激活 dev，服务监听 8080，健康检查成功。Flyway 验证通过并在现有 `gba_dev` 应用 V11 `agent tool summary`；启动记录为 2026-11-14 23:23:22。RAG 保持启用，使用本机 Milvus 和已配置的真实 Embedding；生成模型为 `dots3-note-prev`。配置与凭据保留在本机忽略文件中。

## 实际工具闭环

使用既有员工密码登录 API，以 `M_3C_DEMO` 的开发员工创建独立验证会话。没有伪造 Actor、直接签发 token 或新增工具 HTTP 路由。认证信息只在脚本内存使用，验证后已调用退出接口撤销本次登录。

请求模型查询开发订单 `DEV-2026-0002` 的当前状态、支付状态和总金额。先通过授权订单详情 API 和只读 SQL 核对当前业务值，再通过原有消息 POST 接收完整 SSE。

| 项目 | 实际结果 |
| --- | --- |
| 查询前订单状态 | `SHIPPED` |
| 查询前支付状态 | `PAID` |
| 查询前订单金额 | `89.90 CNY` |
| 模型选择及执行 | `lookup_order` 成功，完成后返回 `SUCCEEDED` 摘要 |
| 最终回答 | 包含 `SHIPPED（已发货）`、`PAID（已支付）` 和 `89.90 元（CNY）`，与查询前实时业务值一致 |
| SSE | 收到 `start`、多个 `token`、一个成功 `done`，无 error |
| RAG | 本轮 `sources: []`，无匹配来源仍完成工具与模型回答闭环 |
| 历史与数据库 | 正文、sources、toolCalls 与 SSE 一致，消息状态 COMPLETED |
| 幂等 | 相同 clientMessageId 和正文重复提交返回原 runId |

最终 PowerShell 联调于 23:28:51 发起，文档核对于 23:29:54 完成。会话 `115110711369138176`、运行 `115110711624990720`、助手消息 `115110711624990722` 可用于追踪；脱敏检查结果见 [evidence.json](evidence.json)。成功摘要为：

```json
[{"name":"lookup_order","status":"SUCCEEDED"}]
```

运行日志位于本机忽略路径 `api/.runtime/logs/function-calling-dev-start.log`。PowerShell 脚本和完整非凭据结果分别位于 `api/.runtime/manual/function-calling-dev-verify.ps1`、`function-calling-dev-powershell-result.json`。

## springdoc 与前端接口判断

成功联调后读取 `/v3/api-docs.yaml/agent-conversation-v1` 和 `/v3/api-docs/agent-conversation-v1`。运行时 YAML 与仓库 `docs/api/agent-conversation-v1.openapi.yaml` 在统一换行后逐字一致；运行时 ChatMessage 和 ChatDone 均包含 toolCalls，分组仍为原有 6 个操作。

本轮不重复导出。当前交付已覆盖这次响应扩展，联调没有改变契约。前端继续使用原有发送消息 POST 与历史 GET，可按需读取工具摘要；无需新接口、新请求参数或新 SSE 事件，工具参数和执行协议留在后端。

## 脚本问题及修正

首次数据库比对曾失败：MySQL 命令行默认字符集将中文输出成问号。只读 HEX 核对确认数据库原文与 SSE 相同；PowerShell SQL 检查显式指定 utf8mb4，并按 UTF-8 解码正文，完整业务和持久化检查通过。

PowerShell 完整联调随后已通过模型、历史、数据库和幂等检查，但 springdoc YAML 请求沿用了仅接受 JSON/SSE 的 Accept 头，返回 406。补充文档请求可接受的 MIME 后，仅重试文档核对成功；未重复执行模型调用。两处均为临时验证脚本问题，没有修改应用实现或放宽业务断言。

本轮覆盖一个员工授权订单的真实模型成功链路及 RAG 无匹配路径；消费者越权、员工跨商户、RAG 命中、取消与超时等确定性覆盖仍以前置 5.1 / 5.2 结果为证，不据本次单例宣称全部真实模型场景已验收。
