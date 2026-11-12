# 认证后端联调

范围为 add-redis-auth-sessions 第 4–7 节。这里记录后端操作与前端接入要求；未实现前端、未发布生产；第 9 节全量验收结果见 [验证记录（本目录入口）](README.md#verification)。

## 开发配置与账号

仅 dev 加载 `db/devdata/R__020_auth_demo_accounts.sql`，在 010 租户种子之后由 Flyway 正常执行，无迁移回调；已执行旧名 R 的开发库先按 [种子升级说明（本目录入口）](README.md#deployment-dev-seed-upgrade) 完成一次性受控维护。生产没有首员工 bootstrap。本地 dev YAML 使用两把独立随机 Base64 JWT key，并显式配置：

```yaml
merchant:
  auth:
    namespace: dev
    sms:
      provider: mock
      expose-debug-code: true
```

两商户 `M_3C_DEMO`、`M_ISOLATION_TEST` 各有 `demo_staff`，手机号 `13800000301`，仅开发密码 `DevOnly-Auth-2026!`。数据库只存 cost=12 BCrypt。样板消费者为 `13800000401`、`13800000402`；对照商户也有 `13800000401`，各自取码登录。账号已存在时 dev seed 不覆盖凭证或状态；若开发者改过密码，以下脚本的初始密码不再适用，不能重跑 seed 重置。

test 正常 profile 只加载 nonprod 租户，测试账号由独立 `@Sql` fixture 提供，密码与这里不同。AuthDevManualIT 是显式模拟 dev 的专用例外：在全新 Testcontainers 上启用 devdata/mock，无真实短信、不写 gba-dev。

## 可运行步骤

推荐从仓库根运行隔离联调：

```powershell
mvn -f api/pom.xml '-Dtest=AuthDevManualIT' test
Get-Content api/target/auth-manual-verification.log
```

该测试启动动态端口 MySQL/Redis 和后端，实际调用下方 PowerShell 脚本，结束销毁容器；输出只含 PASS，不打印密码/验证码/token。它还从实际 Springdoc 导出 `api/target/auth-v1.openapi.yaml`，核验九操作及 dev seed 重跑不改密码/状态。

也可针对自行创建的隔离 dev 后端运行（替换端口；会创建合成账号，不要指向共享业务库）：

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File api/docs/auth/manual-verification.ps1 -BaseUrl http://127.0.0.1:8080
```

脚本顺序：两租户员工用户名/手机号登录 → dev debugCode 取码 → 消费者首次自动建号 200 → 验证码重放 400 → me/本人资源 → 非本人/跨租户 404 与消费者开通员工 403 → 员工开通 201 并独立密码登录 → refresh 带无效 access 仍成功 → 同 UUID 重试同双 token/absolute → 历史 refresh 退出/重复退出 → access/refresh 失效 → 其他设备和店铺仍可使用。使用随机合成号码和用户名重复运行；发送/登录超限应等待 Retry-After，不循环冲击限流。

员工密码通过模块外安全渠道交付，接口只回摘要，不返回密码/hash 或替同事登录；无找回密码、账号状态管理、RBAC 或生产首员工开通接口。

## 前端待接入验收

| 场景 | 期望 |
| --- | --- |
| A 登录→B→A | 按类型+店铺选择 localStorage；B 无会话私有功能要求 B 登录；保留 A，回 A 按需使用/刷新 |
| 同店多 tab | 同源锁共享在途 refreshRequestId，复用结果，不用不同 UUID 竞争同一个旧 refresh |
| 刷新/重开浏览器 | 在 original absolute 内按需 refresh；idle 过期可恢复，不把重开当重新凭证登录 |
| 绝对截止 | 消费者 30 天、员工 7 天后重新短信/密码验证，refresh 不延长截止 |
| 退出/新登录/切店时迟到响应 | 记录原分区与 epoch，退出广播并清理，丢弃旧 epoch 的 login/refresh 结果，不能复活已退出会话 |
| me/轮询/无人操作 | 不无条件定时 refresh；me 不 touch；后端不信客户端 active 标记 |
| localStorage | XSS 可窃取双 token，键分区不是安全隔离；生产 Cookie+CSRF/CORS 迁移独立设计 |
| 503 | 登录/刷新不保存任何失败结果；退出提示服务端撤销未确认，本地清理与重试策略明确 |

这些浏览器场景是接入验收清单，尚无前端执行证据。时间与竞争通过 AuthFlowIT 可控时钟/并发用例验证，不能把后端测试称为已完成浏览器联调。

## 运行证据

2026-11-06 定向认证回归：43 tests，0 failures、0 errors、0 skipped；命令见 [测试说明（本目录入口）](README.md#testing) 的认证段。本次实际运行包括下述集成测试和脚本，不等于第 9 节的全量验收。

AuthFlowIT 覆盖账号提交/故障、独立挑战并发唯一冲突、双 token、idle/absolute、加密幂等重试/重放、退出竞争、状态检查与 HTTP 权限。AuthSmsRedisIT 覆盖挑战并发一次消费、失败清理、限流和旧 Redis 序列化并存。HttpClientTest 回归旧路由/头/envelope 和新旧 token 交叉拒绝；AuthContextTest 验证正常/异常 finally 清上下文。JsonLoggingTest 读取真实 JSON 日志验证审计字段和分流。

报告与本次脚本输出只在 `api/target/`。第 9 节完整 `test`/`clean verify` 汇总见 [验证记录（本目录入口）](README.md#verification)；Docker 不可用应失败，不跳过或改连固定开发端口。
