/** 首頁「管理員登入」浮動視窗（外觀對齊 #roadmapBoard）。 */

function lockTimeDisplay(remainingSec) {
  const mins = Math.floor(remainingSec / 60);
  const secs = remainingSec % 60;
  return mins > 0 ? mins + ' 分 ' + secs + ' 秒' : secs + ' 秒';
}

function lockMessage(remainingSec) {
  return '[警告] 嘗試錯誤過多！帳號已鎖定，請等待 ' + lockTimeDisplay(remainingSec) + '。';
}

export function wireHomeLoginModal(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const fetchFn = (opts && opts.fetch) || (win.fetch ? win.fetch.bind(win) : null);
  const onAuthed = typeof (opts && opts.onAuthed) === 'function' ? opts.onAuthed : null;
  const onOpenChange = typeof (opts && opts.onOpenChange) === 'function' ? opts.onOpenChange : null;

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
  const countdownEl = board.querySelector('[data-login-countdown]');

  let open = false;
  let lastFocus = null;
  let lockTimer = null;
  let lockRemainingSec = 0;

  function setOpen(next) {
    open = !!next;
    board.hidden = !open;
    board.setAttribute('aria-hidden', open ? 'false' : 'true');
    if (onOpenChange) onOpenChange(open);
  }

  function clearAlert() {
    if (alertBox) alertBox.innerHTML = '';
    if (countdownEl) countdownEl.textContent = '';
  }

  function showAlert(html) {
    if (!alertBox) return;
    alertBox.innerHTML = html;
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
    showAlert(
      '<div class="error-alert" data-login-lock-alert>[警告] 嘗試錯誤過多！帳號已鎖定，請等待 ' +
        '<span data-login-countdown>' + lockTimeDisplay(lockRemainingSec) + '</span>。</div>'
    );
    const liveCountdown = board.querySelector('[data-login-countdown]');
    lockTimer = win.setInterval(() => {
      lockRemainingSec -= 1;
      if (lockRemainingSec > 0) {
        if (liveCountdown) liveCountdown.textContent = lockTimeDisplay(lockRemainingSec);
        return;
      }
      stopLockTimer();
      setFormLocked(false);
      clearAlert();
    }, 1000);
  }

  function applyFailurePayload(body) {
    if (!body || typeof body !== 'object') {
      showAlert(
        '<div class="error-alert">[警告] 密碼錯誤！剩餘嘗試次數：4 次 (滿5次鎖定15分鐘)</div>'
      );
      return;
    }
    if (body.locked && body.remainingSec > 0) {
      startLockCountdown(body.remainingSec);
      return;
    }
    const msg =
      typeof body.message === 'string' && body.message
        ? body.message
        : '[警告] 密碼錯誤！剩餘嘗試次數：4 次 (滿5次鎖定15分鐘)';
    showAlert('<div class="error-alert">' + msg + '</div>');
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

  function close() {
    if (!open) return;
    setOpen(false);
    resetFormFields();
    if (lastFocus && typeof lastFocus.focus === 'function') {
      try {
        lastFocus.focus({ preventScroll: true });
      } catch (_e) { /* ignore */ }
    }
    lastFocus = null;
  }

  function openModal() {
    if (open) return;
    lastFocus = doc.activeElement;
    resetFormFields();
    setOpen(true);
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
    open: openModal,
    close,
    isOpen() {
      return open;
    },
    destroy() {
      doc.removeEventListener('keydown', onDocKey);
      stopLockTimer();
      close();
    },
    lockMessage
  };
}