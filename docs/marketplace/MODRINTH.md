# MeowEco Economy

**Move your economy without wiping player balances. Know where every unit of currency went.**

MeowEco is a Paper-first, multi-currency economy for live servers that need more control than a basic balance provider. Create coins, points, gems, or event tokens; migrate from your current economy with a dry run and automatic backup; then investigate every balance change with transaction-level audit history.

[Download the latest release](https://modrinth.com/plugin/meoweco/versions) | [Read the full guide](https://github.com/xiaoyiluck666/MeowEco/wiki) | [Plan a migration](https://github.com/xiaoyiluck666/MeowEco/wiki/WIKI_EN#migrating-from-another-economy)

> Start with SQLite and no required economy bridge. Add Vault, PlaceholderAPI, or MySQL only when your server needs them.

## Why Server Owners Choose MeowEco

- **Keep players when you switch** - preview and import balances from any live Vault economy, EssentialsX userdata, or CSV instead of asking your community to start over.
- **Resolve balance disputes with evidence** - inspect deposits, withdrawals, payments, exchanges, admin edits, Vault/API writes, taxes, and migrations with before/after balances and shared transaction IDs.
- **Run more than one progression loop** - give coins, points, gems, and tokens independent names, precision, starting balances, transfer tax, and exchange rates.
- **Control inflation and reserved money** - schedule rich tax per currency, and freeze, unfreeze, or deduct locked funds for deposits, penalties, auctions, and custom gameplay.
- **Keep your current plugin stack** - connect Vault shops, PlaceholderAPI scoreboards, TrMenu flows, and custom plugins through a documented API.

## Compatibility

| Component | Supported |
| --- | --- |
| Server | Paper 26.1.x and 26.2; compatible Paper forks such as Purpur |
| Java | Java 25 |
| Storage | SQLite out of the box; MySQL optional |
| Integrations | Vault, PlaceholderAPI, TrMenu through commands/placeholders |
| Migration sources | Any Vault economy provider, EssentialsX userdata, standard CSV |
| Languages | English by default; bundled Simplified Chinese |

No client mod is required. The current download is a server-side Paper plugin.

## Install In 3 Minutes

1. Put the MeowEco JAR in `plugins/`, then start the server once.
2. Edit `plugins/MeowEco/config.yml` to name your currencies. SQLite works immediately; MySQL is optional.
3. Run `/meco reload`, then verify the setup with `/meco bal` and `/meco debug currencies`.

Vault and PlaceholderAPI are optional. Install them before startup when you want existing shop compatibility or placeholders.

Chinese server? Set the following value, save the file, and run `/meco reload`:

```yaml
messages:
  language: "zh_CN"
```

## Switch Without Guesswork

Migration commands are dry-runs until you add `--apply`. Review the detected account count, total balance, and skipped rows before changing live data.

```text
/meco migrate sources
/meco migrate vault coins Essentials
/meco migrate vault coins Essentials --apply
/meco migrate essentials coins --apply
/meco migrate csv coins balances.csv --apply
```

Applied migrations set the imported balance in the target currency and create a rollback-compatible CSV under `plugins/MeowEco/migration-backups/`. Keep a full database backup for production migrations.

## Find Out Where The Money Went

When a player reports a missing payment or an admin needs to investigate inflation, query or export the persistent audit trail:

```text
/meco audit <player> [currency] [limit]
/meco audit export [limit]
```

Audit records include the operation, source, actor, currency, amount, before/after balances, timestamp, and transaction ID. Related sides of a payment or exchange share the same transaction ID.

## Run A Measurable Money Policy

Use `/meco policy report` as a read-only checkpoint before changing tax or sink settings. It shows each currency's total supply, circulating balance (excluding frozen funds), account count, and the share held by the richest account and Top 10 accounts, followed by the active rich-tax rules.

This turns “fight inflation” into an operating loop: measure supply and concentration, adjust a policy lever, then compare the next report and audit entries. Rich tax removes or redirects the amount above each threshold; it does not rewrite prices or guarantee a fixed value by itself.

## A Practical Fit For

- Survival servers with a main currency and premium or event tokens
- RPG progression with separate reputation, guild, or activity currencies
- Shops and menus that already use Vault or PlaceholderAPI
- Servers moving away from an old economy without resetting players
- Staff teams that need evidence for balance disputes and admin actions

## Documentation And Support

- [English and Chinese Wiki](https://github.com/xiaoyiluck666/MeowEco/wiki)
- [Commands, permissions, configuration, and integrations](https://github.com/xiaoyiluck666/MeowEco/wiki/WIKI_EN)
- [Source code and CI](https://github.com/xiaoyiluck666/MeowEco)
- [Issues and feature requests](https://github.com/xiaoyiluck666/MeowEco/issues)

**Ready to keep your economy and gain control of it? [Download MeowEco Economy](https://modrinth.com/plugin/meoweco/versions).**
