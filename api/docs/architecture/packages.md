# 后端包结构说明

`api/` 是单个后端 Maven 应用。Maven 坐标为 `com.gba:gba-merchant-bao`，Java 根包为 `com.gba.merchantbao`。

```text
api/src/main/java/com/gba/merchantbao/
  MerchantApplication.java
  api/                 基础 HTTP 接口样例和异常处理
  common/api/          公共响应格式
  common/error/        公共错误码
  common/privacy/      展示和输出时的隐私脱敏
  config/              基础配置
  infrastructure/web/  请求关联标识等 Web 基础设施
  legacy/              保留的旧餐饮项目实现
```

原来的 `com.countmaske.merchant` 存放旧餐饮项目实现，`com.countmaske.merchantassistant` 存放新建的商户助手基础组件。两者由同一个应用扫描和运行，并不是两个独立部署的应用。现在用更明确的结构区分迁移边界：新代码放在根包及对应业务域的子包中，保留的旧代码统一放在 `legacy` 下。

原来的 `merchant-common`、`merchant-pojo` 和 `merchant-server` 三模块已经合并到 `api/src/main`。重复源码、旧 POM 和退役模块的实际目录均已删除，迁移后为空的旧 `com/countmaske` 源码目录也已清理。三个旧本地配置和测试草稿文件保留在 `api/.runtime/legacy-backup` 中，该目录被 Git 忽略。

Java 中的 `api` 包负责 HTTP 协议层的适配，不是另一个后端工程或 Maven 模块。新增业务接口应放在对应业务域的 `controller`、`dto` 等包中。后续的 `merchant` 业务域表示商户和租户业务，不再表示整个旧项目。根包下的 `common` 存放新项目的公共基础组件；`legacy` 则保留旧项目的常量、实体、Mapper 和 Service 等包，以维持兼容。

旧路由 `/admin` 和 `/user` 保留现有接口契约，新路由使用 `/api/v1`。包名迁移本身不改变业务行为。新基础组件不能直接导入旧项目的实体或 Mapper。

后端维护文档统一放在 `api/docs/` 下，按主题分类。架构说明放在当前目录，测试运行说明见 [自动化测试说明](../testing/README.md)。业务规划、OpenSpec 和历史交付记录继续保留在仓库级目录中。后端根目录的 README 作为文档导航入口。
