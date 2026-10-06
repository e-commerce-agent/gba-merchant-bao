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

在本机临时 MySQL 库 `electricbusiness_flyway_empty` 上顺序执行 V1、V2、V3 和 `R__local_test_tenants.sql`：

- 结构表 12 张（旧库 11 张加 `merchant`）。
- `merchant` 恰好 2 行，业务编码为 `M_3C_DEMO`、`M_ISOLATION_TEST`。
- 两行状态均为 `ACTIVE`，币种为 `CNY`。
- 演练库已删除，原 `sky_take_out` 未执行写操作。
