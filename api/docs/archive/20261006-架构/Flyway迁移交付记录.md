# Flyway 迁移交付记录

## 版本清单

| 版本 | 类型 | 内容 | 数据影响 |
| --- | --- | --- | --- |
| V1 | 基线/兼容 | 从已核对的旧库结构建立 11 张 legacy 表 | 只建空表，不复制演示数据，不删除旧表 |
| V2 | 新增 | `merchant` 租户根表、唯一业务编码和状态索引 | 新增结构，不触碰旧餐饮表 |
| V3 | 修改/兼容 | 给 `merchant` 增加 `currency_code`，默认 `CNY` | 可为空间兼容旧租户，现有行使用默认值 |

当前没有退役表，也没有旧字段到新业务表的隐式重命名。商品、SKU、订单、物流、知识和会话映射在对应技术模块的 change 中追加。

## 运行位置

- `classpath:db/migration` 是所有环境的结构迁移目录。
- `classpath:db/devdata` 只由 `dev`、`local`、`test` profile 追加加载，当前只包含 `M_3C_DEMO` 和 `M_ISOLATION_TEST` 的幂等种子。
- 生产 profile 只加载结构迁移，不加载 `db/devdata`，也不创建账号、商品、订单或评测数据。

## 旧库接管

已有非空数据库必须先完成只读备份、`SHOW CREATE TABLE`/索引核对，并在演练库执行一次性 baseline。因为 `baseline-on-migrate=false`，没有 `flyway_schema_history` 的非空库会被拒绝，防止把未知结构误当作 V1。结构不匹配时停止发布，保存 Flyway 校验错误和差异，不执行自动修复。

演练命令记录：

```text
mysqldump --single-transaction --routines --triggers --events sky_take_out > legacy-before-migration.sql
mysql electricbusiness_migration_check < legacy-before-migration.sql
flyway -url=jdbc:mysql://localhost:3306/electricbusiness_migration_check -user="$DB_USER" -password="$DB_PASSWORD" baseline
```

实际发布使用 Flyway 提供的 `baseline` 命令或受控运维脚本创建 history 记录，不能手工伪造已执行版本。baseline 前后核对旧表数量、主键、唯一索引、行数和应用读写回归。

## 校验和回退

V1/V2/V3 一旦执行不得修改；修复只能追加 V4+。发布前记录 `flyway_schema_history` 的版本、checksum、执行时长和每张表行数。失败时停止应用新包，保留旧应用和数据库，优先恢复迁移前备份或执行已评审的降级 SQL；不执行“down migration”删除生产数据。

当前文件 SHA-256：

```text
V1__legacy_schema_baseline.sql       694EE44ECC087FA5246AB4A11BD7A9A9469675C5A3F7A10D7C23866ACE573107
V2__merchant_tenant_root.sql         4A935FE633DE73BD9F9E7DFD41015A13ECDBF146B0C5DA6F228C1AB3428E7B49
V3__merchant_currency_compatibility.sql BF1AA83D261BDC70B3BD16F4B032B07A03BA1F3EAF6ECABEFEED77E76AB54567
```

重复执行已应用版本必须由 Flyway checksum 校验拦截；追加版本按 V1、V2、V3 顺序执行。固定租户种子使用业务编码幂等写入，测试通过 `merchant_code` 查询主键，不把数字 ID 当作外部契约。

## 本次演练结果

以下本机 MySQL 演练为本次调整前的历史记录。后续自动化验收改用 Docker Desktop/Testcontainers，不再连接本机 MySQL。

在本机临时 MySQL 库 `electricbusiness_flyway_empty` 上顺序执行 V1、V2、V3 和 `R__local_test_tenants.sql`：

- 结构表 12 张（旧库 11 张加 `merchant`）。
- `merchant` 恰好 2 行，业务编码为 `M_3C_DEMO`、`M_ISOLATION_TEST`。
- 两行状态均为 `ACTIVE`，币种为 `CNY`。
- 演练库已删除，原 `sky_take_out` 未执行写操作。

## 容器自动化复验（2026-10-06）

`mvn -f api/pom.xml clean verify` 成功。MySQL 镜像 `mysql:8.0.34`、Redis 镜像 `redis:7.4-alpine`、Testcontainers `1.20.6`，Docker Desktop 引擎 `29.8.0`。连接由随机映射端口注入，测试结束后容器自动清理；本次未访问宿主机数据库。

- 快速单元/契约测试：12 个，失败 0、错误 0、跳过 0。
- MySQL/Flyway 集成测试：3 个，覆盖空库 V1/V2/V3、重复迁移、幂等租户种子、非空旧结构拒绝自动 baseline、显式 baseline 后升级且保留旧测试行。
- Redis 集成测试：6 个，覆盖旧 Redis 配置下字符串及 TTL/条件写入、Hash、List、Set、Sorted Set 和 key 类型/删除。
- 应用集成测试：2 个，真实启动应用、加载 V1/V2/V3 和租户种子、连接容器 Redis、访问健康接口和 OpenAPI YAML。
- 合计 23 个，失败 0、错误 0、跳过 0。报告位于 `api/target/surefire-reports` 和 `api/target/failsafe-reports`。

这里的旧库升级用例使用从 V1 构建的合成旧结构及测试行，不替代生产发布前对真实旧库执行备份、结构对比和恢复演练。已发布 SQL 文件未修改，原 checksum 契约测试通过。

## 目录治理和容器隔离复验（2026-10-06）

文档位置与日志配置调整后再次执行 `mvn -f api/pom.xml clean verify` 成功：快速测试 12 个、集成测试 11 个，合计 23 个，失败/错误/跳过均为 0。

- 测试不调用开发/生产 Compose，不固定宿主端口，不绑定业务数据目录/命名卷/网络，四个测试容器声明均显式 `.withReuse(false)`。开发固定端口留给后续实际引入中间件的模块 change。
- Maven 实际日志文件为 `api/target/test-logs/application.log`，同时包含 `HttpClientTest` 和 `FoundationApplicationIT` 的日志；`api/logs` 未重新生成。测试结束后未残留运行中的 Testcontainers 容器。
- 包结构说明移至 `api/docs/architecture/packages.md`，测试文档移至 `api/docs/testing/README.md`；后端根 README 提供导航，原根目录专题文件不再存在。
- 空 main/test 旧包目录和三个退役模块目录均已物理删除。独有 dev 配置及两个测试草稿保留于 Git 忽略的 `api/.runtime/legacy-backup`，当前活动 dev 配置未删除。
- 历史日志保留于 `api/.runtime/logs`；开发运行以 `api/` 为工作目录，生产通过 `LOG_DIR` 设置外部日志目录。
- 本次未执行 Git 暂存、commit、push 或远程 PR 操作，由作者手动交付。
