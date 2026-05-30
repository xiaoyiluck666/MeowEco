# 26.7.4 修复记录

## 任务
修复代码审查中发现的两个问题，并将版本升级到 `26.7.4`，补充对应更新日志。

## 改动
- 移除 `PlayerLookup.resolveOfflinePlayer(...)` 中对 `Bukkit#getOfflinePlayer(String)` 的名字兜底，避免不存在的玩家名被解析成伪造离线账户。
- 调整 `EcoCommand` 权限判断：
  - `give` / `take` / `set` / `freeze` / `unfreeze` / `deductfrozen` 依赖各自独立权限节点。
  - `setrate` / `refresh` / `hide` / `unhide` 继续要求 `meoweco.admin`。
- 将项目版本与 Paper 插件版本更新到 `26.7.4`。
- 在 `CHANGELOG.md` 新增 `v26.7.4 - 2026-04-08` 发布日志。

## 验证
执行命令：`./gradlew.bat :meoweco-paper:check :meoweco-paper:shadowJar`

结果：成功。

覆盖点：
- `compileJava` 成功
- `compileLegacyPaperCompatJava` 成功
- `shadowJar` 成功

## 产物
- `meoweco-paper/build/libs/meoweco-paper-26.7.4.jar`

## 备注
- 构建过程中仍有 `MeowEconomy.java` 的过时 API 提示，但未阻塞本次修复和打包。