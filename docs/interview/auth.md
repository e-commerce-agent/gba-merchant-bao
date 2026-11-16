# 认证模块面试卡片

更新：2026-11-06。实现已由 [PR #12](https://github.com/e-commerce-agent/gba-merchant-bao/pull/12) 合入；本次 `docs/auth-interview` 只归档和整理材料，不增加认证功能。完整需求与决策见 [归档 change](../../openspec/changes/archive/2026-11-06-add-redis-auth-sessions/design.md)，实际验证见 [验收记录](../../api/docs/testing/20261106-auth-acceptance/README.md)。

## 一、业务梳理

消费者从某家店铺进入，用手机号和短信验证码登录。第一次验证成功时自动建立该店的消费者账号，之后登录复用；同一手机号在不同商户是不同账号。员工由商户内部开通，用商户编码、用户名或手机号、密码登录。

认证模块解决三个问题：确认访问者是谁、限定其商户和角色、限定其能读取的具体账号资源。登录后支持按需刷新、当前设备退出和其他设备继续使用，为后续订单与 Agent 提供可信 Actor。当前只实现账号资源授权，订单/物流 Tool 和 Agent 仍是后续模块。

可直接讲的开场：

> 我做了多商户场景的认证模块。消费者从店铺页面短信登录，首次验证成功自动建号；员工走受控开通和密码登录。服务端用 JWT、Redis 会话和数据库账号状态共同确认身份，再用可信商户和本人条件约束 SQL。这里我重点解释两个设计：未登录时如何确定店铺，以及首次登录怎样兼顾正常流程、并发和失败恢复。

## 二、技术实现

**痛点：** 消费者没有登录时，系统还不知道其商户；同手机号又允许存在于不同商户。仅按手机号查询会串账号，仅签 JWT 无法立即退出或检查停用。验证码消费、MySQL 建号和 Redis 建会话还跨越不同存储，不能假定一次事务全部回滚。

**主方案：** 匿名认证入口放在 `/api/v1/stores/{storeSlug}/auth/**` 下，由服务端解析 ACTIVE 商户。账号持久化采用 MySQL 租户内唯一约束；短信挑战由 Redis Lua 一次消费。access 是 HS256 JWT，refresh 是随机 opaque 凭证；Redis 保存活跃记录、控制状态和刷新摘要。后续私有接口只接受服务端验证后的 Actor。

**递进：** 增加手机号规范化、发送/登录限流、独立挑战并发建号冲突处理、刷新短时幂等与重放撤销、退出竞争保护、依赖故障 503、脱敏日志及环境密钥检查。包结构采用 `controller → service → mapper`，辅以 entity/dto、安全与缓存包。当前 Controller 仍有短信发送编排和成功操作后的 touch；refresh/logout 在过滤器凭证分支调用 Service，不能描述成所有入口都严格经过 Controller 执行业务。

**数据流：** 店铺页面携带公开 locator → 申请并验证短信 → 事务创建/复用账号 → 提交 MySQL → 原子建立 Redis 会话 → 成功返回双 token → 私有请求验签、校验会话和主体状态 → Actor 限定 SQL 范围。详细正常登录流程见下节。

## 三、技术深挖：只选两个主讲难点

### 难点一：为什么使用 storeSlug，其他方案有什么取舍？

这是多商户匿名入口的设计问题，不是生产故障。消费者尚无 token，系统不能从登录身份取得商户；但短信挑战和消费者账号都必须先绑定到一家店铺。

| 方案 | 在本项目中的取舍 |
| --- | --- |
| 店铺路径 `storeSlug` | 页面已有 `/store/{storeSlug}` 上下文，可直接携带；匿名阶段即可定位商户，消费者不用手输商户编码。本期采用。 |
| 请求体里的 merchantId | 技术上可以作为公开定位参数，但需要页面维护内部 ID，且各入口都要区分“定位”与“授权”。本期不向消费者认证 DTO 开放内部商户字段。内部 ID 本身也不是秘密或权限凭证。 |
| 自定义店铺 Header | 同样是可篡改的客户端输入，不比路径更可信；页面已有路径上下文，本期不增加一套约定。 |
| 独立域名/子域名 | 适合独立店铺域名，但需增加域名映射、可信代理/Host 边界和部署维护；当前单应用路径足够。 |
| 从 JWT 取商户 | 适合已登录的私有请求；短信首次登录时尚无 token，不能解决匿名定位。 |
| 全平台手机号账号 | 会改变“消费者账号归属商户”的模型，需要平台身份与商户成员关系；本期没有引入。 |

实现落点：`AuthInput.storeSlug` 当前直接复用 `merchantCode` 规范化规则；`TenantResolver.resolveActive` 按现有编码查 ACTIVE 商户。**当前没有单独 slug 列、独立别名表、改名或跳转机制。** `storeSlug` 表达公开入口角色，当前值就是商户编码的公开别名；不是额外随机生成的一种安全标识。

URL 可被修改，因此它只定位，不授权。访问 B 店的短信入口就处理 B 店挑战；已登录的通用私有接口则按 Actor 的商户处理，绝不拿 URL 或模型输入覆盖身份。A 店 token 若被误发到通用接口，服务端只能按 A 身份限定数据，无法猜测浏览器其实正在看 B 店；前端必须按店铺和账号类型选择凭证并核对 principal。

面试话术：

> 我选店铺路径，是因为消费者从店铺进入时已经有公开上下文，首次登录不需要再输入商户编码。路径并不天然安全：服务端把它解析为有效商户，只用于匿名定位；登录后的私有权限来自经过验证的 Actor。当前 slug 复用商户编码，没有另建映射表，未来支持店铺改名时才增加别名迁移。

证据：`AuthInputTest`、`AuthFlowIT#httpContractStrictInputRolesResourcesAndDedicatedBranches` 以及租户/本人资源测试。没有前端 A→B→A 实测或“防攻击提升百分比”。

### 难点二：消费者正常登录怎么跑，首次登录与异常如何处理？

1. 消费者进入店铺页面并输入手机号。页面从路由获取 `storeSlug`，申请 `POST /api/v1/stores/{storeSlug}/auth/sms-codes`；请求体只传手机号。
2. 服务端解析 ACTIVE 商户，手机号规范化为 `+86` 格式，检查手机号/IP 配额和发送冷却，创建绑定“商户 + 手机号 + LOGIN”的挑战。验证码为六位随机数、300 秒有效，Redis 保存加盐摘要；发送成功后激活挑战。当前是 dev/test mock，dev 显式开关才返回 debugCode；prod 未接真实短信。
3. 用户提交手机号和验证码到同店的 `consumer/login`。服务端执行登录限流、店铺解析和规范化，Lua 原子校验并消费挑战；同挑战只能成功消费一次。
4. 数据库事务按 `(merchant_id, phone)` 查询：已有账号则复用；不存在则生成 ID 并创建 ACTIVE 账号。多个独立有效挑战恰好并发首次建号时，由唯一约束决定赢家，失败方在事务结束后查询赢家账号；不是返回重复注册 409。
5. 确认账号 ACTIVE；停用账号拒绝登录，不能重建绕过停用。MySQL 账号事务完成后才调用 `SessionService.login`，再次检查当前账号/商户状态。
6. 服务端生成独立 sid、access 和 refresh。Redis Lua 原子写入 active/control/refresh 索引；只有完整建立会话后才向客户端返回 200 和双 token。首次与再次登录均为 200，没有独立注册接口。
7. 后续按需刷新；退出只撤销当前 sid。按店铺和账号类型保存凭证、多 tab 协调及迟到响应处理目前是前端接入契约，尚无浏览器实现。

| 失败点 | 对外结果与恢复方式 |
| --- | --- |
| 验证码错误、过期、已用或范围不符 | 400/11002；不得继续建号。同一挑战并发消费最多一个通过。 |
| 配额超限 | 429，附 Retry-After；不能持续重试绕过。 |
| 有效商户下已有消费者停用 | 401/11001；不重建账号。 |
| 验证码已消费，数据库失败 | 503；不恢复验证码，重新取码。 |
| 账号已提交，Redis 建会话失败 | 503，无任一 token；保留账号，下次取码登录复用。 |

面试话术：

> 消费者只有一个短信登录入口，第一次验证成功自动建号。先校验并消费商户范围的验证码，再用数据库事务创建或复用账号，提交后才建立 Redis 会话。同挑战重放由 Lua 拒绝，独立挑战并发建号由数据库唯一约束兜底。数据库和 Redis 没有共享事务，所以 Redis 失败时保留已提交账号、返回 503 且不返回 token，用户重新取码即可继续。

证据：`AuthFlowIT#independentChallengesRacingFirstLoginReuseOneCommittedAccount` 用数据库查询屏障制造并发；`databaseFailureConsumesChallengeAndSessionFailureKeepsCommittedAccount` 验证跨存储失败边界；`AuthSmsRedisIT#concurrentConsumptionHasExactlyOneWinner` 验证挑战一次消费。这些是隔离测试构造的竞争和故障，不冒充线上事故。

其余实现问题统一存入 [painpoints](painpoints.md)，不再增加第三个主讲难点。

## 四、高频八股与项目对应

来源是仓库已有 [牛客面经摘录](../design/03牛客面经-1.txt)，其中包含短信登录/Redis token、事务隔离、Lua、索引和 ThreadLocal 问题；未重新联网统计题目频次。下面是针对本项目整理的备答。

| 追问 | 本项目如何回答 |
| --- | --- |
| JWT 和 Redis 会话为什么并用？ | JWT 携带可验签身份；Redis 确认活跃与撤销状态，数据库确认当前账号/商户状态。不是“用了 JWT 就不用查询存储”。 |
| Lua 为什么能防重复消费？ | 同一 Redis 实例内脚本串行执行校验和删除，避免 GET 后 DEL 的竞争；不把原子执行等同于跨 MySQL 的事务，也不承诺脚本运行错误自动回滚。 |
| 先查再插能避免重复账号吗？ | 不能，两次查询都可能不存在；用商户内唯一索引兜底，并处理 DuplicateKey 后读取赢家。 |
| MySQL 事务能回滚 Redis 吗？ | 不能。本项目明确划开验证码消费、账号事务与会话创建，按失败点确定恢复路径。 |
| 复合索引为什么 merchant_id 在前？ | 登录查询按商户和规范化手机号等值定位，员工用户名/手机号分别使用对应唯一索引；实际 EXPLAIN 由持久化 IT 验证。 |
| 为什么不保存明文密码或 MD5？ | 员工采用带盐 BCrypt cost=12；同时校验 UTF-8 字节数，避免超 72 字节截断。旧 MD5 账号不自动迁入。 |
| idle 和 absolute 区别？ | idle 是合格活动可推进的活跃期限，absolute 从原始登录固定计时；有效 refresh 可恢复 idle，不能延长 absolute。 |
| ThreadLocal 有什么风险？ | 线程池复用可能残留上下文；SecurityContext 与 legacy BaseContext 在结束路径清理，正常/异常均有测试。 |
| 认证能直接供 Agent Tool 使用吗？ | 后续 Tool 从服务端注入 Actor 并调用领域 Service；模型不能自行指定 merchantId/customerId。本 PR 没有实现 Tool。 |

## 附：核心代码、commit 与 PR

表中路径是当前三层包位置。功能首交付与路径迁移分开列，避免把后来的文件路径指向尚未包含它的早期 commit。共同关联实现 [PR #12](https://github.com/e-commerce-agent/gba-merchant-bao/pull/12)；合并 commit 为 `a10a905`，本次文档 mini PR 的编号在创建后再补，不预填。

| 核心类 / 方法 | 职责与当前文件 | 功能 commit | 当前路径 commit |
| --- | --- | --- | --- |
| AuthController#send / #consumer | [短信与消费者登录入口](../../api/src/main/java/com/gba/merchantbao/auth/controller/AuthController.java) | `68b660c` | `7f09f0b` |
| AuthInput#storeSlug / #phone | [公开定位及手机号规范化](../../api/src/main/java/com/gba/merchantbao/auth/util/AuthInput.java) | `68b660c` | `7f09f0b` |
| TenantResolver#resolveActive | [有效商户解析](../../api/src/main/java/com/gba/merchantbao/auth/service/TenantResolver.java) | `c4802d2` | `7f09f0b` |
| SmsChallengeService#send / #consume | [挑战发送、激活和一次消费](../../api/src/main/java/com/gba/merchantbao/auth/service/SmsChallengeService.java) | `68b660c` | `7f09f0b` |
| AccountService#consumerLogin | [事务建号/复用、冲突处理、状态校验](../../api/src/main/java/com/gba/merchantbao/auth/service/AccountService.java) | `68b660c` | `7f09f0b` |
| ConsumerAccountMapper#findByPhone / #insert | [MyBatis 接口](../../api/src/main/java/com/gba/merchantbao/auth/mapper/ConsumerAccountMapper.java)与[租户 SQL](../../api/src/main/resources/mapper/auth/ConsumerAccountMapper.xml) | `c4802d2` | `7f09f0b` |
| SessionService#login / #refresh / #logout | [会话建立、轮换与撤销](../../api/src/main/java/com/gba/merchantbao/auth/service/SessionService.java) | `68b660c` | `7f09f0b` |
| AccessTokenService#issue / #verify | [JWT 签发与严格验证](../../api/src/main/java/com/gba/merchantbao/auth/service/AccessTokenService.java) | `9db2a3e` | `7f09f0b` |
| AuthSecurityFilter#doFilterInternal | [凭证分支、Actor 与 finally 清理](../../api/src/main/java/com/gba/merchantbao/auth/security/AuthSecurityFilter.java) | `68b660c` | `7f09f0b` |
| DevSeedUpgrade#main | [一次性开发种子维护入口](../../api/src/main/java/com/gba/merchantbao/maintenance/DevSeedUpgrade.java) | `fe4657c` | `fe4657c` |

Commit 链接：[账户持久化 c4802d2](https://github.com/e-commerce-agent/gba-merchant-bao/commit/c4802d2)、[JWT 9db2a3e](https://github.com/e-commerce-agent/gba-merchant-bao/commit/9db2a3e)、[认证闭环 68b660c](https://github.com/e-commerce-agent/gba-merchant-bao/commit/68b660c)、[种子维护 fe4657c](https://github.com/e-commerce-agent/gba-merchant-bao/commit/fe4657c)、[三层迁移与验收 7f09f0b](https://github.com/e-commerce-agent/gba-merchant-bao/commit/7f09f0b)。

## 附：测试条件与真实数字

以下是实现阶段 2026-11-06 的记录，本次文档整理未重跑业务测试。条件：JDK 21、Maven 3.9.11、Docker Desktop Linux 容器引擎；MySQL `8.0.34`、Redis `7.4-alpine`，Testcontainers 动态端口和可销毁实例，不连接 gba-dev 固定端口或业务卷。

| 记录 | 实际结果 | 来源与解释 |
| --- | --- | --- |
| `mvn -f api/pom.xml test` | 44 项，失败/错误/跳过均 0；19:27:41 完成 | Surefire XML 与 [最终验收](../../api/docs/testing/20261106-auth-acceptance/README.md) |
| `mvn -f api/pom.xml clean verify` | 44 单元/契约 + 62 IT = 106 项；失败/错误/跳过均 0；6 分 45 秒 | 19:34:59 完成；重复运行的 44 项不再累计成独立用例 |
| 核心认证 IT | AuthFlowIT 15 项，AuthSmsRedisIT 6 项，AuthDevManualIT 1 项 | 类级用例数，不能表述为“并发 15/6/1 人” |
| HTTP / Springdoc | 九操作；隔离 PowerShell 联调 PASS | 实际快照结构比较忽略 runtime servers 后一致；未执行独立 oasdiff 或 Apifox |
| access / consumer / staff 期限 | access 7200 秒；consumer idle 7 天/absolute 30 天；staff idle 1 天/absolute 7 天 | 配置与可控时钟测试，未真实连续等待 30 天 |
| 短信与重试 | 验证码 300 秒、最多五次错误；默认冷却 60 秒、refresh 幂等 10 秒 | 默认规则和测试验证，属于功能参数，不是优化指标 |

原始报告位于忽略的 `api/target/surefire-reports`、`failsafe-reports`、`auth-manual-verification.log`。仓库保存验收摘要，clean 可能清除本机原始报告。没有 QPS、P95/P99、线上成功率、节省成本或性能提升百分比；这些数字必须等独立压测/线上观测后再写。

真实短信、生产首员工、完整 RBAC、前端、Cookie 迁移、Redis Cluster、Agent/RAG/MQ/ChatMemory 与真实生产发布均未完成；不能从认证测试通过推导这些能力已交付。
