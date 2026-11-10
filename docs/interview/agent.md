# Agent 基础与对话接口面试卡片

更新：2026-11-10。实现已由 [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) 合入, 本次docs/agent-interview分支已归档. 验收记录见 [Agent 验收记录](../../api/docs/agent/verification.md)。

openspec的change add-agent-foundation-conversation-api, Apifox的6.1、6.2尚未完成实际配置与协议执行

## 一、业务梳理

消费者或商户员工登录后，需要创建自己的对话、继续发送消息，并在刷新页面或连接中断后读取历史。服务端必须确保会话只对所属商户、账号和角色开放；同一条消息不能因网络重试重复触发模型调用。

本模块提供本人会话创建、列表、详情、归档、历史分页和流式发送。消息与运行状态保存到 MySQL，模型通过 OpenAI Chat Completions 兼容协议接入，SSE 负责把增量回答发回客户端。当前交付基础对话，不包含知识检索、业务工具调用、完整前端或真实 Nginx 多实例部署。

可直接讲的开场：

> 我在已有认证模块上做了 Agent 基础运行时和本人会话接口。请求先用可信 Actor 校验会话归属，消息和运行权先提交到 MySQL，再由模型异步生成并通过 SSE 返回。这里我重点讲两个取舍：如何在不截断当前问题的前提下控制上下文预算，以及为什么当前用 LangChain4j 的低层流式 API，而没有把编排交给 AiServices。

## 二、技术实现

**痛点：** 长对话会让模型输入超出上下文限制；失败或未完成的回答不能作为正常历史继续注入。流式生成期间还会遇到重复请求、超时、断线和迟到回调，若只依赖进程内状态，可能重复生成或让会话永久 busy。

**主方案：** 使用 `AgentModel` 隔离模型适配，用 LangChain4j OpenAI Streaming Chat Model 处理兼容协议和上游 SSE 解析。应用从 MySQL 成功历史中选择上下文，由 Spring MVC `SseEmitter` 输出 start/token/done/error。MySQL 保存会话、消息、幂等标识、运行 deadline 和终态。

**递进：**

- 只把完整、成功的 USER/ASSISTANT 轮次放进上下文；失败、取消和超时消息仍保留在历史查询中。
- `clientMessageId` 先做幂等核对，再检查归档与 busy；同 key 同正文返回既有运行视图，同 key 改文返回 409。
- 用数据库持久化运行权与 runId/deadline 控制同会话串行和过期恢复；不持有事务等待模型响应。
- 完成状态事务提交后才发送 done；取消、超时、失败和完成只能竞争一个终态，迟到回调不能影响后继 run。
- 只在正文输出前对瞬时网络错误及 429/5xx 有限重试，沿用同一总 deadline；正文输出后不从头重试。
- 认证 Actor/requestId 显式传给异步流程；有界执行器、队列和 SSE 写入限制避免无限占用资源。

**数据流：** Bearer → 认证过滤器得到可信 Actor → `ChatController` 校验请求 → `ChatService#accept` 校验归属/幂等并在事务内取得运行权、保存用户消息和助手占位 → `AgentContext#assemble` 选取成功历史 → 有界执行器调用 `AgentAttempts` 与 `AgentModel` → `ChatRunExecution` 转发增量并处理终态 → MySQL 提交终态、释放运行权 → `SseEmitter` 发送 done/error。客户端断开后通过本人历史接口查询，不承诺 SSE 重放或跨实例恢复生成。

**核心代码位点：**

