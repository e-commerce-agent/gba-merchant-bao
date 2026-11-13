# user-auth Specification

## Purpose

定义消费者首次短信登录建号、商户员工开通和登录、双 token 生命周期、退出及三层授权的行为。实现与验收证据见 `api/docs/auth/verification.md`，任务状态以本 change 的 `tasks.md` 为准；不表示已发布生产。

## Requirements

### Requirement: Consumers shall automatically obtain a merchant-scoped account on first SMS login

系统 MUST 在 ACTIVE 店铺下用手机号和有效 LOGIN 短信验证码完成单一登录流程，首次验证成功自动建号，已有账号复用；MUST NOT 提供独立消费者注册入口、REGISTER 挑战、首次建号提示或协议同意门槛。账号 MUST 在商户内唯一，同手机号在不同商户的账号、会话与数据独立。消费者 MUST NOT 手输商户编码或指定内部租户、角色和主体 ID。

#### Scenario: First and subsequent SMS login
- **WHEN** 消费者从 /store/{storeSlug} 首页提交手机号和有效验证码
- **THEN** 服务端解析有效商户，创建或复用该商户消费者，成功均返回 200 双 token 登录结果，不要求先注册

#### Scenario: Concurrent creation resolves to one account
- **WHEN** 同一商户规范化手机号的多个独立有效挑战通过，建号发生唯一冲突
- **THEN** 数据库最多保留一个账号，各请求可复用该账号并重新核验状态，不返回重复注册 409；同一已消费挑战重放仍为验证码无效

#### Scenario: Disabled account cannot be recreated
- **WHEN** 有效短信验证对应的租户内账号已 DISABLED
- **THEN** 返回统一 401/11001，不重新创建账号绕过停用，不建立会话

#### Scenario: Account commits before Redis session creation fails
- **WHEN** 首次账号创建已提交但 Redis 会话写入失败
- **THEN** 返回 503 且不含任一 token，保留已提交账号且不恢复验证码；重新取码可登录该账号

### Requirement: Public store context shall locate tenants without granting authority

匿名消费者短信和登录 MUST 使用 /api/v1/stores/{storeSlug}/auth 路径，店铺首页自动携带公开 locator，初期可复用 merchantCode；无上下文 MUST 先进入店铺。公开资源按 URL 定位，私有通用资源 MUST 仅按可信 token/Redis Actor 限定租户，不要求自定义店铺请求头，不采纳客户端商户覆盖。refresh MUST 使用统一 /api/v1/auth/refresh，商户和类型从 opaque refresh 摘要对应的可信记录解析，不重复传 storeSlug/merchantId/role。

#### Scenario: Consumer opens store B while signed into store A
- **WHEN** A 已登录，消费者打开 B 的公开主页或请求 B 的私有功能
- **THEN** B 公开页面可匿名浏览；前端只选 B 的凭证，B 无会话则触发该店登录，保留 A 的凭证与会话，回 A 可继续使用或按需刷新

#### Scenario: A generic private endpoint receives an A credential
- **WHEN** 前端向通用私有接口误发 A token
- **THEN** 后端按 A Actor 限定数据，不能推断页面正在 B；前端必须按店铺选凭证并核对 principal，不宣称该请求会自动返回 B 专属 401

#### Scenario: Explicit protected store URL conflicts with the principal
- **WHEN** 一个有效身份访问显式保护的店铺 URL，解析租户与该身份不符
- **THEN** 返回 403；具体资源跨租户或非本人查询仍与不存在统一 404，无有效凭证才返回 401

### Requirement: Merchant staff shall be provisioned inside the merchant and use scoped passwords

系统 MUST 由 devdata 演示或独立 test fixture 提供首员工，后续员工由已登录的本商户 MERCHANT_STAFF 开通，固定相同角色，不替同事登录。员工 MUST 使用 merchantCode + 用户名/手机号 + BCrypt 密码登录；用户名、手机号各自在租户内唯一。首期合并管理员/客服共享开通能力，不建设 RBAC，不提供匿名员工注册、生产首员工命令/HTTP bootstrap 或生产账号种子。

