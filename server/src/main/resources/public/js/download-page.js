/**
 * 若伺服器未注入安裝檔網址，向 GitHub Releases 查詢 SKLbaby-mac-arm.dmg／SKLbaby-mac-intel.dmg／SKLbaby-win.exe。
 */
(function () {
  var MAC_ARM_NAME = 'SKLbaby-mac-arm.dmg';
  var MAC_INTEL_NAME = 'SKLbaby-mac-intel.dmg';
  var MAC_ARM_LEGACY = 'SKLbaby-mac.dmg';
  var WIN_NAME = 'SKLbaby-win.exe';
  var REPO = 'p4774756/PunchClock';

  var macArmBtn = document.querySelector('[data-download-mac-arm]');
  var macIntelBtn = document.querySelector('[data-download-mac-intel]');
  var winBtn = document.querySelector('[data-download-win]');
  var missing = document.querySelector('[data-desktop-missing]');
  if (!macArmBtn || !macIntelBtn || !winBtn) {
    return;
  }

  var macArmUrl = (window.PUNCHCLOCK_DESKTOP_MAC_ARM_URL || window.PUNCHCLOCK_DESKTOP_MAC_URL || '').trim();
  var macIntelUrl = (window.PUNCHCLOCK_DESKTOP_MAC_INTEL_URL || '').trim();
  var winUrl = (window.PUNCHCLOCK_DESKTOP_WIN_URL || '').trim();

  function setBtn(btn, url) {
    if (url) {
      btn.href = url;
      btn.removeAttribute('hidden');
      return true;
    }
    btn.setAttribute('hidden', 'hidden');
    return false;
  }

  function showDesktopButtons(macArm, macIntel, win) {
    var any = setBtn(macArmBtn, macArm) | setBtn(macIntelBtn, macIntel) | setBtn(winBtn, win);
    if (missing) {
      if (any) {
        missing.setAttribute('hidden', 'hidden');
      } else {
        missing.removeAttribute('hidden');
      }
    }
  }

  function assetUrl(release, filename) {
    if (!release || !release.assets) {
      return '';
    }
    for (var i = 0; i < release.assets.length; i++) {
      var asset = release.assets[i];
      if (asset && asset.name === filename && asset.browser_download_url) {
        return asset.browser_download_url;
      }
    }
    return '';
  }

  function macArmFromRelease(release) {
    var url = assetUrl(release, MAC_ARM_NAME);
    if (url) {
      return url;
    }
    return assetUrl(release, MAC_ARM_LEGACY);
  }

  if (macArmUrl || macIntelUrl || winUrl) {
    showDesktopButtons(macArmUrl, macIntelUrl, winUrl);
    return;
  }

  fetch('https://api.github.com/repos/' + REPO + '/releases/latest', {
    headers: { Accept: 'application/vnd.github+json' }
  })
    .then(function (resp) {
      if (!resp.ok) {
        return null;
      }
      return resp.json();
    })
    .then(function (release) {
      showDesktopButtons(
        macArmFromRelease(release),
        assetUrl(release, MAC_INTEL_NAME),
        assetUrl(release, WIN_NAME)
      );
    })
    .catch(function () {
      showDesktopButtons('', '', '');
    });
})();