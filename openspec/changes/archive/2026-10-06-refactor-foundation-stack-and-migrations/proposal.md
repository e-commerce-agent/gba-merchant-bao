# Proposal

## Why

当前 `api` 仍是 Spring Boot 2.7.3 的三模块餐饮单体，Maven 依赖同时包含旧的 Knife4j、Druid、PageHelper 和已经预置但尚未按业务模块使用的 Redis；数据库迁移目录中的 V1 脚本还会删除并重建旧表，无法安全接管已有数据库。现在需要先建立包结构、依赖治理、Flyway 版本化迁移、Springdoc OpenAPI、日志、测试和可重复租户夹具的基础约束，让后续登录、商品、Agent 等技术模块可以逐个迁移，并在实现某个模块时再引入该模块真正需要的中间件。

## What Changes

- 后端工程保留在仓库根目录 `api/`，新项目统一使用 `com.gba.merchantbao` 根包；原 `com.countmaske.merchantassistant` 基础组件迁入该根包，旧三模块的 `com.countmaske.merchant` 代码收敛到 `com.gba.merchantbao.legacy`，保留旧接口行为并清理重复模块源码。
- 重整父子 Maven POM，建立目标 JDK/Spring Boot 与核心插件、依赖版本的单一管理位置；移除或标记旧技术栈的迁移状态，避免把未使用的中间件作为全局前置依赖。
- 引入 Flyway 的受控配置和迁移目录约定，将旧数据库接管脚本改造成不可破坏原数据的基线/增量迁移流程，并定义备份、校验、回退或降级证据要求。
- 在基础迁移中建立 `merchant` 租户根表，并为 `local/test` 固定 `M_3C_DEMO` 样板租户和 `M_ISOLATION_TEST` 对照租户；商品、SKU、订单、物流、知识和会话等完整固定数据由对应技术模块逐步追加，生产迁移不加载演示数据。
- 将多商户数据库不变量写入可验收的 schema contract：私有表的非空 `merchant_id`、租户维度唯一键和索引、服务端身份注入、带租户条件的更新/删除、平台级表分类、统一主键和公共字段、金额/时间/状态规则，以及手机号规范化存储和运行时脱敏。
- 引入 Springdoc OpenAPI 3 基础配置和接口文档约定，为后续 `/api/v1` 接口迁移提供统一契约；现有接口按模块逐步迁移，不在本 change 一次性重写全部 Controller。
- 为新 `/api/v1` 接口建立 `code/message/data/requestId` 响应契约、公共错误码和 HTTP 状态映射；旧 `/admin`、`/user` 接口继续保留旧响应格式，迁移时再逐步接入 requestId。
- 建立结构化日志、请求关联标识、异常边界和最小测试分层的基础规范，要求每个后续技术模块同步补齐自己的测试和日志证据。
- 测试源码必须随实现提交；MySQL/Redis 集成测试由 Testcontainers 在 Docker Desktop 内自动创建和销毁隔离容器，使用随机端口，不连接本机 MySQL 或固定 Redis。完整 `mvn clean verify` 验收不允许因 Docker 不可用而静默跳过，也不允许永久禁用 Redis 测试；缺 Docker 时提示启动 Docker Desktop 后重新运行。
- 自动化测试独立于开发/生产 Compose：禁止调用业务 Compose、固定宿主端口、容器复用或挂载业务数据卷；开发固定端口由后续实际依赖中间件的 change 登记。清理迁移后的空旧包和退役模块物理目录，独有本地文件保留到忽略的 `.runtime/legacy-backup`。
- 后端维护文档集中到 `api/docs/architecture` 和 `api/docs/testing`，根 README 仅作为入口；开发日志写入 `.runtime/logs`（后端工作目录），Maven 测试日志强制写入 `api/target/test-logs`，生产日志由 `LOG_DIR` 指定外部目录。
- 固化“按技术模块引入依赖和中间件”的交付策略：本 change 不实现登录，不新增登录表、Redis 会话、短信或认证依赖；登录模块开始时再单独引入 Redis 及其数据库/接口/测试变更。
- 保留 Agent 四个既有技术亮点作为后续验收基线；基础模块完成后，每开始并完成一个亮点就单独进行量化、评测、留证和归档。本 change 不创建 Agent 评测模板、指标或评测结果。

## Capabilities

### New Capabilities

无。本 change 建立现有应用技术基线的实现约束，不新增独立业务能力。

### Modified Capabilities

- `application-technical-baseline`: 补充增量式 Maven/中间件治理、Flyway 接管旧库和可回退迁移、Springdoc OpenAPI 契约迁移、模块级日志与测试证据要求。

## Impact

- 主要影响 `api/pom.xml`、`api/src/main` 和 `api/src/test` 的包布局、配置及测试入口；清理已合并的 `api/merchant-*` 重复模块文件。迁移脚本由 `api/src/main/resources/db/migration` 管理，已发布版本保持不可变。
- 影响 HTTP 文档入口、OpenAPI 生成结果、日志字段和测试执行方式；旧接口在完成调用方盘点和兼容验证前不得直接删除。
- 基础迁移新增 `merchant` 根表和 `local/test` 的两个固定租户；后续登录模块将新增 Redis、认证相关数据库表、接口、日志和测试；这些不属于本 change 的实施结果，只在设计和任务中定义触发条件。
- 本 change 不修改前端页面、不实现登录或其他完整业务模块、不引入 RabbitMQ、Milvus、LangChain4j 等尚未被当前模块使用的中间件依赖，也不创建 Agent 评测模板或量化结果。
