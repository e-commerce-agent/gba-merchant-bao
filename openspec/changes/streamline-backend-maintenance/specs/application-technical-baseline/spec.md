# Spec Delta

## MODIFIED Requirements

### Requirement: Backend documentation and runtime files shall have explicit locations

根 `README.md` 和整个 `api/docs/prod-profile/` MUST 保持原样；其他后端文档按目录 README 集中导航，architecture、agent、deployment 的长期说明保留，五个基础模块分别冻结，临时验证集中 testing。持续维护 MUST 以 OpenSpec、`docs/interview` 和组织仓库 commit/PR 为准，接口/数据细节分别查生成契约/SQL，不在旧设计中重复同步。本地实际配置不得提交，示例只能包含占位值。dev Compose 读取根 `.env`，IDEA 应用读取被忽略的 `application-dev.yml`，只激活 dev，不要求 IDE 加载 `.env`。test 使用 Testcontainers，不依赖重复的 local profile。测试日志和报告 MUST 留在 `api/target/`，开发日志保留 `.runtime/logs`；个人旧文件备份从 `.runtime/legacy-backup` 迁至忽略的根 `backups/`。

#### Scenario: A developer starts local integration
- **WHEN** 开发者按后端运行手册启动服务
- **THEN** 根 Compose 读取 `.env` 启动持久中间件，IDEA 只激活 dev 并读取 `api/src/main/resources/application-dev.yml`；工作目录为 api，不要求加载 `.env` 或配置额外环境变量，也不依赖 local profile

#### Scenario: Initial connection values are prepared
- **WHEN** 按手册创建本地实际配置
- **THEN** dev MySQL root 与应用密码先使用 `123456`，数据库和用户名与本地 YAML 一致，Redis 不配置密码；prod 实际环境文件的 MySQL/Redis 密码先使用 `123456`，生产 YAML 读取环境变量占位符；未来引入 RabbitMQ 时初始密码亦按此选择设置，不提前新增配置

#### Scenario: Automated tests start the application context
- **WHEN** 集成测试启动应用上下文
- **THEN** 测试显式激活 `test` 并注入 Testcontainers 连接值，不受开发者 `dev` 环境设置影响

#### Scenario: Local configuration is committed
- **WHEN** 检查仓库中提交的本地配置示例
- **THEN** 只跟踪占位值和操作说明；真实凭证及个人本地设置均被忽略，且不会写入日志

#### Scenario: Production preparation is planned
- **WHEN** 准备生产 Compose、环境文件、账号脚本和 Dockerfile
- **THEN** 实际文件分别位于 `api/docs/prod-profile/opt/gba/docker-compose.yml`、该目录的 `.env`、`docker/mysql/init/01-create-users.sh` 和 `app/Dockerfile`；该目录是生产配置唯一来源，Compose、`.env.example`、Dockerfile、`.dockerignore` 和初始化脚本按文件放行提交；实际 `.env`、JAR、发布 SQL 和备份仍忽略，删除重复 examples/prod，测试直接读取该目录；操作手稿可提交，不包含 chmod 600 步骤

#### Scenario: Production artifacts are not released yet
- **WHEN** 本 change 只准备生产配置而尚未上线
- **THEN** 不在准备目录创建 app.jar 或复制 SQL；结构和 common seed 仍保留源码目录，上线时再复制同版本发布产物到 ECS 的 app/、db/migration/ 和 db/seed/common/，不把缺少产物的模板描述为已完成生产构建或部署

#### Scenario: A developer runs Maven verification
- **WHEN** 开发者执行文档规定的 Maven 验证命令
- **THEN** Surefire/Failsafe 的应用测试日志只写入 `api/target/test-logs`，测试报告分别写入 `api/target/surefire-reports` 和 `api/target/failsafe-reports`；测试不得同时创建或修改 `.runtime/logs` 或生产目录中的日志，后端根目录不新增日志或专题文档

#### Scenario: Retired source directories are cleaned
- **WHEN** 旧源码目录已迁移并通过验证
- **THEN** 不保留可重复构建的退役模块源码；必要的个人备份放在被忽略的根 `backups/`，不作为运行依赖

