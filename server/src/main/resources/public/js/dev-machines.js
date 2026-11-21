import { mountDonateLinks } from './donate-link.js';
import { createLabOfficeController } from './lab-office.js';
import {
  buildRunRows,
  createGithubLink,
  filterMergedToday,
  formatBatteryLine,
  formatCursorLine,
  formatLastSeenAgo,
  formatMachineWork,
  formatRunDuration,
  formatTaipeiClock,
  machineRole,
  viewerCanLink
} from './dev-machines-format.js';

const REFRESH_MS = 30 * 1000;
const REPO = 'https://github.com/p4774756/PunchClock';
const MERGED_PREVIEW = 8;
const LOGIN_NEXT = '/login?next=/dev-status';

function el(tag, className) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  return node;
}

function setText(node, text) {
  node.textContent = text == null ? '' : String(text);
}

function isAdminViewer() {
  if (typeof window !== 'undefined' && window.PUNCHCLOCK_AUTHENTICATED === true) return true;
  return false;
}

/** 管理員已登入時隱藏頁尾「管理員登入」。 */
export function updateDevMachinesLoginFooter(doc, canLink) {
  if (!doc || !doc.querySelector) return;
  const foot = doc.querySelector('.dev-machines-login-footer');
  if (foot) foot.hidden = !!canLink;
}

function setGuestChrome(doc, guest) {
  const sub = doc && doc.querySelector('.dev-machines-sub');
  if (sub) sub.hidden = !!guest;
}

export function renderGuestLoginPanel(root, doc) {
  const d = doc || (typeof document !== 'undefined' ? document : null);
  if (!root || !d) return;
  root.replaceChildren();
  setGuestChrome(d, true);
  const panel = el('section', 'dev-machines-guest maple-window');
  const chrome = el('header', 'maple-win-title');
  const title = el('h2', null);
  setText(title, '請先登入');
  chrome.appendChild(title);
  panel.appendChild(chrome);
  const body = el('div', 'dev-machines-guest-body');
  const msg = el('p', 'dev-machines-guest-msg');
  setText(msg, '開發機狀態只開放管理員查看，請先登入。');
  body.appendChild(msg);
  const login = el('a', 'dev-btn dev-btn-gold');
  login.href = LOGIN_NEXT;
  setText(login, '登入');
  body.appendChild(login);
  panel.appendChild(body);
  root.appendChild(panel);
  updateDevMachinesLoginFooter(d, false);
}

function renderErrorBanner(root, message) {
  let bar = root.querySelector('[data-dev-machines-error]');
  if (!bar) {
    bar = el('p', 'dev-machines-error');
    bar.setAttribute('data-dev-machines-error', '1');
    root.insertBefore(bar, root.firstChild);
  }
  setText(bar, message || '');
  bar.hidden = !message;
}

function fieldRow(doc, label, valueNode) {
  const row = doc.createElement('div');
  row.className = 'dev-field-row';
  const lab = doc.createElement('span');
  lab.className = 'dev-field-label';
  lab.textContent = label;
  row.appendChild(lab);
  const val = doc.createElement('div');
  val.className = 'dev-field-value';
  if (typeof valueNode === 'string') {
    val.textContent = valueNode;
  } else if (valueNode) {
    val.appendChild(valueNode);
  }
  row.appendChild(val);
  return row;
}

export function renderCard(doc, machine, now, opts) {
  const d = doc || (typeof document !== 'undefined' ? document : null);
  const mk = (tag, className) => {
    const node = d.createElement(tag);
    if (className) node.className = className;
    return node;
  };
  const setTxt = (node, text) => {
    node.textContent = text == null ? '' : String(text);
  };
  const o = opts || {};
  const canLink = viewerCanLink(o.canLink != null ? o.canLink : o.viewer);
  const card = mk('article', 'dev-machine-card maple-window');
  const chrome = mk('header', 'maple-win-title');
  const title = mk('h2', 'dev-machine-name');
  setTxt(title, machine.label || machine.machine);
  const badge = mk('span', 'dev-badge ' + (machine.online ? 'is-online' : 'is-offline'));
  setTxt(badge, machine.online ? '在線' : '離線');
  chrome.appendChild(title);
  chrome.appendChild(badge);
  card.appendChild(chrome);

  const body = mk('div', 'dev-machine-body');
  const role = machineRole(machine);
  if (role) {
    const roleEl = mk('p', 'dev-machine-role');
    setTxt(roleEl, role);
    body.appendChild(roleEl);
  }

  const runs = Array.isArray(machine.runs) ? machine.runs : [];
  const work = formatMachineWork(machine, REPO);
  let workVal;
  if (work.href && canLink) {
    const a = mk('a', 'dev-link');
    a.href = work.href;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
    setTxt(a, work.text);
    workVal = a;
  } else if (work.href && !canLink) {
    workVal = createGithubLink(d, { text: work.text, href: work.href, canLink: false });
  } else {
    workVal = machine.online ? work.text : '—';
  }
  body.appendChild(fieldRow(d, '工作', workVal));

  if (machine.online && runs.length > 0) {
    body.appendChild(buildRunRows(d, runs, now, { canLink: canLink, repo: REPO }));
  } else if (machine.online && machine.issue != null) {
    const runText = machine.runStartedAt
      ? formatRunDuration(machine.runStartedAt, now)
      : '—';
    body.appendChild(fieldRow(d, '已跑時間', runText));
    if (machine.branch) {
      body.appendChild(fieldRow(d, '分支', String(machine.branch)));
    }
  }

  if (machine.online) {
    const cursor = formatCursorLine(machine.agentRunning);
    const cursorRow = fieldRow(d, 'Cursor', cursor);
    if (machine.agentRunning === true && cursorRow.querySelector) {
      const val = cursorRow.querySelector('.dev-field-value');
      if (val && val.classList) val.classList.add('is-running');
    }
    body.appendChild(cursorRow);
    const batt = formatBatteryLine(machine);
    if (batt) {
      body.appendChild(fieldRow(d, '電量', batt));
    }
  }

  body.appendChild(fieldRow(d, '最後回報', machine.lastSeen != null
    ? formatLastSeenAgo(machine.lastSeen, now)
    : formatLastSeenAgo(null, now)));

  card.appendChild(body);
  return card;
}

