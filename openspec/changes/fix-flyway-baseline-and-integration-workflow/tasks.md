# Tasks

## 1. Local Integration Environment

- [ ] 1.1 添加开发者专用的 Compose 配置，启动 MySQL 和 Redis，设置健康检查、命名持久卷、可覆盖的固定主机端口和独立数据库；验证 `docker compose config` 成功，且服务重启后测试数据仍存在。
- [ ] 1.2 将本地配置统一为 `dev` 和 `test`，移除重复的 `application-local.yml` profile 路径，并将凭证移出已跟踪 YAML；验证配置契约测试拒绝提交真实密钥，且测试显式激活 `test`。
- [ ] 1.3 添加忽略本地环境文件的规则和仅含占位值的示例；说明 Compose 与 IDE/运行配置如何注入环境变量，因为 Spring Boot 不会自动读取 `.env`；验证新检出的工作区可按文档完成设置。
- [ ] 1.4 更新本地运行手册及 Apifox/前端连接说明，统一使用持久化的 `dev` 数据库，并与 Testcontainers 区分；验证文档中的命令和 profile 名称与 Compose/配置一致。
- [ ] 1.5 记录若已跟踪配置曾包含真实凭证，应通过对应服务提供方轮换；验证仓库中不再存在可用的本地凭证。

## 2. Flyway Baseline Workflow

- [ ] 2.1 将应用的默认 `baseline-version` 设为 `1`，并保持 `baseline-on-migrate=false`、启用校验、禁用 clean 和 out-of-order；验证空的 MySQL Testcontainers 数据库无需手动 baseline，按顺序执行 V1、V2、V3。
- [ ] 2.2 在 Flyway 集成测试中保留一个小型独立旧库接管场景：从 V1 SQL 构建旧结构并插入代表性数据，确认普通 migrate 拒绝未登记的非空库；显式 baseline 到版本 1 后只执行 V2、V3，并验证旧数据保留。测试保持聚焦，不建设大型自动结构差异检测工具。
- [ ] 2.3 编写旧库接管操作手册，准确说明 V1/V2/V3 结构、备份和逐项结构核验要求、停止条件、显式 baseline 命令、后续 migrate 及历史/行数核对；在隔离演练库验证命令，并确认常规应用启动不会自动 baseline。
- [ ] 2.4 验证每位新开发者的空数据库、本地新建 Compose 数据库和常规自动化集成测试都从 V1 开始；baseline 版本 1 仅作为经过核验的旧库显式接管目标，不会造成空库跳过 V1，也不代表旧数据已转换为新商贸模型。

## 3. Fixtures and Isolation

- [ ] 3.1 在 `dev` 和 `test` 的样板数据位置保留两个稳定租户根，并断言编码正确且重复应用具幂等性；验证每个全新环境恰好有一个 `M_3C_DEMO` 和一个 `M_ISOLATION_TEST`。
- [ ] 3.2 记录设计手稿中的完整样板数据计划（3C 商品/SKU、订单、物流轨迹、知识和最小会话数据）及其未来模块归属；验证本基础 change 不创建未实现模块的数据表或业务数据。
- [ ] 3.3 保留 Testcontainers 隔离检查，证明测试不连接固定 Compose 端口、不挂载持久卷；在本地 Compose 服务运行时执行完整集成测试验证。

## 4. Full Verification

- [ ] 4.1 运行 `mvn -f api/pom.xml test`，确认单元测试和配置测试通过，且不依赖本地 MySQL 或 Redis。
- [ ] 4.2 在 Docker 可用时运行 `mvn -f api/pom.xml clean verify`，确认 Testcontainers 实际启动 MySQL/Redis、没有意外跳过测试，且测试日志隔离。
- [ ] 4.3 严格校验已完成的 OpenSpec change，并确认需求、实现文件和操作手册对 profile、baseline 行为及样板数据归属的描述一致。
