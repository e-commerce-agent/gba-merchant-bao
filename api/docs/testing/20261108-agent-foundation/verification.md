# Agent 基础与对话接口验收记录

日期：2026-11-08；change：`add-agent-foundation-conversation-api`。本记录区分自动化证据、真实模型/dev 维护和未完成的 Apifox 验收，当前不宣称整个模块验收通过。

## 运行结果

| 验证 | 实际结果 | 原始证据（本机忽略文件） |
| --- | --- | --- |
| `mvn -f api/pom.xml test` | 68 项，失败/错误/跳过均 0，21:29:03 BUILD SUCCESS | `.runtime/agent-final-test.log`；Surefire XML |
| `mvn -f api/pom.xml '-Dtest=ChatAcceptanceIT' test` | 4 项，失败/错误/跳过均 0，21:37:59 BUILD SUCCESS | `.runtime/agent-acceptance-test.log` |
| 首轮 `mvn -f api/pom.xml clean verify` | 68 单元通过；86 集成中 2 项失败、错误/跳过 0。FoundationApplicationIT 与 ProductionInfrastructureIT 仍预期 V4，实际为 V5；三处版本预期已更新 | `.runtime/agent-clean-verify-before-v5-expectation-fix.log` |
| 修正后 `mvn -f api/pom.xml clean verify` | 68 项单元/契约 + 86 项集成，失败/错误/跳过均 0，23:01:05 BUILD SUCCESS（耗时 09:57） | `.runtime/agent-final-clean-verify.log`；最终 Surefire/Failsafe XML |
| 可选 Apifox 隔离服务启动/停止 | 本机 fixture 可读取、真实登录临时凭证就绪；主动停止后 1 项成功，23:00:22。只证明服务可用，不算 Apifox 协议通过 | `.runtime/agent-apifox-workspace.log` |

