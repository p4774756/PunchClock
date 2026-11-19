import {
  filterMergedToday,
  formatBatteryLine,
  formatCursorLine,
  formatLastSeenAgo,
  formatMachineWork,
  formatRunDuration,
  formatTaipeiClock,
  machineRole
} from './dev-machines-format.js';

const REFRESH_MS = 30 * 1000;
const REPO = 'https://github.com/p4774756/PunchClock';
const MERGED_PREVIEW = 8;

function el(tag, className) {
  const node = document.createElement(tag);
  if (className) node.className = className;
  return node;
}

function setText(node, text) {
  node.textContent = text == null ? '' : String(text);
}

function renderLoginPrompt(root) {
  root.replaceChildren();
  const panel = el('section', 'dev-machines-guest maple-window');
  const msg = el('p', 'dev-machines-guest-msg');
  setText(msg, '請先登入管理員');
  const btn = el('a', 'dev-btn dev-btn-action dev-machines-login');
  btn.href = '/login';
  setText(btn, '管理員登入');
  panel.appendChild(msg);
  panel.appendChild(btn);
  root.appendChild(panel);
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

function fieldRow(label, valueNode) {
  const row = el('div', 'dev-field-row');
  const lab = el('span', 'dev-field-label');
  setText(lab, label);
  row.appendChild(lab);
  const val = el('div', 'dev-field-value');
  if (typeof valueNode === 'string') {
    setText(val, valueNode);
  } else if (valueNode) {
    val.appendChild(valueNode);
  }
  row.appendChild(val);
  return row;
}

function renderCard(doc, machine, now) {
  const card = el('article', 'dev-machine-card maple-window');
  const chrome = el('header', 'maple-win-title');
  const title = el('h2', 'dev-machine-name');
  setText(title, machine.label || machine.machine);
  const badge = el('span', 'dev-badge ' + (machine.online ? 'is-online' : 'is-offline'));
  setText(badge, machine.online ? '在線' : '離線');
  chrome.appendChild(title);
  chrome.appendChild(badge);
  card.appendChild(chrome);

  const body = el('div', 'dev-machine-body');
  const role = machineRole(machine);
  if (role) {
    const roleEl = el('p', 'dev-machine-role');
    setText(roleEl, role);
    body.appendChild(roleEl);
  }

  const work = formatMachineWork(machine, REPO);
  const workVal = work.href
    ? (() => {
        const a = el('a', 'dev-link');
        a.href = work.href;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        setText(a, work.text);
        return a;
      })()
    : (machine.online ? work.text : '—');
  body.appendChild(fieldRow('工作', workVal));

  const runText = (machine.online && machine.issue != null && machine.runStartedAt)
    ? formatRunDuration(machine.runStartedAt, now)
    : '—';
  body.appendChild(fieldRow('已跑時間', runText));

  if (machine.online) {
    const cursor = formatCursorLine(machine.agentRunning);
    const cursorRow = fieldRow('Cursor', cursor);
    if (machine.agentRunning === true) {
      cursorRow.querySelector('.dev-field-value').classList.add('is-running');
    }
    body.appendChild(cursorRow);
    const batt = formatBatteryLine(machine);
    if (batt) {
      body.appendChild(fieldRow('電量', batt));
    }
  }

  body.appendChild(fieldRow('最後回報', machine.lastSeen != null
    ? formatLastSeenAgo(machine.lastSeen, now)
    : formatLastSeenAgo(null, now)));

  if (machine.online && machine.branch) {
    body.appendChild(fieldRow('分支', String(machine.branch)));
  }
  if (machine.online && machine.commit) {
    const short = String(machine.commit).slice(0, 7);
    body.appendChild(fieldRow('提交', short));
  }

  card.appendChild(body);
  return card;
}

function renderMerged(root, timeline, now) {
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

function renderMachines(root, payload, devStatus, now) {
  const machines = payload && Array.isArray(payload.machines) ? payload.machines : [];
  root.replaceChildren();
  const grid = el('div', 'dev-machines-grid');
  machines.forEach((m) => grid.appendChild(renderCard(document, m, now)));
  root.appendChild(grid);
  const timeline = devStatus && Array.isArray(devStatus.timeline) ? devStatus.timeline : [];
  renderMerged(root, timeline, now);
}

export function createDevMachinesPage(opts) {
  const doc = (opts && opts.doc) || document;
  const fetchFn = (opts && opts.fetch) || (globalThis.fetch ? globalThis.fetch.bind(globalThis) : null);
  const root = doc.getElementById('devMachinesRoot');
  let timer = null;
  let lastGood = null;
  let lastDev = null;

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
    else {
      refresh();
      schedule();
    }
  }

  function refresh() {
    if (!fetchFn || !root) return;
    const now = Date.now();
    Promise.all([
      fetchFn('/api/dev-machines').then((res) => {
        if (res.status === 401) return { auth: false };
        if (!res.ok) throw new Error('bad status');
        return res.json().then((body) => ({ auth: true, body }));
      }),
      fetchFn('/api/dev-status').then((res) => (res.ok ? res.json() : null)).catch(() => null)
    ])
      .then(([machinesResult, devBody]) => {
        if (!machinesResult.auth) {
          renderLoginPrompt(root);
          renderErrorBanner(root, '');
          lastGood = null;
          lastDev = null;
          return;
        }
        const body = machinesResult.body;
        if (body.success === false) {
          renderLoginPrompt(root);
          return;
        }
        lastGood = body;
        if (devBody) lastDev = devBody;
        renderErrorBanner(root, '');
        renderMachines(root, body, lastDev, now);
      })
      .catch(() => {
        renderErrorBanner(root, '暫時讀不到狀態');
        if (lastGood && root) {
          renderMachines(root, lastGood, lastDev, Date.now());
        }
      });
  }

  if (doc && typeof doc.addEventListener === 'function') {
    doc.addEventListener('visibilitychange', onVisibility);
  }
  refresh();
  if (!doc.hidden) schedule();

  return { refresh, stop: stopTimer };
}

if (typeof document !== 'undefined' && document.getElementById('devMachinesRoot')) {
  createDevMachinesPage();
}