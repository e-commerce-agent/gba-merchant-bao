# Proposal

## Why

当前 `api` 仍是 Spring Boot 2.7.3 的三模块餐饮单体，Maven 依赖同时包含旧的 Knife4j、Druid、PageHelper 和已经预置但尚未按业务模块使用的 Redis；数据库迁移目录中的 V1 脚本还会删除并重建旧表，无法安全接管已有数据库。现在需要先建立包结构、依赖治理、Flyway 版本化迁移、Springdoc OpenAPI、日志、测试和可重复租户夹具的基础约束，让后续登录、商品、Agent 等技术模块可以逐个迁移，并在实现某个模块时再引入该模块真正需要的中间件。

## What Changes

- 将后端包结构从按旧餐饮实现堆叠的组织方式，重构为面向业务域和基础设施边界的模块化单体布局，保留必要的兼容适配边界。
- 重整父子 Maven POM，建立目标 JDK/Spring Boot 与核心插件、依赖版本的单一管理位置；移除或标记旧技术栈的迁移状态，避免把未使用的中间件作为全局前置依赖。
- 引入 Flyway 的受控配置和迁移目录约定，将旧数据库接管脚本改造成不可破坏原数据的基线/增量迁移流程，并定义备份、校验、回退或降级证据要求。
- 在基础迁移中建立 `merchant` 租户根表，并为 `local/test` 固定 `M_3C_DEMO` 样板租户和 `M_ISOLATION_TEST` 对照租户；商品、SKU、订单、物流、知识和会话等完整固定数据由对应技术模块逐步追加，生产迁移不加载演示数据。
- 将多商户数据库不变量写入可验收的 schema contract：私有表的非空 `merchant_id`、租户维度唯一键和索引、服务端身份注入、带租户条件的更新/删除、平台级表分类、统一主键和公共字段、金额/时间/状态规则，以及手机号规范化存储和运行时脱敏。
- 引入 Springdoc OpenAPI 3 基础配置和接口文档约定，为后续 `/api/v1` 接口迁移提供统一契约；现有接口按模块逐步迁移，不在本 change 一次性重写全部 Controller。
- 为新 `/api/v1` 接口建立 `code/message/data/requestId` 响应契约、公共错误码和 HTTP 状态映射；旧 `/admin`、`/user` 接口继续保留旧响应格式，迁移时再逐步接入 requestId。
- 建立结构化日志、请求关联标识、异常边界和最小测试分层的基础规范，要求每个后续技术模块同步补齐自己的测试和日志证据。
- 固化“按技术模块引入依赖和中间件”的交付策略：本 change 不实现登录，不新增登录表、Redis 会话、短信或认证依赖；登录模块开始时再单独引入 Redis 及其数据库/接口/测试变更。
- 保留 Agent 四个既有技术亮点作为后续验收基线；基础模块完成后，每开始并完成一个亮点就单独进行量化、评测、留证和归档。本 change 不创建 Agent 评测模板、指标或评测结果。

## Capabilities

### New Capabilities

无。本 change 建立现有应用技术基线的实现约束，不新增独立业务能力。

### Modified Capabilities

- `application-technical-baseline`: 补充增量式 Maven/中间件治理、Flyway 接管旧库和可回退迁移、Springdoc OpenAPI 契约迁移、模块级日志与测试证据要求。

## Impact

- 主要影响 `api/pom.xml`、`api/merchant-*/pom.xml`、`api/merchant-server/src/main` 的包布局和配置，以及 `api/merchant-server/src/main/resources/db/migration` 的迁移脚本组织。
- 影响 HTTP 文档入口、OpenAPI 生成结果、日志字段和测试执行方式；旧接口在完成调用方盘点和兼容验证前不得直接删除。
- 基础迁移新增 `merchant` 根表和 `local/test` 的两个固定租户；后续登录模块将新增 Redis、认证相关数据库表、接口、日志和测试；这些不属于本 change 的实施结果，只在设计和任务中定义触发条件。
- 本 change 不修改前端页面、不实现登录或其他完整业务模块、不引入 RabbitMQ、Milvus、LangChain4j 等尚未被当前模块使用的中间件依赖，也不创建 Agent 评测模板或量化结果。
