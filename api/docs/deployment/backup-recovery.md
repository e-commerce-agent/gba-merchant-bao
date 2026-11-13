# 备份、恢复与迁移失败处理

MySQL 每份 SQL 同时包含结构＋数据，不另出纯数据备份。名称中的 data 不代表只有数据。
InnoDB 使用 --single-transaction，包含 routines/events/triggers，导出期间禁止并发 DDL。涉及旧库接管或恢复风险的迁移前停止写入。
生产至少每日一次，结构变更前额外一次；检查退出码、非空文件、代表性结构/记录、异机副本和恢复结果。

## dev 导出（仓库根 PowerShell）

```powershell
./api/scripts/dev/backup-dev.ps1
```

脚本读取已运行 dev MySQL 容器中的配置，通过 mysqldump --result-file 在容器 /tmp 生成原始 SQL，再 docker cp 到根 backups/。
不经 PowerShell 文本重定向，避免编码变化；不打印密码。文件命名 gba_dev-data-YYYYmmdd-HHMMSS-fff.sql，同日不覆盖，整个 backups/ 忽略 Git。
脚本只操作根 Compose 的 dev 服务。备份前确认目标项目 gba-dev、库 gba_dev；旧库若在其他实例，应按实际连接参数另行导出，不能把这份 dev 空数据备份当作旧库备份。

## ECS 导出与笔记本异机保存

在 ECS /opt/gba 运行，先停止 app 写入（首次空库可略过停应用），禁止并发 DDL：

```sh
set -eu
cd /opt/gba
mkdir -p /opt/gba/backups/mysql
stamp=$(date +%Y%m%d-%H%M%S)-$$
file="gba-data-$stamp.sql"
docker compose exec -T mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysqldump -uroot --single-transaction --routines --events --triggers --no-tablespaces --set-gtid-purged=OFF gba --result-file="$1"' sh "/tmp/$file"
docker compose cp "mysql:/tmp/$file" "/opt/gba/backups/mysql/$file"
test -s "/opt/gba/backups/mysql/$file"
docker compose exec -T mysql rm -f "/tmp/$file"
sha256sum "/opt/gba/backups/mysql/$file"
```

在笔记本的仓库根 PowerShell 下载（文件名替换为实际导出名）：

```powershell
New-Item backups/production -ItemType Directory -Force | Out-Null
scp root@ECS_IP:/opt/gba/backups/mysql/gba-data-YYYYmmdd-HHMMSS-PID.sql backups/production/
if ($LASTEXITCODE -ne 0) { throw '异机下载失败' }
Get-FileHash backups/production/gba-data-YYYYmmdd-HHMMSS-PID.sql -Algorithm SHA256
```

比较两端校验和并定期恢复演练。ECS 本机备份和命名卷不构成异机备份；目前异机只保存至自己的笔记本。

## 恢复演练

永远先恢复到独立临时 MySQL（可销毁容器）和新库，不覆盖 dev/prod。SQL 不带 --databases，因此可恢复到指定新库。
下面示例只在自行创建的 rehearsal-mysql 容器上执行，容器管理员密码通过其环境提供：

```powershell
docker cp backups/gba_dev-data-实际日期.sql rehearsal-mysql:/tmp/restore.sql
if ($LASTEXITCODE -ne 0) { throw '上传失败' }
docker exec rehearsal-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -e "CREATE DATABASE gba_restore"'
if ($LASTEXITCODE -ne 0) { throw '创建隔离恢复库失败' }
docker exec rehearsal-mysql sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot gba_restore < /tmp/restore.sql'
if ($LASTEXITCODE -ne 0) { throw '恢复失败' }
```

核对 SHOW CREATE TABLE、所有业务表行数、代表性稳定业务标识和 Flyway 历史；针对历史库的管理核对允许对比全表行数，这不等同业务用例依赖空表。
ProductionInfrastructureIT 已用隔离 MySQL 验证结构、代表性行及 version 恢复；它不证明任意生产备份都可恢复。

## 失败与回退

1. validate/migrate 失败立即停止后续发布和新版 app 启动，保存日志与失败 history，不自动 clean、不删卷、不用 repair 掩盖差异。
2. MySQL DDL 可能已部分提交，不能假定 Flyway 失败就整批回滚。停写后确认当前结构与已执行语句，保留证据。
3. 首选评审后向前修复。需要数据库回退时，将迁移前完整 SQL 恢复到隔离库，核对结构、数据、历史并验证兼容的旧应用，再受控切换。
4. 迁移后已有新写入时先评估丢失范围、补偿和对账；禁止直接覆盖。旧镜像回退只适用于当前数据库向后兼容，不等于数据库自动降级。
5. 不承诺 Flyway undo 或自动反向 SQL。history 的任何人工修复都需另行调查和评审，不能作为常规恢复捷径。

唯一已规划的开发种子改名维护见 [dev-seed-upgrade（本目录入口）](README.md#dev-seed-upgrade)：停同库应用、完整备份及隔离恢复逐行核验后，才显式 repair 已知旧 R 并校验仅追加预期 Deleted 标记，再 migrate 新 R、validate。保留旧执行记录，不删业务数据；该工具不是失败恢复捷径，也不接入应用/Compose 自动启动或生产。

## Redis

认证会话不能仅靠 JWT 恢复。只有 `session:{sid}` active idle 数据丢失时，仍有效且未撤销的 control/当前 refresh 可经 refresh 原子恢复；control 或当前 refresh 索引丢失，必须重新短信/密码登录。Redis 或 MySQL 无法确认时 API 返回 503，不降级放行。

历史 RDB/AOF 可能把已撤销/已轮换会话恢复成有效状态。恢复后先隔离验证，不直接接流量；评审后为所有 API 实例同时切换新的 `merchant.auth.namespace`（例如 prod-recovery-日期），强制旧会话重登，或受控轮换两类认证 key 并一致切换。不能让部分实例继续访问旧前缀。保留原备份供调查，不能用 flush 开发/生产 Redis 代替演练；namespace 变更也会重置限流窗口，需纳入切换计划。

AuthFlowIT 在隔离容器验证 active 缺失可 refresh、control/refresh 缺失拒绝、撤销后不可恢复和故障 503；未恢复任何真实卷。历史快照的受控失效操作属于实际恢复流程，不能把测试中删除专用键当成生产恢复授权。

dev/prod 开启 AOF、数据在命名卷。若仅缓存或可重新登录的会话可重建，则不预建备份作业。
后续模块若存不可丢状态，明确一致性策略：RDB 可触发 BGSAVE，确认 LASTSAVE 更新及后台保存结束后保存已完成的快照；
Redis 7 多段 AOF 应按其原生备份流程在受控窗口暂停自动重写并确认无重写，再复制完整 appendonlydir（含 manifest），完成后恢复配置，或停服后一致性复制。
不得只在线复制一个 AOF 文件。恢复到隔离实例，核对代表性键、TTL 和数据类型后再切换。
生产存 /opt/gba/backups/redis/ 并下载笔记本。其他中间件首次引入时再定义 /opt/gba/backups/<service>/，本次不预建作业。
