# Design

## Context

动机见 proposal.md。已只读核对当前代码、SQL、测试与 OpenSpec，未读取冻结模块档案。

| 位置 | 已观察到的事实 |
| --- | --- |
| `db/devdata/R__040_product_demo.sql`、`R__050_inventory_demo.sql` | 当前 M_3C_DEMO 只有 5 个商品，其中 3 个初始上架；脚本采用稳定 ID、依赖/身份 guard、只补缺失的 INSERT |
| `DevSeedUpgrade`、`DevSeedMigrationIT` | 维护工具登记 R010–R070；迁移测试含 18 次执行、8 条库存、8 个属性等旧基数断言，新增 R080 必须同步适配 |
| `view/src/views/merchant/ProductsView.vue` | 员工管理列表每页 20 条，新增按钮仍是占位；无需接入新表单 |
| `view/src/views/public/ProductListView.vue`、`view/src/api/catalog.ts` | 消费者商城每页 12 条，登录后仍读取 `/api/v1/stores/{storeSlug}/products`；搜索仅过滤已加载商品 |
| `ProductQueryService`、`ProductCacheStore` | 只缓存公开无 cursor 首页、商品详情、SKU 详情；员工/消费者受保护读取及 Agent 事实查询不走该缓存；缓存目录后仍实时聚合库存 |
| `ProductCacheStore.bump` | 版本键缺失时读取默认 1，bump 缺失分支也写 1；这是首次写入失效必须针对性验证的风险，不能用预先 bump 的测试掩盖 |
| `ProductAvailabilityIT` | 已覆盖缓存目录不含 inStock、库存变化即时可见、Redis 故障回源，适合增补目录热读次数与下架失效的证据 |
| `docker/nginx/dev.conf`、`view/vite.config.ts` | Vite 已代理 `/services`，Nginx 只匹配 `/api`；直连 8080/8082 的 WSDL 均 200，经 8081/5173 为 HTML 404 |

诊断期以真实 Nginx 的隔离回归复现了 WSDL `expected 200 but was 404`，对应临时测试已撤回，不算已交付测试。后续 apply 必须提交正式回归并验证修复。现有工作树含用户改动，实施只能叠加本 change 的必要修改。

## Goals / Non-Goals

**Goals:** 通过一个追加种子让两个现有页面展示足够的真实商品；用现有接口、Redis 键和隔离测试解释缓存；修复开发代理遗漏。

**Non-Goals:** 商品新建/编辑表单、图片上传或图片生成、额外账户、下单支付、新订单/物流样例、缓存仪表盘、压测系统、新缓存层、生产代理调整。商品缩略图继续使用现有展示机制。

## Decisions

### 1. 一个 R080，20 个新增 SPU，40 个 SKU

新增 `api/src/main/resources/db/devdata/R__080_product_showcase.sql`，不修改 R040/R050 以免已有种子因 checksum 变化重跑。R080 内依次写分类、商品、SKU、结构化属性、库存，复用现有租户和 3 个分类，并增加 5 个根分类。每商品初始 PUBLISHED、两个 SKU 初始 ACTIVE；每商品至少一个 SKU 为正库存。少量第二 SKU 为零，展示“有货/缺货”差异。

相比只增加 SKU，此选择满足“多 20 个商品”；相比重置原种子，此选择保留用户已下架状态；相比只加商品不备货，此选择能展示完整详情与可售状态。

预留标识（当前资源及活动规范中未发现占用，SQL 执行仍必须 guard）：

| 数据 | ID 范围 | 初始数量 |
| --- | --- | --- |
| 新根分类 | 2500000000000801–0805 | 5 |
| 商品 | 2500000000000901–0920 | 20 |
| SKU | 2500000000001001–1040 | 40 |
| 结构化属性 | 2500000000001101–1140 | 40，每商品 2 个 |
| 库存 | 2500000000001201–1240 | 40 |

分类 801–805 分别为移动电源、电脑配件、输入设备、存储设备、智能配件，完整 ID 均带表中前缀。原充电器、数据线、耳机分别沿用 2500000000000101–0103。

商品清单按下表顺序分配 901–920；第 n 个商品的 SKU 为 `1001+2(n-1)`、`1002+2(n-1)`，属性与库存按同序映射。业务编码冻结，具体价格、数量、规格与属性正文由实施 SQL 维护，不在多份手册重复复制数值。

