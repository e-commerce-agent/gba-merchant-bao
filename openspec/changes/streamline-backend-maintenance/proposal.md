# Proposal

## Why

认证、商品、库存、订单、物流及其数据关联已经交付，但测试平铺、运行材料混杂，文档正文交叉导航与临时验收流水账使单人后端开发难以定位资料。进入 Agent 开发前，需要集中维护文件关系、冻结五个基础模块的历史设计，并让工具和临时记录各归其位。

## What Changes

- 建立单一维护链路：OpenSpec 保存当前需求与变更，`docs/interview` 保存讲解材料，GitHub 组织仓库的 commit/PR 保存交付证据；API 以 Springdoc 导出为准，DDL/DML 以现有 V/R/fixture SQL 为准。
- 根 `README.md` 和整个 `api/docs/prod-profile/**` 保持原样。其余文档目录（含 architecture、agent、testing、deployment）以各自 README 集中链接文件并说明用途；正文保留必要语境与事实，需要查找其他说明文档时只链接所属目录 README 的对应条目。
- 五个基础模块先整理导航、迁出脚本和临时验收记录，再分别冻结到 `docs/archive/YYYYmmdd-auth/`、`YYYYmmdd-product/`、`YYYYmmdd-inventory/`、`YYYYmmdd-order/`、`YYYYmmdd-shipment/`。日期为实际冻结日，保留现有文件名，不补原来没有的文档；Agent 文档继续留在当前目录。
- 散落于 architecture、Agent 和其他文档的临时验证记录统一迁到 `api/docs/testing/YYYYmmdd-事件/`；日期为事件结束日，跨日事件不强拆。testing README 每个事件一行说明原因和用途，结束后停止续写；日常操作说明留所属目录。
- 技术规则、场景编号、测试类名、主要断言和验收结论原文保留，允许整段迁移和机械同步实际路径；导航整理不能变成改写结论。冻结基线在一次整理之后建立，通过来源记录、哈希和版本化 Agent 规则防止后续误改。
- 将测试迁入 `test.auth/product/inventory/order/shipment/agent/chat/common/foundation/integration/support`，维护共享辅助类、继承关系与 Maven 发现规则，保留原有测试和断言。
- 保存并提交本 change 的 artifacts/ 下保护、文件映射和测试发现三份 JSON 基线，原始报告仍留忽略的 target；后续仅修正已确认的商品/认证 OpenAPI 分组断言归属，不改运行接口，保留初始失败结果供对比，最终完整验证须通过。
- 可复用脚本按执行目标进入 `api/scripts/prod/`、`dev/`、`test/`、`tools/`，每个实际目录有简短 README；文档归档不留脚本副本。prod-profile 内原脚本原地保留、不复制；生产手稿仍调用的旧 backup-dev 入口仅保留转发。
- `.runtime` 只承担本地日志与运行状态；一次性脚本、备份和旧输出保全到忽略的备份目录，Maven 产物留在 `target`，所有本次新建的带日期事件/归档目录统一使用日期前缀。

## Capabilities

### New Capabilities

无。复用既有技术基线，不另建覆盖相同职责的规范。

### Modified Capabilities

- `application-technical-baseline`：调整文档与运行文件位置要求，定义目录 README 导航、日期前置的模块冻结和事件记录、脚本分类与按域组织测试。既有部署、数据隔离、日志及迁移约束继续有效。

## Impact

- 实施将涉及 `api/README.md`、根 `AGENTS.md`、五个模块档案、`api/docs`（排除 prod-profile）、`api/scripts`、测试 Java 包与相关调用路径；仅按需修复 interview 和 SQL 目录 README 的导航。根 README、prod-profile、SQL 正文均不改。
- 运行时 REST/SOAP/SSE、生产配置、业务 Service/Mapper、数据库结构、V/R/fixture SQL 内容与租户隔离行为均不变；不实现新的 Agent 功能，不改前端，不增中间件或 CI 系统。
- 测试完整类名和维护脚本路径发生调整，需提供新入口并修复有效引用；Springdoc 快照保持生成性质，不手写第二份接口契约。
- 组织仓库已是 `e-commerce-agent/gba-merchant-bao`，沿用现有仓库和 PR 流程，不新建仓库或执行远程发布。旧 OpenSpec change 不自动归档、不补勾未验证任务。
- 本轮只生成规划工件；实施后才执行文件迁移和回归验证。
