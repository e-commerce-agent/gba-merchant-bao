# 验证记录

ChatHttpIT 的 `clientDisconnectCancelsUpstreamAndLeavesAQueryableTerminal` 用 5 秒等待上游取消，全量并行启动多个容器时偶发超时（2026-11-10 首次全量失败，单类重跑与二次全量均通过）。