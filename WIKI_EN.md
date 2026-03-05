# MeowEco Documentation (v26.6.9)

MeowEco is a **multi-currency**, **high-performance**, and **highly compatible** economy plugin for Minecraft, designed for modern servers.

## ✨ Core Features

*   **Multi-Currency System**
    *   Support for unlimited custom currencies (e.g., Coins, Points, Gems).
    *   Each currency has independent decimal places, initial balance, display name, and transfer tax.
    *   Built-in **Exchange Rate System**, allowing players to convert between different currencies.

*   **High Efficiency**
    *   **Asynchronous I/O**: All database operations (MySQL/SQLite) are executed on asynchronous threads.
    *   **Schema Migration**: Automatically upgrades the database schema to support multi-currency without conflicts.
    *   **Intelligent Caching**: Leaderboards and balances have a built-in cache that refreshes every 5 minutes and invalidates in real-time on changes.

*   **Unified Command System**
    *   **Single Prefix**: All features are consolidated under the `/meco` (or `/meoweco`) command, no longer occupying multiple top-level commands.
    *   **Smart Completion**: Automatically suggests sub-commands, player names, amounts (10/100/1000), or currency IDs based on context.

*   **Great Compatibility**
    *   **Paper 1.21+**: Native support for the latest server cores.
    *   **Adventure API**: Supports RGB color codes and `&` color parsing.
    *   **Vault & PAPI**: Fully compatible with Vault Economy API and PlaceholderAPI placeholders.

---

## 1. Configuration Guide

### 1.1 config.yml
Location: `plugins/MeowEco/config.yml`

```yaml
# Storage Settings
storage:
  type: sqlite # sqlite or mysql
  mysql:
    host: localhost
    port: 3306
    database: meoweco
    username: root
    password: password
    use-ssl: false

# Update Checker Settings
update-checker:
  enabled: true   # Enable automatic update checking
  interval: 24    # Check interval (hours)
  slug: "meoweco" # Modrinth project slug

# Default currency used for Vault and commands when no currency is specified
default-currency: "coins"

# Exchange rates (1 source currency = N target currency)
exchange-rates:
  enabled: true  # Whether to enable the currency exchange system
  coins:
    points: 0.1  # 1 Coin = 0.1 Points
    gems: 0.01   # 1 Coin = 0.01 Gems
  points:
    coins: 10.0  # 1 Point = 10 Coins

# Currency Definitions
currencies:
  coins:
    display-name: "&eCoins"      # Display name (supports color)
    singular: "Coin"            # Singular name
    plural: "Coins"             # Plural name
    initial-balance: 100.0      # Initial balance for new players
    decimal-places: 2           # Decimal places (0-2)
    transfer-tax: 0.0           # Tax rate (0.05 = 5%)
  points:
    display-name: "&bPoints"
    singular: "Point"
    plural: "Points"
    initial-balance: 0.0
    decimal-places: 0
    transfer-tax: 0.0
```

---

## 2. Command System (Unified Prefix /meco)

All features can be accessed via `/meco`, and the following conventional shortcut commands are also supported:

### 2.1 Conventional Shortcut Commands
| Command | Aliases | Description |
| :--- | :--- | :--- |
| `/bal` | `/money`, `/balance` | Check balance |
| `/pay` | - | Pay players |
| `/baltop` | - | View leaderboard |
| `/eco` | `/economy` | Admin economy commands |

### 2.2 /meco Player Commands
| Sub-command | Parameters | Description |
| :--- | :--- | :--- |
| `bal` | `[player] [currency]` | Check balance |
| `pay` | `<player> <amount> [currency]` | Transfer money |
| `top` | `[currency]` | View leaderboard |
| `exchange` | `<amount> <from> <to>` | Exchange currency |

