# 后端包结构说明

`api/` 是单个后端 Maven 应用。Maven 坐标为 `com.gba:gba-merchant-bao`，Java 根包为 `com.gba.merchantbao`。

```text
api/src/main/java/com/gba/merchantbao/
  MerchantApplication.java
  api/                 基础 HTTP 接口样例和异常处理
  common/api/          公共响应格式
  common/error/        公共错误码
  common/privacy/      展示和输出时的隐私脱敏
  config/              基础配置
  infrastructure/web/  请求关联标识等 Web 基础设施
  auth/controller/     九个认证操作的 HTTP 入口
  auth/service/        账号用例、短信挑战、限流、JWT 与会话生命周期
  auth/mapper/         认证账号及商户查询的 MyBatis Mapper
  auth/entity/         账号数据库 Row 与商户数据
  auth/dto/            请求 DTO 与校验声明
  auth/security/       Actor、可信主体、安全过滤器与刷新凭证加密
  auth/cache/          Redis 访问与 Lua 脚本执行
  auth/handler/        Controller 异常与 HTTP 错误适配
  auth/exception/      认证异常
  auth/enums/          账号角色
  auth/util/           输入规范化与安全随机/摘要工具
  auth/logging/        脱敏认证审计
  auth/config/         运行校验、双安全链与 Springdoc 分组
  agent/config/        模型开关、配置校验与 LangChain4j 装配
  agent/service/       AgentModel 边界、OpenAI 兼容适配、有限重试与上下文预算
  chat/controller/     六个对话操作与串行 SSE 写入
  chat/service/        本人授权、事务、共享运行权、幂等与终态竞争
  chat/mapper/         会话/消息 MyBatis Mapper（SQL 同时限定租户与本人）
  chat/entity/         会话、消息数据库 Row 与可信归属
  chat/dto/            创建、发送和空请求 DTO
  chat/vo/             独立会话/消息/运行响应
  chat/config/         有界流式资源、Springdoc 分组
  chat/exception/      对话错误码与 HTTP 适配
  product/             商品模块（分类、商品/SPU、SKU、属性）
  product/controller/  公开商城与员工维护 HTTP 入口
  product/service/     ProductQueryService 读取与 ProductCommandService 维护编排
  product/mapper/      四表 MyBatis Mapper（SQL 全部带商户范围）
  product/entity/      分类/商品/SKU/属性数据库 Row
  product/dto/         请求 DTO 与校验声明
  product/vo/          公开与员工视图模型
  product/exception/   商品错误码与 HTTP 适配
  product/config/      Springdoc 分组
  legacy/              保留的旧餐饮项目实现
```

原来的 `com.countmaske.merchant` 存放旧餐饮项目实现，`com.countmaske.merchantassistant` 存放新建的商户助手基础组件。两者由同一个应用扫描和运行，并不是两个独立部署的应用。现在用更明确的结构区分迁移边界：新代码放在根包及对应业务域的子包中，保留的旧代码统一放在 `legacy` 下。

原来的 `merchant-common`、`merchant-pojo` 和 `merchant-server` 三模块已经合并到 `api/src/main`。重复源码、旧 POM 和退役模块的实际目录均已删除，迁移后为空的旧 `com/countmaske` 源码目录也已清理。三个旧本地配置和测试草稿文件保留在 `api/.runtime/legacy-backup` 中，该目录被 Git 忽略。

根目录现有 `api` 包负责基础 HTTP 样例，不是另一个后端工程或 Maven 模块。auth 与后续商品、库存、订单、物流及 Agent 相关业务域统一采用 `controller / service / mapper` 三层包结构；MyBatis 统一命名 `mapper`，不再同时建立同义的 dao 包。数据库模型放 entity，请求放 dto，独立响应模型放 vo；辅助包按实际职责建立，不预建空目录，也不强制为每个 Service 增加一层没有实际差异的接口/impl。后续的 `merchant` 业务域表示商户和租户业务，不再表示整个旧项目。根包下的 `common` 存放新项目的公共基础组件；`legacy` 则保留旧项目的常量、实体、Mapper 和 Service 等包，以维持兼容。

## auth 三层调用核对

