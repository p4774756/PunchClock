import {
  ANONYMOUS,
  BODY_MAX,
  COMMENT_MAX,
  containsForbiddenLink,
  formatJournalWhen,
  normalizeNickname
} from './journal-core.js';

export function mountJournalPanel(opts) {
  const doc = opts && opts.doc;
  const win = opts && opts.win;
  const root = opts && opts.root;
  const authed = !!(win && win.PUNCHCLOCK_AUTHENTICATED);
  const compose = root && root.querySelector('[data-journal-compose]');
  const listEl = root && root.querySelector('[data-journal-list]');
  const statusEl = root && root.querySelector('[data-journal-status]');
  const loadMoreBtn = root && root.querySelector('[data-journal-more]');
  let nextBefore = null;
  let loading = false;

  if (compose) compose.hidden = !authed;

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

  function renderTags(tags, parent) {
    if (!tags || !tags.length) return;
    const wrap = doc.createElement('div');
    wrap.className = 'journal-tags';
    for (let i = 0; i < tags.length; i++) {
      wrap.appendChild(elText('span', tags[i], 'journal-tag'));
    }
    parent.appendChild(wrap);
  }

  function renderComments(post, block) {
    const list = doc.createElement('div');
    list.className = 'journal-comments';
    list.setAttribute('data-journal-comments', post.id);
    const comments = post.comments || [];
    for (let i = 0; i < comments.length; i++) {
      const c = comments[i];
      const row = doc.createElement('article');
      row.className = 'journal-comment';
      row.appendChild(elText('p', c.nickname || ANONYMOUS, 'journal-comment-name'));
      row.appendChild(elText('p', c.body || '', 'journal-comment-body'));
      row.appendChild(elText('time', formatJournalWhen(c.createdAt), 'journal-comment-time'));
      if (authed) {
        const del = doc.createElement('button');
        del.type = 'button';
        del.className = 'journal-comment-delete';
        del.textContent = '刪除留言';
        del.addEventListener('click', () => deleteComment(post.id, c.id, row));
        row.appendChild(del);
      }
      list.appendChild(row);
    }
    block.appendChild(list);

    const form = doc.createElement('form');
    form.className = 'journal-comment-form';
    form.noValidate = true;
    const nick = doc.createElement('input');
    nick.type = 'text';
    nick.name = 'nickname';
    nick.className = 'journal-input';
    nick.maxLength = 12;
    nick.placeholder = '暱稱（可留空）';
    const hp = doc.createElement('input');
    hp.type = 'text';
    hp.name = 'website';
    hp.className = 'journal-hp';
    hp.tabIndex = -1;
    hp.autocomplete = 'off';
    hp.setAttribute('aria-hidden', 'true');
    const body = doc.createElement('textarea');
    body.name = 'body';
    body.className = 'journal-input';
    body.rows = 2;
    body.maxLength = COMMENT_MAX;
    body.required = true;
    body.placeholder = '留言';
    const submit = doc.createElement('button');
    submit.type = 'submit';
    submit.className = 'btn btn-primary';
    submit.textContent = '送出留言';
    form.append(nick, hp, body, submit);
    form.addEventListener('submit', (ev) => {
      ev.preventDefault();
      submitComment(post.id, form, list);
    });
    block.appendChild(form);
  }

  function renderPost(post) {
    const article = doc.createElement('article');
    article.className = 'journal-post';
    article.setAttribute('data-journal-post', post.id);
    const head = doc.createElement('header');
    head.className = 'journal-post-head';
    head.appendChild(elText('time', formatJournalWhen(post.createdAt), 'journal-post-time'));
    renderTags(post.tags, head);
    article.appendChild(head);
    article.appendChild(elText('p', post.body || '', 'journal-post-body'));
    if (post.imageUrl) {
      const img = doc.createElement('img');
      img.className = 'journal-post-image';
      img.alt = '';
      img.loading = 'lazy';
      img.decoding = 'async';
      img.src = post.imageUrl;
      article.appendChild(img);
    }
    if (authed) {
      const admin = doc.createElement('div');
      admin.className = 'journal-admin-row';
      const del = doc.createElement('button');
      del.type = 'button';
      del.className = 'btn btn-ghost';
      del.textContent = '刪除文章';
      del.addEventListener('click', () => deletePost(post.id, article));
      admin.appendChild(del);
      article.appendChild(admin);
    }
    renderComments(post, article);
    return article;
  }

  async function fetchJson(url, init) {
    const res = await fetch(url, init || {});
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
    setStatus(items.length ? '' : '尚無心得');
  }

  async function deletePost(id, node) {
    const { res } = await fetchJson('/api/journal/' + encodeURIComponent(id), { method: 'DELETE' });
    if (res.ok && node && node.parentNode) node.parentNode.removeChild(node);
    else setStatus('無法刪除');
  }

  async function deleteComment(postId, commentId, node) {
    const path = '/api/journal/' + encodeURIComponent(postId) + '/comments/' + encodeURIComponent(commentId);
    const { res } = await fetchJson(path, { method: 'DELETE' });
    if (res.ok && node && node.parentNode) node.parentNode.removeChild(node);
    else setStatus('無法刪除留言');
  }

  async function submitComment(postId, form, list) {
    const fd = new FormData(form);
    const nickname = normalizeNickname(fd.get('nickname'));
    const body = String(fd.get('body') || '').trim();
  const honeypot = String(fd.get('website') || '').trim();
    if (!nickname) {
      setStatus('暱稱太長');
      return;
    }
    if (!body) {
      setStatus('請輸入留言');
      return;
    }
    if (containsForbiddenLink(body)) {
      setStatus('留言不可包含連結');
      return;
    }
    const payload = {
      nickname,
      body,
      website: honeypot
    };
    const { res, data } = await fetchJson(
      '/api/journal/' + encodeURIComponent(postId) + '/comments',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      }
    );
    if (!res.ok) {
      setStatus((data && data.message) || '留言失敗');
      return;
    }
    const comment = data.comment;
    if (comment && list) {
      const row = doc.createElement('article');
      row.className = 'journal-comment';
      row.appendChild(elText('p', comment.nickname || ANONYMOUS, 'journal-comment-name'));
      row.appendChild(elText('p', comment.body || '', 'journal-comment-body'));
      row.appendChild(elText('time', formatJournalWhen(comment.createdAt), 'journal-comment-time'));
      list.appendChild(row);
    }
    form.reset();
    setStatus('');
  }

  if (loadMoreBtn) {
    loadMoreBtn.addEventListener('click', () => loadPage(false));
  }

  if (compose && authed) {
    compose.addEventListener('submit', async (ev) => {
      ev.preventDefault();
      const fd = new FormData(compose);
      const body = String(fd.get('body') || '').trim();
      if (!body || body.length > BODY_MAX) {
        setStatus(body ? '正文太長' : '請輸入正文');
        return;
      }
      const multipart = new FormData();
      multipart.set('body', body);
      const tags = String(fd.get('tags') || '').trim();
      if (tags) multipart.set('tags', tags);
      const file = fd.get('image');
      if (file && file.size > 0) multipart.set('image', file);
      const { res, data } = await fetchJson('/api/journal', { method: 'POST', body: multipart });
      if (!res.ok) {
        setStatus((data && data.message) || '發文失敗');
        return;
      }
      compose.reset();
      nextBefore = null;
      await loadPage(true);
      setStatus('已發佈');
    });
  }

  return {
    onOpen() {
      nextBefore = null;
      loadPage(true);
    }
  };
}