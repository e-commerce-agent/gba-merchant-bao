# Proposal

## Why

当前 Flyway 配置和测试对 baseline 版本的使用不一致：应用默认值为 `0`，旧库接管测试使用 `1`。V1 是旧餐饮数据库的 11 张表结构，V2 新增商户根表，V3 为商户表增加币种字段。新开发者和自动化集成测试都应从空数据库按顺序执行 V1、V2、V3；只有现存旧库在备份并核验结构确实匹配 V1 后，才可显式 baseline 到版本 1，再继续执行 V2、V3。本地持久联调数据也需要与 Testcontainers 的临时测试数据分开，并统一开发 profile。

## What Changes

- 明确并验证空数据库与已核验旧数据库的 Flyway 路径；关闭自动 baseline。
- 将 `baseline-version` 默认值统一为 `1`，并说明它只用于显式接管旧库；空库启动不执行 baseline，仍从 V1 开始迁移。
- 使用 `dev` profile 连接本地 Docker Compose MySQL/Redis 持久服务；使用 `test` profile 和独立 Testcontainers 运行自动化测试；移除重复的 `local` profile。
- 保留从空库执行 V1 到最新版本的常规集成测试，并以一个小型独立 Testcontainers 场景验证已核验旧库显式 baseline 后执行 V2/V3；不建设大型自动结构比对工具。
- 记录设计手稿中的固定样板租户 `M_3C_DEMO` 和隔离对照租户 `M_ISOLATION_TEST`；商品、订单等业务样板数据随各自技术模块逐步加入。
- 阿里云 RDS 和生产发布配置不在本次范围内；不为尚未实现的技术模块预建表或加入依赖。
- 将本地连接设置和凭证移出已跟踪的应用配置，提供仅含占位值的示例和忽略本地密钥文件的说明。

## Capabilities

### New Capabilities

无

### Modified Capabilities

- `application-technical-baseline`：规定清晰的 dev/test 数据库生命周期、安全的 Flyway 初始化及旧库接管流程，以及确定性的样板/隔离租户数据。

## Impact

- 后端配置：`api/src/main/resources/application*.yml`、Flyway 迁移和集成测试配置。
- 本地开发环境：Docker Compose MySQL/Redis 配置、持久数据卷、环境变量注入说明和忽略的本地密钥文件。
- 测试与文档：Flyway 集成测试、Testcontainers 生命周期检查、profile 选择、baseline 操作手册和样板数据归属说明。
- 不包含阿里云 RDS 部署、生产凭证配置、认证表或未来 Agent 技术模块的数据表。
