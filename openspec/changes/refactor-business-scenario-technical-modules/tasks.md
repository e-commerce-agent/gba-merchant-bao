# 任务

## 1. 确认文档边界

- [ ] 1.1 确认 `docs/design/04商户宝业务场景_技术模块.txt` 作为已完成的旧商户宝资料保持不变；验收：变更 diff 不包含该文件。
- [ ] 1.2 确认新的业务场景和技术模块只追加到 `docs/initialization/设计手稿.md` 原有业务场景和技术模块内容下方；验收：旧段落保持原样，新段落有明确标题和插入位置。
- [ ] 1.3 明确本 change 只负责文档规划，不实现商品、库存、订单、物流服务，不编写单元测试，不执行数据库迁移；验收：tasks、design 和 proposal 的范围描述一致。

## 2. 补充新的业务场景和技术模块

- [ ] 2.1 在 `docs/initialization/设计手稿.md` 中追加 3C 配件电商客服业务场景：商品/SKU、库存、订单、物流、售后知识、商户运营、消费者和人工客服使用方式；验收：业务目标、角色和范围外事项清晰可读。
- [ ] 2.2 在设计手稿的业务场景下方追加目标技术模块及职责：JDK/Spring Boot、模块化单体、MySQL、Redis、RabbitMQ、LangChain4j、Milvus、Flyway、Springdoc、SSE 和对象存储；验收：每个模块说明其服务对象和边界，且没有写成已完成实现。
- [ ] 2.3 新建 `docs/initialization/重构后的角色-路由-资源矩阵.md` 并写入角色/路由/资源矩阵；验收：覆盖未登录访问者、消费者、商户客服和延期的平台管理员，以及 `/public/**`、`/consumer/**`、`/merchant/**`、`/platform/**` 路由。
- [ ] 2.4 在 `docs/initialization/重构后的角色-路由-资源矩阵.md` 中增加 Agent 四亮点冻结清单；验收：RAG、Function Calling、RabbitMQ 批量文档异步向量化、Redis ChatMemory + MySQL 对话历史四项均保留原业务定义，不新增写操作 Tool、MCP、多 Agent 或外贸工作流。
- [ ] 2.5 在设计手稿或独立初始化文档中记录前端协作方式：前端依据后端导出的 Springdoc OpenAPI 文档开发，完成一个技术模块后再同步接口和联调；验收：文档明确 `view/` 不是本次前端实现目录。

## 3. 文档一致性审查

- [ ] 3.1 审查新旧业务场景和技术模块的衔接；验收：旧内容未被覆盖，新内容位于其下方，`docs/design/04商户宝业务场景_技术模块.txt` 没有 diff。
- [ ] 3.2 审查独立初始化文档中的角色/路由/资源矩阵和 Agent 四亮点冻结清单；验收：矩阵与清单和原有 Agent 业务场景及技术模块一致，没有把四亮点改成待重构内容。
- [ ] 3.3 审查前端协作描述和技术范围；验收：明确 OpenAPI 驱动开发，且没有把商品、库存、订单、物流只读服务或单元测试列为本 change 的实施结果。
- [ ] 3.4 执行 `openspec validate "refactor-business-scenario-technical-modules" --type change --strict`；验收：change 校验通过，且所有规划工件状态为完成。
