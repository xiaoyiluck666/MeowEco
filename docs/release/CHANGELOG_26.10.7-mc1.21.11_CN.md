# MeowEco Economy 26.10.7-mc1.21.11

此 LTS 兼容版本将当前 MeowEco 的交易账本、审计命令和经济政策分析带到 Paper 1.21.11，同时保持现代主线继续使用 Java 25。

## 新增

- 为 Paper 1.21.11 提供 `/meco audit` 交易历史查询和导出功能。
- 为 Paper 1.21.11 提供 `/meco policy report` 货币供应与集中度分析。
- 包含 MeowEco 26.10.7 的共享 MySQL 多实例一致性修复。

## 变更

- 构建目标调整为 Paper API 1.21.11 和 Java 21。
- 使用 `api-version: 1.21.11`，使服务端按明确的兼容基线校验插件。
- 作为独立 LTS 产物维护，现代主线继续使用 Paper 26.x 和 Java 25。
- 自动更新检查仅跟踪 Modrinth 上兼容 Paper 1.21.11 的版本。

## 修复

- 将 Java 25 专属的未命名资源变量替换为 Java 21 兼容代码，不改变审计行为。

## 验证

- 使用 Java 21 字节码针对 Paper API 1.21.11 编译完整插件。
- 通过核心经济、SQLite、迁移、Classic Vault 和 VaultUnlocked v2 回归测试。
- 在真实 Paper 1.21.11 服务端验证打包后的插件 JAR。

## 兼容性

- Paper 1.21.11
- Java 21
- 继续支持 Classic Vault。
- 继续提供可选的 VaultUnlocked v2 集成。

## 维护策略

- 此分支接收严重 Bug、安全、数据库正确性和兼容性修复。
- Paper 26.x 专属的新功能默认保留在现代主线，只有明确选中的功能才会回移。
