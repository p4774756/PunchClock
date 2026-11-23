/** 首頁登入浮動視窗（外觀對齊 #roadmapBoard）。 */

function lockTimeDisplay(remainingSec) {
  const mins = Math.floor(remainingSec / 60);
  const secs = remainingSec % 60;
  return mins > 0 ? mins + ' 分 ' + secs + ' 秒' : secs + ' 秒';
}

const LOGIN_RESULT_UNKNOWN = '[警告] 登入失敗，請稍後再試';

export function wireHomeLoginModal(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const fetchFn = (opts && opts.fetch) || (win.fetch ? win.fetch.bind(win) : null);
  const onAuthed = typeof (opts && opts.onAuthed) === 'function' ? opts.onAuthed : null;
  const onOpenChange = typeof (opts && opts.onOpenChange) === 'function' ? opts.onOpenChange : null;
  const officeModal = opts && opts.officeModal;

  const board = doc.getElementById('loginBoard');
  if (!board) {
    return { open() {}, close() {}, isOpen() { return false; } };
  }

  const form = board.querySelector('[data-login-form]');
  const alertBox = board.querySelector('[data-login-alert]');
  const pwdInput = board.querySelector('[data-login-password]');
  const toggleBtn = board.querySelector('[data-login-toggle-pwd]');
  const submitBtn = board.querySelector('[data-login-submit]');
  const closeBtn = board.querySelector('[data-login-close]');

  let open = false;
  let lastFocus = null;
  let lockTimer = null;
  let lockRemainingSec = 0;

  function setOpen(next, meta) {
    const want = !!next;
    if (want === open) return;
    open = want;
    board.hidden = !open;
    board.setAttribute('aria-hidden', open ? 'false' : 'true');
    if (open) {
      if (officeModal) officeModal.notifyOpened('login', meta && meta.trigger);
    } else if (!meta || !meta.silent) {
      if (officeModal) officeModal.notifyClosed('login');
    }
    if (onOpenChange) onOpenChange(open);
  }

  function clearAlert() {
    if (alertBox) alertBox.textContent = '';
  }

  function showError(message) {
    if (!alertBox) return;
    alertBox.textContent = '';
    const box = doc.createElement('div');
    box.className = 'error-alert';
    box.textContent = message == null ? '' : String(message);
    alertBox.appendChild(box);
  }

  function stopLockTimer() {
    if (lockTimer != null) {
      win.clearInterval(lockTimer);
      lockTimer = null;
    }
  }

  function setFormLocked(locked) {
    if (pwdInput) pwdInput.disabled = locked;
    if (toggleBtn) toggleBtn.disabled = locked;
    if (submitBtn) {
      submitBtn.disabled = locked;
      submitBtn.textContent = locked ? '冷卻鎖定中…' : '登入';
    }
  }

  function startLockCountdown(remainingSec) {
    stopLockTimer();
    lockRemainingSec = Math.max(0, Number(remainingSec) || 0);
    if (lockRemainingSec <= 0) {
      setFormLocked(false);
      clearAlert();
      return;
    }
    setFormLocked(true);
    if (!alertBox) return;
    alertBox.textContent = '';
    const box = doc.createElement('div');
    box.className = 'error-alert';
    box.setAttribute('data-login-lock-alert', '');
    const countdown = doc.createElement('span');
    countdown.setAttribute('data-login-countdown', '');
    countdown.textContent = lockTimeDisplay(lockRemainingSec);
    box.appendChild(doc.createTextNode('[警告] 嘗試錯誤過多！帳號已鎖定，請等待 '));
    box.appendChild(countdown);
    box.appendChild(doc.createTextNode('。'));
    alertBox.appendChild(box);
    lockTimer = win.setInterval(() => {
      lockRemainingSec -= 1;
      if (lockRemainingSec > 0) {
        countdown.textContent = lockTimeDisplay(lockRemainingSec);
        return;
      }
      stopLockTimer();
      setFormLocked(false);
      clearAlert();
    }, 1000);
  }

  function applyFailurePayload(body) {
    if (!body || typeof body !== 'object') {
      showError(LOGIN_RESULT_UNKNOWN);
      setFormLocked(false);
      return;
    }
    if (body.locked && Number(body.remainingSec) > 0) {
      startLockCountdown(body.remainingSec);
      return;
    }
    const msg =
      typeof body.message === 'string' && body.message
        ? body.message
        : LOGIN_RESULT_UNKNOWN;
    showError(msg);
    setFormLocked(false);
  }

  function resetFormFields() {
    stopLockTimer();
    clearAlert();
    setFormLocked(false);
    if (form && typeof form.reset === 'function') form.reset();
    if (pwdInput) pwdInput.type = 'password';
    if (toggleBtn) toggleBtn.textContent = '顯示';
  }

  function close(meta) {
    if (meta && (meta.fromManager || meta.silent)) {
      if (!open) return;
      open = false;
      board.hidden = true;
      board.setAttribute('aria-hidden', 'true');
      resetFormFields();
      if (!meta.silent && officeModal) officeModal.notifyClosed('login');
      if (onOpenChange) onOpenChange(false);
      return;
    }
    if (!open) return;
    setOpen(false);
    resetFormFields();
    if (!officeModal && lastFocus && typeof lastFocus.focus === 'function') {
      try {
        lastFocus.focus({ preventScroll: true });
      } catch (_e) { /* ignore */ }
    }
    lastFocus = null;
  }

  function openModal(trigger) {
    if (open) return;
    lastFocus = trigger || doc.activeElement;
    resetFormFields();
    setOpen(true, { trigger });
    if (pwdInput && !pwdInput.disabled) {
      try {
        pwdInput.focus({ preventScroll: true });
      } catch (_e) { /* ignore */ }
    }
  }

  function togglePasswordVisibility() {
    if (!pwdInput || !toggleBtn) return;
    if (pwdInput.type === 'password') {
      pwdInput.type = 'text';
      toggleBtn.textContent = '隱藏';
    } else {
      pwdInput.type = 'password';
      toggleBtn.textContent = '顯示';
    }
  }

  function submitLogin(ev) {
    if (ev && typeof ev.preventDefault === 'function') ev.preventDefault();
    if (!fetchFn || !pwdInput) return;
    if (pwdInput.disabled) return;
    const password = pwdInput.value || '';
    const body = new URLSearchParams();
    body.set('password', password);
    fetchFn('/login', {
      method: 'POST',
      credentials: 'same-origin',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        Accept: 'application/json'
      },
      body: body.toString()
    })
      .then((r) => r.json().then((data) => ({ ok: r.ok, status: r.status, data })))
      .catch(() => ({ ok: false, status: 0, data: null }))
      .then((result) => {
        if (result.ok && result.data && result.data.success) {
          close();
          if (onAuthed) onAuthed();
          return;
        }
        applyFailurePayload(result.data);
      });
  }

  if (closeBtn) {
    closeBtn.addEventListener('click', (ev) => {
      ev.preventDefault();
      close();
    });
  }

  board.addEventListener('click', (ev) => {
    if (ev.target === board) close();
  });

  const panel = board.querySelector('.pt-login-panel');
  if (panel) {
    panel.addEventListener('click', (ev) => ev.stopPropagation());
  }

  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => togglePasswordVisibility());
  }

  if (form) {
    form.addEventListener('submit', submitLogin);
  }

  function onDocKey(ev) {
    if (!open) return;
    if (ev.key === 'Escape') {
      ev.preventDefault();
      close();
    }
  }
  doc.addEventListener('keydown', onDocKey);

  return {
    root: board,
    open: openModal,
    close,
    isOpen() {
      return open;
    },
    destroy() {
      doc.removeEventListener('keydown', onDocKey);
      stopLockTimer();
      close();
    }
  };
}