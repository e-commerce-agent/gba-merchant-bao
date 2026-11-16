# 通用工具

- [export-openapi.ps1](export-openapi.ps1)：`./api/scripts/tools/export-openapi.ps1 -BaseUrl http://127.0.0.1:8080 [-TimeoutSeconds 30]`，从指定运行中后端读取 auth-v1、agent-conversation-v1、knowledge-v1、product-v1、inventory-v1、order-v1、shipment-v1 的 Springdoc JSON/YAML；全部分组非空且路径校验成功后，更新根 `docs/api/*.openapi.json` 和 `*.openapi.yaml`，暂存与原快照备份在 `api/target/openapi-export/<批次>/`；需要 PowerShell 5.1+ 和可访问的 Springdoc，不依赖 Java、classpath 或个人 runtime。读取源后端并写本地快照，不修改数据库；仅在接口变更需更新契约时执行。

- [verify-rag-live.ps1](verify-rag-live.ps1)：PowerShell 7下从仓库根目录运行 `pwsh -File api/scripts/tools/verify-rag-live.ps1 -BaseUrl http://127.0.0.1:8080`。需要真实配置的 RAG/模型服务和同商户的 `RAG_DEMO_STAFF_TOKEN`、`RAG_DEMO_CHAT_TOKEN` 环境变量。会上传三份3C资料并创建五个本人会话，检查SSE来源、历史和幂等，结果写入 `api/target/rag-live/<UTC时间>/results.json`；重复运行会新增资料，不自动删除。

生成契约与验证入口见 [后端文档目录](../../docs/README.md)。

- [organize-runtime.ps1](organize-runtime.ps1)：默认预览 `./api/scripts/tools/organize-runtime.ps1`，确认清单后显式加 `-Execute` 执行。它只把可识别的历史日志、一次性源码/class、SQL 和 `legacy-backup` 从 `api/.runtime` 复制、SHA-256 核验后移入忽略的 `backups/YYYYmmdd-backend-maintenance/<批次>/`；活动日志、状态、兼容 env、未知或占用文件保留并报告。可用 `-RuntimePath`、`-BackupRoot`、`-BatchName` 做隔离验证；不递归清空 runtime、不覆盖已有备份。
