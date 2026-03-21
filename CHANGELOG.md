# MeowEco Changelog

This file now serves as the running changelog for MeowEco.

## Unreleased - 2026-03-13

### Added

- Rich tax now supports strict per-currency settings under `rich-tax.currencies` (`enabled`, `threshold`, `rate`), with unset currencies excluded from taxation.
- Added missing permission nodes to `plugin.yml`: `meoweco.eco.freeze`, `meoweco.eco.unfreeze`, `meoweco.eco.deductfrozen`.
- Added DB-level index bootstrap for frequent leaderboard/tax queries.

### Changed

- Renamed project documentation flow: changelog moved to `CHANGELOG.md`, and `README.md` is now a pointer file.
- Hardened currency/exchange-rate loading and lookup with normalized ids and safer access patterns for reload/runtime use.
- `/eco bal` and `/eco top` now delegate to the shared main command handlers to avoid duplicate command instances and cache divergence.
- Rich tax config structure simplified to per-currency-only mode for multi-currency servers.
- Name-based player resolution now prefers online players, known offline profiles, and stored UUID mappings instead of creating synthetic offline-player UUIDs.

### Fixed

- Fixed thread-safety risks around update-check command messaging by switching async callbacks back to main-thread message send.
- Replaced non-atomic two-step currency exchange with atomic DB transaction exchange to prevent partial state on failure.
- Synced command exposure/completions for `freeze`, `unfreeze`, and `deductfrozen` paths.
- Fixed offline balance grants/takes/queries sometimes landing on a mismatched UUID, which caused players to see `0` until they received currency again after logging in.
- Avoided ambiguous username-to-UUID fallback when multiple UUID rows share the same player name, preventing old bad rows from being reused after manual cleanup.

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
