## 尚未交付与模块验收门槛

Apifox 6.1、6.2 未完成：2026-11-09 用户已完成 Agent/认证契约导入，本轮原生客户端连接持续报 pipe unavailable，重试与 kernel 重置无法恢复；只读 MCP 仅返回 `/api/health`，无法配置或执行目标模块。保留 tasks.md 未勾选，不以自动化或真实网关的命令行结果替代。已准备隔离服务与14项导入用例，语法检查通过；具体入口见 [本目录 README](README.md#apifox-verification)，但用例尚未在 Apifox 运行。完整前端、浏览器实测、真实 Nginx 多实例/负载均衡压测、RAG、Function Calling、RabbitMQ、Redis ChatMemory 均未交付。citation/tool_call 只预留扩展，不发送伪造事件；Redis 只服务既有认证，没有新增 ChatMemory 键/摘要机制。

后续可复用 AgentModel、MySQL 完整历史和可信 Actor，按既有租户/本人限制接入 RAG/只读 Tool。只有剩余 Apifox 用例有真实证据、全量构建通过且任务矩阵一致后，才能提交整个模块验收；本轮不自动提交、合并或归档。

当前任务状态：27/29；6.3 与第 7 节全部完成，仅 6.1/6.2 待 Apifox 实际操作和证据。可控模型临时服务已主动停止并退出临时登录，保留 dev 运行。

| 验证 | 实际结果 | 原始证据（本机忽略文件） |
| --- | --- | --- |
| Apifox 6.1/6.2 | 2026-11-09 用户已确认导入 Agent 与认证模块；环境/凭证引用和协议验收待核对。原生客户端连接不可用，当前只读 MCP 未指向所需模块 | [实际状态与续做步骤（本目录入口）](README.md#apifox-verification) |
