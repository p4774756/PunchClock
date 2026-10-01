# PunchClock

自動化上班打卡系統，由桌面端排程工具與遠端監控伺服器組成。

## 架構

```
┌─────────────────┐   HTTP 心跳 + Bearer    ┌──────────────────┐   REST 取消指令   ┌─────────────┐
│  PunchClock     │ ─────────────────────► │  server          │ ◄─────────────── │  Web 控制台  │
│  (Java 桌面端)   │ ◄── action / actions ── │  (Javalin 伺服器)  │ ── WS 狀態推送 ─► │  (瀏覽器)    │
└────────┬────────┘                        └──────────────────┘                   └─────────────┘
         │ Playwright
         ▼
   打卡網站 (自動點擊)
```

**通訊協定：** Worker 統一走 HTTP 心跳；Dashboard 用 REST 下指令、WebSocket 只推狀態。詳見 [`server/README.md`](server/README.md)。

遠端僅支援「取消全部 / 取消單一任務」；排程請在桌面端本機建立。

| 模組 | 說明 | 技術 |
|------|------|------|
| [`client/`](client/) | 桌面端：排程、自動打卡、任務管理 | Java 11、Swing、Playwright |
| [`server/`](server/) | 伺服器：心跳接收、Web 監控、遠端控制 | Java 11、Javalin、WebSocket |
| [`shared/`](shared/) | 共用程式（`PeerFileRules` 等） | Java 11 |

## 快速開始

### 1. 啟動伺服器

```bash
mvn -pl server -am package -DskipTests
java -jar server/target/punchclock-server.jar
```

伺服器預設監聽 `http://localhost:3000`。開啟瀏覽器登入 Web 控制台（預設密碼：`secret`）。

### 2. 啟動桌面端

**前置需求：** JDK 11+、Maven 3.6+

```bash
mvn -pl client -am package -DskipTests
java -jar client/target/punchclock-client-standalone.jar
```

首次執行 Playwright 會自動下載瀏覽器驅動，請確保網路暢通。

### 3. 連線設定

在桌面端「雲端設定」分頁：

1. 勾選「啟用雲端單向狀態回報」
2. 填入 Server 網址，例如 `http://localhost:3000`
3. 設定 Client ID（預設 `company-worker`）
4. 填入心跳 Token（需與伺服器 `HEARTBEAT_SECRET` 一致，本機預設 `punchclock-dev-secret`）
5. 點擊「測試連線」確認成功

### 4. 公司受限網路

- **SSL 攔截（PKIX 錯誤）：** 公司網路若會解開 HTTPS 重新簽章，Java 會出現 `PKIX path building failed`。勾選「信任所有 SSL（除錯）」可略過憑證驗證，心跳與中繼 Proxy 共用此設定（啟用雲端時會鎖定，需先取消雲端才能修改）。僅建議在已知有 SSL 攔截的環境使用。
- **判斷哪條路線被擋：** 「Ping/Pong」分頁可對任意網址做 HTTP GET，「連線方式」切換「系統 Proxy／直連」對照結果；結果會列出憑證簽發者，簽發者不是公開 CA 代表連線被 SSL 攔截。
- **需要 Proxy：** 心跳與中繼預設不讀系統 Proxy；公司要求走 Proxy 時，啟動加 `-Djava.net.useSystemProxies=true`，或 `-Dhttps.proxyHost=主機 -Dhttps.proxyPort=埠`。
- **中繼 Proxy：** 同事連不到雲端、但你可以時，在「雲端設定」勾選「啟用中繼 Proxy」（預設埠 `8888`），同事把 Server 網址改成 `http://你的IP:8888`，請求會經由你的電腦轉發到雲端。
- **建置時抓不到 `git-commit-id-maven-plugin`：** 這個 plugin 只用來在 app 顯示最後一次 commit 時間，抓不到時改用建置時間。VS Code／Eclipse 會自動略過；命令列加 `-P '!git-commit-time'`：

  ```bash
  mvn -pl client -am package -DskipTests -P '!git-commit-time'
  ```

  或在網路正常的電腦打包好 `punchclock-client-standalone.jar` 再帶過去執行。

## 主要功能

### 桌面端

- 多任務排程（支援單次新增、批量建立週一至週五）
- 隨機時間浮動（±5 分鐘，避免固定時間打卡）
- 任務編輯、重新排定、立即執行
- 本地任務持久化（`~/.punchclock/tasks.json`）
- 視窗透明度（標題列滑桿，設定會寫入 `~/.punchclock/config.json`）
- 支援 Edge、Chrome、Chromium、Firefox、WebKit
- 同事傳檔：任意副檔名、可傳資料夾（自動壓 ZIP）、傳檔紀錄可在過期前重複下載或手動清除
- 「Ping/Pong」連線測試分頁：對 Server `/ping` 或任意網址做 HTTP GET，可切換系統 Proxy／直連，並顯示憑證簽發者（判斷公司 SSL 攔截）
- 中繼 Proxy：讓區網內連不到雲端的同事透過你的電腦轉發心跳等請求

### server

- 即時顯示所有連線裝置狀態
- 遠端取消排程 / 取消單一任務
- 傳檔狀態（等待收取／已下載）、過期前可下載、可手動清除；暫存保留 6 小時
- 伺服器運行狀態（CPU、JVM／系統記憶體、傳檔暫存用量）
- 3 分鐘無心跳自動判定離線
- 登入保護（5 次失敗鎖定 15 分鐘）

## 部署建議

將 server 部署至雲端（如 Render、Railway、VPS），桌面端填入對應的 HTTPS 網址即可。

**Render（Docker）：**

| 欄位 | 值 |
|------|-----|
| Runtime | **Docker**（不是 Node） |
| Dockerfile Path | `server/Dockerfile` |
| Root Directory | （留空） |

或使用根目錄 [`render.yaml`](render.yaml) 一鍵部署。

**環境變數：**

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `PORT` | 伺服器監聽埠 | `3000` |
| `HEARTBEAT_SECRET` | 心跳 API Bearer token | `punchclock-dev-secret` |
| `ADMIN_PASSWORD` | Web 控制台管理員密碼 | `secret` |

生產環境請務必修改 `HEARTBEAT_SECRET` 與 `ADMIN_PASSWORD`。

## 開發

```bash
# 全部測試
mvn test

# 只測桌面端
mvn -pl client -am test

# 只測伺服器
mvn -pl server -am test
```

## VS Code 除錯

工作區 launch configuration：

- **App (desktop)** — 啟動 Swing 桌面端
- **Server (Javalin)** — 啟動監控伺服器
