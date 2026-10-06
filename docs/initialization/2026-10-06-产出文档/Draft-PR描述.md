# Draft PR 标题

feat(foundation): unify merchantbao baseline and container verification

# Draft PR 描述

## 关联信息

- OpenSpec change：`refactor-foundation-stack-and-migrations`
- 技术模块：后端基础工程、旧模块兼容、Flyway、Springdoc、日志和测试底座。
- 旧接口：保留 `/admin/**`、`/user/**`；新增基础 `/api/v1/health`、`/api/v1/echo`。
- 项目身份：`com.gba:gba-merchant-bao`；新代码根包 `com.gba.merchantbao`，旧代码 `com.gba.merchantbao.legacy`。

## 一、业务梳理

后续商户宝 Agent 电商客服模块需要稳定的工程、租户数据和接口契约。当前旧餐饮代码与新基础代码并存，测试依赖个人配置且存在跳过，审阅者无法仅凭成功构建确认全部基础能力已验收。本次统一命名及旧代码边界，提供可重复的 MySQL/Redis 容器测试，作为后续逐模块交付的基础。

## 二、技术实现

**痛点：** 原 `merchant` 和 `merchantassistant` 两套根包含义不明确；三模块已有合并副本；测试文件被忽略；Docker 缺失时跳过 MySQL，Redis 草稿被永久禁用。

**主方案：** 后端维持 `api/` 单 Maven 应用，新根包 `com.gba.merchantbao`，旧三模块归入 legacy，清理重复源码/POM。统一 JDK 21、Boot 3.5.16、MyBatis、Flyway 和 Springdoc 的基础工程及扫描引用。

**递进：** 保留旧接口兼容契约，建立 requestId 和新响应边界；测试使用独立 test profile 与合成配置。Testcontainers 自动管理临时容器和随机端口，显式关闭复用，不调用开发/生产 Compose 或挂载其数据卷；完整 verify 要求执行容器测试，缺 Docker 时失败并提示启动。清理退役模块及空旧包目录，本地配置/草稿保留到忽略的 `.runtime/legacy-backup`；文档集中到 `api/docs/`，测试日志写 `target/test-logs`，开发日志写 `.runtime/logs`，生产由 `LOG_DIR` 指定外部目录。

**数据流：** 测试连接 Docker Desktop -> 创建隔离 MySQL/Redis -> Flyway 顺序迁移并加载测试租户 -> 应用注入容器连接 -> HTTP/数据库/Redis 断言 -> 输出 Surefire/Failsafe 报告 -> 清理容器。

**核心代码位点：**

- `MerchantApplication`：统一组件扫描根。
- `FoundationController` / `FoundationExceptionHandler` / `RequestIdFilter`：基础接口、错误及请求关联。
- `FlywayMigrationIT`：空库、种子幂等和旧结构 baseline/升级。
- `FoundationApplicationIT`：实际启动、容器连接与 OpenAPI YAML。
- `RedisIT`：旧 Redis 配置的真实操作断言。
- `DockerRequired#check`：Docker 不可用时提示并失败。

## 三、技术深挖

1. 为什么旧代码放 legacy？明确其为兼容实现，避免与后续商户领域混淆；包迁移本身不改变旧业务契约。
2. 为什么使用随机端口？避免占用个人 MySQL/Redis 端口或误连已有业务实例，让测试环境独立且可重复。
3. 为什么 test 成功不等于完整验收？test 运行快速单元/契约检查，verify 还必须执行 Failsafe 容器集成测试。
4. 已有非空库如何接管？先核对结构与备份，显式 baseline 后再升级；默认禁止自动 baseline，已发布迁移不可回改。

## 四、八股文清单

- [ ] Java/Spring：组件扫描、profile、异常映射、MDC、测试生命周期。
- [ ] MySQL/Flyway：baseline、版本顺序、checksum、幂等、租户约束。
- [ ] Redis/Testcontainers：序列化、TTL、随机端口、容器隔离和资源清理。

## API / 数据库 / 中间件

- 基础接口及旧接口兼容边界保持原 change 约定；包重命名不新增业务接口。
- V1/V2/V3 和测试租户种子保持原文件内容；本次未修改已发布迁移。
- Redis 验证已有缓存配置，不实现新登录会话或新增认证表。
- RabbitMQ、Milvus、LangChain4j 和 Agent 业务实现由后续模块 change 引入。

## 验证证据

- [x] `mvn -f api/pom.xml clean verify`：12 个快速测试 + 11 个集成测试，共 23 个，失败 0、错误 0、跳过 0。
- [x] Docker Desktop 29.8.0；Testcontainers 1.20.6；MySQL `8.0.34`；Redis `7.4-alpine`；临时容器使用随机端口。
- [x] 真实应用启动、Flyway 种子、Redis ping、健康接口和 OpenAPI YAML 验证通过。
- [x] OpenSpec 严格校验通过；四类产物同步补充命名和容器验收约束。
- [x] 测试源码解除忽略，运行命令见 `api/docs/testing/README.md`；根 README 提供文档导航。
- [x] 目录治理后重新运行完整 verify：仍为 23 个测试，失败/错误/跳过均为 0；快速和应用集成日志均写入 `api/target/test-logs/application.log`，根目录未重新生成 logs。
- [x] 空旧包及三个退役模块目录已物理清理，三个独有本地文件保留到忽略的 `.runtime/legacy-backup`；测试没有调用业务 Compose、固定宿主端口或挂载业务数据，未残留运行中的测试容器。
- [ ] 测试源码及本次改动已提交并推送到 Draft PR 分支。当前仅完成工作区改动，需随本次提交交付。
- [ ] 本次 Apifox 人工用例与前后端联调。当前自动化结果不代表已执行这些检查。
- [ ] 真实发布库的备份恢复及结构对比。本次容器旧结构测试不替代生产演练。

## 风险与回退

根包及 Maven groupId 变化可能影响 IDE 启动项和外部脚本，应更新为新入口；外部 HTTP 路由保持兼容。测试首次运行需要下载 Maven 依赖和容器镜像。回退应用使用迁移前的 Git 版本及旧应用包；数据库按原迁移交付记录降级，不能回改已执行的迁移或执行 clean。

运行应用时开发工作目录设为 `api/`；生产部署必须设置 `LOG_DIR` 为外部可写日志路径。开发中间件的固定端口在后续实际引入依赖的 change 中登记，本次随机测试端口不作为开发/生产端口配置。作者自行 commit、push 和操作 Draft PR，本次仅交付工作区改动及验证证据。

## 合入检查

- [ ] 本次完整 diff 已由作者复核并提交。
- [ ] 最新 push 已由其他成员审阅，Review conversation 全部解决。
- [ ] PR 描述与实际代码及验证结果一致。
- [ ] 使用 Create a merge commit 合入。
