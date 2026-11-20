// 塗鴉牆純邏輯（不依賴 Three.js）。署名整理、空白畫布、牆面格子供 node:test。
// 面板本身用 pointer events，手機上畫的時候不讓頁面跟著捲。

export const GRAFFITI_WIDTH = 512;
export const GRAFFITI_HEIGHT = 256;
export const WALL_SLOTS = 12;
export const SIGNATURE_MAX = 12;
export const ANONYMOUS_SIGNATURE = '路過的旅人';
export const GRAFFITI_COLORS = Object.freeze([
  '#1f2430', '#f06a2a', '#1f4d73', '#f4a0c0', '#7ec8ea', '#9fd9b4', '#f6d35a', '#ffffff'
]);
export const GRAFFITI_BRUSHES = Object.freeze([4, 14, 28]);

export function normalizeSignature(raw) {
  const cleaned = String(raw == null ? '' : raw).replace(/[\u0000-\u001F\u007F]/g, '').trim();
  if (!cleaned) return ANONYMOUS_SIGNATURE;
  if (Array.from(cleaned).length > SIGNATURE_MAX) return null;
  return cleaned;
}

export function isBlankCanvas(imageData) {
  const data = imageData && imageData.data;
  if (!data || !data.length) return true;
  for (let i = 3; i < data.length; i += 4) {
    if (data[i] !== 0) return false;
  }
  return true;
}

/** 最新的一批貼上牆，不足的格子是 null（空白磚）。 */
export function wallSlots(items, count) {
  const n = count == null ? WALL_SLOTS : count;
  const list = items || [];
  const slots = [];
  for (let i = 0; i < n; i++) slots.push(list[i] || null);
  return slots;
}

export function formatGraffitiDate(ms) {
  const date = new Date(ms);
  if (Number.isNaN(date.getTime())) return '';
  const p = (n) => String(n).padStart(2, '0');
  return date.getFullYear() + '-' + p(date.getMonth() + 1) + '-' + p(date.getDate())
    + ' ' + p(date.getHours()) + ':' + p(date.getMinutes());
}