| 末三位 | 商品编码 | 展示名称 | 分类 |
| --- | --- | --- | --- |
| 901 | P-DEMO-CHG-30W | 30W 迷你快充充电器 | 充电器 |
| 902 | P-DEMO-CHG-100W | 100W 三口氮化镓充电器 | 充电器 |
| 903 | P-DEMO-CHG-140W | 140W 桌面充电站 | 充电器 |
| 904 | P-DEMO-WIRELESS-3IN1 | 三合一无线充电座 | 充电器 |
| 905 | P-DEMO-CABLE-240W | 240W 编织快充数据线 | 数据线 |
| 906 | P-DEMO-CABLE-A2C | USB-A 转 USB-C 数据线 | 数据线 |
| 907 | P-DEMO-HEADSET-ANC | 头戴式降噪耳机 | 耳机 |
| 908 | P-DEMO-EARBUDS-OPEN | 开放式蓝牙耳机 | 耳机 |
| 909 | P-DEMO-BANK-10K | 10000mAh 便携移动电源 | 移动电源 |
| 910 | P-DEMO-BANK-20K | 20000mAh 笔记本移动电源 | 移动电源 |
| 911 | P-DEMO-HUB-8IN1 | 八合一 USB-C 扩展坞 | 电脑配件 |
| 912 | P-DEMO-HDMI-21 | HDMI 2.1 高清连接线 | 电脑配件 |
| 913 | P-DEMO-STAND-LAPTOP | 折叠笔记本支架 | 电脑配件 |
| 914 | P-DEMO-KEYBOARD | 轻薄无线键盘 | 输入设备 |
| 915 | P-DEMO-MOUSE | 三模静音鼠标 | 输入设备 |
| 916 | P-DEMO-SSD | 便携固态移动硬盘 | 存储设备 |
| 917 | P-DEMO-FLASH-DUAL | 双接口 USB 闪存盘 | 存储设备 |
| 918 | P-DEMO-TFCARD | 高速存储卡 | 存储设备 |
| 919 | P-DEMO-SOCKET | 智能计量插座 | 智能配件 |
| 920 | P-DEMO-TRACKER | 蓝牙寻物标签 | 智能配件 |

SKU 编码由商品编码的 `P-` 前缀替换为 `SKU-`，追加 `-A`、`-B`。属性必须是适合该商品的接口、协议、用途、容量或兼容信息；不把同一段无意义文案复制到 20 个商品。品牌使用既有 Gba 系列演示品牌，不暗示实际厂家背书。

### 2. 先验证全体身份，再补缺

SQL 用临时预期数据集与 NOT NULL guard，全部 guard 成功后才进入业务 INSERT。验证 M_3C_DEMO、引用的既有分类及其租户，新增分类/商品/SKU 的固定 ID 与自然键，属性的商品/名称，以及库存的同商户 SKU 身份。重复执行不 UPDATE 人工字段，不使用 INSERT IGNORE 或冲突覆盖。

guard 不把可变名称、价格、库存、状态或属性值当作不可变身份。商品/SKU 业务编码、商户和父资源关系构成身份；新增分类的稳定名称/父级、属性名称及其父商品采用与现有种子一致的 guard 约定。演示数据不产生库存流水，不改历史订单快照。

更新 devdata README 的编号/范围/执行依赖；为 `DevSeedUpgrade.NEW` 登记 R080。新空库正常 dev 迁移由 18 次变为 19 次，包含 R080 一次，未变更重启为 0。测试通过租户及新编码/ID 集合核验数据，旧演示集合的断言显式限定范围，不用删掉断言或依赖空表来兼容新种子。

### 3. 最小缓存演示只加手册与针对性测试

新增日常操作说明 `api/docs/deployment/product-demo.md`，从该目录 README 导航；不新增自动改状态脚本。手册给出六步短流程，命令不包含真实凭证，access token 从当前会话/环境获得。

| 步骤 | 操作与可观察证据 |
| --- | --- |
| 准备 | 关闭 Mock，启用现有商品缓存，以 M_3C_DEMO 员工/消费者登录；加载完整分页确认 20 个新增编码。指定 P-DEMO-CHG-30W，仅在其当前为 PUBLISHED 且无人并发维护时演示 |
| 冷读 | 仅删除指定商品详情键和所选首页键，保留版本键；GET `/api/v1/stores/M_3C_DEMO/products?size=12` 与指定详情，记录对应键由不存在变为存在、TTL 大于 0 |
| 热读 | 重复完全相同请求，键继续存在；隔离集成测试用 Mapper spy 证明目录读取不再增加，库存 SQL 仍可发生，不用耗时做唯一证据 |
| 下架 | 员工 POST `/api/v1/products/{productId}/unpublish`，观察版本变化；公共详情 404、列表移除、员工管理详情为 UNPUBLISHED；消费者受保护读取不返回该商品 |
| 恢复 | 员工通过 publish 恢复，再读公共详情/列表成功并生成新版本条目；出现异常也执行恢复，失败需明确报告 |
| 边界 | 查看登录消费者商城网络请求仍是公开路径；带 cursor 的下一页不缓存。Redis 故障回源及首次版本初始化由隔离自动化验证，不停止 dev Redis |

