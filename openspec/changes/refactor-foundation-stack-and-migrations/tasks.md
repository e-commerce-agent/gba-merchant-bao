# Tasks

## 1. 建立迁移基线和兼容清单

- [x] 1.1 盘点当前 `api` 的 Maven 模块、依赖、Java 包、Controller 路由、配置和测试入口，生成可审阅的基线清单；验证：清单能列出 `merchant-common/merchant-pojo/merchant-server`、旧 `/admin`/`/user` 路由、Knife4j/Springfox、Redis 使用点和现有测试文件。
- [x] 1.2 对接管数据库执行只读备份和 `SHOW CREATE TABLE`/结构导出，建立旧表处置矩阵和恢复演练记录；验证：在隔离 MySQL/演练库中能恢复备份并核对表、索引、约束和行数，原库未执行 destructive SQL。
- [x] 1.3 冻结基础租户和固定数据契约：`M_3C_DEMO` 作为样板租户、`M_ISOLATION_TEST` 作为对照租户，并登记后续商品、SKU、订单、物流、知识和会话的业务编码规则；验证：契约明确数据归属、生产不加载范围和跨租户测试矩阵，后续模块只能追加不能改写编码。
- [x] 1.4 为旧接口建立“保留、适配、弃用、退役”清单，并保存迁移前最小回归结果；验证：每个旧 Controller 有调用方状态、兼容策略和对应测试或人工场景证据。

## 2. 重整 Maven 和包结构

- [x] 2.1 将父 POM 锁定 JDK 21、Spring Boot 3.5.16、Maven 编译/测试插件和核心版本管理，删除动态版本；验证：`mvn -f api/pom.xml validate` 通过，依赖树中不存在未声明版本和冲突的 Boot 版本。
- [x] 2.2 将三模块收敛为单 Maven 应用的按业务域包结构，先迁移基础配置、公共响应/异常和一个最小入口，保留旧模块适配层；验证：`mvn -f api/pom.xml test` 通过，目标包能启动，未迁移旧接口仍可回归。
- [x] 2.3 处理 Boot 3 的 `javax.* -> jakarta.*`、旧 Swagger/Springfox、WebSocket、JWT 和 MyBatis 编译兼容问题；验证：全量编译无旧命名空间或 Springfox 引用，`rg` 检查只保留经审查的兼容适配。
- [x] 2.4 删除 Knife4j/Springfox、旧 Swagger 2 注解和无使用证据的全局 PageHelper/Druid 依赖，保留每个仍在运行模块实际需要的依赖；验证：`mvn dependency:tree` 和源码搜索均无误删后的运行时缺类，应用启动与最小回归通过。

## 3. 接入 Flyway 和旧库迁移路径

- [x] 3.1 在应用中加入 Flyway 核心与 MySQL 支持，配置 `validate-on-migrate=true`、`clean-disabled=true`、`out-of-order=false`、`baseline-on-migrate=false` 及独立 `db/devdata` 位置；验证：配置测试确认生产不加载演示种子，`mvn test` 能加载 Flyway 配置。
- [x] 3.2 根据真实旧库导出整理不可破坏的 `V1__legacy_schema_baseline.sql`，移除无条件 `DROP TABLE` 和不可审计的演示数据；验证：空 MySQL/Testcontainers 从 V1 初始化成功，已存在且结构匹配的演练库按记录 baseline 成功，结构不匹配会失败并保留原因。
- [x] 3.3 新增基础 `merchant` 租户根表及 `local/test` 幂等种子，只创建 `M_3C_DEMO` 和 `M_ISOLATION_TEST`，不创建登录账号或其他业务演示数据；验证：空库顺序迁移后两个业务编码各有一行，生产 profile 不加载种子，重复执行不会重复插入。
- [x] 3.4 编写至少一个后续版本示例，记录新增/修改/兼容/退役表、数据映射、行数校验、失败处理和降级步骤；验证：重复执行已应用版本被 Flyway 校验拦截，追加版本可顺序执行，旧脚本文件 hash 未改变。
- [x] 3.5 将迁移备份、恢复、校验和回退证据写入模块交付记录；验证：演练文档包含备份文件、恢复命令、迁移版本表和失败后的应用/数据库降级动作。

