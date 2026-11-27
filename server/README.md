# server

PunchClock 遠端監控、公開網站與指令中繼（Java + Javalin）。啟動、部署與環境變數見根目錄 [`README.md`](../README.md)。

Worker 走 HTTP 心跳。主控台以 REST 下指令，WebSocket `/ws/dashboard` 只推 `STATUS_UPDATE`。3 分鐘無心跳視為離線。

## 路由

實作在 `ServerApp.registerRoutes`，以及 `web` 套件的 `PeerFileRoutes`、`RelayRoutes`、`ChatRoutes` 等。

| 方法與路徑 | 誰可以呼叫 | 用途 |
| ------------ | ------------ | ------ |
| `GET /` | 任何人 | 夜間 3D 私人秘書辦公室（窗台 Game Boy 可點擊開彈窗） |
| `GET /download`、`GET /download/app.jar`、`GET /download/SKLbaby.jar` | 任何人 | 下載頁與 jar 導向 |
| `GET /login`、`POST /login`、`GET /logout` | 瀏覽器 | 登入／登出 |
| `GET /console` | 瀏覽器（任務等需登入） | 管理主控台 |
| `GET /ping` | 任何人 | 存活與版號 |
| `GET /api/status` | 已登入 | 裝置、傳檔、資源摘要 |
| `WS /ws/dashboard` | 已登入 | 只推 `STATUS_UPDATE` |
| `POST /api/heartbeat` | Bearer | 上報狀態；回應 `action`／`actions[]`、peers、files、chatRooms、`update` |
| `POST /api/clients/{id}/cancel-schedule` | 已登入 | 取消全部排程 |
| `POST /api/clients/{id}/cancel-task/{taskId}` | 已登入 | 取消單一任務 |
| `DELETE /api/clients/{id}` | 已登入 | 刪除裝置紀錄 |
| `POST /api/peer/message`、`POST /api/clients/{id}/message` | Bearer／已登入 | 同事或後台訊息 |
| `POST /api/peer/upload` … `complete`、`GET/DELETE /api/peer/file/{fileId}` | Bearer／已登入 | 分段傳檔暫存（約 6 小時） |
| `POST /api/peer/relay` 與 `/api/peer/relay/{id}/...` | Bearer | 直傳（記憶體轉手） |
| `GET/POST /api/chat/...` | 已登入／Bearer | 與桌面端一對一聊天 |
| `GET/POST /api/roadmap/...`、`GET /api/dev-status/...` 等 | 多為公開讀取 | 施工進度、開發狀態（見對應 Routes） |

聊天與直傳只在記憶體，重啟後清空。

## 桌面端 jar

心跳帶 `appVersion`。伺服器快取 GitHub `releases/latest`；有較新 Release 且客戶端非 `dev` 時，心跳回應可帶 `update`（`version`、`sha256`、`size`、`path` 等）。`GET /download/app.jar`、`/download/SKLbaby.jar` 302 導向最新 Release jar。

下載頁與首頁下載浮窗僅提供 `SKLbaby.jar` 下載連結；使用者需自行安裝 Java 11 或以上後執行 jar。

## 測試

```bash
mvn -pl server -am test
```