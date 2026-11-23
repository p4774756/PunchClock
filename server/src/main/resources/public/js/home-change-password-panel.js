import { adminWriteFetch, setCsrfToken } from './home-admin-write.js';

export function createHomeChangePasswordController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const onDone = typeof (opts && opts.onDone) === 'function' ? opts.onDone : null;
  const root = doc.getElementById('changePasswordBoard');
  const form = root && root.querySelector('[data-change-pwd-form]');
  const alertBox = root && root.querySelector('[data-change-pwd-alert]');
  const newInput = root && root.querySelector('[data-change-pwd-new]');
  const confirmInput = root && root.querySelector('[data-change-pwd-confirm]');
  let forcedOpen = false;

  function showAlert(message) {
    if (!alertBox) return;
    alertBox.textContent = '';
    const box = doc.createElement('div');
    box.className = 'error-alert';
    box.textContent = message == null ? '' : String(message);
    alertBox.appendChild(box);
  }

  function setOpen(next, opts2) {
    if (!root) return;
    const want = !!next;
    forcedOpen = !!(opts2 && opts2.forced);
    root.hidden = !want;
    root.setAttribute('aria-hidden', want ? 'false' : 'true');
    if (doc.body) {
      doc.body.classList.toggle('roadmap-open', want);
    }
  }

  function close() {
    if (forcedOpen) return;
    setOpen(false);
  }

  function openForced() {
    setOpen(true, { forced: true });
    if (newInput) {
      try {
        newInput.focus({ preventScroll: true });
      } catch (_e) { /* ignore */ }
    }
  }

  async function submitForm(ev) {
    if (ev && ev.preventDefault) ev.preventDefault();
    const a = newInput ? newInput.value : '';
    const b = confirmInput ? confirmInput.value : '';
    if (a !== b) {
      showAlert('兩次輸入的新密碼不一致');
      return;
    }
    try {
      const res = await adminWriteFetch(win, '/api/accounts/change-password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
        body: JSON.stringify({ newPassword: a })
      });
      const data = await res.json().catch(() => ({}));
      if (!res.ok || !data.success) {
        showAlert((data && data.message) || '變更失敗');
        return;
      }
      if (data.csrfToken) {
        setCsrfToken(win, data.csrfToken);
      }
      win.PUNCHCLOCK_MUST_CHANGE_PASSWORD = false;
      win.PUNCHCLOCK_AUTHENTICATED = true;
      forcedOpen = false;
      setOpen(false);
      if (form && typeof form.reset === 'function') form.reset();
      if (onDone) onDone();
    } catch (_err) {
      showAlert('變更失敗，請稍後再試');
    }
  }

  if (form) {
    form.addEventListener('submit', submitForm);
  }
  if (root) {
    root.addEventListener('click', (ev) => {
      if (ev.target === root && !forcedOpen) close();
    });
  }
  const panel = root && root.querySelector('.pt-login-panel');
  if (panel) {
    panel.addEventListener('click', (ev) => ev.stopPropagation());
  }

  return {
    openForced,
    close,
    isOpen() {
      return root && !root.hidden;
    }
  };
}