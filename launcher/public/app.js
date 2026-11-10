const state = {
  instances: [],
  current: null,
  mods: [],
  results: [],
};

const $ = (id) => document.getElementById(id);

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.error || `HTTP ${response.status}`);
  return data;
}

let toastTimer;
function toast(message, bad = false) {
  const el = $('toast');
  el.textContent = message;
  el.className = `toast show${bad ? ' error' : ''}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = 'toast'; }, 4200);
}

function fmtBytes(bytes) {
  if (!bytes) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB'];
  let value = bytes;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) { value /= 1024; index++; }
  return `${value.toFixed(value >= 10 || index === 0 ? 0 : 1)} ${units[index]}`;
}

function fmtDownloads(value) {
  if (value >= 1_000_000) return `${(value / 1_000_000).toFixed(1)}M`;
  if (value >= 1_000) return `${(value / 1_000).toFixed(1)}K`;
  return String(value || 0);
}

async function health() {
  try {
    await api('/api/health');
    $('health').textContent = '本地服务正常';
    $('health').className = 'status ok';
  } catch {
    $('health').textContent = '本地服务不可用';
    $('health').className = 'status bad';
  }
}

function renderInstances() {
  const list = $('instances');
  list.innerHTML = '';
  $('emptyInstances').style.display = state.instances.length ? 'none' : 'block';
  for (const instance of state.instances) {
    const button = document.createElement('button');
    button.className = `instance${state.current?.id === instance.id ? ' active' : ''}`;
    button.innerHTML = `<div class="instance-name"></div><div class="instance-meta"></div>`;
    button.querySelector('.instance-name').textContent = instance.name;
    button.querySelector('.instance-meta').textContent = `${instance.loader} · ${instance.mcVersion}`;
    button.addEventListener('click', () => selectInstance(instance));
    list.appendChild(button);
  }
}

function renderCurrent() {
  const instance = state.current;
  $('launch').disabled = !instance;
  $('openFolder').disabled = !instance;
  if (!instance) {
    $('instanceName').textContent = '选择一个实例';
    $('instanceMeta').textContent = '从左侧选择，或新建一个实例。';
    return;
  }
  $('instanceName').textContent = instance.name;
  $('instanceMeta').textContent = `${instance.path}  ·  ${instance.loader} ${instance.mcVersion}`;
  $('loaderSelect').value = instance.loader;
  $('mcInput').value = instance.mcVersion;
  $('settingName').value = instance.name;
  $('settingLaunch').value = instance.launchCommand || '';
}

async function selectInstance(instance) {
  state.current = instance;
  renderInstances();
  renderCurrent();
  await refreshMods();
}

async function refreshInstances() {
  state.instances = await api('/api/instances');
  if (!state.current && state.instances.length) {
    state.current = state.instances[0];
  } else if (state.current) {
    state.current = state.instances.find((it) => it.id === state.current.id) || state.instances[0] || null;
  }
  renderInstances();
  renderCurrent();
  if (state.current) await refreshMods();
}

async function refreshMods() {
  if (!state.current) {
    $('modList').innerHTML = '';
    return;
  }
  try {
    const data = await api(`/api/instances/${state.current.id}/mods`);
    state.mods = data.mods || [];
    renderMods();
  } catch (error) {
    toast(error.message, true);
  }
}

function renderMods() {
  const list = $('modList');
  list.innerHTML = '';
  if (!state.mods.length) {
    list.innerHTML = '<div class="hint">还没有安装模组。去“搜索模组”里装一个。</div>';
    return;
  }
  for (const mod of state.mods) {
    const row = document.createElement('div');
    row.className = 'mod-row';
    const toggle = document.createElement('button');
    toggle.className = `switch${mod.enabled ? ' on' : ''}`;
    toggle.title = mod.enabled ? '禁用' : '启用';
    toggle.addEventListener('click', async () => {
      try {
        await api(`/api/instances/${state.current.id}/mods/toggle`, {
          method: 'POST',
          body: JSON.stringify({ file: mod.file, enabled: !mod.enabled }),
        });
        await refreshMods();
      } catch (error) { toast(error.message, true); }
    });
    const info = document.createElement('div');
    info.style.minWidth = '0';
    info.innerHTML = `<div class="mod-name"></div><div class="mod-sub"></div>`;
    info.querySelector('.mod-name').textContent = mod.file.replace(/\.disabled$/, '');
    info.querySelector('.mod-sub').textContent = `${mod.enabled ? '已启用' : '已禁用'} · ${fmtBytes(mod.size)}`;
    const del = document.createElement('button');
    del.className = 'btn small danger';
    del.textContent = '删除';
    del.addEventListener('click', async () => {
      if (!confirm(`删除 ${mod.file}？`)) return;
      try {
        await api(`/api/instances/${state.current.id}/mods?file=${encodeURIComponent(mod.file)}`, { method: 'DELETE' });
        await refreshMods();
      } catch (error) { toast(error.message, true); }
    });
    const actions = document.createElement('div');
    actions.className = 'mod-actions';
    actions.append(del);
    row.append(toggle, info, actions);
    list.appendChild(row);
  }
}

async function search() {
  if (!state.current) return toast('先创建一个实例', true);
  const query = $('searchInput').value.trim();
  const loader = $('loaderSelect').value;
  const mc = $('mcInput').value.trim();
  $('searchBtn').disabled = true;
  $('searchHint').textContent = '正在搜索 Modrinth…';
  try {
    const result = await api(`/api/search?query=${encodeURIComponent(query)}&loader=${encodeURIComponent(loader)}&mc=${encodeURIComponent(mc)}`);
    state.results = result.hits || [];
    renderResults();
    $('searchHint').textContent = `找到 ${state.results.length} 个结果 · ${loader} ${mc}`;
  } catch (error) {
    $('searchHint').textContent = error.message;
    toast(error.message, true);
  } finally {
    $('searchBtn').disabled = false;
  }
}

function renderResults() {
  const box = $('searchResults');
  box.innerHTML = '';
  if (!state.results.length) {
    box.innerHTML = '<div class="hint">没有结果，换个关键词或版本试试。</div>';
    return;
  }
  for (const project of state.results) {
    const card = document.createElement('div');
    card.className = 'card';
    const head = document.createElement('div');
    head.className = 'card-head';
    if (project.icon_url) {
      const img = document.createElement('img');
      img.className = 'card-icon';
      img.src = project.icon_url;
      img.alt = '';
      head.appendChild(img);
    }
    const title = document.createElement('div');
    title.className = 'card-title';
    title.textContent = project.title;
    head.appendChild(title);
    const desc = document.createElement('div');
    desc.className = 'card-desc';
    desc.textContent = project.description || '';
    const foot = document.createElement('div');
    foot.className = 'card-foot';
    const downloads = document.createElement('span');
    downloads.className = 'downloads';
    downloads.textContent = `↓ ${fmtDownloads(project.downloads)}`;
    const install = document.createElement('button');
    install.className = 'btn small primary';
    install.textContent = '安装';
    install.addEventListener('click', () => installProject(project, install));
    foot.append(downloads, install);
    card.append(head, desc, foot);
    box.appendChild(card);
  }
}

async function installProject(project, button) {
  if (!state.current) return toast('先创建一个实例', true);
  button.disabled = true;
  button.textContent = '解析版本…';
  try {
    const loader = state.current.loader;
    const mc = state.current.mcVersion;
    const versions = await api(`/api/versions?project=${encodeURIComponent(project.project_id)}&loader=${encodeURIComponent(loader)}&mc=${encodeURIComponent(mc)}`);
    if (!versions.length) throw new Error(`没有找到 ${loader} ${mc} 的版本`);
    const version = versions[0];
    button.textContent = '下载中…';
    const result = await api(`/api/instances/${state.current.id}/install`, {
      method: 'POST',
      body: JSON.stringify({ versionId: version.id }),
    });
    const installed = (result.log || []).filter((it) => it.action === 'installed');
    toast(installed.length ? `已安装 ${installed.length} 个文件` : '文件已经存在');
    await refreshMods();
  } catch (error) {
    toast(error.message, true);
  } finally {
    button.disabled = false;
    button.textContent = '安装';
  }
}

async function sync() {
  if (!state.current) return toast('先创建一个实例', true);
  const url = $('syncUrl').value.trim();
  if (!url) return toast('填同步地址', true);
  $('syncBtn').disabled = true;
  $('syncLog').textContent = '正在读取 manifest…';
  try {
    const result = await api(`/api/instances/${state.current.id}/sync`, {
      method: 'POST',
      body: JSON.stringify({ url }),
    });
    $('syncLog').textContent = (result.files || [])
      .map((it) => `${it.action.padEnd(16)} ${it.name}`)
      .join('\n') || '没有文件';
    toast(`同步完成：${result.serverName}`);
    await refreshMods();
  } catch (error) {
    $('syncLog').textContent = error.message;
    toast(error.message, true);
  } finally {
    $('syncBtn').disabled = false;
  }
}

async function saveSettings() {
  if (!state.current) return;
  try {
    const updated = await api(`/api/instances/${state.current.id}`, {
      method: 'PATCH',
      body: JSON.stringify({
        name: $('settingName').value.trim(),
        launchCommand: $('settingLaunch').value,
      }),
    });
    state.current = updated;
    $('settingsMsg').textContent = '已保存';
    await refreshInstances();
    toast('设置已保存');
  } catch (error) {
    $('settingsMsg').textContent = error.message;
    toast(error.message, true);
  }
}

function setupTabs() {
  document.querySelectorAll('.tab').forEach((tab) => {
    tab.addEventListener('click', () => {
      document.querySelectorAll('.tab').forEach((it) => it.classList.remove('active'));
      document.querySelectorAll('.panel').forEach((it) => it.classList.remove('active'));
      tab.classList.add('active');
      $(`tab-${tab.dataset.tab}`).classList.add('active');
      if (tab.dataset.tab === 'installed') refreshMods();
    });
  });
}

function setupCreate() {
  const dialog = $('createDialog');
  $('newInstance').addEventListener('click', () => dialog.showModal());
  $('cancelCreate').addEventListener('click', () => dialog.close());
  $('createForm').addEventListener('submit', async (event) => {
    event.preventDefault();
    const form = new FormData(event.target);
    try {
      const instance = await api('/api/instances', {
        method: 'POST',
        body: JSON.stringify(Object.fromEntries(form.entries())),
      });
      dialog.close();
      event.target.reset();
      state.current = instance;
      await refreshInstances();
      await selectInstance(instance);
      toast('实例已创建');
    } catch (error) {
      toast(error.message, true);
    }
  });
}

function setupActions() {
  $('searchBtn').addEventListener('click', search);
  $('searchInput').addEventListener('keydown', (event) => { if (event.key === 'Enter') search(); });
  $('refreshMods').addEventListener('click', refreshMods);
  $('syncBtn').addEventListener('click', sync);
  $('saveSettings').addEventListener('click', saveSettings);
  $('openFolder').addEventListener('click', async () => {
    if (!state.current) return;
    try { await api(`/api/instances/${state.current.id}/open`, { method: 'POST', body: '{}' }); }
    catch (error) { toast(error.message, true); }
  });
  $('launch').addEventListener('click', async () => {
    if (!state.current) return;
    try {
      const result = await api(`/api/instances/${state.current.id}/launch`, { method: 'POST', body: '{}' });
      toast(`已启动，PID ${result.pid}`);
    } catch (error) {
      toast(error.message, true);
    }
  });
}

async function boot() {
  setupTabs();
  setupCreate();
  setupActions();
  await health();
  try {
    await refreshInstances();
  } catch (error) {
    toast(error.message, true);
  }
}

boot();
