import {
  formatBatteryLine,
  formatCursorLine,
  formatLastSeenAgo,
  formatMachineWork,
  formatRunDuration
} from './dev-machines-format.js';

const REFRESH_MS = 30 * 1000;
const REPO = 'https://github.com/p4774756/PunchClock';

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
  const panel = el('section', 'dev-machines-guest');
  const msg = el('p', 'dev-machines-guest-msg');
  setText(msg, '請先登入管理員');
  const btn = el('a', 'btn btn-primary dev-machines-login');
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

function renderCard(doc, machine, now) {
  const card = el('article', 'dev-machine-card');
  const head = el('header', 'dev-machine-head');
  const dot = el('span', 'dev-machine-dot ' + (machine.online ? 'is-online' : 'is-offline'));
  dot.setAttribute('aria-hidden', 'true');
  const status = el('span', 'dev-machine-status-text');
  setText(status, machine.online ? '在線' : '離線');
  const title = el('h2', 'dev-machine-name');
  setText(title, machine.label || machine.machine);
  head.appendChild(dot);
  head.appendChild(status);
  head.appendChild(title);
  card.appendChild(head);

  const last = el('p', 'dev-machine-last');
  const agoLabel = machine.lastSeen != null
    ? '最後回報 ' + formatLastSeenAgo(machine.lastSeen, now)
  : formatLastSeenAgo(null, now);
  setText(last, agoLabel);
  card.appendChild(last);

  const workRow = el('p', 'dev-machine-work');
  const work = formatMachineWork(machine, REPO);
  if (work.href) {
    const a = el('a', 'dev-machine-issue-link');
    a.href = work.href;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
    setText(a, work.text);
    workRow.appendChild(a);
  } else {
    setText(workRow, machine.online ? work.text : '—');
  }
  card.appendChild(workRow);

  if (machine.online && machine.issue != null && machine.runStartedAt) {
    const run = el('p', 'dev-machine-run');
    setText(run, formatRunDuration(machine.runStartedAt, now));
    card.appendChild(run);
  }

  if (machine.online) {
    const cursor = el('p', 'dev-machine-cursor');
    setText(cursor, formatCursorLine(machine.agentRunning));
    card.appendChild(cursor);
    const batt = formatBatteryLine(machine);
    if (batt) {
      const bat = el('p', 'dev-machine-battery');
      setText(bat, batt);
      card.appendChild(bat);
    }
  }

  return card;
}

function renderMachines(root, payload, now) {
  const machines = payload && Array.isArray(payload.machines) ? payload.machines : [];
  root.replaceChildren();
  const grid = el('div', 'dev-machines-grid');
  machines.forEach((m) => grid.appendChild(renderCard(document, m, now)));
  root.appendChild(grid);
}

export function createDevMachinesPage(opts) {
  const doc = (opts && opts.doc) || document;
  const fetchFn = (opts && opts.fetch) || (globalThis.fetch ? globalThis.fetch.bind(globalThis) : null);
  const root = doc.getElementById('devMachinesRoot');
  let timer = null;
  let lastGood = null;

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
    fetchFn('/api/dev-machines')
      .then((res) => {
        if (res.status === 401) {
          renderLoginPrompt(root);
          renderErrorBanner(root, '');
          lastGood = null;
          return null;
        }
        if (!res.ok) throw new Error('bad status');
        return res.json();
      })
      .then((body) => {
        if (!body) return;
        if (body.success === false) {
          renderLoginPrompt(root);
          return;
        }
        lastGood = body;
        renderErrorBanner(root, '');
        renderMachines(root, body, Date.now());
      })
      .catch(() => {
        renderErrorBanner(root, '暫時讀不到狀態');
        if (lastGood && root) {
          renderMachines(root, lastGood, Date.now());
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