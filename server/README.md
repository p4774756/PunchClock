# server

PunchClock 遠端監控、公開網站與指令中繼（Java + Javalin）。啟動、部署與環境變數見根目錄 [`README.md`](../README.md)。

Worker 走 HTTP 心跳。主控台以 REST 下指令，WebSocket `/ws/dashboard` 只推 `STATUS_UPDATE`。3 分鐘無心跳視為離線。

## 路由

實作在 `ServerApp.registerRoutes`，以及 `web` 套件的 `RelayRoutes`、`ChatRoutes`、`GraffitiRoutes` 等。

| 方法與路徑 | 誰可以呼叫 | 用途 |
| ------------ | ------------ | ------ |
| `GET /` | 任何人 | 夜間 3D 私人秘書辦公室 |
| `GET /download`、`GET /download/app.jar`、`GET /download/XiaoBaobei.jar`、`GET /download/SKLbaby.jar` | 任何人 | 下載頁與 jar 導向 |
| `GET /login`、`POST /login` | 瀏覽器 | 登入 |
| `GET /logout` | 任何人 | 說明頁（不會清除 session） |
| `POST /logout` | 已登入＋CSRF | 登出 |
| `GET /console` | 瀏覽器 | 導向 `/?open=tasks` |
| `GET /ping` | 任何人；版號僅已登入 | 存活（Render 健康檢查） |
| `GET /api/office/public` | 任何人 | 辦公室公開狀態（CPU／RAM 等） |
| `GET /api/graffiti`、`POST /api/graffiti` | 任何人 | 塗鴉牆讀取／張貼 |
| `DELETE /api/graffiti/{id}` | 已登入＋CSRF | 刪除塗鴉 |
| `GET /api/roadmap` | 任何人 | 施工進度 |
| `GET /api/dev-status` | 任何人 | 開發狀態 JSON |
| `GET /api/dev-machines` | 已登入 | 開發機列表 |
| `POST /api/dev-machines/heartbeat` | Bearer（`DEV_STATUS_TOKEN`） | 開發機回報 |
| `GET /api/status` | 已登入 | 裝置、資源摘要 |
| `WS /ws/dashboard` | 已登入 | 只推 `STATUS_UPDATE` |
| `POST /api/heartbeat` | Bearer | 桌面端心跳 |
| `POST /api/peer/message` | Bearer | 同事訊息 |
| `POST /api/peer/relay` 與 `/api/peer/relay/{id}/...` | Bearer | 直傳（記憶體轉手） |
| `POST /api/clients/{id}/cancel-schedule` | 已登入＋CSRF | 取消全部排程 |
| `POST /api/clients/{id}/cancel-task/{taskId}` | 已登入＋CSRF | 取消單一任務 |
| `DELETE /api/clients/{id}` | 已登入＋CSRF | 刪除裝置紀錄 |

聊天與直傳只在記憶體，重啟後清空。

未登入請求的 body 上限為 2MB（Bearer 心跳／直傳／開發機回報除外）；全站上限仍 64MB。

## 桌面端 jar

心跳帶 `appVersion`。伺服器快取 GitHub `releases/latest`；有較新 Release 且客戶端非 `dev` 時，心跳回應可帶 `update`（`version`、`sha256`、`size`、`path` 等）。`GET /download/app.jar`、`/download/SKLbaby.jar` 302 導向最新 Release jar。

下載頁與首頁下載浮窗僅提供 `SKLbaby.jar` 下載連結；使用者需自行安裝 Java 11 或以上後執行 jar。

## 環境變數（Render）

`ADMIN_PASSWORD` 在正式環境必須是 bcrypt 雜湊；產生方式見根目錄 `README.md` 的 `AdminPasswordHashCli` 說明。登入鎖定依可信 IP（`CF-Connecting-IP` 或 `punchclock.trustProxy`）與全站上限；鎖定狀態目前僅記憶體，重啟後歸零。

## 測試

```bash
mvn -pl server -am test
```