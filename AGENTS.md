# AGENTS.md — PunchClock

給 AI／新貢獻者快速進入狀況。產品說明、啟動、部署與環境變數以 [`README.md`](README.md) 為準。HTTP 路由以 [`server/README.md`](server/README.md) 為準。

## 這是什麼

自動化上班打卡系統：桌面端排程並用 Playwright 自動點擊打卡網站；伺服器收心跳、提供 Web 監控，遠端只能取消任務（不能建立排程）。

## 模組地圖

| 路徑 | 職責 | 入口 |
|------|------|------|
| `client/` | 排程、UI、自動打卡、心跳、傳檔、網路工具、中繼 Proxy | `com.example.App` |
| `server/` | 心跳、Dashboard、peer 訊息／傳檔、取消指令 | `com.example.server.ServerApp` |
| `shared/` | 跨端共用（`PeerFileRules`、`ChatProtocol`、`ClientUpdatePolicy`） | — |

**Client 常見包：**

- `com.example.ui` — Swing（`PanelFactory`、`SlotController`、`UiFonts`…）
- `com.example.service` — 排程、自動化、心跳、持久化、中繼 Proxy
- `com.example.model` — `CheckInTask`、`WorkSlot`、`TaskStatus`

**Server 常見包：**

- `web` / `auth` / `store` / `health` — Dashboard、登入、Client／檔案暫存、健康指標

靜態前端在 `server/src/main/resources/public/`。

## 技術約束

- Java **11**、Maven 多模組（parent `pom.xml`，目前版本見 parent `<version>`）
- UI：Swing（不要引進 JavaFX／其它桌面框架，除非明確要求）
- 自動化：Playwright（首次執行會下載瀏覽器）
- 伺服器：Javalin 6；測試：JUnit 4
- 本機狀態：`~/.punchclock/tasks.json`、`~/.punchclock/config.json`
- 建置與執行指令、本機預設網址與密鑰見 [`README.md`](README.md)。VS Code／Cursor：`.vscode/launch.json` 有 `App`（桌面端）、`ServerApp`（伺服器）

## 程式慣例

- **對話框／選檔：** 走 `UiFonts.showMessage`／`showConfirm`／`fileChooser`，不要直接 `new JOptionPane`／`JFileChooser`。不要改 `UIManager` 全域字型（會害中文缺字）。
- **字型：** 中文 UI 用 `UiFonts.chinese*`；URL／Selector 等 ASCII 欄位用 `latin*`。
- **範圍：** 只改任務需要的檔案；不要順手大重構 `App.java`（檔案很大）。
- **心跳客戶端：** 公開 API 留在 `HeartbeatService`。傳檔與直傳改 `PeerFileClient`，同事訊息改 `PeerMessageClient`，心跳回應解析改 `HeartbeatResponseParser`。
- **伺服器路由：** 登入、心跳、取消留在 `ServerApp`。傳檔、直傳、聊天、照片分別在 `PeerFileRoutes`、`RelayRoutes`、`ChatRoutes`、`PhotoRoutes`。
- **文件：** 使用者沒要求就不要新增／改 Markdown。
- **Commit／PR：** 使用者沒明確要求就不要 commit 或開 PR。
- **語言：** UI 字串以繁中為主；與現有程式註解風格一致即可。

## 改動時先看哪裡

| 要做的事 | 優先看 |
|----------|--------|
| 排程／任務槽 UI | `SlotController`、`PanelFactory`、`App` |
| 自動打卡流程 | `AutomationService`、`SchedulerService` |
| 雲端心跳／遠端取消 | `HeartbeatService`、`HeartbeatResponseParser`、`ServerApp.heartbeat` |
| 同事訊息／聊天 | `PeerMessageClient`、`ChatRoutes`、`ChatStore` |
| 字型／對話框 | `UiFonts` |
| 中繼 Proxy（同事經你的電腦轉發到雲端） | `RelayProxyService`、`App.startRelayProxy`；「信任所有 SSL」套用到心跳與中繼 |
| 桌面端自動更新 | `ClientUpdateCoordinator`、`ClientUpdateInstaller`、`HeartbeatService.parseUpdateNotice`、`ClientRelease`、`ClientUpdatePolicy`、`SourceDigest`；僅 `java -jar` 啟動才能就地更新 |
| 傳檔／直傳 | `PeerFileClient`、`PeerFileRoutes`、`RelayRoutes`、`FileOfferStore`、`RelayStore` |
| 菜單牆照片 | `PhotoRoutes`、`PhotoStore` |
| Dashboard | `server/.../resources/public/` + `DashboardBroadcaster` |

## 近期方向（方便對齊歷史）

- `UiFonts` 統一對話框／選檔（取代散落的 `JOptionPane`）
- 中繼 Proxy 與公司 SSL 攔截（「信任所有 SSL」心跳與中繼共用）
- 同事傳檔（任意類型、資料夾 ZIP、狀態與 TTL）

有衝突時以程式與測試為準，再對照 README。
