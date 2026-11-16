## dev Flyway V5 维护

显式运行 dev profile，复用原有 gba-dev 持久数据卷。迁移前确认当前 V4、V5 未占用，并生成本机忽略备份 `backups/20261113-backend-maintenance/runtime-preservation-20261113-0955/agent-before-v5.sql`（25755 字节）。21:38:11 完成 V5__agent_conversation.sql 与 R__030_chat_demo.sql，21:38:14 应用启动；后续为模型联网重启后依旧 V5 无待执行迁移。中断后原 dev 进程与中间件停止，本轮恢复原 Compose 容器/数据卷，23:03:38 dev 启动成功、health=UP，Flyway 显示 gba_dev 当前 V5 且无待迁移；只读核对下列校验和一致。恢复日志为 `backups/20261113-backend-maintenance/runtime-preservation-20261113-0955/agent-dev-resumed.log`。dev 服务地址 `http://127.0.0.1:8080`，不代表部署生产。

| 版本 | 实际 checksum | success |
| --- | --- | --- |
| V1 | 1595743141 | 1 |
| V2 | -313524576 | 1 |
| V3 | 621412658 | 1 |
| V4 | -422677036 | 1 |
| V5 | -2063483985 | 1 |

chat_session/chat_message 已存在；没有 repair、删除数据卷或改写已执行 SQL。V5 在此开发库已执行，后续合并物流模块若有相同版本，只能协调物流尚未执行的迁移编号；不要重命名这里已执行的 V5，不能靠 repair 消除两个不同迁移的版本冲突。


| 验证 | 实际结果 | 原始证据（本机忽略文件） |
| --- | --- | --- |
| dev V4→V5 | 通过，V1–V5 全 success=1 | `backups/20261113-backend-maintenance/runtime-preservation-20261113-0955/agent-dev-startup.log`、数据库 flyway_schema_history |