#### Scenario: A developer follows an existing documentation link
- **WHEN** 可修改的当前文档引用了迁移前的说明文件
- **THEN** 按目录 README 的集中导航规则修复定位；根 README 和 prod-profile 字节不变，只有其既有有效调用确实需要时才保留最短兼容入口，不逐文件生成跳转空壳

## ADDED Requirements

### Requirement: Maintained documentation shall identify authoritative artifacts

维护入口 MUST 将当前行为指向代码、测试与 OpenSpec，将 REST 接口详情指向 Springdoc 导出，将 DDL、种子 DML 与测试数据指向实际 SQL；手写文档 MUST NOT 再复制维护完整接口表和字段表。`docs/interview` MUST 只记录真实实现、取舍和证据。GitHub commit/PR MUST 承载变更和验收证据，不另建平行版本的设计正文。

#### Scenario: An API or database detail is needed
- **WHEN** 开发者或 Agent 查找当前请求字段、表字段、演示数据或用例数据
- **THEN** 入口分别指向 Springdoc、`db/migration/V*.sql`、`db/seed/**/R*.sql`、`db/devdata/R*.sql` 与 `src/test/resources/db/fixture/*.sql`；各 SQL 目录 README 只补充执行范围、顺序、稳定标识和非显然约束

#### Scenario: A generated contract is refreshed
- **WHEN** API 代码发生经批准的变化
- **THEN** `docs/api` 中的接口快照由应用的 Springdoc 生成并关联版本，不能通过手改快照或旧接口设计文档来宣称契约已经更新；SOAP 原有 WSDL/XSD 仍是其契约来源

#### Scenario: Daily documentation is maintained
- **WHEN** 更新后端开发入口
- **THEN** `api/README.md` 保留简短运行与目录入口，各目录 README 链接现有文件并简述用途；不改根 README，不为已完成模块新增缺失的 PRD、接口设计或表设计，也不复制技术长文到 README

### Requirement: Documentation relationships shall be centralized in directory readmes

本次整理的每个实际文档目录及脚本分类目录 MUST 有 README，集中链接文件并说明用途；prod-profile 整体例外。正文 MUST 保留必要语境和技术事实，需要查找其他说明文档时仅链接所属目录 README 的对应条目，不直接互链或改成重复文件名导航。代码、SQL、生成契约的直接证据引用及场景/测试标识 MUST 保留。

#### Scenario: A reader follows the acceptance procedure
- **WHEN** 正文说明隔离服务与14项导入用例已准备且语法检查通过
- **THEN** 保留该事实与结论，并通过本目录 README 的验收条目找到具体说明文件；README 链接该文件并说明服务启动、凭据与用例执行用途，正文不重复维护其文件关系

#### Scenario: A reader checks the interface baseline
- **WHEN** 正文需要说明接口基线或需求矩阵与证据的关系
- **THEN** 保留接口基线语境及 RT/CV 编号对应表，通过所属目录 README 的稳定条目定位具体文档；不因删除直接文档链接而删去测试类、路径、主要断言和验收限制

#### Scenario: A navigation entry crosses directories
- **WHEN** architecture 正文需要引导读者查测试说明或另一个目录的材料
- **THEN** 正文指向 architecture README 的对应条目，由该条目链接目标目录 README 或文件；链接与锚点可解析，architecture 的技术规则保留

### Requirement: Verification history shall be grouped by completed event

临时验证内容 MUST 从模块、architecture 及其他长期文档中迁到 `api/docs/testing/YYYYmmdd-事件/`，日期为事件实际结束日，跨日同一事件不强拆。testing README MUST 每事件一行说明发生原因和记录用途，事件目录 README 链接记录文件。原技术/验收段落 MUST 保留，日常操作说明留所属目录，完成事件不得无限追加后续流水账。

#### Scenario: Architecture contains an old package migration result
- **WHEN** 整理 packages.md 中 2026-11-06 的包迁移验证内容
- **THEN** 原验证段落迁到 testing 的 `20261106-auth-package-migration` 事件目录，命令、数量和结果不改，长期文档不再混放该事件正文；testing README 一行说明原因与用途

