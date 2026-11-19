export const DONATE_PREPARE_MSG = '贊助頁準備中，謝謝你的心意 ♥';
export const DONATE_TOAST_MS = 3000;

let toastDismiss = null;

export function isHttpsDonateUrl(url) {
  if (url == null || typeof url !== 'string') return false;
  if (url.length > 300 || !url.startsWith('https://')) return false;
  for (let i = 0; i < url.length; i += 1) {
    const c = url.charCodeAt(i);
    if (c <= 32 || c === 127) return false;
    if (c === 34 || c === 39 || c === 60 || c === 62) return false;
  }
  return true;
}

export function isDonateConfigured(url, configuredFlag) {
  if (!configuredFlag || !isHttpsDonateUrl(url)) return false;
  if (url === 'https://example.com/donate') return false;
  try {
    const host = new URL(url).hostname.toLowerCase();
    if (host === 'example.com' || host.endsWith('.example.com')) return false;
  } catch {
    return false;
  }
  return true;
}

export function readDonateConfig(win) {
  const raw = win && win.PUNCHCLOCK_DONATE;
  const url = raw && raw.url != null ? String(raw.url) : '';
  const configured = !!(raw && raw.configured);
  return { url, configured };
}

export function applyDonateAnchor(anchor, url, configured) {
  if (!anchor || !anchor.setAttribute) return;
  const safeUrl = isHttpsDonateUrl(url) ? url : 'https://example.com/donate';
  anchor.setAttribute('href', safeUrl);
  const ready = isDonateConfigured(safeUrl, configured);
  if (ready) {
    anchor.setAttribute('target', '_blank');
    anchor.setAttribute('rel', 'noopener noreferrer');
    anchor.removeAttribute('data-donate-placeholder');
    return;
  }
  anchor.setAttribute('data-donate-placeholder', '1');
  anchor.removeAttribute('target');
  anchor.setAttribute('rel', 'noopener noreferrer');
}

export function dismissDonateToast(doc) {
  if (toastDismiss) {
    toastDismiss();
    toastDismiss = null;
  }
  if (!doc || !doc.querySelector) return;
  const existing = doc.querySelector('[data-donate-toast]');
  if (existing && existing.parentNode) {
    existing.parentNode.removeChild(existing);
  }
}

export function showDonateToast(doc, placement = 'island') {
  if (!doc || !doc.body) return null;
  dismissDonateToast(doc);
  const slot = placement === 'dev' ? 'dev' : 'island';
  const wrap = doc.createElement('div');
  wrap.className = `donate-toast maple-window dev-login-toast donate-toast--${slot}`;
  wrap.setAttribute('data-donate-toast', '1');
  wrap.setAttribute('role', 'status');
  const msg = doc.createElement('p');
  msg.className = 'dev-login-toast-msg donate-toast-msg';
  msg.textContent = DONATE_PREPARE_MSG;
  const close = doc.createElement('button');
  close.type = 'button';
  close.className = 'dev-login-toast-close';
  close.setAttribute('aria-label', '關閉');
  close.textContent = '×';
  const remove = () => {
    if (wrap.parentNode) wrap.parentNode.removeChild(wrap);
    if (toastDismiss === remove) toastDismiss = null;
  };
  close.addEventListener('click', remove);
  wrap.appendChild(msg);
  wrap.appendChild(close);
  doc.body.appendChild(wrap);
  toastDismiss = remove;
  setTimeout(remove, DONATE_TOAST_MS);
  return wrap;
}

export function handleDonateClick(ev, doc, url, configured, placement = 'island') {
  if (isDonateConfigured(url, configured)) return false;
  if (ev) {
    ev.preventDefault();
    ev.stopPropagation();
  }
  showDonateToast(doc, placement);
  return true;
}

function onPlaceholderClick(ev, doc, url, configured, placement) {
  handleDonateClick(ev, doc, url, configured, placement);
}

export function bindDonateAnchor(anchor, win, doc, placement = 'island') {
  if (!anchor) return;
  const documentRef = doc || (win && win.document);
  const { url, configured } = readDonateConfig(win);
  applyDonateAnchor(anchor, url, configured);
  anchor.addEventListener('click', (ev) => {
    if (anchor.getAttribute('data-donate-placeholder') === '1') {
      onPlaceholderClick(ev, documentRef, url, configured, placement);
    }
  });
}

export function mountDonateLinks(win) {
  const doc = win && win.document;
  if (!doc) return;
  const island = doc.getElementById('islandMoreDonate');
  if (island) bindDonateAnchor(island, win, doc, 'island');
  const dev = doc.getElementById('devMachinesDonate');
  if (dev) bindDonateAnchor(dev, win, doc, 'dev');
}