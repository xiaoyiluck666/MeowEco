# MeowEco Economy 26.11.0

Release date: 2026-11-13

A feature release from `26.10.7` with tracked starting funds, optional signed-identity grants, player-trade debits and persistent refund receipts. It also fixes 26.x servers being offered the Minecraft 1.21.11 backport. The new features and APIs warrant the minor version increment to `26.11.0`.

## Added

- **Starting-fund source ledger:** tracks the unspent part of initial grants alongside the total balance. Balance, source labels and source audit commit or roll back together.
- **Reloadable grant policy:** `initial-funds.allow-player-transfer` defaults to `false`; `spending-order` defaults to `GIFT_FIRST` and also supports `EARNED_FIRST`. Apply changes with `/meco reload`.
- **Optional signed-identity grants:** configure an offline base amount and premium total for new wallets through a MeowIdentity signed-session bridge and PlaceholderAPI. Pay the base first, then supplement a verified premium session once. The example is 100/1000; this integration is disabled by default and its bridge plugins are not bundled.
- **Player-trade API:** `getInitialGiftBalance`, `getTransferableBalance` and atomic `withdrawForPlayerTrade` for shops that need to enforce eligible funding sources.
- **Persistent payment receipts:** `debitWithReceipt`, `getReceiptState` and `refundReceipt`. Repeated debit references do not charge twice; a refund returns money to the original wallet once, restoring its original grant labels. Conflicting reuse of a reference fails.
- **Regression coverage:** delayed identity resolution, one-time supplements, spending orders, transfers, exchanges, frozen/admin/import debits, audit rollback, duplicate receipts, independent instances/restarts, update selection and Vault async cleanup.

## Changed

- Track newly qualifying configured initial balances and future premium supplements. Existing balances with unknown sources are not relabeled or reset.
- By default, grants remain available for ordinary personal spending but cannot be sent through `/pay`. Permitted direct transfers carry their grant labels to the recipient; currency exchanges preserve labels proportionally.
- Frozen funds conservatively reduce earned funds eligible for transfer. Ordinary withdrawals, admin debits and migration reductions follow the spending order.
- Generic Vault withdrawals retain ordinary spending behavior. **Vault does not identify the counterparty or payment purpose: third-party player shops must explicitly integrate the player-trade API to enforce grant restrictions. A plain Vault deposit refund is treated as earned money.**
- Filter update requests and responses by the running Minecraft version and Paper loader, selecting the highest compatible plugin version instead of the first upload.
- Updated the project introduction, publishing summary and bilingual release notes; the JAR now includes the original MIT license.
- Retained inflation reports, progressive rich/transfer taxes, multi-currency accounts, Classic Vault, optional VaultUnlocked v2, migration and SQL storage. Existing tax rates are not changed.

## Fixed

- Fixed 26.x servers receiving an update notice for the Minecraft 1.21.11-only `26.10.7.1-mc1.21.11` build.
- Fixed dotted numeric version comparison without folding `+maowo.2` build metadata or `-mc1.21.11` target suffixes into release numbers.
- Prevented duplicate identity supplements across reconnects, restarts and concurrent instances without replacing spending, earnings or frozen funds accumulated in the meantime.
- Prevented exchanges and permitted direct transfers from shedding grant restrictions; prevented duplicate refunds, conflicting references and source-audit failures from producing invalid balances.
- Added English and Chinese `/pay` feedback when available funds exist but their grant source prevents a transfer.
- Fixed nullability diagnostics in commands, player lookups, notifications, formatting, migration and tests; completed Vault async futures and cleanup consistently when a supplier returns null or throws.

## Upgrade and rollback

- Back up the database and configuration first. Requires Java 25 and Paper 26.1 / 26.1.1 / 26.1.2 / 26.2 / 26.3. Purpur compatibility follows the corresponding Paper line.
- Adds separate grant, source-audit and payment-receipt tables without rewriting historical wallet balances. Existing wallets are not retroactively granted money. Pending identity grants retain their original configured amounts.
- With identity grants disabled, currencies still use their existing `initial-balance`. Newly qualifying initial funds are tracked and follow the default grant-transfer restriction.
- To relax grant restrictions, change the policy on this release. Older JARs do not maintain labels or receipts; transactions during a downgrade require reconciliation before re-enabling restrictions. Do not overwrite later player transactions with an old database or remove receipts that provide duplicate protection.

## Verification

- Complete Gradle build covering core economy, SQLite, migration, Classic Vault, VaultUnlocked v2 and update-checker regressions.
- Compatibility compilation against Paper 26.1.1, 26.1.2 and 26.2; primary build against Paper 26.3.
- A live Modrinth request filtered for 26.2 / Paper excluded the 1.21.11 branch; regression fixtures cover multiple supported 26.x versions.
- New source-ledger and receipt behavior was verified with real SQLite databases. This release does not add real MySQL integration verification for those new features; Purpur was not separately runtime-tested.

## Download

- Compiled plugin JAR: [Modrinth versions](https://modrinth.com/plugin/meoweco/versions)
- Source code and issue tracker: [GitHub](https://github.com/xiaoyiluck666/MeowEco)
