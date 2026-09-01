# MeowEco Economy 26.10.0

本版本为运行 Java 25 的 Paper 26.1.x / 26.2 服务器加入了可预检的余额迁移与持久化交易审计。

## Added

- 支持从任意在线 Vault 经济提供者迁移，可覆盖常见的 EssentialsX、CMI、XConomy 和 The New Economy 环境。
- 支持离线读取 EssentialsX userdata 与标准 CSV 余额文件。
- 正式迁移前自动生成 CSV 备份，并在单个数据库事务内完成整批导入。
- 持久化记录来源、操作者、交易 ID、金额、变更前后余额与冻结余额。
- 新增 `/meco audit <玩家> [货币] [数量]` 与 `/meco audit export [数量]`。
- 接入可关闭的 bStats，并预留存储类型、货币数量和语言图表。
- 新增 GitHub Actions 自动测试、兼容编译和发布构建。

## Changed

- 默认消息语言改为英文。中文服将 `messages.language` 设置为 `zh_CN` 后执行 `/meco reload` 即可。
- 商店展示名改为 **MeowEco Economy**，内部插件名、数据目录、命令、API 和 Placeholder 标识保持兼容。
- Modrinth 首屏改为产品定位、五个卖点、兼容表和三分钟安装流程。

## Fixed

- 失败的余额操作和迁移不会留下误导性的审计记录。
- 转账与兑换的双方记录共享同一个交易 ID。
- Paper-only 构建不再在 Modrinth 版本元数据中声明 Bukkit 或 Spigot 加载器。
