# 生产发布

- [deploy-prod.ps1](deploy-prod.ps1)：`./api/scripts/prod/deploy-prod.ps1`，从任意工作目录定位仓库，先完整验证，再上传产物至 ECS；JDK 21、Maven、Docker、SSH/SCP 和已评审的生产目标必须就绪，执行前将脚本中的 `ECS_IP` 占位符替换为实际目标；会写远端 `/opt/gba/`，不会自动启动远端应用。

生产配置唯一来源及发布前提见 [部署目录](../../docs/deployment/README.md#production)。