#### Scenario: Staff provisions a colleague
- **WHEN** 有效员工提交用户名、手机号、显示名和初始密码
- **THEN** 服务端取当前商户/创建者，保存 BCrypt cost=12 摘要，返回 201、Location 与脱敏摘要，不返回密码/hash/token；同事可独立密码登录

#### Scenario: Unauthorized creation and identity injection
- **WHEN** 匿名或消费者开通员工，或者有效员工提交 merchantId/role/accountId 等未声明字段
- **THEN** 分别返回 401、403、400，不创建跨租户或平台账号

#### Scenario: Staff credentials are tenant scoped
- **WHEN** 同一员工用用户名或手机号登录，或相同标识存在于不同商户
- **THEN** 两种标识定位同一租户内员工，不使用其他商户密码验证；未知商户/账号、错密码和停用均为相同 401/11001，平台管理员没有登录入口

### Requirement: SMS challenges shall be single-use scoped and rate-limited

LOGIN 短信挑战 MUST 绑定商户和规范化手机号，六位安全随机、300 秒、摘要存储、一次原子消费、最多五次错误。重发 MUST 使旧挑战失效，发送/消费/失败清理 MUST 按 challengeId 区分代际；发送、手机号/IP 和密码失败配额 MUST 由共享存储原子维护 TTL。用户名/手机号 MUST 共享员工账号失败计数，未知账号执行 dummy BCrypt，不信任任意 X-Forwarded-For。

#### Scenario: A challenge is used concurrently or outside its scope
- **WHEN** 同挑战并发使用、跨商户/手机号使用，或已过期/消费
- **THEN** 同挑战最多一次通过，其余统一 400/11002，不产生重复验证成功

#### Scenario: Attempts exceed the configured limit
- **WHEN** 60 秒内重发、超过手机号/IP 配额，或验证码累计五次错误
- **THEN** 配额超限返回 429/11003 和 Retry-After，五次错误使挑战失效；并发不绕过计数或留下永久 key

### Requirement: Simulated SMS shall remain a non-production capability

系统 MUST 通过可替换发送边界模拟短信，不调用真实 SMS。仅 dev/test 可 mock；dev 显式开启才回显 debugCode，日志 MUST NOT 记录验证码。prod 模拟/回显 MUST 启动失败；没有真实适配器时 provider=disabled 并返回 503，不伪报已发送。

#### Scenario: Developer obtains a test code
- **WHEN** dev 显式开启 mock/debugCode 并申请短信
- **THEN** 响应可用于首次或已有消费者登录，常规与审计日志无验证码、原始手机号或 token；发送失败按 generation 清理而不误删下一挑战

#### Scenario: Production SMS remains disabled
- **WHEN** prod 未接入真实短信，或被错误配置为 mock/debug
- **THEN** disabled 请求返回 503/11502；错误 mock/debug 配置拒绝启动

### Requirement: Access sessions shall have idle recovery within an immutable absolute lifetime

两类登录 MUST 返回 HS256 access JWT 和高熵 opaque refresh。access 标称 7200 秒且 exp 不得超过原 absolute；消费者 idle=7天、absolute=30天，员工 idle=1天、absolute=7天。activeExpiresAt MUST 为 min(最后合格活动+idle, original absolute)。idle 到期 MUST 只失去活跃访问记录，refresh/control 保留至 absolute；绝对期限 MUST NOT 随刷新滑动。每次凭证登录独立 sid；refresh 沿用 sid，access jti 可随签发变化。

#### Scenario: Access is validated
- **WHEN** 请求受保护接口
- **THEN** 校验 HS256、issuer/audience、exp、sub/merchantId/role/sid、匹配活跃记录以及当前账号/商户 ACTIVE；任一无效 401，不能凭旧协议或未验签 role 建立身份

