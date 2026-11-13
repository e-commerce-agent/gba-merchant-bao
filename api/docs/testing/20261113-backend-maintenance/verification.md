# 后端维护整理验收记录

2026-11-13（Asia/Shanghai），对应 `streamline-backend-maintenance` 第 8 节；任务与目录入口见 [本目录 README](README.md)。

## 来源与检查范围

初始保护与测试发现基线来自提交 `ebc2c58096437b84058046a02a07fb9624e1c9fd`。本次执行起点为 `0e74804`，工作区干净；其中 `1c5136b` 已冻结并搬迁五个模块，`0e74804` 已完成 runtime 分类保全。初始来源和失败事实如实记录，没有修改其他 change 的状态。

最终交付以本事件 Markdown 记录核验结论，具体文件变化可结合 Git 历史追溯；三份临时 JSON 核对文件经确认无测试、构建或运行代码依赖后删除，无需随 change 提交或归档保留。

本次机械修复归档搬迁后常规文档中的 20 处链接、五个归档 README 中的 16 处链接，并同步物流历史记录中的实际归档路径。五个 README 的修改已获用户明确授权，仅改变链接地址；五个基础模块的其他归档正文未读取或修改。补齐归档及脚本父目录 README、旧备份兼容入口导航，删除用户已取消的 `check-maintenance.ps1`，没有重建归档 manifest 或哈希机制。

验证期间用户将两批早期材料移到 `api/docs/archive/20261005-鉴权/` 和 `20261006-架构/`，并明确授权同步导航。对应 10 个历史文件的正文和 YAML 保持原内容，仅机械调整链接；新增两个目录 README。FoundationContractTest、FoundationSchemaContractTest 读取历史证据的路径随之同步，断言保持原样；另补跑这两个类的 6 项测试通过，确认新位置可读。

## 目录与保护范围

- 常规正文的说明文档链接经所属 README 定位；可修改文件的本地链接目标与非冻结正文锚点检查通过。归档内部只检查五个 README，历史技术正文及其章节内容不在本次读取范围。
- 五模块分别位于 `api/docs/archive/20261113-auth`、`20261113-product`、`20261113-inventory`、`20261113-order`、`20261113-shipment`；没有脚本副本。冻结约束仍由根 AGENTS.md 声明。
- testing 事件使用日期前缀、每事件一行原因与用途、每事件独立 README；旧事件没有追加新验收结果，本次另建事件目录。
- 根 README、整个 prod-profile、V/R/fixture SQL 与初始基线零差异；34 个记录哈希的保护文件匹配，另 1 个本地敏感配置只核对存在性和元信息，不读取凭据正文。
- 7 个受维护 PowerShell 脚本语法解析通过；分类为 prod 1、dev 3、test 1、tools 2。文档目录中仅保留生产手稿所需的 `api/docs/deployment/backup-dev.ps1` 参数与退出码透传入口，没有第二套备份逻辑。

## 完整 Maven 验证

实际执行命令：

```powershell
mvn -f api/pom.xml clean verify
```

使用 JDK 21.0.12.1、Maven 3.9.11、Docker Desktop 29.8.0 的 Linux 容器引擎。沙箱访问 Docker 命名管道返回 EPERM 后，经授权在沙箱外执行。Testcontainers 使用独立 MySQL 8.0.34 / Redis 7.4-alpine、随机端口与测试数据；没有执行业务 Compose、生产发布或真实开发库维护。

完整命令于 2026-11-13 12:46:40 +08:00 结束，耗时 20:25，退出码 0、BUILD SUCCESS。本次 XML 逐项汇总如下：

| 阶段 | 报告类数 | 测试/参数化用例数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: | ---: |
| Surefire | 29 | 109 | 0 | 0 | 0 |
| Failsafe | 26 | 183 | 0 | 0 | 0 |

普通 Spring 上下文使用 test；AuthDevManualIT 按原有设计显式启用 dev+test，并把数据库和 Redis 连接注入独立临时容器，用于验证 dev 种子。它不连接已有开发库。Maven 完成后开发日志 `api/.runtime/logs/application.json`、`audit.json` 的大小与修改时间均未变化。

历史材料在完整运行期间发生移动，相关两个单元契约类在移动前已执行。因此，在机械同步读取路径后补充执行：

```powershell
mvn -f api/pom.xml '-Dtest=FoundationContractTest,FoundationSchemaContractTest' test
```

该命令重新编译 59 个测试源文件，于 12:47:30 +08:00 结束，退出码 0；FoundationContractTest 2 项、FoundationSchemaContractTest 4 项，均无失败、错误或跳过。这 6 项是重跑，不累加到完整验收的 292 项不同用例中。完整运行的原 XML 与发现对照另保存在忽略的 `api/target/maintenance-clean-verify/`，避免被定向报告覆盖。

## 文件与发现语义对照

59 个 Java 文件逐一匹配原映射；29 个默认 `*Test`、26 个默认 `*IT`、三个辅助类和一个手工入口未减少。除计划内的 package/import、辅助类必需访问开放、两处 OpenAPI 分组断言、Apifox 状态路径、两个例外类的注释，以及本轮用户移动历史材料后两个契约测试的证据路径外，测试主体与原断言保留。ChatRunExecutionTest、ChatSseLifecycleTest 仍保留原生产包声明，文件在 chat 目录。生产 Java、应用配置、fixture 与 Maven 发现配置没有修改。

发现结果按“简单类名 + XML 中的方法/参数化用例名称”逐项比较：Surefire 109 项、Failsafe 183 项与初始基线一一对应，缺失 0、新增 0。AgentApifoxWorkspace 未被默认发现。唯一状态变化为 ProductAvailabilityIT.exportsActualProductOpenApiWithThePublicBooleanContract 从初始失败变为通过；其余状态未变。AuthDevManualIT 的认证分组断言同样通过；初始结果为 Surefire 109 项通过、Failsafe 183 项中该用例失败，原失败事实与修复结果分别记录于此。

实际 XML、日志和逐项对照结果保留在忽略的 `api/target/`；本事件只记录脱敏结论。OpenSpec strict validate、链接与目录检查、7 个 PowerShell 脚本解析和 git diff --check 均通过。未重跑旧 runtime 修复脚本，未额外执行契约导出工具或手改快照；ChatHttpIT 按原测试逻辑写出 Agent 快照，Git 无语义差异。

## 验收边界

本次只验证维护整理及原有自动化覆盖，不代表 Apifox、真实模型、浏览器、Nginx 多实例或生产发布通过；其他 change 的待验收状态保持。未执行 commit、push、远程 PR、OpenSpec 同步或归档。
