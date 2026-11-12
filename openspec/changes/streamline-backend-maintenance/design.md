# Design

## Context

只读盘点日期为 2026-11-12。测试根包平铺 59 个 Java 文件（29 个 `*Test`、26 个 `*IT`、三个辅助类及显式手工入口），`.runtime` 顶层有 63 个混合文件。这些是文件数，不是用例数。

当前 `architecture/packages.md` 同时包含长期包规则、2026-11-06 的迁移验证、其他手册的导航；Agent 正文也混合场景编号、测试证据和相互链接。整理必须区分技术内容与导航，不能只批量删链接。

`application-technical-baseline` 旧位置约定随本 change 的 delta 调整。根 `README.md` 由用户维护，本 change 不改；唯一整个排除的后端文档目录为 `api/docs/prod-profile/**`。其生产手稿仍调用旧 backup-dev 路径，需保留转发。

现有订单 change 任务 5.2 未勾选，商品/物流 change 尚未归档；本 change 不补勾、同步或归档它们。Agent 文档中的 Apifox 状态存在“不做”和“待完成”的历史差异，整理时保留各原始结论，不推断新的验收状态。

## Goals / Non-Goals

**Goals:** 通过目录 README 找到资料；五个基础模块分开冻结；验证事件集中；脚本按执行目标查找；测试按业务域定位；新检出仓库不依赖个人 runtime 中的代码。

**Non-Goals:** 不改业务规则、测试断言、接口、SQL 内容与编号，不补缺失 PRD/API/表设计，不实现 Agent 新能力，不改根 README、prod-profile 或前端，不扩展为全仓资料治理、CI 或远程发布。

## Decisions

### 1. 持续维护方式与目录导航

持续维护以 OpenSpec、`docs/interview` 和现有组织仓库 `e-commerce-agent/gba-merchant-bao` 为主。当前 REST 细节查 Springdoc 导出，DDL 与种子/用例 DML 查 V/R/fixture SQL，实际规则查当前 Service 和测试；SOAP 保持既有 WSDL/XSD。旧设计只作历史资料，不再写平行最新版接口表/字段表。

`api/docs/README.md` 是文档目录导航。每个实际保留的文档目录（包括 architecture、agent、deployment、testing、日期事件目录）和五个模块档案目录有一个 README：逐项链接文件，以一行说明用途。有既有 README 的按相同规则整理；其技术内容先迁到适当文件保留，不用导航覆盖业务正文。空目录不补 README，prod-profile 全部例外。

`api/README.md` 只保留简短运行/测试入口与目录导航，不取代各目录 README，不扩写项目业务介绍。根 `README.md` 原样保留；文档整理不承诺修复其既有业务描述或运行命令。

正文需要引用其他说明性 Markdown 时，集中经所属目录 README 的稳定条目定位；跨目录关系也由该 README 链接目标目录 README 或目标文件。规则只适用于本次整理的文档/脚本说明，不批量改写 OpenSpec 历史工件或全仓文件。示例：

| 原写法 | 整理后正文 | 所属 README |
| --- | --- | --- |
| `[需求矩阵](requirements.md) 的 RT/CV 编号对应以下证据。` | `RT/CV 编号对应以下证据；矩阵入口见 [本目录 README](README.md#需求与场景)。` | 链接 requirements.md，说明场景矩阵与编号的用途 |
| `已准备 [隔离服务与14项导入用例](apifox-verification.md)，语法检查通过。` | `已准备隔离服务与14项导入用例，语法检查通过；具体入口见 [本目录 README](README.md#验收入口)。` | 链接 apifox-verification.md，说明隔离服务、凭据和用例执行方式 |
| `接口基线见 api-design.md；……` | `接口基线入口见 [本目录 README](README.md#接口契约)。`，后面的技术事实保留 | 链接 api-design.md，说明该阶段契约及当前 Springdoc 来源 |
| `测试运行说明见 ../testing/README.md` | 如需定位则链接本目录 README 的测试条目；纯重复导航可删 | 集中链接 testing/README.md |

不把超链接简单换成同一文件名的纯文字导航，也不删成读者无法定位的孤立结论。正文保留 RT/CV 编号、表格对应关系、测试类名/方法、代码路径、断言、命令、结果和限制；代码、SQL、Springdoc 产物的直接引用可留。测试分包后的实际路径作机械更新，不能以“技术内容不改”为由保留失效当前路径。

README 只承担导航、用途、必要命令与前提，不复制技术长文。脚本目录 README 额外说明执行目标与写入影响；testing README 每个事件只一行“原因 + 用途”。本 change 不为各模块新建总结、PRD、接口设计、表设计或 interview 卡片。

### 2. 五模块分别归档，先整理再冻结

所有本次新建的带日期目录都采用 `YYYYmmdd-名称`，日期按 Asia/Shanghai。模块用实际冻结日期，事件用实际结束日期；不把既有 OpenSpec 归档等全仓历史目录顺带重命名。

