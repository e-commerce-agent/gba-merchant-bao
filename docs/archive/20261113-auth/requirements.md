# 认证模块业务需求

状态：add-redis-auth-sessions 的账号、短信、会话与授权后端已实现；种子升级见部署手册，最终验收见 [验证记录（本目录入口）](README.md#verification)。后续模块仅交付路线，未发布生产，也未实现前端。

## 主体、账号和店铺

| 主体 | 账号建立 | 登录 | 边界 |
| --- | --- | --- | --- |
| CONSUMER | 当前店铺首次有效短信登录自动建号 | 手机号 + 短信验证码 | 同手机号在不同商户是不同账号，仅本人和当前租户数据 |
| MERCHANT_STAFF | dev 演示首员工 / test 独立 fixture；后续由本商户已登录员工开通 | 商户编码 + 用户名/手机号 + BCrypt 密码 | 商户内账号；管理员与客服首期合并，共享员工开通能力 |

首员工虽可业务称管理员，系统角色仍 MERCHANT_STAFF；不暗加 isAdmin 或提前 RBAC。不开放匿名员工注册。生产租户/首管理员开通、商户入驻和平台后台延期，不提供 CLI、公开 bootstrap 或生产认证种子。

消费者由普通店铺首页 /store/{storeSlug} 进入，页面自动携带公开上下文；初期 slug 可复用 merchantCode，只定位商户，不授权。不要求消费者输入商户编码、扫描专属二维码或先经过店铺列表；没有上下文则先进入/选择店铺，不接受无店铺手机号登录。公开 slug 后续演进与内部 merchantId 分开。

## 消费者登录闭环

1. 在有效店铺路径申请 LOGIN 六位验证码，提交手机号和验证码完成一个流程；不提供独立注册接口或 REGISTER 用途。
2. 有效验证后查找当前租户规范化手机号：不存在自动创建，存在复用，已停用不得重建绕过。无需首次建号提示、协议链接或同意勾选门槛。
3. 数据库以 (merchant_id, phone) 唯一约束处理竞争；独立有效挑战的并发插入冲突复用赢家账号并核验状态，不返回重复注册 409。同一挑战最多一次消费，重放无效。
4. 先提交账号事务，再建立 Redis 会话。账号成功但会话失败返回 503、无 access/refresh，保留账号且不恢复验证码；下次重新取码登录。

验证码绑定 LOGIN、商户、规范化手机号，300 秒、五次错误即失效、成功一次消费；重发使旧挑战失效，challengeId 避免发送失败误删新代。发送冷却/配额、IP 和密码失败由 Redis 原子限流。dev/test 可 mock；dev 仅显式开启才回显 debugCode，prod 禁止 mock/debug，真实 SMS 未接入时返回 503 不伪报成功。

## 员工闭环

员工两种标识定位同一租户内账号，其他商户同名不混用密码。用户名/手机号租户内分别唯一；密码 BCrypt cost=12，不保存原值。未知商户/账号、错密码、账号/商户停用统一 401/11001，避免枚举。

已登录员工提交用户名、手机号、显示名和初始密码开通同事，商户/创建者/固定角色取服务端 Actor。返回 201、Location 和脱敏摘要，不返回密码/hash/token；同事再单独登录。手机号是内部维护标识，本期不声称已验证持有权；密码通过模块外安全渠道交付。不包含找回密码、员工状态管理或分权管理 API。

## 会话生命周期

| 规则 | 消费者 | 员工 |
| --- | --- | --- |
| access JWT 标称寿命 | 2 小时 | 2 小时 |
| 活跃 idle | 7 天 | 1 天 |
| original absolute | 凭证登录起 30 天 | 凭证登录起 7 天 |
| idle 超时 | 活跃访问失效，有效 refresh 可无感恢复 | 同左 |
| absolute 超时 | 重新短信验证 | 重新密码登录 |

access exp 不超过 absolute；activeExpiresAt=min(lastQualifyingActivity+idle,absoluteAt)。refresh/control 不随 idle 消失；旋转和恢复都不延长原始 absolute。

成功完成允许续期的私有业务请求或前台所需 token 刷新可 touch idle；公开匿名浏览、失败请求、me 探测、后台定时刷新/轮询不能持续续命。后端不能可靠判断真人操作，前端必须避免无条件保活，不把 active=true 当安全证据。幂等刷新重试不重复 touch。

受保护请求需要有效 HS256 access、Redis 活跃记录及当前 ACTIVE 账号/商户；多次凭证登录独立 sid，刷新沿用 sid。仅 active 丢失可由有效 refresh 恢复，control/refresh 丢失或撤销必须重登；Redis/MySQL 故障 503，不能凭 JWT 重建。

## 刷新、退出与浏览器

统一 POST /api/v1/auth/refresh 从 body 的不透明 refresh 凭证对应 Redis 元数据确定租户/角色，不要求 access 或 URL 店铺。高熵随机 refresh 轮换，普通记录只存摘要。前端同店同角色跨 tab 协调；10 秒内相同旧摘要、相同 refreshRequestId、仍是当前代可返回同次结果，使用认证加密的短期响应缓存，不存明文 token。真正已知旧值重放撤销当前 sid/family；未知随机值只拒绝，不能撤销其他可猜身份。

统一 POST /api/v1/auth/logout 可用有效未过期 access；access 缺失、无法验证或已过期时用 body.refreshToken 定位（不得放 URL），不为退出发新 token。已知历史摘要可撤销/重复退出但不可刷新；可用 access 与已知 refresh 同时提供须同 sid，否则 400；无效/过期 access 不阻止有效 refresh 独立定位。原子撤销 active 与全部 refresh 能力、清除重试缓存，保留非明文墓碑/摘要至原 absolute。已撤销同会话重复退出 200，未知/伪造/全部绝对过期 401，撤销无法确认 503，账号停用仍可退出。

退出/刷新同 sid 原子串行，撤销后不得重建。前端清除对应凭证、同步同店 tabs、禁止自动刷新，丢弃迟到刷新结果；其他店铺、类型、设备不受影响。已授权在途业务不承诺取消。

当前 dev/test 和短期生产均将 access+refresh 放 localStorage，按店铺+账号类型存，切店不清其他键，刷新/关浏览器后在 absolute 内按需恢复。键分区不是安全隔离，XSS 可窃取长期凭证。生产目标另开 change：access 内存 + refresh HttpOnly Secure Cookie，并设计 CSRF/CORS。当前只交付前端接入契约，不实现前端。

A 已登录打开 B：公开页面匿名浏览，私有功能只使用 B 凭证，无 B 会话则登录 B；A 保留，回 A 不因切店丢登录。通用私有接口若误收 A token，只知道 A 身份，无法推断浏览器 B 页面；前端必须按店铺选凭证并核对 principal。

## 三层授权和错误

公开 URL 定位店铺，通用私有资源从可信 Actor 取商户。先租户、再角色、再资源归属；SQL 必须带 merchantId 和必要本人条件，不先全局查详情。未来同类资源可共用 /api/v1/orders，由角色限定查询，不要求 /staff 前缀；当前不实现订单或 Tool。

| 条件 | 结果 |
| --- | --- |
| 格式/未知字段/身份注入/双凭证会话不符 | 400/10001 |
| access 无效/active 缺失；refresh 无效/撤销/absolute 到期 | 401/10002 |
| 登录账号/商户/凭证无效或停用 | 401/11001 统一消息 |
| 有效主体角色越权；显式保护店铺 URL 与身份不符 | 403/10003 |
| 具体资源不存在、跨租户、非本人 | 相同 404/10004 |
| 员工用户名/手机号冲突 | 409/10009 |
| 验证码无效、过期、已用或范围不符 | 400/11002 |
| 发送/登录/刷新超限 | 429/11003，Retry-After |
| MySQL/Redis 故障；短信不可用 | 503/11501；503/11502 |

普通 JSON 为 code/message/data/requestId，错误 data=null；X-Request-Id 与 body 一致，认证响应 no-store。普通账号摘要仅 phoneMasked，不含密码/hash；日志不含原始手机号、短信、access/refresh、Authorization 或请求/响应 body。dev debugCode 是显式受限例外。

## 数据与交付边界

租户根留 db/seed/nonprod；演示业务账号只 db/devdata，提供两个租户员工、样板双消费者与对照同手机号消费者，重复不覆盖凭证/状态。测试独立 src/test/resources/db/fixture，由 @Sql 显式加载，密码不依赖演示数据，覆盖唯一性、跨租户和资源归属；prod 不加载两者。V4 仅追加账号表，V1–V3 不改。

与 user-auth delta spec 对应：自动建号、店铺定位、员工开通/密码、短信/mock、idle/absolute、轮换重试/重放、退出、授权、故障/隐私、密钥与数据分层已有实现与定向验证。浏览器仅交付接入契约；真实短信、生产首员工、Cookie 迁移与后续 Agent 业务不在本期实现。第 9 节完整验收的实际结果见 [验证记录（本目录入口）](README.md#verification)。
