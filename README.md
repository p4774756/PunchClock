# clickClick

自動化上班打卡系統，由桌面端排程工具與遠端監控伺服器組成。

## 架構

```
┌─────────────────┐   HTTP 心跳 + Bearer    ┌──────────────────┐   REST 取消指令   ┌─────────────┐
│  clickClick     │ ─────────────────────► │  server          │ ◄─────────────── │  Web 控制台  │
│  (Java 桌面端)   │ ◄── action / actions ── │  (Node.js 伺服器)  │ ── WS 狀態推送 ─► │  (瀏覽器)    │
└────────┬────────┘                        └──────────────────┘                   └─────────────┘
         │ Playwright
         ▼
   打卡網站 (自動點擊)
```

**通訊協定：** Worker 統一走 HTTP 心跳；Dashboard 用 REST 下指令、WebSocket 只推狀態。詳見 [`server/README.md`](server/README.md)。

遠端僅支援「取消全部 / 取消單一任務」；排程請在桌面端本機建立。

| 子目錄 | 說明 | 技術 |
|--------|------|------|
| `src/` | 桌面端：排程、自動打卡、任務管理 | Java 11、Swing、Playwright |
| [`server/`](server/) | 伺服器：心跳接收、Web 監控、遠端控制 | Node.js、Express、WebSocket |

## 快速開始

### 1. 啟動伺服器

```bash
cd server
npm install
node index.js
```

伺服器預設監聽 `http://localhost:3000`。開啟瀏覽器登入 Web 控制台（預設密碼：`secret`）。

### 2. 啟動桌面端

**前置需求：** JDK 11+、Maven 3.6+

```bash
mvn package
java -jar target/clickClick-standalone.jar
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

將 `server/` 部署至雲端（如 Render、Railway、VPS），桌面端填入對應的 HTTPS 網址即可。部署時將 root directory 設為 `server`。

**環境變數：**

| 變數 | 說明 | 預設值 |
|------|------|--------|
| `PORT` | 伺服器監聽埠 | `3000` |
| `HEARTBEAT_SECRET` | 心跳 API Bearer token | `clickclick-dev-secret` |
| `ADMIN_PASSWORD` | Web 控制台管理員密碼 | `secret` |

桌面端雲端設定另有「信任所有 SSL（除錯）」開關，**預設關閉**；僅本機自簽憑證除錯時再開。

生產環境請務必修改 `HEARTBEAT_SECRET` 與 `ADMIN_PASSWORD`。

## 開發

```bash
# 桌面端測試
mvn test

# 伺服器測試
cd server && npm test
```

## 專案結構（桌面端）

```
src/main/java/com/example/
├── App.java                      # 主視窗
├── model/
│   ├── CheckInTask.java          # 任務資料模型
│   └── TaskStatus.java           # 任務狀態列舉
├── service/
│   ├── AutomationService.java    # Playwright 自動打卡
│   ├── SchedulerService.java     # 排程管理
│   ├── HeartbeatService.java     # 雲端心跳回報
│   ├── TaskPersistenceService.java # 本地 JSON 任務持久化
│   └── ConfigPersistenceService.java # 雲端設定持久化
└── ui/
    ├── PanelFactory.java         # UI 面板建構
    └── TaskEditDialog.java       # 任務編輯對話框
```

## 依賴

| 套件 | 用途 |
|------|------|
| Playwright 1.49 | 瀏覽器自動化 |
| LGoodDatePicker | 日期選擇器 UI |
| Gson | JSON 序列化 |
| JUnit 4 | 單元測試 |

## VS Code 除錯

工作區已設定 launch configuration，選擇 **App** 即可直接啟動主程式。