| 资料 | 去向 |
| --- | --- |
| auth 的长期需求/设计/日常说明 | `docs/archive/YYYYmmdd-auth/` |
| product 的长期需求/设计 | `docs/archive/YYYYmmdd-product/` |
| inventory 的长期需求/设计 | `docs/archive/YYYYmmdd-inventory/` |
| order 的长期需求/设计 | `docs/archive/YYYYmmdd-order/` |
| shipment 的长期需求/设计/日常说明 | `docs/archive/YYYYmmdd-shipment/` |
| agent 的需求、设计、模型接入、日常操作 | 留 `api/docs/agent/`；不冻结整个 Agent 目录 |
| architecture 长期规则与依赖说明 | 留 `api/docs/architecture/`；清理导航并迁出临时验证段落 |
| deployment 日常环境/发布/恢复说明 | 留 `api/docs/deployment/`，建立 README；迁出脚本与临时事件 |
| 任一目录中的临时验证记录 | 统一迁 `api/docs/testing/YYYYmmdd-事件/` |

例如实施于 2026-11-12，则分别建立 `20261112-auth`、`20261112-product`、`20261112-inventory`、`20261112-order`、`20261112-shipment`，不共用一个日期容器目录。保留原文件名；若 inventory/order 的 README 原有技术长文，先原文迁入同目录 `module.md`，再让 README 导航该文件，不丢正文、不补不存在的设计类型。

执行顺序：保全来源提交和原始版本 → 分离脚本/事件 → 整理 README 和正文导航 → 更新实际路径 → 核对技术段落原文 → 冻结。五个档案里没有脚本原件或脚本副本；脚本仍可从来源提交追溯。

用一份 `docs/archive/manifest.json` 记录原路径、最终路径、来源提交、原始哈希、整理后冻结哈希/日期，以及技术段落迁移去向。原始哈希和冻结哈希可以不同，不能要求整理后文件与旧 Git blob 字节相同。原始版本由 Git/必要本地保全追溯，不另复制一套未整理档案；未提交修改不得冒记为 HEAD 内容。

冻结对象是整理后的档案（含 README）；以后 Agent 默认不读不写，追溯原因时定向读取。根 `AGENTS.md` 和 archive 作用域规则明确这一点。维护校验以冻结哈希检出改写/丢失，并检查来源和清单变化，不能通过重算清单掩盖变动；不声称文件系统绝对只读或新建 CI。

旧五模块目录在有效引用修复后移除，不逐文件留跳转空壳。不可改的根 README 或 prod-profile 如确有有效文档引用，则仅保留必需的最短兼容入口；不可修改方既有坏链接记录为既有问题。

### 3. 临时验证记录统一按事件集中

`api/docs/testing/README.md` 每个事件一行链接该事件目录 README，并说明“因何发生、用于什么”。事件 README 链接其中已有记录，技术/验收正文原文迁移，不再散落于长期架构/需求文档中。日常复现操作留所属日常目录，通过目录 README 定位。

已核查的映射：

| 来源 | 事件目录/文件 | 保留内容 |
| --- | --- | --- |
| architecture/packages.md 中 2026-11-06 包迁移验证段落 | `api/docs/testing/20261106-auth-package-migration/verification.md` | 原始测试数量、命令、结果与限制整段迁移 |
| testing/commerce-integration-verification.md（跨 11 月 11–12 日） | `api/docs/testing/20261112-commerce-integration/commerce-integration-verification.md` | 原文件名、全部日期和验收结果；跨日仍是同一事件 |
| auth/verification、inventory/snowflake-verification、shipment/verification、deployment/verification 等 | 各 `YYYYmmdd-事件/`，据正文实际结束日期归类 | 原文件名保留；同名文件分不同事件目录 |
| agent/verification 的基础验收、真实模型验证、dev Flyway 维护等阶段 | 按实际事件拆出原段落；同一事件可跨日 | 不改 Apifox 未执行等状态，不把不同事件混成无止境总记录 |

日期按正文已有结束时间/可追溯证据确定，不一律用整理日；证据不足不捏造，保留未决项。完成事件停止追加；新事件新建目录。日常操作与事件结果混合的文件，允许按完整段落分离，长期规则和结论字句不重写。一次整理后，过去完成事件也不借新 Agent change 续写；勘误通过明确的新变更说明。

### 4. 脚本按执行目标单独维护

| 目录 | 当前脚本/职责 |
| --- | --- |
| `api/scripts/prod/` | deployment/deploy-prod.ps1：生产发布；生产配置仍读原 prod-profile |
| `api/scripts/dev/` | docker-dev.ps1、backup-dev.ps1、dev-seed-upgrade.ps1：本地 dev 环境维护 |
| `api/scripts/test/` | auth/manual-verification.ps1：现有 AuthDevManualIT 驱动的隔离联调入口 |
| `api/scripts/tools/` | export-openapi.ps1、check-maintenance.ps1、organize-runtime.ps1 等环境通用工具 |

只创建实际有脚本的目录，各有 README；不在 scripts 根平铺工具，不用 ops/manual 混合环境。prod-profile 内现有初始化/备份等脚本原地保留、不复制、不新增 README。

