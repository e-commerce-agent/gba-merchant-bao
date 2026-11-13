# 隔离联调

- [manual-verification.ps1](manual-verification.ps1)：仓库根运行 `powershell.exe -NoProfile -ExecutionPolicy Bypass -File api/scripts/test/manual-verification.ps1 -BaseUrl http://127.0.0.1:<隔离端口>`；目标为显式启用 dev mock/debug SMS 的隔离后端，使用公开演示账号并写入本轮认证数据；需要 PowerShell、JDK 21、Maven 和 Docker，优先由 `mvn -f api/pom.xml '-Dtest=AuthDevManualIT' test` 创建临时 MySQL/Redis 并调用，不将命令发到日常开发库。

认证联调前提见 [认证目录](../../../docs/archive/20261113-auth/README.md#manual-verification)，隔离规则见 [测试目录](../../docs/testing/README.md)。
