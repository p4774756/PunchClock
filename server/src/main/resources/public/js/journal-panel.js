import {
  ANONYMOUS,
  BODY_MAX,
  containsForbiddenLink,
  formatJournalWhen,
  NICKNAME_MAX,
  normalizeNickname
} from './journal-core.js';
import { JOURNAL_NICK_KEY, mountConfirmEdit, readStored, writeStored } from './confirm-edit.js';

export function mountJournalPanel(opts) {
  const doc = opts && opts.doc;
  const win = opts && opts.win;
  const root = opts && opts.root;
  const compose = root && root.querySelector('[data-journal-compose]');
  const listEl = root && root.querySelector('[data-journal-list]');
  const statusEl = root && root.querySelector('[data-journal-status]');
  const loadMoreBtn = root && root.querySelector('[data-journal-more]');
  const whoEl = root && root.querySelector('[data-journal-who]');
  const nickEl = root && root.querySelector('[data-journal-nickname]');
  const bodyEl = root && root.querySelector('[data-journal-body]');
  const countEl = root && root.querySelector('[data-journal-count]');
  const AUTH_EVENT = 'punchclock:auth-changed';
  let nextBefore = null;
  let loading = false;

  function isAuthed() {
    return !!(win && win.PUNCHCLOCK_AUTHENTICATED);
  }

  function syncAuthUi() {
    if (compose) compose.hidden = false;
    if (whoEl) whoEl.hidden = !isAuthed();
    if (nickEl) nickEl.hidden = isAuthed();
    if (nickEdit && nickEdit.root) nickEdit.root.hidden = isAuthed();
  }


  let nickEdit = null;
  if (nickEl) {
    const stored = readStored(win && win.localStorage, JOURNAL_NICK_KEY);
    if (stored) nickEl.value = stored;
    nickEdit = mountConfirmEdit(nickEl, {
      max: NICKNAME_MAX,
      initial: nickEl.value,
      normalize: (raw) => String(raw == null ? '' : raw).trim(),
      canSave: (draft) => normalizeNickname(draft) != null,
      onConfirm: (value) => writeStored(win && win.localStorage, JOURNAL_NICK_KEY, value)
    });
    if (nickEdit.root) nickEdit.root.hidden = isAuthed();
  }
  syncAuthUi();

  function setStatus(text) {
    if (statusEl) statusEl.textContent = text || '';
  }

  function clearChildren(el) {
    if (!el) return;
    while (el.firstChild) el.removeChild(el.firstChild);
  }

  function elText(tag, text, className) {
    const node = doc.createElement(tag);
    if (className) node.className = className;
    node.textContent = text == null ? '' : String(text);
    return node;
  }

  function codePointLength(text) {
    return Array.from(String(text == null ? '' : text)).length;
  }

  function updateCount() {
    if (!countEl || !bodyEl) return;
    countEl.textContent = codePointLength(bodyEl.value) + ' / ' + BODY_MAX;
  }

  function renderTags(tags, parent) {
    if (!tags || !tags.length) return;
    const wrap = doc.createElement('div');
    wrap.className = 'journal-tags';
    for (let i = 0; i < tags.length; i++) {
      wrap.appendChild(elText('span', tags[i], 'journal-tag'));
    }
    parent.appendChild(wrap);
  }

  function renderPost(post) {
    const article = doc.createElement('article');
    article.className = 'journal-post';
    article.setAttribute('data-journal-post', post.id);
    const head = doc.createElement('header');
    head.className = 'journal-post-head';
    const name = post.authorName || (post.owner ? '站長' : ANONYMOUS);
    head.appendChild(elText('span', name, 'journal-author'));
    head.appendChild(elText('time', formatJournalWhen(post.createdAt), 'journal-post-time'));
    if (isAuthed()) {
      const del = doc.createElement('button');
      del.type = 'button';
      del.className = 'journal-post-delete';
      del.textContent = '刪除';
      del.setAttribute('aria-label', '刪除這則留言');
      del.addEventListener('click', () => deletePost(post.id, article));
      head.appendChild(del);
    }
    article.appendChild(head);
    article.appendChild(elText('p', post.body || '', 'journal-post-body'));
    renderTags(post.tags, article);
    if (post.imageUrl) {
      const img = doc.createElement('img');
      img.className = 'journal-post-image';
      img.alt = '';
      img.loading = 'lazy';
      img.decoding = 'async';
      img.src = post.imageUrl;
      article.appendChild(img);
    }
    return article;
  }

  async function fetchJson(url, init) {
    const fetchFn = (win && typeof win.fetch === 'function') ? win.fetch.bind(win) : fetch;
    const res = await fetchFn(url, init || {});
    const data = await res.json().catch(() => ({}));
    return { res, data };
  }

  async function loadPage(reset) {
    if (!listEl || loading) return;
    loading = true;
    setStatus(reset ? '載入中…' : '');
    const qs = new URLSearchParams();
    if (!reset && nextBefore) qs.set('before', String(nextBefore));
    qs.set('limit', '15');
    const { res, data } = await fetchJson('/api/journal?' + qs.toString());
    loading = false;
    if (!res.ok) {
      setStatus('載入失敗');
      return;
    }
    if (reset) clearChildren(listEl);
    const items = data.items || [];
    for (let i = 0; i < items.length; i++) {
      listEl.appendChild(renderPost(items[i]));
    }
    nextBefore = data.nextBefore || null;
    if (loadMoreBtn) loadMoreBtn.hidden = !nextBefore;
    if (reset && items.length === 0) setStatus('尚無留言');
    else setStatus('');
  }

  async function deletePost(id, node) {
    if (win && typeof win.confirm === 'function' && !win.confirm('刪除這則留言？')) {
      return;
    }
    const { res } = await fetchJson('/api/journal/' + encodeURIComponent(id), { method: 'DELETE' });
    if (res.ok && node && node.parentNode) node.parentNode.removeChild(node);
    else setStatus('無法刪除');
  }

  if (loadMoreBtn) {
    loadMoreBtn.addEventListener('click', () => loadPage(false));
  }

  if (bodyEl) {
    bodyEl.addEventListener('input', updateCount);
    updateCount();
  }

  if (compose) {
    compose.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const fd = new FormData(compose);
      const body = String(fd.get('body') || '').trim();
      if (!body) {
        setStatus('請輸入正文');
        return;
      }
      if (codePointLength(body) > BODY_MAX) {
        setStatus('正文最多 280 個字');
        return;
      }
      if (!isAuthed() && containsForbiddenLink(body)) {
        setStatus('不可包含網址或連結');
        return;
      }
      const payload = { body };
      if (!isAuthed()) {
        const nickname = normalizeNickname(fd.get('nickname'));
        if (!nickname) {
          setStatus('暱稱太長');
          return;
        }
        payload.nickname = nickname;
        payload.website = String(fd.get('website') || '').trim();
      }
      const { res, data } = await fetchJson('/api/journal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      });
      if (!res.ok) {
        setStatus((data && data.message) || '發文失敗');
        return;
      }
      compose.reset();
      syncAuthUi();
      updateCount();
      nextBefore = null;
      await loadPage(true);
      setStatus('已發佈');
    });
  }

  function onAuthChanged() {
    syncAuthUi();
    nextBefore = null;
    loadPage(true);
  }

  if (win && typeof win.addEventListener === 'function') {
    win.addEventListener(AUTH_EVENT, onAuthChanged);
  }

  return {
    onOpen() {
      syncAuthUi();
      nextBefore = null;
      return loadPage(true);
    }
  };
}