移动脚本时修复 `$PSScriptRoot`、工作目录、测试里的硬编码路径和当前命令引用；脚本行为与输入输出不改。分类看实际执行目标，不只看文件名。只有被不可改生产手稿调用的 `api/docs/deployment/backup-dev.ps1` 留薄转发，参数和退出码透传到新 dev 脚本，不保留第二套逻辑，也不放档案副本。

Springdoc 通用导出提升到 tools，接收 base URL，读取既有六分组，先暂存至 target 校验全部成功再更新既有快照；失败/空分组不部分覆盖。无需 runtime Java/classpath；不因纯整理重导出无语义变更快照。其他一次性脚本不机械提升为维护工具。

### 5. 测试按域分包

保留根 `com.gba.merchantbao.test`，下列映射覆盖当前清单；不额外按 unit/IT 重复分层：

| 子包 | 当前文件 |
| --- | --- |
| auth | Auth*、AccessTokenServiceTest、LegacyJwtCompatibilityTest |
| product | Product*、PublicCatalogSecurityTest |
| inventory / order / shipment | 各域同名前缀测试 |
| agent | AgentAttemptsTest、AgentConfigurationTest、AgentContextTest、AgentModelProtocolTest |
| chat | Chat* 及 AgentApifoxWorkspace |
| common | SnowflakeIdGeneratorTest、JsonLoggingTest |
| foundation | Foundation*、FlywayMigrationIT、DevSeedMigrationIT、MigrationScriptContractTest、RuntimeConfigurationTest、ProductionInfrastructureIT、RedisIT、JwtPackagingIT、HttpClientTest |
| integration | CommerceIntegrationIT |
| support | DockerRequired、MutableAuthClock、ScriptedAgentModel |

辅助类仅开放跨包必需访问；AgentApifoxWorkspace 与 ChatAcceptanceIT 同 chat 包，保持包级继承协作且手工入口不改成 *Test/*IT。Surefire/Failsafe 默认发现语义、简单类名选择、所有用例和断言保留；不靠 include/exclude 缩范围，不移动 fixture，不改变容器数据隔离。

对比迁移前后实际“简单类名 + 测试方法/参数化用例”的发现清单，不能只比总数或 BUILD SUCCESS。当前文档与脚本同步实际类路径；冻结前完成对应机械更新，不能在冻结后追着包名反复改档案。

### 6. Runtime 分离运行状态与历史

保留 `.runtime/logs`、tmp、manual 的职责；Maven 产物只进 target。旧日志、SQL 备份、legacy-backup、一次性源码/class 迁到忽略的 `backups/YYYYmmdd-backend-maintenance/<唯一批次>/`；记录去向与哈希，不运行旧修复脚本。维护脚本仅在 scripts，文档档案不存这些副本。

tools/organize-runtime.ps1 默认预览；执行前核实绝对边界、无链接逃逸、不覆盖，复制校验后才移除源文件，未知/占用文件保留并报告。不递归清空 runtime、不移动活动日志、不停止业务服务。生产手稿引用的 `.runtime/flyway-legacy.env` 保留兼容位置，不读/提交真实凭据正文。

无活动 Apifox 工作区时才将其状态统一到 `.runtime/manual/agent`，同步显式入口和 test 文档；有活动状态则延后该项并报告，不能伪报迁完。

## Risks / Trade-offs

- [README 成为第二套长文] → 每文件一行用途，正文只保留必要 README 定位入口，不复制规则与验收表。
- [清理导航时改写技术结论] → 原段落迁移及前后对照，保留 RT/CV、断言、数值、日期、限制；来源哈希与冻结哈希区分。
- [日期或历史状态不一致] → 按证据保留事实和未决状态，不以本次整理更新验收结论。
- [根 README/prod-profile 旧说明不能更新] → 原样保护，只在有效调用需要时保留最少兼容；不扩展为修复其历史问题。
- [包迁移少跑测试] → 保存发现基线、检查共享访问/继承、完整验证并对比实际用例。
- [本地材料含运行数据] → 忽略备份保全，不把 runtime 整目录放 Git 或文档档案。

## Migration Plan

1. 留来源与工作树基线，记录根 README、prod-profile、V/R/fixture SQL 哈希及测试发现清单。
2. 按目录 README 规则先整理导航与日常说明，原段落集中到日期前置的 testing 事件；五模块各自归档，Agent/architecture/deployment 留当前目录。
3. 迁脚本至 prod/dev/test/tools 并修复调用；分包测试，先更新相关实际路径，再建立整理后冻结基线。
4. 预览后保全 runtime 历史，核对哈希与剩余活动文件；验证新检出仓库不依赖旧 runtime 代码。
5. 校验 README/正文链接边界、锚点与技术段落去向、冻结哈希、保护区域/SQL 零差异；运行 mvn test 与 Docker 环境 clean verify 并比较发现清单。工具用隔离样例验证，不执行真实生产部署或写开发业务库。
6. 本地整理组织仓库 PR 所需的 change/命令/真实结果与回退说明，不创建远程 PR、不改其他 change 完成状态。

回退按本 change 的定向反向变更恢复目录、包和调用；本地文件按保全清单恢复且不覆盖新文件。禁止仓库级 reset/clean、覆盖真实配置或业务数据。本 change 无数据库回滚。
