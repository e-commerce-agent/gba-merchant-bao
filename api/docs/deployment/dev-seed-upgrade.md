# 开发种子数字前缀与一次性历史升级

正常运行使用 `R__010_local_test_tenants.sql`（dev/test）、`R__020_auth_demo_accounts.sql` 和 `R__030_chat_demo.sql`（后两者仅 dev），以及 dev 专用 R040 商品、R050 库存、R060 订单、R070 物流。V1–V5 与 R010/R020/R030 是已执行基线，内容和摘要不变。首次启动空库先执行所有 V，再按 R 的描述排序；locations 的排列不代表依赖。

租户和账号只插缺失记录，保留密码、停用状态、名称及其他人工资料。预期租户缺失、固定 ID/编码/用户名/手机号/商户归属冲突时，在写入前通过名为 `dev_seed_*` 的 NOT NULL 临时校验列报错，错误列名说明具体问题；临时表只存在于当前连接，不创建永久业务表或过程。不要用 INSERT IGNORE 或覆盖式 upsert 隐藏问题。

编号统一登记在 `src/main/resources/db/devdata/README.md`。V6–V9 按商品、库存、订单、物流排序；R040–R070 依次种入商品目录、库存、订单成交快照和物流。维护工具核对精确文件名和 Flyway description，并拒绝旧 V6–V9 布局或未知迁移；R050 只补缺失库存，不覆盖人工数量/version 或生成库存流水。R 仅首次或校验和变化时由 Flyway 执行，上游变化不触发未变化的下游，手工删数据后普通重启也不补回。若确需补种，停相关写入、备份、核验依赖和身份后显式执行对应 SQL，再核验；不要改 history 或给脚本加入时间戳强制每次执行。

## 适用边界

当前 V7 库存三表已统一为应用分配雪花主键，R050 显式固定 ID 及其身份 guard 见 [dev 数据登记](../../src/main/resources/db/devdata/README.md)。此修订只适用于尚未执行新 V7 的约定基线；如果目标已执行旧自增 V7，必须停止并另行评审向前迁移。本工具不能通过 repair/history/checksum 改写来适配这类结构差异，也不重编号已有库存。`align-commerce-module-integration` 第 6 节只做代码与隔离验证，第 7、8 节的综合验收与本地升级另行执行。

