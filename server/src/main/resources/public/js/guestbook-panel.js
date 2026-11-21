import {
  GUESTBOOK_BRUSHES,
  GUESTBOOK_COLORS,
  GUESTBOOK_DOODLE_HEIGHT,
  GUESTBOOK_DOODLE_WIDTH,
  DEFAULT_PAGE_SIZE,
  emojiGlyph,
  formatGuestDate,
  isBlankCanvas,
  normalizeMessage,
  normalizeNickname
} from './guestbook-logic.js';

export function createGuestbookController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis.window;
  const hooks = (opts && opts.hooks) || {};
  const fetchFn = (opts && opts.fetch) || (win && win.fetch ? win.fetch.bind(win) : null);
  const root = doc.getElementById('guestbookBoard');
  const paint = root && root.querySelector('[data-guestbook-paint]');
  const nicknameInput = root && root.querySelector('[data-guestbook-nickname]');
  const messageInput = root && root.querySelector('[data-guestbook-message]');
  const honeypot = root && root.querySelector('[data-guestbook-honeypot]');
  const statusEl = root && root.querySelector('[data-guestbook-status]');
  const listEl = root && root.querySelector('[data-guestbook-list]');
  const submitBtn = root && root.querySelector('[data-guestbook-submit]');
  const closeBtn = root && root.querySelector('[data-guestbook-close]');
  const pageLabel = root && root.querySelector('[data-guestbook-page]');
  const prevBtn = root && root.querySelector('[data-guestbook-prev]');
  const nextBtn = root && root.querySelector('[data-guestbook-next]');
  const authed = !!(win && win.PUNCHCLOCK_AUTHENTICATED);
  let openState = false;
  let color = GUESTBOOK_COLORS[0];
  let brush = GUESTBOOK_BRUSHES[0];
  let erasing = false;
  let drawing = false;
  let last = null;
  let selectedEmoji = '';
  let page = 1;
  let totalPages = 1;
  let loading = false;

  function setStatus(text) {
    if (statusEl) statusEl.textContent = text || '';
  }

  function ctx2d() {
    return paint ? paint.getContext('2d') : null;
  }

  function clearCanvas() {
    const ctx = ctx2d();
    if (!ctx || !paint) return;
    ctx.clearRect(0, 0, paint.width, paint.height);
  }

  function pointer(ev) {
    const rect = paint.getBoundingClientRect();
    const sx = paint.width / rect.width;
    const sy = paint.height / rect.height;
    return {
      x: (ev.clientX - rect.left) * sx,
      y: (ev.clientY - rect.top) * sy
    };
  }

  function stroke(from, to) {
    const ctx = ctx2d();
    if (!ctx) return;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.lineWidth = erasing ? brush + 4 : brush;
    ctx.globalCompositeOperation = erasing ? 'destination-out' : 'source-over';
    ctx.strokeStyle = erasing ? 'rgba(0,0,0,1)' : color;
    ctx.beginPath();
    ctx.moveTo(from.x, from.y);
    ctx.lineTo(to.x, to.y);
    ctx.stroke();
    ctx.globalCompositeOperation = 'source-over';
  }

  function renderTools() {
    if (!root) return;
    root.querySelectorAll('[data-guestbook-color]').forEach((btn) => {
      const on = btn.getAttribute('data-guestbook-color') === color && !erasing;
      btn.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    root.querySelectorAll('[data-guestbook-brush]').forEach((btn) => {
      const on = Number(btn.getAttribute('data-guestbook-brush')) === brush && !erasing;
      btn.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    const eraser = root.querySelector('[data-guestbook-eraser]');
    if (eraser) eraser.setAttribute('aria-pressed', erasing ? 'true' : 'false');
    root.querySelectorAll('[data-guestbook-emoji]').forEach((btn) => {
      const key = btn.getAttribute('data-guestbook-emoji');
      btn.setAttribute('aria-pressed', key === selectedEmoji ? 'true' : 'false');
    });
  }

  function setOpen(want) {
    const next = !!want;
    if (next === openState) return;
    openState = next;
    if (root) {
      root.hidden = !next;
      root.setAttribute('aria-hidden', next ? 'false' : 'true');
    }
    if (doc.body) doc.body.classList.toggle('guestbook-open', next);
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: next });
    if (next) {
      setStatus('');
      page = 1;
      clearCanvas();
      if (nicknameInput) nicknameInput.value = '';
      if (messageInput) messageInput.value = '';
      if (honeypot) honeypot.value = '';
      selectedEmoji = '';
      renderTools();
      loadPage();
      const focusEl = nicknameInput || closeBtn;
      if (focusEl && focusEl.focus) focusEl.focus();
    }
  }

  function renderList(items) {
    if (!listEl) return;
    listEl.replaceChildren();
    const list = items || [];
    if (!list.length) {
      const empty = doc.createElement('p');
      empty.className = 'guestbook-empty';
      empty.textContent = '還沒有留言，寫下第一則吧';
      listEl.appendChild(empty);
      return;
    }
    for (let i = 0; i < list.length; i++) {
      const item = list[i];
      const card = doc.createElement('article');
      card.className = 'guestbook-entry' + (item.hidden ? ' is-hidden' : '');
      const head = doc.createElement('div');
      head.className = 'guestbook-entry-head';
      const name = doc.createElement('strong');
      name.textContent = item.nickname || '';
      head.appendChild(name);
      if (item.emoji) {
        const em = doc.createElement('span');
        em.className = 'guestbook-entry-emoji';
        em.textContent = emojiGlyph(item.emoji);
        head.appendChild(em);
      }
      const when = doc.createElement('time');
      when.textContent = formatGuestDate(item.createdAt);
      head.appendChild(when);
      card.appendChild(head);
      const body = doc.createElement('p');
      body.className = 'guestbook-entry-msg';
      body.textContent = item.message || '';
      card.appendChild(body);
      if (item.doodleUrl) {
        const img = doc.createElement('img');
        img.src = item.doodleUrl;
        img.alt = '';
        img.width = GUESTBOOK_DOODLE_WIDTH;
        img.height = GUESTBOOK_DOODLE_HEIGHT;
        img.className = 'guestbook-entry-doodle';
        img.loading = 'lazy';
        img.decoding = 'async';
        card.appendChild(img);
      }
      if (authed) {
        const mod = doc.createElement('div');
        mod.className = 'guestbook-mod';
        const hideBtn = doc.createElement('button');
        hideBtn.type = 'button';
        hideBtn.className = 'btn btn-ghost guestbook-mod-btn';
        hideBtn.textContent = '隱藏';
        hideBtn.addEventListener('click', () => moderate(item.id, 'hide'));
        const delBtn = doc.createElement('button');
        delBtn.type = 'button';
        delBtn.className = 'btn btn-ghost guestbook-mod-btn';
        delBtn.textContent = '刪除';
        delBtn.addEventListener('click', () => moderate(item.id, 'delete'));
        mod.appendChild(hideBtn);
        mod.appendChild(delBtn);
        card.appendChild(mod);
      }
      listEl.appendChild(card);
    }
  }

  function updatePager() {
    if (pageLabel) pageLabel.textContent = ' ' + page + '/' + totalPages + ' ';
    if (prevBtn) prevBtn.disabled = page <= 1;
    if (nextBtn) nextBtn.disabled = page >= totalPages;
  }

  async function loadPage() {
    if (!fetchFn || loading) return;
    loading = true;
    try {
      const res = await fetchFn('/api/guestbook?page=' + encodeURIComponent(page) + '&limit=' + DEFAULT_PAGE_SIZE);
      const body = await res.json();
      if (body && body.success !== false) {
        page = body.page || page;
        totalPages = body.totalPages || 1;
        renderList(body.items);
        updatePager();
      }
    } catch (ignored) {
      setStatus('讀取留言失敗');
    } finally {
      loading = false;
    }
  }

  async function moderate(id, action) {
    if (!fetchFn || !id) return;
    const url = action === 'hide' ? '/api/guestbook/' + encodeURIComponent(id) + '/hide' : '/api/guestbook/' + encodeURIComponent(id);
    const method = action === 'hide' ? 'POST' : 'DELETE';
    try {
      const res = await fetchFn(url, { method });
      const body = await res.json().catch(() => ({}));
      if (res.ok) {
        setStatus(body.message || '已處理');
        loadPage();
      } else {
        setStatus((body && body.message) || '無法處理');
      }
    } catch (ignored) {
      setStatus('無法處理');
    }
  }

  async function submit() {
    if (!fetchFn) return;
    const nickname = normalizeNickname(nicknameInput && nicknameInput.value);
    const message = normalizeMessage(messageInput && messageInput.value);
    if (!nickname) {
      setStatus('請填暱稱（最多 20 字）');
      return;
    }
    if (!message) {
      setStatus('請填留言（最多 200 字）');
      return;
    }
    const payload = {
      nickname,
      message,
      emoji: selectedEmoji || '',
      website: (honeypot && honeypot.value) || ''
    };
    const ctx = ctx2d();
    if (ctx && paint && !isBlankCanvas(ctx.getImageData(0, 0, paint.width, paint.height))) {
      const dataUrl = paint.toDataURL('image/png');
      payload.doodle = dataUrl;
    }
    setStatus('送出中…');
    if (submitBtn) submitBtn.disabled = true;
    try {
      const res = await fetchFn('/api/guestbook', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      const body = await res.json().catch(() => ({}));
      if (res.ok) {
        setStatus(body.message || '已簽名');
        clearCanvas();
        if (messageInput) messageInput.value = '';
        page = 1;
        loadPage();
      } else {
        setStatus((body && body.message) || '無法送出');
      }
    } catch (ignored) {
      setStatus('無法送出');
    } finally {
      if (submitBtn) submitBtn.disabled = false;
    }
  }

  function bindPaint() {
    if (!paint) return;
    const down = (ev) => {
      if (!openState) return;
      ev.preventDefault();
      drawing = true;
      last = pointer(ev);
    };
    const move = (ev) => {
      if (!drawing || !last) return;
      ev.preventDefault();
      const pt = pointer(ev);
      stroke(last, pt);
      last = pt;
    };
    const up = () => {
      drawing = false;
      last = null;
    };
    paint.addEventListener('pointerdown', down);
    paint.addEventListener('pointermove', move);
    paint.addEventListener('pointerup', up);
    paint.addEventListener('pointerleave', up);
    paint.addEventListener('pointercancel', up);
  }

  function bindUi() {
    if (!root) return;
    if (closeBtn) closeBtn.addEventListener('click', () => setOpen(false));
    if (submitBtn) submitBtn.addEventListener('click', submit);
    if (prevBtn) prevBtn.addEventListener('click', () => {
      if (page > 1) {
        page -= 1;
        loadPage();
      }
    });
    if (nextBtn) nextBtn.addEventListener('click', () => {
      if (page < totalPages) {
        page += 1;
        loadPage();
      }
    });
    root.querySelectorAll('[data-guestbook-color]').forEach((btn) => {
      btn.addEventListener('click', () => {
        color = btn.getAttribute('data-guestbook-color');
        erasing = false;
        renderTools();
      });
    });
    root.querySelectorAll('[data-guestbook-brush]').forEach((btn) => {
      btn.addEventListener('click', () => {
        brush = Number(btn.getAttribute('data-guestbook-brush'));
        erasing = false;
        renderTools();
      });
    });
    const eraser = root.querySelector('[data-guestbook-eraser]');
    if (eraser) {
      eraser.addEventListener('click', () => {
        erasing = !erasing;
        renderTools();
      });
    }
    const clearBtn = root.querySelector('[data-guestbook-clear]');
    if (clearBtn) clearBtn.addEventListener('click', () => clearCanvas());
    root.querySelectorAll('[data-guestbook-emoji]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const key = btn.getAttribute('data-guestbook-emoji');
        selectedEmoji = selectedEmoji === key ? '' : key;
        renderTools();
      });
    });
    doc.addEventListener('keydown', (ev) => {
      if (!openState) return;
      if (ev.key === 'Escape') setOpen(false);
    });
    doc.addEventListener('click', (ev) => {
      const trigger = ev.target && ev.target.closest ? ev.target.closest('[data-open-guestbook]') : null;
      if (!trigger) return;
      ev.preventDefault();
      setOpen(true);
    });
  }

  renderTools();
  bindPaint();
  bindUi();

  return {
    open: () => setOpen(true),
    close: () => setOpen(false),
    toggle: () => setOpen(!openState),
    isOpen: () => openState
  };
}