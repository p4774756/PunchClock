/** 首頁「任務」浮動視窗（外觀對齊 #roadmapBoard）。 */

export function createHomeTasksController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const hooks = Object.assign({}, (opts && opts.hooks) || {});
  if (typeof hooks.onChange !== 'function' && opts && typeof opts.onChange === 'function') {
    hooks.onChange = opts.onChange;
  }
  const officeModal = opts && opts.officeModal;
  const root = doc && doc.getElementById ? doc.getElementById('tasksBoard') : null;
  const closeBtn = root && root.querySelector('[data-tasks-close]');
  const panel = root && root.querySelector('.home-tasks-panel');
  let openState = false;

  function setOpen(next, meta) {
    const want = !!next;
    if (want === openState) return;
    openState = want;
    if (root) {
      root.hidden = !want;
      root.setAttribute('aria-hidden', want ? 'false' : 'true');
    }
    if (officeModal) {
      if (want) officeModal.notifyOpened('tasks', meta && meta.trigger);
      else if (!meta || !meta.silent) officeModal.notifyClosed('tasks');
    } else if (doc.body) doc.body.classList.toggle('roadmap-open', want);
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: want });
  }

  function closeFromBackdrop(ev) {
    if (!root || ev.target !== root) return;
    setOpen(false);
  }

  if (closeBtn) {
    closeBtn.addEventListener('click', (ev) => {
      ev.preventDefault();
      setOpen(false);
    });
  }

  if (root) {
    root.addEventListener('click', closeFromBackdrop);
  }
  if (panel) {
    panel.addEventListener('click', (ev) => ev.stopPropagation());
  }

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
      if (meta && (meta.fromManager || meta.silent)) {
        if (!openState) return;
        openState = false;
        if (root) {
          root.hidden = true;
          root.setAttribute('aria-hidden', 'true');
        }
        if (!meta.silent && officeModal) officeModal.notifyClosed('tasks');
        if (typeof hooks.onChange === 'function') hooks.onChange({ open: false });
        return;
      }
      setOpen(false);
    },
    isOpen() {
      return openState;
    },
    destroy() {
      if (doc && typeof doc.removeEventListener === 'function') {
        doc.removeEventListener('keydown', onEscapeKey);
      }
      setOpen(false);
    }
  };
}