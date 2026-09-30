# MeowEco Economy 26.10.7

This release adds reliable balance consistency for networks running multiple Paper servers against one shared MySQL database.

## Added

- Added atomic balance snapshots so balance and frozen funds are read together from the shared database.
- Added real MySQL regression coverage using two independent plugin database instances.

## Changed

- Classic Vault balance reads now use MySQL as the source of truth instead of a process-local read cache.
- Multi-instance shared-MySQL deployments are now explicitly supported and documented.

## Fixed

- Fixed stale Vault balances when one Paper server changed an account that another server had recently read.
- Fixed a MySQL concurrency edge case that could roll back one of two valid simultaneous deposits.
- Added deterministic account lock ordering to prevent deadlocks during opposing transfers while preserving total balances.

## Verification

- Passed 25 rounds each of concurrent withdrawals, concurrent deposits, and opposing transfers through two independent connection pools on MySQL 8.4.
- Verified that committed balance and frozen-fund changes are immediately visible to the other instance.
- Passed the complete Gradle check suite, including SQLite, migration, Classic Vault, VaultUnlocked v2, and Paper compatibility checks.

## Compatibility

- Paper 26.1.x / 26.2 / 26.3
- Java 25
- Classic Vault remains supported.
- VaultUnlocked v2 remains available as an optional integration.

## Download

- Download the compiled plugin JAR from [Modrinth versions](https://modrinth.com/plugin/meoweco/versions).
- GitHub hosts the source code and issue tracker.
