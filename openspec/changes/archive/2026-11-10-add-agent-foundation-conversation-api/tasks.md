# Tasks

本 change 交付 Agent 基础与对话接口；六项运行时子技术见 design.md 第 1 节。以下分组是开发与交付顺序，OpenAPI、Flyway、TDD、Apifox 和验收不另算运行时子技术。进度以各任务勾选为准；各实现项均按红→绿→重构推进，并同时交付测试和文档。

## 1. Requirements and API contract

- [x] 1.1 在 `api/docs/agent/requirements.md` 整理消费者/员工本人问答、消息历史和错误场景的需求与验收矩阵，标明四个亮点、完整前端和 Nginx 实际部署的后续边界；通过逐项映射两份 spec 场景验证没有混入 Redis ChatMemory、匿名入口或客服接管。
- [x] 1.2 在 `api/docs/agent/api-design.md` 冻结六个操作的路径、方法、字段、分页、ID/时间格式、Bearer 权限、HTTP/业务错误码和 POST 消息的 SSE/幂等 JSON 两种响应；用正常、跨租户、他人、busy、归档、幂等冲突示例检查契约完整性。
- [x] 1.3 在接口文档中定义 start/token/done/error 公共字段、顺序、终态、usage 缺失语义及后续 citation/tool_call 扩展，说明 fetch POST、CORS、断开后查历史且不重放；通过请求/响应样例确认前端无需持有模型 API key。

## 2. Flyway schema and persistence

- [x] 2.1 在 `api/docs/agent/data-design.md` 完成 chat_session/chat_message 字段、主外键、租户优先索引、USER 幂等唯一键、消息序号和共享运行权设计；按当前 V4 暂定下一版 V5，并写明模块汇合只协调未执行编号，通过与现有迁移及数据约定对照验证。
- [x] 2.2 先编写 MySQL/Flyway 失败用例，再新增 V5 结构迁移，覆盖从空库及 V4 升级、合法状态、复合租户外键和 clientMessageId 唯一性；以隔离 MySQL Testcontainer 验证通过，保持 V1–V4 校验和不变，不操作 dev 数据卷。
- [x] 2.3 先编写 Mapper 集成测试，再实现实体、MyBatis SQL 和稳定分页，查询/更新同时限定商户、会话及必要本人角色/账号；按稳定业务标识验证同租户他人和跨租户不能读写，消息序号排序正确，不使用整表总数证明用例结果。
- [x] 2.4 为自动测试在 `src/test/resources/db/fixture` 增加两租户、多本人会话及消息数据并显式用 `@Sql` 加载，为 dev 单独增加按现有编号顺延的最小演示数据；验证重复执行不覆盖人工修改、test/prod 不加载 devdata，数据使用和约束同步写入数据设计文档。

## 3. LangChain4j model integration and context

- [x] 3.1 按现有 controller/service/mapper/entity/dto/vo 约定建立 agent/chat 实际需要的类，集中锁定 LangChain4j 及 OpenAI 集成版本；以 `mvn -f api/pom.xml -DskipTests compile` 验证，不引入 WebFlux、Anthropic 协议依赖、各供应商专属适配器或空 ChatMemory 模块。
- [x] 3.2 先写配置校验测试，再实现 `merchant.agent` 的 enabled/base-url/api-key/model、Prompt 版本、输入/输出及超时上限；验证禁用时已有功能不要求模型凭证、启用时非法配置拒绝启动且日志不泄密，同步维护 dev/prod 配置示例和模型服务根路径说明。
- [x] 3.3 先用本地 HTTP stub 写 OpenAI 兼容协议契约，再接入 LangChain4j 流式模型；验证最终 `/chat/completions` 路径、Bearer、model、system/user/assistant、stream 和分片解析，SDK 负责协议转换，业务代码不手写供应商请求和流解析。
- [x] 3.4 建立小型模型调用边界和确定性替身，先用预设 delta/错误/延迟编写测试，再实现上下文组装；精确断言 system、当前输入、最近成功完整轮次、条数/预算限制和输出预留，失败轮次不注入，超限输入明确拒绝，不做摘要或 Redis ChatMemory。
- [x] 3.5 在 `api/docs/agent/model-integration.md` 记录应用消息映射、LangChain4j 协议转换和未来 RAG/FC/ChatMemory 的职责分工；用本地 stub 测试结果核对配置示例，不固定后续必须使用某种高层 AI Services 编排方式。

## 4. Conversation execution and authorization

