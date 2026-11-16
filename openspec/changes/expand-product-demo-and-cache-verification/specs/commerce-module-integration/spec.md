# Spec Delta

## ADDED Requirements

### Requirement: Development showcase shall add twenty products with complete sellable references

开发补充种子 MUST 为 `M_3C_DEMO` 额外提供 20 个不同商品、每个商品 2 个 SKU、配套结构化属性与每 SKU 一条同商户库存。首次插入的商品 MUST 为 PUBLISHED、SKU 为 ACTIVE，且每个商品至少一个 SKU 有可售库存。数据 MUST 仅由 dev 自动加载，不新增账户、订单或物流记录，不实现商品新建页面。

#### Scenario: A fresh development database loads the showcase
- **WHEN** dev 通过正常迁移加载新增补充种子
- **THEN** 新增固定商品编码集合恰有 20 个商品、40 个 SKU、40 条库存，关联完整且属于 M_3C_DEMO，每个商品具备规格和结构化属性

#### Scenario: Staff and consumers browse the same newly published catalog
- **WHEN** M_3C_DEMO 员工读取管理列表，消费者登录同店后浏览商城并加载全部分页
- **THEN** 双方均能看到这 20 个新增商品；消费者只见有效 SKU 及有货/无货信息，管理端保留既有权限与库存入口

#### Scenario: Pagination remains stable with the expanded catalog
- **WHEN** 消费者以每页 12 条、员工以每页 20 条遍历商品游标
- **THEN** 全部新增商品均可达且不重不漏，已有商品按其当前状态继续参与相应列表

#### Scenario: Test or production initializes normally
- **WHEN** test 或 prod 使用各自常规迁移 locations 初始化
- **THEN** 不自动执行此 dev 补充种子；隔离的种子验收可显式加载 devdata，不改变普通 test 配置

### Requirement: Showcase seed reruns shall preserve operator data and reject identity conflicts

补充种子 MUST 使用独立登记的稳定 ID 和业务编码，仅补缺失记录；重跑 MUST 保留人工上下架、价格、属性值、库存数量及 version。缺失父依赖、固定 ID 或自然键归属冲突 MUST 在业务写入前明确失败，不以覆盖、忽略冲突、修改迁移历史或删除数据卷完成补种。

#### Scenario: Existing products were unpublished before the supplement
- **WHEN** 开发者先下架原有商品，再加载新增补充种子
- **THEN** 原有下架状态不变，仅新增约定展示数据，原有账户、订单成交快照与物流不变

#### Scenario: A developer reruns the supplement after manual edits
- **WHEN** 人工修改新增商品状态、SKU 价格、属性值和库存后显式重跑补充种子
- **THEN** 人工值全部保留，无重复数据或伪造库存流水，缺失且身份合法的行被补齐

#### Scenario: A stable showcase identity is occupied
- **WHEN** 固定 ID 被其他记录占用，或约定商品/SKU 自然键已绑定其他 ID、商户或父资源
- **THEN** 种子明确报出相应冲突并停止，失败前没有插入任何展示业务行

### Requirement: Product cache demonstration shall prove read reuse and write invalidation

开发验收 MUST 通过真实公开商品请求展示冷读回源、热读复用及员工上下架后的商户缓存失效。证据 MUST 同时包含 Redis 商品键/TTL/版本及隔离测试中的目录查询次数，不能仅以响应变快证明命中。演示 MUST 使用既有接口与缓存，不新增展示 UI、诊断 API 或业务响应字段。

#### Scenario: A repeated public read reuses the catalog
- **WHEN** 对同店同参数公开首页或详情依次冷读和热读
- **THEN** 冷读生成有 TTL 的商品目录缓存，热读复用缓存且不增加相同目录查询；实时库存查询仍被允许

#### Scenario: Staff unpublishes a warmed showcase product
- **WHEN** 某新增商品已被公开读取并缓存，员工通过既有接口下架
- **THEN** 商户缓存版本推进，公开列表移除该商品、公开详情返回 404；员工仍能读取 UNPUBLISHED 管理数据，消费者受保护读取也不返回下架商品

#### Scenario: The first mutation follows a read with no stored version key
- **WHEN** 商户版本键缺失，公开读取已生成目录缓存，随后发生首次员工上下架写入或并发的首次商品写入
- **THEN** 写入后的有效版本不复用写入前版本且不回退，旧缓存不可达，下架内容不会继续公开可见

#### Scenario: The demonstration finishes or fails after changing publication status
- **WHEN** 演示对原本 PUBLISHED 的指定新增商品执行了临时下架
- **THEN** 演示执行者在结束或异常清理时通过既有接口恢复上架并核验；如恢复失败，明确报告商品编码和状态，不宣称演示完成

#### Scenario: Cache evidence distinguishes the existing read paths
- **WHEN** 演示员工管理、消费者登录商城与带 cursor 的后续页
- **THEN** 员工受保护读不被当作缓存命中证据；消费者商城公开读可命中缓存；后续页不宣称首页缓存命中，缓存内容不包含库存数量或可售布尔快照

#### Scenario: Redis is unavailable in isolated verification
- **WHEN** 在隔离验收环境使商品缓存不可用并读取公开商品
- **THEN** 商品查询回源且按现有契约返回，验证不停止共享 dev Redis 或清空认证/会话数据
