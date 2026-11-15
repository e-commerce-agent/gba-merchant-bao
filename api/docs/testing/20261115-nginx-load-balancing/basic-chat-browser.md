# 基础对话前后端联调（2026-11-15）

对应 `add-nginx-load-balancing` 第 4 节，仅验收基础对话。RAG 来源、Function Calling 工具摘要等前端能力及其专属错误不在本轮实施和验收范围；后端沿用已启动的 dev 配置。

## 结果与 403 根因

浏览器真实登录、新建会话、发送、收到真实模型回答和刷新恢复历史均成功。六个 OpenAPI 操作通过 `127.0.0.1:5173 → Vite → localhost:8081 Nginx → 8080/8082` 验证。本地忽略的 `.env.development.local` 关闭两个 Mock 开关，保留仓库离线演示默认值。

修复前最小复现（无需登录）：

```powershell
curl.exe -i -X POST http://127.0.0.1:5173/api/v1/chat/sessions `
  -H 'Origin: http://127.0.0.1:5173' -H 'Content-Type: application/json' --data '{}'
```

返回 `403 Invalid CORS request`；仅去掉 Origin 就得到正常 JSON 401/10002。Vite 原 `changeOrigin: true` 将 Host 改为 `localhost:8081`，Origin 仍为页面 5173；Nginx 保留收到的 Host，Spring 因来源不一致而拒绝。

修复为 `changeOrigin: false`，同时保留原 Host/Origin。同一命令恢复正常 JSON 401，恶意 `Origin: https://evil.example` 仍为 403。没有移除 Origin、使用通配来源或修改后端安全配置。

代理回归测试实际启动 Vite 和随机端口 HTTP 接收端，验证 localhost/127.0.0.1 的 Host、原始 Origin、Bearer、路径与方法透传。该测试修复前因 Host 改写失败，修复后通过。

## 基础对话修复

- 创建会话把契约 `sessionId/ACTIVE` 转成页面 `id/AGENT`，避免后续 `/undefined/messages`；URL 保留真实 ID，创建失败有提示。
- 历史气泡使用真实 sessionId，按 nextCursor 读取；幂等 JSON 重试从历史恢复原助手，避免空白成功。
- SSE 支持分片 UTF-8 和 LF/CRLF；错误使用契约 `code/message/requestId`。缺少 done/error 的断流报中断，不伪造成功，结束时释放 reader。
- 保留离线 Mock；没有新增 RAG/Function Calling 展示或修改其后端实现。

## 实际浏览器验证

使用开发演示消费者真实验证码登录，点击“新建会话”，得到 `115372849551642624`；发送合成的充电器兼容性问题，显示真实回答。刷新同一 URL 后，两条持久化消息仍在。截图位于 `api/target/nginx-browser/basic-chat-history.jpg`，浏览器页保留供继续操作。

此处只证明基础问答与历史回显，不声明 RAG/Function Calling 前端已实现或已验收，也不评价回答的事实准确性。

## 六操作和边界

在 view 显式运行 `node scripts/verify-chat-dev.mjs`。脚本只允许 loopback，要求 dev 短信 mock 的 debugCode，新增标记合成会话、调用真实模型，最后撤销自身登录。它不属于隔离的 `npm test`，会写 dev 测试会话；报告不含 token、验证码或消息正文。

| 检查 | 实测结果 |
| --- | --- |
| 创建 | 201 / ACTIVE / 十进制字符串 ID |
| 列表、详情 | 200，ID 一致 |
| 发送 | 200 / SSE：start 73ms、token 3767ms、done 3798ms |
| 历史 | 200，size=1 + nextCursor 得到 USER、COMPLETED ASSISTANT |
| 归档 | 200 / ARCHIVED；重复归档 200 |
| 同键同正文 | 200 JSON，返回原助手 messageId |
| 同键不同正文 | 409，无额外消息 |
| 归档后发送 | 409，历史仍仅两条 |
| size=0 / 未登录 / 恶意来源 | 分别 400 / JSON 401 / 403 |

脚本会话 `115373132713299968` 已归档。18 条 `chat-dev-` Nginx 日志分别命中 8080/8082 各 9 次；首次 SSE 由 8080 处理，同键 JSON 重试由 8082 返回原结果。

真实模型本次只有一个 token 事件，不能据此证明持续逐 token 时序；代理渐进交付的稳定证明仍是本目录第 3 节报告的受控协议替身，没有为演示而改模型/工具选择行为。

## 检查与证据

- 前端 Node tests：9 passed，0 failed，覆盖真实 Vite 代理、会话映射、历史 ID、分片 SSE、错误、缺少终态、分页幂等恢复、无效 JSON 和离线 Mock。
- `vue-tsc --noEmit`、`vite build`：通过，构建 9.41 秒。
- `openspec validate add-nginx-load-balancing --strict`：通过。
- 本轮只改前端、任务与说明，没有重跑 Maven 全量；原报告的 397 项属于上一轮。

当前终端默认 node/npm 受 nvmd 未设置默认版本影响，实际使用已安装的 workspace Node 调用上述同一工具，没有修改全局 Node 设置。

脱敏证据在 `api/target/nginx-browser/`：`agent-dev-results.json`、`upstream.log`、`frontend-tests.log`、`frontend-build.log` 和浏览器截图。没有记录凭证或模型密钥，没有删除 dev 数据或卷。
