# 开发环境维护

- [docker-dev.ps1](docker-dev.ps1)：`./api/scripts/dev/docker-dev.ps1`，校验根 Compose 并启动/查看 `gba-dev` 中间件；需要 Docker Desktop Linux 引擎和根 `.env`，会使用现有开发卷。
- [backup-dev.ps1](backup-dev.ps1)：`./api/scripts/dev/backup-dev.ps1 [-DockerExecutable <docker.exe>]`，从运行中的根 Compose MySQL 导出结构与数据至根 `backups/`；需要开发容器、导出权限和可写备份目录，目标为 `gba-dev` / `gba_dev`。
- [dev-seed-upgrade.ps1](dev-seed-upgrade.ps1)：仓库根运行 `./api/scripts/dev/dev-seed-upgrade.ps1 -ReportDirectory (Join-Path (Get-Location) 'backups/dev-seed-实际日期时间')`，默认 Check，完整备份并在临时 MySQL 恢复核验；需 Docker、JDK 21、Maven、现有 `gba-dev` 卷和人工停写，Execute 另需 `-Mode Execute -MaintenanceConfirmed`，仅适用说明中列明的旧种子基线。

操作边界见 [部署目录](../../docs/deployment/README.md#dev-seed-upgrade)，备份恢复见 [部署目录](../../docs/deployment/README.md#backup-recovery)。旧文档备份入口只为不可改的生产手稿保留转发。
