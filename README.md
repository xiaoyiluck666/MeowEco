# MeowEco Economy

[![Modrinth 下载](https://img.shields.io/badge/Modrinth_⬇️-下载-E53935?style=flat&logo=modrinth&logoColor=white)](https://modrinth.com/plugin/meoweco/versions)&nbsp;&nbsp;[![GitHub Wiki 文档](https://img.shields.io/badge/GitHub_📚-Wiki_文档-E53935?style=flat&logo=github&logoColor=white)](https://github.com/xiaoyiluck666/MeowEco/wiki)&nbsp;&nbsp;[![GitHub 源码](https://img.shields.io/badge/GitHub_💻-源码-E53935?style=flat&logo=github&logoColor=white)](https://github.com/xiaoyiluck666/MeowEco)&nbsp;&nbsp;[![GitHub 提交 Issue](https://img.shields.io/badge/GitHub_📝-提交_Issue-E53935?style=flat&logo=github&logoColor=white)](https://github.com/xiaoyiluck666/MeowEco/issues/new/choose)

**🧊 Measure inflation. Tune policy. Keep every balance change explainable.**

MeowEco is a Paper-first, multi-currency economy for servers that want an economy they can operate, not just a number they can display. It gives staff a practical loop for keeping progression healthy:

**📊 Measure → ⚙️ Adjust → 🧾 Verify**

- **📊 Measure supply and concentration** with `/meco policy report`: total supply, circulating balance, account count, richest-account share, and Top 10 concentration.
- **⚙️ Adjust the money policy** with per-currency rich tax, transfer tax, exchange rates, and frozen funds for sinks, locked deposits, penalties, and progression gates.
- **🧾 Verify every result** with transaction-level audit history, before/after balances, actor, source, and shared transaction IDs.

You still get everything a modern server economy needs: 💰 coins, points, gems, tokens, and custom currencies; 🏪 Classic Vault and VaultUnlocked v2 compatibility; menu integrations; 📈 leaderboards; 🔄 safe migration; and 🛡️ SQLite/MySQL storage.

The project is focused exclusively on the Paper plugin, with a current runtime target of Paper 26.1.x / 26.2 / 26.3 on Java 25.

Chinese server: set `messages.language: "zh_CN"` in `plugins/MeowEco/config.yml`, then run `/meco reload`.

## Runtime

- Server: Paper 26.1.x / 26.2 / 26.3
- Java: 25
- Optional integrations: Classic Vault, VaultUnlocked v2, PlaceholderAPI, TrMenu via commands/placeholders
- Storage: SQLite by default, MySQL supported

## Build

```powershell
.\gradlew.bat :meoweco-paper:check
.\gradlew.bat :meoweco-paper:shadowJar
```

These commands are for contributors and CI verification. **服务器用户请只从 Modrinth 下载构建好的插件 JAR**：

[⬇️ 下载 MeowEco（Modrinth）](https://modrinth.com/plugin/meoweco/versions)

## Main Features

- 🧊 **Anti-inflation policy loop:** measure supply and wealth concentration, apply targeted tax or fund controls, then compare the next report and audit trail.
- 📉 **Controlled sinks and locked funds:** rich tax can remove or redirect balances above a threshold, while transfer tax and frozen funds shape how money enters, moves through, and leaves the economy.
- 🧾 **Accountability by default:** deposits, withdrawals, payments, exchanges, admin edits, API/Vault writes, taxes, and migrations record who changed what and why.
- 🪙 Multi-currency accounts with custom names, symbols, precision, starting balances, and transfer tax.
- 🔌 Classic Vault bridge for the default currency, plus an optional VaultUnlocked v2 provider with UUID accounts, multiple currencies, transfers, and async operations.
- 🧩 PlaceholderAPI placeholders for balances, frozen balances, total balance, and leaderboard-style displays.
- 🏪 Menu-friendly command flows for shops, VIP pages, exchanges, and reward systems.
- 🧊 Admin tools for give, take, set, freeze, unfreeze, deduct frozen funds, hide/unhide, reload, and debug.
- 📊 SQLite and MySQL storage with indexed balance queries for leaderboard and tax workflows.
- ⚖️ Progressive rich-tax and transfer-tax tiers per currency, applying marginal rates to slow runaway inflation without manual spreadsheet work.
- 🔄 Dry-run migration from live Vault providers, EssentialsX userdata, or CSV with automatic pre-import backups.
- 🧾 Transaction audit history for commands, transfers, exchanges, Vault/API writes, tax cycles, and migrations.

## Policy Loop: Measure → Adjust → Verify

### 📊 Measure

Run `/meco policy report` to inspect each currency's total supply, circulating balance, account count, richest-account share, Top 10 concentration, and active rich-tax settings.

### ⚙️ Adjust

- **Progressive rich tax** removes or redirects balances through configurable marginal wealth brackets.
- **Progressive transfer tax** turns high-volume transfers into a controlled sink with the same bracket model.
- **Frozen funds** reserve deposits, support penalties, and gate progression.
- **Exchange rates** keep currencies independent while defining deliberate conversion paths.
- **Multiple currencies** separate rewards, premium tokens, and event economies.

### 🧾 Verify

Use `/meco audit <player> [currency] [limit]` or `/meco audit export [limit]` to review the result. Audit entries include the operation, source, actor, amount, before/after balances, timestamp, and transaction ID; related sides of a payment or exchange share one transaction ID.

MeowEco does not promise fixed prices or automatic protection from every inflation source. It gives staff the measurements and policy levers to make deliberate changes and verify their effect.

## Compatibility Matrix

| Server | Java | Status |
| --- | --- | --- |
| Paper 26.1.1 | 25 | Compile-tested |
| Paper 26.1.2 | 25 | Compile-tested |
| Paper 26.2 | 25 | Compatibility compile-tested |
| Paper 26.3 | 25 | Primary build target |
| Purpur based on the supported Paper lines | 25 | Expected compatible |

## Commands

| Command | Purpose |
| --- | --- |
| `/meoweco` / `/meco` / `/meow` | Unified command entry |
| `/money [player] [currency]` | Check balance |
| `/money exchange <amount> <from> <to>` | Exchange currencies |
| `/pay <player> <amount> [currency]` | Pay another player |
| `/baltop [currency]` | Show balance leaderboard |
| `/eco <give|take|set> <player> <amount> [currency]` | Admin balance operations |
| `/eco <freeze|unfreeze|deductfrozen> <player> <amount> [currency]` | Admin frozen-fund operations |
| `/eco setrate <from> <to> <rate>` | Update exchange rate |

## Permissions

| Permission | Default | Purpose |
| --- | --- | --- |
| `meoweco.balance` | true | Check own balance |
| `meoweco.balance.other` | op | Check another player's balance |
| `meoweco.pay` | true | Pay players |
| `meoweco.top` | true | View leaderboard |
| `meoweco.take` | op | Use `/take` |
| `meoweco.reload` | op | Reload config and language files |
| `meoweco.eco.give` | op | Admin give |
| `meoweco.eco.take` | op | Admin take |
| `meoweco.eco.set` | op | Admin set |
| `meoweco.eco.freeze` | op | Freeze funds |
| `meoweco.eco.unfreeze` | op | Unfreeze funds |
| `meoweco.eco.deductfrozen` | op | Deduct frozen funds |
| `meoweco.debug` | op | Toggle debug mode |
| `meoweco.admin` | op | Parent admin permission |

## Configuration

Primary config: `meoweco-paper/src/main/resources/config.yml`

Important sections:

- `storage`: choose `sqlite` or `mysql`.
- `default-currency`: currency used by Vault and default command behavior.
- `exchange-rates`: enable/disable and define rates.
- `rich-tax`: configure per-currency periodic tax rules.
- `currencies`: define currency ids, symbols, names, initial balances, and transfer tax.

## Release Notes

See [CHANGELOG.md](./CHANGELOG.md) for release details. Releases are distributed through [Modrinth](https://modrinth.com/plugin/meoweco/versions); GitHub hosts the source code, CI status, and [Wiki documentation](https://github.com/xiaoyiluck666/MeowEco/wiki).