应用使用多个副本时，每个存活进程（含滚动发布重叠）分别设置不同的 `MERCHANT_ID_WORKER_ID`，实例内共用已有单例生成器；默认 0 仅适用于单实例 dev。重启复用 worker 前确认时钟已越过该 worker 最后使用的时间，保持既有 epoch。共享 MySQL 的自增同样支持多副本，雪花统一不代替集群验收、租户授权或业务幂等；详见 [库存主键与多副本运行](../inventory/README.md#主键与多副本运行)。

本次只升级根 `docker-compose.yml` 管理的 `gba-dev` MySQL 8.0.34 / `gba_dev` 库，保留 `gba-dev_mysql_data`、`gba-dev_redis_data` 命名卷。工具从实际容器读取库账号及 loopback 端口（默认 MySQL 3307，Redis 6380），不打印口令。根 `.env` 与实际 dev YAML 的连接应一致；改 .env 不会自动改变已有卷的数据库账号。

每个已执行旧 R 的开发库各操作一次，新开发者空库无需 repair。生产仍只加载 migration/common，应用使用 DML 账号、关闭 Flyway；独立迁移成功才启动新版应用，不执行本次开发种子 repair。生产滚动发布、Nginx、真实短信接入不属于此工具范围。

独立入口 `com.gba.merchantbao.maintenance.DevSeedUpgrade` 不被 Spring 自动扫描为组件。应用与工具统一 Flyway 11.7.2，工具固定完整 dev locations。数据库允许目标为 loopback 的 gba_dev；`gba_seed_it_<hex>` 仅用于隔离验证。其他目标、未知缺失、失败、V 校验和变化、未知待迁移或新 R 已改写均阻断。

DevSeedUpgrade 按精确 V1–V9 脚本名和 R010–R070 repeatable 白名单检查布局，允许受支持的 V3/V4/V5 前缀逐级补齐当前 V9 结构。它只对已登记旧 R 执行显式受控 repair，不自动修复，也不在应用常规启动时补种。已应用的旧 V6–V9 脚本名与当前布局不同，工具会拒绝；其他数据库不属于本次兼容范围。历史旧 SQL 测试固定 target=4，避免把未来表结构当成历史快照。

## 人工维护清单

1. 核对根 Compose 的项目/服务/镜像/命名卷及目标库；执行 `docker compose up -d --wait`，不删除或重建数据卷。
2. 安排维护窗口，停止所有连接同库的 IDEA/AI/其他后端、数据库写入和迁移任务，关闭数据库客户端连接。命令会拒绝可见的其他同库连接；这不替代人工确认，多个数据库用户应由管理员检查 processlist。保持停写直到 Execute 和验收完成。
3. 在仓库根执行默认 Check。使用新的、位于被忽略 backups/ 下的独立报告目录；工具不会覆盖已有检查证据。Windows 例：

   ```powershell
   ./api/docs/deployment/dev-seed-upgrade.ps1 -ReportDirectory "A:/work/project/ElectricBusiness/backups/dev-seed-实际日期时间"
   ```

   若 docker 不在 PATH，显式追加 `-DockerExecutable '<实际 docker.exe 路径>'`。脚本编译维护入口并构建依赖 classpath，不运行应用或完整测试；它只删除 target/classes 下列明的旧 R/V 资源及认证旧生成类，避免 Maven 保留改名资源。不会执行 Maven clean。

4. Check 核对 history 后，导出 `before.sql`（结构＋数据＋routines/events/triggers＋history），在新建、随机端口、无真实卷的临时 MySQL 恢复，逐表核对全部原行的 SHA-256 与完整迁移历史。只清理带专用 label 的本次临时容器及其匿名卷。核对以下文件，不外传实际备份：

   | 文件 | 用途 |
   | --- | --- |
| review.json | 目标库、旧 R 退役白名单、七份新 R Pending 集合、pendingVersions（空或 V4–V9 内的连续剩余后缀） |
   | history-before.json | 实际旧迁移历史，不含连接凭据 |
   | rows-before.json | 每表每主键与整行摘要，不写原始行值 |
   | before.sql | 完整备份，含凭证摘要及业务隐私，仅保存在忽略目录 |
   | restore-evidence.json | 该备份 SHA-256、随机隔离恢复库及验证时间 |

5. 人工 Review：V1 起连续 Success，至少到 V3，最高到当前 V9；pendingVersions 必须精确列出剩余的 V4–V9 连续后缀及新脚本名。旧名租户 R 必须有真实历史，账号旧 R 可有可无；实际旧名 R Missing、全部七份新 R010–R070 Pending。V3 分支不能把账号旧 R 已执行却表不存在等异常当正常。新空库、仅有正常数字编号 R 的 V5 dev 库不使用此旧名 R 工具，按正常 migrate/validate 升级。已执行旧 V6–V9 布局或存在未知差异时停止调查，不能扩大忽略规则或直接 repair。
6. 报告和恢复验证通过后，仍在停写窗口显式执行：

   ```powershell
   ./api/docs/deployment/dev-seed-upgrade.ps1 -Mode Execute -MaintenanceConfirmed -ReportDirectory "A:/work/project/ElectricBusiness/backups/dev-seed-实际日期时间"
   ```

   Execute 重新检查目标、连接、迁移集合、已存在业务行以及备份 hash 对应的恢复证据。repair 仅针对已核验旧 R，校验没有额外对齐或失败清理；再 migrate（精确执行 pendingVersions 后缀，再执行七份 R010–R070）、validate，并逐行确认已有数据未改写。V3 基线为六份 V 加七份 R，共十三项；V4 基线为五份 V 加七份 R，共十二项。旧历史保留，追加 DELETE 退役标记，新名按真实执行登记。Deleted 不删除业务数据；新 R 必须真正执行，不能伪造为已应用。
7. 查看 `history-after.json`、`result.json`（备份 SHA-256、完成时间和保留结果）；核对租户/账号及人工数据。完成后不混跑旧迁移包。工具再次 Check 显示 complete；无差异重入 Execute 不重复 repair，但仍需对应检查及备份证据。
8. IDEA 激活 dev 或 AI 在 api 下执行 `mvn spring-boot:run '-Dspring-boot.run.profiles=dev'`。确认新 API 就绪、演示账号登录正常，再重启一次确认无新增种子执行或人工数据变化。账号曾改密码/停用时按现状验证，不强制重置。

## 失败处理与证据

失败立即停止，不启动旧迁移包或继续发布；保留报告、完整 SQL 和日志。禁止直接 UPDATE/DELETE history、clean、删卷、永久关闭 validate 或配置忽略 Missing。repair 成功但 migrate 失败时可能已有新 history，先调查实际语句和数据；不能把重新 repair 当恢复。使用完整备份先在隔离实例恢复核验，按维护评审选择向前修复或受控恢复，见 [备份恢复](backup-recovery.md)。

隔离验收运行：

```powershell
mvn -f api/pom.xml clean verify '-Dtest=MigrationScriptContractTest' '-Dit.test=DevSeedMigrationIT'
```

覆盖空库顺序、旧租户 R/两份旧 R 两个升级分支、历史保留与 Deleted、账号密码/状态/人工修改、显式补种、缺依赖/身份冲突、未知迁移/失败/校验和拦截、备份恢复、CLI 确认门槛和并发独立 Flyway。测试只连接自己的随机端口容器，不操作 gba-dev 卷；Docker 不可用失败，报告仅在 target。实际开发库维护记录单独留在 backups，并将无秘密的结果摘要记录到本说明关联的交付记录。

## 2026-11-06 验证记录

以上定向命令实际运行 33 tests，0 failures、0 errors、0 skipped，BUILD SUCCESS；日志为 `api/target/seed-targeted-verification.log`。其中 DevSeedMigrationIT 15 项、AuthDevManualIT 1 项，使用可销毁实例，真实备份恢复及 CLI 升级已在隔离库验证。该定向阶段尚未执行 tasks 第 9 节的全量验收；后续结果见 [认证验收记录](../auth/verification.md)。未执行生产发布。

用户确认采用 B 后，根 Compose 的 `gba-dev` / `gba_dev` 在 18:25–18:27 维护窗口执行了精确 V3 分支。MySQL 8.0.34 与 Redis 均来自现存命名卷；执行时停止后端、无其他同库连接，未重建卷。升级前库 history 为 V1/V2/V3 成功和旧租户 R 成功，两个租户存在；旧业务表没有行，故未发现其他人工业务数据。

完整备份位于忽略目录 `backups/dev-seed-v3-upgrade-20261106-1825/before.sql`，SHA-256 为 `886cdbfbc7b2a45c09ec4f8d35d8aeccea3177b3f618eccd9eac06b49ba706e6`。备份恢复到本次随机命名的临时 MySQL；所有表行摘要和完整旧 history 均匹配，证据在同目录 `restore-evidence.json`。隔离测试 22 项通过：0 failures、0 errors、0 skipped，含 V3→V4→010→020 分支及备份恢复/CLI。

真实执行在 `api/target/seed-real-execute.log` 和忽略的报告目录留有记录。原 `R__local_test_tenants.sql` 成功行保留，Flyway 追加同名 `DELETE` 退役记录；其后 V4、`R__010_local_test_tenants.sql`、`R__020_auth_demo_accounts.sql` 成功，`validate` 通过。history 共 8 行（7 SQL 成功、1 DELETE）；V1–V4 文件未修改。升级后数据库为 2 个租户、2 个员工、3 个消费者。

dev profile 两次成功启动，均校验 8 条 history 记录且显示无待执行迁移。两商户 `M_3C_DEMO` 和 `M_ISOLATION_TEST` 的员工均通过密码登录、`me` 和退出；health 返回 UP，成功 code 为 0。两次启动及登录前后完整业务行摘要和 Flyway history 均一致，报告为 `runtime-result.json`。临时后端进程已关闭，8080 已释放。本维护窗口未执行第 9 节全量验收或生产操作；后续 `clean verify` 结果见 [认证验收记录](../auth/verification.md)。
