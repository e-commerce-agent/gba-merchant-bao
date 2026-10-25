#### 目录
每次量化运行只创建一个目录：
docs/eval/runs/<run-id>/
├── run-manifest.yaml
├── result.json 或 result.csv
└── report.md

`docs/eval/runs/`只保存在本地并由`.gitignore`整体忽略，不需要为了提交GitHub再处理原始结果脱敏。
完成一次对比后，只把样本数、测试条件、指标定义和基线 + 改造后数字整理到`docs/eval/reports/<module>-<date>.md`。
聚合报告不得包含手机号、订单明细、完整Prompt、密钥、原始请求响应或数据库导出。

#### 量化控制变量
run-manifest.yaml 固定记录：
- Git commit
- 技术亮点和运行目的
- 数据集名称与版本
- 模型和 Prompt 版本
- Java、Spring Boot、LangChain4j、MySQL、Redis、RabbitMQ、Milvus 版本
- 操作系统、CPU、内存、Docker Desktop
- 并发数、预热时间、运行时长、运行轮数
- Apifox、JMeter、Another Redis Desktop Manager、Attu 版本
- 故障注入条件
- 指标分母、统计口径和失败分类
后续不需要每个任务另外设计一套记录方式，只需要复制这个模板并填写。
