# MeowEco Economy 26.10.1

This patch makes the monetary policy report fully localizable and publishes the report implementation that was documented in 26.10.0 planning materials.

## Added

- Added customizable English and Simplified Chinese templates for `/meco policy report`.
- Added localized labels for rich-tax status and destinations.

## Changed

- The report follows `messages.language` and reloads custom text from `plugins/MeowEco/lang/` after `/meco reload`.
- Bumped the release version to `26.10.1`.

## Fixed

- Removed hard-coded English labels from the monetary policy report output.
