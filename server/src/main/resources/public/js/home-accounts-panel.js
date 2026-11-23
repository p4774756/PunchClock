import { adminWriteFetch, setCsrfToken } from './home-admin-write.js';

function escapeHtml(text) {
  const d = globalThis.document;
  if (!d) return String(text || '');
  const el = d.createElement('span');
  el.textContent = text == null ? '' : String(text);
  return el.innerHTML;
}

export function createHomeAccountsController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const root = doc.getElementById('accountsBoard');
  const closeBtn = root && root.querySelector('[data-accounts-close]');
  const memberPane = root && root.querySelector('[data-accounts-member]');
  const adminPane = root && root.querySelector('[data-accounts-admin]');
  const loading = root && root.querySelector('[data-accounts-loading]');
  const memberForm = root && root.querySelector('[data-accounts-member-form]');
  const memberAlert = root && root.querySelector('[data-accounts-member-alert]');
  const adminAlert = root && root.querySelector('[data-accounts-admin-alert]');
  const createForm = root && root.querySelector('[data-accounts-create]');
  const listHost = root && root.querySelector('[data-accounts-list]');
  let openState = false;

  function isAdmin() {
    return win.PUNCHCLOCK_AUTH_ROLE === 'admin';
  }

  function setOpen(next) {
    const want = !!next;
    if (want === openState) return;
    openState = want;
    if (root) {
      root.hidden = !want;
      root.setAttribute('aria-hidden', want ? 'false' : 'true');
    }
    if (doc.body) doc.body.classList.toggle('roadmap-open', want);
    if (want) refresh();
  }

  function showAlert(box, message) {
    if (!box) return;
    box.textContent = '';
    if (!message) return;
    const el = doc.createElement('div');
    el.className = 'error-alert';
    el.textContent = String(message);
    box.appendChild(el);
  }

  async function refresh() {
    if (!root) return;
    if (loading) loading.hidden = false;
    if (memberPane) memberPane.hidden = true;
    if (adminPane) adminPane.hidden = true;
  if (!isAdmin()) {
      if (loading) loading.hidden = true;
      if (memberPane) memberPane.hidden = false;
      return;
    }
    try {
      const res = await fetch('/api/accounts', { credentials: 'same-origin', headers: { Accept: 'application/json' } });
      const data = await res.json().catch(() => ({}));
      if (loading) loading.hidden = true;
      if (adminPane) adminPane.hidden = false;
      if (!res.ok || !data.success) {
        showAlert(adminAlert, (data && data.message) || '無法載入帳號');
        return;
      }
      renderList(Array.isArray(data.accounts) ? data.accounts : []);
    } catch (_e) {
      if (loading) loading.hidden = true;
      if (adminPane) adminPane.hidden = false;
      showAlert(adminAlert, '無法載入帳號');
    }
  }

  function renderList(accounts) {
    if (!listHost) return;
    let html = '';
    for (let i = 0; i < accounts.length; i++) {
      const a = accounts[i];
      const user = escapeHtml(a.username);
      const role = escapeHtml(a.role || 'member');
      const enabled = a.enabled !== false;
      const must = !!a.mustChangePassword;
      html += '<article class="accounts-row" data-account-user="' + user + '">';
      html += '<div class="accounts-row-head"><strong>' + user + '</strong>';
      html += '<span class="chip">' + (role === 'admin' ? '管理員' : '成員') + '</span>';
      if (!enabled) html += '<span class="chip chip-muted">已停用</span>';
      if (must) html += '<span class="chip chip-pending">須改密碼</span>';
      html += '</div><div class="accounts-row-actions">';
      html += '<button type="button" class="btn btn-ghost" data-act="role">切換角色</button>';
      html += '<button type="button" class="btn btn-ghost" data-act="reset-pwd">重設密碼</button>';
      html += '<button type="button" class="btn btn-ghost" data-act="toggle">' + (enabled ? '停用' : '啟用') + '</button>';
      html += '<button type="button" class="btn btn-ghost-danger" data-act="delete">刪除</button>';
      html += '</div></article>';
    }
    listHost.innerHTML = html || '<p class="empty-state">尚無其他帳號</p>';
  }

  async function patchAccount(username, body) {
    return adminWriteFetch(win, '/api/accounts/' + encodeURIComponent(username), {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify(body)
    });
  }

  async function onListClick(ev) {
    const btn = ev.target && ev.target.closest('[data-act]');
    if (!btn || !listHost) return;
    const row = btn.closest('[data-account-user]');
    if (!row) return;
    const username = row.getAttribute('data-account-user');
    const act = btn.getAttribute('data-act');
    showAlert(adminAlert, '');
    try {
      if (act === 'delete') {
        if (!win.confirm('確定刪除帳號「' + username + '」？')) return;
        const res = await adminWriteFetch(win, '/api/accounts/' + encodeURIComponent(username), {
          method: 'DELETE',
          headers: { Accept: 'application/json' }
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok || !data.success) {
          showAlert(adminAlert, (data && data.message) || '刪除失敗');
          return;
        }
      } else if (act === 'toggle') {
        const enabledNow = btn.textContent === '停用';
        const res = await patchAccount(username, { enabled: !enabledNow });
        const data = await res.json().catch(() => ({}));
        if (!res.ok || !data.success) {
          showAlert(adminAlert, (data && data.message) || '更新失敗');
          return;
        }
      } else if (act === 'role') {
        const res = await fetch('/api/accounts', { credentials: 'same-origin' });
        const list = await res.json().catch(() => ({}));
        const acc = (list.accounts || []).find((x) => x.username === username);
        const nextRole = acc && acc.role === 'admin' ? 'member' : 'admin';
        const res2 = await patchAccount(username, { role: nextRole });
        const data = await res2.json().catch(() => ({}));
        if (!res2.ok || !data.success) {
          showAlert(adminAlert, (data && data.message) || '更新失敗');
          return;
        }
      } else if (act === 'reset-pwd') {
        const pwd = win.prompt('輸入「' + username + '」的新暫時密碼：');
        if (!pwd) return;
        const res = await patchAccount(username, { password: pwd });
        const data = await res.json().catch(() => ({}));
        if (!res.ok || !data.success) {
          showAlert(adminAlert, (data && data.message) || '重設失敗');
          return;
        }
      }
      refresh();
    } catch (_e) {
      showAlert(adminAlert, '操作失敗');
    }
  }

  async function onCreate(ev) {
    if (ev && ev.preventDefault) ev.preventDefault();
    const userEl = root.querySelector('[data-accounts-create-user]');
    const roleEl = root.querySelector('[data-accounts-create-role]');
    const pwdEl = root.querySelector('[data-accounts-create-pwd]');
    const body = {
      username: userEl && userEl.value ? userEl.value.trim() : '',
      role: roleEl ? roleEl.value : 'member',
      password: pwdEl ? pwdEl.value : ''
    };
    showAlert(adminAlert, '');
    try {
      const res = await adminWriteFetch(win, '/api/accounts', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify(body)
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.success) {
        showAlert(adminAlert, (data && data.message) || '建立失敗');
        return;
      }
      if (createForm && typeof createForm.reset === 'function') createForm.reset();
      refresh();
    } catch (_e) {
      showAlert(adminAlert, '建立失敗');
    }
  }

  async function onMemberSubmit(ev) {
    if (ev && ev.preventDefault) ev.preventDefault();
    const input = root.querySelector('[data-accounts-member-new]');
    const pwd = input ? input.value : '';
    showAlert(memberAlert, '');
    try {
      const res = await adminWriteFetch(win, '/api/accounts/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ newPassword: pwd })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.success) {
        showAlert(memberAlert, (data && data.message) || '變更失敗');
        return;
      }
      if (data.csrfToken) setCsrfToken(win, data.csrfToken);
      showAlert(memberAlert, '');
      const ok = doc.createElement('p');
      ok.textContent = '密碼已更新';
      memberAlert.appendChild(ok);
      if (memberForm && typeof memberForm.reset === 'function') memberForm.reset();
    } catch (_e) {
      showAlert(memberAlert, '變更失敗');
    }
  }

  if (closeBtn) {
    closeBtn.addEventListener('click', (ev) => {
      ev.preventDefault();
      setOpen(false);
    });
  }
  if (root) {
    root.addEventListener('click', (ev) => {
      if (ev.target === root) setOpen(false);
    });
  }
  const panel = root && root.querySelector('.home-tasks-panel');
  if (panel) panel.addEventListener('click', (ev) => ev.stopPropagation());
  if (listHost) listHost.addEventListener('click', onListClick);
  if (createForm) createForm.addEventListener('submit', onCreate);
  if (memberForm) memberForm.addEventListener('submit', onMemberSubmit);

  return {
    open() {
      setOpen(true);
    },
    close() {
      setOpen(false);
    },
    isOpen() {
      return openState;
    },
    refresh
  };
}