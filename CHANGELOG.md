# MeowEco Changelog

This file now serves as the running changelog for MeowEco.

## v26.10.0 - 2026-09-01

### Added

- Added dry-run and transactional balance migration from live Vault providers, EssentialsX userdata, and standard CSV files.
- Added automatic pre-migration CSV backups, per-migration transaction IDs, and rollback-compatible CSV input.
- Added persistent transaction auditing for account creation, admin operations, payments, exchanges, Vault/API writes, rich tax, and migrations.
- Added audit history and export commands plus regression coverage for audit atomicity, paired transaction IDs, migration rollback, and CSV parsing.
- Added optional bStats wiring and custom charts for storage backend, configured currency count, and default language.
- Added GitHub Actions CI, a compatibility matrix, marketplace copy, and Chinese/English 26.10.0 release notes.

### Changed

- Changed the default message language from Simplified Chinese to English; documented the one-line `zh_CN` switch for Chinese servers.
- Changed the marketplace display name to `MeowEco Economy` while preserving the internal plugin identity and integration contracts.
- Reworked the Modrinth page around positioning, five benefits, compatibility, and a three-minute install flow.
- Limited new Modrinth release loader metadata to Paper and Purpur for the Paper-only artifact.
- Bumped the plugin version to `26.10.0`.

### Fixed

- Fixed the GitHub Actions Gradle invocation for repositories where the wrapper executable bit is not preserved.
- Kept audit rows in the same database transaction as their balance mutations so failed writes cannot leave false history.
- Kept complete migration batches atomic and rejected invalid rows before any balance was changed.
- Used one transaction ID for both sides of transfers and exchanges.

## Documentation - 2026-08-03

### Added

- Added a reusable English `Support and Feedback` section for the Modrinth project page.

### Changed

- Published the latest English-only Modrinth project introduction from `MeowEco.wiki/WIKI_EN.md`.
- Reformatted the Modrinth GitHub issues link as a descriptive support link instead of a standalone top-line link.
- Updated the Modrinth publishing helper to build the project page body from the English Wiki content.

### Fixed

- None.

## v26.9.1 - 2026-06-29

### Added

- Added Chinese and English `26.9.1` release notes under `docs/release/` with Vault cache performance and safety guidance.
- Added a short Vault default-currency balance read cache to reduce repeated database reads from external Vault consumers.
- Added a Paper 26.1.2 stable API compatibility compile gate alongside the existing earliest 26.1.x guard and Paper 26.2 main compile target.

### Changed

- Bumped the project and generated plugin metadata version to `26.9.1`.
- Invalidated the Vault balance cache after successful Vault, MeowEco API, command, join-time account creation, player quit cleanup, and rich-tax write paths so cached reads cannot outlive money changes.
- Expanded release verification so the supported Modrinth version range is covered by Paper 26.1.x earliest, Paper 26.1.2 stable, and Paper 26.2 compile checks.

### Fixed

- Kept Vault withdrawals, deposits, and account creation on direct database writes so cached reads cannot approve stale deductions or duplicate deposits.
- Removed README trailing whitespace while preserving the emoji-led marketplace introduction layout.

## v26.9.0 - 2026-06-29

### Added

- Added Chinese and English `26.9.0` release notes under `docs/release/` with Paper 26.2, precision, regression-test, and upgrade guidance.
- Added project memory files under `.codex/` documenting the Paper-only product direction and current build commands.
- Added a Paper-focused README with runtime requirements, build commands, features, commands, permissions, and configuration entry points.
- Added self-contained regression coverage for core economy transfers, exchange, frozen-fund operations, and rich-tax execution.
- Added self-contained SQLite regression coverage for account creation, transfers, exchange rollback, frozen funds, and leaderboard exclusions.
- Added shared money amount precision policy and regression coverage for rounded tax/exchange/tax-cycle results.
- Added read-only stored balance precision reporting for legacy databases, including `/meco precision report` and an optional startup warning.

### Changed

