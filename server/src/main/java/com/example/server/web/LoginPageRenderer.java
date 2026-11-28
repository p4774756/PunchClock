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
        String nextField = "";
        if (nextPath != null && !nextPath.isEmpty()) {
            nextField = "            <input type=\"hidden\" name=\"next\" value=\"" + HtmlEscape.escape(nextPath) + "\" />\n";
        }
        String safeVersion = HtmlEscape.escape(version);
        return ""
                + "<!DOCTYPE html>\n"
                + "<html lang=\"zh-TW\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "  <title>私人女秘書 · 登入</title>\n"
                + "  <link rel=\"icon\" href=\"/favicon.ico\" sizes=\"any\">\n"
                + "  <link rel=\"icon\" href=\"/favicon.svg\" type=\"image/svg+xml\">\n"
                + "  <link rel=\"stylesheet\" href=\"/css/dashboard.css\">\n"
                + "  <link rel=\"stylesheet\" href=\"/css/console.css\">\n"
                + "  <link rel=\"stylesheet\" href=\"/css/roadmap.css\">\n"
                + "  <link rel=\"stylesheet\" href=\"/css/login.css\">\n"
                + "  <link href=\"https://fonts.googleapis.com/css2?family=Noto+Sans+TC:wght@400;700;900&display=swap\" rel=\"stylesheet\">\n"
                + "</head>\n"
                + "<body class=\"login-page console-page\">\n"
                + "  <header class=\"console-head\" aria-label=\"登入導覽\">\n"
                + "    <div class=\"console-brand\">\n"
                + "      <strong>私人女秘書</strong>\n"
                + "      <small>v" + safeVersion + "</small>\n"
                + "    </div>\n"
                + "  </header>\n"
                + "  <main class=\"login-main\" aria-labelledby=\"login-title\">\n"
                + "    <section class=\"console-sheet login-sheet\">\n"
                + "      <header class=\"maple-win-title\">\n"
                + "        <h2 id=\"login-title\">登入</h2>\n"
                + "        <a class=\"roadmap-close-x\" href=\"/\" aria-label=\"回辦公室\">×</a>\n"
                + "      </header>\n"
                + "      <div class=\"login-sheet-body\">\n"
                + "        <p class=\"login-lead\">請先登入</p>\n"
                + safeAlert
                + "        <form action=\"/login\" method=\"POST\" class=\"form-signin\">\n"
                + nextField
                + "          <div class=\"form-group\">\n"
                + "            <label for=\"password\">密碼</label>\n"
                + "            <div class=\"pwd-row\">\n"
                + "              <input type=\"password\" id=\"password\" name=\"password\" class=\"form-input\" required autofocus "
                + (locked ? "disabled" : "") + " />\n"
                + "              <button type=\"button\" id=\"togglePwdBtn\" class=\"btn btn-ghost btn-toggle-pwd\" onclick=\"togglePasswordVisibility()\" title=\"顯示/隱藏密碼\">顯示</button>\n"
                + "            </div>\n"
                + "          </div>\n"
                + "          <button type=\"submit\" id=\"btnSubmit\" class=\"btn btn-primary btn-submit\" "
                + (locked ? "disabled" : "") + ">登入</button>\n"
                + "        </form>\n"
                + "      </div>\n"
                + "    </section>\n"
                + "  </main>\n"
                + "  <footer class=\"site-footer\">私人女秘書 · v" + safeVersion + "</footer>\n"
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
                + "  </script>\n"
                + "</body>\n"
                + "</html>\n";
    }
}