#### Scenario: Idle expires before the absolute limit
- **WHEN** 用户闲置超过消费者 7 天或员工 1 天后回来，但 refresh 仍有效
- **THEN** 原 access 因活跃记录缺失返回 401，按需 refresh 可恢复同 sid 活跃记录并轮换双 token，不要求短信/密码重登

#### Scenario: Absolute limit expires
- **WHEN** 距最初凭证登录超过消费者 30 天或员工 7 天
- **THEN** access/refresh 均不能继续该会话，必须重新短信或密码验证，刷新不能延长 absolute

#### Scenario: Activity touches only the idle deadline
- **WHEN** 成功完成允许续期的私有业务操作或前台所需刷新
- **THEN** 可推进 idle 但不得超过 absolute；匿名浏览、失败、me 探测、后台轮询/定时保活不得续命，前端避免无条件刷新，不能将客户端活跃标记当作可信授权依据

### Requirement: Refresh rotation shall tolerate bounded retries and revoke actual replay

统一 POST /api/v1/auth/refresh MUST 从 JSON refreshToken 验证当前 sid 与账号/商户状态，不依赖 access，不接受客户端租户/角色。系统 MUST 原子轮换摘要并保留历史摘要至 absolute，前端同店同类型跨 tab 协调。短时幂等 MUST 限同一旧摘要、refreshRequestId、10 秒窗口且下一代仍为当前；重试不得再次滑动期限。普通 Redis 记录 MUST 不含明文 refresh；短时同次响应缓存 MUST 使用域分离密钥的认证加密且有严格 TTL，不能退化为明文 token 缓存。

#### Scenario: First rotation response is lost
- **WHEN** 客户端在窗口内用同一旧 refresh 和同一 refreshRequestId 重试，且该轮换仍为当前
- **THEN** 返回同一次双 token 结果，不产生第二次轮换或推进期限；返回前再次确认未撤销及绝对期限

#### Scenario: A used refresh is actually replayed
- **WHEN** 已知旧 refresh 在接受的重试条件外重用
- **THEN** 原子撤销其 sid/family 的 active 与 refresh 能力并返回 401，其他设备/店铺不受影响；未知随机凭证只 401，不撤销任何可猜会话

#### Scenario: Active data is missing but control metadata remains
- **WHEN** refresh 当前有效且账号/商户 ACTIVE，active 因 idle 消失
- **THEN** 可恢复 active；control/refresh 数据全丢失或 REVOKED 时必须 401，不能用 JWT 重建

### Requirement: Logout shall atomically revoke the current session using available credentials

统一 POST /api/v1/auth/logout MUST 支持有效未过期 access，或 access 缺失/过期时 JSON body.refreshToken 定位当前会话，凭证不得放 URL。可用 access 与已知 refresh 同时提交 MUST 指向同一 sid；若 access 无效或过期，可由有效 refresh 独立定位，不得让失效 access 阻断退出。退出 MUST 原子撤销 active 和整条 refresh 能力、清除重试结果并保留非明文墓碑/已知摘要至 original absolute。同会话已撤销重复退出 MUST 幂等 200；未知/伪造/全部绝对过期凭证 MUST 401，无法确认撤销 MUST 503。

#### Scenario: Access expires before logout
- **WHEN** access 已过期但 refresh 对应当前可验证会话
- **THEN** 退出分支不被 access 过期拦截，使用 refresh 撤销且不签发新 token；有效历史 refresh 摘要可定位同一会话完成退出/重试

#### Scenario: Logout is repeated or races with refresh
- **WHEN** 同会话重复退出，或退出与刷新竞争
- **THEN** 已撤销同会话幂等成功；撤销后刷新/重试不得重建 active 或返回缓存 token，前端清除对应凭证、广播退出且丢弃迟到刷新结果

