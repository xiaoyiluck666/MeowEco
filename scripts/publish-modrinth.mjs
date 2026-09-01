import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

const projectSlug = 'meoweco';
const projectId = 'M9JO0TFv';
const userAgent = 'xiaoyiluck666/meoweco-release';

function readProjectVersion() {
  const buildFile = fs.readFileSync(path.resolve('build.gradle.kts'), 'utf8');
  const match = buildFile.match(/^\s*version\s*=\s*"([^"]+)"/m);
  if (!match) {
    throw new Error('Unable to read project version from build.gradle.kts');
  }
  return match[1];
}

function readListEnv(name, fallback) {
  return (process.env[name] ?? fallback)
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
}

const versionNumber = process.env.MEOWECO_VERSION?.trim() || readProjectVersion();
const jarPath = path.resolve(process.env.MEOWECO_JAR ?? `meoweco-paper/build/libs/meoweco-paper-${versionNumber}.jar`);
const changelogPath = path.resolve(process.env.MEOWECO_CHANGELOG ?? `docs/release/CHANGELOG_${versionNumber}_EN.md`);
const supportedGameVersions = readListEnv('MODRINTH_GAME_VERSIONS', '26.1,26.1.1,26.1.2,26.2');

const changelog = fs.readFileSync(changelogPath, 'utf8');

function readProjectBody() {
  return fs.readFileSync(path.resolve('docs/marketplace/MODRINTH.md'), 'utf8')
    .replace(/\r\n/g, '\n')
    .trim();
}

const projectBody = readProjectBody();

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
    game_versions: supportedGameVersions,
    version_type: 'release',
    loaders: ['paper', 'purpur'],
    featured: true,
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
      title: 'MeowEco Economy',
      description: 'Multi-currency Paper economy with no-reset Vault/EssentialsX/CSV migration, transaction audit, inflation controls, Vault, SQLite, and MySQL.',
      body: projectBody,
      source_url: 'https://github.com/xiaoyiluck666/MeowEco',
      issues_url: 'https://github.com/xiaoyiluck666/MeowEco/issues',
      wiki_url: 'https://github.com/xiaoyiluck666/MeowEco/wiki',
    }),
  });
}

async function main() {
  if (!fs.existsSync(jarPath)) {
    throw new Error(`Missing release jar: ${jarPath}`);
  }
  if (!fs.existsSync(changelogPath)) {
    throw new Error(`Missing release changelog: ${changelogPath}`);
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
