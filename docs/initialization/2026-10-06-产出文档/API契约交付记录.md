# 基础 API 契约交付记录

## 文档入口

- JSON：`GET /v3/api-docs`
- YAML：`GET /v3/api-docs.yaml`
- 认证专组：`GET /v3/api-docs/auth-v1`、`GET /v3/api-docs.yaml/auth-v1`
- 本地 UI：`/swagger-ui.html`
- `dev/local/test` 开启文档；`prod` 关闭文档入口。

## 新 `/api/v1` 响应

普通 JSON 响应使用：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "requestId": "request-correlation-id"
}
```

`X-Request-Id` 响应头与 body 的 `requestId` 一致。公共错误码从 `10001` 段开始，模块实现时只追加自己的枚举，不提前占用商品、Agent 或评测段。

## 兼容路由

旧 `/admin/**`、`/user/**` Controller 保留原字段、`code/msg` 响应和认证头；当前迁移状态与调用方证据见 `旧接口迁移清单.md`。新接口迁移完成前不在旧路径静默改语义。OpenAPI 同时记录旧路径和新 `/api/v1` 路径，后续使用 oasdiff 检查差异。

## 特殊协议

- 文件上传：声明允许的 MIME 和大小上限；超限返回 HTTP `413`，不套普通 JSON 成功 envelope。
- 异步文档任务：创建返回 HTTP `202`，body 至少包含 `taskId`、`status`、`statusUrl`，并通过状态 URL 查询进度和失败原因。
- SSE：建立连接后只发送 `token`、`citation`、`tool_call`、`done`、`error` 事件。建立连接后发生错误发送 `error` 后结束流，不改发 HTTP `500` JSON；客户端重连、重复消息和断开取消由对应 Agent/文档模块测试覆盖。

## 基础快照

本 change 的稳定基础 operation 为 `getFoundationHealth` 和 `echoFoundationMessage`，导出快照必须包含 `bearerAuth`、`X-Request-Id` 参数、`code/message/data/requestId` 响应结构和 400/401/403/500 状态说明。完整导出由测试中的 Springdoc endpoint 生成，旧接口快照随迁移批次更新。

## 认证实现交付（2026-11-06）

`add-redis-auth-sessions` 的实际 [九操作快照](../../api/auth-v1.openapi.yaml) 来自 `AuthDevManualIT` 启动的隔离后端 Springdoc，包含短信、消费者首次自动建号登录、员工登录、统一 refresh/logout、me、员工创建/查询与消费者本人查询；没有独立注册接口。快照服务器使用相对地址 `/`，导入环境自行配置 Base URL。

登录/刷新返回 accessToken、opaque refreshToken、两类截止时间和脱敏 principal；私有资源声明 Bearer，refresh 只使用 JSON refreshToken/refreshRequestId，logout 可用 Bearer 或 JSON refresh。员工开通返回 201、Location 和账号摘要，不含密码/hash/token；首次/已有消费者登录均 200。快照登记统一 envelope、X-Request-Id、no-store、400/401/403/404/409/429/503/500，429 附 Retry-After；debugCode 只在 dev 显式 mock 回显，生产禁用。

Apifox 接入步骤：导入该 YAML → 配置隔离环境 Base URL → 私有资源选择 bearerAuth → refresh/logout body 传凭证，勿把 token 放 URL → 按 [人工联调说明](../../../docs/archive/20261113-auth/manual-verification.md) 建立场景。当前未操作 Apifox，也未完成浏览器接入或第 8 节全量验收；旧基础快照不作为本次认证实现的证据。
