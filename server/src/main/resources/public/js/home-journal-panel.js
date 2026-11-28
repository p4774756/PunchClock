/** 首頁留言板浮窗。 */

import { ensureJournalAssets } from './journal-loader.js';

export function createHomeJournalController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const officeModal = opts && opts.officeModal;
  const hooks = Object.assign({}, (opts && opts.hooks) || {});
  const root = doc && doc.getElementById ? doc.getElementById('journalBoard') : null;
  const panel = root && root.querySelector('.home-journal-panel');
  const closeBtn = root && root.querySelector('[data-journal-close]');
  let openState = false;
  let mounted = false;
  let panelApi = null;

  function setOpen(next, meta) {
    const want = !!next;
    if (want === openState) return;
    const startOpen = () => finishOpen(meta);
    if (want) {
      ensureJournalAssets(win, doc)
        .then(() => import('./journal-panel.js'))
        .then((mod) => {
          if (!mounted && mod && typeof mod.mountJournalPanel === 'function') {
            panelApi = mod.mountJournalPanel({ doc, win, root });
            mounted = true;
          }
          startOpen();
        })
        .catch(() => {});
      return;
    }
    openState = false;
    if (root) {
      root.hidden = true;
      root.setAttribute('aria-hidden', 'true');
    }
    if (!meta || !meta.silent) {
      if (officeModal) officeModal.notifyClosed('journal');
      else if (doc.body) doc.body.classList.remove('roadmap-open');
    }
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: false });
  }

  function finishOpen(meta) {
    openState = true;
    if (root) {
      root.hidden = false;
      root.setAttribute('aria-hidden', 'false');
    }
    if (officeModal) {
      officeModal.notifyOpened('journal', meta && meta.trigger);
    } else if (doc.body) doc.body.classList.add('roadmap-open');
  if (panelApi && typeof panelApi.onOpen === 'function') panelApi.onOpen();
    const focusTarget = closeBtn || panel;
    if (focusTarget && typeof focusTarget.focus === 'function') {
      try {
        focusTarget.focus({ preventScroll: true });
      } catch (_e) { /* ignore */ }
    }
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: true });
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
        if (!silent && officeModal) officeModal.notifyClosed('journal');
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