| 职责 | 现有类与调用 |
| --- | --- |
| Controller | `controller.AuthController` 接收请求、验证 dto.AuthRequests、组装 HTTP 响应；没有直接注入或调用 Mapper |
| Service | `service.AccountService` 编排登录/开通/账号查询并调用 ConsumerAccountMapper、StaffAccountMapper；`service.AccountDirectory` 标注 `@Service`，通过三个 Mapper 查询有效主体；店铺定位通过 service.TenantResolver 调用 AuthTenantMapper |
| Mapper | `mapper` 下的 ConsumerAccountMapper、StaffAccountMapper、AuthTenantMapper，配合 `resources/mapper/auth/*.xml` 访问 MySQL，返回 entity 中的数据；账号查询带服务端商户与必要本人范围 |

账号业务主链路为 Controller → Service → Mapper。安全入口仍保留 refresh/logout 的现有凭证分支：security.AuthSecurityFilter 调用 service.SessionService，Controller 返回处理结果；SessionService 通过 cache.AuthRedis 管理会话，通过 service.AccountDirectory 查询账号状态。过滤器是 HTTP 安全入口，Redis 操作不需要绕行 MySQL Mapper。MapperScan、Springdoc、ControllerAdvice、XML namespace/resultType 和测试引用已同步到新包名。

当前 Controller 仍包含短信发送时的店铺解析/手机号规范化编排，以及成功私有操作后的 `sessions.touch` 调用，并使用 Service 的响应 record。若进一步要求 Controller 只做协议适配，可在后续调整中将用例编排和 touch 策略收拢到 Service；不能把当前实现描述为所有业务编排都已下沉。后续模块统一保持 Controller 不直接访问 Mapper、Service 负责业务与授权、SQL 同时限定范围。路线与前置依赖见 [后续模块路线](module-roadmap.md)。

2026-11-06 包迁移验证：29 个迁移类的方法逻辑保持；`mvn -f api/pom.xml clean test` 通过 44 项单元/契约测试，随后 `mvn -f api/pom.xml verify` 通过 62 项隔离容器集成测试，均无失败或跳过。AuthPersistenceIT 验证真实 Mapper/XML，AuthDevManualIT 验证九个 Springdoc 操作，AuthFlowIT 验证认证/会话行为。报告在 `api/target/surefire-reports` 与 `api/target/failsafe-reports`。

旧路由 `/admin` 和 `/user` 保留现有接口契约，新路由使用 `/api/v1`。包名迁移本身不改变业务行为。新基础组件不能直接导入旧项目的实体或 Mapper。

后端维护文档统一放在 `api/docs/` 下，按主题分类。架构说明放在当前目录，测试运行说明见 [自动化测试说明](../testing/README.md)。业务规划、OpenSpec 和历史交付记录继续保留在仓库级目录中。后端根目录的 README 作为文档导航入口。

`/api/v1/**` 使用 stateless Spring Security 链，RequestIdFilter 在安全链之前；AuthSecurityFilter 仅在链内创建，避免重复 servlet 注册。refresh/logout 走各自凭证验证分支，旧链继续交给 legacy 拦截器。SecurityContext 与 legacy BaseContext 都在 finally 清理。

后续领域入口接收安全链构造的不可变 Actor；不得从 body/header 自报 merchantId/role。通用私有 SQL 必带 Actor.merchantId，消费者再带 Actor.accountId；公开店铺只按 URL 定位 ACTIVE 商户。先角色、再带范围查资源；越界和不存在同为 404。通用路由若收到 A token 就处理 A，无法知道浏览器停在 B 页面；未来显式保护店铺路径才核对 URL/token 租户并返回 403。Agent 的 V5 新增 chat_session/chat_message，消费者和员工均限定本商户本人角色与账号；员工不能读取消费者会话。

对话调用为 ChatController → ChatService → ChatSessionMapper/ChatMessageMapper。ChatService 在提交消息占位和运行权后，经 AgentModel 调用模型；OpenAiAgentModel 使用 LangChain4j 转换协议，AgentContext 从 MySQL 成功完整轮次组装上下文。后续 RAG/FC 可复用此边界及可信 Actor，不能把模型返回的身份当授权。MySQL 保存完整历史；当前没有 Redis ChatMemory 键、摘要、Tool 或 RAG 编排。验收与剩余 Apifox 工作见 [Agent 验收记录](../agent/verification.md)。

auth 不导入 legacy 账号/Mapper/BaseContext，依赖检查由 AuthContextTest 覆盖。common 只承载 ID/envelope 等通用边界，不把 legacy 会话当可信 Actor。
