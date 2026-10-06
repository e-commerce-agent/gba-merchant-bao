# 后端自动化测试说明

环境要求：JDK 21、Maven 3.9.11，以及已启动 Linux 容器引擎的 Docker Desktop。首次运行时需要能够下载 Maven 依赖和指定版本的容器镜像。

在仓库根目录执行以下命令：

```powershell
# 快速单元测试和 HTTP、数据库结构契约检查，不需要 Docker。
mvn -f api/pom.xml test

# 完整验收，包含独立 MySQL、Redis 容器的集成测试。
mvn -f api/pom.xml clean verify
```

完整测试由 Testcontainers 自动创建和清理容器。你只需要启动 Docker Desktop，无需手动创建或启动测试用 MySQL、Redis 容器。宿主机端口动态分配，测试不使用本机安装的 MySQL、已有数据库或固定 Redis 实例，容器复用保持关闭。

测试不调用或复用开发、生产环境的 `docker-compose.yaml`，不挂载业务数据目录或命名数据卷，不加入业务网络，也不指定业务容器名称或固定宿主机端口。`withExposedPorts(6379)` 声明的是测试容器内部的 Redis 端口，`getMappedPort(6379)` 获取的是随机分配的宿主机端口。代码已显式关闭容器复用。测试与开发容器共用 Docker 镜像缓存、引擎的 CPU 和内存资源，但中间件数据相互独立。自动化测试应在开发用 Docker Desktop 引擎上运行。

开发环境 Compose 的固定端口和数据持久化位置，由实际引入相关中间件的 change 登记，包括后续登录模块的 change。这些固定端口不会作为自动化测试的默认连接端口。

加载 Spring 应用上下文的测试明确使用 `application-test.yml` 和专用测试配置，无需个人的 `application-dev.yml`。集成测试通过容器实际映射的地址和端口连接。Docker 不可用时，完整验收会失败，并提示启动 Docker Desktop 后重新运行。仅 `mvn test` 成功不能代表完整验收通过。

Surefire 的单元和契约测试报告位于 `api/target/surefire-reports`，Failsafe 的集成测试报告位于 `api/target/failsafe-reports`。两类 XML 报告均记录实际测试总数、失败数、错误数和跳过数。生成的报告被 Git 忽略，测试源码需要随实现一并提交。

Maven 的 Surefire、Failsafe 会将 `LOG_DIR` 设置为 `api/target/test-logs` 的绝对路径，因此测试日志不会追加到开发或生产日志中。执行 Maven 的 `clean` 阶段时，这些测试日志会被清理。应用运行时通过 `logging.file.path` 或 `LOG_DIR` 配置日志位置：工作目录设为 `api/` 时，开发日志默认写入 `api/.runtime/logs`；生产环境需要将 `LOG_DIR` 指向外部可写日志目录或部署挂载目录。在 IDE 中直接运行测试时，可以通过 JVM 系统属性 `LOG_DIR` 指定输出目录。

MySQL 测试覆盖空库迁移、重复迁移、租户测试数据的幂等性，以及模拟旧数据库结构的显式 baseline 和升级，并验证旧测试记录得到保留。Redis 测试使用保留的旧序列化方式和配置，通过实际断言验证字符串、哈希、列表、集合、有序集合和键操作。

上述测试使用构造的测试数据。生产数据库的备份、恢复演练和结构对比仍需在发布流程中单独执行，不能用这些测试替代。
