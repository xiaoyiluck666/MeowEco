# MeowEco Economy 26.10.7-mc1.21.11

This LTS compatibility release brings the current MeowEco transaction ledger, audit commands, and policy analytics to Paper 1.21.11 without changing the Java 25 baseline of the modern main line.

## Added

- Added `/meco audit` transaction history and export support for Paper 1.21.11.
- Added `/meco policy report` supply and concentration analytics for Paper 1.21.11.
- Included the shared-MySQL multi-instance consistency fixes from MeowEco 26.10.7.

## Changed

- Targets Paper API 1.21.11 and Java 21.
- Uses `api-version: 1.21.11` so the server validates the intended compatibility baseline.
- Published as a separate LTS artifact while the modern main line remains on Paper 26.x and Java 25.
- Checks for updates only within the Paper 1.21.11 Modrinth release channel.

## Fixed

- Replaced Java 25-only unnamed resource variables with Java 21-compatible code without changing audit behavior.

## Verification

- Compiled the complete plugin against Paper API 1.21.11 using Java 21 bytecode.
- Passed the core economy, SQLite, migration, Classic Vault, and VaultUnlocked v2 regression suites.
- Verified the shaded plugin JAR on a real Paper 1.21.11 server.

## Compatibility

- Paper 1.21.11
- Java 21
- Classic Vault remains supported.
- VaultUnlocked v2 remains available as an optional integration.

## Maintenance Policy

- This branch receives critical bug, security, database correctness, and compatibility fixes.
- New Paper 26.x-only features remain on the modern main branch unless explicitly selected for backporting.
