# 商品展示数据与 Redis 缓存演示

本地店铺为 `M_3C_DEMO`。开发环境先启动 MySQL/Redis 和 Nginx，再以 dev profile 启动后端及 Vite；具体启动命令沿用 [本地运行说明](local-development.md)。正常 dev 启动由 Flyway 加载 R080；运行中的服务可通过数据库客户端显式执行 [R080](../../src/main/resources/db/devdata/R__080_product_showcase.sql)，目标为 `127.0.0.1:3307/gba_dev`。执行前确认目标和依赖，客户端遇错停止；不修改 Flyway history。显式执行后，下次正常 dev migrate 仍会登记该新脚本，补缺逻辑不会覆盖已有数据。

R080 新增 20 个商品，各有两个 SKU、两个属性及两条库存，全部归属该店铺。原商品下架状态保留。消费者打开 <http://localhost:5173/store/M_3C_DEMO>，每页 12 条，点“加载更多”；员工使用现有登录打开 <http://localhost:5173/merchant/products>，每页 20 条，也需加载更多。搜索只筛选已加载的商品。新建商品按钮无需使用。

## 运行中直接补种后的缓存刷新

SQL 不经过业务写接口，不会自动使 Redis 缓存失效。连接开发 Redis `127.0.0.1:6380`、数据库 0，在 Redis 控制台只推进目标商户版本：

```redis
GET gba:product:catalog:v2:ver:1000000000000001
EVAL "local v=redis.call('GET',KEYS[1]); if not v then redis.call('SET',KEYS[1],'2','EX',86400); return 2 end; local n=redis.call('INCR',KEYS[1]); redis.call('EXPIRE',KEYS[1],86400); return n" 1 gba:product:catalog:v2:ver:1000000000000001
```

版本不存在时公开读取默认使用 1，因此手工失效应推进到 2，不能初始化为 1。已存在时递增并保持 24 小时 TTL。旧版本条目按原 TTL 自然过期。此操作完成后刷新页面即可，无需重启后端，不使用 FLUSHDB/FLUSHALL/MONITOR。

## 观察冷读与热读

先 GET 版本键，以实际返回值替换下面的 `{version}`。消费者首页、详情和 SKU 详情分别对应：

| 请求路径（均经 localhost:5173） | Redis 键（前缀 `gba:product:catalog:v2:`） |
| --- | --- |
| `/api/v1/stores/M_3C_DEMO/products?size=12` | `e:1000000000000001:v{version}:page:0:size:12` |
| `/api/v1/stores/M_3C_DEMO/products/2500000000000901` | `e:1000000000000001:v{version}:product:2500000000000901` |
| `/api/v1/stores/M_3C_DEMO/skus/2500000000001001` | `e:1000000000000001:v{version}:sku:2500000000001001` |

在新版本条目尚未生成时，使用 `EXISTS <完整键>` 确认不存在；请求一次后观察 `GET <完整键>`、`TTL <完整键>`，应出现缓存及约 900 秒 TTL。重复相同请求，数据一致、TTL 继续递减。若已有条目，只删除表中要演示的完整条目键，保留版本键。不要凭响应耗时推断命中。

只有公开无 cursor 首页、商品详情和 SKU 详情使用这层目录缓存。消费者登录商城仍调用公开路径；员工 `/api/v1/products`、消费者受保护读及带 cursor 的后续页不属于命中证据。缓存 JSON 不保存 `inStock` 或库存数量，响应的有货状态仍实时查询。现场键与 TTL 观察不等于 Mapper 查询次数证明，后者需另做隔离验证。

若需继续演示写入失效，可用员工页面对原为 PUBLISHED 的新增商品 `P-DEMO-CHG-30W` 临时下架，观察版本推进、公开详情 404、列表移除，再通过员工页面恢复上架并确认公开详情恢复。异常退出也需恢复该商品；不要操作原先已下架的旧商品。

## 2026-11-16 现场记录

- 当前运行的 gba_dev 已导入：分类 5、商品 20、SKU 40、属性 40、库存 40；商品均 PUBLISHED，SKU 均 ACTIVE，每商品至少一个正库存 SKU。
- 重复执行 R080 后，五张目录/库存表内容及库存流水、幂等表计数均不变。导入前后原商品和库存快照一致。
- 目标商品版本由 4 推进到 5。上述三个键冷读前不存在，冷读后存在；首页 PTTL 899949 → 897772 ms，商品详情 899950 → 897851 ms，SKU 详情 899990 → 897835 ms；重复读取响应数据相同，缓存不含库存字段。
- 公开分页为 12 + 9，共 21 个商品，新增 20 个编码不重不漏；四个原下架商品和另一商户商品的公开详情均返回 404。
- 本轮未执行员工/消费者登录会话验证、临时下架恢复、Mapper 查询次数统计或 IT。原始现场 JSON 留在本地 `api/target/product-showcase-live.json`，不提交真实凭证或响应令牌。
