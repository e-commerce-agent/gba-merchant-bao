## 运行证据

2026-11-06 定向认证回归：43 tests，0 failures、0 errors、0 skipped；命令见 [测试说明（本目录入口）](README.md#testing) 的认证段。本次实际运行包括下述集成测试和脚本，不等于第 9 节的全量验收。

AuthFlowIT 覆盖账号提交/故障、独立挑战并发唯一冲突、双 token、idle/absolute、加密幂等重试/重放、退出竞争、状态检查与 HTTP 权限。AuthSmsRedisIT 覆盖挑战并发一次消费、失败清理、限流和旧 Redis 序列化并存。HttpClientTest 回归旧路由/头/envelope 和新旧 token 交叉拒绝；AuthContextTest 验证正常/异常 finally 清上下文。JsonLoggingTest 读取真实 JSON 日志验证审计字段和分流。

报告与本次脚本输出只在 `api/target/`。第 9 节完整 `test`/`clean verify` 汇总见 [验证记录（本目录入口）](README.md#verification)；Docker 不可用应失败，不跳过或改连固定开发端口。
