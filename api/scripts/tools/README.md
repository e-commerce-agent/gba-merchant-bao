# 通用工具

- [export-openapi.ps1](export-openapi.ps1)：`./api/scripts/tools/export-openapi.ps1 -BaseUrl http://127.0.0.1:8080 [-TimeoutSeconds 30]`，从指定运行中后端读取 auth-v1、agent-conversation-v1、product-v1、inventory-v1、order-v1、shipment-v1 的 Springdoc JSON/YAML；全部分组非空且路径校验成功后，更新根 `docs/api/*.openapi.yaml`，暂存与原快照备份在 `api/target/openapi-export/<批次>/`；需要 PowerShell 5.1+ 和可访问的 Springdoc，不依赖 Java、classpath 或个人 runtime。读取源后端并写本地快照，不修改数据库；仅在接口变更需更新契约时执行。

生成契约与验证入口见 [后端文档目录](../../docs/README.md)。
