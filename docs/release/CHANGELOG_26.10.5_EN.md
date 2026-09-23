# MeowEco Economy 26.10.5

This release adds optional VaultUnlocked v2 support while preserving the existing Classic Vault integration.

## Added

- Added a VaultUnlocked v2 economy provider with UUID accounts, multiple currencies, transfers, exact amount validation, and asynchronous operations.
- Added regression coverage for Classic Vault and VaultUnlocked v2 interoperability, precision boundaries, progressive transfer tax, rollback, concurrency, and async shutdown.
- Added a direct GitHub Issue button to the project header.

## Changed

- Classic Vault and VaultUnlocked v2 balance mutations now share the same economy service, database transaction path, and audit metadata.
- Updated the README, Modrinth project description, and plugin metadata to document the new compatibility.
- Kept VaultUnlocked v2 optional; servers without its API continue using Classic Vault as before.

## Fixed

- Rejected v2 amounts that cannot be represented at the configured currency precision or by the existing double-backed storage.
- Coordinated asynchronous v2 operations with plugin shutdown and deferred database close when a running operation needs more time to finish.

## Compatibility

- Paper 26.1.x / 26.2
- Java 25
- Classic Vault remains supported.
- VaultUnlocked v2 is supported as an optional integration.

## Download

- Download the compiled plugin JAR from [Modrinth versions](https://modrinth.com/plugin/meoweco/versions).
- GitHub Release contains release notes only and intentionally has no build-file attachments.
