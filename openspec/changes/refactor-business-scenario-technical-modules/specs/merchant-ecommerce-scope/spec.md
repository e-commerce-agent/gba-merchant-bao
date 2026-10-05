# Spec Delta

## Purpose

定义商户宝重构后的 3C 配件多商户电商业务范围、访问主体和交易数据边界，
让公开商城、消费者中心与商户客服工作台共享同一套可实现、可验收的业务契约。

## ADDED Requirements

### Requirement: The product shall expose three role-separated business entries

系统 MUST 在同一 Web 应用中提供公开商城、消费者中心和商户客服工作台三个业务入口，并按主体限制可见功能；平台管理后台只能作为后续扩展入口，不得成为本期核心流程的前置依赖。

#### Scenario: Anonymous visitor browses public commerce data
- **WHEN** 未登录访问者访问公开商城
- **THEN** 系统允许查看已公开的商品、SKU、公开库存状态和公开知识问答，但拒绝订单、物流、历史会话和私有工具数据

#### Scenario: Consumer accesses own commerce data
- **WHEN** 已登录消费者访问消费者中心
- **THEN** 系统允许其发起 Agent 会话并查询本人订单与物流，不返回其他消费者或其他商户的数据

#### Scenario: Merchant staff operates the workspace
- **WHEN** 商户管理员或客服访问商户客服工作台
- **THEN** 系统只展示其所属商户的商品、SKU、库存、知识库、任务和会话工作台能力

### Requirement: Commerce resources shall be tenant-scoped

商品、SKU、库存、订单、物流、知识文档、会话和反馈等业务资源 MUST 绑定商户租户；任何读取或写入都必须依据服务端主体上下文校验商户归属，客户端传入的 merchantId 不能覆盖该上下文。

#### Scenario: Cross-tenant resource access is rejected
- **WHEN** 主体请求另一个商户的商品、订单、文档或会话资源
- **THEN** 系统拒绝请求并不泄露资源是否存在、私有字段或可用于枚举的详细信息

#### Scenario: Tenant context is derived server-side
- **WHEN** 请求同时携带与登录主体不一致的 merchantId
- **THEN** 系统使用服务端解析出的租户上下文并拒绝越权操作

### Requirement: The commerce domain shall use 3C product and transaction concepts

系统 MUST 以商品/SPU、SKU 属性、库存、订单及订单项快照、物流及轨迹作为 3C 配件电商的核心业务概念，并支持兼容性、规格差异、可售状态、售后政策和物流状态等客服问题所需的数据。

#### Scenario: Product and SKU data support compatibility questions
- **WHEN** 用户查询手机型号兼容性、接口、功率、长度、颜色或套装差异
- **THEN** 系统从结构化商品/SKU 数据或商户知识内容返回对应信息，而不是把餐饮菜品模型当作电商商品模型

#### Scenario: Historical order details remain stable
- **WHEN** 用户查看已完成订单
- **THEN** 系统使用订单项保存的商品名称、SKU 属性、成交单价和数量快照，不因当前商品资料变化而改写历史交易含义

### Requirement: The existing Agent business scope shall remain unchanged

本次业务重构 MUST 保留商户宝 Agent 电商客服的四个既有技术亮点及对应场景：RAG 知识库问答、Function Calling 只读业务工具、RabbitMQ 批量文档异步向量化、Redis ChatMemory 与 MySQL 对话历史；本 change 不新增写操作 Tool、MCP、多 Agent 或外贸工作流。

#### Scenario: Agent four-highlight contract is preserved
- **WHEN** 后续实现依据本 change 调整业务或数据模块
- **THEN** Agent 仍能围绕商品/售后知识、订单/库存/物流查询、文档异步导入和多轮历史对话接入这些模块，且四个亮点的业务定义不被改写

#### Scenario: Out-of-scope Agent expansion is rejected
- **WHEN** 需求试图在本 change 中加入退款、改价、发货等写操作 Tool，或加入外贸工作流、多 Agent 编排
- **THEN** 该需求被识别为后续独立 change，不进入本次重构范围
