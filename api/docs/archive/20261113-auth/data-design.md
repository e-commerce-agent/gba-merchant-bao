# 认证数据设计

本页记录 add-redis-auth-sessions 的账号持久化、短信与 Redis 会话实现。后续模块仅交付路线，完整验收见 [验证记录（本目录入口）](README.md#verification)；这里不表示生产发布。

## 表与字段

`V4__auth_accounts.sql` 只追加 `consumer_account` 与 `merchant_staff_account`，均为 InnoDB。默认字符集为 utf8mb4 / utf8mb4_0900_ai_ci；手机号、用户名、状态、密码摘要另显式使用 ASCII / ascii_bin。账号类型决定未来固定角色，不建 role 列、权限表或平台账号，不使用 merchant_id=0。

两表共有字段如下。

| 字段 | 类型与默认 | 边界 |
| --- | --- | --- |
| id | BIGINT NOT NULL，主键 | 应用分配 Snowflake；不可从请求指定 |
| merchant_id | BIGINT NOT NULL | 引用 merchant.id；来自服务端解析的租户/后续可信 Actor |
| phone | VARCHAR(20) NOT NULL，ascii_bin | 保存规范化 E.164 原值；商户内唯一，跨商户可复用 |
| display_name | VARCHAR(64) NOT NULL DEFAULT '' | 可空字符串，不能 NULL；展示资料 |
| status | VARCHAR(16) NOT NULL DEFAULT 'ACTIVE'，ascii_bin | CHECK 只允许大小写精确的 ACTIVE / DISABLED |
| created_at | TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) | 数据库生成创建时刻 |
| updated_at | TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) | 数据库维护更新时间 |
| version | INT NOT NULL DEFAULT 0 | 更新与删除使用 expectedVersion 乐观并发条件，成功更新加一 |

`merchant_staff_account` 另有以下字段。

| 字段 | 类型与默认 | 边界 |
| --- | --- | --- |
| username | VARCHAR(32) NOT NULL，ascii_bin | 上层规范化为小写；商户内唯一，与手机号分别查询 |
| password_hash | VARCHAR(100) NOT NULL，ascii_bin | 仅保存带盐 BCrypt 摘要，目标 cost=12；不保存明文/可逆密码，不接收旧 MD5 |
| created_by | BIGINT NULL | 首员工由受控 fixture/后续 devdata 提供时为空；组合外键要求非空创建者属于相同商户 |

MySQL TIMESTAMP 按时区转换并保存时刻，持久化行使用 `Instant`，接口时间约定 UTC ISO-8601。不得用本地时间字符串替代真实时刻；运行连接的时区设置必须与驱动约定一致。

外键均不级联删除或修改。员工仍被其他员工引用时不能删除；业务停用保留记录，不释放原手机号/用户名。Mapper 的 delete 是内部受租户与版本约束的数据操作，本期没有账号删除 HTTP 功能。数据库只证明非空创建者同租户，不证明调用者身份、权限或“仅首员工可 NULL”；开通用例从可信 Actor 写入商户/创建者，不开放 bootstrap。

## 索引与查询

| 表 / 索引 | 列 | 对应查询或约束 |
| --- | --- | --- |
| merchant / 既有 uk_merchant_code | merchant_code | 匿名店铺/员工登录按编码等值解析商户，再确认 ACTIVE；storeSlug 当前复用编码别名 |
| merchant / PRIMARY | id | 已知服务端 merchantId 时查询当前商户状态 |
| consumer_account / PRIMARY | id | 全局主键唯一；组合资源查询仍必须带 merchant_id |
| consumer_account / uk_consumer_merchant_id | merchant_id, id | 同租户资源键，支持后续组合引用与租户资源定位 |
| consumer_account / uk_consumer_merchant_phone | merchant_id, phone | 短信登录账号查询、首次建号并发唯一约束 |
| merchant_staff_account / PRIMARY | id | 全局主键唯一；不能据此提供无租户详情查询 |
| merchant_staff_account / uk_staff_merchant_id | merchant_id, id | 员工资源定位与 created_by 组合外键的被引用键 |
| merchant_staff_account / uk_staff_merchant_username | merchant_id, username | 员工用户名等值查询、开通唯一约束 |
| merchant_staff_account / uk_staff_merchant_phone | merchant_id, phone | 员工手机号等值查询、开通唯一约束 |
| merchant_staff_account / idx_staff_merchant_creator | merchant_id, created_by | 创建者组合外键的引用端索引，限制跨租户创建关系 |

