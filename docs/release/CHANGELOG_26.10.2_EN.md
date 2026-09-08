# MeowEco Economy 26.10.2

This patch hardens currency normalization, frozen-balance invariants, rich-tax filtering, and payment account initialization.

## Added

- Added regression coverage for canonical currency IDs, frozen balances, and rich-tax account filtering.

## Changed

- Payment account initialization now closes the join-time race for online participants.

## Fixed

- Fixed mixed-case API currency operations.
- Fixed balance resets that could drop below frozen funds.
- Excluded hidden and tax accounts from rich-tax collection.
- Prevented self-tax transfers and negative available balances.