- Updated the Paper module's primary API target to `io.papermc.paper:paper-api:26.2.build.40-alpha` while keeping the 26.1.x compatibility compile check.
- Routed `net.kyori` dependencies away from the Aliyun Maven mirrors so Paper 26.2's Adventure 5.x artifacts resolve from Maven Central.
- Updated runtime documentation to list Paper 26.2 alongside the existing Paper 26.1.x support baseline.
- Bumped the project and generated plugin metadata version to `26.9.0`.
- Reworked the README and Wiki introductions with clearer server-owner value messaging and emoji-led feature highlights.
- Updated the Modrinth publishing helper to derive the release version, jar path, changelog path, and Paper API version from the project by default.
- Enabled detailed deprecation linting for the Paper module so future deprecated API usage reports exact source locations.
- Removed the abandoned non-Paper module from the source tree and active Gradle build so routine Paper checks only configure Paper-related modules.
- Removed unused non-Paper platform version properties from `gradle.properties`.
- Expanded `.gitignore` to exclude generated module `bin/` folders, local runtime folders, `.serena/`, and local dependency/runtime artifacts.
- Configured the core module to run its self-contained regression checks as part of `:meoweco-core:test`.
- Configured the Paper module to run SQLite regression checks as part of `:meoweco-paper:test`.
- Extracted small database access hooks in `AbstractSQLDatabase` so SQL behavior can be tested without booting a Paper server.
- Suppressed noisy SQLite test runtime logging/native-access warnings so verification output stays focused.
- Enforced currency decimal precision for command, API, and Vault money inputs while rounding internal tax and exchange outputs for storage.
- Added `precision-migration.report-on-startup` config to scan old over-precision balances without modifying player money.
- Removed stale local IDE `bin/` outputs and obsolete private amount validators after moving validation to `MoneyAmountPolicy`.

### Fixed

- Fixed the Chinese Wiki runtime note that still described Java 17 bytecode after the project moved to Java 25.
- Removed unnecessary deprecation suppressions from `MeowEconomy`; the remaining javac deprecation note is from required Vault legacy-interface overrides.
- Fixed project messaging that still treated another platform as an active release target after the product direction changed to Paper-only.

## v26.8.1 - 2026-05-19

### Added

- Added user-facing Chinese and English release notes for the `26.8.1` release.
- Added a Paper 26.1.x compatibility compile gate that checks the Paper module against the earliest available Paper 26.1.1 API artifact.
- Added configurable MySQL connection timeout, socket timeout, HikariCP pool, and extra driver property settings.

### Changed

- Bumped the project and generated plugin metadata version to `26.8.1`.
- Changed Paper plugin metadata to use Gradle version expansion, reducing the chance of future release version mismatches.
- Updated Wiki version/runtime notes to the `26.8.1` Paper 26.1.x / Java 25 baseline.
- Lowered the plugin API declaration to `api-version: '26.1'` while keeping the primary compile target on Paper 26.1.2.
- Bundled and relocated MySQL Connector/J in the Paper artifact so MySQL storage works without manually installing the driver on the server.
- Updated MySQL JDBC defaults to use `utf8mb4`, public-key retrieval compatibility, and explicit network timeouts.

### Fixed

- Fixed stale documentation references that still described older Paper 26.1.1 / Java 17 compatibility behavior.

## Unreleased - 2026-03-13

### Added

- Rich tax now supports strict per-currency settings under `rich-tax.currencies` (`enabled`, `threshold`, `rate`), with unset currencies excluded from taxation.
- Added missing permission nodes to `plugin.yml`: `meoweco.eco.freeze`, `meoweco.eco.unfreeze`, `meoweco.eco.deductfrozen`.
- Added DB-level index bootstrap for frequent leaderboard/tax queries.
- Added Gradle multi-module skeleton with `meoweco-core` and `meoweco-paper`.
- Added shared JDBC implementation in core (`JdbcDatabaseManager`) for cross-platform economy storage operations.
- Added shared core economy service (`EconomyService`) to centralize pay/exchange/admin operation rules across platforms.
- Added shared balance/top query models (`BalanceResult`, `TopResult`) in core economy service for cross-platform read paths.
- Added shared total-balance query method in core economy service to unify leaderboard summary reads.
- Added shared rich-tax execution engine in core (`RichTaxEngine`) so tax rules are defined and executed consistently across platforms.
- Added China-friendly Gradle mirror repositories (Aliyun public/central/plugin) and `mavenLocal()` priority in `settings.gradle.kts` to reduce repeated remote downloads.

### Changed

