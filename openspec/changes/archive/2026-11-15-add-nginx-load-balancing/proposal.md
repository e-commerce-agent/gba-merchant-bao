# Proposal

## Why

当前 dev 环境由 Docker Desktop 提供中间件、IDEA/Maven 启动后端，但缺少可实际验证的 Nginx 反向代理与多实例负载均衡链路。先在本机运行两个 dev 后端，验证普通 API 和 Agent SSE 都能经过 Nginx，同时保留 IDEA Services 的断点调试流程。

## What Changes

- 根 `docker-compose.yml` 默认启动独立 Nginx 容器，与既有 MySQL、Redis 一起由 `docker compose up -d` 管理；Nginx 发布 `127.0.0.1:8081:80`。
- 在 IDEA Services 使用两个 Run/Debug Configuration 启动同一应用，均激活 `dev`，分别监听 `8080`、`8082` 并使用 worker ID `0`、`1`。
- Nginx 将 `/api` 原路径轮询转发到 `host.docker.internal:8080` 和 `host.docker.internal:8082`，支持先启动代理、后启动宿主机后端。
- 前端继续通过 Vite `npm run dev` 提供页面，Vite 的 `/api` 代理目标改为 `http://localhost:8081`；Nginx 不托管前端静态文件。
- 验证跨实例认证、流量分发、单实例停止后的只读访问及 SSE `start/token/done/error`，使用本地模型协议替身稳定验收，真实模型成功流作为可选补充。
- 两个后端共享现有 dev MySQL、Redis、JWT 及启用时的 Milvus standalone；保留 dev 启动自动 Flyway 和现有持久卷。

## Capabilities

### New Capabilities

- 无。

### Modified Capabilities

- `application-technical-baseline`：扩展开发基础设施与联调流程，提供 Docker 内 Nginx 到宿主机两个 dev 实例的反向代理、负载均衡和 SSE 验收要求。

## Impact

- 根 `docker-compose.yml`、新增 dev Nginx 配置、Vite 开发环境配置及相应本地联调说明。
- IDEA 双 Run/Debug Configuration 的操作说明、必要的配置检查和隔离代理集成验证。
- 本 change 仅覆盖 dev 集群演示；不修改生产 Compose、生产 profile、生产部署脚本或手册，不引入 Maven Wrapper，不改变 API 契约、数据库结构和 Milvus 拓扑。
