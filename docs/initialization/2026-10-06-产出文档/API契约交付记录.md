# 基础 API 契约交付记录

## 文档入口

- JSON：`GET /v3/api-docs`
- YAML：`GET /v3/api-docs.yaml`
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
