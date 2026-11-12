# 测试文档

事件目录采用 `YYYYmmdd-事件`，日期为实际结束日（Asia/Shanghai）；同一跨日事件集中保存。已完成事件停止续写，新事件另建目录并增加一行原因与用途。勘误由明确的新变更关联原记录；日期证据不足时保留未决状态，不以整理日补造日期。

## 本目录文件

- [automation.md](automation.md)：自动化测试环境、运行方法和覆盖范围。

## 相关资料

- <a id="agent-apifox-verification"></a>[agent / apifox-verification.md](../agent/apifox-verification.md)：隔离服务和 Apifox 导入用例。
- <a id="agent-manual-verification"></a>[agent / manual-verification.md](../agent/manual-verification.md)：人工联调步骤。
- <a id="auth-data-design"></a>[auth / data-design.md](../auth/data-design.md)：数据结构和边界。
- <a id="auth-manual-verification"></a>[auth / manual-verification.md](../auth/manual-verification.md)：人工联调步骤。
- <a id="deployment-dev-seed-upgrade"></a>[deployment / dev-seed-upgrade.md](../deployment/dev-seed-upgrade.md)：开发种子升级步骤。
- <a id="deployment-local-development"></a>[deployment / local-development.md](../deployment/local-development.md)、<a id="deployment-local-development-v9-upgrade"></a>[V5 开发库升级至 V9](../deployment/local-development.md#v5-开发库升级到商品库存订单物流-v9)：本地开发和升级步骤。

## 验证事件

- [运行与部署基线验收](20261101-runtime-architecture/README.md)：运行架构整理引起的验收，用于核对隔离测试、配置与生产准备状态。
- <a id="auth-verification"></a>[认证模块验收](20261106-auth-acceptance/README.md)：认证模块交付引起的验收，用于核对自动化、联调与未交付边界。
- [auth 包迁移](20261106-auth-package-migration/README.md)：认证三层包迁移引起的验证，用于核对命令、数量与结果。
- [开发种子历史升级验证](20261106-dev-seed-upgrade/README.md)：开发种子改名维护引起的验证，用于核对隔离演练及授权开发库升级结果。
- [Agent dev Flyway V5 维护](20261108-agent-dev-flyway/README.md)：Agent 会话迁移引起的开发库维护，用于核对备份、V5 与恢复启动证据。
- <a id="agent-verification"></a>[Agent 基础验收](20261108-agent-foundation/README.md)：Agent 基础与对话交付引起的自动化验收，用于核对 RT/CV 证据和限制。
- [Agent 持久化阶段验证](20261108-agent-persistence/README.md)：Agent 会话存储实施引起的阶段验证，用于核对 46 项定向结果及边界。
- [Agent 真实模型验证](20261108-agent-real-model/README.md)：兼容模型接入引起的联调，用于核对 SSE、历史与 MySQL 闭环。
- [Agent Apifox 阶段记录](20261109-agent-apifox/README.md)：Apifox 导入和客户端受阻引起的阶段记录，用于保留“不做”和“待完成”的原始差异。
- <a id="agent-regression"></a>[Agent HTTP 取消回归](20261110-agent-regression/README.md)：全量运行时 HTTP 取消偶发超时引起的复验，用于保留首轮失败与重跑结果。
- [物流模块验收](20261110-shipment-acceptance/README.md)：物流 REST/SOAP 交付引起的验收，用于核对测试、契约与限制。
- <a id="commerce-migration-verification"></a>[电商迁移与种子定向验证](20261111-commerce-migration/README.md)：电商迁移重排引起的定向验证，用于核对第 1、2 组的测试命令与结果。
- [库存雪花主键验收](20261111-inventory-snowflake/README.md)：库存主键统一引起的定向验收，用于核对隔离命令、数量与范围。
- [商品公开目录定向回归](20261111-product-catalog/README.md)：公开店铺路径和库存布尔整合引起的回归，用于核对两轮隔离验证结果。
- [订单与物流整合验证](20261111-shipment-order-integration/README.md)：物流改用订单授权服务引起的验证，用于核对成交快照与 REST/SOAP 隔离结果。
- <a id="commerce-integration-verification"></a><a id="deployment-commerce-dev-recovery"></a>[电商模块关联整合验证](20261112-commerce-integration/README.md)：电商模块关联整合引起的验证，用于追溯跨日验收、失败恢复和实际开发库结果。
