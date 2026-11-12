# 电商模块整合验收

变更：`align-commerce-module-integration`。本记录对应任务第 7、8 节；前置实现记录见各模块文档。
本次不修改前端或其他变更的完成状态。

## 隔离验收

- `MigrationScriptContractTest`：冻结 V1–V5、R010/020/030 的 SHA-256，迁移版本唯一且只包含 DDL。
- `FlywayMigrationIT`：空库/V3 升级到 V9，按实际 test/prod YAML locations 初始化时无电商演示数据，库存三表无自增；重复 migrate 和 validate 保留数据与 history。
- `DevSeedMigrationIT.frozenV5UpgradeMatchesFreshSchemaAndPreservesAccountsChatAndHistory`：从仅包含冻结 V1–V5/R010/020/030 的资源副本建立 V5，修改账号及会话人工值后升级到 V9；旧行与完整旧 history 保留，商品/库存/订单/物流关联及库存固定 ID 正确，最终列结构与空库一致。修改库存后重复 migrate 为零，全部业务摘要及 history 不变。
- `CommerceIntegrationIT`：真实 Spring/MySQL/Redis，关闭循环依赖许可；同一上下文执行商品到库存只读、库存到商品引用、物流到订单授权，预热缓存后的库存变化即时反映，当前目录变化不改历史成交快照，两商户及同商户他人订单隔离成立。

迁移定向验证：6 项契约测试、40 项迁移/种子集成测试通过，0 failures/errors/skipped；日志
`api/.runtime/commerce-migration-verify.log`。跨模块与库存导出定向测试 2 项通过，日志
`api/.runtime/commerce-cross-module.log`。

2026-11-11 23:41（Asia/Shanghai）最终命令
`mvn -f api/pom.xml clean verify '-DforkCount=4'` BUILD SUCCESS，耗时 9:38。
Surefire 109 项、Failsafe 182 项，共 291 项，0 failures/errors/skipped。
日志 `api/.runtime/commerce-complete-verify.log`，明细为 `api/target/{surefire,failsafe}-reports/`。
迁移测试和综合用例分别包含 6 项 Flyway、36 项种子维护、2 项跨模块/库存契约测试；
库存 Flow 23 项、物流 SOAP 10 项均通过。

修复全量回归中的遗留 V8 断言：`FlywayMigrationIT`、`ChatSchemaIT`、
`FoundationApplicationIT`、`ProductionInfrastructureIT` 统一当前 V9。
为文档导出增加 `inventory-v1` 分组，并补齐全局 Springdoc 的库存/订单控制器扫描；
综合测试核对六个库存操作、Bearer、错误响应与模型。业务路由和权限保持既有契约。

JAR 内 SQL 清单与源码逐项一致：V1–V9 共 9 份，R010–R070 共 7 份，无旧编号/旧名资源，
实际 `application-dev.yml` 不进入 JAR。完整清单留在备份报告目录 `packaged-migrations.json`。

## 本地开发库