当前商品键命名为 `gba:product:catalog:v2:`，版本键为 `ver:{merchantId}`，详情键为 `e:{merchantId}:v{version}:product:{productId}`，首页键为 `e:{merchantId}:v{version}:page:0:size:12`；版本键缺失时按当前代码默认值处理。目录条目 TTL 15 分钟，版本键 TTL 配置为 24 小时；版本失效使旧键不可达，不要求旧键立即被物理删除。禁止 FLUSHDB、FLUSHALL、广域删除及在共享 dev 使用 MONITOR 记录认证数据。

对“版本键缺失 → 公开预热 → 第一次员工写入”添加独立回归；若重现旧版本复用，局部修正 `ProductCacheStore` 的默认版本与原子初始化/递增一致性，保持既有接口、目录/库存分离、TTL 和租户隔离。缺失版本的首次并发写也不得回退或复用写前版本，不引入第二种缓存策略。现有 Redis 故障回源测试继续执行。

种子直接写 SQL 不触发服务层缓存失效。R080 执行后、恢复浏览前，在确认成功的目标 dev 环境通过既有商品写操作触发该商户失效，或只推进该商户商品版本并维持其 TTL；手册说明这一点。单纯重启后端不会清空 Redis。

### 4. SOAP 只修开发路由

`docker/nginx/dev.conf` 为 `/services/shipment` 增加独立精确 location，复用 dev upstream；保留原路径与查询参数、Content-Type、SOAPAction、SOAP Header 以及现有请求关联和日志脱敏策略。无需扩大整个 `/services/**` 或修改 Vite 代理，不在 Nginx 注入凭证。HTTP POST 不启用 non_idempotent 重试。

更新运行说明中“只代理 /api”的描述，明确精确 SOAP 例外。活动配置由 `15-host-gateway.sh` 从模板生成，修改模板后必须重新生成，再执行 `nginx -t` 和 reload；只对旧生成文件执行 reload 不会生效。可执行 `docker compose exec -T nginx /docker-entrypoint.d/15-host-gateway.sh` 后检查并重载，避免为了这项修复重启其他中间件。

在 `NginxLoadBalancingIT` 中正式增加 GET WSDL、有效凭证的 ping/query、无效凭证 XML Fault 及无关路径 404。先证明旧配置失败，再用新配置转绿，保留 REST/SSE 现有覆盖。实际前端代理全链路另以 5173 请求确认；WSDL 200 不等于 SOAP 鉴权成功，服务凭证不匹配仍应按既有契约失败。

## Risks / Trade-offs

- [20 个商品超过首页容量] → 明确使用加载更多及游标遍历核对完整编码集合，不把首页条数当作新增数量。
- [原数据已有人工下架与新价格] → 只追加新身份，受控重跑测试同时覆盖原集合与新增集合的人工值。
- [首次版本初始化可能保留旧缓存] → 先跑缺失版本键回归，再做局部一致性修复；不以预先 bump 的 happy path 作为唯一证明。
- [SQL 补种后已有 Redis 首页仍旧] → 迁移成功后只使目标商户商品缓存失效；不清空登录、ChatMemory 或其他租户数据。
- [真实演示下架与他人维护冲突] → 仅操作指定新增商品，事前检查状态并约定无人并发维护，最后恢复并核验；不得动原有下架商品。
- [新 R080 使维护工具与硬编码基数失配] → 同步白名单、顺序、并发 migrate 和限定集合的基数测试。
- [SOAP 凭证尚未配置] → 404 修复与凭证校验分别验收；不读取或提交个人密钥，不关闭拦截器。

## Migration Plan

1. apply 阶段先在 Testcontainers 验证 R080 的首次加载、重跑、冲突、可见性和缓存行为；不通过改 V/history/repair 适配现有库。
2. 正常 dev 启动依 Flyway 锁顺序加载 R080；双后端先启动一个完成迁移，再启动另一个。新脚本是追加 DML，已合法到最新 V 的 dev 无需重做旧 V5 升级工具流程。
3. 在明确目标 dev、完成既有备份流程后运行；核对仅新增约定记录，保留原状态与旧历史。执行商户级商品缓存失效，刷新并加载两端列表。
4. 生成 Nginx 活动配置，先 `nginx -t` 再 reload，核验 8081 和 5173 的 SOAP 入口。
5. 完成手册演示并恢复指定商品状态。实际结果写入 `api/target/product-demo-cache/`，一次完成事件记录进入实际完成日期的 testing 目录，通过 README 导航。
6. 回退代码不自动删除已使用的新数据或改 Flyway 历史；如需停止展示，以员工接口下架新增商品。数据库问题按现有备份恢复/向前修复流程处理；Nginx 可恢复上一份模板并重新生成、检查、重载。
