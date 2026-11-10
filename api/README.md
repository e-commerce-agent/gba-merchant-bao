# 商户宝后端

`api/` 是单 Maven 后端工程，使用 JDK 21、Spring Boot 3.5.16。新根包为 `com.gba.merchantbao`，旧餐饮实现位于 `legacy` 子包。

## 文档入口

- [包结构与兼容边界](docs/architecture/packages.md)
- [认证后可并行模块与四个 Agent 亮点前置](docs/architecture/module-roadmap.md)
- [认证表、索引与数据边界](docs/auth/data-design.md)
- [认证需求](docs/auth/requirements.md) / [九操作契约](docs/auth/api-design.md)
- [认证开发账号与后端联调脚本](docs/auth/manual-verification.md)
- [认证模块最终验收记录](docs/auth/verification.md)
- [认证 Springdoc 快照（Apifox 导入）](../docs/api/auth-v1.openapi.yaml)
- [Agent 需求与验收矩阵](docs/agent/requirements.md) / [六操作契约](docs/agent/api-design.md)
- [Agent 数据设计](docs/agent/data-design.md) / [模型边界与配置](docs/agent/model-integration.md)
- [Agent curl/fetch 与凭证说明](docs/agent/manual-verification.md) / [Apifox 验收步骤](docs/agent/apifox-verification.md)
- [Agent 验收记录与待验收项](docs/agent/verification.md)
- [Agent Springdoc 快照（Apifox 导入）](../docs/api/agent-conversation-v1.openapi.yaml)
- [商品模块数据/契约/授权说明](docs/product/module.md)
- [自动化测试、容器隔离和日志目录](docs/testing/README.md)
- [本地持久联调与 IDEA/Apifox](docs/deployment/local-development.md)
- [Flyway 空库与旧库接管](docs/deployment/flyway-baseline.md)
- [开发种子排序与旧名历史一次性升级](docs/deployment/dev-seed-upgrade.md)
- [生产准备与未来手动发布](docs/deployment/production.md)
- [日志](docs/deployment/logging.md) / [备份恢复](docs/deployment/backup-recovery.md)
- [基础运行环境验收记录](docs/deployment/verification.md)

## 验证入口

从仓库根目录运行：

```powershell
mvn -f api/pom.xml test
mvn -f api/pom.xml clean verify
```

完整验收前启动 Docker Desktop 的 Linux 容器引擎。测试自动创建和清理隔离 MySQL/Redis 容器，无需手动启动固定中间件容器。

## 目录约定

`src/` 保存实现和测试，`docs/` 保存后端维护文档，`target/` 保存构建产物、测试报告和测试日志，`.runtime/` 保存忽略的本地运行文件。开发运行的工作目录设为 `api/`，日志在 `.runtime/logs`；生产通过 `LOG_DIR` 指定外部路径。当前 dev Compose 提供 MySQL/Redis，后续中间件随首次使用的技术模块添加。

