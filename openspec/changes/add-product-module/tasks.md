# Tasks

本 change 交付商品模块（分类、商品/SPU、SKU、规格/兼容性属性、商户维护、公开商品查询）。按开发顺序分组；实现项按红→绿→重构推进，并同步交付测试与文档。

## 1. Requirements and contract

- [x] 1.1 在 `openspec/changes/add-product-module/specs/product-module/spec.md` 冻结公开/登录/维护三组接口的需求与场景（匿名可见性、租户隔离、角色门禁、状态流转）。
- [x] 1.2 在 design.md 冻结四表字段/索引/状态枚举、游标规则、错误映射与 `:publish` 改子路径的措辞决定，并同步 PRD 6.3 的表述差异说明。

## 2. Flyway schema and dev data

- [x] 2.1 新增 `V8__product_module.sql`：四张表、复合外键（含自引用与跨表 `(merchant_id,id)` 唯一键）、CHECK 约束、租户优先索引；不改 V1–V5。
- [x] 2.2 新增 `db/devdata/R__050_product_demo.sql`：M_3C_DEMO 充电器/数据线/耳机及多 SKU（含 UNPUBLISHED 商品与 DISCONTINUED SKU）、M_ISOLATION_TEST 最小对照商品；幂等补缺、固定业务编码、缺租户时校验失败；devdata README 登记 050 编号。

## 3. Product package

- [x] 3.1 entity/mapper/XML：四表 Row record 与 MyBatis SQL，查询/写入全部带 `merchant_id` 范围；公开查询只命中 PUBLISHED/ACTIVE。
- [x] 3.2 dto/vo：请求 DTO 拒绝未知字段并校验编码/长度/价格/状态；VO 使用 `ApiId` 与 UTC 时间，公开视图不含内部管理字段。
- [x] 3.3 service：`ProductQueryService`（公开/登录/Agent 可见范围查询与游标分页）与 `ProductCommandService`（分类/商品/上下架/属性/SKU 维护，含二级深度与状态流转校验、编码冲突 409）。
- [x] 3.4 controller/exception/config：公开与员工两个 Controller、`ProductError` 与 scoped `@RestControllerAdvice`、Springdoc 分组登记与 `application.yml` 扫描。
- [x] 3.5 `MyBatisConfiguration` 登记 `product.mapper`；`AuthSecurityFilter` 开放 GET `/api/v1/public/**` 匿名白名单，商品域非 GET 加 MERCHANT_STAFF 角色门禁。

## 4. Tests

- [x] 4.1 `ProductRequestsTest`：DTO 校验单元测试（无容器依赖）。
- [x] 4.2 `ProductPersistenceIT`：隔离 MySQL Testcontainer 覆盖四表租户隔离、发布可见性、编码唯一、属性替换、游标分页；`@Sql` fixture 双租户场景。
- [x] 4.3 fixture `db/fixture/product_catalog.sql` 与 devdata 分离，仅在 `@Sql` 测试中加载。

## 5. Docs and delivery

- [x] 5.1 `api/docs/product/module.md`：数据设计、路由契约、授权规则、错误码、测试与联调说明。
- [x] 5.2 更新 `api/docs/architecture/packages.md` 包结构与 `api/README.md` 导航；导出 OpenAPI 供前端与 Apifox 使用。
