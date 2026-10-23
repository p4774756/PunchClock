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

在桌面端「設定」分頁：

1. 勾選「啟用雲端單向狀態回報」
2. 填入 Server 網址，例如 `http://localhost:3000`
3. 設定 Client ID（預設 `company-worker`）
4. 填入心跳 Token（需與伺服器 `HEARTBEAT_SECRET` 一致，本機預設 `punchclock-dev-secret`）
5. 點擊「測試連線」確認成功

### 4. 公司受限網路

- **SSL 攔截（PKIX 錯誤）：** 公司網路若會解開 HTTPS 重新簽章，Java 會出現 `PKIX path building failed`。勾選「信任所有 SSL（除錯）」可略過憑證驗證，心跳與中繼 Proxy 共用此設定（啟用雲端時會鎖定，需先取消雲端才能修改）。僅建議在已知有 SSL 攔截的環境使用。
- **需要 Proxy：** 心跳與中繼預設不讀系統 Proxy；公司要求走 Proxy 時，啟動加 `-Djava.net.useSystemProxies=true`，或 `-Dhttps.proxyHost=主機 -Dhttps.proxyPort=埠`。
- **中繼 Proxy：** 同事連不到雲端、但你可以時，在「設定」勾選「啟用中繼 Proxy」（預設埠 `8888`），同事把 Server 網址改成 `http://你的IP:8888`，請求會經由你的電腦轉發到雲端。
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
- 中繼 Proxy：讓區網內連不到雲端的同事透過你的電腦轉發心跳等請求
- 自動更新：伺服器附帶最新的桌面端 jar，心跳發現原始碼摘要不同時詢問是否更新（詳見下方「桌面端自動更新」）

### 桌面端自動更新

- **運作方式：** 桌面端每次心跳會帶上版本與原始碼摘要（`client/src/main`、`shared/src/main` 與兩個 `pom.xml` 的 SHA-256）。伺服器 Docker image 內附同一次 commit 打包的 `punchclock-client-standalone.jar`；摘要不同時，心跳回應會帶 `update`。「設定」裡的「自動檢查更新」預設開啟，關閉後忽略這則通知，不詢問、不下載（「有新版時閒置自動更新」會一併鎖住）。開啟時，再由「有新版時閒置自動更新」決定閒置直接安裝，或先詢問使用者。
- **使用者選「是」：** 跳出「程式更新」小視窗顯示下載進度（已下載／總大小、百分比），可按「取消」中止（不會記為忽略，下次心跳會再詢問）。下載到 `~/.punchclock/updates/`，校驗大小與 SHA-256 後，由 `apply-update.bat`／`apply-update.sh` 等程式結束、換掉原本的 jar 並重新啟動；設定與排程保留。
- **使用者選「否」：** 該版摘要記在 `~/.punchclock/config.json` 的 `ignoredUpdateDigest`，不再詢問；想重測請清空這個欄位。
- **打卡執行中**不會詢問，下一次心跳再問。
- **限制：** 以 `java -jar punchclock-client-standalone.jar` 啟動時，覆蓋正在跑的 jar 再重開；jar 所在資料夾要有寫入權限。IDE 直接跑（版本 `dev`、沒有原始碼摘要）不會收到更新通知。若 classpath 上已是打包過、摘要有值的程式，會先詢問（不自動更新）；同意後把 jar 寫到 `~/.punchclock/punchclock-client-standalone.jar`，結束後用 `java -jar` 啟動它，之後請執行那份 jar。更新失敗時看 `~/.punchclock/updates/apply-update.log`。
- **發版：** 只要改了 `client/src/main`、`shared/src/main` 或 client／shared 的 `pom.xml`，摘要就會變；部署 server（Render 會重新打包 client）後，已連線的桌面端就會收到更新通知。只改 `server/` 不會觸發。建議同時進版號（四個 `pom.xml` 的 `<version>` 一起改）。
- **本機測試：** 打包兩版 client jar，伺服器以 `CLIENT_JAR_PATH=<新版 jar>` 啟動，再用舊版 jar 連線。啟動 log 出現 `Client release: v… xxxxxxxx` 代表伺服器已載入可發布的 jar。

  ```bash
  CLIENT_JAR_PATH=/path/to/client-new.jar java -jar server/target/punchclock-server.jar
  ```

  Windows `cmd` 請用 `set CLIENT_JAR_PATH=C:\path\client-new.jar`，且 profile 要寫成 `-P "!git-commit-time"`（雙引號）。

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
