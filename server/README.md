# server

clickClick 遠端監控與指令中繼伺服器。

## 通訊協定（HTTP-primary）

```
┌──────────────┐  POST /api/heartbeat + Bearer   ┌──────────────────┐
│  clickClick  │ ───────────────────────────────► │ server           │
│  (Worker)    │ ◄──── action / actions[] ─────── │                  │
└──────────────┘                                  └────────┬─────────┘
                                                           │
                     REST 下指令（取消任務）                 │ WS 推送狀態
                                                           ▼
                                                  ┌──────────────────┐
                                                  │  Web Dashboard   │
                                                  └──────────────────┘
```

| 角色 | 通道 | 用途 |
|------|------|------|
| Worker（桌面端） | HTTP `POST /api/heartbeat` | 上報任務狀態；收取遠端指令 |
| Dashboard | REST `/api/clients/...` | 下達取消排程 / 取消任務 / 刪除紀錄 |
| Dashboard | REST `GET /api/status` | 裝置狀態（**需登入**；targetUrl 遮網域與路徑，buttonId 保留） |
| Dashboard | Web 頁面 `/` | **需登入**；`/index.html` 會導向登入或 `/`，不直接當靜態檔 |
| Dashboard | WebSocket `/ws/dashboard` | **需登入**；僅推送 `STATUS_UPDATE`，不接受指令 |

### 支援的遠端指令（桌面端）

| action | 說明 |
|--------|------|
| `CANCEL_SCHEDULE` | 取消該裝置全部排程 |
| `CANCEL_TASK:<taskId>` | 取消指定任務 |

指令寫入 pending queue（TTL 30 秒），Worker 下次心跳（約 15 秒內）取回並執行。  
不支援遠端建立排程或遠端觸發打卡——請在桌面端本機操作。

## 啟動

```bash
npm install
node index.js
# 或指定密鑰與管理員密碼
HEARTBEAT_SECRET=your-secret ADMIN_PASSWORD=your-admin-pass node index.js
```

## Render 環境變數

| 變數 | 必填 | 說明 |
|------|------|------|
| `HEARTBEAT_SECRET` | 建議 | 與桌面端心跳 Token 一致 |
| `ADMIN_PASSWORD` | 建議 | Web 控制台登入密碼 |
| `PORT` | 不用設 | Render 自動注入 |

部署時將 **Root Directory** 設為 `server`。

## 測試

```bash
npm test                 # Dashboard HTML/JS 語法
npm run test:heartbeat   # 需先啟動 server，驗證 Bearer token
```
