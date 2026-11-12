# 第 3、4 节实施验证

实施日期：2026-11-12（Asia/Shanghai）。本次范围仅为 tasks 3.1–3.4、4.1–4.4；第 5 节及后续未执行、未回勾。未执行生产发布或写入日常 dev 业务库。

- 验证记录集中为 16 个日期事件。来源提交、原路径、去向、原始文件/段落摘要见同目录 `verification-event-mapping.json`；完整迁移文件及抽取段落对照原文，仅调整导航和实际脚本路径。Agent 的“不做”和“待完成”分别保留，跨 11 月 11–12 日的电商整合未拆开。
- 78 份当前后端文档/脚本 README 的本地链接、锚点、正文经所属 README 定位检查通过；16 个事件各在 testing README 有一行原因/用途，均有事件 README。检查实现和原始对照数据留在忽略的 `api/target/`。
- 5 个脚本迁至 `api/scripts/prod`、`dev`、`test`，通用导出置于 `tools`。三个 dev/test 脚本实现原文保留；生产发布和开发容器入口只增加仓库定位及工作目录恢复。分类 README 给出命令、目标、前提及影响。docs 中仅保留被 prod-profile 手稿调用的 backup-dev 薄包装；prod-profile 内原脚本未动未复制。
- PowerShell 语法和参数检查通过；用替身验证生产/开发容器入口的 12 次调用、仓库定位和调用方工作目录恢复，备份脚本的 4 次 Docker 调用仅操作 target 下的样例仓库，种子维护入口参数保留且拒绝 backups 外路径。未调用真实 SCP、Docker 发布或开发库维护。
- `mvn -f api/pom.xml '-Dtest=AuthDevManualIT' test`：首次沙箱内 Docker 命名管道访问被拒绝（1 error）；获准沙箱外重跑后 BUILD SUCCESS，1 test、0 failures、0 errors、0 skipped。测试使用临时 MySQL/Redis，实际执行迁移后的 PowerShell 认证联调脚本。日志为 `api/target/maintenance-auth-script-test-elevated.log`，脚本日志为 `api/target/auth-manual-verification.log`。仅机械更新该测试的脚本路径，未改断言或 Java 包。
- 旧 backup-dev 包装在样例仓库中透传带空格的 DockerExecutable、输出及退出码 0/23，通过 Windows PowerShell 子进程核验；无重复备份逻辑。
- export-openapi 在 Windows PowerShell 5.1、PowerShell 7 各验证成功、空分组、最后一组 HTTP 503、异常 YAML，共 8 个本机 HTTP 样例。成功读取六组 JSON/YAML（12 请求）、保留中文并发布全部快照；其余情况保留全部原快照字节。Windows PowerShell 对 YAML 的二进制响应按 UTF-8 解码。所有样例快照都位于 target 下，仓库 `docs/api` 未重导出或修改。
- 由 670 个可版本化文件复制等价新检出，不含旧 `.runtime`、实际环境文件或 target 产物；四类 README/脚本、Java 调用入口和隔离调用检查通过。脚本无个人绝对路径或真实凭据，仅沿用明确公开的测试账号与生产目标占位符，不依赖个人 Java/classpath。
- 与既有保护基线比较：34 个可哈希文件（根 README、prod-profile 可提交材料、V/R/fixture SQL）字节一致；本地敏感 `.env` 仍按原基线只记元信息，不生成/提交其哈希或正文。其他 OpenSpec change 与生成快照无修改。`git diff --check` 通过。

本次没有执行完整 `clean verify`、测试分包、已知商品分组断言修复、模块冻结或 runtime 整理；这些仍由第 5 节及后续任务跟踪。原测试发现/保护基线未覆盖或重算。
