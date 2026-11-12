## 真实兼容模型联调（6.3）

dev 本机忽略配置：service=dots，base-url=`https://note3-prev-api.askdiandian.com/v1`，model=`dots3-note-prev`；自定义 api-key 请求头由 LangChain4j 适配，不把 key 或应用 Bearer 写进文档/契约。
2026-11-08 21:47:34+08:00，通过本应用员工登录 → 创建会话 → 消息 POST → SSE → 历史查询；合成输入只要求简短问候，不发送业务隐私。

| 字段 | 实际结果 |
| --- | --- |
| sessionId | `112910648056217600` |
| runId | `112910648454676480` |
| clientMessageId | `bf807a01-d1bc-4397-b543-5c1dc26056dd` |
| SSE | start → token → done；text/event-stream；无 error |
| 正文 | 非空，共 8 字符；不对随机自然语言做固定答案匹配 |
| 终态 | 历史与 MySQL 助手行均 COMPLETED，流式拼接与历史正文完全一致 |
| 耗时 | firstTokenMs=57342，totalMs=57753；本机首 token/总时限为 90s/180s |
| 清理 | 归档测试会话，退出该次登录凭证 |

本次初次沙箱进程调用失败后，已用允许外连的 dev 进程重试成功，失败轮次未冒充成功。此验收证明兼容协议、非空流式输出和持久化闭环；不是 Apifox、RAG 或 Tool 事实质量验收。


| 验证 | 实际结果 | 原始证据（本机忽略文件） |
| --- | --- | --- |
| 真实兼容模型 HTTP/SSE → 历史 → MySQL | 通过，详见下文；不属于 Apifox 操作证据 | `.runtime/agent-real-model-result.json`、`.runtime/agent-dev-startup.log` |
