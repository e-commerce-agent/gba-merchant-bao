# Spec Delta

## Purpose

定义与 3C 多商户业务匹配的模块化单体技术基线、接口契约、迁移边界和基础设施职责，
使后续实现能够在不重写 Agent 四个亮点的前提下逐步替换旧餐饮模型。

## ADDED Requirements

### Requirement: The application shall be organized by business domain

后端 MUST 以认证、商户、商品、库存、订单、物流、会话、知识库、文档任务、Agent 和评测等业务域组织模块；协议转换、业务规则和持久化访问必须有清晰职责边界，Agent 编排不能复制交易域规则。

#### Scenario: Domain service owns business rules
- **WHEN** Agent Tool 或 HTTP Controller 需要查询订单、库存或物流
- **THEN** 调用对应业务域服务并复用其租户、用户归属和隐私规则，不能绕过服务直接访问 Mapper 或数据库

#### Scenario: A new domain does not require renaming old models
- **WHEN** 实现商品、SKU 或订单模块
- **THEN** 使用独立的通用电商模型，并为旧餐饮模型建立明确的适配、迁移或退役关系

### Requirement: Protected APIs shall use one versioned contract and server-side identity

受保护接口 MUST 使用统一的 `/api/v1` 契约、稳定 HTTP 状态和业务码，并从认证上下文解析主体、角色和租户；订单、物流、会话及知识库接口必须声明其资源归属规则。

#### Scenario: A consumer requests a protected resource
- **WHEN** 消费者调用订单、物流或会话接口
- **THEN** 系统先验证登录会话和资源归属，再返回脱敏后的允许字段；缺少权限时返回明确的 401/403 结果

#### Scenario: API contract is documented
- **WHEN** 新增或变更 `/api/v1` 接口
- **THEN** 请求、响应、校验错误、鉴权方案、异步任务状态和 SSE 事件都能从同一份 OpenAPI 契约导出

### Requirement: Schema evolution shall be versioned and reversible

数据库结构 MUST 通过版本化迁移演进；旧库接管、新电商表、数据映射和退役步骤必须可追踪。已执行迁移不得原地修改，生产变更前必须具备备份与回退/降级路径。

#### Scenario: A fresh database is initialized
- **WHEN** 在空数据库启动应用
- **THEN** 系统按顺序执行基线和后续迁移，建立当前所需的电商表、索引和约束

#### Scenario: An existing restaurant database is migrated
- **WHEN** 对已有旧餐饮库执行重构迁移
- **THEN** 系统先完成结构核对和备份，再按保留、适配、映射或退役清单处理旧表，不静默删除旧数据或把旧表改名伪装成新领域

### Requirement: Infrastructure responsibilities shall be explicit

Redis、RabbitMQ、对象存储、向量数据库和关系数据库 MUST 按职责分工；缓存、异步任务、文档向量化、会话记忆和完整历史不得互相替代，失败状态必须可观测并可恢复。

#### Scenario: A dependency is unavailable
- **WHEN** Redis、RabbitMQ、Milvus 或对象存储暂时不可用
- **THEN** 系统按所属模块的降级或重试约定返回可识别错误，并保留可查询的业务状态，不把中间件可用性当作唯一事实来源

#### Scenario: Agent dependencies integrate with the baseline
- **WHEN** 接入既有 Agent 四个亮点
- **THEN** RAG、Function Calling、RabbitMQ 文档任务和 Redis ChatMemory 使用各自的领域接口与数据隔离规则，不改变其已确定的业务场景

### Requirement: The refactor shall preserve compatibility evidence

重构 MUST 为旧接口和旧前端调用建立保留、适配、弃用或退役清单，并记录每个迁移阶段的回归证据；本 change 不得直接删除尚未确认调用方的旧能力。

#### Scenario: A legacy endpoint is still in use
- **WHEN** 旧 `/admin` 或 `/user` 接口仍有调用方
- **THEN** 系统在兼容期保留或提供适配，并明确新旧语义、认证方式和退役条件

#### Scenario: A legacy endpoint is retired
- **WHEN** 旧接口完成调用方确认、数据对账和回归验证
- **THEN** 系统按记录的退役步骤下线，并保留迁移说明和回退策略
