# MeowEco Economy 26.10.6

本版本新增经过验证的 Paper 26.3 支持，同时继续兼容现有的 Paper 26.1.x 与 26.2 服务端。

## 新增

- 将 Paper 26.3 build 38 设为主编译目标。
- 在现有 Paper 26.1.x 检查之外，新增独立的 Paper 26.2 稳定版兼容编译检查。

## 变更

- 支持范围更新为运行 Java 25 的 Paper 26.1.x、26.2 与 26.3。
- README、Modrinth 项目描述和发布元数据均已补充 Paper 26.3 兼容说明。

## 修复

- 每次发布检查都会同时使用 Paper 26.2 与 26.3 API 编译完整插件源码，避免后续引入 26.3 专属 API 时无意破坏 26.2 兼容性。

## 验证

- 已通过 Paper 26.1.1、26.1.2、26.2 与 26.3 API 编译。
- 已通过数据库、迁移和 VaultUnlocked v2 回归测试。
- 已在真实 Paper 26.3 build 38 服务端完成启动与停止烟测，SQLite 初始化和数据库关闭均正常。

## 兼容性

- Paper 26.1.x / 26.2 / 26.3
- Java 25
- 继续支持 Classic Vault。
- 继续支持可选的 VaultUnlocked v2 集成。

## 下载

- 请从 [Modrinth 版本页](https://modrinth.com/plugin/meoweco/versions) 下载编译好的插件 JAR。
- GitHub Release 仅包含发布说明，按项目分发策略不附加构建文件。
