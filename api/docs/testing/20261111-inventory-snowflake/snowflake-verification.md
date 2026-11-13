# 库存雪花主键定向验收

对应 `align-commerce-module-integration` 第 6 节，2026-11-11（Asia/Shanghai）。规则见 [库存模块（本目录入口）](README.md#module-ids)；第 1–5 节的历史结果保持原记录。本次使用 Testcontainers MySQL 8.0.34 / Redis 隔离环境，不连接或升级本地 dev 库。

| 测试 | 数量 | 验证内容 |
| --- | ---: | --- |
| `MigrationScriptContractTest` | 6 | 冻结文件摘要、唯一 V/R 布局、V7 无自增及原引用约束 |
| `InventorySchemaIT` | 2 | 三表 id 的 BIGINT/非空/无默认值/无自增/主键元数据；省略 id 时 MySQL 拒绝 |
| `SnowflakeIdGeneratorTest` | 6 | 两个 worker 在固定同一毫秒内并发生成 6,400 个唯一 ID；单例、回退、耗尽及已有 ID 契约 |
| `InventoryFlowIT` | 23 | 注入 ID 实际落库、首次/并发初始化、成功重放无新增、FAILED 重试保留幂等 ID、乐观锁、流水失败回滚及租户/角色边界；幂等/库存/流水分配 ID 故障和已有库存回滚 |
| `DevSeedMigrationIT` 定向方法 | 12 | 固定库存 ID 装载、重复执行、人工数量/version 保留、自然键/固定 ID/跨商户占用冲突、缺目录时写前失败，无合成流水 |
| `ProductAvailabilityIT` | 11 | 显式库存 fixture 下的公开有货布尔、目录缓存与权限回归 |

两次执行均 `BUILD SUCCESS`，失败、错误、跳过均为 0。按不同用例计 60 项（12 项单元测试、48 项集成测试）；迁移契约的 6 项在两次命令中重复执行，不重复计数。

第一次于 21:37:26 完成，耗时 57.861 秒，原始日志 `api/target/inventory-snowflake-schema.log`：

```powershell
mvn -f api/pom.xml '-Dtest=MigrationScriptContractTest' '-Dit.test=InventorySchemaIT' test-compile surefire:test failsafe:integration-test failsafe:verify
```

第二次于 21:46:32 完成，耗时 3 分 23 秒，原始日志 `api/target/inventory-snowflake-flow.log`：

```powershell
mvn -f api/pom.xml '-Dtest=MigrationScriptContractTest,SnowflakeIdGeneratorTest' '-Dit.test=InventoryFlowIT,ProductAvailabilityIT,DevSeedMigrationIT#freshOrderAndUnchangedMigrateHaveExactlyOneExecutionPerSeed+commerceSeedsPreserveManualValuesOnExplicitRerun+commerceSeedIdentityConflictsFailBeforeBusinessWrites+inventorySeedRejectsAStableIdOwnedByAnotherMerchantBeforeAddingMissingRows+commerceSeedGuardsRejectMissingOrMismatchedCatalogBeforeWrites' test-compile surefire:test failsafe:integration-test failsafe:verify
```

日志及 JUnit XML 保存在本地忽略目录 `api/target/`，容器使用临时数据；Maven 获准在沙箱外访问 Docker 命名管道。静态核对确认库存 Mapper 均显式插入 id，库存服务复用现有 Spring 单例，无新算法或客户端内部 ID 参数；V1–V5 和 R010/020/030 内容不变，无 V10。`git diff --check` 和 `openspec validate align-commerce-module-integration --strict` 通过。

第 7 节的空库/冻结 V5 路径综合验收、完整 `clean verify`，以及第 8 节的本地备份、升级、启动与调试验收均暂未执行。这些定向结果不代表完成真实 Nginx 集群部署；多副本仍须分配不同 worker，遵守时钟与重启约束。
