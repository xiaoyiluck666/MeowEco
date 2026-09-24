# MeowEco Economy 26.10.6

This release adds verified Paper 26.3 support while retaining compatibility with the existing Paper 26.1.x and 26.2 server lines.

## Added

- Added Paper 26.3 build 38 as the primary compile target.
- Added a dedicated Paper 26.2 stable compatibility check alongside the existing Paper 26.1.x checks.

## Changed

- Updated the supported server range to Paper 26.1.x, 26.2, and 26.3 on Java 25.
- Updated the README, Modrinth project description, and release metadata for Paper 26.3.

## Fixed

- Prevented future Paper 26.3 API changes from silently breaking Paper 26.2 compatibility by compiling the complete plugin source against both API lines during every release check.

## Verification

- Compiled successfully against Paper 26.1.1, 26.1.2, 26.2, and 26.3 APIs.
- Passed all database, migration, and VaultUnlocked v2 regression tests.
- Started and stopped successfully on a real Paper 26.3 build 38 server with SQLite initialization and clean database shutdown.

## Compatibility

- Paper 26.1.x / 26.2 / 26.3
- Java 25
- Classic Vault remains supported.
- VaultUnlocked v2 remains available as an optional integration.

## Download

- Download the compiled plugin JAR from [Modrinth versions](https://modrinth.com/plugin/meoweco/versions).
- GitHub Release contains release notes only and intentionally has no build-file attachments.