#### Scenario: A verification event spans two dates
- **WHEN** 整理跨 2026-11-11 和 2026-11-12 的电商整合验收
- **THEN** 原文件名与正文实际时间保留，放在 `20261112-commerce-integration/`；不会倒置为名称在前的目录，也不以整理日期冒充事件日期

#### Scenario: Another verification is completed later
- **WHEN** 已完成记录之后发生新的验证事件
- **THEN** 新事件另建日期前置目录并在 testing README 新增一行，旧事件不续写；同一事件的材料仍集中，不重抄到业务目录档案

### Requirement: Historical module documents shall be archived

认证、商品、库存、订单、物流 MUST 先整理导航、分离脚本与事件，再分别冻结于 `api/docs/archive/YYYYmmdd-模块/`，日期为实际冻结日，保留现有文件名且不补缺失文档。技术规则、场景编号、测试证据和验收结论 MUST 原文保留，可整段迁移及机械更新实际路径。

#### Scenario: Completed backend designs are archived
- **WHEN** 冻结认证、商品、库存、订单、物流文档
- **THEN** 五个模块分别位于 `api/docs/archive/` 下的同级日期前置目录，各有 README 导航，不混入单个日期容器；临时验收集中 testing，原来缺失的设计不补，模块档案不保留脚本副本

#### Scenario: Existing Agent foundation documents are retained
- **WHEN** 整理当前 Agent 文档
- **THEN** 需求、设计、模型接入与日常操作继续留在 api/docs/agent，通过 README 集中导航；仅临时验证记录迁到 testing，原待验收或不再执行的状态保留，不把整个 Agent 目录归档或宣称后续能力完成

#### Scenario: A later Agent change is implemented
- **WHEN** Agent 工作需要接入已完成业务模块
- **THEN** 默认读取本次 change、相关规范、当前领域服务与对应测试；仅在追溯历史原因时定向读取档案，任何经授权的当前能力变更通过新 change、代码和生成产物表达

#### Scenario: Module documents are archived after organization
- **WHEN** 已完成导航整理、原段落迁移和实际路径更新
- **THEN** 五个模块位于 `api/docs/archive/` 下的同级日期目录，各有 README 导航；不把技术结论改写伪装成导航整理，原 README 中的技术长文迁出后仍可找到

### Requirement: Backend tests shall be discoverable by domain without reducing coverage

测试源码文件 MUST 在 `com/gba/merchantbao/test` 下按业务域、公共基础、跨模块集成和共享辅助职责分目录、分包；仅 ChatRunExecutionTest、ChatSseLifecycleTest 保留原生产包声明以测试包级内部实现，文件仍进入 chat 目录，其他声明与目标目录一致。分包 MUST 保留测试类名、用例与断言、fixture 资源路径及单元/集成命名约定；仅允许下述已确认的 OpenAPI 分组断言归属修正，不得减少覆盖。Maven 默认发现范围与已有手工验收入口的显式选择语义 MUST 保持。完整验证仍使用隔离容器，Docker 不可用时失败。生产代码及 dev/prod profile、配置、运行环境 MUST NOT 因本次分包修改。

#### Scenario: Maintenance baselines are committed
- **WHEN** 保存本 change 的来源、保护范围、测试映射和实际发现结果
- **THEN** artifacts/protected-baseline.json、artifacts/test-file-mapping.json、artifacts/test-discovery-baseline.json 随 change 提交并随其归档保留，原始 XML/日志仍留忽略的 api/target/；基线只含脱敏元信息和测试结果，后续修复不得覆盖初始失败或重算保护哈希掩盖差异

#### Scenario: The product contract test checks an authentication schema
- **WHEN** 修正初始基线中 ProductAvailabilityIT 对 StaffLogin 的错误分组断言
- **THEN** product-v1 不包含 StaffLogin，AuthDevManualIT 在 auth-v1 验证 StaffLogin.merchantCode；商品 inStock、公开路径和敏感字段断言保留，不扩大扫描范围或改运行接口，定向测试与最终完整验证通过，初始失败结果保留

