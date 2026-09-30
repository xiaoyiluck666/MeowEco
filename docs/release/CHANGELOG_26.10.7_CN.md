# MeowEco Economy 26.10.7

本版本为多个 Paper 服务端共用同一个 MySQL 数据库的网络新增可靠的余额一致性支持。

## 新增

- 新增原子余额快照，使余额和冻结金额通过同一次共享数据库查询读取。
- 新增使用两个独立插件数据库实例的真实 MySQL 回归测试。

## 变更

- Classic Vault 余额读取现在直接以 MySQL 为准，不再使用进程内读取缓存。
- 正式支持并记录多个 Paper 实例共用 MySQL 的部署方式。

## 修复

- 修复一个 Paper 服务端修改账户后，另一个服务端仍可能返回旧 Vault 余额的问题。
- 修复 MySQL 并发场景下两笔有效存款中的一笔可能被回滚的问题。
- 为多账户操作加入确定性的加锁顺序，防止双向转账死锁并保持总余额不变。

## 验证

- 在 MySQL 8.4 上通过两个独立连接池完成并发提现、并发存款和双向转账各 25 轮压力回归。
- 验证已提交的余额与冻结金额变更可立即被另一实例读取。
- 通过完整 Gradle 检查，包括 SQLite、迁移、Classic Vault、VaultUnlocked v2 和 Paper 兼容性测试。

## 兼容性

- Paper 26.1.x / 26.2 / 26.3
- Java 25
- 继续支持 Classic Vault。
- 继续支持可选的 VaultUnlocked v2 集成。

## 下载

- 请从 [Modrinth 版本页](https://modrinth.com/plugin/meoweco/versions) 下载编译好的插件 JAR。
- GitHub 提供源代码和问题跟踪。
