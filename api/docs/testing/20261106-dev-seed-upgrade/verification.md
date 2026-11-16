原记录所指的定向命令与维护步骤见 [本目录 README](README.md#reproduction)。

## 2026-11-06 验证记录

以上定向命令实际运行 33 tests，0 failures、0 errors、0 skipped，BUILD SUCCESS；日志为 `api/target/seed-targeted-verification.log`。其中 DevSeedMigrationIT 15 项、AuthDevManualIT 1 项，使用可销毁实例，真实备份恢复及 CLI 升级已在隔离库验证。该定向阶段尚未执行 tasks 第 9 节的全量验收；后续结果见 [认证验收记录（本目录入口）](README.md#auth-verification)。未执行生产发布。

用户确认采用 B 后，根 Compose 的 `gba-dev` / `gba_dev` 在 18:25–18:27 维护窗口执行了精确 V3 分支。MySQL 8.0.34 与 Redis 均来自现存命名卷；执行时停止后端、无其他同库连接，未重建卷。升级前库 history 为 V1/V2/V3 成功和旧租户 R 成功，两个租户存在；旧业务表没有行，故未发现其他人工业务数据。

完整备份位于忽略目录 `backups/dev-seed-v3-upgrade-20261106-1825/before.sql`，SHA-256 为 `886cdbfbc7b2a45c09ec4f8d35d8aeccea3177b3f618eccd9eac06b49ba706e6`。备份恢复到本次随机命名的临时 MySQL；所有表行摘要和完整旧 history 均匹配，证据在同目录 `restore-evidence.json`。隔离测试 22 项通过：0 failures、0 errors、0 skipped，含 V3→V4→010→020 分支及备份恢复/CLI。

真实执行在 `api/target/seed-real-execute.log` 和忽略的报告目录留有记录。原 `R__local_test_tenants.sql` 成功行保留，Flyway 追加同名 `DELETE` 退役记录；其后 V4、`R__010_local_test_tenants.sql`、`R__020_auth_demo_accounts.sql` 成功，`validate` 通过。history 共 8 行（7 SQL 成功、1 DELETE）；V1–V4 文件未修改。升级后数据库为 2 个租户、2 个员工、3 个消费者。

dev profile 两次成功启动，均校验 8 条 history 记录且显示无待执行迁移。两商户 `M_3C_DEMO` 和 `M_ISOLATION_TEST` 的员工均通过密码登录、`me` 和退出；health 返回 UP，成功 code 为 0。两次启动及登录前后完整业务行摘要和 Flyway history 均一致，报告为 `runtime-result.json`。临时后端进程已关闭，8080 已释放。本维护窗口未执行第 9 节全量验收或生产操作；后续 `clean verify` 结果见 [认证验收记录（本目录入口）](README.md#auth-verification)。