## 4. 建立 Springdoc 和增量接口契约

- [x] 4.1 引入与 Boot 3.5.16 兼容的 Springdoc WebMVC starter，配置 `/v3/api-docs`、`/v3/api-docs.yaml` 和本地 Swagger UI，移除旧文档生成配置；验证：应用启动后三个入口按环境策略可访问，生产配置默认关闭或限制 Swagger UI。
- [x] 4.2 将一个最小健康/样例接口迁移到 `/api/v1`，使用 Request/Response、Bean Validation、稳定 `operationId`、`code/message/data/requestId` 响应和公共错误码；验证：导出的 OpenAPI YAML 包含请求、响应、错误、状态码、鉴权描述和 requestId 约定，契约校验通过。
- [x] 4.3 对旧 `/admin`、`/user` 接口保留兼容适配并标注迁移状态，不改变旧字段语义；验证：旧接口回归通过，新旧接口清单与导出的 OpenAPI 快照一致，oasdiff 结果有记录。
- [x] 4.4 为文件上传、异步任务和 SSE 写明各自响应协议，并将 OpenAPI 快照、公共错误码和前后端联调说明纳入模块交付物；验证：普通 JSON、202/task、文件 MIME/413 和 SSE error/done 场景分别通过契约或集成测试。

## 5. 建立日志、异常和分层测试底座

- [x] 5.1 增加 RequestIdFilter/MDC、响应头与 JSON `requestId`、统一异常映射和敏感字段脱敏；验证：带/不带 `X-Request-Id` 的请求均能在响应和日志中关联同一 ID，异常响应不含堆栈、密码、令牌或验证码。
- [x] 5.2 配置 Logback 的本地控制台/滚动文件输出和结构化字段，区分应用事件、审计事件与未来 LLM 记录；验证：并发请求、异步任务和 SSE 结束后 MDC 被清理，日志包含模块、耗时和稳定错误码且无串号。
- [x] 5.3 建立 JUnit 5 + Mockito + AssertJ 单元测试、Spring Boot/Testcontainers MySQL/Flyway 集成测试和 OpenAPI 契约校验入口；验证：单元测试秒级通过，集成测试能从 V1 升级到最新迁移，测试报告可关联提交或 change。
- [x] 5.4 为租户 schema contract、基础种子、包边界、迁移校验、旧接口兼容和日志脱敏补齐测试与运行文档；验证：测试覆盖两租户固定编码、非空/租户维度约束、更新删除带租户条件、已执行 Flyway 版本不可修改、旧接口回归和 requestId/手机号脱敏场景。

## 6. 固化按技术模块增量交付规则

- [x] 6.1 建立模块完成清单模板，要求依赖、配置、中间件、数据库表、接口、日志、测试、OpenAPI 快照和回退证据同一 change 交付；验证：使用模板审查基础样例模块，缺任一证据会阻止标记完成。
- [x] 6.2 明确登录模块的后续触发边界：另开 change 时才加入 Redis starter、登录会话 key、认证表、登录接口、认证日志和 Redis/集成测试；验证：本 change 的 POM、配置、迁移和接口中不存在登录会话实现或新增认证表。
- [x] 6.3 为商品、订单和后续 Agent 亮点保留同样的模块化迁移入口和依赖登记位置；验证：每个入口注明真实依赖后再启用 RabbitMQ、Milvus、LangChain4j 等中间件，不因基础 change 预装空依赖。
- [x] 6.4 固定交付顺序：先完成基础模块和可重复租户夹具，再开始 Agent 亮点；每个 Agent 亮点另开 change，在自身完成时独立进行真实量化、评测、留证和归档；验证：本基础 change 不包含评测模板、指标或结果，后续亮点 change 不等待其他亮点完成。

