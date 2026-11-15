# dev Nginx 双实例验收

第 4 节新增的基础对话浏览器联调见 [basic-chat-browser.md](basic-chat-browser.md)：修复浏览器 POST 的 CORS 403，验收基础对话六操作；RAG/Function Calling 前端不在该轮范围。

日期：2026-11-15（Asia/Shanghai）。对应 OpenSpec `add-nginx-load-balancing` 第 3 节；用于记录新增隔离代理测试及 Docker Desktop 固定端口故障恢复结果。

## 配置与边界

- Windows、JDK 21、Maven 3.9.11、Spring Boot 3.5.16、Testcontainers 1.20.6；Docker Engine 29.8.0 / API 1.56。
- Nginx `1.28.0-alpine`、MySQL `8.0.34`、Redis `7.4-alpine`、Milvus `v2.5.9` standalone。
- dev 入口 `127.0.0.1:8081`，upstream 为 `host.docker.internal:8080/8082`；Vite 保持 5173。
- 隔离测试只使用可销毁容器、随机端口、test profile 和公开测试 JWT。未连接 dev 数据库、Redis 或 Milvus，未挂载现有业务卷；日志及生成配置均在 `api/target/`。
- 本节通过 IDEA 原有 `MerchantApplication-8082` Run Configuration 恢复第二实例，没有新做 IDEA Debug 断点验收。第 2 节的调试任务与本节的 HTTP/故障恢复验证分开看待。
- 真实聊天模型成功流为可选项，本节未执行。知识上传使用实际已配置的 embedding 服务及共享 Milvus；这不代表验证过真实聊天生成质量。

## 隔离代理与 SSE

新增 `NginxLoadBalancingIT`，没有替换应用 Bean。两个真实 Spring Boot Servlet 应用共享独立 MySQL/Redis，使用 worker ID 1000/1001。真实 Nginx 直接加载仓库 `docker/nginx/dev.conf`，仅替换 upstream 为 Testcontainers 宿主机转发地址及随机端口。

模型 HTTP 协议替身先返回 `lookup_order`，应用真实查询隔离 fixture 订单，再进入最终回答流。现有工具选择阶段会缓冲候选文字，因此渐进流断言放在最终回答阶段，没有改变 Function Calling 实现。

| 场景 | 断言与结果 |
| --- | --- |
| 批量只读请求 | 12 次认证请求命中两个 upstream；同一登录凭证也能直接访问两个节点 |
| 成功流 | start、3 个相隔约 2 秒的 token、done；全量运行实测 token 在 138/2123/4126 ms，done 在 6157 ms；两节点历史一致且为 COMPLETED |
| 幂等 | 同键重试返回原 run 的 JSON，另一节点也可重试；同键不同正文返回 409 |
| 模型错误 | start/error，没有 done；历史 FAILED，同键 JSON 查询保持原 run |
| 客户端断开 | 已收到部分 token 后关闭连接；历史 CANCELLED，幂等重试不重新生成 |
| 后端退出 | 已建立 SSE 的所属应用上下文退出；存活节点可查询 CANCELLED 并返回原 run 的 JSON，流没有跨节点续接 |
| 防重放与日志 | 正常 run 的工具选择与最终生成各 1 次；错误请求 1 次；各原始 POST 的 Nginx 日志只有一个 upstream；日志不含测试查询值、登录密码或 bearer |

定向命令：

```powershell
mvn -f api/pom.xml test-compile '-Dit.test=NginxLoadBalancingIT' failsafe:integration-test failsafe:verify
```

定向结果：4 tests，0 failures/errors/skipped，BUILD SUCCESS。首轮曾因替身未适配现有工具选择流程和错误的查询参数而失败，修正测试后通过；没有为使测试通过而修改应用协议。

`results.json` 的正常场景 `modelCalls` 统计最终生成请求；测试还分别断言工具选择请求恰好一次。错误场景的 `modelCalls` 统计被拒绝的模型请求。

