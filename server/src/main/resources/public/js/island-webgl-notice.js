export const WEBGL_NOTICE_TITLE = '目前無法顯示 3D 島嶼';

const WEBGL_NOTICE_BODY_BASE =
  '這台裝置或瀏覽器暫時無法使用 3D 繪圖（WebGL）。可以試試：完全關閉瀏覽器再開、確認瀏覽器已開啟硬體加速，或重新開機。';

export function shouldShowWebglNotice({ rendererOk, contextLost }) {
  return rendererOk === false || contextLost === true;
}

export function webglNoticeBody({ contextLost = false } = {}) {
  if (contextLost) return WEBGL_NOTICE_BODY_BASE + '3D 繪圖中途中斷。';
  return WEBGL_NOTICE_BODY_BASE;
}

export function readForceNoWebgl(search = '') {
  return new URLSearchParams(search).get('forceNoWebgl') === '1';
}

/**
 * @param {{ root: HTMLElement, location?: Location }} opts
 */
export function bindWebglNotice({ root, location: loc = globalThis.location }) {
  if (!root) {
    return {
      present(opts) {},
      dismiss() {},
      dismissed() { return false; }
    };
  }

  const titleEl = root.querySelector('[data-webgl-notice-title]');
  const bodyEl = root.querySelector('[data-webgl-notice-body]');
  const reloadBtn = root.querySelector('[data-webgl-notice-reload]');
  const closeBtn = root.querySelector('[data-webgl-notice-close]');

  if (titleEl) titleEl.textContent = WEBGL_NOTICE_TITLE;

  let userDismissed = false;

  const present = ({ rendererOk, contextLost }) => {
    const show = shouldShowWebglNotice({ rendererOk, contextLost }) && !userDismissed;
    root.hidden = !show;
    if (show && bodyEl) bodyEl.textContent = webglNoticeBody({ contextLost });
  };

  const dismiss = () => {
    userDismissed = true;
    root.hidden = true;
  };

  reloadBtn?.addEventListener('click', () => {
    loc.reload();
  });
  closeBtn?.addEventListener('click', () => dismiss());

  return {
    present,
    dismiss,
    dismissed() {
      return userDismissed;
    }
  };
}

/**
 * @param {HTMLCanvasElement} canvas
 * @param {{ present: (opts: { rendererOk: boolean, contextLost: boolean }) => void, location?: Location }} notice
 */
export function attachWebglContextHandlers(canvas, notice, loc = globalThis.location) {
  if (!canvas || !notice) return;
  canvas.addEventListener('webglcontextlost', (event) => {
    event.preventDefault();
    notice.present({ rendererOk: true, contextLost: true });
  });
  canvas.addEventListener('webglcontextrestored', () => {
    loc.reload();
  });
}