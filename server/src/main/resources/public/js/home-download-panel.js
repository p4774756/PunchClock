/** 首頁下載浮窗（外觀對齊 #roadmapBoard）。 */

import { ensureHomeDownloadCss } from './home-panel-css-loader.js';

export function createHomeDownloadController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const officeModal = opts && opts.officeModal;
  const hooks = Object.assign({}, (opts && opts.hooks) || {});
  const root = doc && doc.getElementById ? doc.getElementById('downloadBoard') : null;
  const panel = root && root.querySelector('.home-download-panel');
  const closeBtn = root && root.querySelector('[data-download-close]');
  let openState = false;

  function setOpen(next, meta) {
    const want = !!next;
    if (want === openState) return;
    openState = want;
    if (root) {
      root.hidden = !want;
      root.setAttribute('aria-hidden', want ? 'false' : 'true');
    }
    if (want) {
      ensureHomeDownloadCss(win, doc).catch(() => {});
      if (officeModal) {
        officeModal.notifyOpened('download', meta && meta.trigger);
      } else if (doc.body) doc.body.classList.add('roadmap-open');
      const focusTarget = closeBtn || panel;
      if (focusTarget && typeof focusTarget.focus === 'function') {
        try {
          focusTarget.focus({ preventScroll: true });
        } catch (_e) { /* ignore */ }
      }
    } else if (!meta || !meta.silent) {
      if (officeModal) officeModal.notifyClosed('download');
      else if (doc.body) doc.body.classList.remove('roadmap-open');
    }
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: want });
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
        if (!silent && officeModal) officeModal.notifyClosed('download');
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