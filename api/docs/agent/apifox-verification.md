# Agent Apifox 导入与验收

实际阶段状态见 [本目录 README](README.md#apifox-status)。

## 导入与本地环境

1. 在用户已导入的目标项目中核对 Agent 与认证模块，避免重复导入或覆盖。检查全部六操作和八模型；Server 不导入为共享环境，随机测试端口不写入共享配置。
2. 操作应为：创建会话、分页会话列表、本人详情、归档、分页消息历史、POST 消息。消息操作的 200 同时保留 text/event-stream 和 application/json；JSON 用于同 key 同正文的幂等返回。导入的 chatBearer 为 HTTP Bearer。
3. 建立独立 `Agent-受控模型-本机` 环境。baseUrl 指向本次隔离验收应用随机端口；真实模型另建 `Agent-dev-真实模型-本机`，baseUrl 为 `http://127.0.0.1:8080`。禁止把受控命令发给真实模型。
4. 本地值中保存 accessToken、staffToken、otherTenantToken、sessionId、clientMessageId；共享/初始值留空。Bearer 引用 `{{accessToken}}`，路径引用 `{{sessionId}}`。登录返回的 data.accessToken 与后端 merchant.agent.api-key 不同；Apifox 不需要模型 key。
5. 新发送意图使用新 UUID，网络重试和幂等用例复用原 UUID/原文；保存业务标识供历史与 DB 查询。环境和用例导出前去掉 token、开发密码和真实 key。

自动化 [ChatAcceptanceIT](../../src/test/java/com/gba/merchantbao/test/chat/ChatAcceptanceIT.java) 提供独立 MySQL/Redis 与真实认证的可控模型基线。另提供仅显式选择才运行的 [AgentApifoxWorkspace](../../src/test/java/com/gba/merchantbao/test/chat/AgentApifoxWorkspace.java)，默认 test/verify 不选择它，也不打入应用 JAR。需要 Docker，在仓库根目录启动：

```powershell
mvn -f api/pom.xml '-Dtest=AgentApifoxWorkspace#serveApifoxOnDisposableInfrastructure' test
```

服务仅绑定 127.0.0.1，使用 test profile 与动态端口，45 分钟后超时失败。准备成功后 `api/.runtime/manual/agent/agent-apifox-local.json` 记录 baseUrl、fixtureUrl 与手工用例 sessionId；不记录真实模型 key。fixtureUrl 是本机临时入口，不要公开。每次请求可从该入口获取临时应用登录 token，token 只存脚本运行变量，不放共享环境。停止时从仓库根目录另开终端执行：

```powershell
New-Item -ItemType Directory -Force -Path api/.runtime/manual/agent
New-Item -ItemType File -Path api/.runtime/manual/agent/agent-apifox.stop
```

服务会退出临时账号会话、关闭 helper、清理容器。下次启动前只删除该 stop 标记。不要把临时地址当作长期服务或指向共享 dev 中间件。

可将 [14 项 Postman 格式用例](../../../docs/api/agent-acceptance.postman_collection.json) 导入 Apifox 的独立测试目录，建立独立本机环境，设置 fixtureUrl 的本地值（共享值留空）。集合前置脚本从本机 fixture 获取 baseUrl/临时 token，每个请求只通过 `pm.variables` 引用。按 01–14 顺序运行，测试脚本校验状态、MIME、事件/正文及按 sessionId/runId 查询的 DB 结果；这些脚本目前只通过 JSON/JavaScript 语法检查，尚未在 Apifox 执行，不能作为 Apifox 通过证据。忙状态与停止请求仍需下述人工操作。

受控命令：普通内容输出 hello 与空格 world 后完成；accept:error 输出 hello 后故障；accept:timeout 首 token 超时（10 秒）；accept:busy/accept:abort 输出 hello 后等待（总时限 30 秒），期间可验证 busy 或点击停止。busySessionId/abortSessionId 由 fixture 提供，使用有效临时 token，通过导入的消息操作发送新 UUID。busy 等待期间相同 key/正文为 JSON 原 run、不同 key 和归档为 409/13002；abort 需停止后查询历史直到 CANCELLED，不把 TIMED_OUT 当作取消通过。不要将这些命令发送给真实网关。

## 用例组织和断言

| 目录 | Apifox 请求/操作 | 预期 |
| --- | --- | --- |
| 01 本人完整链路 | 登录、创建、列表/详情、发送、历史、归档两次 | 创建 201，字符串 ID/UTC 时间；SSE start/token/token/done，预设文本 hello world；COMPLETED；归档幂等 |
| 02 身份隔离 | 同租户员工查消费者、同租户另一账号、另一租户访问详情/消息/归档/发送 | 均 404；缺少或非法 Bearer 为 401，身份覆盖字段为 400；模型未调用 |
| 03 幂等与 busy | 同 key 同文重发、同 key 改文、受控等待期间不同 key 发送及归档 | 幂等为 200 JSON 原 run；改文 409/13003；busy 409/13002，无第二次生成 |
| 04 上游错误 | 受控模型输出 partial 后报错 | start/token/error，安全 DEPENDENCY_ERROR，无 done；历史 FAILED，保留部分文本 |
| 05 超时 | 受控模型不输出 | start/error TIMEOUT，无 done；TIMED_OUT，运行权释放，迟到回调无效 |
| 06 客户端中止 | 输出部分 token 后点击停止；以原身份查历史 | 最终 CANCELLED；断开不等于完成，可能需等下一次写/心跳感知；原 key 可核对，不重放 |

SSE 检查应使用 Apifox 的流事件视图/原始响应，核对实际 MIME 和事件顺序；幂等 JSON 必须切换到 JSON 分支检查 envelope/runId。所有事件的 version/sessionId/runId/messageId/requestId 应关联同一轮；done 与 error 互斥。用量未知为 null。

按本次创建的标识查询数据库，示例只包含占位符，先绑定真实参数再执行：

```sql
SELECT id, status, active_run_id, run_deadline_at
FROM chat_session WHERE merchant_id = ? AND id = ?;
SELECT id, run_id, sender_type, status, content, message_seq, error_code
FROM chat_message WHERE merchant_id = ? AND session_id = ? AND run_id = ?
ORDER BY message_seq;
```

每轮记录 HTTP/MIME、事件顺序、业务标识、预设正文比对、DB 终态和清理结果；不用整表计数或空表假设。归档测试会话并退出临时认证。真实模型只断言非空输出、完成状态和历史一致，实际结果见 [验收记录（本目录入口）](README.md#verification)。