#### Scenario: Other sessions survive
- **WHEN** 退出当前店铺、当前账号类型、当前 sid
- **THEN** 其他店铺/类型/独立设备会话仍有效，已退出后发起的私有请求失败，已授权在途业务不承诺取消

### Requirement: Browser integration shall preserve per-store credentials across tabs and restarts

当前 dev/test 与短期生产集成约定 MUST 将 access+refresh 放 localStorage，按店铺和账号类型分区，切店不清其他键，同店跨 tab 协调轮换/退出并拒绝迟到响应覆盖。文档 MUST 明示 XSS 可窃取长期凭证且键分区不是安全隔离；生产目标另开 change 迁移 access 内存与 refresh HttpOnly Secure Cookie，并补 CSRF/CORS。本 change MUST NOT 宣称已实现前端或 Cookie 迁移。

#### Scenario: Browser restarts inside the absolute lifetime
- **WHEN** 用户关闭后隔天打开原店铺，凭证未撤销且未超过 absolute
- **THEN** 可按需刷新恢复，无须因 access 或 idle 到期立即重登；A/B 多 tab 凭证互不覆盖，同店结果同步

### Requirement: Authorization shall enforce tenant role and resource ownership

系统 MUST 只接受 MERCHANT_STAFF/CONSUMER，按可信 Actor 先限定租户、再判断角色、再校验资源归属，所有 SQL 带服务端租户和必要本人条件。具体资源跨租户/非本人/不存在 MUST 同为 404，角色越权 403；通用同类资源路由可共用，不强制拆消费者/员工路径。未来 Tool MUST 注入服务端身份，不采纳模型的 merchantId/customerId；当前只实现账号资源，不提前实现订单/Tool。

#### Scenario: Account resources enforce scope
- **WHEN** 员工查询同商户员工，或消费者查询自己账号
- **THEN** 返回脱敏摘要；员工跨商户和消费者查他人均与不存在同为 404，角色不符 403，不先全局查详情泄露存在性

### Requirement: Authentication dependencies shall fail closed with stable privacy-safe contracts

新 API MUST 用 code/message/data/requestId、匹配 X-Request-Id、认证响应 no-store 与稳定 400/401/403/404/409/429/503。MySQL/Redis 不可用 MUST 503，不能仅验 JWT、放行匿名或用内存替代；双 token 会话未完整写入不得返回任一 token，撤销未确认不得假成功。日志 MUST 不含密码/hash/token/验证码/原始手机号/body。旧 /admin、/user MUST 保留旧头/响应，新旧 token 不互通。

#### Scenario: Storage or filter fails
- **WHEN** 过滤器认证、状态查询、登录写入、刷新或退出依赖失败
- **THEN** 返回统一 JSON 与 requestId，不返回 HTML/堆栈或假成功；账号已提交仍保留，凭证不泄露

#### Scenario: Legacy compatibility is exercised
- **WHEN** 调用旧路由或交叉使用新旧 token
- **THEN** 旧契约保持、跨协议拒绝，旧日志不泄露凭证；新 POST /api/v1/echo 匿名变 401，认证后输入校验仍适用

### Requirement: Runtime JWT keys shall be strong explicit and environment-isolated

员工/消费者 MUST 各用独立随机 Base64 HS256 key，解码至少 32 字节，缺失/占位/非法/过短/相同拒绝启动。dev 实际 key MUST 只在忽略 YAML，test 独立值，prod 环境注入。文档 MUST 说明两次 openssl rand -base64 32、正确属性、只改 dev JWT 时重启 API 即可，以及已有 gba-dev 的 up -d 和 Compose 环境变化重建方式，不要求删除卷。

#### Scenario: Keys are configured or rejected
- **WHEN** 配置合规不同 key，或输入弱/占位/无效 key
- **THEN** 前者按明确 HS256 启动且无需根 dev .env/Compose 加 JWT；后者启动失败且错误不含 key，不自动散列补长；legacy Base64 语义变化需重新登录

