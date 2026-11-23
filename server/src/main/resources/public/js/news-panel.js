/** 首頁即時新聞列表彈窗。 */

function relativeTimeLabel(iso, nowMs) {
  if (!iso) return '';
  const t = Date.parse(iso);
  if (!Number.isFinite(t)) return '';
  const diff = Math.max(0, nowMs - t);
  const min = Math.floor(diff / 60_000);
  if (min < 1) return '剛剛';
  if (min < 60) return min + ' 分鐘前';
  const hr = Math.floor(min / 60);
  if (hr < 24) return hr + ' 小時前';
  const day = Math.floor(hr / 24);
  return day + ' 天前';
}

export function createNewsController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const fetchFn = (opts && opts.fetch) || (win.fetch ? win.fetch.bind(win) : null);
  const officeModal = opts && opts.officeModal;
  const hooks = Object.assign({}, (opts && opts.hooks) || {});
  const root = doc && doc.getElementById ? doc.getElementById('newsBoard') : null;
  const listEl = root && root.querySelector('[data-news-list]');
  const noteEl = root && root.querySelector('[data-news-note]');
  const closeBtn = root && root.querySelector('[data-news-close]');
  let openState = false;
  let latest = null;

  function setOpen(next, meta) {
    const want = !!next;
    if (want === openState) return;
    openState = want;
    if (root) {
      root.hidden = !want;
      root.setAttribute('aria-hidden', want ? 'false' : 'true');
    }
    if (want) {
      if (officeModal) {
        officeModal.notifyOpened('news', meta && meta.trigger);
      } else if (doc.body) doc.body.classList.add('roadmap-open');
      refresh();
      const focusTarget = closeBtn || root;
      if (focusTarget && typeof focusTarget.focus === 'function') {
        try {
          focusTarget.focus({ preventScroll: true });
        } catch (_e) { /* ignore */ }
      }
    } else if (!meta || !meta.silent) {
      if (officeModal) officeModal.notifyClosed('news');
      else if (doc.body) doc.body.classList.remove('roadmap-open');
    }
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: want });
  }

  function paint(data) {
    latest = data || null;
    if (noteEl) {
      if (latest && latest.message && (!latest.items || !latest.items.length)) {
        noteEl.textContent = latest.message;
      } else if (latest && latest.stale) {
        noteEl.textContent = '顯示的是上次讀到的新聞';
      } else {
        noteEl.textContent = '';
      }
    }
    if (!listEl) return;
    while (listEl.firstChild) listEl.removeChild(listEl.firstChild);
    const items = (latest && latest.items) || [];
    const now = Date.now();
    for (let i = 0; i < items.length; i++) {
      const item = items[i] || {};
      const row = doc.createElement('li');
      row.className = 'news-list-row';
      const btn = doc.createElement('button');
      btn.type = 'button';
      btn.className = 'news-list-title';
      btn.textContent = item.title == null ? '' : String(item.title);
      const url = item.url == null ? '' : String(item.url);
      if (url) {
        btn.addEventListener('click', () => {
          win.open(url, '_blank', 'noopener,noreferrer');
        });
      }
      const meta = doc.createElement('span');
      meta.className = 'news-list-meta';
      const src = item.source == null ? '' : String(item.source);
      const rel = relativeTimeLabel(item.publishedAt, now);
      meta.textContent = [src, rel].filter(Boolean).join(' · ');
      row.appendChild(btn);
      row.appendChild(meta);
      listEl.appendChild(row);
    }
  }

  function refresh() {
    if (!fetchFn) return;
    fetchFn('/api/news')
      .then((r) => (r && r.ok ? r.json() : null))
      .catch(() => null)
      .then((body) => paint(body));
  }

  if (closeBtn) {
    closeBtn.addEventListener('click', (ev) => {
      ev.preventDefault();
      setOpen(false);
    });
  }

  function closeFromBackdrop(ev) {
    if (!root || ev.target !== root) return;
    setOpen(false);
  }
  if (root) root.addEventListener('click', closeFromBackdrop);
  const panel = root && root.querySelector('.news-panel');
  if (panel) panel.addEventListener('click', (ev) => ev.stopPropagation());

  function onEscapeKey(ev) {
    if (!openState || !ev) return;
    if (ev.key !== 'Escape' && ev.key !== 'Esc') return;
    if (typeof ev.preventDefault === 'function') ev.preventDefault();
    setOpen(false);
  }
  if (doc && typeof doc.addEventListener === 'function') {
    doc.addEventListener('keydown', onEscapeKey);
  }

  return {
    root,
    open(meta) {
      setOpen(true, meta);
    },
    close(meta) {
      const silent = meta && meta.silent;
      const fromManager = meta && meta.fromManager;
      if (fromManager || silent) {
        if (!openState) return;
        openState = false;
        if (root) {
          root.hidden = true;
          root.setAttribute('aria-hidden', 'true');
        }
        if (!silent && officeModal) officeModal.notifyClosed('news');
        if (typeof hooks.onChange === 'function') hooks.onChange({ open: false });
        return;
      }
      setOpen(false);
    },
    isOpen() {
      return openState;
    },
    refresh,
    destroy() {
      if (doc && typeof doc.removeEventListener === 'function') {
        doc.removeEventListener('keydown', onEscapeKey);
      }
      setOpen(false);
    }
  };
}