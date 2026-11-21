// 虛擬辦公室視圖（獨立頁與開發機狀態分頁共用，不依賴島嶼／Three.js）

import {
  CHARACTER_IDS,
  LAB_OFFICE_POLL_MS,
  OFFICE_PROP_POS,
  SPOT_POS,
  buildAllCharacterStates,
  buildCharacterStatesFromPublic,
  characterCardData,
  viewerCanOpenLabOffice
} from './lab-office-logic.js';

const CHAR_LABEL = Object.freeze({
  oki: '軍師 Oki',
  tiger: '猛虎工程師',
  reviewer: '獨立審核員'
});

function setText(node, text) {
  if (node) node.textContent = text == null ? '' : String(text);
}

function posForSpot(spot) {
  return SPOT_POS[spot] || SPOT_POS.sofa;
}

const FRAME_HTML =
  '<div class="lab-office-frame">' +
  '<header class="lab-office-head">' +
  '<h2>虛擬辦公室</h2>' +
  '<button type="button" class="lab-office-close" data-lab-office-close aria-label="關閉">×</button>' +
  '</header>' +
  '<div class="lab-office-stage" data-lab-office-stage>' +
  '<img class="lab-office-bg" src="/assets/lab-2f/office-bg.webp" width="1280" height="720" alt="">' +
  '<div class="lab-office-chars" data-lab-office-chars></div>' +
  '</div>' +
  '<p class="lab-office-foot" data-lab-office-foot>每 30 秒更新 · 僅管理員</p>' +
  '<div class="lab-office-card-backdrop" data-lab-office-card hidden>' +
  '<div class="lab-office-card" role="dialog" aria-label="角色詳情"></div>' +
  '</div>' +
  '</div>';

function ensureOverlay(doc) {
  let root = doc.getElementById('labOfficeRoot');
  if (root) return root;
  root = doc.createElement('section');
  root.id = 'labOfficeRoot';
  root.className = 'lab-office-root';
  root.hidden = true;
  root.setAttribute('role', 'dialog');
  root.setAttribute('aria-modal', 'true');
  root.setAttribute('aria-label', '虛擬辦公室');
  root.innerHTML = FRAME_HTML;
  doc.body.appendChild(root);
  return root;
}