## 7. 集成验收

- [x] 7.1 在干净环境执行从构建、启动、Flyway V1/V2 演练到 OpenAPI 导出的完整流程；验证：`mvn -f api/pom.xml clean verify`、迁移演练和 `/v3/api-docs.yaml` 校验全部通过，MySQL/Redis 集成测试实际执行，无外部依赖跳过。2026-10-06 Docker Desktop 复验：23 个测试，0 失败、0 错误、0 跳过；结果见 Flyway 迁移交付记录。
- [x] 7.2 复核变更 diff，确认只修改本 change 允许的基础代码和配置，未提前实现登录或 Agent 业务模块；验证：源码、POM、数据库迁移和文档审查无 Redis 登录会话、RabbitMQ/Milvus/LangChain4j 空依赖、量化结果或未登记的破坏性改表。
- [x] 7.3 执行 `openspec validate "refactor-foundation-stack-and-migrations" --type change --strict` 并修复所有错误；验证：change 校验通过，proposal、spec、design、tasks 四类工件均为完成状态。

## 8. 统一项目命名和可交付容器测试

- [x] 8.1 将新根包迁为 `com.gba.merchantbao`，旧三模块合并为其 `legacy` 子包，入口移到新根包并清理重复旧模块源码/POM；同步包声明、扫描配置、Mapper XML、日志类别和 Maven 身份；验证：全量编译通过，活动源码不存在旧包引用，旧路由保留。
- [x] 8.2 将测试源码纳入 Git，修复测试资料路径，提供审阅者可重复执行的快速测试和完整验收命令；验证：测试未被 gitignore 排除，干净克隆无需个人 dev 配置即可运行快速测试。

  实施记录：测试源码已在 `api/src/test`，忽略规则、资料路径、独立 test 配置和运行文档已修复，快速及完整测试通过。`git add` 请求被拒绝，文件尚未暂存/提交，本项先勾选完成；提交时必须包含测试源码。
    手动commit 并推送到远程仓库并合并
- [x] 8.3 以 Testcontainers 自动管理 MySQL/Redis，使用随机端口和独立 test profile，将 Redis 草稿改为带断言的集成测试；完整验收移除静默跳过策略，Docker 不可用时明确失败并提示启动 Docker Desktop；验证：代码不连接本机数据库，快速测试通过，完整验收容器执行结果由 7.1 记录。
- [x] 8.4 更新包结构说明、测试运行记录和 Draft PR 描述，明确真实验证结果和尚未完成项；验证：OpenSpec 严格校验通过，PR 文案不把跳过或未执行的检查描述为成功。

## 9. 容器隔离与后端目录治理

- [x] 9.1 明确测试独立于开发/生产 Compose，显式关闭容器复用，继续使用随机宿主端口和隔离数据；验证：测试源码没有 Compose 调用、固定宿主端口、业务数据卷/网络绑定，完整测试通过，开发固定端口留给后续模块 change。
- [x] 9.2 删除空旧包和三个退役模块的物理目录，先将独有本地配置/草稿保留到忽略的 `.runtime/legacy-backup`；验证：活动配置保留，五个旧目录不存在，备份不进入 Git。
- [x] 9.3 文档迁入 `api/docs/architecture`、`api/docs/testing` 并建立 README 导航；将开发日志归 `.runtime/logs`、测试日志归 `target/test-logs`、生产日志由 `LOG_DIR` 外部配置；验证：所有文档引用一致，完整测试实际写新日志路径，根目录不再出现原专题文件或 logs。
- [x] 9.4 复验并更新交付记录及 Draft PR 描述，保留用户手动 commit/push/PR 的分工；验证：完整测试、目录检查及 OpenSpec 严格校验通过，记录真实结果，不执行 Git 写操作或远程 PR 操作。
