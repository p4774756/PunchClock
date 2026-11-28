/** LINE 風格行內編輯：框內字數、右側確認／取消。取消還原成最後確認的值。 */


export const JOURNAL_NICK_KEY = 'punchclock.journal.nickname';
export const GRAFFITI_SIG_KEY = 'punchclock.graffiti.signature';

export function readStored(storage, key) {
  if (!storage || typeof storage.getItem !== 'function') return '';
  try {
    const raw = storage.getItem(key);
    return raw == null ? '' : String(raw);
  } catch (_e) {
    return '';
  }
}

export function writeStored(storage, key, value) {
  if (!storage || typeof storage.setItem !== 'function') return;
  try {
    storage.setItem(key, value == null ? '' : String(value));
  } catch (_e) {
    // 隱私模式或不支援 storage 時略過
  }
}

export function formatCounter(text, max) {
  if (!(max > 0)) return '';
  return Array.from(String(text == null ? '' : text)).length + '/' + max;
}

export function isDirty(draft, committed) {
  return String(draft == null ? '' : draft) !== String(committed == null ? '' : committed);
}

export function mountConfirmEdit(input, opts) {
  const options = opts || {};
  const max = Number(options.max) || 0;
  const doc = (input && input.ownerDocument) || globalThis.document;
  if (!input || !doc) return { destroy() {} };

  const wrap = doc.createElement('div');
  wrap.className = 'confirm-edit';
  const fieldWrap = doc.createElement('div');
  fieldWrap.className = 'confirm-edit-field';
  const count = doc.createElement('span');
  count.className = 'confirm-edit-count';
  count.setAttribute('aria-hidden', 'true');
  const confirmBtn = doc.createElement('button');
  confirmBtn.type = 'button';
  confirmBtn.className = 'confirm-edit-ok';
  confirmBtn.setAttribute('aria-label', '確認');
  confirmBtn.title = '確認並儲存（Enter）';
  confirmBtn.innerHTML = '<svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true"><path d="M1.5 6.2 4.2 9.2 10.5 2.4" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round"/></svg>';
  const cancelBtn = doc.createElement('button');
  cancelBtn.type = 'button';
  cancelBtn.className = 'confirm-edit-cancel';
  cancelBtn.setAttribute('aria-label', '取消');
  cancelBtn.title = '取消並還原成原本的內容（Esc）';
  cancelBtn.innerHTML = '<svg viewBox="0 0 12 12" width="12" height="12" aria-hidden="true"><path d="M2 2 10 10M10 2 2 10" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round"/></svg>';

  const parent = input.parentNode;
  if (parent) parent.insertBefore(wrap, input);
  fieldWrap.appendChild(input);
  if (max > 0) fieldWrap.appendChild(count);
  wrap.appendChild(fieldWrap);
  wrap.appendChild(confirmBtn);
  wrap.appendChild(cancelBtn);

  let committed = options.initial == null ? String(input.value || '') : String(options.initial);
  const normalize = typeof options.normalize === 'function' ? options.normalize : (v) => v;

  function current() {
    return String(input.value || '');
  }

  function paint() {
    const draft = current();
    if (max > 0) count.textContent = formatCounter(draft, max);
    const dirty = isDirty(normalize(draft), normalize(committed));
    const canSave = dirty && (typeof options.canSave !== 'function' || options.canSave(draft, committed));
    confirmBtn.disabled = !canSave;
    cancelBtn.disabled = !dirty;
    wrap.classList.toggle('is-dirty', dirty);
  }

  function setCommitted(value) {
    committed = value == null ? '' : String(value);
    paint();
  }

  function confirm() {
    if (confirmBtn.disabled) return;
    const next = normalize(current());
    if (typeof next === 'string') input.value = next;
    if (typeof options.onConfirm === 'function') options.onConfirm(current(), committed);
    setCommitted(current());
  }

  function cancel() {
    input.value = committed;
    if (typeof options.onCancel === 'function') options.onCancel(committed);
    paint();
    input.blur();
  }

  function onKey(ev) {
    if (ev.key === 'Escape') {
      ev.preventDefault();
      ev.stopPropagation();
      cancel();
    } else if (ev.key === 'Enter') {
      ev.preventDefault();
      ev.stopPropagation();
      confirm();
    }
  }

  input.addEventListener('input', paint);
  input.addEventListener('change', paint);
  input.addEventListener('keydown', onKey);
  confirmBtn.addEventListener('click', confirm);
  cancelBtn.addEventListener('click', cancel);
  paint();

  return {
    root: wrap,
    confirm: confirm,
    cancel: cancel,
    setCommitted,
    getCommitted: () => committed,
    destroy() {
      input.removeEventListener('input', paint);
      input.removeEventListener('change', paint);
      input.removeEventListener('keydown', onKey);
    }
  };
}
