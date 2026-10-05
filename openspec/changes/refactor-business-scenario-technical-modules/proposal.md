# Proposal

## Why

现有设计手稿在“原来的业务场景和技术模块”之后缺少重构后的 3C 配件电商客服业务场景和技术模块说明。现在需要只补齐初始化设计手稿中的新内容，冻结业务范围、角色入口、技术模块和后续 OpenAPI 驱动的协作方式，供后续开发 change 使用。

本次 change 保留商户宝 Agent 电商客服已经确定的四个技术亮点及其业务场景：RAG 知识库问答、Function Calling 业务工具、RabbitMQ 批量文档异步向量化、Redis ChatMemory + MySQL 对话历史。它们是本次重构的既有约束，不在本 change 中重新设计。

## What Changes

- 只在 `docs/initialization/设计手稿.md` 原有业务场景和技术模块内容下方补充 3C 配件电商客服的新业务场景和技术模块。
- 在新建的 `docs/initialization/重构后的角色-路由-资源矩阵.md` 中固化多商户租户边界、角色职责、业务入口、前端路由和资源访问矩阵。
- 在该独立初始化文档中增加 Agent 四个既有技术亮点冻结清单；不重新设计、不改写其业务场景和技术模块。
- 记录前端协作方式：前端不以 `view/` 目录为本次审计对象，而是依据后端导出的 Springdoc OpenAPI 文档开发，后续按技术模块逐步联调。
- 本 change 只产出规划文档和 OpenSpec 工件，不实现商品、库存、订单、物流服务，不编写单元测试，不执行数据库迁移。

## Capabilities

### New Capabilities

- `merchant-ecommerce-scope`: 3C 配件多商户电商的角色、入口、商品交易领域和租户/资源访问边界。
- `application-technical-baseline`: 支撑上述业务范围的模块化单体技术基线、接口/迁移约束和基础设施职责边界。

### Modified Capabilities

无。当前 `openspec/specs/` 没有既有 capability；Agent 四个亮点也不属于本 change 的修改范围。

## Impact

- 文档：修改 `docs/initialization/设计手稿.md` 的新业务场景和技术模块补充段落，并新建 `docs/initialization/重构后的角色-路由-资源矩阵.md`；`docs/design/04商户宝业务场景_技术模块.txt` 保持不变。
- 后续实现输入：新文档将作为后续 API、数据库、Agent 和基础设施 change 的范围依据；本 change 不直接修改 `api/` 或 `view/`。
- 前端协作：以 Springdoc 导出的 OpenAPI 文档作为接口依据，`view/` 不被假定为当前仓库内的前端实现目录。
- Agent：仅作为冻结的依赖和验收边界，后续实现必须继续使用其四个既有亮点，不得借本 change 扩大到 MCP、多 Agent、外贸工作流或写操作 Tool。