该用例验证约 6 秒的实际渐进流，没有等待 300 秒空闲超时。300 秒配置约束相邻代理读写间隔，仍需和应用 deadline 一起理解，不能由此推导无限时长或 SSE 故障无缝续传。

## Docker Desktop 固定端口故障恢复

Nginx 容器中 `getent hosts host.docker.internal` 解析到 `192.168.65.254`，容器内分别访问宿主机 8080、8082 健康接口成功。

| 阶段 | 请求数 | HTTP 结果 | upstream 证据 |
| --- | ---: | --- | --- |
| 双节点运行 | 16 | 全部 200 | 同时出现 8080 和 8082 |
| 停止 8082 | 16 | 全部 200 | 存活 8080 处理；有一条 `8082, 8080` 的只读失败重试记录 |
| IDEA 恢复 8082 | 24 | 全部 200 | 再次出现 8080 和 8082 |

停止前核对 8082 的监听 PID 属于 MerchantApplication Java 进程，仅停止该后端。恢复使用 IDEA 原配置，待健康后复验。未停止或重建 Nginx/MySQL/Redis/Milvus；恢复后 Vite 的 `/api/v1/health` 也返回 200。

## 共享 Milvus 与上传

本地 dev 配置启用 RAG，两个后端读取同一 dev YAML，连接 `127.0.0.1:19530`、1536 维 collection `knowledge_qwen37_1536`，embedding model 为 `qwen3.7-text-embedding`。实际容器命令为 `milvus run standalone`，健康检查返回 OK。

使用真实员工登录，经 8081 上传独立合成文本，返回 201 / READY / 1 chunk；随后分别从 8080、8082 的受保护知识列表查到同一 document。上传 ID 为 `115363373524189184`，标题为“Nginx 20261115 代理上传验收（合成资料）”。该合成验收资料保留在开发环境，没有删除已有资料或更改业务规则；本次登录最后通过 logout 撤销。

另发送恰好 1 MiB 文件，multipart 总体积 1,048,831 bytes。请求经 Nginx 到达 8082，由应用的 50,000 codepoint 限制返回 JSON 413（code 10001，带相同 requestId），Nginx 日志 upstream_status=413。它证明代理允许 1 MiB 加 multipart 开销到达应用，不表示该长文本符合应用字符数限制。

## 全量结果与证据

`mvn -f api/pom.xml clean verify`：BUILD SUCCESS，2026-11-15 16:33:51 +08:00 结束，用时 24:44。

| 报告 | suites | tests | failures | errors | skipped |
| --- | ---: | ---: | ---: | ---: | ---: |
| Surefire | 37 | 183 | 0 | 0 | 0 |
| Failsafe | 34 | 214 | 0 | 0 | 0 |
| 合计 | 71 | 397 | 0 | 0 | 0 |

新增 Nginx 用例在全量构建中再次 4/4 通过。全量执行重新生成的 OpenAPI 与原快照语义一致，仅对象字段顺序不同；生成文件保留到 target，仓库快照恢复原内容。`openspec validate add-nginx-load-balancing --strict` 通过。

本地生成证据（被 Git 忽略）：

- `api/target/nginx-load-balancing/results.json`、`access.log`、`nginx.conf` 及 `node-0/`、`node-1/`：隔离用例、事件相对时间和日志。
- `api/target/nginx-load-balancing/clean-verify.log`、`api/target/surefire-reports/`、`api/target/failsafe-reports/`：全量构建与测试报告。
- `api/target/nginx-dev/`：DNS、容器回连、故障前后请求、Nginx upstream、Milvus 健康、上传结果和中间件 mount 对比。

dev MySQL/Redis/Milvus 的容器 ID 与 Mounts 在故障前后完全一致，未删卷、恢复备份或重新初始化。真实登录/退出及合成知识上传会按原功能写入其自身状态，不声称业务卷逐字节未变化。生产 Compose、prod profile、生产脚本/手册和数据库迁移均未修改。