- [x] 4.1 先写 Service 授权用例，再实现创建、本人列表/详情和归档；验证消费者和员工都只访问本商户本人会话，跨租户/他人/不存在统一 404，重复归档幂等，运行中归档返回 busy，规则同步到需求和接口文档。
- [x] 4.2 先写并发与幂等用例，再实现事务内会话运行权、序号分配、用户/助手占位落库及提交后调用模型；验证同 key 同正文返回原标识、同 key 不同正文 409、不同 key 运行中 409，幂等判断先于 busy，不持有长事务调用模型。
- [x] 4.3 先写终态竞争用例，再实现完成/取消/超时/失败条件更新、有限部分文本及 usage 保存、运行权释放；验证同一 run 只有一个终态、done 前已提交、迟到回调不能改写终态或后继 run，MySQL 写失败不能假报已完成。
- [x] 4.4 先写跨实例状态用例，再实现持久化 runId/deadline 与按需过期恢复；用两个独立执行上下文共用隔离数据库验证同会话只有一个有效 run、原实例消失后可超时恢复、不同会话互不阻塞，不依赖单机锁或 Nginx 粘性会话。
- [x] 4.5 先写瞬时网络错误、429/5xx、凭证错误和正文输出后故障用例，再实现受总 deadline 限制的有限重试与错误映射；验证正文输出后不从头生成、错误不自动切换供应商，并在运行说明中注明重试不保证上游单次计费。

## 5. HTTP and SseEmitter streaming

- [x] 5.1 先写 HTTP 契约用例，再实现会话和分页历史 Controller、严格 DTO 校验及独立输出 VO；验证统一 envelope、请求关联头、ID/时间格式、长度限制，以及客户端身份/system/role 覆盖字段在模型调用前被拒绝。
- [x] 5.2 先写流式契约用例，再使用 SseEmitter 和有界异步执行实现消息 POST；验证 start/token/done/error 顺序、公共标识、串行写入、幂等 JSON 与 SSE 的 Content-Type 区分，不伪造 citation/tool_call，也不实现 Last-Event-ID 重放。
- [x] 5.3 先写中止、超时、慢客户端与写失败用例，再实现断开监听、上游取消请求、注释心跳和输出/队列上限；验证迟到 token 被丢弃、运行终态可查、线程资源释放、心跳不续期认证，不新增独立跨实例取消接口。
- [x] 5.4 验证异步请求与既有安全过滤器集成，显式传递 Actor/requestId，增加显式来源的 CORS/预检配置及 fetch 示例；通过 MockMvc 或真实 HTTP 流测试验证预检、Bearer、JSON 错误和分段 SSE，禁止任意来源放行，不新增 WebFlux 测试运行时。
- [x] 5.5 增加必要的结构化运行日志和现有指标，记录 requestId/runId、商户/角色、服务标签、Prompt/模型、首 token/总耗时和终止原因；用日志用例验证密钥、token、正文、完整 Prompt 和原始手机号不会输出，同步维护运行说明，不另建监控平台。
- [x] 5.6 从实际 Springdoc 分组导出 `docs/api/agent-conversation-v1.openapi.yaml`，在 `api/docs/agent/manual-verification.md` 给出 fetch/curl、凭证选择、断开后历史查询和后续 Nginx 缓冲/超时检查；验证快照可解析且覆盖六操作、两种成功 MIME、鉴权、错误与事件样例。

## 6. Apifox integration

- [ ] 6.1 将实际 OpenAPI 导入 Apifox 对应模块，配置独立测试环境和本地凭证引用，保存导入与用例组织说明；核对六操作及 SSE/JSON 响应可用，不把真实 API key、登录 token 或开发密码写进导出契约。
- [ ] 6.2 使用可控模型完成 Apifox 协议验收，覆盖本人创建/发送/历史/归档、员工查他人拒绝、两租户隔离、幂等/busy、错误、超时和客户端中止；断言结构、事件顺序、预设文本与按业务标识定位的数据库状态，不以共享整表总数或空表为前提。
- [x] 6.3 使用本地配置的真实 DeepSeek、Qwen 或其他 OpenAI 兼容网关完成真实模型联调，验证协议连通、非空流式回答、终态和 MySQL 历史一致；记录实际服务/模型及结论，不精确匹配固定自然语言、不把基础聊天联通称为 RAG/Tool 效果验收，无法执行时如实保留待验收。

## 7. Integrated acceptance

- [x] 7.1 执行 `mvn -f api/pom.xml test` 和 `mvn -f api/pom.xml clean verify`，汇总单元、协议、MySQL/Flyway 和真实认证 HTTP/SSE 的实际报告；完整链路使用独立 MySQL/Redis Testcontainers，Redis 仅服务既有认证，Docker 不可用明确失败，不连接开发卷或静默跳过集成测试。
- [x] 7.2 将已完成的需求/接口/表结构/测试/Apifox 证据对应到 `api/docs/agent/verification.md`，检查按业务标识断言、租户本人隔离、断开恢复和两个执行上下文共享状态；明确完整前端、真实 Nginx 集群、RAG、FC、RabbitMQ、Redis ChatMemory 尚未交付，未通过项不得标记完成。
- [x] 7.3 同步 `api/README.md`、架构模块路线图、包结构与测试文档入口，复核后续模块可复用模型边界、完整历史和可信身份；验证链接/命令有效、未创建 ChatMemory 键或摘要机制、已执行迁移未改写，所有实现任务与验收矩阵一致后再提交模块验收。
