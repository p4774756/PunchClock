package com.example.server.web;

import com.example.server.auth.AuthService.LoginLockStatus;
import com.example.server.util.HtmlEscape;

public final class LoginPageRenderer {

    private LoginPageRenderer() {
    }

    public static String render(String alertMessage, LoginLockStatus lockStatus, String version) {
        return render(alertMessage, lockStatus, version, null);
    }

    public static String render(String alertMessage, LoginLockStatus lockStatus, String version, String nextPath) {
        String safeAlert = alertMessage == null ? "" : alertMessage;
        boolean locked = lockStatus.isLocked();
        int remainingSec = lockStatus.remainingSec;
        String nextField = "";
        if (nextPath != null && !nextPath.isEmpty()) {
            nextField = "            <input type=\"hidden\" name=\"next\" value=\"" + HtmlEscape.escape(nextPath) + "\" />\n";
        }
        return ""
                + "<!DOCTYPE html>\n"
                + "<html lang=\"zh-TW\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>PunchClock · 管理員登入</title>\n"
                + "  <link rel=\"stylesheet\" href=\"/css/login.css\">\n"
                + "</head>\n"
                + "<body class=\"login-page\">\n"
                + "  <main class=\"login-scene\" aria-labelledby=\"login-title\">\n"
                + "    <div class=\"punch-clock\" aria-hidden=\"true\">\n"
                + "      <svg class=\"punch-clock-svg\" viewBox=\"0 0 320 200\" role=\"presentation\" xmlns=\"http://www.w3.org/2000/svg\">\n"
                + "        <defs>\n"
                + "          <linearGradient id=\"clockBody\" x1=\"0%\" y1=\"0%\" x2=\"0%\" y2=\"100%\">\n"
                + "            <stop offset=\"0%\" stop-color=\"#f0e6cc\"/>\n"
                + "            <stop offset=\"100%\" stop-color=\"#dcc9a3\"/>\n"
                + "          </linearGradient>\n"
                + "          <radialGradient id=\"dialGlow\" cx=\"50%\" cy=\"45%\" r=\"55%\">\n"
                + "            <stop offset=\"0%\" stop-color=\"#fff8e8\"/>\n"
                + "            <stop offset=\"100%\" stop-color=\"#f5e4c0\"/>\n"
                + "          </radialGradient>\n"
                + "          <filter id=\"clockShadow\" x=\"-10%\" y=\"-10%\" width=\"120%\" height=\"130%\">\n"
                + "            <feDropShadow dx=\"0\" dy=\"4\" stdDeviation=\"6\" flood-color=\"#000\" flood-opacity=\"0.35\"/>\n"
                + "          </filter>\n"
                + "        </defs>\n"
                + "        <rect x=\"40\" y=\"16\" width=\"240\" height=\"120\" rx=\"12\" fill=\"url(#clockBody)\" stroke=\"#b8a078\" stroke-width=\"2\" filter=\"url(#clockShadow)\"/>\n"
                + "        <circle cx=\"56\" cy=\"128\" r=\"4\" fill=\"#8a7a5a\"/>\n"
                + "        <circle cx=\"264\" cy=\"128\" r=\"4\" fill=\"#8a7a5a\"/>\n"
                + "        <circle cx=\"160\" cy=\"72\" r=\"44\" fill=\"url(#dialGlow)\" stroke=\"#c9a85c\" stroke-width=\"3\"/>\n"
                + "        <circle cx=\"160\" cy=\"72\" r=\"38\" fill=\"none\" stroke=\"#4a4030\" stroke-width=\"1\" stroke-dasharray=\"2 4\" opacity=\"0.4\"/>\n"
                + "        <g stroke=\"#2a2520\" stroke-width=\"2\" stroke-linecap=\"round\">\n"
                + "          <line x1=\"160\" y1=\"72\" x2=\"160\" y2=\"42\"/>\n"
                + "          <line x1=\"160\" y1=\"72\" x2=\"188\" y2=\"82\"/>\n"
                + "        </g>\n"
                + "        <circle cx=\"160\" cy=\"72\" r=\"4\" fill=\"#3a3228\"/>\n"
                + "        <rect x=\"72\" y=\"138\" width=\"176\" height=\"14\" rx=\"3\" fill=\"#3d3428\"/>\n"
                + "        <rect x=\"76\" y=\"142\" width=\"168\" height=\"8\" rx=\"2\" fill=\"#1a1510\"/>\n"
                + "      </svg>\n"
                + "    </div>\n"
                + "    <article class=\"punch-slip\">\n"
                + "      <div class=\"punch-holes\" aria-hidden=\"true\"></div>\n"
                + "      <div class=\"punch-slip-inner\">\n"
                + "        <h1 id=\"login-title\" class=\"slip-title\">辦公室</h1>\n"
                + "        <p class=\"slip-sub\">管理員請先登入</p>\n"
                + safeAlert
                + "        <form action=\"/login\" method=\"POST\" class=\"form-signin\">\n"
                + nextField
                + "          <div class=\"form-group\">\n"
                + "            <label for=\"password\">密碼</label>\n"
                + "            <div class=\"pwd-wrapper\">\n"
                + "              <input type=\"password\" id=\"password\" name=\"password\" class=\"form-input\" required autofocus "
                + (locked ? "disabled" : "") + " />\n"
                + "              <button type=\"button\" id=\"togglePwdBtn\" class=\"btn-toggle-pwd\" onclick=\"togglePasswordVisibility()\" title=\"顯示/隱藏密碼\">顯示</button>\n"
                + "            </div>\n"
                + "          </div>\n"
                + "          <button type=\"submit\" id=\"btnSubmit\" class=\"btn-submit\" "
                + (locked ? "disabled" : "") + ">" + (locked ? "冷卻鎖定中…" : "打卡登入") + "</button>\n"
                + "        </form>\n"
                + "        <a class=\"back-office\" href=\"/\">← 回到辦公室</a>\n"
                + "      </div>\n"
                + "    </article>\n"
                + "  </main>\n"
                + "  <footer class=\"site-footer\">PunchClock · v" + HtmlEscape.escape(version) + "</footer>\n"
                + "\n"
                + "  <script>\n"
                + "    function togglePasswordVisibility() {\n"
                + "      const pwdInput = document.getElementById('password');\n"
                + "      const toggleBtn = document.getElementById('togglePwdBtn');\n"
                + "      if (pwdInput.type === 'password') {\n"
                + "        pwdInput.type = 'text';\n"
                + "        toggleBtn.textContent = '隱藏';\n"
                + "      } else {\n"
                + "        pwdInput.type = 'password';\n"
                + "        toggleBtn.textContent = '顯示';\n"
                + "      }\n"
                + "    }\n"
                + "\n"
                + "    let remainingSec = " + remainingSec + ";\n"
                + "    if (remainingSec > 0) {\n"
                + "      const timerSpan = document.getElementById('countdown');\n"
                + "      const submitBtn = document.getElementById('btnSubmit');\n"
                + "      const passwordInput = document.getElementById('password');\n"
                + "      const lockAlert = document.getElementById('lockAlert');\n"
                + "\n"
                + "      const interval = setInterval(() => {\n"
                + "        remainingSec--;\n"
                + "        if (remainingSec > 0) {\n"
                + "          const mins = Math.floor(remainingSec / 60);\n"
                + "          const secs = remainingSec % 60;\n"
                + "          if (timerSpan) {\n"
                + "            timerSpan.textContent = mins > 0 ? `${mins} 分 ${secs} 秒` : `${secs} 秒`;\n"
                + "          }\n"
                + "        } else {\n"
                + "          clearInterval(interval);\n"
                + "          if (passwordInput) passwordInput.disabled = false;\n"
                + "          if (submitBtn) {\n"
                + "            submitBtn.disabled = false;\n"
                + "            submitBtn.textContent = '打卡登入';\n"
                + "          }\n"
                + "          if (lockAlert) {\n"
                + "            lockAlert.style.display = 'none';\n"
                + "          }\n"
                + "        }\n"
                + "      }, 1000);\n"
                + "    }\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>\n";
    }
}