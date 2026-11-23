/** 桌面安裝檔按鈕：伺服器注入網址或查 GitHub Releases latest（與 download-page 共用）。 */

const REPO = 'p4774756/PunchClock';
const MAC_ARM_NAMES = ['XiaoBaobei-mac-arm.dmg', 'XiaoBaobei-mac.dmg', 'SKLbaby-mac-arm.dmg', 'SKLbaby-mac.dmg'];
const MAC_INTEL_NAMES = ['XiaoBaobei-mac-intel.dmg', 'SKLbaby-mac-intel.dmg'];
const WIN_NAMES = ['XiaoBaobei-win.exe', 'SKLbaby-win.exe'];

function assetUrl(release, filename) {
  if (!release || !release.assets) return '';
  for (let i = 0; i < release.assets.length; i++) {
    const asset = release.assets[i];
    if (asset && asset.name === filename && asset.browser_download_url) {
      return asset.browser_download_url;
    }
  }
  return '';
}

function firstAssetUrl(release, names) {
  for (let i = 0; i < names.length; i++) {
    const url = assetUrl(release, names[i]);
    if (url) return url;
  }
  return '';
}

function desktopUrlsFromWindow(win) {
  const w = win || globalThis;
  return {
    macArm: (w.PUNCHCLOCK_DESKTOP_MAC_ARM_URL || w.PUNCHCLOCK_DESKTOP_MAC_URL || '').trim(),
    macIntel: (w.PUNCHCLOCK_DESKTOP_MAC_INTEL_URL || '').trim(),
    win: (w.PUNCHCLOCK_DESKTOP_WIN_URL || '').trim()
  };
}

function setBtn(btn, url) {
  if (!btn) return false;
  if (url) {
    btn.href = url;
    btn.removeAttribute('hidden');
    return true;
  }
  btn.setAttribute('hidden', 'hidden');
  return false;
}

export function applyDesktopDownloadButtons(root, win, urls) {
  if (!root) return;
  const macArmBtn = root.querySelector('[data-download-mac-arm]');
  const macIntelBtn = root.querySelector('[data-download-mac-intel]');
  const winBtn = root.querySelector('[data-download-win]');
  const missing = root.querySelector('[data-desktop-missing]');
  if (!macArmBtn || !macIntelBtn || !winBtn) return;

  const macArm = urls.macArm;
  const macIntel = urls.macIntel;
  const winUrl = urls.win;
  const any =
    setBtn(macArmBtn, macArm) | setBtn(macIntelBtn, macIntel) | setBtn(winBtn, winUrl);
  if (missing) {
    if (any) missing.setAttribute('hidden', 'hidden');
    else missing.removeAttribute('hidden');
  }
}

export function wireDesktopDownloadActions(root, opts) {
  if (!root) return Promise.resolve();
  const win = (opts && opts.win) || globalThis;
  const fetchFn =
    (opts && opts.fetch) || (win.fetch ? win.fetch.bind(win) : null);
  const injected = desktopUrlsFromWindow(win);
  if (injected.macArm || injected.macIntel || injected.win) {
    applyDesktopDownloadButtons(root, win, injected);
    return Promise.resolve();
  }
  if (!fetchFn) {
    applyDesktopDownloadButtons(root, win, { macArm: '', macIntel: '', win: '' });
    return Promise.resolve();
  }
  return fetchFn('https://api.github.com/repos/' + REPO + '/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' }
  })
    .then((resp) => (resp.ok ? resp.json() : null))
    .then((release) => {
      applyDesktopDownloadButtons(root, win, {
        macArm: firstAssetUrl(release, MAC_ARM_NAMES),
        macIntel: firstAssetUrl(release, MAC_INTEL_NAMES),
        win: firstAssetUrl(release, WIN_NAMES)
      });
    })
    .catch(() => {
      applyDesktopDownloadButtons(root, win, { macArm: '', macIntel: '', win: '' });
    });
}