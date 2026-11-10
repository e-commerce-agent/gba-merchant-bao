# dev 演示业务数据

只由 dev 加载，不进入 test/prod 的迁移 locations。
设计手稿约定一个 3C 样板租户 M_3C_DEMO 和一个最小隔离对照租户 M_ISOLATION_TEST；
二者的租户根位于 db/seed/nonprod，不在此重复创建。

后续按模块增量提供幂等演示数据：商品模块负责充电器、数据线、耳机及 SKU；
库存模块负责可售数量；订单模块负责订单及订单项快照；物流模块负责运单和轨迹；
知识/RAG 模块负责公开及私有知识；会话模块负责最小会话与消息。
未实现模块不预建表，不将旧餐饮数据冒充上述样板数据。会话模块已由 V5 提供两张表，物流模块由 V6 提供 `shipment`/`shipment_track`，订单模块由 V7 提供 `trade_order`/`trade_order_item`。
用例需要的数据单独放 src/test/resources/db/fixture，由 @Sql 显式加载。

R__020_auth_demo_accounts.sql 仅在 dev 提供两个租户首员工和三个消费者，稳定用户名/手机号缺失才插入，不重置 hash/status。开发密码与联调步骤见 api/docs/auth/manual-verification.md；生产开通延期。普通 test 不加载，AuthDevManualIT 仅在独立临时基础设施显式模拟 dev 加载。

租户与账号仅补缺失记录，保留人工名称/状态/密码等资料；预期租户缺失或稳定身份冲突时通过 `dev_seed_*` 校验明确失败。R 首次或校验和变化才执行，上游变化不自动触发下游；手工删除数据后重启不自动补回。补种须显式受控执行，不改 history。
## 初始化顺序

；后续真实模块再分配三位编号及依赖，不在各模块重复维护编号。新 V 不依赖 R 数据。

先执行所有待执行 V，再按 R 描述的三位数字前缀排序：nonprod 的 `R__010_local_test_tenants.sql` → devdata 的 `R__020_auth_demo_accounts.sql` → `R__030_chat_demo.sql` → `R__040_shipment_demo_data.sql` → `R__050_order_demo_orders.sql`。locations 顺序不表达依赖，不使用 AFTER_MIGRATE 回调或启动补写。

编号统一登记：010 租户（dev/test）、020 账号（仅 dev）、030 会话（仅 dev，依赖 010/020）、040 物流运单与轨迹（仅 dev）、050 订单与订单项（仅 dev）, 050 商品（仅 dev，依赖 010；充电器/数据线/耳机及多 SKU、未发布与停售样例、隔离租户对照商品，见 api/docs/product/module.md）；后续真实模块再分配三位编号及依赖，不在各模块重复维护编号。新 V 不依赖 R 数据。

R030 提供四个本人会话（同商户两个消费者、一个员工、另一商户消费者），每个会话一轮静态演示问答，不表示已接入真实模型。会话 ID 为 2200000000000001–4，消息 ID 为 2300000000000001–8，run ID 为 2400000000000001–4，账号沿用 R020。只补缺失记录，不覆盖人工内容、状态或运行信息；缺账号/商户、固定 ID 归属或消息序号/幂等键/run 标识冲突时明确失败。详见 [会话数据设计](../../../../../docs/agent/data-design.md)。

R040 仅在 dev 提供物流演示：M_3C_DEMO 两单（运输中 3 段轨迹、已签收）与 M_ISOLATION_TEST 一单（运输中），幂等补缺；order_id/order_no 与 R050 订单演示数据对齐，NOT_SHIPPED 由服务在无运单记录时合成，不落数据行。物流联调步骤见 api/docs/shipment/manual-verification.md。

R050 仅在 dev 提供订单演示：M_3C_DEMO 六单覆盖全部订单/支付状态及订单项成交快照，M_ISOLATION_TEST 一单；幂等补缺。product_id/sku_id 为追溯引用不建外键。订单联调步骤见 api/docs/order/README.md。

R050 提供两租户商品目录演示数据：M_3C_DEMO 的充电器/数据线/耳机及多 SKU（功率+插头、长度+颜色、颜色组合，含一个 DISCONTINUED SKU）、一个 UNPUBLISHED 与一个 DRAFT 商品、结构化兼容性属性，以及 M_ISOLATION_TEST 的最小对照商品。分类/商品/SKU/属性 ID 分别为 2500000000000101–105、2500000000000201–206、2500000000000301–309、2500000000000401–408。只补缺失记录，不覆盖人工内容与状态；租户缺失、分类名/商品编码/SKU 编码与固定 ID 归属冲突时通过 dev_seed_* 校验明确失败。库存数量由库存模块后续提供，不在本脚本预建。

旧名已执行库按 `api/docs/deployment/dev-seed-upgrade.md` 先完成一次性受控升级；不保留运行期兼容空脚本，不自动 repair，不混跑旧迁移包，不删除卷。生产只加载 migration/common，不执行开发种子改名 repair。
