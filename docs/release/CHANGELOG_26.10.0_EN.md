# MeowEco Economy 26.10.0

This release adds guarded balance migration and a persistent audit trail for Paper 26.1.x / 26.2 servers running Java 25.

## Added

- Dry-run migration from any live Vault economy provider, including common EssentialsX, CMI, XConomy, and The New Economy setups.
- Offline EssentialsX userdata migration and standard CSV migration.
- Automatic CSV backup before every applied migration and a single database transaction for the complete import.
- Persistent transaction audit records with source, actor, transaction ID, amount, before/after balance, and frozen-balance state.
- `/meco audit <player> [currency] [limit]` and `/meco audit export [limit]`.
- Optional bStats integration with storage, currency-count, and language charts after a project id is configured.
- GitHub Actions verification for tests, compatibility compiles, and the release jar.

## Changed

- English is now the default message language. Chinese servers can set `messages.language: "zh_CN"` and run `/meco reload`.
- The marketplace name is now **MeowEco Economy** for clearer search discovery; the plugin id, data directory, commands, API, and placeholders remain `MeowEco` compatible.
- The Modrinth page now starts with positioning, five product benefits, a compatibility table, and a three-minute install path.

## Fixed

- Failed balance mutations and failed migrations do not create audit records.
- Transfer and exchange audit rows share one transaction id across both affected accounts or currencies.
- Modrinth loader metadata no longer claims direct Bukkit or Spigot compatibility for the Paper-only build.
