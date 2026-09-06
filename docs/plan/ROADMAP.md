# MeowEco Policy-First Roadmap

Updated: 2026-09-06

## Product thesis

MeowEco's core advantage is not simply having multiple currencies. It is giving a server owner the tools to run a small, transparent monetary policy:

- create and remove currency with deliberate sinks and faucets;
- slow wealth concentration with configurable rich tax and transfer tax;
- protect reserved funds with freeze, unfreeze, and frozen deductions;
- inspect supply, concentration, and every balance mutation through audit data;
- tune policy from measured server behavior instead of guessing.

The product promise should be **policy control and monetary stability for long-running Paper servers**. Avoid promising that a configuration can guarantee a fixed price level: player activity, item supply, and shop pricing remain external factors. MeowEco can make the money supply and policy levers visible, adjustable, and testable.

## What is already shipped

- Per-currency rich-tax threshold, rate, schedule, and system/player destination.
- Per-currency transfer tax, exchange rates, decimal precision, and starting balances.
- Frozen balances for deposits, penalties, reservations, and custom gameplay.
- Persistent audit rows with operation, source, actor, transaction ID, amount, and before/after balances.
- Dry-run, transactional migration from Vault providers, EssentialsX userdata, and CSV, with pre-import backups.
- SQLite by default, optional MySQL, Vault and PlaceholderAPI integration.

## Next release: policy safety and observability

Target this before adding new platform support or a large GUI.

1. Add a read-only monetary report command, for example `/meco policy report`, with per currency:
   - total circulating balance;
   - account count and active-account count;
   - top-1/top-10 concentration;
   - rich-tax collected by cycle;
   - transfer-tax and exchange volume;
   - configured issuance and sink settings.
2. Add a scheduled report export so owners can compare supply and concentration week over week.
3. Give every policy action a clear audit source (`rich_tax`, `transfer_tax`, `exchange`, `admin`, `migration`) and transaction ID.
4. Add guardrails and startup warnings for zero sinks, extreme tax rates, inverted exchange rates, and policies that would silently create money.
5. Add regression tests for tax rounding, tax destination totals, repeated cycles, disabled currencies, and report calculations.

## Following release: complete the policy toolkit

1. Add explicit currency sinks and faucets that administrators can schedule and audit:
   - configurable server fees and sink commands;
   - controlled grants with daily or per-player limits;
   - optional treasury destination instead of silent destruction.
2. Add policy profiles per currency (`stable`, `growth`, `event`) so owners can change a known set of thresholds, rates, and schedules together.
3. Add a simulation/dry-run mode for rich tax and scheduled sinks that reports expected accounts affected and total removal before applying.
4. Add account-level anti-abuse limits for transfers, grants, and exchange loops without blocking legitimate high-value transactions.

## Later: integrations that reinforce the thesis

- Async report and API methods so remote MySQL policy dashboards never block the Paper main thread.
- Economy events for shops, quests, auctions, and custom sinks to report their monetary impact.
- Optional Treasury compatibility after the core policy and reporting model is stable.
- A small web-friendly JSON/CSV export for external dashboards; keep the server plugin usable without a hosted service.

## Explicitly deferred

Cross-server synchronization, client mods, Fabric/Forge/Velocity builds, and a large management GUI remain lower priority. They expand the support surface without strengthening the monetary-policy differentiator.

## Success metrics

Measure product value with anonymized, opt-in data and support feedback:

- percentage of active servers with rich tax enabled;
- percentage using at least one configured sink or transfer tax;
- number of policy reports/export commands run;
- migration completion rate and rollback incidents;
- distribution of currencies per server and supported Paper versions.

The first product milestone is not “more features shipped.” It is a server owner being able to answer: **How much currency exists, where is it concentrated, what entered or left the economy, and which policy change caused it?**
