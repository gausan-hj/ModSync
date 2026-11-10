import http from 'node:http';
import https from 'node:https';
import fs from 'node:fs/promises';
import path from 'node:path';
import crypto from 'node:crypto';
import os from 'node:os';
import { spawn } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const ROOT = path.dirname(fileURLToPath(import.meta.url));
const PUBLIC = path.join(ROOT, 'public');
const DATA = path.join(ROOT, 'data');
const INSTANCES_FILE = path.join(DATA, 'instances.json');
const PORT = Number(process.env.PORT || 17321);
const UA = 'ModSync-Launcher/0.1.0 (local)';

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
};

function json(res, status, body) {
  const data = Buffer.from(JSON.stringify(body, null, 2));
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': data.length,
    'Cache-Control': 'no-store',
  });
  res.end(data);
}

function text(res, status, body, type = 'text/plain; charset=utf-8') {
  const data = Buffer.from(body);
  res.writeHead(status, { 'Content-Type': type, 'Content-Length': data.length });
  res.end(data);
}

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
    if (Buffer.concat(chunks).length > 2 * 1024 * 1024) {
      throw new Error('body too large');
    }
  }
  if (!chunks.length) return {};
  return JSON.parse(Buffer.concat(chunks).toString('utf8'));
}

async function loadInstances() {
  try {
    return JSON.parse(await fs.readFile(INSTANCES_FILE, 'utf8'));
  } catch {
    return [];
  }
}

async function saveInstances(list) {
  await fs.mkdir(DATA, { recursive: true });
  await fs.writeFile(INSTANCES_FILE, JSON.stringify(list, null, 2), 'utf8');
}

function defaultInstancePath(name) {
  const safe = name.replace(/[^\w\-.\u4e00-\u9fa5 ]+/g, '_').trim() || 'instance';
  return path.join(os.homedir(), 'AppData', 'Roaming', 'ModSyncLauncher', 'instances', safe);
}

async function ensureInstance(instance) {
  await fs.mkdir(path.join(instance.path, 'mods'), { recursive: true });
  await fs.mkdir(path.join(instance.path, 'config'), { recursive: true });
  await fs.mkdir(path.join(instance.path, 'resourcepacks'), { recursive: true });
  await fs.mkdir(path.join(instance.path, 'shaderpacks'), { recursive: true });
}

function instanceById(instances, id) {
  return instances.find((it) => it.id === id) || null;
}

function getBuffer(url, headers = {}, redirects = 5) {
  return new Promise((resolve, reject) => {
    const parsed = new URL(url);
    const client = parsed.protocol === 'https:' ? https : http;
    const request = client.get(parsed, {
      family: 4,
      timeout: 30000,
      headers: { 'User-Agent': UA, ...headers },
    }, (response) => {
      const status = response.statusCode || 0;
      if (status >= 300 && status < 400 && response.headers.location && redirects > 0) {
        response.resume();
        resolve(getBuffer(new URL(response.headers.location, url).toString(), headers, redirects - 1));
        return;
      }
      if (status < 200 || status >= 300) {
        response.resume();
        reject(new Error(`HTTP ${status}`));
        return;
      }
      const chunks = [];
      response.on('data', (chunk) => chunks.push(chunk));
      response.on('end', () => resolve(Buffer.concat(chunks)));
      response.on('error', reject);
    });
    request.on('timeout', () => request.destroy(new Error('request timeout')));
    request.on('error', reject);
  });
}

async function modrinth(url) {
  const data = await getBuffer(url, { Accept: 'application/json' });
  return JSON.parse(data.toString('utf8'));
}

function facetsFor(loader, mcVersion) {
  const facets = [['project_type:mod']];
  if (loader) facets.push([`categories:${loader}`]);
  if (mcVersion) facets.push([`versions:${mcVersion}`]);
  return JSON.stringify(facets);
}

async function searchMods(query, loader, mcVersion) {
  const params = new URLSearchParams({
    query: query || '',
    limit: '24',
    index: 'relevance',
    facets: facetsFor(loader, mcVersion),
  });
  return modrinth(`https://api.modrinth.com/v2/search?${params}`);
}

async function projectVersions(slug, loader, mcVersion) {
  const params = new URLSearchParams();
  if (loader) params.set('loaders', JSON.stringify([loader]));
  if (mcVersion) params.set('game_versions', JSON.stringify([mcVersion]));
  return modrinth(`https://api.modrinth.com/v2/project/${encodeURIComponent(slug)}/version?${params}`);
}

async function versionById(id) {
  return modrinth(`https://api.modrinth.com/v2/version/${encodeURIComponent(id)}`);
}

function primaryFile(version) {
  return version.files?.find((f) => f.primary) || version.files?.[0] || null;
}

