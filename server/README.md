# server

clickClick 遠端監控與指令中繼伺服器（Java + Javalin）。

## 通訊協定（HTTP-primary）

```
┌──────────────┐  POST /api/heartbeat + Bearer   ┌──────────────────┐
│  clickClick  │ ───────────────────────────────► │ server           │
│  (Worker)    │ ◄──── action / actions[] ─────── │ (Javalin)        │
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
| Dashboard | REST `GET /api/status` | 裝置狀態（**需登入**） |
| Dashboard | Web 頁面 `/` | **需登入** |
| Dashboard | WebSocket `/ws/dashboard` | **需登入**；僅推送 `STATUS_UPDATE` |

## 本機啟動

```bash
# 從 repo 根目錄
mvn -pl server -am package -DskipTests
java -jar server/target/clickClick-server.jar

# 或指定密鑰
HEARTBEAT_SECRET=your-secret ADMIN_PASSWORD=your-admin-pass java -jar server/target/clickClick-server.jar
```

## Render 部署

Render **不支援 Java native runtime**，請用 **Docker**：

| 欄位 | 值 |
|------|-----|
| Language / Runtime | **Docker** |
| Root Directory | （留空，使用 repo 根目錄） |
| Dockerfile Path | `server/Dockerfile` |
| Docker Context | `.` |
| Build / Start Command | （留空，由 Dockerfile 處理） |

或使用 repo 根目錄的 [`render.yaml`](../render.yaml) 建立 Blueprint。

| 變數 | 必填 | 說明 |
|------|------|------|
| `HEARTBEAT_SECRET` | 建議 | 與桌面端心跳 Token 一致 |
| `ADMIN_PASSWORD` | 建議 | Web 控制台登入密碼 |
| `PORT` | 不用設 | Render 自動注入 |

## 測試

```bash
mvn -pl server -am test
```

靜態前端（`src/main/resources/public/`）沿用原 Dashboard HTML/CSS/JS，API 路徑與 Node 版相容。
