# server

PunchClock 遠端監控與指令中繼（Java + Javalin）。怎麼啟動、Render 部署、環境變數見根目錄 [`README.md`](../README.md)。

Worker 走 HTTP 心跳。Dashboard 用 REST 下指令，WebSocket 只推 `STATUS_UPDATE`。3 分鐘無心跳視為離線。

## 路由

實作在 `ServerApp.registerRoutes`，以及 `web` 套件的 `PeerFileRoutes`、`RelayRoutes`、`ChatRoutes`、`PhotoRoutes`。

| 方法與路徑 | 誰可以呼叫 | 用途 |
| ------------ | ------------ | ------ |
| `GET /login`、`POST /login`、`GET /logout` | 瀏覽器 | 控制台登入 |
| `GET /`、`GET /index.html` | 已登入 | 控制台頁面 |
| `GET /ping` | 任何人 | 存活與版號 |
| `GET /api/status` | 已登入 | 裝置、傳檔、CPU／記憶體 |
| `GET /api/health/history` | 已登入 | 監控取樣（約 3 天） |
| `WS /ws/dashboard` | 已登入 | 只推 `STATUS_UPDATE` |
| `POST /api/heartbeat` | Bearer | 上報狀態；回應 `action`／`actions[]`、peers、files、chatRooms、`update` |
| `POST /api/clients/{id}/cancel-schedule` | 已登入 | 取消全部排程 |
| `POST /api/clients/{id}/cancel-task/{taskId}` | 已登入 | 取消單一任務 |
| `DELETE /api/clients/{id}` | 已登入 | 刪除裝置紀錄 |
| `POST /api/peer/message` | Bearer | 同事訊息 |
| `POST /api/clients/{id}/message` | 已登入 | 後台傳話給某台桌面端 |
| `POST /api/peer/file` | Bearer | 整檔上傳（舊路徑；新客戶端改走分段） |
| `POST /api/peer/upload`、`PUT /api/peer/upload/{id}`、`GET`、`POST .../complete`、`DELETE` | Bearer | 分段上傳 |
| `GET /api/peer/file/{fileId}`、`DELETE /api/peer/file/{fileId}` | Bearer 或已登入 | 下載或清除一筆暫存（約 6 小時） |
| `DELETE /api/peer/files` | 已登入 | 清除全部傳檔暫存 |
| `POST /api/peer/relay` 與 `/api/peer/relay/{id}/...` | Bearer | 直傳。雙方都要在線，只在記憶體轉手 |
| `GET /api/chat`、`POST /api/chat/send` | 已登入；送出亦接受 Bearer | 跟某台桌面端一對一說話 |
| `POST /api/photos`、`GET /api/photos`、`DELETE /api/photos/{id}`、`DELETE /api/photos`、`POST /api/photos/notes`、`PUT /api/photos/notes/{id}` | 已登入 | 飲品牆照片與筆記 |

聊天只在記憶體，重啟後清空。飲品牆的照片和筆記預設也是；設定 `PHOTO_STORAGE_DIR` 後改存磁碟。直傳不寫磁碟。

## 桌面端 jar

心跳帶 `appVersion`。伺服器快取 GitHub `releases/latest`；Release 版號較新且客戶端不是 `dev` 時，回應帶 `update`（`version`、`releaseTime`、`sha256`、`size`、`path` 為 GitHub asset 網址，可選 `changes`）。讀不到 Release 時啟動 log 顯示 `Client release: none (GitHub)`。桌面端怎麼套用更新見根目錄 README「桌面端自動更新」。

`GET /download/app.jar`、`/download/SKLbaby.jar` 302 導向 GitHub 最新 Release 的 `SKLbaby.jar`。公開下載頁 `GET /download` 另可連到 GitHub Release 的 macOS（`SKLbaby-mac-arm.dmg`、`SKLbaby-mac-intel.dmg`）與 Windows（`SKLbaby-win.exe`）安裝檔；可選用環境變數 `DESKTOP_MAC_ARM_DOWNLOAD_URL`／`DESKTOP_MAC_INTEL_DOWNLOAD_URL`／`DESKTOP_WIN_DOWNLOAD_URL` 覆寫按鈕網址（`DESKTOP_MAC_DOWNLOAD_URL` 仍可作為 Apple 晶片版後援）。

## 測試

```bash
mvn -pl server -am test
```