function safeFileName(name) {
  return path.basename(String(name)).replace(/[<>:"/\\|?*\u0000-\u001f]/g, '_');
}

async function download(url) {
  return getBuffer(url);
}

async function installVersion(instance, version, visited = new Set(), log = []) {
  if (!version || visited.has(version.id)) return;
  visited.add(version.id);
  const file = primaryFile(version);
  if (!file) return;
  const name = safeFileName(file.filename);
  const target = path.join(instance.path, 'mods', name);
  const relative = path.relative(instance.path, target);
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    throw new Error('unsafe mod path');
  }
  try {
    const existing = await fs.stat(target);
    if (existing.size === file.size) {
      log.push({ name, action: 'already-installed' });
      return;
    }
  } catch {}
  const data = await download(file.url);
  if (file.hashes?.sha512) {
    const actual = crypto.createHash('sha512').update(data).digest('hex');
    if (actual.toLowerCase() !== file.hashes.sha512.toLowerCase()) {
      throw new Error(`sha512 mismatch: ${name}`);
    }
  }
  await fs.mkdir(path.dirname(target), { recursive: true });
  await fs.writeFile(target, data);
  log.push({ name, action: 'installed', size: data.length, version: version.version_number });

  for (const dep of version.dependencies || []) {
    if (dep.dependency_type !== 'required') continue;
    try {
      if (dep.version_id) {
        await installVersion(instance, await versionById(dep.version_id), visited, log);
      } else if (dep.project_id) {
        const versions = await projectVersions(dep.project_id, instance.loader, instance.mcVersion);
        if (versions.length) await installVersion(instance, versions[0], visited, log);
      }
    } catch (error) {
      log.push({ name: dep.project_id || dep.version_id, action: 'dependency-failed', error: error.message });
    }
  }
}

async function listMods(instance) {
  const dir = path.join(instance.path, 'mods');
  await fs.mkdir(dir, { recursive: true });
  const entries = await fs.readdir(dir, { withFileTypes: true });
  const mods = [];
  for (const entry of entries) {
    if (!entry.isFile()) continue;
    if (!entry.name.endsWith('.jar') && !entry.name.endsWith('.jar.disabled')) continue;
    const stat = await fs.stat(path.join(dir, entry.name));
    mods.push({
      file: entry.name,
      enabled: entry.name.endsWith('.jar'),
      size: stat.size,
      modified: stat.mtimeMs,
    });
  }
  mods.sort((a, b) => a.file.localeCompare(b.file));
  return mods;
}

function modPath(instance, file) {
  const name = safeFileName(file);
  if (name !== file) throw new Error('unsafe file name');
  const target = path.join(instance.path, 'mods', name);
  const relative = path.relative(path.join(instance.path, 'mods'), target);
  if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('unsafe path');
  return target;
}

async function toggleMod(instance, file, enabled) {
  const current = modPath(instance, file);
  const targetName = enabled
    ? (file.endsWith('.disabled') ? file.slice(0, -'.disabled'.length) : file)
    : (file.endsWith('.jar') ? file + '.disabled' : file);
  const target = modPath(instance, targetName);
  await fs.rename(current, target);
  return targetName;
}

async function syncServer(instance, url) {
  if (!url) throw new Error('missing sync url');
  let base = url.replace(/\/+$/, '');
  let manifestUrl = base.endsWith('.json') ? base : `${base}/manifest.json`;
  if (!base.includes('/modsync') && !base.endsWith('.json')) {
    base = `${base}/modsync`;
    manifestUrl = `${base}/manifest.json`;
  }
  const manifest = JSON.parse((await getBuffer(manifestUrl, { Accept: 'application/json' })).toString('utf8'));
  const log = [];
  for (const file of manifest.files || []) {
    const name = safeFileName(file.file);
    const target = path.join(instance.path, 'mods', name);
    if (file.kind && file.kind !== 'mods') continue;
    try {
      const stat = await fs.stat(target);
      if (stat.size === file.size) {
        log.push({ name, action: 'already-present' });
        continue;
      }
    } catch {}
    const data = await download(`${base}/files/${encodeURIComponent(file.file)}`);
    if (file.sha256) {
      const actual = crypto.createHash('sha256').update(data).digest('hex');
      if (actual.toLowerCase() !== String(file.sha256).toLowerCase()) {
        throw new Error(`sha256 mismatch: ${name}`);
      }
    }
    await fs.writeFile(target, data);
    log.push({ name, action: 'installed', size: data.length });
  }
  return { serverName: manifest.serverName || 'server', files: log };
}

async function serveStatic(req, res, pathname) {
  let file = pathname === '/' ? '/index.html' : pathname;
  const target = path.normalize(path.join(PUBLIC, file));
  if (!target.startsWith(PUBLIC)) return text(res, 403, 'forbidden');
  try {
    const data = await fs.readFile(target);
    const ext = path.extname(target).toLowerCase();
    res.writeHead(200, {
      'Content-Type': MIME[ext] || 'application/octet-stream',
      'Content-Length': data.length,
      'Cache-Control': 'no-store',
    });
    res.end(data);
  } catch {
    text(res, 404, 'not found');
  }
}

async function api(req, res, pathname, query) {
  if (pathname === '/api/health') return json(res, 200, { ok: true, version: '0.1.0' });

  if (pathname === '/api/instances' && req.method === 'GET') {
    return json(res, 200, await loadInstances());
  }

  if (pathname === '/api/instances' && req.method === 'POST') {
    const body = await readBody(req);
    const name = String(body.name || '').trim();
    if (!name) return json(res, 400, { error: 'name required' });
    const instances = await loadInstances();
    const id = crypto.randomUUID();
    const instance = {
      id,
      name,
      loader: ['fabric', 'forge', 'neoforge'].includes(body.loader) ? body.loader : 'fabric',
      mcVersion: String(body.mcVersion || '1.21.1'),
      path: body.path ? path.resolve(String(body.path)) : defaultInstancePath(name),
      launchCommand: String(body.launchCommand || ''),
      createdAt: Date.now(),
    };
    await ensureInstance(instance);
    instances.push(instance);
    await saveInstances(instances);
    return json(res, 200, instance);
  }

  const instanceMatch = pathname.match(/^\/api\/instances\/([^/]+)(?:\/(.*))?$/);
  if (instanceMatch) {
    const instances = await loadInstances();
    const instance = instanceById(instances, instanceMatch[1]);
    if (!instance) return json(res, 404, { error: 'instance not found' });
    const action = instanceMatch[2] || '';

    if (!action && req.method === 'GET') return json(res, 200, instance);

    if (!action && req.method === 'PATCH') {
      const body = await readBody(req);
      if (body.name) instance.name = String(body.name);
      if (body.launchCommand !== undefined) instance.launchCommand = String(body.launchCommand);
      await saveInstances(instances);
      return json(res, 200, instance);
    }

    if (action === 'mods' && req.method === 'GET') {
      return json(res, 200, { instance, mods: await listMods(instance) });
    }

    if (action === 'mods' && req.method === 'DELETE') {
      const file = query.get('file');
      if (!file) return json(res, 400, { error: 'file required' });
      await fs.unlink(modPath(instance, file));
      return json(res, 200, { ok: true });
    }

    if (action === 'mods/toggle' && req.method === 'POST') {
      const body = await readBody(req);
      const name = await toggleMod(instance, String(body.file || ''), body.enabled !== false);
      return json(res, 200, { ok: true, file: name });
    }

    if (action === 'install' && req.method === 'POST') {
      const body = await readBody(req);
      const version = await versionById(String(body.versionId || ''));
      const log = [];
      await installVersion(instance, version, new Set(), log);
      return json(res, 200, { ok: true, log });
    }

    if (action === 'sync' && req.method === 'POST') {
      const body = await readBody(req);
      const result = await syncServer(instance, String(body.url || ''));
      return json(res, 200, { ok: true, ...result });
    }

    if (action === 'open' && req.method === 'POST') {
      const target = instance.path;
      const command = process.platform === 'win32' ? 'explorer.exe' : process.platform === 'darwin' ? 'open' : 'xdg-open';
      spawn(command, [target], { detached: true, stdio: 'ignore' }).unref();
      return json(res, 200, { ok: true });
    }

    if (action === 'launch' && req.method === 'POST') {
      if (!instance.launchCommand) {
        return json(res, 400, { error: '没有配置启动命令。请在实例设置里填写启动命令。' });
      }
      const child = spawn(instance.launchCommand, {
        cwd: instance.path,
        shell: true,
        detached: true,
        stdio: 'ignore',
        windowsHide: false,
      });
      child.unref();
      return json(res, 200, { ok: true, pid: child.pid });
    }

    return json(res, 404, { error: 'unknown instance action' });
  }

  if (pathname === '/api/search' && req.method === 'GET') {
    return json(res, 200, await searchMods(query.get('query') || '', query.get('loader') || '', query.get('mc') || ''));
  }

  if (pathname === '/api/versions' && req.method === 'GET') {
    return json(res, 200, await projectVersions(query.get('project') || '', query.get('loader') || '', query.get('mc') || ''));
  }

  return json(res, 404, { error: 'not found' });
}

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url, `http://${req.headers.host || '127.0.0.1'}`);
    if (url.pathname.startsWith('/api/')) {
      return await api(req, res, url.pathname, url.searchParams);
    }
    return await serveStatic(req, res, url.pathname);
  } catch (error) {
    console.error(error);
    if (!res.headersSent) json(res, 500, { error: error.message || String(error) });
    else res.end();
  }
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`ModSync Launcher http://127.0.0.1:${PORT}`);
});
