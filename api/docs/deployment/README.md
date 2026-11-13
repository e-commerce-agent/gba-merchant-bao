# 运行与部署文档

## 本目录文件

- [backup-dev.ps1](../../scripts/dev/backup-dev.ps1)：开发数据库备份脚本。
- [backup-dev.ps1 兼容入口](backup-dev.ps1)：仅为现有生产手稿保留，参数和退出码透传到开发脚本。
- <a id="backup-recovery"></a>[backup-recovery.md](backup-recovery.md)：备份及恢复操作。
- <a id="commerce-dev-recovery"></a>[commerce-dev-recovery.md](../testing/20261112-commerce-integration/commerce-dev-recovery.md)：电商开发数据恢复操作。
- [deploy-prod.ps1](../../scripts/prod/deploy-prod.ps1)：生产发布脚本。
- <a id="dev-seed-upgrade"></a>[dev-seed-upgrade.md](dev-seed-upgrade.md)：开发种子升级的操作与边界。
- [dev-seed-upgrade.ps1](../../scripts/dev/dev-seed-upgrade.ps1)：开发种子升级脚本。
- [docker-dev.ps1](../../scripts/dev/docker-dev.ps1)：开发容器管理脚本。
- <a id="flyway-baseline"></a>[flyway-baseline.md](flyway-baseline.md)：Flyway 空库和旧库接管。
- [local-development.md](local-development.md)：本地开发环境与联调。
- <a id="logging"></a>[logging.md](logging.md)：日志配置和运行说明。
- <a id="production"></a>[production.md](production.md)：生产运行与发布准备。
- [verification.md](../testing/20261101-runtime-architecture/verification.md)：已有验证过程、结果与限制。

## 相关资料

- <a id="devdata"></a>[devdata / README.md](../../src/main/resources/db/devdata/README.md)：目录资料入口。
- <a id="auth-data-design"></a>[auth / data-design.md](../archive/20261113-auth/data-design.md)：数据结构和边界。
- <a id="auth-verification"></a>[auth / verification.md](../testing/20261106-auth-acceptance/verification.md)：验证记录与实际状态。
- <a id="inventory-module-ids"></a>[库存主键与多副本运行](../archive/20261113-inventory/module.md#主键与多副本运行)：库存主键生成和多副本配置。
- <a id="testing"></a>[testing / README.md](../testing/README.md)：目录资料入口。
- <a id="testing-commerce-integration-verification"></a>[testing / commerce-integration-verification.md](../testing/20261112-commerce-integration/commerce-integration-verification.md)、<a id="testing-commerce-integration-verification-dev-recovery"></a>[授权恢复与实际 dev 启动](../testing/20261112-commerce-integration/commerce-integration-verification.md#2026-11-12-授权恢复与实际-dev-启动)：电商整合验证结果。
- <a id="dev-seed-verification"></a>[种子升级事件](../testing/20261106-dev-seed-upgrade/README.md)：隔离验证与授权开发库维护记录。