#### Scenario: A developer looks for module tests
- **WHEN** 开发者打开某个业务域的测试包
- **THEN** 能在对应的 auth、product、inventory、order、shipment、agent 或 chat 包定位该域测试；跨域验证位于 integration，共享辅助位于 support，测试根包不再平铺 Java 文件

#### Scenario: Maven discovers tests after relocation
- **WHEN** 执行单元验证与完整验证并与迁移前的测试清单比较
- **THEN** 除完整包名变化外发现同一批测试和用例，`*Test` 与 `*IT` 的发现语义不变，无测试因访问权限、继承或选择器变化而消失，失败/错误/跳过数量如实记录

#### Scenario: A test exercises package-private chat internals
- **WHEN** 整理 ChatRunExecutionTest 与 ChatSseLifecycleTest
- **THEN** 文件移入 test/chat，分别保留 com.gba.merchantbao.chat.service、com.gba.merchantbao.chat.controller 包声明和原断言，不扩大生产代码可见性，不改变 dev/prod profile 或运行环境

#### Scenario: A developer explicitly starts the manual acceptance workspace
- **WHEN** 使用文档中的新入口启动原有 Apifox 临时工作区
- **THEN** 入口仍需显式选择，不被普通 Maven 验证自动启动；继承的验收逻辑和本机临时容器隔离仍有效

### Requirement: Maintained scripts shall be organized by execution target

受维护脚本 MUST 分类到 `api/scripts/prod/`、`dev/`、`test/`、`tools/`，只创建有实际文件的目录并各设 README，说明用途、命令和影响目标。脚本迁移 MUST 修复调用路径并保持行为，不放模块档案副本。prod-profile 中原脚本 MUST 原地保留、不复制；旧入口仅在不可改调用方实际需要时保留薄转发。

#### Scenario: A developer chooses an environment script
- **WHEN** 开发者寻找生产发布、dev 维护、隔离联调或通用工具
- **THEN** 从对应目录 README 定位单一脚本实现，不需要打开历史档案找工具；参数、工作目录、执行目标和必要前提可见，真实配置不进入可提交工具

#### Scenario: The protected production manual invokes the old backup path
- **WHEN** 手稿仍调用 api/docs/deployment/backup-dev.ps1
- **THEN** 旧入口只将参数与退出码透传到 scripts/dev 的实现，不保留重复备份逻辑；生产手稿、prod-profile 内脚本原样不动，也不为无调用方的旧脚本路径留空壳

### Requirement: Local runtime storage shall separate state from maintained tooling

`.runtime` MUST 仅保存被 Git 忽略的开发日志、临时文件和本机验收状态，不承担必须的可复用代码或数据库备份。可维护脚本 MUST 受版本控制且可从新检出仓库定位；测试产物 MUST 位于 `target`，备份和一次性历史材料 MUST 位于忽略的根 `backups`。整理 MUST 支持预览、逐项分类与迁后核验，不能递归清空运行目录。

#### Scenario: Existing mixed runtime files are reorganized
- **WHEN** 整理当前日志、SQL 备份、临时源码、编译结果及环境文件
- **THEN** 先输出来源、去向和保留原因，备份迁移后校验哈希；活动日志不移动，正在使用的状态文件不改路径；一次性材料仍可恢复，真实凭据不会被提升为可提交脚本

#### Scenario: A reusable command is needed on a fresh checkout
- **WHEN** 开发者在新检出的仓库执行契约导出或受维护的联调命令
- **THEN** 能从 `api/scripts/` 找到入口，入口不依赖个人绝对路径或 `.runtime` 中遗留的 Java 源码、class、classpath 和旧脚本；运行结果写入约定的输出目录

#### Scenario: A protected manual still names a local environment file
- **WHEN** 不可改动的生产操作手稿引用旧 `.runtime` 环境文件路径
- **THEN** 保留该文件的本地兼容位置并记录用途，不读取或提交凭据正文；后端日志仍使用 `.runtime/logs`，不改生产日志位置