### Requirement: Development and test accounts shall remain separate from production seeds

dev MUST 用 db/devdata 提供两个既有 nonprod 租户首员工、样板双消费者与对照同手机号消费者，员工只存 BCrypt，幂等不覆盖凭证/状态。test MUST 用独立 src/test/resources/db/fixture 的 @Sql 数据覆盖同租户唯一、跨租户隔离与资源归属，不依赖 devdata/演示密码；租户根仍 db/seed/nonprod，认证业务账号不放 common/nonprod。prod 不加载演示/fixture，生产开通延期。

#### Scenario: Seed and fixture boundaries are verified
- **WHEN** dev 重复初始化或 test/prod 启动
- **THEN** dev 不覆盖已有账号；test 仅显式加载 fixture，prod 无演示账号；V1–V4 保持不变

### Requirement: Development seeds shall execute in Flyway order with a controlled history transition

租户根与认证演示账号 MUST 分别使用 `R__010_local_test_tenants.sql`、`R__020_auth_demo_accounts.sql`，由 Flyway 按描述排序执行，MUST NOT 通过 AFTER_MIGRATE 回调或启动初始化器重跑。脚本 MUST 只补缺失记录并保留人工修改；缺少预期租户或稳定 ID/业务标识/商户归属冲突 MUST 明确失败，不静默忽略。R 仅首次或校验和变化时执行，上游变化不自动触发下游，普通重启不承诺补回人工删除的数据。

历史开发库的改名 MUST 使用独立、显式的一次性维护流程，停同库应用，备份结构＋数据及 history 并完成隔离恢复演练；核验完整 dev 迁移集合，仅允许已知旧 R Missing 与新 R Pending，失败、未知缺失、意外待迁移或 V 校验和变化 MUST 阻断。经报告核对后 repair 仅标记预期旧 R Deleted，再 migrate、validate，保留真实历史与业务数据。MUST NOT 保留运行期旧名兼容脚本、自动 repair、手工改 history、关闭校验或删除业务卷。prod 不加载开发种子，不执行该开发库 repair，继续独立迁移成功后启动应用。

#### Scenario: A fresh development database starts
- **WHEN** dev 连接空库正常启动
- **THEN** V1–V4 先完成，随后 010 租户、020 账号按序执行；history 与实际执行一致，无回调额外写入

#### Scenario: Existing identities and changes are preserved
- **WHEN** 种子显式重复执行，已有租户或账号已修改名称、状态或密码
- **THEN** 不覆盖原行；缺少依赖或稳定标识冲突时明确报错并阻止应用就绪

#### Scenario: Known historical names are retired once
- **WHEN** 经核验的旧开发库显式执行受控改名升级
- **THEN** 旧 R 实际执行记录保留并标记退役，新 R 正常登记成功，业务数据保留；再次运行工具识别已完成，无重复 repair

#### Scenario: Unexpected migration differences are found
- **WHEN** 升级检查发现未知 Missing、失败、V 校验和变化、意外待迁移或目标不符
- **THEN** 在任何 repair/migrate 前失败，保留检查证据，不自动修复历史或继续发布

#### Scenario: A reviewed V3 development database completes the transition
- **WHEN** 已有旧租户 R 历史的开发库 V1–V3 成功，仅固定 V4 及两个新 R 待执行，维护人员确认 B 分支并完成备份隔离恢复
- **THEN** 报告明确列出待执行 V4，repair 只退役旧 R，随后按 V4→010→020 迁移并验证；其他待执行 V、失败与校验和变化仍阻断，全部既有业务行和历史保留

#### Scenario: Multiple development instances migrate concurrently
- **WHEN** 两个独立 Flyway 实例使用相同迁移集合连接同一隔离开发库
- **THEN** 通过 Flyway 锁协调种子执行，无重复记录或唯一键竞争失败；无文件变化重复 migrate 不增加迁移记录、不修改业务行
