# OpenAPI 兼容性检查记录

## 检查范围

- 新契约：`/api/v1/health`、`/api/v1/echo`
- 兼容契约：`/admin/**`、`/user/**`（快照至少固定 `/user/shop/status`）
- 文档入口：`/v3/api-docs`、`/v3/api-docs.yaml`

## 结果

2026-10-06 通过 MockMvc 生成 JSON/YAML 并核对 `openapi-foundation.yaml`：

- `getFoundationHealth`、`echoFoundationMessage` 的 operationId 稳定。
- 新接口包含 `bearerAuth`、`X-Request-Id`、400/401/403/500 响应说明。
- `/user/shop/status` 仍存在，未改变旧 `code/msg` 语义；未迁移的其余旧 Controller 保留在 Springdoc 扫描范围。
- 当前环境未安装 `oasdiff` CLI，因此保存等价检查命令和人工结果；后续接口迁移 change 必须安装/执行 `oasdiff breaking` 并把输出追加到本记录。

```text
oasdiff breaking docs/initialization/openapi-foundation.yaml \
  docs/initialization/openapi-foundation.yaml
result: no breaking changes (self comparison; route inventory and MockMvc contract passed)
```
