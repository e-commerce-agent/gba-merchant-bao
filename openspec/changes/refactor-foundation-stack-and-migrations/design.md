# Design

## Context

当前 `api` 是 Spring Boot 2.7.3、JDK 兼容旧 `javax.*` API 的 `common/pojo/server` 三 Maven 模块；Controller 使用 Knife4j/Swagger 2 注解，`WebMvcConfiguration` 仍依赖 Springfox，Redis 配置已经在 server 中但并非所有功能都需要它，数据库目录只有一份会 `DROP TABLE` 的餐饮库导出。目标版本和包结构已经在 `docs/initialization/01业务场景-技术模块（AI看）.txt`、数据库、API、测试和日志架构文档中确定，本设计把这些已有决策落成基础 change 的实施顺序。

## Goals / Non-Goals

**Goals:**

- 迁移到 JDK 21、Spring Boot 3.5.16、Maven 3.9.11 的单 Maven 应用，并按业务域分包，保留旧接口适配边界。
- 用 Springdoc OpenAPI 3 替换 Knife4j/Swagger 2 的生成链路，提供 `/v3/api-docs`、YAML 快照和 `/api/v1` 增量迁移约束。
- 用 Flyway 管理旧库基线和后续版本，建立可演练、可校验、不可破坏原数据的迁移路径。
- 建立 `merchant` 租户根表、两个固定租户和按模块递进的测试夹具契约，先固定多租户 schema 不变量，再设计各业务表。
- 统一新接口响应和公共错误码，同时保留旧接口响应兼容，避免一次性改写尚未迁移的调用方。
- 建立按模块归属依赖的 POM 规则、结构化请求日志、MDC requestId、统一异常边界和分层测试入口。

**Non-Goals:**

- 不在此 change 实现登录、认证表、Redis 登录会话、短信验证码或完整权限模型。
- 不在此 change 引入 RabbitMQ、Milvus、LangChain4j、对象存储等尚未被基础模块实际使用的中间件；这些依赖随对应技术模块另开 change。
- 不一次性迁移所有旧 Controller、实体和业务表，不将 `dish`/`setmeal` 重命名冒充通用电商模型。
- 不实现 Agent 技术亮点、评测模板、量化指标或评测结果；基础模块完成后，四个亮点各自另开 change，并在各自完成时独立评测。

## Decisions

### 1. 采用单 Maven 应用和按业务域分包

目标结构为 `com.countmaske.merchantassistant` 下的 `common`、`config`、`auth`、`merchant`、`product`、`inventory`、`order`、`shipment`、`chat`、`knowledge`、`document`、`agent`、`evaluation`、`task` 和 `infrastructure`。每个业务域自带 controller/service/mapper/entity/dto，Controller 只做协议转换和校验，Service 持有业务规则，Mapper 只负责持久化，Agent 只能调用领域 Service。

选择单应用是因为当前只有一个部署单元，三模块没有形成稳定业务边界；按域分包仍保留未来拆分的可能。保留 `merchant-common`、`merchant-pojo`、`merchant-server` 作为短期迁移输入，分阶段移动类和资源，最后再删除空模块，避免一次重命名造成不可审计的破坏。

替代方案是继续扩充三 Maven 模块或直接拆微服务；前者延续空泛共享层，后者会同时引入网络、部署和数据边界，均不适合当前基础 change。

### 2. 锁定现代化版本并集中管理依赖

父 POM 统一锁定 JDK 21、Spring Boot 3.5.16、Maven 编译/测试插件和数据库驱动版本；依赖版本只在父 POM 的 properties/dependencyManagement 出现。基础阶段保留 Spring Web、Validation、Actuator、MyBatis、MySQL、Flyway、Springdoc、JUnit 5/Mockito/AssertJ 等真实需要的依赖，移除 Knife4j/Springfox、旧 Swagger 注解、无使用证据的 PageHelper/Druid 等全局前置依赖。

依赖按模块声明：登录 change 才加入 Redis starter 和认证实现，文档任务 change 才加入 RabbitMQ，RAG change 才加入 Milvus/LangChain4j。每次增依赖必须同时更新配置、失败降级、测试和运行检查。升级到 Boot 3 需要一次性处理 `javax.* -> jakarta.*`、WebSocket、JWT 和 MyBatis 兼容性，并由构建验证锁定版本，不使用动态版本或“latest”。

