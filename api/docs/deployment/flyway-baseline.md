# Flyway 空库初始化与旧库接管

日常 dev、所有新开发者和常规集成测试从空库执行 V1→V9，不执行 baseline。
默认 baseline-version=1 只是显式 baseline 操作的版本参数；baseline-on-migrate=false 时空库不会跳过 V1。
所有执行方保持 validate-on-migrate=true、clean-disabled=true、out-of-order=false，已应用 V1–V5 不可改写。

V1 是11张旧餐饮表的结构快照，无数据；V2 增加 merchant 租户根；V3 增加其 currency_code。
V4 仅新增 consumer_account / merchant_staff_account 结构与约束，已登记 V3 的库只追加 V4；旧账号不迁入，账号数据分层见 [认证数据设计](../auth/data-design.md)。V5 会话结构和 R030 会话种子已在 dev 基线中执行并冻结。
V2/V3 不会替旧业务表补 merchant_id，不会把旧餐饮数据转换成新商贸数据。租户根种子与后续业务数据有单独层次。

V5 之后当前 dev V5 基线的新增结构按依赖排序：V6 商品目录、V7 库存、V8 只读订单、V9 物流。V6–V9 仅结构 DDL，跨域复合外键约束库存/SKU、订单项/商品 SKU、运单/订单及轨迹/运单的同商户关系。R040–R070 依次提供商品、库存、订单、物流演示数据；编号、稳定 ID 和重跑边界见 [dev 演示数据登记](../../src/main/resources/db/devdata/README.md)。当前 change 只支持本地尚在 V5 的 dev 库与新库，不兼容已执行旧 V6–V9 顺序的其他库；维护工具会拒绝旧布局。

## 仅接管已核验旧库

1. 确认实例、库名、负责人和停写窗口，先 [备份结构＋数据](backup-recovery.md)，在隔离库恢复验证。
2. 另建参考空库，仅执行仓库批准的 V1。用 SHOW CREATE TABLE 与 information_schema 逐项核对目标和参考：表名、列顺序/类型/长度、默认值、NULL、主键、索引/唯一约束、外键、字符集/排序规则、引擎及 MySQL 版本兼容性。AUTO_INCREMENT 当前值等运行状态单独记录，不误判为模型差异。
3. 目标不得存在 flyway_schema_history 或 merchant；有缺表、多表、字段/索引/约束差异时立即停止并记录差异，不能因为“差不多”就 baseline。
4. 保留核验记录及旧表行数/代表性主键，在恢复出的隔离演练库先执行一次显式 baseline-1，然后 migrate，核对旧行及新结构。
5. 演练通过才允许在停写目标库重复经审核的操作。普通应用启动应拒绝无 history 的非空库。

## 显式命令示例

独立 CLI 与项目一致，版本11.7.2。准备一个被忽略的 api/.runtime/flyway-legacy.env，填入已核验演练库的实际连接：

```dotenv
FLYWAY_URL=jdbc:mysql://legacy-mysql:3306/legacy_rehearsal?allowPublicKeyRetrieval=true&useSSL=false
FLYWAY_USER=REPLACE_WITH_VERIFIED_OPERATOR
FLYWAY_PASSWORD=REPLACE_WITH_OPERATOR_PASSWORD
FLYWAY_LOCATIONS=filesystem:/flyway/sql
FLYWAY_BASELINE_VERSION=1
FLYWAY_BASELINE_ON_MIGRATE=false
FLYWAY_VALIDATE_ON_MIGRATE=true
FLYWAY_CLEAN_DISABLED=true
FLYWAY_OUT_OF_ORDER=false
```

本地隔离演练实例若在 gba-dev_default 网络上，且网络别名为 legacy-mysql，仓库根 PowerShell 执行。这里的 legacy-mysql 必须指向单独创建的演练容器，不能替换成 dev Compose 的 mysql 服务：

```powershell
$migration = (Resolve-Path api/src/main/resources/db/migration).Path
docker run --rm --network gba-dev_default --env-file api/.runtime/flyway-legacy.env --mount "type=bind,source=$migration,target=/flyway/sql,readonly" flyway/flyway:11.7.2 -baselineVersion=1 baseline
if ($LASTEXITCODE -ne 0) { throw 'baseline 失败，停止' }
docker run --rm --network gba-dev_default --env-file api/.runtime/flyway-legacy.env --mount "type=bind,source=$migration,target=/flyway/sql,readonly" flyway/flyway:11.7.2 migrate
if ($LASTEXITCODE -ne 0) { throw '迁移失败，停止' }
docker run --rm --network gba-dev_default --env-file api/.runtime/flyway-legacy.env --mount "type=bind,source=$migration,target=/flyway/sql,readonly" flyway/flyway:11.7.2 validate
if ($LASTEXITCODE -ne 0) { throw '校验失败，停止' }
```

其他环境按核验的地址/网络替换，不照抄目标库。baseline 会向 flyway_schema_history 写入 type=BASELINE、version=1，表示旧结构已存在，不执行 V1。
migrate 随后执行 V2–V9。核对 history 中 BASELINE1、SQL2–9 均成功，租户、认证、会话及商品/库存/订单/物流结构存在，旧表行数和代表性行不变。
空库 history 应是 SQL1–9，而不是 BASELINE1。后续日常流程只 validate/migrate，不再 baseline。
失败时按备份恢复或向前修复处理，绝不能通过 clean、删卷、repair 来掩盖差异。

FlywayMigrationIT 保留了 V1 构造旧结构→普通 migrate 拒绝→显式 baseline1→追加迁移→旧行保留的测试。本 change 第 6 组将同步其中遗留的 V8 断言并扩展冻结 V5 升级路径；当前第 1、2 组定向验证不包含该类，也不替代真实旧库人工结构核验。
