# Proposal

## Why

目前开发商城的可售商品较少，人工下架后更难展示员工管理、消费者浏览和游标分页。现有 Redis 商品缓存已经实现，但缺少一组可重复演示的商品及清晰的命中、失效证据；同时开发 Nginx 漏转发 SOAP 路径，导致物流页面收到 HTML 404。

## What Changes

- 新增 dev 专用 `R__080_product_showcase.sql`，为 `M_3C_DEMO` **额外增加 20 个商品/SPU**，每个商品 2 个 ACTIVE SKU，配套结构化属性及 40 条库存；初始商品全部 PUBLISHED，供现有员工端、消费者端直接读取。
- 使用新的稳定 ID 与业务编码，只补缺失并预检身份冲突；保留既有商品下架状态、人工价格、库存和历史订单。登记种子编号与依赖，适配现有开发种子维护工具及迁移测试。
- 提供最小缓存演示手册和针对性自动化证据：公开首页/详情冷读与热读、员工下架触发商户版本失效、恢复上架；覆盖版本键缺失时的首次写入，若复现失效缺陷则只修正版本初始化/递增边界。准确区分员工管理接口、消费者商城公开接口、实时库存与目录缓存，不增加缓存面板、命中响应头或生产监控接口。
- 补齐开发 Nginx 的 `/services/shipment` 和 WSDL 转发，保留 SOAP 服务凭证及双后端原始路径，覆盖 ping、物流查询和错误凭证回归。

## Capabilities

### New Capabilities

无。

### Modified Capabilities

- `commerce-module-integration`：追加开发商品展示数据的数量、关联、可见性、补缺和缓存演示验收要求。
- `application-technical-baseline`：追加开发 SOAP 代理可达性要求，澄清开发入口同时承载 REST 与既有 SOAP 服务。

## Impact

- 数据：新增一个 repeatable seed，不修改已执行 V 迁移及 R010–R070；正常 dev Flyway 执行新增 R080，test/prod 不自动加载该数据。
- 后端维护与测试：`DevSeedUpgrade` 的允许脚本集合，`DevSeedMigrationIT` 的执行数量/顺序/业务计数，商品缓存与可见性集成测试；不实现商品新增表单或新建 API，不扩展缓存算法。
- 开发代理：`docker/nginx/dev.conf`、现有 Nginx 集成测试及本地运行说明；不改变生产部署。
- 文档：devdata README、部署/脚本/testing 目录入口及一次完成事件的验收记录；SQL 是商品字段与数值的唯一最终数据源。
- 不新增依赖，不改认证账号和店铺归属，不自动操作本机数据库、清空 Redis、重置数据卷或重新上架既有商品。