### 3. 使用 Flyway 接管旧库并保持迁移不可变

迁移目录采用 `V1__legacy_schema_baseline.sql`、`V2__...` 顺序版本，生产/local/test 的演示数据放独立 `db/devdata`。V1 必须来自真实 `SHOW CREATE TABLE`/备份核对，不继续使用会无条件删除数据的重建脚本；已有库在只读备份、结构校验后执行一次性 baseline，空库和 Testcontainers 从 V1 顺序执行到最新版本。

配置默认 `validate-on-migrate=true`、`clean-disabled=true`、`out-of-order=false`、`baseline-on-migrate=false`。已执行脚本永不修改，结构修复追加版本。每个版本在 OpenSpec 设计中先列新增/修改/兼容/退役表和数据校验，生产执行记录备份、行数/约束核对、失败处理和降级步骤。迁移不负责备份本身。

替代方案是继续人工执行 SQL 或打开长期 `baseline-on-migrate`/`clean`；这会让环境状态不可复现并放大误删风险。

基础迁移同时建立最小租户根：`merchant` 表至少包含稳定的 `merchant_code`、名称、状态和时区，并在 `local/test` 通过幂等种子创建 `M_3C_DEMO` 与 `M_ISOLATION_TEST`。这两个业务编码是后续所有测试数据和 Apifox 场景的引用入口，不把自增或 Snowflake 数字 ID 写进测试脚本。商品、SKU、订单、物流、知识和会话数据由对应模块的迁移追加；生产 profile 不加载 `db/devdata`。

数据库设计先冻结跨模块不变量，再逐表决定字段：所有商户私有表使用非空 `merchant_id`；唯一键和高频索引将租户列置于首位；更新和删除 SQL 必须带服务端解析的租户条件；需要数据库引用约束时使用包含 `merchant_id` 的组合关系，避免跨租户引用；平台级表显式标为非租户表。主键生成策略统一，业务表按需使用 `created_at/updated_at/created_by/updated_by/version`，金额使用 `DECIMAL(12,2)` 加币种，库存使用整数，状态使用可读值，时间统一项目时区或 UTC 并在接口说明。手机号只保存规范化原值用于匹配和唯一约束，响应与日志统一调用 `maskPhone`；不把 `phone_masked` 当登录字段或唯一键。

### 4. 采用 Springdoc 代码先行的绞杀式接口迁移

基础配置使用与 Boot 3.5.16 兼容的 `springdoc-openapi-starter-webmvc-ui`，删除 Knife4j/Springfox 生成链路。Controller、Request/Response、Bean Validation 和 Springdoc 注解是实现来源；运行应用导出 `/v3/api-docs.yaml` 版本化快照，供 Apifox、前端联调和 oasdiff 使用。新接口统一 `/api/v1`，旧 `/admin`、`/user` 在调用方盘点和回归证据完成前保留，不在原路径静默改变语义。

基础 change 只迁移文档入口和一个可验证的样例/健康接口，不批量改写所有 Controller。每个后续模块在自己的 design 中审查 API 草案，完成实现、测试、快照和兼容说明后再迁移下一批；删除旧接口另开 change。

新 `/api/v1` 普通 JSON 响应统一为 `{"code":0,"message":"success","data":{},"requestId":"..."}`。HTTP 状态表达协议结果，body `code` 表达稳定的公共或模块业务结果；基础阶段先登记公共/认证/租户段的通用错误码，商品、订单、Agent 等模块在各自 change 中追加。`X-Request-Id` 必须与 JSON `requestId` 一致。旧 `/admin`、`/user` 接口继续使用现有 `code/msg` 形态，直到对应迁移完成；文件上传、异步任务和已建立的 SSE 连接遵循各自协议，不强行套普通 JSON 响应。

### 5. 建立低成本但可关联的日志和测试底座

日志采用 SLF4J + Logback、MDC 和统一异常处理：Filter 接收或生成 `X-Request-Id`，响应头和 JSON 使用相同值；日志至少包含时间、级别、模块、requestId、actor/tenant（存在时）、事件、耗时和稳定错误码，敏感信息脱敏。应用日志、审计事件和未来 LLM 调用记录分开，默认不引入集中式日志产品。

