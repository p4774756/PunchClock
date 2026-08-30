# clickClick

自動化上班打卡系統，由桌面端排程工具與遠端監控伺服器組成。

## 架構

```
┌─────────────────┐   HTTP 心跳 + Bearer    ┌──────────────────┐   REST 取消指令   ┌─────────────┐
│  clickClick     │ ─────────────────────► │  server          │ ◄─────────────── │  Web 控制台  │
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
| [`shared/`](shared/) | 共用程式（`DailyProverb` 等） | Java 11 |

## 快速開始

### 1. 啟動伺服器

```bash
mvn -pl server -am package -DskipTests
java -jar server/target/clickClick-server.jar
```

伺服器預設監聽 `http://localhost:3000`。開啟瀏覽器登入 Web 控制台（預設密碼：`secret`）。

### 2. 啟動桌面端

**前置需求：** JDK 11+、Maven 3.6+

```bash
mvn -pl client -am package -DskipTests
java -jar client/target/clickClick-standalone.jar
```

首次執行 Playwright 會自動下載瀏覽器驅動，請確保網路暢通。

### 3. 連線設定

在桌面端「雲端服務與裝置設定」區塊：

1. 勾選「啟用雲端狀態回報」
2. 填入 Server 網址，例如 `http://localhost:3000`
3. 設定 Client ID（預設 `company-worker`）
4. 填入心跳 Token（需與伺服器 `HEARTBEAT_SECRET` 一致，本機預設 `clickclick-dev-secret`）
5. 點擊「測試連線」確認成功

## 主要功能

### 桌面端

- 多任務排程（支援單次新增、批量建立週一至週五）
- 隨機時間浮動（±5 分鐘，避免固定時間打卡）
- 任務編輯、重新排定、立即執行
- 本地任務持久化（`~/.clickClick/tasks.json`）
- 支援 Edge、Chrome、Chromium、Firefox、WebKit

### server

- 即時顯示所有連線裝置狀態
- 遠端取消排程 / 取消單一任務
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
| `HEARTBEAT_SECRET` | 心跳 API Bearer token | `clickclick-dev-secret` |
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
