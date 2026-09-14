# AGENTS.md — PunchClock

給 AI／新貢獻者快速進入狀況。產品說明與部署細節以 [`README.md`](README.md)、[`server/README.md`](server/README.md) 為準。

## 這是什麼

自動化上班打卡系統：桌面端排程並用 Playwright 自動點擊打卡網站；伺服器收心跳、提供 Web 監控，遠端**只能取消**任務（不能建立排程）。

```
client (Swing + Playwright)  --HTTP heartbeat + Bearer-->  server (Javalin)
                                    <-- action / actions --
Web Dashboard  --REST 取消指令-->  server  --WS STATUS_UPDATE-->  Dashboard
```

## 模組地圖

| 路徑 | 職責 | 入口 |
|------|------|------|
| `client/` | 排程、UI、自動打卡、心跳、傳檔、網路工具 | `com.example.App` |
| `server/` | 心跳、Dashboard、peer 訊息／傳檔、取消指令 | `com.example.server.ServerApp` |
| `shared/` | 跨端共用（如 `DailyProverb`） | — |

**Client 常見包：**

- `com.example.ui` — Swing（`PanelFactory`、`SlotController`、`UiFonts`、`NetworkToolsPanel`…）
- `com.example.service` — 排程、自動化、心跳、持久化、網路探測
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

## 本機預設

| 項目 | 值 |
|------|-----|
| Server | `http://localhost:3000` |
| `HEARTBEAT_SECRET` | `punchclock-dev-secret` |
| `ADMIN_PASSWORD` | `secret` |
| 預設 Client ID | `company-worker` |

生產務必改密鑰與密碼。Render 用 Docker（`server/Dockerfile` / `render.yaml`），不是 Node runtime。

## 常用指令

```bash
# 建置
mvn -pl client -am package -DskipTests
mvn -pl server -am package -DskipTests

# 執行
java -jar client/target/punchclock-client-standalone.jar
java -jar server/target/punchclock-server.jar

# 測試
mvn test
mvn -pl client -am test
mvn -pl server -am test
```

VS Code／Cursor：`.vscode/launch.json` 有 `App`（桌面端）、`ServerApp`（伺服器）。

## 通訊協定（精簡）

| 通道 | 用途 |
|------|------|
| `POST /api/heartbeat` + Bearer | Worker 上報狀態；回應 `action`／`actions[]`、peers、files |
| `POST /api/clients/{id}/cancel-schedule` | 取消全部排程 |
| `POST /api/clients/{id}/cancel-task/{taskId}` | 取消單一任務 |
| `GET /api/status`、`/`、`/ws/dashboard` | Dashboard（需登入）；WS **只推** `STATUS_UPDATE` |
| `/api/peer/*` | 同事訊息、poke、傳檔（暫存約 6 小時） |

3 分鐘無心跳視為離線。細節見 `server/README.md`。

## 程式慣例

- **對話框／選檔：** 走 `UiFonts.showMessage`／`showConfirm`／`fileChooser`，不要直接 `new JOptionPane`／`JFileChooser`。不要改 `UIManager` 全域字型（會害中文缺字）。
- **字型：** 中文 UI 用 `UiFonts.chinese*`；URL／Selector 等 ASCII 欄位用 `latin*`。
- **範圍：** 只改任務需要的檔案；不要順手大重構 `App.java`（檔案很大）。
- **文件：** 使用者沒要求就不要新增／改 Markdown。
- **Commit／PR：** 使用者沒明確要求就不要 commit 或開 PR。
- **語言：** UI 字串以繁中為主；與現有程式註解風格一致即可。

## 改動時先看哪裡

| 要做的事 | 優先看 |
|----------|--------|
| 排程／任務槽 UI | `SlotController`、`PanelFactory`、`App` |
| 自動打卡流程 | `AutomationService`、`SchedulerService` |
| 雲端心跳／遠端取消 | `HeartbeatService`（client）、`ServerApp.heartbeat` |
| 字型／對話框 | `UiFonts` |
| 網路／Proxy 探測 | `NetworkToolsPanel`、`NetworkProbeService` |
| 傳檔 | client 傳檔 UI + `FileOfferStore`、`/api/peer/file*` |
| Dashboard | `server/.../resources/public/` + `DashboardBroadcaster` |

## 近期方向（方便對齊歷史）

- `UiFonts` 統一對話框／選檔（取代散落的 `JOptionPane`）
- 網路工具分頁（Proxy／封閉網路探測）
- 同事傳檔（任意類型、資料夾 ZIP、狀態與 TTL）

有衝突時以程式與測試為準，再對照 README。
