# MeowEco 26.9.1 Changelog

Release date: 2026-06-29

## 🚀 Highlights

MeowEco `26.9.1` is a performance maintenance release for **Paper 26.1.x / 26.2** servers. It focuses on reducing Vault default-currency balance read pressure.

Many shop, menu, permission, quest, and display plugins query balances through Vault very frequently. This release adds a very short read cache for Vault default-currency balance lookups, reducing repeated database reads while keeping all money-changing operations database-authoritative.

## ✨ Added

- ⚡ Added a short Vault default-currency balance read cache for frequent `getBalance` / `has` calls from external Vault consumers.
- 🧾 Added 26.9.1 release notes explaining the cache strategy, intended use, and safety boundaries.
- 🧹 Player quit now clears that player's Vault cache entries to avoid stale cache growth on long-running servers.
- 🧪 Added a Paper 26.1.2 stable API compatibility compile gate alongside the existing earliest 26.1.x guard and Paper 26.2 main compile target.

## 🔄 Changed

- ☕ Bumped the plugin version to `26.9.1`.
- 🔁 Successful Vault, MeowEco API, admin command, pay/exchange, join-time account creation, and rich-tax write paths now invalidate the related Vault balance cache.
- 🧠 Player-level cache invalidation now removes every Vault cache key for that player instead of relying on the current default-currency setting.
- 📦 Release verification now covers the earliest Paper 26.1.x API, Paper 26.1.2 stable API, and the Paper 26.2 main target API.

## 🛠️ Fixes And Improvements

- 🛡️ The cache is read-only and only applies to Vault default-currency reads; deposits, withdrawals, and account creation still go directly to the database.
- 🔒 Vault withdrawals remain database-authoritative, so insufficient funds are rejected by the atomic database update instead of by cached reads.
- 🧽 Cleaned README trailing whitespace while preserving the emoji-led marketplace introduction layout.

## 🌟 Why Upgrade

- 🏪 Lower database pressure when shop, menu, VIP, and reward plugins repeatedly check balances.
- 📊 Lighter balance displays, requirement checks, and menu refreshes.
- 🔌 Better Vault behavior for high-interaction servers.
- 🛡️ Performance gains without weakening economy safety.

## ⚠️ Upgrade Notes

- Your server must run on **Java 25**.
- This release targets **Paper 26.1.x / 26.2**.
- Back up your database and configuration files before upgrading, especially on production economy servers.
- No extra configuration migration is required when upgrading from 26.9.0.
