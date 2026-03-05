# MeowEco - 现代 Minecraft 经济插件

[![Modrinth Downloads](https://img.shields.io/modrinth/dt/meoweco?color=00AF5C&label=Modrinth%20Downloads)](https://modrinth.com/plugin/meoweco)
[![GitHub license](https://img.shields.io/github/license/xiaoyiluck666/MeowEco)](https://github.com/xiaoyiluck666/MeowEco/blob/main/LICENSE)
[![GitHub Workflow Status](https://img.shields.io/github/actions/workflow/status/xiaoyiluck666/MeowEco/gradle.yml?branch=main)](https://github.com/xiaoyiluck666/MeowEco/actions)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/xiaoyiluck666/MeowEco)](https://github.com/xiaoyiluck666/MeowEco/releases)

MeowEco 是一个**多货币支持**、**极致性能**且**高度兼容**的 Minecraft 经济插件，专为现代服务器设计。

## ✨ 核心特性

*   **多货币系统 (Multi-Currency)**
    *   支持无限种自定义货币（如金币、点券、宝石等）。
    *   每种货币拥有独立的小数位、初始余额、显示名称和转账税率。
    *   内置**汇率推导系统**，支持玩家自主进行跨货币兑换。

*   **极致性能 (High Efficiency)**
    *   **异步读写**：所有的数据库操作（MySQL/SQLite）均在异步线程执行。
    *   **主键纠偏**：自动升级数据库架构，支持多货币共存且不冲突。
    *   **智能缓存**：排行榜和余额内置缓存机制，5分钟自动刷新，变动时实时失效。

*   **统一指令系统 (Unified Command)**
    *   **单一前缀**：所有功能已完全汇集至 `/meco` (或 `/meoweco`) 指令下，不再占用多个顶级指令。
    *   **智能补全**：根据输入上下文自动弹出子命令、玩家名、金额（10/100/1000）或货币 ID。

*   **完美兼容 (High Compatibility)**
    *   **Paper 1.21+**：原生适配最新核心。
    *   **Adventure API**：支持 RGB 颜色代码及 `&` 颜色解析。
    *   **Vault & PAPI**：完全兼容 Vault 经济接口及 PlaceholderAPI 变量。

## 🚀 安装与使用

1.  将 `MeowEco-X.X.X.jar` (最新版本请查看 [Releases](https://github.com/xiaoyiluck/MeowEco/releases)) 放入服务器的 `plugins` 文件夹。
2.  启动服务器，插件将自动生成 `config.yml` 和 `lang` 文件夹。
3.  根据 `config.yml` 配置你的货币、数据库和汇率。
4.  重启服务器以应用配置。

## ⚙️ 配置说明

### `config.yml` (核心配置)

```yaml
# 存储设置
storage:
  type: sqlite # sqlite 或 mysql
  mysql:
    host: localhost
    port: 3306
    database: meoweco
    username: root
    password: password
    use-ssl: false

# 更新检测设置
update-checker:
  enabled: true   # 是否开启自动更新检测
  interval: 24    # 检测间隔 (小时)
  slug: "meoweco" # Modrinth 项目标识

# 默认货币 (用于 Vault 接口和缺省参数)
default-currency: "coins"

# Exchange rates (1 source currency = N target currency)
exchange-rates:
  enabled: true  # 是否启用汇率兑换系统
  coins:
    points: 0.1  # 1 金币 = 0.1 点券
    gems: 0.01   # 1 金币 = 0.01 宝石
  points:
    coins: 10.0  # 1 点券 = 10 金币

# 货币定义
currencies:
  coins:
    display-name: "&e金币"      # 显示名称 (支持颜色)
    singular: "金币"            # 单位 (单数)
    plural: "金币"              # 单位 (复数)
    initial-balance: 100.0      # 新玩家初始余额
    decimal-places: 2           # 小数位 (0-2)
    transfer-tax: 0.0           # 转账税率 (0.05 = 5%)
  points:
    display-name: "&b点券"
    singular: "点券"
    plural: "点券"
    initial-balance: 0.0
    decimal-places: 0
    transfer-tax: 0.0
```

## 🎮 指令系统 (统一前缀 `/meco`)

所有功能均可通过 `/meco` 调用，同时也支持以下常规快捷指令：

### 常规快捷指令

| 指令 | 别名 | 描述 |
| :--- | :--- | :--- |
| `/bal` | `/money`, `/balance` | 查看余额 |
| `/pay` | - | 向玩家转账 |
| `/baltop` | - | 查看财富排行榜 |
| `/eco` | `/economy` | 管理员经济指令 |

### `/meco` 基础子命令

| 子命令 | 参数 | 描述 |
| :--- | :--- | :--- |
| `bal` | `[player] [currency]` | 查看余额 |
| `pay` | `<player> <amount> [currency]` | 向玩家转账 |
| `top` | `[currency]` | 查看财富排行榜 |
| `exchange` | `<amount> <from> <to>` | 跨货币兑换 (需在 config 开启 enabled) |

### 管理子命令 (权限: `meoweco.admin`)

| 子命令 | 参数 | 描述 |
| :--- | :--- | :--- |
| `/eco bal` | `[player] [currency]` | 查看余额 (快捷方式) |
| `/eco top` | `[currency]` | 查看排行榜 (快捷方式) |
| `checkupdate` | - | 手动检查 Modrinth 上的新版本 |
| `give` | `<player> <amount> [currency]` | 发放货币 |
| `take` | `<player> <amount> [currency]` | 扣除货币 |
| `set` | `<player> <amount> [currency]` | 设置玩家余额 |
| `setrate` | `<from> <to> <rate>` | 设置两货币间的汇率 |
| `hide` | `<player>` | 从排行榜中隐藏特定玩家 |
| `unhide` | `<player>` | 取消玩家的排行榜隐藏状态 |
| `refresh` | - | 强制刷新排行榜缓存并尝试修复未知玩家名 |
| `reload` | - | 重载配置文件、语言文件及货币数据 |
| `debug` | `[currencies]` | 开启/关闭调试模式，或列出已加载货币 |

## 📊 PlaceholderAPI 变量

所有变量均支持自定义货币 ID（`<currency>`），例如 `gems`, `points`。

| 变量 | 描述 | 示例输出 |
| :--- | :--- | :--- |
| `%meoweco_balance_<currency>%` | 余额 (带千分位数字) | `1,234.56` |
| `%meoweco_balance_short_<currency>%` | 余额 (K/M/B 短格式) | `1.23K` |
| `%meoweco_balance_formatted_<currency>%` | 余额 (带数字和货币名) | `1,234.56 宝石` |
| `%meoweco_balance_fixed_<currency>%` | 余额 (纯数字，不带千分位) | `1234.56` |
| `%meoweco_top_<N>_name_<currency>%` | 排行榜第 N 名玩家名 | `xiaoyiluck` |
| `%meoweco_top_<N>_formatted_<currency>%` | 排行榜第 N 名余额 (带单位) | `5,000.00 点券` |
| `%meoweco_server_total_<currency>%` | 全服该货币总流通量 | `8,945.00` |
| `%meoweco_currency_display_<currency>%` | 货币彩色显示名 | `&b点券` |

## 💻 开发者 API

MeowEco 提供了简单的 API 接口，供其他插件通过 Bukkit ServicesManager 获取货币信息。

### 获取 API 实例

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.xiaoyiluck.meoweco.api.MeowEcoAPI;
import com.xiaoyiluck.meoweco.objects.Currency;

public class YourPlugin {

    private MeowEcoAPI meowEcoAPI;

    public void onEnable() {
        // ... 你的插件启动逻辑 ...

        RegisteredServiceProvider<MeowEcoAPI> rsp = Bukkit.getServicesManager().getRegistration(MeowEcoAPI.class);
        if (rsp != null) {
            this.meowEcoAPI = rsp.getProvider();
            getLogger().info("成功获取 MeowEco API.");
        } else {
            getLogger().warning("未找到 MeowEco API，请确保 MeowEco 插件已安装并启用！");
        }
    }

    // ... 其他方法 ...

    public void exampleUsage() {
        if (meowEcoAPI != null) {
            // 获取所有已注册的货币
            for (Currency currency : meowEcoAPI.getRegisteredCurrencies()) {
                getLogger().info("货币 ID: " + currency.getId() + ", 显示名: " + currency.getDisplayName());
            }

            // 获取特定货币
            Currency coins = meowEcoAPI.getCurrency("coins");
            if (coins != null) {
                getLogger().info("金币的单数名: " + coins.getSingular());
            }
            
            // 获取玩家余额
            // UUID playerUUID = ...;
            // double balance = meowEcoAPI.getBalance(playerUUID, "coins");
            // getLogger().info("玩家金币余额: " + balance);

            // 扣除玩家余额
            // boolean success = meowEcoAPI.withdraw(playerUUID, "points", 10.0);
            // if (success) {
            //     getLogger().info("成功扣除 10 点券");
            // } else {
            //     getLogger().warning("点券不足或扣除失败");
            // }

            // 存入玩家余额
            // boolean success = meowEcoAPI.deposit(playerUUID, "gems", 5.0);
            // if (success) {
            //     getLogger().info("成功存入 5 宝石");
            // } else {
            //     getLogger().warning("存入宝石失败");
            // }
        }
    }
}
```

### 接口定义

```java
package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.objects.Currency;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.UUID;

public interface MeowEcoAPI {

    /**
     * Gets the instance of the MeowEcoAPI.
     *
     * @return The API instance, or {@code null} if not registered.
     */
    static MeowEcoAPI get() {
        RegisteredServiceProvider<MeowEcoAPI> rsp = Bukkit.getServicesManager().getRegistration(MeowEcoAPI.class);
        if (rsp == null) return null;
        return rsp.getProvider();
    }

    /**
     * Retrieves a collection of all registered currencies in the plugin.
     *
     * @return A {@link Collection} of {@link Currency} objects. The collection is read-only.
     */
    Collection<Currency> getRegisteredCurrencies();

    /**
     * Gets a specific currency by its unique identifier (ID).
     *
     * @param id The ID of the currency to retrieve.
     * @return The {@link Currency} object, or {@code null} if not found.
     */
    Currency getCurrency(String id);

    /**
     * Gets the total balance of a player for a specific currency.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @return The total balance.
     */
    double getBalance(UUID uuid, String currencyId);

    /**
     * Deposits a certain amount of funds to a player's account.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to deposit.
     * @return {@code true} if successful.
     */
    boolean deposit(UUID uuid, String currencyId, double amount);

    /**
     * Withdraws a certain amount of funds from a player's account.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to withdraw.
     * @return {@code true} if successful, {@code false} if insufficient funds.
     */
    boolean withdraw(UUID uuid, String currencyId, double amount);

    /**
     * Gets the frozen balance of a player for a specific currency.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @return The frozen balance.
     */
    double getFrozenBalance(UUID uuid, String currencyId);

    /**
     * Gets the available balance (total balance - frozen balance) of a player for a specific currency.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @return The available balance.
     */
    double getAvailableBalance(UUID uuid, String currencyId);

    /**
     * Freezes a certain amount of funds for a player.
     * The funds must be available in the player's account.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to freeze.
     * @return {@code true} if successful, {@code false} if insufficient available funds.
     */
    boolean freeze(UUID uuid, String currencyId, double amount);

    /**
     * Unfreezes a certain amount of funds for a player.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to unfreeze.
     * @return {@code true} if successful, {@code false} if insufficient frozen funds.
     */
    boolean unfreeze(UUID uuid, String currencyId, double amount);

    /**
     * Deducts a certain amount of funds from the player's frozen balance.
     * This also reduces the total balance of the player.
     *
     * @param uuid The UUID of the player.
     * @param currencyId The ID of the currency.
     * @param amount The amount to deduct.
     * @return {@code true} if successful, {@code false} if insufficient frozen funds.
     */
    boolean deductFrozen(UUID uuid, String currencyId, double amount);
}
```

## 🤝 贡献

欢迎任何形式的贡献！如果你有好的想法、发现了 Bug 或者想提交代码，请随时在 GitHub 上提交 Issue 或 Pull Request。

## 许可证

本项目采用 MIT 许可证。详情请参阅 [LICENSE](https://github.com/xiaoyiluck/MeowEco/blob/main/LICENSE) 文件。
