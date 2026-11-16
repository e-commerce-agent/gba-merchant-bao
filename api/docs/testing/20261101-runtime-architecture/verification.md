# automation-runtime-architecture 验收记录

日期：2026-11-01。本记录针对当前工作区实现；目录统一后的完整重跑于 20:27（Asia/Shanghai）通过，后续修改后应重新执行相应验证。

## 自动化验证

`mvn -f api/pom.xml clean verify` 返回 BUILD SUCCESS：

| 阶段 | 测试数 | 失败 | 错误 | 跳过 |
| --- | ---: | ---: | ---: | ---: |
| Surefire 单元/契约/配置 | 15 | 0 | 0 | 0 |
| Failsafe 容器集成 | 14 | 0 | 0 | 0 |

集成测试包含空库 V1→V2→V3、种子幂等、旧库拒绝自动接管与显式 baseline-1、旧行保留、Spring 应用与 fixture 回滚、生产账号分权、已有数据目录不重跑初始化、完整 SQL 导出恢复及 Redis 认证/数据操作。

报告位于 `api/target/surefire-reports` 和 `api/target/failsafe-reports`。测试日志仅在 `api/target/test-logs`；测试前后开发日志的 SHA256、大小和修改时间一致。首轮实现验收在 dev Compose 运行期间完成，测试结束后两个联调服务仍健康；本次目录统一后的重跑中 dev Compose 未启动，集成测试独立运行。两轮测试均只清理自身容器。

打包检查确认主 JAR 仅含 application.yml 和 application-prod.yml，不含 dev/test/local YAML。原有 V1/V2/V3 未修改；旧快照使用 utf8mb3 仍会产生 MySQL 弃用警告，不为消除警告改写已发布迁移。

## 配置和手动隔离演练

- dev/prod 两套 `docker compose config --quiet` 均通过；生产配置统一位于 prod-profile/opt/gba，不保留重复模板；初始化脚本为 LF，测试直接读取该目录。
- dev MySQL 临时行和 Redis 临时键在重启、不删卷的容器重建后保留，随后仅清理本次演练数据；干净 dev 配置已完成应用启动及健康接口演练。
- 日志 bind mount 使用临时目录和可销毁容器验证重建后文件保留；JSON 输出、关联字段、脱敏、审计分离和轮转配置由聚焦测试验证。
- 本地备份脚本成功导出一份结构＋数据 SQL 到忽略的 backups/；隔离 MySQL 验证完整导出恢复后的结构、代表性记录及 version。
- 同版本 Flyway CLI 11.7.2 在独立演练库验证普通 migrate 拒绝未登记非空库，显式 baseline 后 history 为 BASELINE 1、SQL 2、SQL 3，旧行保留，并完成隔离恢复验证；未接管个人真实旧库。
- 实际 .env、dev YAML、JAR、发布 SQL 和 backups/ 被 Git 忽略；生产 Compose、Dockerfile、初始化脚本、.env.example 及操作手稿按文件放行；OpenSpec 严格校验通过。

## 生产状态

本次只准备 prod Compose、环境配置、初始化脚本、Dockerfile 和未来手动发布说明，并在可销毁测试容器中验收职责。生产准备目录未放置 app.jar 或 SQL，未构建或发布真实生产应用镜像，未执行 ECS/RDS 操作，未新增 CI/tag 发布流水线。

这些验证不替代未来目标库的备份、人工结构核验、实际镜像构建或发布验收。