### 2.3 Admin Commands (Permission: meoweco.admin)
| Sub-command | Parameters | Description |
| :--- | :--- | :--- |
| `/eco bal` | `[player] [currency]` | Check balance (Shortcut) |
| `/eco top` | `[currency]` | View leaderboard (Shortcut) |
| `checkupdate` | - | Manually check for updates |
| `give` | `<player> <amount> [currency]` | Give currency |
| `take` | `<player> <amount> [currency]` | Take currency from a player |
| `set` | `<player> <amount> [currency]` | Set a player's balance |
| `setrate` | `<from> <to> <rate>` | Set exchange rate between two currencies |
| `hide` | `<player>` | Hide a player from the leaderboard |
| `unhide` | `<player>` | Unhide a player from the leaderboard |
| `refresh` | - | Force refresh cache and fix unknown player names |
| `reload` | - | Reload config, messages, and currency data |
| `debug` | `[currencies]` | Toggle debug mode or list loaded currencies |

---

## 3. PlaceholderAPI Placeholders

All placeholders support dynamic currency IDs (`<currency>`), e.g., `gems`, `points`.

| Placeholder | Description | Example Output |
| :--- | :--- | :--- |
| `%meoweco_balance_<currency>%` | Balance (with commas) | `1,234.56` |
| `%meoweco_balance_short_<currency>%` | Balance (Short K/M/B format) | `1.23K` |
| `%meoweco_balance_formatted_<currency>%` | Balance (with commas and unit) | `1,234.56 Gems` |
| `%meoweco_balance_fixed_<currency>%` | Balance (raw numeric, no commas) | `1234.56` |
| `%meoweco_top_<N>_name_<currency>%` | Player name at rank N | `xiaoyiluck` |
| `%meoweco_top_<N>_formatted_<currency>%` | Balance at rank N (with unit) | `5,000.00 Points` |
| `%meoweco_server_total_<currency>%` | Total circulation of currency | `8,945.00` |
| `%meoweco_currency_display_<currency>%` | Colored currency display name | `&bPoints` |

---

## 4. Developer API

MeowEco provides a simple API interface for other plugins to retrieve currency information via Bukkit ServicesManager.

### 4.1 Getting the API Instance

```java
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import com.xiaoyiluck.meoweco.api.MeowEcoAPI;
import com.xiaoyiluck.meoweco.objects.Currency;

public class YourPlugin {

    private MeowEcoAPI meowEcoAPI;

    public void onEnable() {
        // ... Your plugin's enable logic ...

        RegisteredServiceProvider<MeowEcoAPI> rsp = Bukkit.getServicesManager().getRegistration(MeowEcoAPI.class);
        if (rsp != null) {
            this.meowEcoAPI = rsp.getProvider();
            getLogger().info("Successfully hooked into MeowEco API.");
        } else {
            getLogger().warning("MeowEco API not found. Ensure MeowEco plugin is installed and enabled!");
        }
    }

    // ... Other methods ...

    public void exampleUsage() {
        if (meowEcoAPI != null) {
            // Get all registered currencies
            for (Currency currency : meowEcoAPI.getRegisteredCurrencies()) {
                getLogger().info("Currency ID: " + currency.getId() + ", Display Name: " + currency.getDisplayName());
            }

            // Get a specific currency by ID
            Currency coins = meowEcoAPI.getCurrency("coins");
            if (coins != null) {
                getLogger().info("Singular name for Coins: " + coins.getSingular());
            }
        }
    }
}
```

### 4.2 API Interface Definition

```java
package com.xiaoyiluck.meoweco.api;

import com.xiaoyiluck.meoweco.objects.Currency;
import java.util.Collection;

public interface MeowEcoAPI {
    /**
     * Retrieves a collection of all registered currencies in the plugin.
     * @return A read-only {@link Collection} of {@link Currency} objects.
     */
    Collection<Currency> getRegisteredCurrencies();

    /**
     * Gets a specific currency by its unique identifier (ID).
     * @param id The ID of the currency to retrieve.
     * @return The {@link Currency} object, or {@code null} if not found.
     */
    Currency getCurrency(String id);
}
```

---

## 5. FAQ

*   **Q: Why can't a player see their balance after being given Gems?**
    *   A: Ensure the database primary key has been upgraded to a composite key (automatically handled in v26.6.1). Check for `Primary Key fix applied` in the logs.
*   **Q: Are old /money commands still working?**
    *   A: The plugin intercepts and redirects them, but it's highly recommended to switch to `/meco bal` for better compatibility.
*   **Q: How do I show Points on a scoreboard?**
    *   A: Use `%meoweco_balance_points%` or `%meoweco_balance_formatted_points%`.
