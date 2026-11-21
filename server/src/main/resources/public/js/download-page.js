/**
 * 若伺服器未注入安裝檔網址，向 GitHub Releases 查詢 SKLbaby-mac.dmg／SKLbaby-win.exe。
 */
(function () {
  var MAC_NAME = 'SKLbaby-mac.dmg';
  var WIN_NAME = 'SKLbaby-win.exe';
  var REPO = 'p4774756/PunchClock';

  var macBtn = document.querySelector('[data-download-mac]');
  var winBtn = document.querySelector('[data-download-win]');
  var missing = document.querySelector('[data-desktop-missing]');
  if (!macBtn || !winBtn) {
    return;
  }

  var macUrl = (window.PUNCHCLOCK_DESKTOP_MAC_URL || '').trim();
  var winUrl = (window.PUNCHCLOCK_DESKTOP_WIN_URL || '').trim();

  function showDesktopButtons(mac, win) {
    var any = false;
    if (mac) {
      macBtn.href = mac;
      macBtn.removeAttribute('hidden');
      any = true;
    } else {
      macBtn.setAttribute('hidden', 'hidden');
    }
    if (win) {
      winBtn.href = win;
      winBtn.removeAttribute('hidden');
      any = true;
    } else {
      winBtn.setAttribute('hidden', 'hidden');
    }
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

  if (macUrl || winUrl) {
    showDesktopButtons(macUrl, winUrl);
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
      showDesktopButtons(assetUrl(release, MAC_NAME), assetUrl(release, WIN_NAME));
    })
    .catch(function () {
      showDesktopButtons('', '');
    });
})();