- Renamed project documentation flow: changelog moved to `CHANGELOG.md`, and `README.md` is now a pointer file.
- Updated the Paper plugin build to compile and emit Java 25 bytecode, matching Paper 26.1+ runtime requirements.
- Updated the Paper API dependency to `io.papermc.paper:paper-api:26.1.2.build.64-stable`.
- Removed the legacy Paper 1.20.4 compatibility compile check so the Paper plugin module can use the Paper 26.1.2 API as its primary support baseline.
- Updated the Paper module to target Paper API `26.1.2.build.64-stable` and declare `api-version: '26.1.2'` so Paper 26.1.2 loads MeowEco with the matching API compatibility level.
- Changed VS Code Java Gradle import arguments to disable Gradle parallelism and configuration cache during IDE sync, avoiding stale Buildship init-script/cache failures after Java extension updates.
- Changed `meoweco-paper` to use Paper 26.1.2 and Java 25 as the active plugin runtime baseline.
- Hardened currency/exchange-rate loading and lookup with normalized ids and safer access patterns for reload/runtime use.
- `/eco bal` and `/eco top` now delegate to the shared main command handlers to avoid duplicate command instances and cache divergence.
- Rich tax config structure simplified to per-currency-only mode for multi-currency servers.
- Name-based player resolution now prefers online players, known offline profiles, and stored UUID mappings instead of creating synthetic offline-player UUIDs.
- Migrated existing Paper source tree into `meoweco-paper` module without changing runtime behavior.
- Switched build setup to Java 25 toolchain/release for all subprojects.
- Moved shared domain/API contracts (`Currency`, `DatabaseManager`) from paper module into core module for long-term multi-platform maintainability.
- Wired Paper `PayCommand`, `MoneyCommand(exchange)`, and `EcoCommand` admin operations to use the shared core `EconomyService`.
- Wired Paper balance query paths to shared core `EconomyService`, reducing duplicated read-path business logic.
- Wired Paper `BaltopCommand` data reads through core `EconomyService` while preserving existing Paper-side cache and output format.
- Wired Paper `RichTaxService` cycle execution to shared core `RichTaxEngine` (keeping existing Paper schedule/config behavior).
- Removed hardcoded `org.gradle.java.home` lock to local JDK 25 path to keep multi-JDK builds portable.
- Changed Gradle Wrapper `distributionUrl` from machine-local `file:///...` path to official HTTPS distribution URL (`9.3.0`) to improve IDE/CI portability.
- Added workspace VS Code Java/Gradle import settings (`--no-daemon`, single-worker, non-parallel) to reduce local language-server lock contention.
- Changed root Gradle resolution strategy to cache changing/dynamic dependency metadata for 12 hours, reducing repeated snapshot checks during frequent local builds.
- Verified full Paper build and confirmed artifact generation paths for local delivery.

### Fixed

- Fixed Paper build configuration so the plugin no longer compiles Java 17 bytecode while declaring Paper 26.1.2 support.
- Fixed thread-safety risks around update-check command messaging by switching async callbacks back to main-thread message send.
- Replaced non-atomic two-step currency exchange with atomic DB transaction exchange to prevent partial state on failure.
- Synced command exposure/completions for `freeze`, `unfreeze`, and `deductfrozen` paths.
- Fixed offline balance grants/takes/queries sometimes landing on a mismatched UUID, which caused players to see `0` until they received currency again after logging in.
- Avoided ambiguous username-to-UUID fallback when multiple UUID rows share the same player name, preventing old bad rows from being reused after manual cleanup.
- Fixed VS Code `Gradle Language Server` initialization failure caused by invalid Windows path parsing of local `file:///E:/...` wrapper URL.
- Fixed `RichTaxService` warning by removing unused `RichTaxSettings.ruleFor(String)` helper.

## v26.7.3 - 2026-04-04

### Added

- Added a legacy Paper API compatibility compile check to `:meoweco-paper:check`, so the same source set is validated against both the current `26.1.1` line and the older supported Paper API baseline.
- Added wiki documentation for TrMenu integration, including short-action examples and command-based menu examples for giving or taking MeowEco currencies.

### Changed

- Bumped the plugin version to `26.7.3`.
- Updated the Paper module build to compile against `io.papermc.paper:paper-api:26.1.1.build.14-alpha` while still emitting Java 17 bytecode for runtime compatibility on older supported Paper servers.
- Updated the plugin description to target generic Paper servers instead of a hardcoded version range.
- Updated the CN/EN wiki runtime notes to reflect the current `26.7.3` build and Paper 26.1.1 compatibility strategy.