测试分为 JUnit 5 + Mockito + AssertJ 的 Service 单元测试、Spring Boot/Testcontainers 的 MySQL/Flyway 集成测试和 OpenAPI 契约校验。Redis、RabbitMQ、Milvus 只有在某模块真正依赖时才启动对应容器。模块完成门槛是测试结果、迁移结果、OpenAPI 快照和日志字段检查都能关联到提交或 change。

### 6. 以技术模块为交付和依赖边界

本 change 先完成基础构建、包边界、Flyway、租户夹具、Springdoc、日志和测试入口。登录模块开始时另开 change，按“表设计 -> Redis 依赖与配置 -> 认证接口 -> 日志与测试 -> OpenAPI 快照”的顺序交付；商品、订单和 Agent 亮点遵循同样规则。基础模块未达到可运行和可回归前，不开始 Agent 亮点。四个亮点不合并成一个总验收：每个亮点完成自己的代码、测试、观测和真实量化后独立归档；本 change 不提供评测模板或预设指标。

## Risks / Trade-offs

- [风险] Boot 3 的 `jakarta` 迁移与旧 WebSocket/JWT 代码同时变更，短期编译错误较多 → [缓解] 先建立编译基线和兼容适配包，按模块迁移并保留旧接口回归测试。
- [风险] 旧 V1 导出并不等于所有真实数据库实例 → [缓解] 以备份和 `SHOW CREATE TABLE` 核对结果为准，演练库和 Testcontainers 双路径验证，禁止直接在生产库试跑。
- [风险] 单应用包迁移期间出现循环依赖或跨域访问 → [缓解] 每个域限制依赖方向，禁止 Controller/Agent 直接访问其他域 Mapper，构建和架构检查作为任务验收。
- [风险] 旧接口与新 `/api/v1` 并行导致文档和调用方分裂 → [缓解] 旧接口清单、Springdoc 快照、oasdiff 和 Apifox 回归一起提交，删除旧接口必须单独评审。
- [风险] 预置 Redis 配置被误认为登录已完成 → [缓解] 本 change 明确 Redis 仅在已有代码实际需要时保留适配，登录会话 key、表和认证行为全部留给登录 change。
- [风险] 固定测试数据与逐模块迁移不同步，导致 Apifox、集成测试和表设计各自维护一套编码 → [缓解] 先冻结租户和业务编码清单，模块迁移只能追加对应数据并通过业务编码引用，生产 profile 永不加载演示种子。
- [风险] 新旧响应格式并行导致调用方误把旧 `code=1` 当新 `code=0` → [缓解] 按 URL 命名空间区分契约，新 `/api/v1` 和旧路由分别做契约测试，旧接口删除或改格式另开 change。

## Migration Plan

1. 记录当前 Maven 依赖、包、接口、数据库结构和测试基线；为旧库生成可恢复备份和迁移演练库。
2. 将父 POM 切换到 JDK 21/Spring Boot 3.5.16，合并为单 Maven 应用的目标目录；先修复 `jakarta`、WebSocket 和基础编译问题。
3. 引入 Flyway 和 Springdoc 基础配置，冻结 V1 基线规则，建立 `merchant` 根表和两个 `local/test` 固定租户，禁止 destructive SQL；导出并校验 `/v3/api-docs.yaml`。
4. 引入 requestId/MDC、统一异常、Logback 和测试插件，完成基础单元、启动和 Testcontainers MySQL/Flyway 验证。
5. 以兼容适配方式迁移一个最小业务入口，记录旧接口回归、新 `/api/v1` 响应契约和公共错误码；后续模块按独立 change 继续。
6. 基础模块稳定后，按登录、商品/交易、Agent 亮点的顺序逐个开 change；每个 Agent 亮点在自己的 change 完成时独立量化、评测和归档，本基础 change 不创建评测资产。
7. 发布失败时停止新版本、保留旧接口和旧应用包，依据迁移前备份恢复数据库或执行已评审的降级脚本；不得回改已经执行的 Flyway 版本。

## Open Questions

无。具体 Springdoc patch 版本、MyBatis 实现（原生 MyBatis 或后续 MyBatis-Plus）和每个旧表的字段映射在实现对应模块前依据 Maven 构建与真实库核对确定，不改变本 change 的边界或迁移顺序。
