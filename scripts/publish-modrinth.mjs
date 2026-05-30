import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

const projectSlug = 'meoweco';
const projectId = 'M9JO0TFv';
const versionNumber = '26.8.1';
const jarPath = path.resolve('meoweco-paper/build/libs/meoweco-paper-26.8.1.jar');
const userAgent = 'xiaoyiluck666/meoweco-release';

const changelog = `# MeowEco 26.8.1

MeowEco \`26.8.1\` targets **Paper 26.1 / 26.1.1 / 26.1.2** servers running **Java 25**. This release focuses on smoother use on newer Paper environments and a better out-of-the-box MySQL storage experience.

## Added
- MySQL storage now ships with the Paper plugin, so server owners no longer need to install the MySQL driver separately.
- Added MySQL pool settings for tuning connection count, timeouts, and connection lifetime.

## Changed
- Clarified support for \`Paper 26.1\`, \`Paper 26.1.1\`, and \`Paper 26.1.2\`, all running on Java 25.
- Updated MySQL defaults to use \`utf8mb4\` and improved compatibility with common MySQL 8 authentication and timeout scenarios.
- Updated documentation for the current version, supported Paper range, and Java runtime requirement.

## Fixed
- Improved MySQL connection stability for charset, authentication, and slow-network situations.

## Runtime Notes
- Your server must run on **Java 25**.
- This release targets **Paper 26.1 / 26.1.1 / 26.1.2**. If your server is still on Paper / Minecraft \`1.21.11\` or older, do not assume this version is compatible.`;

const projectBody = `[issues](https://github.com/xiaoyiluck666/MeowEco/issues)

# MeowEco Documentation

Version: \`26.8.1\`

MeowEco is a Paper economy plugin with multi-currency support, Vault integration, PlaceholderAPI support, exchange rates, frozen funds, scheduled rich tax, and built-in MySQL storage support for modern server setups.

> Great for point shops, RPG currency systems, VIP menus, recharge flows, and servers that want smooth Vault / PlaceholderAPI / TrMenu integration.

## Platform

- Plugin version: \`26.8.1\`
- Current build target API: \`io.papermc.paper:paper-api:26.1.2.build.64-stable\`
- Additional compatibility guard: compiled against the earliest supported \`26.1.x\` API line to protect \`Paper 26.1 / 26.1.1 / 26.1.2\`
- \`meoweco-paper\` is compiled with a JDK 25 toolchain and emitted as Java 25 bytecode
- If you run \`Paper 26.1 / 26.1.1 / 26.1.2\`, the server itself still needs Java 25 because that requirement comes from upstream Paper

## Implemented Features

- Multiple currencies loaded from \`config.yml\`
- Per-currency display name, singular or plural name, initial balance, decimal precision, and transfer tax
- Configurable default currency used by Vault and command fallbacks
- Currency exchange with direct, inverse, and default-currency-derived rates
- Player balance lookup, payments, and leaderboard
- Admin balance management: give, take, set
- Frozen funds operations: \`freeze\`, \`unfreeze\`, \`deductfrozen\`
- Leaderboard visibility control: \`hide\`, \`unhide\`
- Leaderboard and placeholder cache refresh: \`refresh\`
- Scheduled rich tax with system sink or player collector
- Vault economy provider for the default currency
- PlaceholderAPI expansion with cached balance, top, and server-total placeholders
- Update checker against Modrinth
- Automatic account creation for every configured currency on player join
- Built-in MySQL storage support with bundled driver and pool tuning options
- Command override listener for legacy labels such as \`/bal\`, \`/money\`, \`/pay\`, \`/baltop\`, \`/moneytop\`, and \`/ecotop\`

## PlaceholderAPI

Identifier: \`meoweco\`

- Currency metadata placeholders for singular, plural, display, and id forms
- Balance placeholders for \`balance\`, \`frozen\`, and \`available\`
- Top placeholders and server-total placeholders with cached output for menu and HUD use
- Works well with PlaceholderAPI-powered scoreboards, chat formats, and TrMenu displays

## Developer API

\`\`\`java
MeowEcoAPI api = MeowEcoAPI.get();
\`\`\`

## Operational Notes

- Leaderboard cache lifetime: 5 minutes
- Placeholder balance cache lifetime: 1 second
- Placeholder top and server-total cache lifetime: 30 seconds
- Most database operations are dispatched asynchronously
- Hidden accounts are excluded from leaderboard output at the database layer`;