export function createLabOfficeController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const hooks = (opts && opts.hooks) || {};
  const pageMode = !!(opts && opts.page);
  const allowGuest = opts && opts.allowGuest !== false;
  const fetchFn = (opts && opts.fetch) || (win && win.fetch ? win.fetch.bind(win) : null);
  const root = (opts && opts.existingRoot) || ensureOverlay(doc);
  const stageWrap = root.querySelector('[data-lab-office-stage]') || root.querySelector('.lab-office-stage');
  const stage = root.querySelector('[data-lab-office-chars]');
  const cardLayer = root.querySelector('[data-lab-office-card]');
  const cardInner = cardLayer && cardLayer.querySelector('.lab-office-card');
  const closeBtn = root.querySelector('[data-lab-office-close]');
  const foot = root.querySelector('[data-lab-office-foot]');
  const guest = root.querySelector('[data-office-guest]');
  let openState = false;
  let timer = null;
  let machines = null;
  let devStatus = null;
  let publicPayload = null;

  const PROP_LINK = Object.freeze({
    guestbook: null,
    clock: '/download#checkin',
    bookshelf: '/atlas/tcm-3d/',
    whiteboard: null,
    desk: '/download'
  });

  function stopTimer() {
    if (timer != null) {
      clearInterval(timer);
      timer = null;
    }
  }

  function isAuthed() {
    return viewerCanOpenLabOffice(win && win.PUNCHCLOCK_AUTHENTICATED === true);
  }

  function renderProps() {
    if (!stageWrap) return;
    let layer = stageWrap.querySelector('[data-lab-office-props]');
    if (!layer) {
      layer = doc.createElement('div');
      layer.className = 'lab-office-props';
      layer.setAttribute('data-lab-office-props', '');
      stageWrap.appendChild(layer);
    }
    layer.replaceChildren();
    const ids = Object.keys(OFFICE_PROP_POS);
    for (let i = 0; i < ids.length; i++) {
      const id = ids[i];
      const spec = OFFICE_PROP_POS[id];
      let href = PROP_LINK[id] || '#';
      if (id === 'whiteboard') {
        href = isAuthed() ? '/dev-status' : '/login?next=%2Fdev-status';
      }
      if (id === 'guestbook') {
        const btn = doc.createElement('button');
        btn.type = 'button';
        btn.className = 'lab-office-prop lab-office-prop-guestbook';
        btn.style.left = spec.x + '%';
        btn.style.top = spec.y + '%';
        btn.setAttribute('data-open-guestbook', '');
        btn.setAttribute('aria-label', spec.label);
        const tag = doc.createElement('span');
        tag.className = 'lab-office-prop-label';
        setText(tag, spec.label);
        btn.appendChild(tag);
        layer.appendChild(btn);
        continue;
      }
      const el = doc.createElement(id === 'whiteboard' ? 'div' : 'a');
      el.className = 'lab-office-prop';
      el.style.left = spec.x + '%';
      el.style.top = spec.y + '%';
      const tag = doc.createElement('span');
      tag.className = 'lab-office-prop-label';
      setText(tag, spec.label);
      if (id === 'whiteboard') {
        const main = doc.createElement('a');
        main.className = 'lab-office-prop-main';
        main.href = href;
        main.setAttribute('aria-label', '白板：開發狀態');
        main.appendChild(tag);
        const sub = doc.createElement('a');
        sub.className = 'lab-office-prop-label lab-office-prop-sub';
        sub.href = isAuthed() ? '/console' : '/login?next=%2Fconsole';
        sub.textContent = '主控台';
        el.setAttribute('aria-label', '白板：開發狀態與主控台');
        el.appendChild(main);
        el.appendChild(sub);
      } else {
        el.href = href;
        el.setAttribute('aria-label', spec.label);
        el.appendChild(tag);
      }
      layer.appendChild(el);
    }
  }

  function renderChars() {
    if (!stage) return;
    const now = Date.now();
    const states = isAuthed()
      ? buildAllCharacterStates({ machines, devStatus, now })
      : buildCharacterStatesFromPublic(publicPayload, now);
    stage.replaceChildren();
    for (let i = 0; i < states.length; i++) {
      const st = states[i];
      const id = st.id;
      const p = posForSpot(st.spot);
      const btn = doc.createElement('button');
      btn.type = 'button';
      btn.className = 'lab-office-char' + (st.mode === 'work' ? ' is-work' : '');
      btn.style.left = p.x + '%';
      btn.style.top = p.y + '%';
      btn.style.zIndex = String(10 + Math.round(p.y));
      btn.setAttribute('data-char', id);
      btn.setAttribute('aria-label', CHAR_LABEL[id] || id);
      const img = doc.createElement('img');
      img.src = '/assets/lab-2f/chibi-' + id + '.png';
      img.alt = '';
      img.decoding = 'async';
      btn.appendChild(img);
      const bubble = doc.createElement('span');
      bubble.className = 'lab-office-bubble';
      setText(bubble, st.bubble);
      btn.appendChild(bubble);
      btn.addEventListener('click', () => openCard(id));
      stage.appendChild(btn);
    }
    renderProps();
    if (foot) {
      const modeLabel = isAuthed() ? '管理員' : '訪客';
      setText(foot, '每 30 秒更新 · ' + modeLabel + ' · ' + new Date(now).toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit' }));
    }
  }

  function openCard(charId) {
    if (!cardLayer || !cardInner) return;
    if (!isAuthed()) {
      const row = publicPayload && publicPayload.characters ? publicPayload.characters[charId] : null;
      cardInner.replaceChildren();
      const body = doc.createElement('div');
      body.className = 'lab-office-card-body';
      const h = doc.createElement('h3');
      setText(h, CHAR_LABEL[charId] || charId);
      body.appendChild(h);
      const p = doc.createElement('p');
      p.className = 'lab-office-card-line';
      setText(p, (row && row.status) || '待機');
      body.appendChild(p);
      if (row && row.updatedAt) {
        const t = doc.createElement('p');
        t.className = 'lab-office-card-line';
        setText(t, '更新 ' + new Date(row.updatedAt).toLocaleString('zh-TW'));
        body.appendChild(t);
      }
      cardInner.appendChild(body);
      cardLayer.hidden = false;
      return;
    }
    const data = characterCardData(charId, { machines, devStatus, now: Date.now() });
    cardInner.replaceChildren();
    const portrait = doc.createElement('img');
    portrait.className = 'lab-office-portrait';
    portrait.src = data.portrait;
    portrait.alt = '';
    const body = doc.createElement('div');
    body.className = 'lab-office-card-body';
    const h = doc.createElement('h3');
    setText(h, CHAR_LABEL[charId] || charId);
    body.appendChild(h);
    const lines = [];
    if (data.machine) {
      lines.push(data.machine.online ? '在線' : '離線');
      if (data.machine.battery != null) lines.push('電量 ' + data.machine.battery + '%');
      if (data.machine.charging) lines.push('充電中');
      if (data.machine.issue) lines.push('任務 #' + data.machine.issue);
      if (data.machine.branch) lines.push(data.machine.branch);
      if (data.machine.commit) lines.push('commit ' + data.machine.commit);
      if (data.machine.model) lines.push('模型 ' + data.machine.model);
      if (data.state && data.state.bubble) lines.push(data.state.bubble);
    } else if (charId === 'oki' && data.dev && data.dev.now) {
      const q = data.dev.now.queue ? data.dev.now.queue.length : 0;
      lines.push('佇列 ' + q);
      if (data.dev.now.current && data.dev.now.current.issue) {
        lines.push('#' + data.dev.now.current.issue + ' ' + (data.dev.now.current.title || ''));
      }
    } else {
      lines.push((data.state && data.state.bubble) || '待機');
    }
    for (let i = 0; i < lines.length; i++) {
      const p = doc.createElement('p');
      p.className = 'lab-office-card-line';
      setText(p, lines[i]);
      body.appendChild(p);
    }
    cardInner.appendChild(portrait);
    cardInner.appendChild(body);
    cardLayer.hidden = false;
  }

  function closeCard() {
    if (cardLayer) cardLayer.hidden = true;
  }

  function refresh() {
    if (!fetchFn || !openState) return;
    if (isAuthed()) {
      Promise.all([
        fetchFn('/api/dev-machines').then((r) => (r.ok ? r.json() : null)).catch(() => null),
        fetchFn('/api/dev-status').then((r) => (r.ok ? r.json() : null)).catch(() => null)
      ]).then(([m, d]) => {
        if (!openState) return;
        if (m && m.success !== false) machines = m;
        if (d) devStatus = d;
        renderChars();
      });
      return;
    }
    fetchFn('/api/office/public')
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null)
      .then((payload) => {
        if (!openState) return;
        if (payload && payload.success !== false) publicPayload = payload;
        renderChars();
      });
  }

  function schedule() {
    stopTimer();
    if (!openState) return;
    timer = setInterval(refresh, LAB_OFFICE_POLL_MS);
  }

  function setOpen(want) {
    const next = !!want;
    if (next === openState && next) return;
    openState = next;
    if (!pageMode) {
      root.hidden = !next;
      root.setAttribute('aria-hidden', next ? 'false' : 'true');
    }
    if (next) {
      if (!isAuthed() && !(pageMode && allowGuest)) {
        openState = false;
        if (!pageMode) {
          root.hidden = true;
        }
        if (typeof hooks.onGuest === 'function') hooks.onGuest();
        return;
      }
      if (guest) guest.hidden = true;
      closeCard();
      renderChars();
      refresh();
      schedule();
      if (typeof hooks.onOpen === 'function') hooks.onOpen();
    } else {
      stopTimer();
      closeCard();
      if (typeof hooks.onClose === 'function') hooks.onClose();
    }
  }

  if (closeBtn && closeBtn.tagName === 'BUTTON') {
    closeBtn.addEventListener('click', () => setOpen(false));
  }
  if (cardLayer) {
    cardLayer.addEventListener('click', (ev) => {
      if (ev.target === cardLayer) closeCard();
    });
  }
  doc.addEventListener('keydown', (ev) => {
    if (!openState) return;
    if (ev.key === 'Escape') {
      if (cardLayer && !cardLayer.hidden) closeCard();
      else if (!pageMode) setOpen(false);
    }
  });

  return {
    open: () => setOpen(true),
    close: () => setOpen(false),
    toggle: () => setOpen(!openState),
    isOpen: () => openState,
    refresh,
    renderChars,
    destroy() {
      stopTimer();
      setOpen(false);
    }
  };
}

export { CHARACTER_IDS };