function renderMerged(root, timeline, now, canLink, doc) {
  const d = doc || (typeof document !== 'undefined' ? document : null);
  let block = root.querySelector('[data-dev-merged]');
  if (!block) {
    block = el('section', 'dev-merged-card maple-window');
    block.setAttribute('data-dev-merged', '1');
    root.appendChild(block);
  }
  block.replaceChildren();
  const head = el('header', 'maple-win-title');
  const h = el('h2', null);
  setText(h, '最近合併');
  head.appendChild(h);
  block.appendChild(head);

  const list = filterMergedToday(timeline, now);
  const body = el('div', 'dev-merged-body');
  const note = el('p', 'dev-merged-note');
  setText(note, '今日（台北）已合併 ' + list.length + ' 項');
  body.appendChild(note);

  if (!list.length) {
    const empty = el('p', 'dev-merged-empty');
    setText(empty, '今日尚無合併紀錄');
    body.appendChild(empty);
  } else {
    const ul = el('ul', 'dev-merged-list');
    list.slice(0, MERGED_PREVIEW).forEach((row) => {
      const li = el('li', 'dev-merged-item');
      const time = el('time', 'dev-merged-time');
      setText(time, formatTaipeiClock(row.at));
      li.appendChild(time);
      const ref = el('span', 'dev-merged-ref');
      if (row.url) {
        const a = el('a', 'dev-link');
        a.href = row.url;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        setText(a, row.ref || '');
        ref.appendChild(a);
      } else {
        setText(ref, row.ref || '');
      }
      li.appendChild(ref);
      const title = el('span', 'dev-merged-title');
      setText(title, row.title || '');
      li.appendChild(title);
      ul.appendChild(li);
    });
    body.appendChild(ul);
  }
  block.appendChild(body);
}

function renderMachines(root, payload, devStatus, now, doc) {
  const d = doc || (typeof document !== 'undefined' ? document : null);
  const machines = payload && Array.isArray(payload.machines) ? payload.machines : [];
  const canLink = viewerCanLink(payload && payload.viewer);
  root.replaceChildren();
  setGuestChrome(d, false);
  const grid = d.createElement('div');
  grid.className = 'dev-machines-grid';
  machines.forEach((m) => grid.appendChild(renderCard(d, m, now, { canLink: canLink })));
  root.appendChild(grid);
  const timeline = devStatus && Array.isArray(devStatus.timeline) ? devStatus.timeline : [];
  renderMerged(root, timeline, now, canLink, d);
  updateDevMachinesLoginFooter(d, canLink);
}

export function bindDevStatusTabs(doc, onTab) {
  if (!doc || !doc.querySelectorAll) return;
  const tabs = doc.querySelectorAll('[data-dev-tab]');
  tabs.forEach((btn) => {
    btn.addEventListener('click', () => {
      const id = btn.getAttribute('data-dev-tab') || 'machines';
      tabs.forEach((el) => {
        const active = el.getAttribute('data-dev-tab') === id;
        el.classList.toggle('is-active', active);
        el.setAttribute('aria-pressed', active ? 'true' : 'false');
      });
      if (typeof onTab === 'function') onTab(id);
    });
  });
}

