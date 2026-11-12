# 商户宝后端

`api/` 是使用 JDK 21、Spring Boot 3.5.16 的 Maven 后端工程。

## 运行与测试

开发环境、启动方式和配置前提见 [运行与部署文档](docs/deployment/README.md)。从仓库根目录执行：

```powershell
mvn -f api/pom.xml test
mvn -f api/pom.xml clean verify
```

完整验证需要 Docker Desktop 的 Linux 容器引擎。测试环境和报告说明见 [测试文档](docs/testing/README.md)。

## 目录入口

- [后端文档](docs/README.md)：各主题文档入口。
- [Springdoc 快照](../docs/api/)：已导出的接口文档。
- `src/`：实现、测试和数据库脚本。
- `target/`：构建产物、测试报告和日志。
- `.runtime/`：本地运行文件；开发运行工作目录为 `api/`。
