/**
 * 登入頁 HTML（eschool 校務系統風格）
 * @param {{ alertMessage?: string, lockStatus: { isLocked: boolean, remainingSec: number }, version?: string }} opts
 */
function renderLoginPage({ alertMessage = '', lockStatus, version = '' }) {
  return `
<!DOCTYPE html>
<html lang="zh-TW">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>clickClick · 管理員登入</title>
  <link rel="stylesheet" href="/css/login.css">
</head>
<body>
  <nav class="site-navbar" aria-label="主選單">
    <div class="site-navbar-inner">
      <div class="navbar-brand-text">clickClick 打卡工具 · v${version}</div>
    </div>
  </nav>

  <div class="site-banner">
    <h1 class="header-title">遠端打卡監控控制台</h1>
    <p class="header-sub">管理員登入</p>
  </div>

  <section class="sec-login">
    <div class="login-panel">
      <div class="login-inner">
        ${alertMessage}
        <form action="/login" method="POST" class="form-signin">
          <div class="form-group">
            <label for="password">密碼</label>
            <div class="pwd-wrapper">
              <input type="password" id="password" name="password" class="form-input" placeholder="請輸入管理員密碼" required autofocus ${lockStatus.isLocked ? 'disabled' : ''} />
              <button type="button" id="togglePwdBtn" class="btn-toggle-pwd" onclick="togglePasswordVisibility()" title="顯示/隱藏密碼">顯示</button>
            </div>
          </div>
          <button type="submit" id="btnSubmit" class="btn-submit" ${lockStatus.isLocked ? 'disabled' : ''}>${lockStatus.isLocked ? '冷卻鎖定中…' : '登入'}</button>
        </form>
      </div>
    </div>
  </section>

  <footer class="site-footer">clickClick 管理後台 · 樣式參考臺北市校務行政系統入口</footer>

  <script>
    function togglePasswordVisibility() {
      const pwdInput = document.getElementById('password');
      const toggleBtn = document.getElementById('togglePwdBtn');
      if (pwdInput.type === 'password') {
        pwdInput.type = 'text';
        toggleBtn.textContent = '隱藏';
      } else {
        pwdInput.type = 'password';
        toggleBtn.textContent = '顯示';
      }
    }

    let remainingSec = ${lockStatus.remainingSec};
    if (remainingSec > 0) {
      const timerSpan = document.getElementById('countdown');
      const submitBtn = document.getElementById('btnSubmit');
      const passwordInput = document.getElementById('password');
      const lockAlert = document.getElementById('lockAlert');

      const interval = setInterval(() => {
        remainingSec--;
        if (remainingSec > 0) {
          const mins = Math.floor(remainingSec / 60);
          const secs = remainingSec % 60;
          if (timerSpan) {
            timerSpan.textContent = mins > 0 ? \`\${mins} 分 \${secs} 秒\` : \`\${secs} 秒\`;
          }
        } else {
          clearInterval(interval);
          if (passwordInput) passwordInput.disabled = false;
          if (submitBtn) {
            submitBtn.disabled = false;
            submitBtn.textContent = '登入';
          }
          if (lockAlert) {
            lockAlert.style.display = 'none';
          }
        }
      }, 1000);
    }
  </script>
</body>
</html>
  `;
}

module.exports = { renderLoginPage };
