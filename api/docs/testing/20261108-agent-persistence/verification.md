## 2026-11-08 持久化验证

本轮按切片先观察失败再实现：迁移仍停在 V4、非法状态未被拒绝、Mapper 类型缺失、@Sql fixture 缺失、R030 数据缺失，以及旧维护工具拒绝待执行 V5。对应实现后分别复验通过。仅实现任务 2，后续 Service 必须继续验证幂等、双消息原子提交及跨实例执行事务，不能将这些 Mapper 测试等同于完整运行时验收。

最终在仓库根运行（本地 Maven 依赖已缓存，使用 `-o`）：

```powershell
mvn -o -f api/pom.xml '-Dtest=ChatSchemaIT,ChatPersistenceIT,DevSeedMigrationIT,FlywayMigrationIT,AuthSchemaIT,AuthPersistenceIT,FoundationSchemaContractTest,MigrationScriptContractTest,RuntimeConfigurationTest' test
```

| 测试 | 通过项数 | 验证边界 |
| --- | --- | --- |
| ChatSchemaIT | 2 | 空库/V4 升级、历史 checksum/行保留、租户 FK、幂等/序号/run 唯一、状态/身份/用量约束 |
| ChatPersistenceIT | 3 | 角色/账号/商户隔离、会话游标、消息序号、归档/运行权条件写、终态/迟到回调、fixture 显式加载与回滚 |
| DevSeedMigrationIT | 21 | R030 顺序/补缺/人工修改/身份冲突，以及既有改名维护、备份恢复、CLI 与拒绝未知迁移 |
| FlywayMigrationIT | 4 | 既有空库、baseline、V3 升级、重复执行兼容 |
| AuthSchemaIT / AuthPersistenceIT | 2 / 4 | 既有认证结构与 Mapper 回归 |
| FoundationSchemaContractTest / MigrationScriptContractTest / RuntimeConfigurationTest | 4 / 3 / 3 | V1–V4 不变、种子脚本约定、test/prod locations 隔离 |

共 **46 项，0 failures、0 errors、0 skipped**，Maven 退出码 0。原始报告在 `api/target/surefire-reports/` 的对应 `TEST-*.xml` 和 `.txt`。`openspec validate add-agent-foundation-conversation-api --strict` 通过。V1–V4 的文件 SHA256 与本轮修改前一致；测试不操作开发数据库或数据卷。此记录仅是持久化阶段验证，完整模块验收仍由后续任务完成。