export function createDevMachinesPage(opts) {
  const doc = (opts && opts.doc) || document;
  const win = (opts && opts.win) || (typeof window !== 'undefined' ? window : null);
  const fetchFn = (opts && opts.fetch) || (globalThis.fetch ? globalThis.fetch.bind(globalThis) : null);
  const root = doc.getElementById('devMachinesRoot');
  let timer = null;
  let lastGood = null;
  let lastDev = null;
  let activeTab = 'machines';
  let officeCtrl = null;

  function stopTimer() {
    if (timer != null) {
      clearInterval(timer);
      timer = null;
    }
  }

  function schedule() {
    stopTimer();
    timer = setInterval(() => refresh(), REFRESH_MS);
  }

  function onVisibility() {
    if (doc.hidden) stopTimer();
    else if (activeTab === 'machines') {
      refresh();
      schedule();
    } else if (officeCtrl) {
      officeCtrl.refresh();
    }
  }

  function setActiveTab(id) {
    activeTab = id === 'office' ? 'office' : 'machines';
    if (!doc.querySelectorAll) return;
    const tabs = doc.querySelectorAll('[data-dev-tab]');
    tabs.forEach((el) => {
      const active = el.getAttribute('data-dev-tab') === activeTab;
      el.classList.toggle('is-active', active);
      el.setAttribute('aria-pressed', active ? 'true' : 'false');
    });
  }

  function showMachinesView() {
    if (root) root.hidden = false;
    const host = doc.getElementById('labOfficeRoot');
    if (host) host.hidden = true;
    if (officeCtrl) officeCtrl.close();
  }

  function returnToMachineList() {
    if (activeTab !== 'office') return;
    setActiveTab('machines');
    if (root) root.hidden = false;
    refresh();
    if (!doc.hidden) schedule();
  }

  function showOfficeView() {
    if (!isAdminViewer()) {
      renderGuestLoginPanel(root, doc);
      return;
    }
    if (root) {
      root.replaceChildren();
      root.hidden = true;
    }
    if (!officeCtrl) {
      officeCtrl = createLabOfficeController({
        doc,
        win,
        hooks: { onClose: returnToMachineList }
      });
    }
    officeCtrl.open();
  }

  function onTab(id) {
    setActiveTab(id);
    if (activeTab === 'office') {
      stopTimer();
      showOfficeView();
    } else {
      showMachinesView();
      refresh();
      if (!doc.hidden) schedule();
    }
  }

  bindDevStatusTabs(doc, onTab);

  function updateGuestbookStatsHint() {
    const sub = doc.querySelector('.dev-machines-sub');
    if (!sub || !fetchFn || !isAdminViewer()) return;
    fetchFn('/api/guestbook/stats')
      .then((res) => (res.ok ? res.json() : null))
      .then((body) => {
        if (!body || body.success === false) return;
        const base = '每 30 秒更新 · 時間 台北';
        const tail = body.today > 0 ? ' · 簽名簿近 24h ' + body.today + ' 則' : '';
        setText(sub, base + tail);
        sub.hidden = false;
      })
      .catch(() => {});
  }

  function refresh() {
    if (!fetchFn || !root) return;
    if (activeTab === 'office') return;
    if (!isAdminViewer()) {
      renderGuestLoginPanel(root, doc);
      stopTimer();
      return;
    }
    updateGuestbookStatsHint();
    const now = Date.now();
    Promise.all([
      fetchFn('/api/dev-machines').then((res) => {
        if (res.status === 401) {
          stopTimer();
          renderGuestLoginPanel(root, doc);
          return null;
        }
        if (!res.ok) throw new Error('bad status');
        return res.json();
      }),
      fetchFn('/api/dev-status').then((res) => (res.ok ? res.json() : null)).catch(() => null)
    ])
      .then(([body, devBody]) => {
        if (!body) return;
        if (body.success === false) {
          renderErrorBanner(root, body.message || '暫時讀不到狀態');
          return;
        }
        lastGood = body;
        if (devBody) lastDev = devBody;
        renderErrorBanner(root, '');
        renderMachines(root, body, lastDev, now, doc);
      })
      .catch(() => {
        renderErrorBanner(root, '暫時讀不到狀態');
        if (lastGood && root) {
          renderMachines(root, lastGood, lastDev, Date.now(), doc);
        }
      });
  }

  if (!root) {
    return { refresh: () => {}, stop: stopTimer };
  }

  if (!isAdminViewer()) {
    renderGuestLoginPanel(root, doc);
    return { refresh: () => renderGuestLoginPanel(root, doc), stop: stopTimer };
  }

  if (doc && typeof doc.addEventListener === 'function') {
    doc.addEventListener('visibilitychange', onVisibility);
  }
  refresh();
  if (!doc.hidden) schedule();

  return { refresh, stop: stopTimer };
}

if (typeof document !== 'undefined' && document.getElementById('devMachinesRoot')) {
  mountDonateLinks(typeof window !== 'undefined' ? window : document.defaultView);
  createDevMachinesPage();
}