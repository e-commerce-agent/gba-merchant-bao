# 本地电商开发库恢复方案与执行结果

变更：`align-commerce-module-integration`，任务 8.2–8.4。
本方案针对 2026-11-11 的 R040 分类种子失败；2026-11-12 已在隔离副本验证修复。
用户于 2026-11-12 确认执行；已按下述路线完成真实开发库恢复及任务 8.2–8.4。
实际证据见 [整合验收记录](../testing/commerce-integration-verification.md#2026-11-12-授权恢复与实际-dev-启动)。

## 故障与修复

开发库已成功执行 V6–V9，但 `R__040_product_demo.sql` 第 130 行因
`fk_product_category_parent` 外键检查失败而中断，history 留有 `success=0`。
原语句将根分类与子分类放在同一个 UNION/JOIN 的 INSERT 中；隔离副本的执行计划采用
hash join，实际首先输出子分类，父分类尚未插入。InnoDB 即时检查外键，因此失败。

修复仅调整 R040 分类写入：第一条 INSERT 插入四个根分类，第二条 INSERT 插入子分类。
保留写前冲突检查、稳定 ID、商户归属与仅补缺失语义；V1–V9、R010–R030 内容不变。
`DevSeedMigrationIT.productSeedCreatesParentCategoriesBeforeChildrenWithRefreshedMerchantStatistics`
通过 `ANALYZE TABLE merchant` 复现该计划条件，原 SQL 失败，修复后成功，并验证人工值在重跑后保留。

仅替换 SQL 后直接重启无法消除已经存在的失败 history。本方案选择恢复已核验的完整 V5
快照，再让 Flyway 正常迁移；不以 repair 或手工修改历史记录绕过失败。

## 已完成的隔离演练

使用 MySQL 8.0.34、随机宿主端口及独立容器，不挂载开发卷：

1. 恢复原 V5 备份，原 SQL 的完整 migrate 及单独 R040 均复现同一分类外键错误。
2. 保存失败日志和执行计划；修复后的 R040 在同一失败副本直接执行成功。
3. 仅重建隔离副本中的 `gba_dev`，再次恢复原 V5 快照。所有原业务行摘要、完整 history、
   各表 DDL 均与备份报告相同。
4. 修复后的完整迁移执行 V6/V7/V8/V9、R040/R050/R060/R070 共 8 项，成功到达 V9。
   原业务行和完整原 history 保留。
5. 验证五个分类无孤儿、商品 6、SKU 9、库存 8、订单 7、订单项 8、运单 3、轨迹 6；
   未生成库存业务流水或幂等记录。Flyway validate 通过，再次 migrate 为 0，业务摘要及 history 不变。

演练证据目录：`backups/commerce-category-repro-20261112/`（Git 忽略）。
完整验收和测试结果见 [commerce-integration-verification.md](../testing/commerce-integration-verification.md)。
隔离 migrate 的二次执行验证不替代任务 8.4 的真实应用二次启动验收。

## 已执行路线：完整恢复 V5 后重新升级

该路线重建真实开发库 `gba_dev`；本次已获得用户明确确认。开发卷及其他数据库保留。

1. 保持所有后端、维护程序和其他写入停止。仅启动 `gba-dev` 所需基础设施，重新只读核对
   容器、3307 端口、数据库 `gba_dev`、应用账号 `dev_admin` 和当前 history。
   必须仍为本次 V6–V9 成功、R040 失败的状态。
2. 将当前全部业务行摘要、完整 history、表 DDL 与
   `backups/commerce-v5-upgrade-20261111/failed-startup-{rows,history,schema}.json` 精确比较。
   任意新增、修改或删除均停止：先评审数据保留方式，不覆盖今天的新数据。
3. 对当前失败状态另做一次完整备份（包含 routines/events/triggers），保存到新的恢复操作目录，
   不覆盖 `before.sql` 或昨天的失败证据。计算 SHA-256，在新隔离容器恢复并核对全部业务行、
   DDL 和完整 history，确保可回退到恢复操作前的状态。
4. 校验原 V5 备份 `backups/commerce-v5-upgrade-20261111/before.sql` 的 SHA-256 必须为
   `604B7FC6994D586D43D1BEAE370F7B1E3867D6DF592324D0F202C607D2BA0EFB`。
   只在已核对的开发 MySQL 中重建 `gba_dev`（utf8mb4 / utf8mb4_0900_ai_ci），导入该完整备份。
   不删除 Docker 命名卷，不操作其他库，不直接 UPDATE/DELETE history。
5. 恢复后、启动前核对全部业务摘要、表 DDL、完整 history 与原 V5 报告完全一致，最高版本为 V5。
   冻结脚本校验必须通过，待执行项必须为 V6–V9 及 R040–R070。
6. 使用修复代码重新 clean 构建，核对打包 SQL 无旧资源，IDEA 使用 JDK 21、工作目录 `api`、
   dev profile 和 `MERCHANT_ID_WORKER_ID=0` 启动。应用通过 `dev_admin` 自动迁移。
   记录实际迁移清单、健康检查、Flyway validate 和库存三表无自增元数据，完成 8.2。
7. 从运行中的 dev Springdoc 分组分别导出商品、库存、订单、物流 OpenAPI 到 `docs/api/`，
   核对路径、模型和权限，完成 8.3。
8. 执行 8.4 的双租户联调、运行时雪花 ID、订单快照及物流权限验证；记录重启前快照，
   再次启动后逐项比较业务数据和 history，更新验收记录及任务状态。

口令只经本地配置或进程环境读取，不写入文档、命令输出、日志或 Git。

执行结果：恢复前数据、history、DDL 与失败现场完全一致；新失败现场备份在隔离 MySQL 中恢复核验通过，
保存在 `backups/commerce-dev-recovery-20261112/failed-state.sql`，SHA-256 为
`190107ABC34BA7607A9E5C129B4814CEECD09EFF0EAC86679111C7B1F265C335`。
恢复 V5 后原业务行、完整 history 和 DDL 与原报告相同，正常 dev 启动执行 8 项迁移到 V9；
健康、Flyway validate、库存元数据、四组 API 导出及双租户联调通过。
实际二次启动无迁移，全部业务数据与完整 history 不变，最终 42/42 完成。未执行 repair。
后端继续在 IDEA 运行；临时短信启动覆盖及日志断点已清理。两份完整备份和日志保留。

## 中止与回退

任一步骤目标、摘要、history、备份恢复核验或迁移结果不符时立即停止后端，保留新旧备份和日志。
MySQL DDL 不视为事务回滚。不得继续重启尝试、删卷、重编号、自动 repair 或手工登记迁移成功。

如果重建开发库后需要回退，先保存现场，再选择已核验的原 V5 快照或本次操作前的失败状态
完整快照，评审后恢复；失败状态快照仍带 R040 失败记录，仅用于还原现场，不能作为可启动版本。
其他恢复路线（例如保留 V9 并处理失败 history）未获本方案授权，须单独评审。
