# Design

## Context

当前 `api/` 仍是 Spring Boot 2.7.3 的 `merchant-common`、`merchant-pojo`、`merchant-server` 三模块，持久化模型和接口仍围绕旧餐饮系统。`docs/design/04商户宝业务场景_技术模块.txt` 是已经写好的旧商户宝业务场景和技术模块资料，本 change 不修改它。新业务场景和技术模块补充到 `docs/initialization/设计手稿.md` 原有内容下方；角色/路由/资源矩阵和 Agent 冻结清单单独放入 `docs/initialization/重构后的角色-路由-资源矩阵.md`。前端依据 Springdoc 导出的 OpenAPI 文档开发，`view/` 不是本次前端实现审计对象。

## Goals / Non-Goals

**Goals:**

- 在设计手稿中补齐 3C 配件电商的业务场景和技术模块，并在独立初始化文档中补齐角色/路由/资源矩阵和 Agent 四亮点冻结清单。
- 明确后续开发以 Springdoc 导出的 OpenAPI 文档为前后端协作契约。
- 为后续实现 change 提供范围清晰的文档基线。

**Non-Goals:**

- 本 change 不修改 `docs/design/04商户宝业务场景_技术模块.txt`。
- 本 change 不实现业务代码、Flyway 脚本、商品/库存/订单/物流服务、单元测试或前端页面。
- 不在本期加入退款、改价、发货等 Agent 写操作 Tool，不扩展 MCP、多 Agent、外贸工作流或微服务。

## Decisions

### 1. 以设计手稿作为新范围的文档落点

将新的业务场景和技术模块补充在 `docs/initialization/设计手稿.md` 原有“业务场景+技术模块”内容下方，与旧内容形成历史基线和新目标的连续记录。将角色/路由/资源矩阵和 Agent 冻结清单放入独立的 `docs/initialization/重构后的角色-路由-资源矩阵.md`。`docs/design/04商户宝业务场景_技术模块.txt` 保持原样，不把旧资料改写成新系统说明。

设计手稿中的技术模块只描述目标架构和职责，不代表本 change 已完成代码实现。

### 2. 在独立初始化文档中明确角色、路由和资源矩阵

独立初始化文档中记录认证主体、租户、角色、路由和资源访问关系：公开商城使用店铺编码解析租户；匿名访问不创建 guest 账号；消费者只访问自己的订单、物流、会话和反馈；商户客服只访问所属商户；平台管理员保留为后续扩展。

该矩阵是后续实现授权和 Agent Tool 身份注入的规划依据，不在本 change 中实现授权代码。

### 3. 使用 Springdoc OpenAPI 作为前端协作契约

后续新接口使用 `/api/v1`，由后端 Controller、Request/Response、校验和 Springdoc 注解生成 OpenAPI 文档；前端依据导出的文档开发和联调。每完成一个技术模块，再同步该模块的接口文档和前端工作。

本 change 只记录这种协作方式，不实施接口迁移、适配层或前端代码。

### 4. 在独立初始化文档中冻结 Agent 四个既有技术亮点

独立初始化文档中只列出并冻结 RAG 知识库问答、Function Calling 只读业务工具、RabbitMQ 批量文档异步向量化、Redis ChatMemory + MySQL 对话历史四个亮点及其既有业务场景。它们不在本 change 中重写或实现。

### 5. 技术模块只作为后续实现输入

新技术模块章节可描述 Spring Boot/JDK、模块化单体、MySQL、Redis、RabbitMQ、LangChain4j、Milvus、Flyway、Springdoc、SSE 和对象存储等目标模块及职责，但本 change 只负责写清楚这些规划，不负责实现任何模块。

## Risks / Trade-offs

- [风险] 新旧业务场景在文档中混淆 → [缓解] 保留旧段落原样，只在其下方增加新目标段落，并在审查任务中检查边界。
- [风险] 技术模块文档被误解为已完成实现 → [缓解] 在文档和 tasks 中明确本 change 只做规划，代码实现另开 apply/change。
- [风险] 前端接口理解与后端实现不同步 → [缓解] 约定以前端使用后端导出的 Springdoc OpenAPI 文档为准，按模块更新和联调。

## Migration Plan

1. 在 `docs/initialization/设计手稿.md` 原有内容下方补充新业务场景和技术模块，并新建独立初始化文档记录角色/路由/资源矩阵和 Agent 冻结清单。
2. 审查新旧段落边界，确认 `docs/design/04商户宝业务场景_技术模块.txt` 未被修改，且 Agent 四亮点没有被重写。
3. 审查 OpenAPI 驱动的前后端协作说明，确认没有把 `view/` 错当成本次前端实现目录。
4. 通过 OpenSpec 校验后结束本 change；商品、库存、订单、物流服务以及单元测试另行进入后续实现 change。

## Open Questions

无。供应商选型、数据库迁移和领域服务实现均明确留给后续 change，不影响本次文档规划。