### Fixed

- Fixed Paper `26.1.1` build resolution by compiling the `meoweco-paper` module with a JDK 25 toolchain, which is now required by the upstream Paper API metadata.
- Fixed a future compatibility risk by preventing accidental usage of Paper `26.x`-only APIs from silently breaking older supported Paper servers.

## v26.7.4 - 2026-04-08

### Added

- Added a focused follow-up release for command safety and permission behavior fixes after the `26.7.3` Paper compatibility update.

### Changed

- Bumped the plugin version to `26.7.4`.
- Refreshed the CN/EN wiki presentation with clearer section styling and emoji-based visual grouping, while keeping the technical content aligned with the current `26.7.4` behavior.
- Rewrote the CN/EN wiki structure to highlight MeowEco's core strengths first, including rich tax, exchange-rate support, formatted display output, TrMenu-friendly integration, runtime efficiency, and Paper compatibility strategy.
- Refined the CN/EN wiki messaging so server owners can more quickly understand formatting benefits for inflated economies, rich-tax balancing use cases, custom currency flexibility, and practical Paper/Vault/PlaceholderAPI/TrMenu adoption.
- Added concrete CN/EN wiki examples for balance formatting modes such as `raw`, `formatted`, `fixed`, and `short`.
- Adjusted the CN/EN wiki section order so the balance-formatting section leads directly into the TrMenu section, and renamed the formatting heading to focus more clearly on balance display.

### Fixed

- Fixed offline-player name resolution to stop falling back to `Bukkit#getOfflinePlayer(String)`, preventing admin economy commands from creating or targeting synthetic UUID accounts for players who do not actually exist.
- Fixed `/eco` permission handling so `give`、`take`、`set`、`freeze`、`unfreeze`、`deductfrozen` can rely on their dedicated permission nodes instead of being blocked by an unconditional `meoweco.admin` gate.
- Kept `setrate`、`refresh`、`hide`、`unhide` on the stricter `meoweco.admin` path, preserving the expected admin-only behavior for high-impact maintenance operations.

## v26.8.1 - 2026-05-30

### Added

- Added the `26.8.1` Modrinth release package for the current Paper plugin build so the hosted download matches the latest repository version.

### Changed

- Updated the Modrinth project summary and long-form description to reflect the current `Paper 26.1 / 26.1.1 / 26.1.2` and `Java 25` support baseline.
- Published the `26.8.1` release notes to Modrinth using the current MySQL, Paper compatibility, and runtime guidance from the repository docs.
- Added a local `scripts/publish-modrinth.mjs` helper that reads the Modrinth PAT at runtime and publishes the release through the official API flow.
- Published Modrinth version `26.8.1` with version ID `LYlYyKTM` and refreshed the project page through the Modrinth API.

### Fixed

- Fixed the release distribution mismatch where Modrinth was still advertising the older `26.7.4` build and outdated project description after the repository had already moved to `26.8.1`.

## v26.7.2 - 2026-03-12

### Added

- Added shared offline-player lookup support for admin commands and rich-tax player destinations so offline accounts can be resolved by UUID, cached profile, database name mapping, or final server lookup.
- Added explicit `meoweco.eco.freeze`, `meoweco.eco.unfreeze`, and `meoweco.eco.deductfrozen` permission declarations to `plugin.yml`.

### Changed

- Optimized `/eco bal` and `/eco top` alias handling to delegate to the main `meoweco` command handlers, keeping command behavior and caches consistent.
- Improved rich-tax configuration to use per-currency rules and clearer scheduling log output.
- Updated the project documentation and wiki content to match the current feature set.

### Fixed

- Fixed `/eco give` failing for offline players when the target was not already present in the immediate Bukkit offline-player cache.
- Fixed rich-tax destination-player resolution to reuse the same offline-player lookup path as admin commands.
- Fixed the stale `org.bukkit.Bukkit` import warning in `RichTaxService`.

## Format For Future Updates

Use one section per release:

```md
## vX.Y.Z - YYYY-MM-DD

### Added
- ...

### Changed
- ...

### Fixed
- ...
```
