# MeowEco Changelog

This file now serves as the running changelog for MeowEco.

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
- Added Gradle multi-module skeleton with `meoweco-core`, `meoweco-paper`, and `meoweco-fabric`.
- Added initial Fabric module bootstrap (`fabric.mod.json` + `ModInitializer`) with compatibility declaration `fabricloader >=0.26.1` and `java >=25`.
- Added shared JDBC implementation in core (`JdbcDatabaseManager`) for cross-platform economy storage operations.
- Added Fabric runtime config loader and command support for `meoweco`, `money`, `pay`, `eco`, and `baltop` command paths.
- Added Fabric join-time account bootstrap to auto-create configured currency accounts for players.
- Added shared core economy service (`EconomyService`) to centralize pay/exchange/admin operation rules across platforms.
- Added shared balance/top query models (`BalanceResult`, `TopResult`) in core economy service for cross-platform read paths.
- Added shared total-balance query method in core economy service to unify leaderboard summary reads.
- Added shared rich-tax execution engine in core (`RichTaxEngine`) so tax rules are defined and executed consistently across platforms.
- Added Fabric 26.1-compatible build path using the new non-obfuscated flow (`net.fabricmc.fabric-loom` + direct Minecraft/Fabric dependencies, no Yarn dependency line).
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
- Generated Gradle Wrapper and pinned it to local distribution `file:///E:/fabricmod/meowconsole/gradle-9.3.0-bin.zip`.
- Moved shared domain/API contracts (`Currency`, `DatabaseManager`) from paper module into core module for long-term multi-platform maintainability.
- Updated Fabric build to include Fabric API and YAML parsing dependencies for feature parity command/config flows.
- Wired Paper `PayCommand`, `MoneyCommand(exchange)`, and `EcoCommand` admin operations to use the shared core `EconomyService`.
- Wired Fabric command handlers to use the same shared core `EconomyService` for pay/exchange/admin operations.
- Wired Paper balance query and Fabric balance/top queries to shared core `EconomyService`, reducing duplicated read-path business logic.
- Wired Paper `BaltopCommand` data reads through core `EconomyService` while preserving existing Paper-side cache and output format.
- Updated Fabric `top` output to use shared total-balance read path for consistent summary behavior.
- Wired Paper `RichTaxService` cycle execution to shared core `RichTaxEngine` (keeping existing Paper schedule/config behavior).
- Added Fabric rich-tax scheduling and execution support based on existing `config.yml` (`start-time`, `interval`, per-currency rules, destination mode), executed via shared core `RichTaxEngine`.
- Upgraded Fabric module to target Minecraft `26.1` and Java `25`, including command/runtime API migration from old Yarn-named server classes to the new official class/method names.
- Updated Gradle plugin resolution so `net.fabricmc.fabric-loom` can be resolved from Fabric Maven in this multi-module setup.
- Changed default subproject Java toolchain/release target to `17` so non-Fabric modules compile against the lowest compatibility baseline.
- Removed hardcoded `org.gradle.java.home` lock to local JDK 25 path to keep multi-JDK builds portable.
- Changed Gradle Wrapper `distributionUrl` from machine-local `file:///...` path to official HTTPS distribution URL (`9.3.0`) to improve IDE/CI portability.
- Added workspace VS Code Java/Gradle import settings (`--no-daemon`, single-worker, non-parallel) to reduce local language-server lock contention.
- Changed workspace Java null-analysis mode to `disabled` to suppress Fabric API-related false-positive null safety warnings in VS Code problem panel.
- Changed Fabric Loader setup to use `0.18.5` for development/runtime resolution while keeping mod dependency floor at `>=0.18.4`.
- Changed root Gradle resolution strategy to cache changing/dynamic dependency metadata for 12 hours, reducing repeated snapshot checks during frequent local builds.
- Changed Fabric dependency declarations to use `implementation(...)` for loader/API coordinates in the current Loom setup.
- Verified full multi-module build (`clean build`) for plugin (`meoweco-paper`) and mod (`meoweco-fabric`), and confirmed artifact generation paths for local delivery.

### Fixed

- Fixed Paper build configuration so the plugin no longer compiles Java 17 bytecode while declaring Paper 26.1.2 support.
- Fixed thread-safety risks around update-check command messaging by switching async callbacks back to main-thread message send.
- Replaced non-atomic two-step currency exchange with atomic DB transaction exchange to prevent partial state on failure.
- Synced command exposure/completions for `freeze`, `unfreeze`, and `deductfrozen` paths.
- Fixed offline balance grants/takes/queries sometimes landing on a mismatched UUID, which caused players to see `0` until they received currency again after logging in.
- Avoided ambiguous username-to-UUID fallback when multiple UUID rows share the same player name, preventing old bad rows from being reused after manual cleanup.
- Fixed Fabric module compilation issues caused by UTF-8 BOM in newly created source/resources files.
- Fixed Fabric 26.1 build failures caused by obsolete `remapJar` expectations and old command API imports/method names.
- Fixed build configuration so both `meoweco-fabric` and `meoweco-paper` are explicitly compiled with Java `25`.
- Fixed VS Code `Gradle Language Server` initialization failure caused by invalid Windows path parsing of local `file:///E:/...` wrapper URL.
- Fixed `FabricCommandRegistrar` static analysis warning for unused `java.util.Map` import.
- Fixed `RichTaxService` warning by removing unused `RichTaxSettings.ruleFor(String)` helper.
- Fixed Fabric build failure (`Configuration with name 'modImplementation' not found`) by replacing invalid `modImplementation` usage with `implementation`.

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