不新增 status、created_at、last_login_at 索引；当前没有列表查询。员工用户名与手机号由输入规则分流，分别命中唯一索引，不使用 OR 或全表扫描。`AuthPersistenceIT` 对实际 Mapper BoundSql 执行 MySQL EXPLAIN，验证手机号、用户名、merchant_code 使用对应唯一索引；按全局唯一 id 定位时优化器可以选 PRIMARY，SQL 中的 merchant_id 仍是不可省略的过滤条件。

`auth.mapper` 自有 Mapper 与 `mapper/auth/*.xml`，数据库 Row 放在 `auth.entity`，不依赖 legacy 实体/Mapper。`MyBatisConfiguration` 显式扫描新旧 `@Mapper`，`classpath*:mapper/**/*.xml` 同时加载根目录旧 XML 和递归新 XML。auth 查询禁用结果缓存并刷新本次会话缓存，以便状态查询不复用之前的 ACTIVE 结果；事务可见性仍遵守 MySQL 的隔离级别，不能把缓存设置理解成跨事务即时一致性。

消费者查手机号、员工查用户名/手机号、资源详情都要求 merchantId；消费者资源查询另带 ownerAccountId。无匹配返回 null，由授权用例统一解释为 404，不能先全局查存在性。更新/删除同时带 merchant_id、id、expectedVersion，不匹配返回 0；不能更新租户、主键或 created_by。插入的 created_by 即使被误传其他商户 ID，也由组合外键拒绝。

## 手机号与凭证数据边界

手机号持久化前必须规范化：大陆 11 位或等价 +86 输入统一成 `+86` 加 11 位。Mapper 接受已规范化值，不自行 trim/拼接国家码；输入规范化与格式验证由 AuthInput 完成。ASCII 二进制唯一索引只比较规范化后原值，不替应用验证号码格式。用户名同理，按 `[a-z][a-z0-9_-]{2,31}` 规范化为小写后写入。

原始规范化手机号只用于内部索引定位和后续短信发送；不把掩码写回数据库或用掩码判断唯一。新 API 必须显式映射为 phoneMasked（含 +86 的脱敏能力在任务组 4 完成），不直接返回持久化行。`ConsumerAccountRow` / `StaffAccountRow` 是内部数据，toString 不含手机号、用户名、密码摘要；不能记录整行、请求体或 SQL 参数。password_hash 也不得进入接口或普通/审计日志。这里的持久化设计不等于手机号已加密或匿名化。

已有 DISABLED 账号仍被查出，由登录用例拒绝，不能过滤后误建新号。消费者并发建号以 `(merchant_id,phone)` 唯一冲突读取赢家并重新检查状态；员工重复标识由开通用例映射 409。Mapper 不实现登录、密码校验或 Redis 会话事务。

## 数据分层与独立 fixture

| 来源 | 当前账号数据 | 加载方式 |
| --- | --- | --- |
| db/migration/V4__auth_accounts.sql | 无，只有 DDL | dev/test 与受控 prod 迁移 |
| db/seed/common | 无认证账号 | 全环境参考数据路径 |
| db/seed/nonprod | 只有两个既有租户根，无认证账号 | dev/test Flyway |
| db/devdata | R__020_auth_demo_accounts.sql，两个首员工及三个消费者 | 仅 dev Flyway |
| src/test/resources/db/fixture/auth_accounts.sql | 独立测试账号 | 相关测试 `@Sql` 显式加载，不在任意 Flyway locations |

空 dev 库中先执行所有待执行 V，再按描述排序执行 R：`010_local_test_tenants` 先于 `020_auth_demo_accounts`，locations 的列举顺序不是依赖。没有迁移完成回调或启动补写。租户与账号只插缺失记录，保留密码、状态和人工资料；缺少预期租户或固定 ID/业务标识冲突以 `dev_seed_*` 列名明确失败。普通 test 不加载 devdata，专用隔离联调测试才显式启用。

