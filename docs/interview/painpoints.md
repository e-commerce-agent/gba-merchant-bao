# 技术问题与决策记录

认证更新：2026-11-06；Agent 追加：2026-11-10。本页记录认证与 Agent 实现中已有代码、测试或维护记录可追溯的问题；“设计约束”“隔离测试构造故障”“实际开发库维护”分别标明，不把它们包装成线上事故。认证面试只选 AUTH-01、AUTH-02；Agent 面试只选 AGENT-01、AGENT-02，其余保留为备查。

共同来源：认证 [实现 PR #12](https://github.com/e-commerce-agent/gba-merchant-bao/pull/12)、[验收](../../api/docs/auth/verification.md)；Agent [实现 PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15)、[验收](../../api/docs/agent/verification.md)。全部数字对应代码规则、具名测试或本机维护记录；没有线上性能指标。

| 编号 / 类型 | 问题或现象 | 原因与处理 | 证据 / commit | 面试主讲 |
| --- | --- | --- | --- | --- |
| AUTH-01 / 设计取舍 | 未登录消费者也必须先定位商户，同手机号跨商户独立 | 用店铺路径 storeSlug；本期复用 merchantCode，不增加独立别名表。公开 locator 不授予私有权限。对比 body、Header、域名与 JWT 的取舍见面试卡片 | AuthInput#storeSlug、TenantResolver#resolveActive、AuthFlowIT HTTP/资源范围；`c4802d2` / `68b660c` | 是 |
| AUTH-02 / 用例及失败边界 | 首次和再次登录要统一，又不能重复建号或产生“账号存在但假登录成功” | 一次消费验证码 → 事务建号/复用 → 提交 → Redis 建会话。独立挑战竞争用唯一约束；Redis 失败保留账号、503 且无 token；消费者均成功返回 200 | AuthFlowIT#independentChallengesRacingFirstLoginReuseOneCommittedAccount、#databaseFailureConsumesChallengeAndSessionFailureKeepsCommittedAccount；`68b660c` | 是 |
| AUTH-03 / 并发验证 | 同验证码可能同时提交；旧发送失败清理可能误删新挑战 | Lua 校验并消费；challengeId 区分代际，发送完成才激活，清理只针对本代。第五次错误失效 | AuthSmsRedisIT#concurrentConsumptionHasExactlyOneWinner、#pendingCannotBeUsedAndOldSendFailureCannotDeleteNewChallenge；`68b660c` | 否 |
| AUTH-04 / 重试设计 | refresh 响应丢失后直接重试，可能被误当重放；存明文响应又会暴露 token | 同旧摘要+同 UUID+默认 10 秒+仍为当前代时返回 AES-GCM 加密缓存结果，其余已知旧值重放撤销当前 sid；未知值不误撤销 | AuthFlowIT#rotationRetryIsEncryptedIdempotentAndReplayRevokesOnlyItsDevice、#oldGenerationWindowAndTamperedCacheNeverReturnCredentials；`68b660c` | 否 |
| AUTH-05 / 并发验证 | refresh 或在途业务 touch 可能与 logout 竞争 | 同 control 上原子检查撤销；touch 不创建已丢失 active，退出清重试缓存，保留撤销墓碑至 absolute | AuthFlowIT#concurrentRefreshAndLogoutNeverResurrects、#touchOnlyOnSuccessfulAllowedWorkAndCannotResurrectLogout；`68b660c` | 否 |
| AUTH-06 / 生命周期 | 只有一个 TTL 无法区分闲置、重新凭证登录和永久续命 | active 控制 idle，control/refresh 保留至原始 absolute；有效 refresh 可恢复 idle，absolute 不滑动，me 不 touch | AuthFlowIT#idleCanRecoverButAbsoluteAndLostControlOrIndexCannot；可控时钟而非等待 30 天；`68b660c` | 否 |
| AUTH-07 / 授权边界 | 客户端传商户/主体 ID 或全局查询资源可能串租户、泄露资源是否存在 | 私有 SQL 只取 Actor 商户，消费者另限定本人；未知 DTO 字段拒绝，跨租户/非本人/不存在统一 404，角色错 403 | AuthPersistenceIT、AuthFlowIT#httpContractStrictInputRolesResourcesAndDedicatedBranches；`c4802d2` / `68b660c` | 否 |
| AUTH-08 / 凭证与配置 | 旧 JWT 有弱默认、短 key 补长及新旧 token 混用风险 | 两把不同 Base64 key 解码至少 32 字节，显式 HS256，校验 claims 和环境；新旧协议隔离，旧会话重登 | AuthJwtConfigurationTest、AccessTokenServiceTest、LegacyJwtCompatibilityTest、JwtPackagingIT；`9db2a3e` | 否 |
| AUTH-09 / 密码输入 | BCrypt 的字节边界与字符数不同；未知账号直接跳过校验有成本差异 | 密码不 trim，8–64 字符且 UTF-8 至多 72 字节；未知账号 dummy BCrypt；用户名/手机号共享实际员工失败锁定 | AuthCredentialTest、AuthInputTest、AuthFlowIT#staffLoginSharesIdentifierLockAndAllCredentialFailuresLookTheSame；`68b660c` | 否 |
| AUTH-10 / 依赖故障验证 | 仅验 JWT 会在 Redis/MySQL 故障或账号停用后错误放行 | 保护请求检查会话和主体，依赖异常 503，停用拒绝；退出允许凭有效凭证撤销停用主体的当前会话 | AuthFlowIT#subjectDisableRejectsPrivateAndRefreshButAllowsLogout、#concurrentSameRefreshRequestReturnsOnePairAndDependencyFaultsUse503；`68b660c` | 否 |
| AUTH-11 / 日志与上下文 | 旧日志输出登录 DTO/微信 code；复用线程可能遗留身份 | 移除敏感日志，认证审计分流，finally/afterCompletion 清 SecurityContext/BaseContext；requestId 排在安全链前 | JsonLoggingTest、AuthContextTest、HttpClientTest；`68b660c` | 否 |
| AUTH-12 / Flyway 交付修正 | 租户/账号旧 R 描述顺序不能表达依赖，回调重跑与普通迁移混杂 | 种子改为 010 租户、020 账号，移除回调；R 仅首次或 checksum 变化执行，上游变更不自动重跑下游 | DevSeedMigrationIT 排序/重入/并发；`fe4657c` | 否 |
| AUTH-13 / 实际开发库维护 | 2026-11-06 预检发现实际库到 V3，旧 R 改名后普通 migrate 会受 Missing 历史阻断 | 用户确认精确 V3 分支，18:25–18:27 停同库应用、完整备份并隔离恢复，显式退役已知旧 R，再 V4→010→020→validate，保留原历史与数据 | [开发库记录](../../api/docs/deployment/dev-seed-upgrade.md)；升级后 2 租户/2 员工/3 消费者是开发数据数量；`fe4657c` | 否 |
| AUTH-14 / 数据安全 | 重跑种子可能覆盖人工密码/停用状态，INSERT IGNORE 可能掩盖身份冲突 | 只补缺失；稳定 ID/编码/手机号/归属冲突明确失败；test 用独立 fixture，prod 不装演示账号 | DevSeedMigrationIT、AuthDevManualIT；`fe4657c` | 否 |
| AUTH-15 / 构建与包迁移 | 移动包后旧 class/SQL 可残留，扫描/XML/Springdoc 引用可能失效 | 29 类迁移时同步所有引用，clean verify 清除生成残留，逐类核对方法体未变；不将只改目录等同于验收完成 | 44 单元/契约 + 62 IT 全过，[包结构](../../api/docs/architecture/packages.md)；`7f09f0b` | 否 |
| AUTH-16 / 测试边界 | 定向测试成功可能被误认为全量成功；固定开发端口会污染业务卷 | Docker 不可用失败，完整 clean verify 实际执行 Failsafe，动态端口/可销毁容器/报告仅 target | [测试说明](../../api/docs/testing/README.md)、最终验收 106 项；没有 Apifox/浏览器/性能测试结论；`7f09f0b` | 否 |
| AUTH-17 / 文档与版本策略 | 归档目录增加一级后相对链接失效；本地主规格被仓库忽略 | 修正归档 design 的相对链接；同步 14 条主规格并逐段对比。保持 openspec/.gitignore 的 `/specs/` 规则，提交归档 delta 作为可追溯来源 | 本次 docs/auth-interview；新 commit 和 mini PR 尚待作者创建 | 否 |
| AGENT-01 / 预算策略 | 长会话可能超过模型上下文；简单截断可能损坏当前问题或只留下半轮 | 先预留 maxOutputTokens，再扣 system/当前输入；以 UTF-8 字节数+每条消息4作近似，从最新完整成功轮次向前选，受条数与剩余预算双限；固定输入超限明确拒绝 | AgentContext#assemble；AgentContextTest#budgetReservesOutputAndNeverTruncatesCurrentInputOrUsesHalfARound；`abc38b2` / PR #15 | 是 |
| AGENT-02 / API 选型 | 高层编排可能把当前必须显式控制的消息/运行生命周期藏到框架抽象里 | 本期用 OpenAiStreamingChatModel 低层 streaming API 交给 SDK 处理协议，应用保留持久化、Actor、deadline、取消与终态控制。RAG/Function Calling 尚未实现，不预判后续必须或不能使用 AiServices | OpenAiAgentModel#stream、ChatService#accept/#execute/#finish；`abc38b2` / PR #15 | 是 |
| AGENT-03 / 并发设计 | 单机 synchronized 无法协调同一会话落到不同后端实例的请求 | 以 MySQL active_run_id 与 run_deadline_at 作为共享运行权，短事务内竞争，模型调用不持有事务；过期后按 runId 恢复 | ChatService#accept/#finish；ChatServiceIT 两个执行上下文共享隔离库；`abc38b2` / PR #15 | 否 |
| AGENT-04 / 幂等顺序 | 相同请求在运行中重试会被 busy 拒绝，若先检查状态则难以返回原运行 | 先按 clientMessageId 查记录并核对正文，再检查归档/busy；同 key 同文返回旧运行，同 key 改文 409，唯一索引兜底并发 | ChatService#accept、ChatAcceptanceIT；`abc38b2`、`b86f2ce` / PR #15 | 否 |
| AGENT-05 / 终态竞争 | 取消、超时、完成和迟到模型回调可能同时更新助手状态或误释放新 run | 终态事务校验 active_run_id、deadline 和 message/run 归属；只有条件更新成功才释放运行权，done 在提交后发送 | ChatService#finish、ChatRunExecution；ChatServiceIT、ChatAcceptanceIT；`abc38b2`、`b86f2ce` / PR #15 | 否 |
| AGENT-06 / 取消资源 | 首个模型 delta 到来前尚未取得 SDK StreamingHandle，只取消 handle 可能无法中止连接 | 每次调用独立持有可关闭 HTTP transport；取消同时触发可用的 streaming handle 与 transport.shutdownNow，并忽略迟到回调 | OpenAiAgentModel#stream、AgentModelProtocolTest、ChatAcceptanceIT；`abc38b2`、`b86f2ce` / PR #15 | 否 |
| AGENT-07 / 异步上下文 | Servlet 安全过滤器结束后线程上下文可能已清理，模型工作线程不能假设 ThreadLocal 仍有 Actor/requestId | 在接受请求时取得可信 Actor/requestId 并显式传入执行对象；使用有界执行器/队列及 admission 限制，不跨异步边界依赖过滤器线程状态 | ChatController#send、ChatService#execute、ChatRunExecution；ChatAcceptanceIT；`abc38b2`、`b86f2ce` / PR #15 | 否 |
| AGENT-08 / 协议兼容 | 兼容服务可能使用非 Bearer 鉴权头，base-url 也可能重复拼接终端路径 | 使用 SDK 的 api-key/customHeaders 配置，base-url 保持 API 根路径；本地 HTTP stub 验证最终 `/chat/completions`、鉴权头、消息角色和流式解析 | OpenAiAgentModel#stream、AgentModelProtocolTest；`abc38b2` / PR #15 | 否 |
| AGENT-09 / 交付验收 | 自动化测试和真实模型联调容易被误写成已完成 Apifox 客户端验收 | 保留 6.1/6.2 未勾选；截至 2026-11-10 总计27/29。已导入两个契约，但环境、凭据引用和协议用例实际执行未完成 | tasks.md、api/docs/agent/apifox-verification.md、api/docs/agent/verification.md；`b86f2ce` / PR #15 | 否 |
| AGENT-10 / 迁移汇合 | V5 已经在开发库执行，后续模块若也使用 V5，改写本迁移会破坏已记录的 Flyway 校验和 | 已执行 V5 保持不变；冲突时协调尚未执行的迁移编号并通过前向迁移演进，不用 repair 掩盖两份不同迁移 | `V5__agent_conversation.sql`、Flyway 开发库记录；`520862d` / PR #15 | 否 |

## 证据保存约定

每次追加问题时注明：问题类型、实际触发条件、发现工具/用例、修复或设计决策、验证结果、限制、commit 和 PR。代码中的 TTL/位数是参数，类中的测试数量是用例数量，Maven 总耗时是构建时间；均不能替换成并发数、接口耗时或线上成功率。Agent 的两个主讲点详见 [面试卡片](agent.md)；剩余条目是备查，不包装成线上事故。

原始测试 XML、脚本输出、开发备份按现有忽略规则保留，仓库只保存脱敏摘要与可运行步骤。没有原始观测依据时写“未测”，不补估计值；后续真实压测或生产问题另行追加，不回写成当前实现已经测得的收益。
