# add-redis-auth-sessions 最终验收记录

日期：2026-11-06，Asia/Shanghai。针对认证模块及本次三层包迁移，未执行真实生产发布。后续商品、库存、订单、物流与 Agent 仅交付 [模块路线（本目录入口）](README.md#architecture-module-roadmap)，不属于已实现能力。

## 自动化结果

在仓库根目录实际执行，均为 BUILD SUCCESS：

| 命令 | 完成时间 | Surefire | Failsafe | 失败 / 错误 / 跳过 |
| --- | --- | ---: | ---: | --- |
| `mvn -f api/pom.xml test` | 19:27:41 | 44 | 不运行 | 0 / 0 / 0 |
| `mvn -f api/pom.xml clean verify` | 19:34:59 | 44 | 62 | 0 / 0 / 0 |

第二条从 clean 开始重新编译、打包并执行两阶段测试，耗时 6 分 45 秒；两行中的 44 项为重复运行的同一组测试。完整验收共 106 项，不将两次运行累加为独立用例数。

最终报告：`api/target/surefire-reports/TEST-*.xml`、`api/target/failsafe-reports/TEST-*.xml`。命令输出副本为 `api/target/auth-final-test.log` 和 `api/target/auth-final-clean-verify.log`；日志及生成物被忽略。clean 会清理早期定向报告，历史阶段的摘要仍保留在各维护文档中。

| 集成测试类 | 实际数量 | 验收内容 |
| --- | ---: | --- |
| AuthFlowIT | 15 | 首次建号与独立挑战并发唯一冲突、两类登录、HTTP 严格输入、角色/租户/本人范围、状态停用、idle/absolute、加密轮换重试/重放、刷新/退出竞争、MySQL/Redis 故障 503 |
| AuthSmsRedisIT | 6 | 摘要与范围、过期/重发/第五次错误、并发一次消费、发送失败代际清理、原子配额、旧序列化并存 |
| AuthPersistenceIT / AuthSchemaIT | 4 / 2 | 真实 Mapper/EXPLAIN、租户写入边界、唯一/非空/状态约束、同商户创建者组合外键 |
| AuthDevManualIT | 1 | 隔离 dev 后端实际运行 PowerShell HTTP 联调、九操作 Springdoc 导出、devdata 非破坏性 |
| DevSeedMigrationIT | 18 | 排序/并发/重入、人工修改保留、身份冲突、历史改名、精确 V3→V4 分支、备份恢复与 CLI 门槛 |
| FlywayMigrationIT / FoundationApplicationIT | 4 / 3 | 空库、升级库、显式 baseline、应用启动和 fixture 事务回滚 |
| JwtPackagingIT | 1 | 主 JAR 排除实际 dev/test 密钥配置 |
| ProductionInfrastructureIT / RedisIT | 2 / 6 | 隔离实例的权限/导出恢复/Redis 认证与数据操作，不是生产发布 |

单元与契约测试还验证密钥/claims、输入与 BCrypt、Snowflake、旧协议、新匿名 echo 401、上下文清理和 JSON 审计分流。Docker 不可用时明确失败，不以跳过 IT 或连接开发实例替代。容器使用动态宿主机端口、不挂载业务卷；本轮全量验收没有连接 gba-dev 的固定 MySQL/Redis 端口。

## 手动链路与契约

`AuthDevManualIT` 实际启动隔离后端并执行 [manual-verification.ps1](../../../scripts/test/manual-verification.ps1)，输出 `api/target/auth-manual-verification.log` 以 PASS 标识成功，不输出密码、验证码或 token。覆盖两租户员工用户名/手机号登录、短信首次建号、验证码重放、me/账号资源、401/403/404、员工开通、refresh 幂等和退出、多设备互不影响；未据此宣称 Apifox 或浏览器前端已验收。

本轮实际 Springdoc 输出为 `api/target/auth-v1.openapi.yaml`。解析后与 [仓库快照](../../../../docs/api/auth-v1.openapi.yaml) 比较：忽略随机端口对应的 `servers` 后完全一致，共九个操作。没有独立消费者注册接口，只有店铺短信/消费者登录、员工登录、统一 refresh/logout/me、员工创建和两类账号详情。已核对 Bearer、双 token、字符串 ID、201 Location、no-store、Retry-After 和错误 envelope。没有运行独立 oasdiff 工具，不将结构比较称为 oasdiff 结果。

兼容差异：旧 `/admin/**`、`/user/**` 的路径、请求头和 envelope 保留，但新旧 token 互不通用；规范化 Base64 密钥后旧会话须重登。`POST /api/v1/echo` 匿名变为 401，认证后非法参数仍为 400。消费者首次登录为 200，员工创建为 201，刷新不要求有效 access，不接受客户端指定租户/角色。

## 迁移与结构

V4 仅增加 `consumer_account`、`merchant_staff_account` 及租户内唯一/组合外键；已发布 V1–V3 未改写。dev 演示数据与 test fixture 分离，prod 不加载演示租户/账号。种子统一为 `R__010_local_test_tenants.sql` → `R__020_auth_demo_accounts.sql`，正常启动无补种回调或自动 repair。

18:25–18:27 已获授权执行的真实开发库维护属于任务 8.10，详见 [开发种子升级记录（本目录入口）](README.md#deployment-dev-seed-upgrade)：备份经隔离恢复核验后，旧租户 R 保留历史并退役，再执行 V4/010/020；升级后 2 租户、2 员工、3 消费者，重复启动与登录未改业务行。最终全量测试未重复此维护，也未修改真实业务卷。

auth 的 29 个类迁到 `controller/service/mapper` 与 `entity/dto/security/cache/handler/exception/enums/util/logging`。逐类对比确认方法体保持不变；同步 MapperScan、XML namespace/resultType、Springdoc、ControllerAdvice 与测试引用，完整 clean verify 已覆盖迁移后的代码。未来业务域沿用该结构。

## 配置、限制与回退

- 实际 `application-dev.yml` 的两把 Base64 key 解码至少 32 字节且不同；文件被忽略、未跟踪，实际 key 未出现在跟踪/候选文件或分支 diff。共享示例只含占位符，测试 key 与演示账号是明确公开的非生产数据。主 JAR 不含实际 dev 配置。
- `openspec validate add-redis-auth-sessions --strict` 通过；proposal、design、user-auth spec、tasks 及维护文档已核对。`.github/PULL_REQUEST_TEMPLATE.md` 保留原版，实际 PR 描述另行填写。
- 真实短信、生产首商户/首员工、完整 RBAC、密码找回、前端、多 tab 浏览器验收、Cookie 迁移、CI 和生产发布未实现或执行。prod 短信 disabled 返回 503；所有员工暂共享本商户开通权限。
- localStorage 接入存在 XSS 风险；Redis Lua 按单实例设计，未验证 Cluster 跨槽。每次私有请求检查 Redis 与 MySQL 状态，尚无吞吐或延迟指标，不虚构性能结论。
- 依赖故障拒绝放行。仅 active 丢失可 refresh 恢复，control/refresh 丢失须重登；恢复历史 Redis 快照后须受控失效认证命名空间或轮换 key，避免撤销回滚。
- 回退代码前核验新增表兼容性，通常保留 V4 表；不恢复弱 key，不修改已发布迁移或删除业务卷。历史种子改名后不得混跑旧迁移包；必要数据库恢复遵循结构＋数据备份与新增写入处置流程。