测试报告位置为 `api/target/surefire-reports` 和 `api/target/failsafe-reports`；XML 汇总为 68/86，均无失败/错误/跳过。可选 AgentApifoxWorkspace 不计入默认 68 项，定向测试与全量测试有重叠，不能将重复执行次数累加为不同用例数。前次重跑被用户中断，没有最终报告，已另存 `.runtime/agent-clean-verify-interrupted.log`；本次使用完整重跑的最终结果。target 与 .runtime 不作为永久仓库附件，本文保留脱敏结果，复现命令见 [测试说明（本目录入口）](README.md#testing)。

## 需求、接口和持久化证据

RT/CV 编号对应以下证据；矩阵入口见 [本目录 README](README.md#requirements)。测试文件均位于 `api/src/test/java/com/gba/merchantbao/test`；这些是自动化覆盖，不能代替尚未执行的 Apifox 或浏览器实测。

| 需求 | 证据和主要断言 |
| --- | --- |
| RT-01 | ChatControllerTest 拒绝客户端身份/system/role/模型覆盖；ChatAcceptanceIT 真实认证链路中身份字段 400、模型零调用 |
| RT-02 | ChatServiceIT 终态竞争、取消、首 token/总超时；ChatAcceptanceIT 真实 HTTP 查询 COMPLETED/FAILED/TIMED_OUT/CANCELLED |
| RT-03 | AgentConfigurationTest 启用配置校验、禁用不要求 key、不回显秘密 |
| RT-04 | ChatRunExecutionTest 运行元数据日志/指标；ChatServiceIT/ChatPersistenceIT 保存模型/Prompt 归因 |
| RT-05 | AgentModelProtocolTest 本地协议服务校验路径、鉴权头、roles/model/stream；真实 dots 网关联调通过 |
| RT-06 | AgentContextTest 预算、输出预留、最近成功完整轮次；ChatPersistenceIT 完整历史分页 |
| RT-07 | AgentContextTest 跳过失败与不完整轮次，历史仍由 Mapper 保留 |
| RT-08 | ChatAcceptanceIT 精确 start/token/token/done 和 hello world；ChatServiceIT 终态提交后通知；ChatHttpIT usage=null |
| RT-09 | ChatAcceptanceIT partial 后故障为安全 error/FAILED，不泄露原始异常 |
| RT-10 | ChatAcceptanceIT 真实 HTTP 输入流关闭后取消，上游句柄被取消；迟到回调不改终态 |
| RT-11 | ChatServiceIT 首 token/总 deadline 落库与释放；ChatAcceptanceIT TIMEOUT/历史一致 |
| RT-12 | AgentAttemptsTest 正文前瞬时错误有限重试，共用原 deadline，不自动切供应商 |
| RT-13 | AgentAttemptsTest 正文后不重试；ChatAcceptanceIT 保存部分失败正文 |
| RT-14 | ChatAcceptanceIT 缺失/非法/退出 JWT 401，有效越权身份 404，调用模型前拒绝 |
| CV-01 | ChatControllerTest 创建 201、Location、字符串 ID/UTC 时间；ChatAcceptanceIT 消费者真实登录后创建 |
| CV-02 | ChatServiceIT 同商户不同账号/角色隔离；ChatAcceptanceIT 员工查消费者及同租户另一消费者被拒绝 |
| CV-03 | ChatAcceptanceIT 另一租户的详情/历史/归档/发送全部 404 |
| CV-04 | ChatServiceIT 幂等先于 busy；ChatAcceptanceIT 重发返回 JSON 原 run、模型只调用一次 |
| CV-05 | ChatServiceIT/ChatAcceptanceIT 同 key 改文冲突、不产生新轮次 |
| CV-06 | ChatServiceIT 原失败消息可查、后续新 run 不受迟到回调影响 |
| CV-07 | ChatAcceptanceIT 新消息 SSE，与按 run 定位的 MySQL 终态/正文一致 |
| CV-08 | ChatControllerTest 严格类型、长度、ID/MIME 与安全 JSON 错误；ChatAcceptanceIT 实际鉴权 |
| CV-09 | ChatHttpIT 实际 Servlet POST Bearer/CORS 与两种 MIME；fetch 示例入口见 [本目录 README](README.md#manual-verification)。完整浏览器 UI 尚未实测 |
| CV-10 | ChatServiceIT 的另一个 ChatService 执行上下文读取/恢复同库历史；没有部署第二个进程或 Nginx |
| CV-11 | ChatPersistenceIT 稳定会话/消息游标分页；ChatControllerTest/ChatHttpIT 非法分页被拒绝 |
| CV-12 | ChatRunExecutionTest 日志不含 key/token/正文/Prompt；HTTP/VO 白名单和上游安全错误映射 |
| CV-13 | ChatServiceIT 首次 archivedAt 不变；ChatAcceptanceIT 两次归档响应一致 |
| CV-14 | ChatAcceptanceIT 归档后新消息 409；ChatServiceIT 无新模型调用 |
| CV-15 | ChatServiceIT 两个并发请求争用同库运行权，仅一个成功；另一 busy，序号不交错 |
| CV-16 | ChatServiceIT 一会话 busy 时另一会话仍可接收独立 run；按会话范围更新终态 |
| CV-17 | ChatServiceIT 并发数据库运行权及另一执行上下文的共享状态测试；无真实多进程竞争压测 |
| CV-18 | ChatServiceIT 另一执行上下文以过期时钟终结旧 run，接收后继 run，旧回调不能释放新权 |

接口基线见 [本目录 README 中的接口契约入口](README.md#api-design)，实际 Springdoc 六操作与 SSE/JSON 由 ChatHttpIT 导出并解析 [快照](../../../../docs/api/agent-conversation-v1.openapi.yaml)。表结构、复合租户外键、USER 幂等键与 messageSeq 唯一性见 [本目录 README 中的数据设计入口](README.md#data-design)、ChatSchemaIT；空库/V4 升级保持 V1–V4 校验和。ChatPersistenceIT 显式 @Sql 加载多租户、多本人 fixture，事务回滚后按 fixture 主键核对。

## 真实认证与隔离边界

本次新增 [ChatAcceptanceIT](../../../src/test/java/com/gba/merchantbao/test/ChatAcceptanceIT.java)，使用独立 MySQL 8.0.34、Redis 7.4-alpine、随机宿主机端口，关闭复用、不挂业务卷、不访问 gba-dev。@Sql 显式加载 auth_accounts.sql；外部短信发送器与模型使用可控替身，真实账号 Service、JWT、Redis 登录会话、安全过滤器、Servlet 异步 SSE、事务/Mapper 均参与运行。DockerRequired 使 Docker 不可用时明确失败。

普通回答精确拼接 hello world，检查事件顺序和幂等 JSON；超时为 TIMED_OUT，部分输出后依赖故障为 FAILED；关闭 HTTP 流触发上游取消与 CANCELLED，迟到 token/complete 无效。按 sessionId/runId 定位助手行并核对正文/终态，按会话 ID 验证 active_run_id 已释放。退出后再查历史为 401，证明断流恢复仍需要有效身份。没有整表总数或空库业务数据假设。

ChatHttpIT 的 SessionService 使用替身，只证明协议/CORS，不作为真实认证的证据。ChatServiceIT 的第二个 ChatService 复用隔离数据库、事务管理器和 Mapper，配合独立时钟验证共享运行状态与恢复；这不等同于真实双进程或 Nginx 集群。