function authHeaders(token, contentType) {
  const headers = {
    Authorization: token,
    'User-Agent': userAgent,
  };
  if (contentType) {
    headers['Content-Type'] = contentType;
  }
  return headers;
}

async function readToken() {
  if (process.env.MODRINTH_TOKEN) {
    return process.env.MODRINTH_TOKEN.trim();
  }

  const rl = readline.createInterface({ input, output });
  try {
    return (await rl.question('Paste Modrinth PAT: ')).trim();
  } finally {
    rl.close();
  }
}

async function requestJson(url, options) {
  const response = await fetch(url, options);
  const text = await response.text();
  let body;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = text;
  }

  if (!response.ok) {
    throw new Error(`${options.method ?? 'GET'} ${url} failed with ${response.status}: ${text}`);
  }

  return body ?? {};
}

async function versionExists() {
  const versions = await requestJson(`https://api.modrinth.com/v2/project/${projectSlug}/version`, {
    headers: { 'User-Agent': userAgent },
  });
  return versions.some((version) => version.version_number === versionNumber);
}

async function createVersion(token) {
  const form = new FormData();
  form.append('data', JSON.stringify({
    name: `MeowEco ${versionNumber}`,
    version_number: versionNumber,
    changelog,
    dependencies: [],
    game_versions: ['26.1', '26.1.1', '26.1.2'],
    version_type: 'release',
    loaders: ['bukkit', 'paper', 'purpur', 'spigot'],
    featured: false,
    project_id: projectId,
    file_parts: ['file'],
    primary_file: 'file',
  }));

  const file = new Blob([fs.readFileSync(jarPath)], { type: 'application/java-archive' });
  form.append('file', file, path.basename(jarPath));

  return requestJson('https://api.modrinth.com/v2/version', {
    method: 'POST',
    headers: authHeaders(token),
    body: form,
  });
}

async function updateProject(token) {
  return requestJson(`https://api.modrinth.com/v2/project/${projectSlug}`, {
    method: 'PATCH',
    headers: authHeaders(token, 'application/json'),
    body: JSON.stringify({
      description: 'A modern Paper economy plugin with multi-currency support, Vault, PlaceholderAPI, rich tax, frozen funds, exchange rates, and built-in MySQL storage.',
      body: projectBody,
    }),
  });
}

async function main() {
  if (!fs.existsSync(jarPath)) {
    throw new Error(`Missing release jar: ${jarPath}`);
  }

  const token = await readToken();
  const user = await requestJson('https://api.modrinth.com/v2/user', {
    headers: authHeaders(token),
  });
  console.log(`Authenticated as ${user.username} (${user.id})`);

  if (await versionExists()) {
    console.log(`Version ${versionNumber} already exists, skipping upload.`);
  } else {
    const version = await createVersion(token);
    console.log(`Created version ${version.version_number} (${version.id})`);
  }

  const project = await updateProject(token);
  console.log(`Updated project ${project.slug ?? projectSlug}`);

  const latest = await requestJson(`https://api.modrinth.com/v2/project/${projectSlug}/version`, {
    headers: { 'User-Agent': userAgent },
  });
  console.log(`Latest listed version: ${latest[0]?.version_number ?? 'unknown'}`);
}

main().catch((error) => {
  console.error(error.message);
  process.exitCode = 1;
});
