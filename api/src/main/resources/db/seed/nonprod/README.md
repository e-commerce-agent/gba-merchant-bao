# 非生产租户根

`R__010_local_test_tenants.sql` 供 dev/test 使用，只补缺失的两个稳定租户，保留人工名称、状态、时区、币种等修改。固定 ID 与 merchant_code 关系冲突明确失败；不得通过覆盖式 upsert 重新激活停用租户。

统一编号登记在 `db/devdata/README.md`：010 租户先于仅 dev 的 020 账号。所有待执行 V 先于 R，locations 顺序不决定执行顺序。普通启动不额外补种，不承诺修复人工删除的数据。

已执行旧名 R 的保留开发库先按 [运行与部署文档入口](../../../../../../docs/deployment/README.md) 受控升级，保留真实 history，不保留运行期旧名脚本、不自动 repair。生产不加载此位置，认证账号不放本目录；测试账号由独立 @Sql fixture 提供。