| 类#方法 | 职责 | 功能提交 / PR |
| --- | --- | --- |
| `AgentContext#assemble` | 估算可用输入预算，筛选最近完整成功轮次并映射为 SDK 消息 | `abc38b2` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `AgentModel#stream`、`OpenAiAgentModel#stream` | 统一增量/完成/错误/取消契约；SDK 负责 Chat Completions 编码和上游流解析 | `abc38b2` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `AgentAttempts#stream` | 仅对正文前可重试故障做有界重试，共用运行 deadline | `abc38b2` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `ChatService#accept` | 本人会话校验、幂等、运行权获取、消息占位和上下文准备 | `abc38b2` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `ChatService#execute`、`#finish` | 有界异步执行，按 runId/deadline 条件提交唯一终态并释放运行权 | `abc38b2` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `ChatRunExecution#delta`、`#complete`、`#error`、`#cancel` | 协调模型回调、持久化结果、SSE 转发和取消 | `abc38b2` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `ChatController#send` | 接收消息并区分新运行 SSE 与幂等重发 JSON | `abc38b2` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `ChatSessionMapper`、`ChatMessageMapper`；`V5__agent_conversation.sql` | 会话/消息持久化、租户归属、序号和幂等约束 | `520862d` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |
| `ChatAcceptanceIT` | 真实认证链路、HTTP/SSE、隔离和终态集成验收 | `b86f2ce` / [PR #15](https://github.com/e-commerce-agent/gba-merchant-bao/pull/15) |

## 三、技术深挖：两个主讲难点

### 难点一：上下文预算怎么计算和选取？

模型上下文长度同时要容纳 system、当前问题、历史和本次输出。若把完整历史全部发送，长会话会超限；若简单截断字符串，又可能丢失当前问题或留下半轮对话。

`AgentContext#assemble` 的流程是：

1. 从 `contextTokens` 中先减去 `maxOutputTokens`，为模型回答预留空间。
2. 再扣除服务端 system Prompt 与当前输入的估算成本。当前实现按 UTF-8 字节数加每条消息 4 个角色开销作保守近似，不将它当作精确 tokenizer 或计费用量。
3. system 与当前输入若已超过预算，返回 `AGENT_CONTEXT_TOO_LARGE`；不静默截断用户输入。
4. 将历史按 `messageSeq` 排序，从最新消息往前检查相邻 USER/ASSISTANT 对。只有两条都为 COMPLETED、序号相邻且 `runId` 相同的完整轮次才可选入。
5. 在 `historyMessages` 消息条数上限和剩余预算内逐轮向前取；预算或条数不足时停止，最后恢复为时间正序。当前输入只放入一次。

失败、取消、超时的助手消息仍能从 MySQL 历史查看，但不进入后续上下文。MySQL 保存完整已提交历史；本期没有 Redis ChatMemory 或摘要。

面试话术：

> 我把上下文选择和历史持久化分开。完整历史留在 MySQL；一次模型调用先预留最大输出额度，再扣掉 system 和当前输入，剩余部分从最近的成功完整轮次向前取，同时受消息数上限约束。输入本身超限就明确拒绝，不截断问题。由于 OpenAI 兼容服务不保证 tokenizer 一致，我当前用 UTF-8 字节数加角色开销作近似，并明确它不是精确 token 数或计费数字。失败轮次不会污染下一轮上下文。

证据：`AgentContextTest#onlyRecentCompleteSuccessfulPairsAreInjectedInChronologicalOrder`、`#budgetReservesOutputAndNeverTruncatesCurrentInputOrUsesHalfARound`。测试精确检查选择结果与边界，没有跨模型 token 精度或成本结论。

### 难点二：为什么使用 LangChain4j 低层 API，而不是高层 AiServices？

当前通过 `OpenAiStreamingChatModel` 的低层 streaming 调用，把消息列表交给 `StreamingChatResponseHandler`，再映射成 `AgentModel.Listener`。LangChain4j 负责 OpenAI 兼容请求序列化、上游 SSE 解析及流式回调；应用保留上下文选择、运行生命周期、事务、取消、错误映射和对外 SSE 的控制。

当前主要验收约束在运行生命周期：消息要先落库，运行权要跨实例共享，模型调用要受 deadline 控制，完成前要先提交终态，客户端断开要尽可能取消上游，迟到回调不得覆盖后继运行。低层 API 让这些边界仍由 `ChatService`、`ChatRunExecution` 和 `AgentModel` 显式管理。当前没有 LangChain4j Tool 编排或 ChatMemory 能力要交给框架，因此尚无必须采用 AiServices 的需求。

这不是说 AiServices 不适用，也不是对后续 RAG/Function Calling 的最终选型。RAG 可以先作为上下文准备步骤评估；Function Calling 到来时，应按实际 API 能力和生命周期测试比较 AiServices 与低层工具调用，并继续满足可信 Actor 注入、参数授权、消息持久化、取消和错误状态要求。

面试话术：

> 现在选低层流式 API，是因为这期的难点不是声明一个聊天接口，而是把 MySQL 运行权、deadline、SSE、取消和唯一终态串成可验证的生命周期。SDK 专注兼容协议与分片解析，应用自己控制持久化和授权。当前也没有 Tool 或框架内存需要高层接口代管，所以不为抽象而引入 AiServices。这个选择只针对当前基础对话，不预判后续一定用或不用 AiServices；接入 Function Calling 后会基于实际工具执行和状态语义重新评估。

证据：`OpenAiAgentModel#stream`、`ChatService#accept/#execute/#finish`、`ChatRunExecution`；`AgentModelProtocolTest` 使用本地 HTTP stub 验证协议，`ChatAcceptanceIT` 验证真实认证链路和 HTTP/SSE。当前没有 Agent 工具调用或 RAG 验收。

其余实现问题收录在 [painpoints.md](painpoints.md)，不增加第三个主讲难点。

## 四、高频八股

题目来源补充自仓库已有 [牛客面经摘录](../design/03牛客面经-1.txt) 中的上下文 token 预算、LangChain4j Tool、SSE 与 Agent Memory 相关问题；以下项目回答只陈述本次已实现能力，不代表市场频次统计。

| 追问 | 项目回答 |
| --- | --- |
| Token 预算与真实 token 数有什么区别？ | 当前 UTF-8 字节数加角色开销是预算近似；不等于所选模型 tokenizer 的精确结果，也不作为账单用量。供应商返回 usage 时另行保存。 |
| 为什么不在调用模型时持有数据库事务？ | 模型响应可能很慢。先在短事务内取得运行权并保存占位，提交后调用模型；终态再开事务条件更新并释放运行权。 |
| 幂等如何避免重复调用模型？ | 按会话和 `clientMessageId` 检查已有用户消息及正文；相同内容返回旧运行，不同内容 409。该核对先于 busy 检查。数据库唯一约束作为并发兜底。 |
| 多实例如何保证同一会话只有一个 run？ | 以 MySQL 会话行和 `active_run_id`/deadline 作为共享运行权，不依赖 JVM `synchronized`。集成测试用两个独立执行上下文共用数据库验证，不声称完成真实双进程压测。 |
| 断线后如何取消与恢复？ | 断开监听请求取消 SDK handle 和该次 transport；最终状态按 runId 条件提交。客户端通过历史查询恢复已提交状态，不重放 token，也不自动重新生成旧 run。 |
| SSE 的 done 什么时候发送？ | 只有助手终态和运行权释放事务提交后才发送 done；存储失败不能报告成功。写不回客户端时以 MySQL 为准。 |
| 重试是否 exactly-once？ | 不是。仅正文输出前对特定瞬时故障做有限重试，供应商可能已接收或计费；正文输出后不重放生成。 |
| RAG / Function Calling 会不会改用 AiServices？ | 当前没有做最终决定。需要比较上下文/工具控制、Actor 授权、持久化、取消和错误终态如何与现有运行边界协作。 |

## API、数据与权限

- 六个本人会话操作：创建、列表、详情、归档、历史分页和发送；发送为 `POST /api/v1/chat/sessions/{sessionId}/messages`。
- 新消息使用 SSE；相同幂等请求返回 JSON 原运行视图。事件为 start/token/done/error，不支持 Last-Event-ID 重放。
- `V5__agent_conversation.sql` 新增 `chat_session`、`chat_message`，保存商户/本人归属、消息序号、幂等键、运行状态与 deadline。V5 已在本机 dev 执行，已执行迁移不能改名或改写。
- 对话接口复用认证 Actor；跨商户、同租户他人或不存在的会话统一 404，未登录 401。无新增 Redis ChatMemory、MQ 或 Milvus。
- 降级时可关闭 `merchant.agent.enabled`，保留已有历史；结构回退走前向迁移，不删除已执行版本。

## 测试条件与真实数字

实现阶段记录的环境为JDK 21、Maven 3.9.11、Docker Testcontainers；MySQL 8.0.34、Redis 7.4-alpine。Redis仅用于既有认证会话。详细证据和限制见 [验收记录](../../api/docs/agent/verification.md) 与 [测试说明](../../api/docs/testing/README.md)。

| 验证 | 实际结果 |
| --- | --- |
| `mvn -f api/pom.xml test` | 68 项，失败/错误/跳过均为 0 |
| `mvn -f api/pom.xml clean verify` | 68 单元/契约 + 86 集成测试；失败/错误/跳过均为 0；09:57，BUILD SUCCESS |
| 真实认证 `ChatAcceptanceIT` | 4 项通过，使用隔离 MySQL/Redis、真实认证链和 Servlet/SSE；模型为可控替身 |
| 真实兼容模型（dots / `dots3-note-prev`） | 1 次真实闭环；SSE start/token/done，回答非空 8 字符，历史与 MySQL 正文/终态一致；first token 57,342 ms、total 57,753 ms |
| Flyway | 开发库 V1–V5 success 均为 1；V5 checksum `-2063483985` |
| OpenSpec / Apifox | 27/29；用户已导入 Agent 和认证契约，Apifox 环境/凭据与六操作协议验收仍未完成 |

1.真实模型的单次耗时是一次联调观测，非压测。没有QPS、P95/P99、成本节省、线上准确率或生产部署数据。集成测试共用数据库的两个执行上下文不等于真实多进程集群
2.还没有联调前端、Nginx 实测、还没做RAG和Function Calling
