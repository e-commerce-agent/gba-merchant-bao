# Design

## Context

当前迁移顺序如下：

- V1 创建从旧库结构快照整理出的 11 张餐饮业务表。
- V2 创建独立的 `merchant` 商户根表。
- V3 为 `merchant` 增加 `currency_code`。

新开发者、本地联调库和自动化集成测试库都从空库开始，按顺序执行 V1、V2、V3。现存旧库只有在实际结构与 V1 完全匹配、且没有 `merchant` 表时，才可以跳过 V1：先备份并完成结构核验，再显式 baseline 到版本 1，随后执行 V2、V3。V2、V3 只增加基础结构，不会把旧餐饮数据转换成 3C 商贸数据。`baseline-version: 1` 只指定显式 baseline 操作记录的版本；当 `baseline-on-migrate: false` 时，空库不会因该设置跳过 V1。

项目目前有 `dev`、`local`、`test`、`prod` 配置。`dev` 和 `local` 都加载 `db/devdata`，而 `dev` 仍指向个人 MySQL 和远程 Redis。集成测试显式选择 `test`，并通过 Testcontainers 的动态地址覆盖连接配置。当前可重复种子只有两个租户根记录；设计手稿还定义了 3C 商品、SKU、订单、物流、知识和会话样板，需随对应模块逐步补充。

## Goals / Non-Goals

**Goals:**

- 将本地持久 Docker Compose 服务作为 API、前端和 Apifox 联调的固定环境。
- 使用 Testcontainers 隔离并销毁自动化集成测试依赖。
- 明确空库完整迁移与现存旧库接管是两种不同流程，并分别验证。
- 保留设计手稿中的样板租户和隔离对照租户作为固定数据根；业务样板数据由对应模块逐步添加。
- 统一 profile，并从已跟踪文件中移除本地凭证。

**Non-Goals:**

- 配置阿里云 RDS 或定义生产发布流程。
- 将旧餐饮业务数据转换到新的商品、订单或租户模型。
- 实现认证表、Redis 登录会话或四个 Agent 技术亮点。

## Decisions

### Use `dev` for persistent local integration and `test` for automated tests

使用 Spring 常见的 `dev`/`test` profile，删除重复的 `local`。`application-dev.yml` 连接本地 Compose 服务，并读取开发者提供的连接配置。Compose 使用固定本地端口和命名持久卷。API 在 `dev` 环境运行，前端和 Apifox 连接此 API；这些联调数据不存放在 Testcontainers 中。

集成测试显式选择 `test`（例如 `@ActiveProfiles("test")`），由 Testcontainers 启动容器，再通过 `@DynamicPropertySource` 注入实际映射的主机和端口。测试启动保护应拒绝缺失的容器连接属性，避免继承的环境设置将测试意外指向 Compose。完整 Maven 验证是需要 Docker 的自动化测试入口。

### Treat Flyway baseline as an operator action for verified legacy schemas

保持 `baseline-on-migrate=false`、`validate-on-migrate=true`、`clean-disabled=true` 和 `out-of-order=false`。默认 `baseline-version` 设为 `1`，作为显式接管旧库时记录的版本。这个默认值不改变空库初始化：新开发者、本地 Compose 新库及常规 Testcontainers 集成测试都直接执行 V1、V2、V3，不做 baseline。

现存旧库仅按以下步骤接管：

1. 停止应用写入，并生成可恢复的备份。
2. 在隔离的演练库中，将目标库与批准的 V1 结构快照逐项比对：表、字段、类型、可空性、索引、约束，以及相关 MySQL 版本和排序规则。
3. 确认目标库没有 `flyway_schema_history` 或冲突的 `merchant` 表，并记录核验结论。
4. 只有核验完全通过后，才显式执行版本 1 的 baseline 操作，再执行 `migrate` 应用 V2、V3；日常应用启动不得自动 baseline。
5. 核对 `flyway_schema_history`、旧表行数、商户新结构、应用启动情况，并确认备份可恢复。

任一结构不匹配都必须停止，不得对部分匹配的库 baseline，也不得用 `repair` 掩盖差异。本 change 使用一个小型独立 Testcontainers 场景验证 Flyway 接管机制和旧行保留；该测试不是完整的线上旧库结构比对工具，也不能证明任意旧库都与 V1 匹配。旧库结构比对由操作手册指导人工执行，不在本次建设大型自动检测工具。

### Use one repeatable tenant-root fixture and incremental business fixtures

`dev` 和 `test` 的样板数据位置都包含两个稳定租户根。`M_3C_DEMO` 是设计手稿定义的 3C 配件样板租户，`M_ISOLATION_TEST` 是隔离对照租户。商品、SKU、订单、物流、知识和会话数据不在本基础 change 中创建，待对应技术模块实现时再增量加入。每个模块使用稳定业务编码和幂等写入，并添加必要的跨租户测试记录；Apifox 使用持久的 `dev` 数据库及各模块稳定编码。

### Keep production deployment work separate and local secrets untracked

本 change 移除重复的 `local` profile，但不实现 RDS 发布流程。已有 `prod` 配置可以保留为非目标占位，不在此处定义生产迁移或凭证行为。本地值通过进程环境或被忽略的本地环境文件注入。Spring Boot 不会自动读取 `.env`，因此操作手册要说明由 Compose、启动脚本或 IDE 运行配置注入。已跟踪的示例文件只保留占位值。

## Risks / Trade-offs

- [Risk] 实际旧库结构可能与 V1 不同，即使 V1 来源于已核验快照 → [Mitigation] 显式 baseline 前要求备份、结构比对和隔离演练；任何差异都停止。
- [Risk] 开发者可能将 `dev` 指向旧业务库 → [Mitigation] 使用独立 Compose 数据库名，写明连接目标，不保留个人或类生产默认值；执行 baseline 或迁移前先核验目标。
- [Risk] 持久 Compose 卷会保留旧结构 → [Mitigation] 使用向前迁移并编写仅供开发者使用的重建步骤；自动化测试只操作临时容器，不触碰 Compose 卷。
- [Risk] 移除 `local` 可能影响显式激活该 profile 的脚本 → [Mitigation] 搜索并更新脚本和启动文档，对不支持的 profile 明确报错。
- [Risk] 已跟踪配置曾保存真实凭证 → [Mitigation] 在本次配置修改中替换为环境变量或占位值，并在凭证提供方控制台轮换历史真实凭证。

## Migration Plan

1. 添加开发者专用的 Compose MySQL/Redis 服务，设置固定开发端口、命名持久卷、健康检查，并确保测试不复用这些服务。
2. 将运行配置统一为 `dev` 和 `test`，移除凭证，提供示例文件并更新启动和测试文档。
3. 将 `baseline-version` 默认值设为 1，同时保持自动 baseline 关闭；验证空库按 V1 到最新版本迁移、旧库显式 baseline-1 后执行 V2/V3，并验证普通启动拒绝未登记的非空库。
4. 验证 `dev` 的数据在容器重启后仍存在，并确认 `mvn test`/`mvn clean verify` 使用 Testcontainers 而非 Compose 服务。
5. 不自动修改开发者的既有数据库。先备份和核验，再由开发者按审阅后的接管手册操作；在此之前保留当前库不变。

应用和配置变更可通过源代码回退。如果本地数据库已执行迁移，应恢复迁移前备份，或另行评审向前修复；不得把删除命名卷作为自动回滚方式。

## Open Questions

无影响已选范围的待决问题。具体本地端口和 Compose 镜像标签可遵循项目现有约定，并避开开发者已占用的端口。