已记录历史旧名 R 的开发库须先按 [种子升级说明（本目录入口）](README.md#deployment-dev-seed-upgrade) 完成一次性受控维护；运行目录不保留旧名空脚本。旧执行记录保留，repair 仅将核验过的旧 R 标记 Deleted（不删除业务数据），再执行新 R 并 validate。应用启动不自动 repair。R 仅首次或校验和变化执行，上游变化不自动触发下游，手工删除数据后普通重启不自动补回；补种须显式受控执行，不改 history 伪造待执行。

fixture 按 `M_3C_DEMO` / `M_ISOLATION_TEST` 商户编码解析 merchant.id，不复制租户内部 ID。两个租户各有 fixture_staff；样板租户两名消费者，对照租户一名复用手机号消费者。使用合成号码、固定测试主键和独立 test-only BCrypt cost=12 凭证，与后续 dev 演示密码分离，不发送真实短信。

`AuthPersistenceIT` 在事务内加载 fixture，验证租户内唯一、跨租户同号/同名共存、消费者本人/员工同租户资源范围，并在 `@AfterTransaction` 按 fixture 主键检查回滚。其他写入用例也回滚；不清空业务表或共享租户 seed。未来 HTTP/异步用例若跨事务，须按本用例商户与稳定标识定向清理。测试使用动态端口 Testcontainers，关闭复用，不连接 gba-dev 或真实生产卷。

## 迁移与恢复

空库顺序 V1→V2→V3→V4；已登记 V3 仅追加 V4。V1/V2/V3 文件及校验和保持不变，V4 不修改旧 employee/user，也不导入旧账号、MD5、微信 openid 或旧会话。新表创建后没有自动认证账号，生产首员工/商户开通延期。

未登记非空旧库仍拒绝自动 migrate；仅在完整结构核验与备份恢复演练后显式 baseline1，再执行 V2/V3/V4，见 [Flyway 接管手册（本目录入口）](README.md#deployment-flyway-baseline)。继续启用 validate-on-migrate、clean-disabled，关闭 baseline-on-migrate、out-of-order。

迁移前确认目标实例与停写窗口，按 [备份恢复手册（本目录入口）](README.md#deployment-backup-recovery) 导出结构＋数据（包含 history），先恢复到隔离库核对代表性行、索引与外键。MySQL DDL 可能部分提交；失败即停止后续应用启动，保留日志与 history，评审后向前修复，不修改已发布迁移，不自动 clean/repair/drop 新表或删除卷。

应用回退前核对新增表与旧应用兼容，通常保留两张新增表即可回退代码；如需数据库恢复，先评估新增账号写入的丢失与补偿，再从完整备份隔离恢复后受控切换。不能把生产恢复当普通测试或自动降级。

`FlywayMigrationIT` 验证空库 V1→V4、已登记 V3→V4、重复 migrate 不重建/改写账号及旧行、历史校验和保留、未登记非空库拒绝与显式 baseline。启动与生产权限测试按版本和成功状态检查，不以总迁移条数猜测版本。

## 主键与 API ID

认证新记录注入 `common.id.IdGenerator`，由单例 `SnowflakeIdGenerator` 分配 BIGINT。布局为 41 位毫秒（固定 epoch：2026-01-01T00:00:00Z）、10 位 worker、12 位序列，符号位为 0。不得改变 epoch 或另建同 worker 的生成器并发写入同一数据库。

`merchant.id.worker-id` 可通过 `MERCHANT_ID_WORKER_ID` 设置，合法范围 0–1023。仓库默认 0 仅供单实例 dev；test 显式为 1023。多实例（包括滚动发布中同时存活的新旧实例）必须分配不同 worker，不可复制默认值。重启或重新分配 worker 前必须确保本机时钟已越过该 worker 最后使用的时间；不提供自动 worker 租约或号段服务。

prod YAML 强制从 `MERCHANT_ID_WORKER_ID` 读取，生产 Compose 同样要求显式提供；`.env.example` 的 0 只表示单实例示例，扩容时必须为各实例分别配置。本次仅更新配置模板，不执行部署。

时钟回退、越过 epoch 范围均拒绝生成；同一毫秒超过 4096 个 ID 时立即失败，调用方只能在时钟推进后重试，不制造未来时间也不忙等。受控测试时钟验证并发唯一、回退和序列耗尽。现有 merchant 固定种子 ID 和 legacy 自增 ID 不改写。

持久化与 Mapper 使用 long/Long；新 API 的 accountId、merchantId 使用 `common.api.ApiId`（`@JsonValue` 输出十进制字符串），避免 JavaScript 对大于 2^53−1 的整数丢失精度；该类型不改变 expiresIn 等数值，也不修改 legacy 序列化。后续认证 DTO 必须沿用此边界。

## Redis 命名空间与原子边界

认证独立使用 StringRedisTemplate 和 Lua，不修改 legacy RedisTemplate 序列化。前缀 `mba:{namespace}:auth:`，namespace 来自 `merchant.auth.namespace`，默认单个激活 profile；多 profile 必须显式配置单一命名空间。它是部署隔离与受控失效开关，不是 Redis Cluster hash tag；当前脚本面向单实例 Redis。所有 API 实例必须使用相同环境前缀，dev/test/prod 不共用前缀。

| 后缀 | 类型 | 值与 TTL |
| --- | --- | --- |
| sms:{merchantId}:LOGIN:{phoneSha256} | hash | 随机 challengeId、盐、验证码摘要、ready、失败数、expiresAt；300 秒 |
| sms-cooldown:{merchantId}:LOGIN:{phoneSha256} | string | 发送占位；默认 60 秒 |
| sms-quota:{merchantId}:LOGIN:{phoneSha256} | string | 默认 10 次/小时；第一次写入原子设置 3600 秒 |
| sms-ip:{remoteAddrSha256} | string | 默认 30 次/小时，3600 秒 |
| login-ip:/refresh-ip:{remoteAddrSha256} | string | 分别默认 30 次/900 秒，原子计数/TTL |
| password-fail:{merchantId}:account:{id} | string | 同员工用户名/手机号共享失败数，5 次/900 秒；未知账号用标识摘要，成功清除账号失败数 |
| session:{sid} | string JSON | 身份与 idleAt；消费者 7 天、员工 1 天，均 capped absolute |
| control:{sid} | hash | sid、可信身份、loginAt、absoluteAt、generation、currentHash、state、retryKey；消费者 30 天/员工 7 天固定截止 |
| refresh:{tokenSha256} | hash | sid、generation、CURRENT/USED/REVOKED、已用 requestId/usedAt；原 absolute 到期 |
| refresh-retry:{sid}:{requestId} | string | AES-256-GCM 密文；默认 10 秒，最多到 absolute |

限流属性在 `merchant.auth.limits`，名称对应 AuthLimits 的 kebab-case，例如 sms-phone-per-hour、sms-ip-per-hour、login-ip-per-window、refresh-ip-per-window、login-window-seconds、password-failures、password-window-seconds、refresh-retry-seconds。只信 servlet remoteAddr，不解析客户端 X-Forwarded-For。超限 429/11003，Retry-After 是剩余窗口秒数。

短信先原子预留 PENDING，再调用 SmsSender，最后按 challengeId 激活。未激活不可用；发送失败按该代 ID 清理，不误删后发挑战。验证码只保留带盐摘要，成功消费与第五次失败删除均为 Lua 原子操作。普通日志不含号码、验证码或 body；没有真实短信 SDK。

消费者先消费挑战，再提交 MySQL 查找/首次建号事务。唯一冲突在回滚事务之外重读赢家并检查 ACTIVE；停用记录不重建。数据库故障不恢复挑战；账号提交后 Redis 故障保留账号，返回 503 且无双 token。

初始会话一次脚本写入 active/control/refresh。sid、refresh 是独立的 256 位安全随机值，refresh 只存 SHA-256；JWT jti 独立随机。refresh 从摘要反查可信身份，MySQL 状态有效后用 CAS 轮换 currentHash/generation 和 idle，original absolute 固定。只有 active 丢失可恢复；control 或当前索引丢失拒绝，JWT 不能补建。me、匿名、失败和后台轮询不 touch；当前两个账号详情与员工创建成功后 touch，脚本须同时验证 control ACTIVE、当前索引及现存 active，退出前的在途请求不能重建。

重试缓存通过角色 JWT key 使用 HKDF-SHA256（info=auth-refresh-retry-v1）派生 AES-256-GCM key，随机 12 字节 nonce，AAD=sid:generation:requestId:absoluteAt。相同旧摘要/UUID 在 10 秒内且下一代仍当前才取回同响应，不重复 touch；密文篡改拒绝。过窗、不同 UUID、旧代重放原子撤销该 family、active 与缓存；未知随机值不撤销其他会话。

logout 支持有效 access 或已知历史 refresh，原子撤销 control、当前 refresh、active 和缓存。历史摘要与撤销墓碑保留到 absolute 支持重复退出；其存在不授予刷新权限。独立设备/店铺/类型互不影响。Redis/MySQL 不可用统一 503，绝不降级为只验 JWT。

## 账号维护

devdata 按商户编码关联，只插入缺失的稳定用户名/手机号，不覆盖现存 hash/status；首员工 created_by=NULL，普通开通只取 Actor。演示密码与 fixture 分离，见 [联调手册（本目录入口）](README.md#manual-verification)。新员工密码由调用方生成，经模块外安全渠道交付；不提供查回明文、自动替同事登录、重置/停用管理或生产 bootstrap。
