# server

PunchClock 遠端監控與指令中繼（Java + Javalin）。怎麼啟動、Render 部署、環境變數見根目錄 [`README.md`](../README.md)。

Worker 走 HTTP 心跳。Dashboard 用 REST 下指令，WebSocket 只推 `STATUS_UPDATE`。3 分鐘無心跳視為離線。

## 路由

實作在 `ServerApp.registerRoutes`，以及 `web` 套件的 `PeerFileRoutes`、`RelayRoutes`、`ChatRoutes`、`PhotoRoutes`。

| 方法與路徑 | 誰可以呼叫 | 用途 |
|------------|------------|------|
| `GET /login`、`POST /login`、`GET /logout` | 瀏覽器 | 控制台登入 |
| `GET /`、`GET /index.html` | 已登入 | 控制台頁面 |
| `GET /ping` | 任何人 | 存活與版號 |
| `GET /api/status` | 已登入 | 裝置、傳檔、CPU／記憶體 |
| `GET /api/health/history` | 已登入 | 監控取樣（約 3 天） |
| `WS /ws/dashboard` | 已登入 | 只推 `STATUS_UPDATE` |
| `POST /api/heartbeat` | Bearer | 上報狀態；回應 `action`／`actions[]`、peers、files、chatRooms、`update` |
| `GET /api/client-release/jar` | Bearer | 下載伺服器附帶的桌面端 jar |
| `POST /api/clients/{id}/cancel-schedule` | 已登入 | 取消全部排程 |
| `POST /api/clients/{id}/cancel-task/{taskId}` | 已登入 | 取消單一任務 |
| `DELETE /api/clients/{id}` | 已登入 | 刪除裝置紀錄 |
| `POST /api/peer/message`、`POST /api/peer/poke` | Bearer | 同事訊息、戳一下 |
| `POST /api/clients/{id}/message` | 已登入 | 後台傳話給某台桌面端 |
| `POST /api/peer/file` | Bearer | 整檔上傳（舊路徑；新客戶端改走分段） |
| `POST /api/peer/upload`、`PUT /api/peer/upload/{id}`、`GET`、`POST .../complete`、`DELETE` | Bearer | 分段上傳 |
| `GET /api/peer/file/{fileId}`、`DELETE /api/peer/file/{fileId}` | Bearer 或已登入 | 下載或清除一筆暫存（約 6 小時） |
| `DELETE /api/peer/files` | 已登入 | 清除全部傳檔暫存 |
| `POST /api/peer/relay` 與 `/api/peer/relay/{id}/...` | Bearer | 直傳。雙方都要在線，只在記憶體轉手 |
| `GET /api/chat`、`POST /api/chat/send` | 已登入；送出亦接受 Bearer | 攤前喊話 |
| `POST /api/chat/groups`、`POST /api/chat/groups/{id}`、`DELETE /api/chat/groups/{id}` | 已登入 | 建立、修改、解散群組 |
| `POST /api/photos`、`GET /api/photos`、`DELETE /api/photos/{id}`、`DELETE /api/photos` | 已登入 | 菜單牆 |

聊天只在記憶體，重啟後清空。照片預設也是；設定 `PHOTO_STORAGE_DIR` 後改存磁碟。直傳不寫磁碟。

## 桌面端 jar

心跳帶 `appVersion`、`appReleaseTime`、`appSourceDigest`。摘要與伺服器上的 jar 不同，且客戶端不是 `dev`、有送摘要時，回應帶 `update`（`version`、`sourceDigest`、`releaseTime`、`sha256`、`size`、`path`）。

jar 來自 `CLIENT_JAR_PATH`，否則是 Docker image 裡的 `/app/client/punchclock-client-standalone.jar`。沒有可發布的 jar 時，啟動 log 顯示 `Client release: none`，心跳不帶 `update`。桌面端怎麼套用更新見根目錄 README「桌面端自動更新」。

## 測試

```bash
mvn -pl server -am test
```
