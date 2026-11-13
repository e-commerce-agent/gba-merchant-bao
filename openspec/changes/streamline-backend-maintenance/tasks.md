# Tasks

## 1. 固定来源与保护范围

- [x] 1.1 在本 change 的 artifacts/protected-baseline.json 记录来源提交、工作树状态、根 README、整个 prod-profile 及全部 V/R/fixture SQL 的路径/哈希并随 change 提交；核验保护对象清单完整，未提交内容不冒记为 HEAD，本地敏感配置只记元信息。
- [x] 1.2 在本 change 的 artifacts/test-file-mapping.json、artifacts/test-discovery-baseline.json 保存当前 59 个 Java 文件的唯一目标映射与实际 Surefire/Failsafe 发现基线并随 change 提交；记录类/方法/参数化用例及失败、错误、跳过，不把文件数或旧报告当本次测试结果；原始 XML/日志仍留忽略的 api/target/，不提交。

## 2. 目录 README 与正文定位

- [x] 2.1 为 api/docs 及实际保留的 architecture、agent、deployment、testing 等目录建立/整理 README，每文件一行链接和用途，根 README 与 prod-profile 不动；核验目录都有入口，现有 README 的技术长文先原文保留到适当文件而非覆盖丢失。
- [x] 2.2 按 design 示例整理正文：保留语境、RT/CV、测试类/路径、断言、命令和结论，所需说明文档定位统一经所属 README 稳定条目；检查无直接说明文档互链、纯文字重复文件名导航或失效锚点，代码/SQL/生成产物直接证据可留。
- [x] 2.3 精简 api/README 为必要运行/测试与目录入口，不取代目录 README；只按需修复现有 interview、SQL 目录 README 的导航，diff 确认根 README、SQL 正文不变，没有新建平行 PRD/API/表设计或 interview 卡片。

## 3. 验证记录集中为日期事件

- [x] 3.1 将 architecture/packages.md 的 2026-11-06 包迁移验证段落原文迁至 testing/20261106-auth-package-migration/verification.md，补事件 README 与 testing README 的一行原因/用途；对照命令、测试数量、结果与限制均保持，长期架构正文不再混放该事件。
- [x] 3.2 将 commerce-integration-verification.md 移至 testing/20261112-commerce-integration/，保留原文件名和跨日正文；事件 README 链接记录，根 testing README 一行说明电商模块关联整合引起的验证，检查所有有效导航可定位。
- [x] 3.3 将各模块、Agent 和 deployment 中其余临时验证文件/段落按实际结束日期归入 testing/YYYYmmdd-事件；保留原文件名或完整原段落、技术规则和验收状态，日常操作留所属目录；逐项对照来源与去向，未知日期不捏造，同一跨日事件不强拆。
- [x] 3.4 在 testing README 说明已完成事件停止续写、新事件另建目录的规则，并核验每事件只有一行原因/用途和可达的事件 README；检查日期前置、无重复散落副本，Agent“不做/待完成”等原始差异未被整理擅自统一。

## 4. 脚本按执行目标分类

- [x] 4.1 将 deployment/deploy-prod.ps1 迁 prod，docker-dev.ps1、backup-dev.ps1、dev-seed-upgrade.ps1 迁 dev，auth/manual-verification.ps1 迁 test；修复根路径和 AuthDevManualIT 等调用，每分类 README 给出用途、命令、目标与前提，验证语法/参数及隔离调用，文档档案不留脚本副本，prod-profile 脚本不动不复制。
- [x] 4.2 旧 api/docs/deployment/backup-dev.ps1 仅保留到 scripts/dev 实现的参数/退出码透传包装；用隔离替身核验透传、无重复逻辑，不为无调用方旧路径保留空壳，不实际执行生产发布或写 dev 业务库。
- [x] 4.3 将通用 Springdoc 导出入口置于 scripts/tools/export-openapi.ps1，支持 base URL 和既有六分组，target 暂存成功后才更新快照；用隔离 HTTP 样例验证成功/空分组/失败行为，不依赖 runtime Java/classpath，不因整理手改或无故重导出快照。
- [x] 4.4 校验 scripts/prod、dev、test、tools 的 README 只承担工具导航与必要执行前提，脚本正文引用其他说明材料时经目录 README 定位；在无旧 runtime 内容的等价新检出环境确认入口可定位且无个人路径或真实凭据。

## 5. 测试按业务职责分包