export function createGraffitiController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis.window;
  const hooks = (opts && opts.hooks) || {};
  const root = doc.getElementById('graffitiBoard');
  const paint = root && root.querySelector('[data-graffiti-paint]');
  const signatureInput = root && root.querySelector('[data-graffiti-signature]');
  const statusEl = root && root.querySelector('[data-graffiti-status]');
  const listEl = root && root.querySelector('[data-graffiti-list]');
  const submitBtn = root && root.querySelector('[data-graffiti-submit]');
  const backBtn = root && root.querySelector('[data-graffiti-back]');
  const authed = !!(win && win.PUNCHCLOCK_AUTHENTICATED);
  let openState = false;
  let color = GRAFFITI_COLORS[0];
  let brush = GRAFFITI_BRUSHES[1];
  let erasing = false;
  let drawing = false;
  let last = null;
  const undos = [];
  let nextBefore = null;
  let loading = false;
  let listDone = false;
  const seen = Object.create(null);

  function setStatus(text) {
    if (statusEl) statusEl.textContent = text || '';
  }

  function ctx2d() {
    return paint ? paint.getContext('2d') : null;
  }

  function snapshot() {
    const ctx = ctx2d();
    if (!ctx) return;
    if (undos.length > 24) undos.shift();
    undos.push(ctx.getImageData(0, 0, paint.width, paint.height));
  }

  function blank() {
    const ctx = ctx2d();
    if (!ctx) return true;
    return isBlankCanvas(ctx.getImageData(0, 0, paint.width, paint.height));
  }

  function renderTools() {
    if (!root) return;
    root.querySelectorAll('[data-graffiti-color]').forEach((btn) => {
      const on = btn.getAttribute('data-graffiti-color') === color && !erasing;
      btn.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    root.querySelectorAll('[data-graffiti-brush]').forEach((btn) => {
      const on = Number(btn.getAttribute('data-graffiti-brush')) === brush && !erasing;
      btn.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    const eraser = root.querySelector('[data-graffiti-eraser]');
    if (eraser) eraser.setAttribute('aria-pressed', erasing ? 'true' : 'false');
  }

  function setOpen(next) {
    const want = !!next;
    if (want === openState) return;
    openState = want;
    if (root) {
      root.hidden = !want;
      root.setAttribute('aria-hidden', want ? 'false' : 'true');
    }
    if (doc.body) doc.body.classList.toggle('graffiti-open', want);
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: want });
    if (want) {
      setStatus('');
      loadFirst();
      const focusEl = backBtn || paint;
      if (focusEl && win.requestAnimationFrame) {
        win.requestAnimationFrame(() => focusEl.focus());
      }
    }
  }

  function pointOf(event) {
    const rect = paint.getBoundingClientRect();
    const sx = rect.width ? paint.width / rect.width : 1;
    const sy = rect.height ? paint.height / rect.height : 1;
    return {
      x: (event.clientX - rect.left) * sx,
      y: (event.clientY - rect.top) * sy
    };
  }

  function drawTo(point) {
    const ctx = ctx2d();
    if (!ctx || !last) return;
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.lineWidth = brush;
    ctx.globalCompositeOperation = erasing ? 'destination-out' : 'source-over';
    ctx.strokeStyle = color;
    ctx.beginPath();
    ctx.moveTo(last.x, last.y);
    ctx.lineTo(point.x, point.y);
    ctx.stroke();
    last = point;
  }

  function onPointerDown(event) {
    if (!paint || event.button != null && event.button !== 0) return;
    event.preventDefault();
    if (paint.setPointerCapture) paint.setPointerCapture(event.pointerId);
    snapshot();
    drawing = true;
    last = pointOf(event);
    drawTo({ x: last.x + 0.01, y: last.y + 0.01 });
  }

  function onPointerMove(event) {
    if (!drawing) return;
    event.preventDefault();
    drawTo(pointOf(event));
  }

  function onPointerUp(event) {
    if (!drawing) return;
    drawing = false;
    last = null;
    if (event) event.preventDefault();
  }

  function undo() {
    const ctx = ctx2d();
    const prev = undos.pop();
    if (!ctx || !prev) return;
    ctx.putImageData(prev, 0, 0);
  }

  function clearPaint() {
    const ctx = ctx2d();
    if (!ctx) return;
    snapshot();
    ctx.clearRect(0, 0, paint.width, paint.height);
  }

  function itemNode(item) {
    const card = doc.createElement('article');
    card.className = 'graffiti-card';
    card.setAttribute('data-id', item.id);
    const img = doc.createElement('img');
    img.alt = '';
    img.src = '/api/graffiti/' + encodeURIComponent(item.id) + '.png';
    const meta = doc.createElement('div');
    meta.className = 'graffiti-card-meta';
    const who = doc.createElement('strong');
    who.textContent = item.signature || ANONYMOUS_SIGNATURE;
    const when = doc.createElement('time');
    when.dateTime = String(item.createdAt || '');
    when.textContent = formatGraffitiDate(item.createdAt);
    meta.appendChild(who);
    meta.appendChild(when);
    card.appendChild(img);
    card.appendChild(meta);
    if (authed) {
      const del = doc.createElement('button');
      del.type = 'button';
      del.className = 'btn btn-ghost graffiti-delete';
      del.textContent = '刪除';
      del.addEventListener('click', () => removeItem(item.id, card));
      card.appendChild(del);
    }
    return card;
  }

  function prependItem(item) {
    if (!listEl || !item || seen[item.id]) return;
    seen[item.id] = true;
    const empty = listEl.querySelector('[data-graffiti-empty]');
    if (empty) empty.remove();
    listEl.insertBefore(itemNode(item), listEl.firstChild);
  }

  async function loadPage(before) {
    if (loading || listDone) return;
    loading = true;
    try {
      const query = before ? ('?limit=24&before=' + encodeURIComponent(before)) : '?limit=24';
      const res = await win.fetch('/api/graffiti' + query);
      const body = await res.json();
      const items = body && body.items || [];
      if (!listEl) return;
      if (!before && !items.length) {
        listEl.textContent = '';
        const empty = doc.createElement('p');
        empty.className = 'graffiti-empty';
        empty.setAttribute('data-graffiti-empty', '');
        empty.textContent = '牆上還是空白的。';
        listEl.appendChild(empty);
      }
      items.forEach((item) => {
        if (!item || seen[item.id]) return;
        seen[item.id] = true;
        listEl.appendChild(itemNode(item));
      });
      nextBefore = body && body.nextBefore != null ? body.nextBefore : null;
      if (!nextBefore) listDone = true;
    } catch (err) {
      setStatus('塗鴉清單暫時讀不到');
    } finally {
      loading = false;
    }
  }

  function loadFirst() {
    nextBefore = null;
    listDone = false;
    loading = false;
    Object.keys(seen).forEach((key) => { delete seen[key]; });
    if (listEl) listEl.textContent = '';
    loadPage(null);
  }

  async function removeItem(id, card) {
    if (!win.confirm('確定刪掉這幅塗鴉？')) return;
    const res = await win.fetch('/api/graffiti/' + encodeURIComponent(id), { method: 'DELETE' });
    if (!res.ok) {
      setStatus('沒能刪掉');
      return;
    }
    delete seen[id];
    if (card && card.parentNode) card.parentNode.removeChild(card);
    if (typeof hooks.onWallChange === 'function') hooks.onWallChange();
    setStatus('已從牆上拿下');
  }

  async function submit() {
    if (!paint || blank()) {
      setStatus('先在牆上畫一點再送出');
      return;
    }
    const name = signatureInput ? signatureInput.value : '';
    if (normalizeSignature(name) == null) {
      setStatus('署名最多 12 個字');
      return;
    }
    setStatus('送出中…');
    if (submitBtn) submitBtn.disabled = true;
    try {
      const blob = await new Promise((resolve) => paint.toBlob(resolve, 'image/png'));
      if (!blob) {
        setStatus('這幅畫沒能存成圖');
        return;
      }
      const form = new win.FormData();
      form.append('image', blob, 'graffiti.png');
      form.append('signature', name);
      const res = await win.fetch('/api/graffiti', { method: 'POST', body: form });
      const body = await res.json().catch(() => ({}));
      if (!res.ok) {
        setStatus(body && body.message ? body.message : '沒能留在牆上');
        return;
      }
      clearPaint();
      undos.length = 0;
      if (signatureInput) signatureInput.value = '';
      if (body.item) prependItem(body.item);
      if (typeof hooks.onWallChange === 'function') hooks.onWallChange();
      setStatus('已留在牆上');
    } catch (err) {
      setStatus('沒能留在牆上');
    } finally {
      if (submitBtn) submitBtn.disabled = false;
    }
  }

  function onKey(event) {
    if (!openState || event.key !== 'Escape') return;
    event.preventDefault();
    setOpen(false);
  }

  function onListScroll() {
    if (!listEl || listDone || loading || nextBefore == null) return;
    if (listEl.scrollTop + listEl.clientHeight > listEl.scrollHeight - 48) {
      loadPage(nextBefore);
    }
  }

  if (paint) {
    paint.width = GRAFFITI_WIDTH;
    paint.height = GRAFFITI_HEIGHT;
    const pointerOpts = { passive: false };
    paint.addEventListener('pointerdown', onPointerDown, pointerOpts);
    paint.addEventListener('pointermove', onPointerMove, pointerOpts);
    paint.addEventListener('pointerup', onPointerUp, pointerOpts);
    paint.addEventListener('pointercancel', onPointerUp, pointerOpts);
  }
  if (root) {
    root.querySelectorAll('[data-graffiti-color]').forEach((btn) => {
      btn.addEventListener('click', () => {
        color = btn.getAttribute('data-graffiti-color');
        erasing = false;
        renderTools();
      });
    });
    root.querySelectorAll('[data-graffiti-brush]').forEach((btn) => {
      btn.addEventListener('click', () => {
        brush = Number(btn.getAttribute('data-graffiti-brush'));
        erasing = false;
        renderTools();
      });
    });
    const eraser = root.querySelector('[data-graffiti-eraser]');
    if (eraser) eraser.addEventListener('click', () => { erasing = true; renderTools(); });
    const undoBtn = root.querySelector('[data-graffiti-undo]');
    if (undoBtn) undoBtn.addEventListener('click', undo);
    const clearBtn = root.querySelector('[data-graffiti-clear]');
    if (clearBtn) clearBtn.addEventListener('click', clearPaint);
    if (submitBtn) submitBtn.addEventListener('click', submit);
    if (backBtn) backBtn.addEventListener('click', () => setOpen(false));
  }
  if (listEl) listEl.addEventListener('scroll', onListScroll);
  if (win) win.addEventListener('keydown', onKey);
  doc.querySelectorAll('[data-open-graffiti]').forEach((el) => {
    el.addEventListener('click', (event) => {
      event.preventDefault();
      setOpen(true);
    });
  });
  renderTools();
  if (root) {
    root.hidden = true;
    root.setAttribute('aria-hidden', 'true');
  }

  return {
    open: () => setOpen(true),
    close: () => setOpen(false),
    isOpen: () => openState,
    reload: () => { if (openState) loadFirst(); }
  };
}
