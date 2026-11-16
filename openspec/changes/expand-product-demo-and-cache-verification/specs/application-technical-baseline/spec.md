# Spec Delta

## ADDED Requirements

### Requirement: Development proxy shall forward the shipment SOAP endpoint and its WSDL

开发 Nginx MUST 将 `/services/shipment` 的 SOAP POST 及同路径 `?wsdl` GET 原样转发到既有双后端，使 Vite 同源代理与直接访问 8081 均能到达服务。代理 MUST 保留 XML、SOAPAction 和服务凭证校验，不注入真实凭证或放宽为匿名业务查询；无关页面及相似路径 MUST 继续返回 404。

#### Scenario: Browser loads the shipment WSDL through the development chain
- **WHEN** 后端已启动，客户端经 Vite 5173 或 Nginx 8081 请求 `/services/shipment?wsdl`
- **THEN** 返回 HTTP 200 和合法 WSDL XML，查询字符串被保留，不返回 Nginx HTML 404

#### Scenario: Valid SOAP calls reach the existing service
- **WHEN** 客户端向开发入口 POST 合法 SOAP 信封及有效服务凭证，调用 ping 或本商户既有订单的 queryShipmentByOrderNo
- **THEN** 返回相应 SOAP XML；物流查询保持既有订单及商户归属检查，不受商品是否下架影响

#### Scenario: Invalid SOAP credentials remain rejected
- **WHEN** SOAP 请求缺少或携带无效服务凭证
- **THEN** 由后端返回合法 XML SOAP Fault，不返回物流数据，不用 HTML 路由 404 掩盖实际鉴权结果

#### Scenario: Unrelated development paths remain unavailable
- **WHEN** 请求 `/`、`/apiary` 或 `/servicesary` 等无关路径
- **THEN** Nginx 仍返回 404，不托管前端页面，原 REST/SSE 代理及日志脱敏规则保持有效
