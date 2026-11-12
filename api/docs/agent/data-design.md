# Agent 会话数据设计

本文件对应 change 的任务 2，聚焦持久化设计；接口见 [接口契约（本目录入口）](README.md#api-design)，需求见 [requirements 文档（本目录入口）](README.md#requirements)。模型调用、Service 编排与 HTTP/SSE 的实现证据见 [验收记录（本目录入口）](README.md#verification)；Redis ChatMemory 仍属后续工作。

## 迁移与数据约定

现有结构迁移为 V1–V4；新增 `V5__agent_conversation.sql`，不改写既有脚本及 checksum。商品/订单等模块汇合时只协调**尚未执行**的编号；已执行的迁移只通过后续前向迁移修正。生产回退先禁用功能，保留历史，不删除已执行迁移。

沿用 InnoDB、utf8mb4、BIGINT Snowflake ID 和 merchant 租户根。Java 持久化行使用 record，ID 由现有 IdGenerator 分配；API 层另行转换为十进制字符串。本模块时间用 UTC `DATETIME(3)`，Java 用 LocalDateTime 表达数据库 UTC 墙钟值，后续 Service 必须用 UTC 时钟创建，VO 转 ISO-8601 UTC；既有认证表的 TIMESTAMP(6) 不改动。DATETIME 不自动转换时区，不能使用服务器本地时间写入。

## chat_session

| 字段 | 类型/默认 | 约束与含义 |
| --- | --- | --- |
| id | BIGINT | 全局主键 |
| merchant_id | BIGINT | 非空，FK → merchant.id |
| owner_account_id | BIGINT | 非空，可信 Actor 的账号 |
| owner_role | VARCHAR(32) ASCII binary | CONSUMER / MERCHANT_STAFF |
| title | VARCHAR(200)，新对话 | 非空，非空白；API 后续限制输入 |
| status | VARCHAR(16) ASCII binary，ACTIVE | ACTIVE / ARCHIVED |
| created_at / updated_at | DATETIME(3) | 非空，调用方传入 UTC；updated_at 显式更新 |
| archived_at / archived_by | DATETIME(3) / BIGINT | ACTIVE 时均空，ARCHIVED 时均非空且 archived_by=owner_account_id |
| next_message_seq | BIGINT，1 | 正数，下轮 user 序号，成功分配后加 2 |
| active_run_id / run_deadline_at | BIGINT / DATETIME(3) | 同时空或同时非空；仅 ACTIVE 会话可持有运行权 |

除 PK 外，唯一键 `(merchant_id,id)` 支撑复合外键。列表索引 `(merchant_id,owner_account_id,owner_role,updated_at,id)`；会话列表按 updated_at DESC、id DESC，使用严格小于游标的 keyset 分页，时间相同以 ID 消除歧义。所有者跨消费者/员工两表，不能伪造一个多态账号 FK；后续 Service 必须核验真实账号、状态及角色，Mapper 必须始终限定 merchant/account/role。不存在、跨租户、同租户他人统一由 Service 映射为 404。

## chat_message

| 字段 | 类型 | 约束与含义 |
| --- | --- | --- |
| id / merchant_id / session_id | BIGINT | 主键及所属会话；均非空 |
| message_seq | BIGINT | 会话内正数，完整历史按该值 ASC |
| sender_type | VARCHAR(16) ASCII binary | USER / ASSISTANT |
| sender_account_id | BIGINT，可空 | USER 非空；ASSISTANT 必须空 |
| client_message_id | CHAR(36) ASCII binary，可空 | USER 必须为小写标准 UUID；ASSISTANT 必须空 |
| content | LONGTEXT | 非空，保留空白/大小写，不 trim；助手占位可为空串 |
| status | VARCHAR(16) ASCII binary | USER 仅 COMPLETED；助手 STARTED/STREAMING/COMPLETED/CANCELLED/TIMED_OUT/FAILED |
| run_id | BIGINT | 非空，一轮 user/assistant 共用 |
| error_code | VARCHAR(64) ASCII binary，可空 | 失败/取消/超时的稳定错误；成功及非终态为空 |
| prompt_version / model_service_label / model_name | VARCHAR(64)/VARCHAR(64)/VARCHAR(128)，可空 | 助手调用元信息，不保存 URL、key 或完整 Prompt |
| input_tokens / output_tokens | BIGINT，可空 | 助手可得用量，非负；未知为 NULL，USER 必须空 |
| created_at / completed_at | DATETIME(3) | 创建非空，终态完成时间非空，非终态完成时间为空；完成不早于创建 |

复合 FK `(merchant_id,session_id) → chat_session(merchant_id,id)` 阻止跨租户挂载，默认 RESTRICT，不级联删除审计历史。唯一键均为租户前缀：

- `(merchant_id,session_id,message_seq)`：稳定序号，不允许重复。
- `(merchant_id,session_id,client_message_id)`：USER 幂等；助手为 NULL，MySQL 允许多个 NULL，因此不同轮次助手合法。不能用 nullable sender_account_id 代替幂等键。
- `(merchant_id,session_id,run_id,sender_type)`：每轮最多一个 USER 和一个 ASSISTANT。

消息查询必须 JOIN 归属会话并限定商户、会话、账号及角色；分页用 `message_seq > afterSeq ORDER BY message_seq ASC`。Mapper 的 size 限制在 1–100，非法游标/size 后续由接口拒绝。content 的 DTO/配置上限由后续模块实现，LONGTEXT 不代表允许无界输入。数据库检查合法枚举、USER/ASSISTANT 字段及终态一致性，状态迁移单向性由条件 UPDATE 保证；原始正文按 Java 精确比较以判定同 key 是否同内容。

## 序号、幂等与共享运行权

后续 Service 在短事务内按本人锁定会话（FOR UPDATE），**先查 clientMessageId 再判断 archived/busy**，同 key 同正文返回原标识，不同正文返回冲突。取得空闲会话的 active_run_id/deadline，同时将 next_message_seq 加 2，并用原 next_message_seq 插入 user 和助手占位。所有写操作同事务提交，之后才调用模型；Mapper 不自行发起模型调用或持有长事务。

持久化接口提供归属锁定、空闲条件获取、带原 runId 的释放、归档及助手条件终结。终结必须仍持有该 run 且为非终态：成功还须 deadline 未到；TIMED_OUT 可以处理已过期 run。终态更新与释放必须由后续 Service 在同一事务完成，更新行数不是 1 时不得报告成功。迟到回调不能覆盖终态或释放后继 run。跨实例均以 MySQL 行锁/条件为准，不用内存锁或粘性会话。过期恢复的触发、事务编排和重试属于任务 4。

## 测试与开发数据

自动测试只用隔离的 MySQL 8.0.34 Testcontainer（不复用，不挂 dev 卷）；从空库和 V4 升级分别验证。Mapper 测试以稳定 ID、tenant/session/run/clientMessageId 断言数据及权限，禁止用共享整表总数证明业务结果。

`src/test/resources/db/fixture/chat_conversations.sql` 仅由 @Sql 显式加载，先加载 auth_accounts.sql；包含两租户、同租户不同消费者及员工的会话和消息，测试事务回滚。开发数据单独使用 `db/devdata/R__030_chat_demo.sql`，依赖 R010 租户和 R020 开发账号；不得复用 fixture 账号或密码。重复执行仅补缺，保留人工标题、状态、正文及运行信息；稳定 ID/归属/序号/幂等键冲突应明确失败，不用 INSERT IGNORE 或覆盖更新。test 只加载结构/common/nonprod，prod 应用禁用自动迁移、部署迁移只加载结构/common，均不加载 devdata。

| 数据用途 | 会话 ID | 消息 ID | 账号来源 |
| --- | --- | --- | --- |
| @Sql fixture | 2200000000000101–104 | 2300000000000101–108 | auth fixture 的 200…账号 |
| dev R030 | 2200000000000001–4 | 2300000000000001–8 | R020 的 210…账号 |

每组都有同商户两个消费者、一个员工和另一商户消费者；每个会话各一轮静态问答，next_message_seq=3。所有开发问答标明为演示，不冒充模型联调。R030 的依赖/身份检查在业务写入前完成，保留临时校验列名作为错误定位；MySQL 临时表不能在同一语句中重复打开，依赖和身份校验分成三条 INSERT。显式补种也要求停写、备份及核对身份，不对业务并发补种作承诺。

持久化阶段结果见 [本目录 README](README.md#persistence-verification)。
