/**
 * 若伺服器未注入安裝檔網址，向 GitHub Releases 查詢 XiaoBaobei 安裝檔（仍接受舊 SKLbaby-* 檔名）。
 */
(function () {
  var MAC_ARM_NAME = 'XiaoBaobei-mac-arm.dmg';
  var MAC_INTEL_NAME = 'XiaoBaobei-mac-intel.dmg';
  var MAC_ARM_LEGACY = 'XiaoBaobei-mac.dmg';
  var MAC_ARM_OLD = 'SKLbaby-mac-arm.dmg';
  var MAC_INTEL_OLD = 'SKLbaby-mac-intel.dmg';
  var MAC_ARM_OLD_LEGACY = 'SKLbaby-mac.dmg';
  var WIN_NAME = 'XiaoBaobei-win.exe';
  var WIN_OLD = 'SKLbaby-win.exe';
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

  function firstAssetUrl(release, names) {
    for (var i = 0; i < names.length; i++) {
      var url = assetUrl(release, names[i]);
      if (url) {
        return url;
      }
    }
    return '';
  }

  function macArmFromRelease(release) {
    return firstAssetUrl(release, [MAC_ARM_NAME, MAC_ARM_LEGACY, MAC_ARM_OLD, MAC_ARM_OLD_LEGACY]);
  }

  function macIntelFromRelease(release) {
    return firstAssetUrl(release, [MAC_INTEL_NAME, MAC_INTEL_OLD]);
  }

  function winFromRelease(release) {
    return firstAssetUrl(release, [WIN_NAME, WIN_OLD]);
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
        macIntelFromRelease(release),
        winFromRelease(release)
      );
    })
    .catch(function () {
      showDesktopButtons('', '', '');
    });
})();