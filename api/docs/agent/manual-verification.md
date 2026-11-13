# 对话接口手工验证与运行说明

本页对应第 3～5 节实现。接口契约见 [接口契约（本目录入口）](README.md#api-design)，模型配置见 [model-integration 文档（本目录入口）](README.md#model-integration)。
实际 Springdoc 分组：`GET /v3/api-docs.yaml/agent-conversation-v1`；仓库快照为 [agent-conversation-v1.openapi.yaml](../../../docs/api/agent-conversation-v1.openapi.yaml)。
`ChatHttpIT` 从随机端口应用导出并解析 YAML，断言六操作、鉴权、错误、SSE/JSON MIME 与事件样例。

## 启动与凭证选择

公共配置默认禁用模型；dev 直接在被忽略的 application-dev.yml 中配置 merchant.agent，dots 地址/模型/api-key 请求头见 [模型配置（本目录入口）](README.md#model-integration)。本机文件已预填并启用，填入 api-key 后以 dev 启动或重启 API 即可；不需要配置额外环境变量。
base-url 为 API 根地址，不能包含 `/chat/completions`。浏览器只持有本应用的 access token。
消费者使用目标 store 对应消费者登录流程取得的 access token；员工使用该商户员工登录流程取得的 access token。
员工也只能查看自己创建的会话，不能用员工 token 查看消费者会话。切换 store 或账号时同时切换 token 和 sessionId；不能通过 body 传 merchantId/accountId/role 切换身份。
不要使用 refresh token、其他商户 token 或模型 API key 作为 Bearer。凭证只放本机环境/会话变量，不写入导出契约。

跨域开发时在 `merchant.agent.allowed-origins` 配置精确来源，例如 `http://localhost:5173`；默认空列表不授权跨域。
允许 GET/POST/OPTIONS 和 Authorization/Content-Type/Accept/X-Request-Id；暴露 X-Request-Id/Location，credentials=omit，无通配来源。
允许来源的预检为 200；拒绝来源为 403。预检不建立聊天认证，实际请求仍须 Bearer。

## curl 验证

以下使用 POSIX shell；Windows 可在 Git Bash 执行。`BASE_URL`、`ACCESS_TOKEN` 只是执行 curl 时的本机 shell 变量，并非 Spring Boot 的环境变量配置，也不会从 application-dev.yml 自动读取。
`BASE_URL` 是本应用地址（例如 `http://127.0.0.1:8080`）；`ACCESS_TOKEN` 是消费者/员工登录本应用后返回的 `data.accessToken`，不是 application-dev.yml 中访问模型网关的 `merchant.agent.api-key`。后者只由后端使用。
`SESSION_ID` 使用创建结果中的字符串。可在当前 Git Bash 临时赋值，无需永久配置系统环境变量：

```bash
BASE_URL='http://127.0.0.1:8080'
read -r -s -p '本应用登录返回的 accessToken: ' ACCESS_TOKEN
printf '\n'
# 创建成功后，将返回的 data.sessionId 赋给 SESSION_ID，再执行发送/查询。
```

```bash
curl -i "$BASE_URL/api/v1/chat/sessions" \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -H 'X-Request-Id: chat-create-example' --data '{"title":"商品咨询"}'

curl -N -i "$BASE_URL/api/v1/chat/sessions/$SESSION_ID/messages" \
  -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream, application/json' -H 'X-Request-Id: chat-send-example' \
  --data '{"clientMessageId":"00000000-0000-4000-8000-000000000001","content":"你好"}'

curl "$BASE_URL/api/v1/chat/sessions/$SESSION_ID/messages?size=20" -H "Authorization: Bearer $ACCESS_TOKEN"
curl "$BASE_URL/api/v1/chat/sessions?size=20" -H "Authorization: Bearer $ACCESS_TOKEN"
curl "$BASE_URL/api/v1/chat/sessions/$SESSION_ID" -H "Authorization: Bearer $ACCESS_TOKEN"
curl -X POST "$BASE_URL/api/v1/chat/sessions/$SESSION_ID/archive" -H "Authorization: Bearer $ACCESS_TOKEN" -H 'Content-Type: application/json' --data '{}'
```

相同 clientMessageId/正文重复发送应返回 JSON 原 run 标识，不重新调用模型；改正文为 409/13003。
不同 key 同会话运行中为 409/13002。完成后归档成功且重复归档幂等；新 key 向归档会话发送为 409/13004。
用同商户另一账号或另一商户 token 访问相同 sessionId，详情/历史/发送/归档统一 404。
验证时按创建返回的 sessionId/messageId/runId 查询状态，不依赖共享库整表数量或空表。

## fetch POST 和中止

不能使用只支持 GET 的 EventSource。以下函数保留跨 chunk 的 UTF-8/事件边界，JSON 分支同时处理幂等和建流前错误。
每次新的发送意图生成一个 UUID，网络重试复用同一 UUID 与原文。

```javascript
async function sendChat(baseUrl, accessToken, sessionId, clientMessageId, content, signal, onEvent) {
  const response = await fetch(`${baseUrl}/api/v1/chat/sessions/${sessionId}/messages`, {
    method: 'POST', credentials: 'omit', signal,
    headers: {
      Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json',
      Accept: 'text/event-stream, application/json', 'X-Request-Id': crypto.randomUUID()
    },
    body: JSON.stringify({ clientMessageId, content })
  });
  const mime = response.headers.get('Content-Type') || '';
  if (mime.includes('application/json')) {
    const body = await response.json();
    if (!response.ok || body.code !== 0) throw Object.assign(new Error(body.message), { body });
    return body.data; // 幂等 JSON；随后按 messageId 查历史
  }
  if (!response.ok || !mime.includes('text/event-stream') || !response.body) throw new Error('Unexpected response');
  const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
  let pending = '', terminal = false;
  try {
    for (;;) {
      const { value, done } = await reader.read();
      if (done) break;
      pending = (pending + value).replace(/\r\n/g, '\n');
      let end;
      while ((end = pending.indexOf('\n\n')) >= 0) {
        const frame = pending.slice(0, end); pending = pending.slice(end + 2);
        const lines = frame.split('\n');
        const name = lines.find(line => line.startsWith('event:'))?.slice(6).trim();
        const payload = lines.filter(line => line.startsWith('data:')).map(line => line.slice(5).trimStart()).join('\n');
        if (!name || !payload) continue; // 包括 :heartbeat
        const data = JSON.parse(payload);
        onEvent(name, data); // start 保存标识；token 只追加 delta；done/error 为终态
        if (name === 'done' || name === 'error') terminal = true;
      }
    }
    if (!terminal) throw new Error('Stream ended before terminal; query history');
  } finally { reader.releaseLock(); }
}

const abort = new AbortController();
const clientMessageId = crypto.randomUUID();
// sendChat(baseUrl, accessToken, sessionId, clientMessageId, text, abort.signal, onEvent)
// 点击停止时：abort.abort()
```

客户端停止或网络中断不保证立即被服务端感知；心跳/下一次写入会触发取消，持久化 deadline 提供最终恢复。
中止后用相同身份请求 `GET /api/v1/chat/sessions/{sessionId}/messages`，按 runId/messageId 查终态及有限部分正文。
如首帧尚未到达，原 clientMessageId/原文也能用于幂等核对。STARTED/STREAMING 时可稍后再查，不能把断开当作已完成。
不使用 Last-Event-ID 重放，也不要自动以新 UUID 重新生成。SSE 心跳不刷新 access token 或认证会话；重连必须重新认证。

## 资源、故障和观测

接收事务先保存 USER/ASSISTANT 占位与共享 run/deadline，再异步调用模型；done 仅在终态事务提交后发出。
上下文仅取最近成功完整轮次。首 token/总时长、输出字符、并发、执行队列、SSE 队列和单次写入均有上限。
队列满或写超时终止为 FAILED；客户端断开取消为 CANCELLED；首 token/总 deadline 为 TIMED_OUT。
超时/终止后的 token 被丢弃。MySQL 故障发送安全依赖错误，不假报 done；如终态无法落库，由后续读取/发送按持久化 deadline 恢复。
重试仅限正文前网络错误、429/5xx，次数/退避共用总 deadline；不自动换供应商，也不保证上游仅计费一次。

结构化日志 `agent_run_terminal` 包含 requestId、runId、sessionId、merchantId、role、service、promptVersion、model、firstTokenMs、totalMs、terminalStatus、reason。
首 token 缺失时 firstTokenMs=-1；HTTP 过滤器耗时只表示初始请求分派，完整运行耗时以终态日志/指标为准。
常规日志不输出 API key/access token、消息正文、完整 Prompt、原始手机号或原始供应商错误；排障使用关联标识和安全错误码。
现有 Micrometer 注册表增加 `merchant.agent.runs{status}`、`merchant.agent.duration{status}`、`merchant.agent.first.token`。
status 标签为固定集合，不把 requestId/runId/商户放入指标标签。仅首 token 实际到达时记录对应计时器，不为缺失 usage 伪造 token 指标。
`ChatRunExecutionTest` 断言日志隐私与指标；`ChatSseLifecycleTest` 断言慢写、写失败、队列上限和心跳；`ChatHttpIT` 使用真实 Servlet 与独立 MySQL，认证边界用 SessionService 替身。
这组定向测试不替代第 7 节独立 Redis 的真实认证综合验收。

## 后续 Nginx 检查

部署反向代理时再针对消息 POST 核对 `proxy_buffering off`、缓存关闭、读取/发送超时大于应用总 deadline + 写入余量，避免压缩/代理聚合抹掉分段体验。
通过浏览器网络面板和 `curl -N` 确认 start/token 逐段到达，测试断开、慢客户端、CORS 和 X-Request-Id 透传；数据库共享运行权不依赖粘性会话。
当前没有部署 Nginx 或完整聊天前端。真实模型、独立 MySQL/Redis 综合验收和 Apifox 的实际状态见 [验收记录（本目录入口）](README.md#verification)；Apifox 操作见 [验收步骤（本目录入口）](README.md#apifox-verification)。
