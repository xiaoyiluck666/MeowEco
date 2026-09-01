# MeowEco 26.9.0 Changelog

Release date: 2026-06-29

## 🚀 Highlights

MeowEco `26.9.0` targets **Paper 26.1.x / 26.2** servers running **Java 25**. This release moves the primary build target to Paper 26.2 while keeping the Paper 26.1.x compatibility compile gate, so server owners can adopt the newer Paper line with a clearer safety net.

This is also a stability-focused economy release: money precision, database regression coverage, SQLite behavior, public documentation, and release metadata were all tightened for multi-currency servers, shop menus, leaderboards, rich-tax setups, and integrated server workflows.

## ✨ Added

- 🛡️ Added primary Paper 26.2 API support with `paper-api:26.2.build.40-alpha`.
- 🧪 Added broader economy regression coverage for transfers, exchange, frozen funds, rich tax, and amount precision policy.
- 🗄️ Added SQLite regression coverage for account creation, transfers, exchange rollback, frozen funds, and leaderboard exclusions.
- 🔍 Added read-only precision reporting for legacy balances, helping server owners find over-precise stored amounts without modifying player money.

## 🔄 Changed

- ☕ Bumped the plugin version to `26.9.0`.
- 🧱 Updated the supported runtime range to **Paper 26.1.x / 26.2** with **Java 25**.
- 📦 Kept the Paper 26.1.x compatibility compile check while compiling primarily against Paper 26.2.
- 🔌 Adjusted Maven resolution so Paper 26.2's Adventure 5.x dependencies resolve from Maven Central.
- 📚 Improved README and Wiki copy so server owners can quickly understand where MeowEco fits and why it is useful.

## 🛠️ Fixes And Improvements

- 💰 Unified amount precision handling across commands, API calls, and Vault boundaries.
- 🧾 Rounded tax, exchange, and scheduled economy results before storage for cleaner database values.
- 🧹 Removed stale non-Paper project messaging and kept the project direction focused on the Paper plugin.
- ⚙️ Cleaned up generated-file and local-runtime ignores to keep the repository easier to maintain.

## 🌟 Best For

- 🪙 Servers running coins, points, gems, tokens, or other multi-currency economies.
- 🏪 Shop, VIP, reward, and exchange menus built around Vault, PlaceholderAPI, and TrMenu.
- 📊 Servers that care about leaderboards, formatted balances, and total-economy displays.
- 🧊 RPG and survival servers that need frozen funds, rich tax, and economy-control tools.
- 🛡️ Server owners preparing for Paper 26.2 while keeping Paper 26.1.x compatibility checks in place.

## ⚠️ Upgrade Notes

- Your server must run on **Java 25**.
- This release targets **Paper 26.1.x / 26.2**. If your server is still on Paper / Minecraft `1.21.11` or older, do not assume this version is compatible.
- Back up your database and configuration files before upgrading, especially on production economy servers.
- If you use MySQL, validate connection settings, charset, and permissions on a test server first.