操作步骤、停止条件和恢复指引见 [local-development.md](../deployment/local-development.md#v5-开发库升级到商品库存订单物流-v9)。
实际备份、逐行摘要及含运行环境信息的日志存于忽略 Git 的 `backups/` 和 `api/.runtime/`。
API 快照目录沿用 `docs/api/`，来源必须是本地 dev 后端实际 Springdoc 分组。

2026-11-11 23:00–23:03（Asia/Shanghai）前置核对完成：应用端口 8080 无监听；Compose
`gba-dev` 的 MySQL 8.0.34 / Redis 健康；实际应用目标为 `dev_admin@127.0.0.1:3307/gba_dev`。
最高 V5，冻结 V1–V5/R010/020/030 的 Flyway 校验通过，无其他同库连接，无商品/库存/订单/物流表，
待执行版本精确为 V6/V7/V8/V9。只读核对未改写 history 或业务数据。

完整备份：`backups/commerce-v5-upgrade-20261111/before.sql`，SHA-256
`604B7FC6994D586D43D1BEAE370F7B1E3867D6DF592324D0F202C607D2BA0EFB`。
已恢复到独立随机端口的 MySQL 8.0.34（无开发卷），核对全部业务行 SHA-256、全部表 DDL、完整
history 一致；证据为同目录 `before-rows.json`、`before-schema.json`、`before-history.json`、
`restore-result.json`、`restore-evidence.json`。专用恢复容器已清理，开发卷保留。

### 本地启动失败与暂停状态

2026-11-11 23:43（Asia/Shanghai）通过 IDEA `MerchantApplication` 启动 clean 后端，使用
JDK 21、工作目录 `api`、dev profile 和 `MERCHANT_ID_WORKER_ID=0`。
V6/V7/V8/V9 均成功应用；随后 `R__040_product_demo.sql` 第 130 行插入分类时触发
MySQL 1452，外键 `product_category.fk_product_category_parent` 校验失败。
Flyway history 保留该 repeatable 的 `success=0` 记录，后端启动失败并退出；
已确认无活动调试会话，8080 无监听。8.2 的启动及健康验收未通过。

只读核对确认原 V5 全部业务行摘要保持不变，`product_category` 为 0 行。
完整启动日志保存为备份目录内 `idea-startup-failed.log`；失败状态证据为
`failed-startup-rows.json`、`failed-startup-history.json`、`failed-startup-schema.json`，
摘要为 `api/.runtime/commerce-dev-failure-state.log`。原 V5 备份及隔离恢复核验结果保留。
尚未执行 repair、改写 history/checksum、恢复开发库、删除开发卷或再次启动后端。

待验证的原因：分类 INSERT 在同一条 `INSERT ... SELECT ... UNION ALL ... JOIN merchant`
中同时插入父分类和子分类，未明确保证父分类先于子分类；当前仅为假设，尚未修复或确认。
隔离测试通过不能替代此次真实开发库失败的验收结果。

当前 change 为 39/42 项完成：7.1–7.4、8.1 完成，8.2–8.4 保持未完成。
四份 API 文档尚未从 dev 后端导出，双租户联调及二次启动验收尚未执行。
后续应先在隔离恢复副本定位并验证修复，再评审真实开发库的恢复或续迁方案；
依照升级停止条件及 apply 工作流暂停要求，等待用户指导后再进行开发库恢复操作。

### 2026-11-12 隔离复现、修复与恢复演练

本轮用户授权为隔离副本定位修复及提交开发库恢复方案。未启动真实 dev 后端或操作开发库。
使用独立 MySQL 8.0.34 容器和随机宿主端口，从原 `before.sql` 恢复；原备份 SHA-256 再次核对一致。

复现命令为 `api/.runtime/commerce-category-isolated.ps1 upgrade`，完整迁移在 R040 第 130 行
重现 MySQL 1452 / `fk_product_category_parent`；单独 `category` 重放同样失败。
最小回归条件为冻结结构、R010 两租户、`ANALYZE TABLE merchant` 后执行 R040。
只读 EXPLAIN 显示 `Using join buffer (hash join)`，实际 SELECT 首行是子分类，父分类在后。
这确认 JOIN 输出顺序导致即时外键检查失败；数据库中父分类原本不存在符合首次初始化预期。

新增回归测试
`DevSeedMigrationIT.productSeedCreatesParentCategoriesBeforeChildrenWithRefreshedMerchantStatistics`：
原 SQL 产生同一外键错误，修复后通过；验证五个分类、同租户父子关系及显式重跑保留人工排序/状态。
红、绿阶段日志为 `api/.runtime/commerce-category-{red,green}.log`。
修复仅将 R040 分类 INSERT 拆为先根后子两条语句，保留所有 guard、ID 和补缺语义；
未改 V1–V9 或 R010–R030，未关闭外键或增加启动 repair。

修复后先在同一失败副本显式执行 R040 成功；再仅重建隔离库并重新导入原始 V5 备份，
核对全部原业务行、完整 history、各表 DDL 均与原报告一致后执行完整迁移：
V6/V7/V8/V9、R040/R050/R060/R070 共 8 项成功到 V9。
原业务行及完整原 history 保留；分类 5（无孤儿）、商品 6、SKU 9、库存 8、订单 7、
订单项 8、运单 3、轨迹 6，库存流水和幂等记录均为 0。
Flyway validate 通过，再次 migrate 为 0，全部业务行摘要和迁移 history 不变。
该 migrate 重跑检查尚不代替真实应用二次启动验收。

隔离证据保存于 `backups/commerce-category-repro-20261112/`：
`original-upgrade-failed.log`、`original-category-failed.log`、`original-plan.log`、
`original-R__040_product_demo.sql`、`upgrade.log`、`verify.log`、`fixed-rows.json`、`fixed-history.json`。
原 V5 备份及昨日真实库失败证据均保留。

2026-11-12 08:10（Asia/Shanghai）定向 clean 回归 BUILD SUCCESS，耗时 5:44：

```powershell
mvn -f api/pom.xml clean verify '-Dtest=MigrationScriptContractTest' '-Dit.test=DevSeedMigrationIT,FlywayMigrationIT,CommerceIntegrationIT' '-DforkCount=3'
```

迁移契约 6、种子 37、Flyway 6、跨模块 2，共 51 项，0 failures/errors/skipped。
日志 `api/.runtime/commerce-category-regression.log`。本轮为上述定向回归；
昨日 291 项全量验收结果另见前文，不将其表述为修复后的全量运行。
`openspec validate align-commerce-module-integration --strict` 通过。
隔离演练专用容器及其匿名卷已清理，开发卷保留；汇总证据为同目录 `summary.json`。

待确认执行步骤见 [本地电商开发库恢复方案](../deployment/commerce-dev-recovery.md)。
建议先保留并隔离核验当前失败现场，再完整恢复已核验的 V5 快照并正常升级。
真实库未恢复；8.2–8.4 仍未完成，change 进度保持 39/42。

### 2026-11-12 授权恢复与实际 dev 启动

用户确认执行恢复方案并继续 8.2–8.4。恢复前确认无后端会话和 8080 监听，
真实库的全部业务行、完整 history、表 DDL 精确等于昨日失败证据，无其他数据库连接。
新增失败现场完整备份 `backups/commerce-dev-recovery-20261112/failed-state.sql`，SHA-256：
`190107ABC34BA7607A9E5C129B4814CEECD09EFF0EAC86679111C7B1F265C335`。
该备份在独立 MySQL 8.0.34 随机端口容器恢复后，全部业务行、完整 history、表 DDL 一致；
证据为同目录 `current-{rows,history,schema}.json`、`safety-backup-evidence.json`、
`failed-state-backup-verified.json`，专用恢复容器及其匿名卷已清理。

仅重建目标开发 MySQL 中的 `gba_dev` 并导入已核验的原 V5 完整快照；开发卷及其他数据库保留。
恢复后、启动前核对全部业务行、完整 history、表 DDL 与原 V5 报告相同。
没有执行 repair 或直接改写 history/checksum。`mvn -f api/pom.xml clean package '-DskipTests'`
成功；本轮构建复用前述已通过的 51 项定向回归结果，不把跳过测试的打包视为测试通过。
JAR 中 16 份 SQL 与源码逐字节相同，无实际 dev YAML、无旧迁移资源。

2026-11-12 08:16（Asia/Shanghai）IDEA `MerchantApplication` 使用 Java 21.0.12.1、工作目录 `api`、
dev profile、workerId=0 启动；本次联调显式使用 mock SMS 并开启调试码，不持久化该启动覆盖。
`dev_admin` 正常执行 V6/V7/V8/V9、R040/R050/R060/R070，共 8 项，成功到 V9，应用启动耗时 7.052 秒。
实际 `/api/v1/health` 返回 HTTP 200、code=0、status=UP。
独立 Flyway validate 通过，原 V5 全部业务行和完整原 history 保留，库存三表 id 无默认值、无自增。
任务 8.2 完成。日志和结果为同目录 `idea-startup.log`、`health-first.json`、
`after-{rows,history}.json`、`upgrade-result.json`、`packaged-migrations.json`；
构建日志为 `api/.runtime/commerce-recovery-build.log`。

实际导出时发现全局 Springdoc 扫描与模块路径筛选组合后，商品分组混入其他模块，
订单分组包含物流；收紧商品分组到目录资源路径、订单分组到订单列表/详情两个路径。
`CommerceIntegrationIT` 新增四组精确路径隔离断言并保留库存六操作、Bearer 和模型校验。
`clean verify -Dtest=NoTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CommerceIntegrationIT`
成功，2 项集成测试，0 failures/errors/skipped；日志 `api/.runtime/commerce-export-verify.log`。
重新通过 IDEA 启动后从实际 dev 分组导出：

| 文件（仓库根 `docs/api/`） | 来源 | 路径数 |
| --- | --- | --- |
| `product-v1.openapi.yaml` | `/v3/api-docs.yaml/product-v1` | 13 |
| `inventory-v1.openapi.yaml` | `/v3/api-docs.yaml/inventory-v1` | 6 |
| `order-v1.openapi.yaml` | `/v3/api-docs.yaml/order-v1` | 2 |
| `shipment-v1.openapi.yaml` | `/v3/api-docs.yaml/shipment-v1` | 1 |

来源基址 `http://localhost:8080`；分别核对 JSON/YAML paths/components 一致。
商品新店铺路径和公开 SKU 的布尔 inStock、无精确数量通过；库存、订单、物流 Bearer 标记及
路由范围通过。物流 SOAP 保留独立 WSDL 契约，不并入 REST 快照。
导出结果见 `backups/commerce-dev-recovery-20261112/api-export-result.json`，任务 8.3 完成。

2026-11-12 08:25（Asia/Shanghai）实际 dev 双租户 HTTP 联调通过：

- 两商户员工和三位既有消费者正常登录，不重置账号；测试会话全部登出。
- 商品 SKU301 预热目录缓存后，可售量 20→0→20 即时改变公开 inStock；原可售和预占数量恢复。
  隔离租户 SKU309 有货，公开响应不含精确数量。
- SKU308 首次正向调整从缺行创建库存，成功幂等重放不增加库存/流水，随后清零。
  共四笔合法库存操作，保留四笔 SUCCESS 幂等记录及四条流水；三表共九个运行时 ID
  均为显式、互异、正数雪花，workerId=0，无自增回退。SKU301 version 因两笔操作正常递增；
  SKU308 保留新建的零库存行，审计不清除。
- 员工跨商户库存读/写均为 404/13001，消费者精确库存读为 400/13006，匿名为 401。
- 本人及同商户员工读取相同订单成交快照；同商户他人、其他商户消费者及员工均不能读取
  该订单或物流（统一 404/10004）。本人可查 DELIVERED、IN_TRANSIT、无运单 NOT_SHIPPED；
  隔离租户消费者可以读取自己的物流。

实际非暂停日志断点命中：`InventoryStockTxService.java:51` 四次，
`ShipmentQueryService.java:40` 七次，覆盖两商户与允许/拒绝分支，未出现断点评估错误。
日志断点已禁用，用户原有断点未修改。证据为同目录 `http-smoke-result.json`、
`runtime-inventory-ids.json`、`debugger-trace.json`、`idea-acceptance.log`。

联调后保存全部业务行及完整 history 快照，再停止并通过 IDEA 使用相同 JDK/目录、dev profile、
workerId=0 启动（最终启动移除本次临时 mock SMS/debug-code 参数覆盖，不修改持久配置）。
08:26 启动日志明确 `No migration necessary`，启动 4.459 秒；健康为 HTTP 200 / UP。
08:27 独立核验全部业务行摘要、完整 history（含时间戳）与重启前完全一致，Flyway validate 通过。
人工值、账号/会话和库存数量/version 不被普通重启覆盖，任务 8.4 完成。
证据为同目录 `restart-{rows,history}.json`、`restart-result.json`、`health-restart.json`、`idea-restart.log`。

最终 `align-commerce-module-integration` 42/42，8.2–8.4 全部完成。
dev 后端留在 IDEA 运行，地址 `http://localhost:8080`；原 V5、失败现场两份完整备份和全部证据保留。
恢复步骤、停止条件及回退说明见 [commerce-dev-recovery.md](../deployment/commerce-dev-recovery.md)。
