# MeowEco 26.8.1 Changelog

Release date: 2026-05-19

## 🚀 Highlights

MeowEco `26.8.1` targets **Paper 26.1 / 26.1.1 / 26.1.2** servers running **Java 25**. This release focuses on smoother use on newer Paper environments and a better out-of-the-box MySQL storage experience.

## ✨ Added

- 🗄️ MySQL storage now ships with the Paper plugin, so server owners no longer need to install the MySQL driver separately.
- ⚙️ Added MySQL pool settings for tuning connection count, timeouts, and connection lifetime.

## 🔄 Changed

- ☕ Bumped the plugin version to `26.8.1`.
- 🛡️ Clarified support for `Paper 26.1`, `Paper 26.1.1`, and `Paper 26.1.2`, all running on Java 25.
- 🔌 Updated MySQL defaults to use `utf8mb4` and improved compatibility with common MySQL 8 authentication and timeout scenarios.
- 📚 Updated documentation for the current version, supported Paper range, and Java runtime requirement.

## 🛠️ Fixes And Improvements

- 🧹 Removed outdated Java 17 compatibility wording from user-facing documentation to avoid upgrade confusion.
- 🛠️ Improved MySQL connection stability for charset, authentication, and slow-network situations.

## ⚠️ Upgrade Notes

- Your server must run on **Java 25**.
- This release targets **Paper 26.1 / 26.1.1 / 26.1.2**. If your server is still on Paper / Minecraft `1.21.11` or older, do not assume this version is compatible.
- Back up your database and configuration files before upgrading, especially on production economy servers.
