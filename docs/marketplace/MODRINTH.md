# MeowEco Economy

**A controllable multi-currency economy for long-running Paper servers.** Run coins, points, gems, or tokens while keeping inflation, locked funds, exchanges, and every balance change visible and auditable.

## Why MeowEco

- **Migrate without resetting players** - preview and import balances from any live Vault economy, EssentialsX userdata, or CSV. Every applied migration creates a rollback CSV first.
- **Audit every money change** - trace deposits, withdrawals, transfers, exchanges, admin actions, Vault/API writes, rich tax, and migrations with before/after balances and shared transaction IDs.
- **Build a real multi-currency economy** - configure independent names, precision, starting balances, transfer tax, and exchange rates for each currency.
- **Control inflation and locked funds** - schedule rich tax per currency and freeze, unfreeze, or deduct reserved balances for deposits, penalties, and custom gameplay.
- **Connect the plugins you already use** - Vault, PlaceholderAPI, SQLite/MySQL, and command/placeholder flows designed for shops, scoreboards, and TrMenu.

## Compatibility

| Component | Supported |
| --- | --- |
| Server | Paper 26.1.x and 26.2; compatible Paper forks such as Purpur |
| Java | Java 25 |
| Storage | SQLite (default), MySQL |
| Integrations | Vault, PlaceholderAPI, TrMenu through commands/placeholders |
| Migration sources | Any Vault economy provider, EssentialsX userdata, standard CSV |
| Languages | English by default; bundled Simplified Chinese |

## Install In 3 Minutes

1. Put `MeowEco` and optional `Vault` / `PlaceholderAPI` jars in `plugins/`, then start the server once.
2. Edit `plugins/MeowEco/config.yml` to name your currencies and choose SQLite or MySQL.
3. Run `/meco reload`, then verify with `/meco bal` and `/meco debug currencies`.

Chinese server? Set `messages.language: "zh_CN"` in `plugins/MeowEco/config.yml`, then run `/meco reload`.

## Move From Another Economy

All migration commands are dry-runs unless `--apply` is present:

```text
/meco migrate sources
/meco migrate vault coins Essentials --apply
/meco migrate essentials coins --apply
/meco migrate csv coins balances.csv --apply
```

CSV files go in `plugins/MeowEco/migration-input/`. Applied migrations create backups under `plugins/MeowEco/migration-backups/`.

Inspect or export transaction history:

```text
/meco audit <player> [currency] [limit]
/meco audit export [limit]
```

## Documentation And Support

- [Full documentation](https://github.com/xiaoyiluck666/MeowEco/wiki)
- [Source code](https://github.com/xiaoyiluck666/MeowEco)
- [Issues and feature requests](https://github.com/xiaoyiluck666/MeowEco/issues)
