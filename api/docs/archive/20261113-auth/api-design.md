# 认证模块 API 契约

状态：以下九操作已实现，隔离测试与联调见 [联调手册（本目录入口）](README.md#manual-verification)。[认证 OpenAPI 快照](../../../docs/api/auth-v1.openapi.yaml) 来自实际 Springdoc auth-v1 分组；最终验收见 [验证记录（本目录入口）](README.md#verification)，未发布生产。

## 通用约定

- JSON envelope：{code,message,data,requestId}，成功 code=0；错误 data=null。X-Request-Id 与 body 相同；认证响应 Cache-Control: no-store；ID 十进制字符串、UTC ISO-8601 时间。
- 私有请求 Authorization: Bearer <accessToken>；新旧协议不互通。refresh/logout 有独立凭证分支，不能因 access 过期提前挡住有效 refresh。
- 新 DTO 拒绝未知字段，特别是 role、merchantId、accountId、customerId。消费者 body 不接受 merchantCode 或 purpose；refresh 不接受店铺/角色字段。不改变 legacy DTO 的宽松行为。
- 大陆手机号 trim 后将 11 位或等价 +86 输入归一 E.164；用户名 trim/小写，格式 [a-z][a-z0-9_-]{2,31}；merchantCode trim 并按既有大写编码解析。storeSlug 初期按相同编码别名解析，不作为授权凭证。
- 密码不 trim，8–64 字符且 UTF-8 最多 72 字节，超限 400，避免 BCrypt 截断。code 为六位数字字符串。
- 未声明的角色/商户覆盖字段均 400；安全身份/角色检查先于 DTO：匿名开通员工 401，消费者开通员工 403，员工提交伪造身份字段 400。

登录/刷新成功 data 结构如下（示例 token 为占位，不是可用凭证）：

~~~json
{
  "accessToken": "<access-jwt>",
  "refreshToken": "<opaque-refresh>",
  "tokenType": "Bearer",
  "expiresIn": 7200,
  "expiresAt": "2026-11-05T04:00:00Z",
  "refreshExpiresAt": "2026-12-05T02:00:00Z",
  "principal": {
    "accountId": "123456789012345678",
    "merchantId": "123456789012345670",
    "merchantCode": "DEMO",
    "role": "CONSUMER",
    "displayName": "消费者",
    "phoneMasked": "138****8000"
  }
}
~~~

access 标称 7200 秒，临近 absolute 时 expiresIn/exp 截短；refreshExpiresAt 固定为最初凭证登录起消费者 30 天/员工 7 天，不随刷新推后。active idle 分别 7 天/1 天，不等于 refresh 有效期。首次自动建号与已有消费者登录均 200，不返回注册 201/Location。

## 九个操作

| # | 方法与路径 | 凭证/权限 | 成功 |
| --- | --- | --- | --- |
| 1 | POST /api/v1/stores/{storeSlug}/auth/sms-codes | 匿名，限流 | 200，expiresIn/retryAfter |
| 2 | POST /api/v1/stores/{storeSlug}/auth/consumer/login | 匿名，有效 LOGIN 验证码 | 200，双 token |
| 3 | POST /api/v1/auth/staff/login | 匿名，商户内密码 | 200，双 token |
| 4 | POST /api/v1/auth/refresh | JSON refresh，不要求 access | 200，同 sid 轮换双 token |
| 5 | POST /api/v1/staff-accounts | MERCHANT_STAFF，本商户 | 201，Location，员工摘要，无 token |
| 6 | GET /api/v1/staff-accounts/{accountId} | MERCHANT_STAFF，同商户 | 200，员工摘要 |
| 7 | GET /api/v1/consumer-accounts/{accountId} | CONSUMER，同商户本人 | 200，消费者摘要 |
| 8 | GET /api/v1/auth/me | access+active+当前有效主体 | 200，principal |
| 9 | POST /api/v1/auth/logout | 有效 access 或 JSON refresh | 200，data=null，撤销当前 sid |

## 1. 申请消费者短信

POST /api/v1/stores/DEMO/auth/sms-codes

~~~json
{ "phone": "13800138000" }
~~~

由路径定位 ACTIVE 店铺，只生成 LOGIN 挑战，不查询手机号是否已建号来决定成功响应。成功 data 为 {expiresIn:300,retryAfter:60}；只有 dev 显式开启 expose-debug-code 时附加 debugCode，test 用可控替身。六位安全随机、300 秒、五次错误、单次消费；重发使旧挑战失效，按 challengeId 激活/清理。

同商户+手机号冷却 60 秒、每小时 10 次；来源 IP 每小时 30 次；取直接 remoteAddr，不信任任意 X-Forwarded-For。429/11003 带 Retry-After，prod disabled 或发送失败 503/11502，商户无效统一 401/11001，不暴露手机号注册状态。

## 2. 消费者短信登录并按需建号

POST /api/v1/stores/DEMO/auth/consumer/login

~~~json
{ "phone": "13800138000", "code": "012345" }
~~~

校验路径商户、规范化手机号和 LOGIN 挑战，首次自动创建；已有 ACTIVE 账号复用。并发唯一冲突读取赢家账号并核验状态，不返回 409；同验证码重放仍 400/11002。停用账号不能重建，统一 401/11001。无独立 register 路径，不要求消费者输入商户编码，不加首次建号提示/协议勾选。

成功 200 返回标准双 token，只有会话完整写入才返回。数据库失败 503/11501；若账号已提交而 Redis 失败，保留账号、消费码不恢复、响应不含任一 token。IP 登录配额 30 次/15 分钟与挑战次数同时适用。

## 3. 员工密码登录

POST /api/v1/auth/staff/login

~~~json
{ "merchantCode": "DEMO", "identifier": "demo_staff", "password": "<用户输入的密码>" }
~~~

identifier 可为用户名或手机号，分别走对应租户内唯一索引，BCrypt cost=12 验证，商户/账号 ACTIVE 才返回双 token。未知商户/账号、错误密码、停用统一 401/11001。用户名/手机号共享实际员工失败计数：5 次/15 分钟锁定；未知账号标识摘要计数并 dummy BCrypt，来源 IP 30 次/15 分钟。成功清账号失败计数，不清 IP 总量；超限 429/11003。原值和摘要不进入普通响应/日志。

## 4. 统一刷新

POST /api/v1/auth/refresh

~~~json
{ "refreshToken": "<opaque-refresh>", "refreshRequestId": "5592aa03-8755-4c16-a449-b7c08beee291" }
~~~

两字段必填，refreshRequestId 为随机 UUID；同次网络重试复用，正常下一次轮换换新 UUID，它不等于每次 HTTP 的 X-Request-Id。服务端从 refresh 摘要对应可信元数据取得身份/租户/类型，不传 storeSlug，不要求 access（该端点以 refresh 独立认证，不消费可有可无的 Authorization 头）。refresh 输入按设计的 256 位 base64url 不透明值校验，普通记录仅存 SHA-256 摘要。

检查 control 未撤销、当前代/摘要、original absolute、账号/商户 ACTIVE，原子轮换与 touch idle，返回同 sid 双 token。idle 到期只有 active 丢失时可恢复；control/refresh 丢失、撤销、绝对过期、主体停用 401/10002；依赖故障 503/11501，不靠 access 重建会话。

同一旧摘要+同 refreshRequestId 在默认 10 秒内且下一代仍为当前，返回相同结果、不再次推进 idle/absolute。短期重试响应采用 AES-256-GCM 认证加密缓存，密钥由角色 key 域分离派生；随机 nonce，绑定 sid/generation/requestId/absoluteAt，TTL 不越 absolute，不存明文 token。未知随机 refresh 401；已知旧 refresh 在允许重试外重用视为重放，撤销当前 sid/family 并 401，不撤销其他设备。来源 IP 刷新配额默认 30 次/15 分钟，429 带 Retry-After；窗口内合法重试仍受配额约束，不应无条件循环。

## 5. 开通同商户员工

POST /api/v1/staff-accounts，要求 MERCHANT_STAFF。

~~~json
{ "username": "new_staff", "phone": "13800138001", "displayName": "新员工", "password": "<初始密码>" }
~~~

商户、创建者和角色由 Actor 确定，成功 201，Location: /api/v1/staff-accounts/{accountId}。data 含 accountId、merchantCode、username、displayName、phoneMasked、role、status，不含密码/hash/token，不替同事登录。匿名 401、消费者 403、同商户用户名/手机号冲突 409/10009；身份注入/格式 400/10001，依赖 503。

## 6–8. 账号资源与当前身份

GET /api/v1/staff-accounts/{accountId}：MERCHANT_STAFF 查询同租户员工，200 返回员工摘要。

GET /api/v1/consumer-accounts/{accountId}：CONSUMER 查询同租户且本人，200 data 含 accountId、merchantCode、displayName、phoneMasked、role、status。

GET /api/v1/auth/me：有效 access、匹配 active 和当前 ACTIVE 主体，200 data 返回 principal。只验证/展示身份，不 touch idle，避免后台身份探测保活。

accountId 为十进制字符串；所有资源 SQL 带 Actor.merchantId，消费者还限定 Actor.accountId。无效 access/active 缺失/主体停用 401；角色越权 403；跨租户、非本人、资源不存在统一 404/10004，不先全局查询泄露存在性；数据库/Redis 不可用 503。

## 9. 退出当前会话

POST /api/v1/auth/logout；有效未过期 access 可提交空 body 或 {}。access 缺失、无法验证或已过期时 body：

~~~json
{ "refreshToken": "<当前或该会话已知历史refresh>" }
~~~

凭证不放 URL。退出先尝试可用 access；若 access 缺失、伪造、无效或过期，可由有效且已知的 refresh 独立定位，不让失效 access 阻断退出。可用 access 与已知 refresh 同时提交须定位同一 sid，不符 400/10001。access active 已消失、账号停用仍可完成退出；refresh 只定位并撤销，不签发新 token。已知历史摘要用于退出/重复退出，不用于刷新。

原子置 control 撤销、删除 active、禁用整条 refresh family 并清除短时缓存；非明文墓碑/历史摘要保留至 original absolute，已撤销同会话重复 200/data=null。未知/伪造/全部 absolute 过期 401/10002；撤销不能确认 503/11501，不报告假成功。其他店铺、角色、设备 sid 不受影响。退出/刷新竞争以后端原子状态决定，撤销后不得恢复；已授权在途业务不保证取消。

前端退出清对应 localStorage、广播同店同类型 tabs，设置 epoch/退出标记并丢弃迟到刷新响应，不能再自动刷新该会话；若收到 503，提示服务端撤销未确认，不宣称服务端已失效。

## 路由和浏览器接入

普通店铺主页 /store/{storeSlug} 自动带公开定位；无需消费者手输 merchantCode。公开资源示例 GET /api/v1/stores/{storeSlug}/products 使用 URL，私有资源示例 GET /api/v1/orders、/orders/{orderId} 使用 token Actor，按消费者本人或员工租户范围过滤，不强制 /staff 前缀；不在本 change 实现示例。显式保护店铺 URL 如未来存在，与有效 token 租户不符为 403；具体资源越界仍 404，无有效凭证才 401。不使用 X-Store-Slug 等自定义头。

当前 dev/test、短期生产：access+refresh 均放 localStorage，键按 accountType+storeSlug 分区。同店同类型跨 tab 用同源锁/消息协调共享在途 refreshRequestId/结果，退出/新登录后迟到响应不得覆盖；A/B 店互不覆盖，切店不清 A。B 无凭证时公开页匿名，私有功能触发 B 登录，A 返回后可继续刷新使用。通用 me 若传 A token 只会返回 A，前端必须按店铺选凭证并核对 principal。

刷新页面或隔天重开可在 original absolute 内恢复，idle 超时也可用 refresh，无需重新输入短信/密码。成功完成允许续期的私有业务操作或前台必要刷新 touch idle；匿名、失败、me/后台轮询不保活。不能仅靠客户端 active 标记识别真人活动；前端禁止无条件定时 refresh。localStorage XSS 可盗取长期凭证，店铺键不是安全隔离；生产 Cookie 分离存储和 CSRF/CORS 留独立 change，不宣称已有前端实现。

## HTTP/业务码与失败例

| HTTP | code | 使用场景 |
| --- | --- | --- |
| 200/201 | 0 | 成功，只有员工创建为 201 |
| 400 | 10001 INVALID_ARGUMENT | 格式、未知字段、身份注入、退出双凭证不一致 |
| 401 | 10002 UNAUTHORIZED | access/active 无效，refresh 失效/撤销/absolute 到期 |
| 403 | 10003 FORBIDDEN | 角色越权，显式保护店铺路径与 token 租户不符 |
| 404 | 10004 RESOURCE_NOT_FOUND | 具体资源不存在/跨租户/非本人 |
| 409 | 10009 CONFLICT | 员工用户名/手机号冲突 |
| 401 | 11001 AUTH_CREDENTIALS_INVALID | 登录账号/密码/商户无效或停用，统一消息 |
| 400 | 11002 AUTH_SMS_CODE_INVALID | 验证码错误、过期、已用、范围不符 |
| 429 | 11003 AUTH_RATE_LIMITED | 发送/登录/刷新超限，Retry-After |
| 503 | 11501 AUTH_DEPENDENCY_UNAVAILABLE | MySQL/Redis 依赖无法完成；缓存篡改作为无效凭证拒绝 |
| 503 | 11502 AUTH_SMS_UNAVAILABLE | 短信关闭或发送失败 |
| 500 | 10500 INTERNAL_ERROR | 非预期错误，不泄露堆栈 |

失败例：POST 员工开通未认证 → HTTP 401；同一接口携消费者 token → 403；已认证员工 body 含 role → 400。三者均为统一 envelope，例如：

~~~json
{ "code": 10002, "message": "未登录或登录状态已失效", "data": null, "requestId": "req-example" }
~~~

过滤器也必须返回 JSON/requestId，不是 HTML。登录统一“凭证无效”，不暴露未知/停用细节；resource 404 的 message/body 不能因真实存在而改变。429 返回剩余窗口秒数；503 不带凭证或假成功。密码、hash、原始手机号、验证码、access/refresh、Authorization 和 body 禁止日志输出；dev 显式 debugCode 仅调试响应可见。

## 验收与延期

定向 HTTP/Redis/MySQL 测试覆盖首次/已有登录、唯一冲突、码重放、A/B 与本人隔离、idle 恢复、absolute 失效、短时重试/真正重放、停用、退出刷新竞争/重复退出、故障 503、独立多设备。数据仍 devdata/fixture 分层，生产首员工、平台管理、RBAC、真实短信、前端和 Cookie 迁移均延期。旧协议兼容与 echo 认证变化登记旧接口迁移清单；全量 test/clean verify 的结果见 [验证记录（本目录入口）](README.md#verification)。

## 配置与前端协调细节

dev YAML 显式 `merchant.auth.sms.provider: mock` 和 `expose-debug-code: true` 才回显 debugCode；默认 disabled 返回 503/11502。test 可替换 SmsSender，test 单独 profile 不回显；prod 禁止 mock/debug 并在启动时拒绝。生产真实短信留待独立集成。限流默认与 Redis key/TTL 见 [数据设计（本目录入口）](README.md#data-design)。

建议 localStorage 键 `merchantbao.auth:{accountType}:{storeSlug}`，保存双 token、principal、截止时刻及本地 epoch。对同一个键使用 Web Locks 或同源协调器形成单次刷新；共享同一在途 refreshRequestId，丢响应的短时重试复用 UUID，后续正常刷新用新 UUID。BroadcastChannel/storage event 同步结果与退出，不能让两个 tab 各用不同 UUID 刷同一个旧 token。锁失败不得无限后台重试。

发请求时记住键和 epoch；切店只变当前选择，响应只更新原键。新登录/退出先提升 epoch，退出广播并清凭证；迟到 refresh/login 只有 epoch 匹配且没有退出标记才可写入。A→B→A 保留 A；不同类型分别存。关闭浏览器后凭持久化 refresh 在 absolute 内按需恢复，重开不延长 absolute。服务端不能证明前端真人活动，禁止无条件定时保活或信任客户端 active 标记。这里是接入要求，仓库尚无该前端实现。
