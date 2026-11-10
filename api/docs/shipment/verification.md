# add-shipment-web-service 实现与验收记录

日期：2026-11-10，Asia/Shanghai。针对物流模块（REST + SOAP）与本次迁移编号顺延（物流 V6/R040、订单 V7/R050、库存 V8），未合并 main、未发布生产。

## 自动化结果

在仓库根目录实际执行（JDK 21 + Docker Linux 容器引擎），BUILD SUCCESS：

| 命令 | 完成时间 | Surefire | Failsafe | 失败 / 错误 / 跳过 | 耗时 |
| --- | --- | ---: | ---: | --- | --- |
| `mvn -f api/pom.xml clean verify` | 13:04:09 | 78 | 106 | 0 / 0 / 0 | 4 分 49 秒 |

报告：`api/target/surefire-reports/TEST-*.xml`、`api/target/failsafe-reports/TEST-*.xml`；命令输出副本 `.runtime/shipment-full-verify.log`（本地忽略）。Docker 不可用时 `DockerRequired` 明确失败，不以跳过 IT 或连接开发实例替代。

| 测试类 | 数量 | 验收内容 |
| --- | ---: | --- |
| ShipmentFlowIT | 7 | 员工同商户 200、消费者本人 200、跨租户/非本人/不存在统一 404/10004、匿名 401、非法 orderId 400、`Cache-Control: no-store`、字符串 ID 与 UTC 时间、预计送达不编造、`shipment-v1` 分组实际导出 |
| ShipmentSoapIT | 7 | CXF 端点与 WSDL 可访问、服务间凭证通过/缺失/错误、`merchantCode+orderNo` 跨商户与不存在 Fault、REST 授权路径不受影响 |
| ShipmentSchemaIT | 6 | `shipment`/`shipment_track` 约束、唯一与索引、租户列、UTC 列类型、无跨表物理外键 |
| ShipmentQueryServiceTest | 6 | 单元：轨迹排序、状态映射、脱敏与空轨迹合成、非法输入 |
| ShipmentServiceTokenInterceptorTest | 4 | 单元：SOAP Header 凭证校验与 Fault 行为 |

其余 92 项集成为认证、会话、Agent、迁移与基础设施回归，未因物流表与编号顺延而退化；`DevSeedMigrationIT` 21 项覆盖 V1–V8 + R__010/R__020/R__030/R__040/R__050 的允许组合。

## 契约快照

`ShipmentFlowIT.exportsActualSpringdocContractForShipmentV1` 在隔离后端上实际取 `/v3/api-docs/shipment-v1` 与 `/v3/api-docs.yaml/shipment-v1`，断言后把 YAML 写入 `api/target/shipment-v1.openapi.yaml`，再落到仓库快照 [docs/api/shipment-v1.openapi.yaml](../../../docs/api/shipment-v1.openapi.yaml)。快照为 Springdoc 原始输出，未手工改写。

核对结果：

- 操作：仅 `getOrderShipment`（`GET /api/v1/orders/{orderId}/shipment`），无按运单号查询入口。
- 安全：`security: bearerAuth`，`bearerAuth` 为 `http`/`bearer`/`JWT`。
- 响应 schema：`ApiResponseShipmentView` → `ShipmentView`（`orderId` 字符串、`status` 枚举 `NOT_SHIPPED`/`IN_TRANSIT`/`DELIVERED`、`tracks` → `TrackView`）；错误统一 `ShipmentErrorEnvelope`。
- 错误码沿用公共段：400/10001、401/10002、403/10003、404/10004、500/10500；每个响应都声明 `X-Request-Id` 与 `Cache-Control: no-store`。

与早前评审稿的差异，均为实现记录带来的变化：

1. schema 名改为实现类名（`Shipment` → `ShipmentView`、`Track` → `TrackView`、`ApiResponseShipment` → `ApiResponseShipmentView`）。
2. 200 响应的 content 为 `*/*`：Controller 未声明 `produces`，与 `auth-v1`、`agent-conversation-v1` 快照的现状一致；实际响应体为 JSON。如后续统一声明 `produces = application/json`，三个快照应一并重导。
3. `nullable` 与字段描述未出现在快照中：record 组件未加 `@Schema`，语义仍以本文与 [api-design.md](api-design.md) 为准。
4. `info.title` 设为 `Merchant Shipment API`，避免与 `auth-v1` 快照同名导致 Apifox 导入混淆。

## 变更校验

`npx @fission-ai/openspec@1.17.0 validate add-shipment-web-service --strict` 通过（本机 PATH 无 `openspec`，用 npx 调用）。proposal、design、specs、tasks 与 `api/docs/shipment/` 已逐项核对：编号结论统一为物流 V6/R040、订单 V7/R050、库存 V8；`docs/api/shipment-v1.openapi.yaml` 已由导出结果替换；`api/README.md` 补齐物流文档与快照导航。

## 已知限制

- SOAP 端点为课程分布式框架演示与模拟外部承运商对接，使用服务间凭证，不替代 REST 授权，不对浏览器前端开放，不作为生产对公能力。
- SOAP 与 REST 均未接入真实承运商；轨迹为库内数据。`NOT_SHIPPED` 由服务按订单归属合成，订单越权仍统一 404。
- 本模块未新增自有错误码，12xxx 段仅为将来物流数据异常预留，未被任何响应使用。
- 前端物流页仍为 Mock 联调；本模块进入 main 后按其接口替换。Apifox 的实际导入由前端负责，本记录不宣称已完成。
