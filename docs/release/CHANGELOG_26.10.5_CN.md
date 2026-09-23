# MeowEco Economy 26.10.5

本版本在保留 Classic Vault 兼容的同时，新增可选的 VaultUnlocked v2 经济接口支持。

## 新增

- 新增 VaultUnlocked v2 经济服务，支持 UUID 账户、多货币、转账、金额精度校验和异步操作。
- 新增 Classic Vault 与 VaultUnlocked v2 互操作、精度边界、渐进式转账税、回滚、并发和异步关闭回归测试。
- 项目顶部入口增加直接提交 GitHub Issue 的按钮。

## 变更

- Classic Vault 与 VaultUnlocked v2 的余额变更统一使用共享经济服务、数据库事务和审计记录。
- README、Modrinth 项目描述和插件元数据均已补充 VaultUnlocked v2 兼容说明。
- VaultUnlocked v2 仍为可选集成；未安装时 Classic Vault 行为保持不变。

## 修复

- 拒绝超出货币精度或现有 double 存储安全范围的 v2 金额，避免不安全金额进入数据库。
- 协调 v2 异步操作与插件关闭流程；运行中的操作超时后会延迟关闭数据库。

## 兼容性

- Paper 26.1.x / 26.2
- Java 25
- 继续支持 Classic Vault。
- 支持可选的 VaultUnlocked v2 集成。

## 下载

- 请从 [Modrinth 版本页](https://modrinth.com/plugin/meoweco/versions) 下载编译好的插件 JAR。
- GitHub Release 仅包含发布说明，按项目分发策略不附加构建文件。