- [x] 5.1 按 design 映射迁移 auth/product/inventory/order/shipment 测试，更新声明/导入；以 test-compile 和文件清单核验一一对应，原用例、断言和 fixture 路径完整保留。
- [x] 5.2 迁移 agent/chat/common/foundation/integration 测试及 support 三辅助类，只开放辅助类跨包必需访问，AgentApifoxWorkspace 与 ChatAcceptanceIT 同 chat 包；ChatRunExecutionTest、ChatSseLifecycleTest 文件进入 chat 但保留原包声明以访问内部实现，不改生产代码或 dev/prod profile；编译验证全部 59 个文件唯一归位，根包不平铺 Java，手工入口不改成默认测试命名。
- [x] 5.3 更新当前文档、脚本、反射/显式类引用及简单类名选择示例，执行单元验证并比较发现清单；验证 -Dtest/-Dit.test 原语义保留、手工工作区不会默认启动，无删断言或放宽发现规则导致漏测。
- [x] 5.4 修正基线中 ProductAvailabilityIT.exportsActualProductOpenApiWithThePublicBooleanContract 的分组断言：商品 product-v1 不应包含 StaffLogin，认证 auth-v1 的 StaffLogin.merchantCode 由 AuthDevManualIT 验证；保留商品原有 inStock、公开路径及敏感字段断言，不扩大商品分组扫描范围；定向运行这两个集成类通过，保留原失败基线并记录修复后的结果，最终由 8.2 完整验证确认通过。

## 6. 五个基础模块分别整理后冻结

- [x] 6.1 将五模块长期文档分别移至 api/docs/archive/ 下的 YYYYmmdd-auth、YYYYmmdd-product、YYYYmmdd-inventory、YYYYmmdd-order、YYYYmmdd-shipment 五个同级目录，日期为实际冻结日，保留现有文件名；各建/整理 README，原 README 技术长文按 design 原文迁出，确认不混入脚本或已集中 testing 的事件、不补缺失设计，Agent/architecture/deployment 留原目录。
- [x] 6.2 对整理前后技术段落、证据对应、数值、日期、状态和限制逐项核对，仅允许导航调整、段落迁移及实际路径机械同步；确认五个归档目录和 README 入口与 design 一致，不改写技术结论或补造缺失设计。
- [x] 6.3 增加短版根 AGENTS 与 archive 作用域冻结规则，确认旧模块目录只在不可改调用方确有需要时留最少兼容入口。

## 7. Runtime 分类保全

- [x] 7.1 在 scripts/tools 实现 organize-runtime.ps1 默认预览与显式执行，更新 tools README；用临时样例验证绝对边界、哈希核验、不覆盖备份、重复执行与占用/未知文件保留，不递归清空 runtime。
- [x] 7.2 将空闲历史日志、一次性代码/class、SQL 和 legacy-backup 保全至忽略的 backups/YYYYmmdd-backend-maintenance/唯一批次，逐项核验哈希与可恢复性；保留活动日志/状态及 env 兼容位置，清单记录剩余项，备份和凭据未提交。
- [x] 7.3 无活动手工工作区时统一 Apifox 状态到 runtime/manual/agent 并同步显式入口及目录 README；核验启动/停止读写一致，Maven 日志只进 target，确有活动文件未迁时如实报告，不把待迁项目标完成。

## 8. 整体一致性与交付

- [ ] 8.1 检查所有目录 README、正文经 README 定位的锚点、事件一行说明、日期前缀、五模块归档目录与脚本单一位置；比较根 README、prod-profile 和 V/R/fixture SQL 基线为零差异，其他旧 change 状态不改。
- [ ] 8.2 在 Docker 可用环境执行 mvn -f api/pom.xml clean verify，对比迁移前后的类/方法/参数化用例发现清单，确认完整隔离、除 5.4 明确修正外的原断言与 *Test/*IT 语义保留；如实记录失败、错误、跳过，确认 5.4 的已知失败已消除、完整验证通过，不只用 BUILD SUCCESS 验收。
- [ ] 8.3 本地整理可用于现有组织仓库 PR 的交付说明，包含 change、来源、实际验证、目录入口、冻结范围、兼容项及回退办法；检查无虚构验收、无重复设计正文，不要求创建/发布远程 PR 或修改组织仓库配置。

## Workflow follow-up

- 本轮只修订规划；用户另行要求实施时进入 apply。
- 本 change 实施复核后按 OpenSpec 流程同步/归档；不顺带归档其他 change，不替订单任务 5.2 认定验收完成。
