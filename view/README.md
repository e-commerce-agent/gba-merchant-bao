# view — 前端（粤港澳商户宝 Web 端）

Vue 3 + TypeScript + Vite + Pinia + Element Plus + ECharts，单应用多路由：
`/public`（公开商城）、`/consumer`（消费者中心）、`/merchant`（商户客服工作台）。
平台管理后台本期不实现（设计手稿 2.2.4）。

## 启动

```bash
cd view
npm install
npm run dev      # http://127.0.0.1:5173
```

构建与类型检查：

```bash
npm run build    # vue-tsc --noEmit && vite build
npm run typecheck
```

## Mock 与真实接口切换

当前 `VITE_USE_MOCK=true`，全部请求由 `src/mock/` 拦截，**不需要启动后端**。
后端接口就绪后只需改 `.env.development`：

```bash
VITE_USE_MOCK=false   # 关掉 Mock，走 vite 代理转发到后端
```

代理配置读取 `VITE_PROXY_TARGET`（默认 http://localhost:8080），把 `/api` 转发到后端，
因此本地开发不依赖后端 CORS 配置。

本机若不想污染仓库，复制 `.env.example` 为 `.env.local` 再改（`.env.local` 已被忽略）。

演示账号（仅 Mock）：

| 端 | 账号 |
|---|---|
| 消费者 | 手机号 `13800138000`，验证码 `123456` |
| 商户员工 | `M_3C_DEMO` / `admin` / `Admin@123` |

## 物流页联调（已接真实接口）

「我的订单」→ 单卡「物流详情」抽屉，走 `GET /api/v1/orders/{orderId}/shipment`（shipment-v1）：

| 后端返回 | 前端表现 |
| --- | --- |
| 200 + `status=IN_TRANSIT` | 承运商 / 运单号 / 预计送达 + 轨迹时间线（倒序，最新高亮） |
| 200 + `status=DELIVERED` | 承运商 / 运单号 / 签收时间 + 轨迹时间线 |
| 200 + `status=NOT_SHIPPED` | 灰底「尚未发货」说明，不报错、不使用错误色 |
| 404 / `code=10004` | 统一文案「订单不存在或无权查看」（不区分“没有”和“不是你的”） |
| 401 | 统一拦截：静默续期 → 失败踢回登录页 |

真接口联调步骤：

```bash
# 1) 后端：切物流分支起服务（PR #14 合并后可直接用 main）
git checkout feat/shipment-web-service && mvn -f api/pom.xml spring-boot:run

# 2) 前端：关掉 Mock
echo 'VITE_USE_MOCK=false' >> view/.env.local
cd view && npm run dev

# 3) 用 devdata 演示单验收（订单 id 与 R__030/R__040 对齐）
#    2100000000000302 → 运输中（顺丰 SF1000000000301，3 段轨迹）
#    2100000000000301 → 已签收
#    2100000000000303 → 待发货（无运单，后端合成 NOT_SHIPPED）
```

Mock 模式（默认）返回同样的三单，切换前后现象一致，便于无后端时演示。

## 目录结构

```
src/
  api/          请求层：request.ts 统一封装（Bearer、X-Request-Id、统一响应体解包）
                types.ts 与后端契约对齐的 TS 类型；auth/catalog/order/shipment/chat/knowledge 按模块拆分
  mock/         本地 Mock（结构对齐 db/migration 表设计），切真实接口后整份删除
  stores/       Pinia：auth（登录态持久化）、app（侧栏折叠）
  router/       路由与守卫（登录态、主体类型校验、guestOnly）
  layouts/      PublicLayout（商城）/ ConsoleLayout（消费者与商户共用控制台壳）
  components/   BrandMark、ProductThumb（SVG 商品插画）、ChatBubble（含引用与工具轨迹）等
  styles/       tokens.scss 设计令牌、base.scss 基础排版、element.scss 主题对齐
  views/        public / consumer / merchant / auth 页面
```

## 与后端的契约约定（重要）

- 统一响应体：`{ code, message, data, requestId }`，`code === 0` 为成功；HTTP 状态表达协议结果。
- 认证：`Authorization: Bearer <jwt>`；请求头 `X-Request-Id` 随机生成并透传。
- 命令式接口用冒号风格：`:takeover`、`:resume-agent`、`:retry`、`:stream`。
- 消费者对话最终走 SSE（`POST /v1/chat/sessions/{id}/messages:stream`），
  当前 Mock 用普通 JSON 模拟，SSE 接入位置在 `ChatView.vue` 的 `send()`。
- 字段与路径以后端导出的 `/v3/api-docs.yaml` 为准，不一致时以后端契约为先。

## 待办

- [x] 真实登录 / 验证码 / 登出（Redis 会话模块）
- [x] 订单列表与详情（order-v1）
- [x] 物流详情抽屉（shipment-v1，四态）
- [ ] SSE 流式回答与打字机效果
- [ ] 商品 / 库存 / 知识库写操作表单（等 inventory / catalog 契约稳定）
- [ ] 商户端会话接管与消息的实时刷新（轮询或 SSE）
- [ ] 移动端深度适配（当前已做基础响应式）
- [ ] 公开商城路径改 `storeSlug` 口径（等团队最终确认后统一改）
