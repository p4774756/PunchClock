/** 首頁「任務」浮動視窗（外觀對齊 #roadmapBoard）。 */

export function createHomeTasksController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const hooks = Object.assign({}, (opts && opts.hooks) || {});
  if (typeof hooks.onChange !== 'function' && opts && typeof opts.onChange === 'function') {
    hooks.onChange = opts.onChange;
  }
  const root = doc && doc.getElementById ? doc.getElementById('tasksBoard') : null;
  const closeBtn = root && root.querySelector('[data-tasks-close]');
  const panel = root && root.querySelector('.home-tasks-panel');
  let openState = false;

  function lockBackground(want) {
    const body = doc && doc.body;
    if (!body || !body.style) return;
    if (want) {
      const y = win && typeof win.scrollY === 'number' ? win.scrollY : 0;
      if (body.dataset) body.dataset.tasksScrollY = String(y);
      body.style.top = (-y) + 'px';
      return;
    }
    const y = body.dataset ? Number(body.dataset.tasksScrollY || 0) : 0;
    body.style.top = '';
    if (body.dataset) delete body.dataset.tasksScrollY;
    if (win && typeof win.scrollTo === 'function') win.scrollTo(0, Number.isFinite(y) ? y : 0);
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
    lockBackground(want);
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
    open() {
      setOpen(true);
    },
    close() {
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