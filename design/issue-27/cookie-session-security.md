# 管理員 Cookie 與 Session 安全強化（Issue #27 設計文件）

| 項目 | 內容 |
|------|------|
| **性質** | 僅設計；**不授權**自動實作 Stage 1 及以上（見 §8） |
| **檢查基準** | `origin/main` @ `f2c1db601f6e6936cbd087abf40cd924d006f4e6` |
| **前置** | PR #26（公開訪客面）已合併；#254 已引入 2D 虛擬辦公室（`/office`）；#257 進行中將把首頁改為 2D、主控台搬到 `/console` 並移除 3D 群島 |
| **測試基準** | 本機 `mvn -pl server test` → **259** tests, 0 failures（2026-11-21） |
| **歷史參考** | 2026-11-13 issue 內 Phase 2 報告（`main@d73f399`）與 ChatGPT 審查留言；本文件已重寫，**不可**視為舊報告複本 |

---

## 0. 摘要（TL;DR）

1. **現況**：`AuthService` 在啟動時算出固定 `auth` Cookie 值（`sha256HexPrefix("punchclock-admin:" + ADMIN_PASSWORD, 32)`，32 位小寫 hex）。`ServerApp.loginPost` 每次登入都設定同一值；`logout` 僅 `clearAuthCookie`，伺服器**不撤銷**；`isAuth`／`isAuthFromCookieHeader` 只做字串相等比對。Cookie 經 `ctx.cookie("auth", value, 86400)` 設定，**無** `HttpOnly`／`SameSite`／`Secure`（`isHttps` 已存在但未用於 Cookie）。
2. **目標**：密碼學隨機不透明 session ID、伺服器端只存 SHA-256(ID)、TTL／閒置過期、登出撤銷並關閉綁定之 `/ws/dashboard`、Cookie 安全旗標、拒絕舊 deterministic Cookie、**不可**把「缺少 Origin/Referer 一律放行」當 CSRF 防線。
3. **訪客面（對齊未來）**：訪客可開啟 **2D 辦公室**（現況 `GET /office`，#257 後預期 `GET /`）、公開靜態資產、公開 API（開發進度、施工路線圖、塗鴉牆讀寫（刪除除外）、島嶼在線人數 WS 等）。**管理員**須有效 session 才能進入主控台（現況 `GET /` 帶 `is-authenticated` 的 `index.html`，#257 後預期 `GET /console`）與管理 API／`/ws/dashboard`。
4. **不變**：`isHeartbeatAuthorized`、`ChatRoutes`、`RelayRoutes`、桌面端 `client/`、21 點（經 peer Bearer）、Atlas 靜態、`DevStatusTokenAuth`（開發機 heartbeat，與 admin Cookie 無關）。
5. **本文件**：供人工／審查後分 PR 實作；**回滾到舊版 image 會重新打開 deterministic cookie 漏洞**，不得作為常規安全回退（見 §7）。

---

## 1. 威脅模型與相容性盤點

### 1.1 資產與攻擊者

| 資產 | 說明 |
|------|------|
| 遠端取消排程／任務、刪除裝置 | `ServerApp.cancelSchedule`／`cancelTask`／`deleteClient` |
| 飲品牆（簡報）與跑馬燈 | `PhotoRoutes`、`MarqueeRoutes` |
| 塗鴉刪除 | `GraffitiRoutes.delete`（張貼為公開） |
| 開發機清單 | `DevMachineRoutes.list` |
| 裝置名冊與健康指標 | `GET /api/status`、`/api/health/history`、`/ws/dashboard` 推送 |
| 管理密碼 | 可由舊 Cookie（密碼衍生）離線暴力破解 |

攻擊者：(a) 公網匿名者；(b) 曾竊取 Cookie 者（共用電腦、擴充、XSS、截圖）；(c) 跨站誘導已登入管理員（CSRF／CSWSH）；(d) 公司 SSL 攔截（README 已述）。
**本案外**：`HEARTBEAT_SECRET` 強度、RBAC、多實例 session 共用、`IpResolver` 信任 `X-Forwarded-For` 導致登入鎖定繞過（建議另開 issue）。

### 1.2 現況認證行為（程式事實）

```21:25:server/src/main/java/com/example/server/auth/AuthService.java
    public AuthService() {
        adminPassword = envOrDefault("ADMIN_PASSWORD", "secret");
        heartbeatSecret = envOrDefault("HEARTBEAT_SECRET", "punchclock-dev-secret");
        authCookieValue = sha256HexPrefix("punchclock-admin:" + adminPassword, 32);
    }
```

```277:310:server/src/main/java/com/example/server/ServerApp.java
    private void loginPost(Context ctx) {
        // ...
        if (authService.checkAdminPassword(password)) {
            authService.clearLoginAttempt(ip);
            setAuthCookie(ctx, authService.authCookieValue());
            ctx.redirect(nextSafe != null ? nextSafe : "/");
            return;
        }
        // ...
    }

    private void logout(Context ctx) {
        clearAuthCookie(ctx);
        ctx.redirect("/");
    }
```

```562:568:server/src/main/java/com/example/server/ServerApp.java
    private void setAuthCookie(Context ctx, String value) {
        ctx.cookie("auth", value, 60 * 60 * 24);
    }

    private void clearAuthCookie(Context ctx) {
        ctx.removeCookie("auth");
    }
```

```239:249:server/src/main/java/com/example/server/ServerApp.java
        app.ws("/ws/dashboard", ws -> {
            ws.onConnect(ctx -> {
                if (!authService.isAuthFromCookieHeader(ctx.header("Cookie"))) {
                    ctx.session.close();
                    return;
                }
                broadcaster.add(ctx);
                // ...
            });
```

`DashboardBroadcaster` 僅 `Set<WsContext>`，**未**綁定 session、**無**登出關閉、**無**過期主動清理。`dashboard.js` 在 `onclose` 每 3 秒重連，401 未處理。

**登出未撤銷（測試亦描述）**：`PublicIslandAuthTest.signedInAdminCanReadStatusAndLogoutRevokesAccess` 註解寫明「登出只清瀏覽器 Cookie」；若客戶端仍送同一 `auth` Cookie，`isAuth` 仍為 true（與 2026-11-13 黑箱一致）。

### 1.3 相容性盤點（路由 × 驗證）

> **#257 註記**：下列「現況 main」仍含 3D 群島（`/` → `index.html`、`/ws/island`、`/island/*` 等）。#257 合併後將刪除島嶼相關路由與靜態；**session 方案應以 2D 辦公室 ＋ 獨立主控台為長期形狀**，實作時與 #257 協調 HTML／路由，但 **Cookie／store／撤銷邏輯與路由無強耦合**。

| 路由／能力 | 驗證方式 | 本案是否觸及 | 備註 |
|------------|----------|--------------|------|
| `GET/POST /login`、`GET /logout` | 密碼／Cookie | **主要** | `LoginPageRenderer`、`LoginNextPath`（允許 `next=/office`、`/dev-status`） |
| `GET /` | `isAuth` → `{{AUTH_BODY_CLASS}}`／`{{AUTH_JS_BOOL}}` | 是（查 session） | 現況：3D 群島＋嵌入管理 UI；#257：`/` → 2D 辦公室 |
| `GET /console`（規劃） | 同左 | 是 | #257 主控台搬家目標 |
| `GET /office`、`/office.html` | 僅 `{{AUTH_JS_BOOL}}` 旗標 | 邊界 | 訪客可看；`GET /api/dev-machines` 仍要 admin |
| `GET /dev-status` | 公開殼層 | 否 | 機器資料 API 要登入 |
| `GET /api/status`、`/api/health/history` | Cookie | 是 | |
| `POST …/cancel-schedule`、`cancel-task`、`DELETE /api/clients/{id}` | Cookie | 是 ＋ CSRF | 寫入型 |
| `PhotoRoutes` 全路由 | Cookie | 是 | 訪客不可列舉簡報 |
| `MarqueeRoutes` GET/PUT | Cookie | 是 | |
| `GraffitiRoutes` DELETE | Cookie | 是 | POST 建立為公開 |
| `DevMachineRoutes` `GET /api/dev-machines` | Cookie | 是 | heartbeat 用 `DevStatusTokenAuth` |
| `/ws/dashboard` | Cookie header | 是 ＋ Origin ＋ 綁定 session | |
| `/ws/island` | **無** | **不變** | `IslandPresenceRoutes`；#257 刪除 |
| `/css/*`、`/js/*`、`/assets/*`、`/pet/*`、`/audio/*`、`/models/*`、`/island/*` | 公開 | 不變 | #257 後島嶼靜態移除 |
| `AtlasRoutes` `/atlas/*` | 公開 | 不變 | |
| `GET /api/roadmap`、`/api/dev-status`、`/api/tea-health` | 公開 | 不變 | |
| `GET /api/graffiti*`（非 DELETE） | 公開 | 不變 | |
| `POST /api/heartbeat`、`GET /api/client-release/jar` | Bearer | **不變** | |
| `ChatRoutes`、`RelayRoutes` | Bearer | **不變** | 21 點、傳檔、聊天 |
| `GET /ping` | 公開 | 不變 | |

**Repo 內**：`client/`、`shared/` 未使用 `auth` Cookie 或 `/login`（與 d73f399 結論相同；已用搜尋复核）。桌面端不需為本案發新版。

### 1.4 現況缺陷清單（對照程式）

| ID | 位置 | 問題 | 嚴重度 |
|----|------|------|--------|
| F1 | `AuthService`／`isAuth` | 決定性 Cookie；無伺服器端到期／撤銷 | 高 |
| F2 | `sha256HexPrefix` | Cookie 為無鹽密碼衍生；外洩可離線破解 | 高 |
| F3 | `ServerApp.setAuthCookie` | 無 HttpOnly／SameSite／Secure | 中高 |
| F4 | 管理 POST（含 `multipart` 上傳） | 未設 SameSite 時跨站 simple request 風險 | 中 |
| F5 | `registerWebSocket`／`DashboardBroadcaster` | 無 Origin；登出／過期不關 WS | 中 |
| F6 | `dashboard.js` | 無限重連；未處理 401 | 低 |
| F7 | `GET /logout` | 跨站可觸發登出（nuisance） | 低 |
| F8 | `checkAdminPassword` | `String.equals`，非常數時間 | 低 |
| F9 | `IpResolver`＋`loginAttempts` | XFF 可偽造 | 中（**範圍外**） |

---

## 2. Session 架構：隨機 ID、儲存、TTL、登出、重啟、上限與 Log

### 2.1 元件（建議新增／調整）

```
AuthService（保留 isAuth／isHeartbeatAuthorized 等對外語意，內部改查 store）
 ├── AuthConfig          ADMIN_PASSWORD、TTL、COOKIE_SECURE、PUBLIC_BASE_URL、ALLOWED_ORIGINS…
 ├── AdminSessionStore   ConcurrentHashMap<sha256(sessionId), SessionRecord>
 └── AdminOriginGuard    寫入路由與 /ws/dashboard 共用（見 §3.3）
DashboardBroadcaster
 └── Map<WsContext, String sessionKey>；closeSession(key)；廣播前剔除失效 session
```

### 2.2 Session ID 產生與查詢

- `SecureRandom` 產生 **32 bytes** → **Base64url（無 padding，43 字元）**，熵 256 bit。
- 伺服器**只存 `SHA-256(id)`（hex 或 bytes）**；原始 ID 僅出現在 `Set-Cookie` 與請求 Cookie。
- 每次 `loginPost` 成功：**新 ID**（防 fixation），忽略請求內舊 `auth`。
- 格式驗證：非 43 字元 base64url → 視為未登入（不查表、不 log 值、不拋出至回應）。
- **舊 32 hex deterministic 值**：格式不符 → 直接拒絕（§4）。

### 2.3 生命週期

| 項目 | 建議預設 | 說明 |
|------|----------|------|
| 絕對 TTL | 24h（`ADMIN_SESSION_TTL_HOURS`） | 對齊現有 `Max-Age=86400` |
| 閒置 TTL | 2h（`ADMIN_SESSION_IDLE_MINUTES`） | 主控台每 60s 拉 health 時可更新 `lastSeen` |
| `lastSeen` 節流 | ≥60s 更新一次 | 減少寫入 |
| Lazy 清理 | 登入、查詢 session 時順便刪過期項 | 避免測試 `new ServerApp().start(0)` 額外執行緒洩漏 |
| **Bounded 過期清理** | 每 **5 分鐘**掃描 store ＋ 關閉失效 session 之 WS（上限單次處理 N 筆） | **回應 2026-11-13 審查**：不得只靠 `broadcast()` 才發現過期 |
| 登出 | `remove(sessionKey)`、清 Cookie、**`broadcaster.closeSession(key)`**（close code **4401**） | |
| 多 session | 允許；`MAX_SESSIONS=50`，滿則淘汰最久未使用 | |
| 單 session WS 數 | 上限 10（可設定） | |
| 改 `ADMIN_PASSWORD` | 需重啟 → 記憶體 session 全失效 | |
| 程序重啟／Render spin down | 全 session 失效 | 體驗上需再登入（風險 R1） |

### 2.4 併發與資源

- `ConcurrentHashMap`；`SessionRecord` 內 `lastSeen` 用 `AtomicLong`。
- 撤銷具冪等性；`DashboardBroadcaster` 維持 `ConcurrentHashMap` 結構。

### 2.5 密碼比對與啟動警告

- `checkAdminPassword` 改為常數時間比較（例如對雙方做 SHA-256 後 `MessageDigest.isEqual`）。
- `ADMIN_PASSWORD` 使用預設時啟動 log **警告**（行為可不變，避免本機開發被擋）。

### 2.6 Log 規則（硬性）

**永不記錄**：密碼、`auth` Cookie 值、session ID、session 雜湊、完整 `Cookie` header、`HEARTBEAT_SECRET`、Bearer token。
**可記錄**：`[Auth] login ok ip=…`、`login failed`、`logout session revoked`、`sessions active=N`、`origin rejected origin=… path=…`（Origin 非秘密）。

---

## 3. Cookie 旗標、反向代理、CSRF、登出 GET vs POST

### 3.1 Cookie

```
Set-Cookie: auth=<43 chars>; Path=/; Max-Age=86400; HttpOnly; SameSite=Lax[; Secure]
```

- 名稱維持 **`auth`**：舊 32 hex 在新驗證下自然失效。
- **SameSite=Lax**（非 Strict）：保留從外部連結首次導覽可載入頁面；擋跨站 POST／PUT／DELETE 與多數 CSWSH。
- **Secure**：`COOKIE_SECURE=auto|true|false`（預設 `auto`）；生產（Render）建議 **`true`**，勿依賴可被偽造的 `X-Forwarded-Proto` 作為唯一依據（見 §3.2）。
- 實作：Javalin 6 `io.javalin.http.Cookie`（`isHttpOnly`、`sameSite`、`secure`），由 `ServerApp.setAuthCookie`／`clearAuthCookie` 統一套用。

### 3.2 可信來源 URL（回應審查第 3 點）

**禁止**仅以 `ctx.scheme()`、`Host`、`X-Forwarded-Host`、`X-Forwarded-Proto` 的**客戶端可影響組合**作為唯一「預期 Origin」。

建議：

| 變數 | 用途 |
|------|------|
| `PUBLIC_BASE_URL` | 權威站點根（例：`https://punchclock.example.com`），用於產生允許的 origin 集合 |
| `ALLOWED_ORIGINS` | 逗號分隔額外 origin（自訂網域、staging） |
| Render／Cloudflare | 部署文件註明需把對外 URL 寫入上述變數；CI／人工驗收用實際網域測 |

`AdminOriginGuard.expectedOrigins()`：解析 `PUBLIC_BASE_URL` ＋ `ALLOWED_ORIGINS` → `Set<String>`（scheme + host + port，正規化）。

HTTPS 與 Secure cookie：**以設定為準**（`COOKIE_SECURE=true`），`isHttps(ctx)` 僅輔助本機 `auto`。

### 3.3 CSRF／同源（回應審查第 2 點 — **阻擋項**）

2026-11-13 設計中「無 Origin 且無 Referer 則放行」**不得**作為 Stage 2 的通用策略。

**建議分層（Stage 2 至少達成粗體）：**

1. **Cookie 層**：`SameSite=Lax`。
2. **瀏覽器寫入請求**（`POST`／`PUT`／`DELETE` 且使用 Cookie 驗證的管理路由、`POST /login`、**`POST /logout`**、`/ws/dashboard`）：
   - **必須**通過下列其一（實作時寫清判斷順序並加測試）：
     - **(A) 明確 CSRF token**：登入後下發不可猜測 token（HttpOnly 第二 Cookie 或 session 欄位），寫入請求帶 `X-Csrf-Token` 或 hidden field；`dashboard.js` 與登入後表單需帶上（審查建議路線）。
     - **(B) 經審查的 Fetch Metadata ＋ Origin**：要求 `Sec-Fetch-Site` 為 `same-origin` 或（若產品要支援）`same-site`，**且** `Origin` 或 `Referer` 主機落在 `expectedOrigins()`；**若為現代瀏覽器卻缺 Fetch Metadata 與 Origin／Referer → 403**（不可預設放行）。
   - **禁止**：僅因「沒有 Origin」就放行——除非請求明確標記為測試專用（見 Stage 0）。
3. **套用範圍**：`authService.isAdminWrite(ctx)` 取代各 handler 內裸 `isAuth`（**不用**全域 `app.before`，避免誤傷 `ChatRoutes`／`RelayRoutes` Bearer 路由）。
4. **Bearer 路由**：不套 Cookie CSRF。

**測試策略**：Stage 0 的 `AdminTestServer` 在整合測試中為每次請求加上合法 `Origin: http://127.0.0.1:<port>`（與 `PUBLIC_BASE_URL` 測試設定一致），必要時加 CSRF token helper——**不把「放行無 Origin」當 production 行為**。

### 3.4 登出 GET vs POST（回應審查第 5 點）

| 方案 | 說明 |
|------|------|
| **建議（Stage 2）** | 僅 **`POST /logout`**（撤銷 session ＋ Clear Cookie ＋ Origin／CSRF 檢查）。UI：`index.html`／未來 `console` 頁「登出」改為 `<form method="post" action="/logout">` 按鈕。 |
| **過渡（若必要）** | `GET /logout` **最多保留一個版本**，文件與 release note **明示** CSRF 登出風險與**移除期限**（例如下一 minor）。**不建議**長期保留具副作用的 GET。 |
| **現況** | 僅 `app.get("/logout", …)`。 |

---

## 4. 舊 deterministic `auth` Cookie 遷移

**建議：新程式上線當下立即拒絕舊 Cookie，不設寬限期。**

- 舊值為可離線破解的密碼衍生憑證；寬限期等於保留 F1／F2。
- 代價：管理員重新登入一次；Render 部署／重啟後本來也會失去記憶體 session。
- **勿**在每個 401 回應主動 `Set-Cookie: auth=; Max-Age=0`（與新登入競態風險）；僅 `logout` 與成功 `login` 覆寫 Cookie。
- **不提供** `LEGACY_AUTH_COOKIE` 緊急開關（等同保留漏洞）。
- 部署後**建議輪換 `ADMIN_PASSWORD`**（舊 Cookie 可能已被破解嘗試）。

**#257 並行**：舊書籤 `GET /` 在未來可能變 2D 辦公室而非主控台；與 Cookie 遷移獨立，但在 release note 一併說明。

---

## 5. 測試計畫

### 5.1 單元

- **`AdminSessionStoreTest`**（注入 `Clock`）：ID 格式、唯一性、撤銷、絕對／閒置 TTL、`lastSeen` 節流、`MAX_SESSIONS` 淘汰、非法輸入、並行壓力、`toString` 不洩密。
- **`AuthConfigTest`**：`ADMIN_PASSWORD` 邊界、`COOKIE_SECURE` 枚舉、`PUBLIC_BASE_URL`／`ALLOWED_ORIGINS` 解析。
- **`AdminOriginGuardTest`**：允許／拒絕 origin、**拒絕**無 Origin／無 Referer／惡意 `Sec-Fetch-Site` 的寫入（在採用策略 B 時）。

### 5.2 整合（`AdminTestServer` + 隨機密碼）

| 場景 | 期望 |
|------|------|
| 登入成功 | 302；`Set-Cookie` 含 HttpOnly、SameSite=Lax、Path、Max-Age；HTTPS 或 `COOKIE_SECURE=true` 時 Secure |
| 兩次登入 | **不同** auth 值 |
| 登入失敗 | `error=1`、無 auth Cookie；鎖定行為不變 |
| 撤銷後重用 | `POST /logout`（與過渡期 `GET` 若存在）後同一 Cookie → `/api/status` **401** |
| 平行 session | A 登出後 B 仍 200 |
| TTL | 推進 Clock → 401；bounded sweeper 關閉 WS |
| 偽造／舊 32 hex | 管理 API 401 |
| 重啟 | `app.stop()` 後新 `ServerApp` → 401 |
| **WS** | 訪客關閉（現有 `dashboardWebSocketRejectsGuests`）；有效 session 收快照；登出 → **4401** 且不重連風暴；惡意 Origin → **4403**；過期後 **5 分鐘內**必斷（bounded cleanup） |
| **CSRF** | 外部 Origin → 寫入路由 403 且狀態不變；同源（測試注入 Origin）→ 200 |
| **Bearer 迴歸** | `ServerAppRelayTest`、`PeerMessageReliabilityTest`、`BlackjackServerIntegrationTest`、`ServerAppHeartbeatSeqTest` 等全過 |
| **訪客公開面** | 更新 `PublicIslandAuthTest`（#257 後改/assert 2D 辦公室與 `/console`）；現況 main 仍測島嶼訪客；**始終**斷言管理 API 401 |
| **ADMIN_PASSWORD** | 未設／自訂／Unicode — 靠注入 `AuthConfig`，非依賴 shell env |
| **log** | 擷取 stdout，無 Cookie／密碼 |

### 5.3 手動（staging／生產）

- DevTools 檢查 Cookie；A 分頁登出 B 分頁 WS 斷線；桌面端心跳、傳檔、聊天、21 點 smoke test。

---

## 6. `password=secret` 與 `AdminAuthCookie`（Issue 第 6 點）

### 6.1 仍硬編碼預設密碼的測試（**三處**，基準 `f2c1db6`）

| 檔案 | 方法／約略位置 |
|------|----------------|
| `server/src/test/java/com/example/server/BlackjackServerIntegrationTest.java` | `dashboardCancelReachesClientThroughHeartbeat`，`POST /login` body `password=secret` |
| `server/src/test/java/com/example/server/PeerMessageReliabilityTest.java` | `login()` private 方法 |
| `server/src/test/java/com/example/server/web/PublicIslandAuthTest.java` | `loginPostRedirectsToSafeNext`，`password=secret&next=…` |

一旦環境設了 `ADMIN_PASSWORD`，上述測試會失敗（目前靠「未設定 → 預設 secret」僅存僥倖）。

### 6.2 依賴 `authCookieValue()` 的測試 helper

| 檔案 | 用途 |
|------|------|
| `server/src/test/java/com/example/server/auth/AdminAuthCookie.java` | `new AuthService().authCookieValue()` |
| 呼叫點 | `PublicIslandAuthTest`、`AtlasRoutesTest`、`GraffitiRoutesTest`、`DevMachineRoutesTest`、`ServerAppBuildInfoTest`、`AuthServiceTest` |

Session 化後 **`authCookieValue()` 應移除**，這些測試須改走真實 `POST /login` 或注入 store。

### 6.3 Stage 0 修法（可重現、不洩密）

1. `AuthConfig` + `AuthService(AuthConfig, Clock)` 建構子；`AuthConfig.fromEnv()`／`from(Map)`。
2. `ServerApp(ClientRelease, AuthService, …)` package-private 測試建構子（延伸現有 `DevStatusService` 注入型）。
3. 新增 **`AdminTestServer`**（public）：每測試 `SecureRandom` 密碼（不 println、不進 assert 訊息）；`login()` 走 `POST /login` 解析 `Set-Cookie`；`originHeader()` 供 CSRF 測試。
4. 替換三處 `password=secret` 與所有 `AdminAuthCookie`；刪除 `AdminAuthCookie.java`。
5. **Stage 0 不改 production 認證語意**（仍為 deterministic cookie），僅測試隔離——**唯一目前審查明確授權實作的範圍**。

---

## 7. 風險、回滾、驗收

### 7.1 風險

| # | 風險 | 緩解 |
|---|------|------|
| R1 | 重啟／Render 休眠 → 再登入 | 接受；可選 Stage 3 持久化 session（需 disk） |
| R2 | Secure 誤判 | 生產 `COOKIE_SECURE=true` |
| R3 | Origin 誤擋 | `PUBLIC_BASE_URL`＋`ALLOWED_ORIGINS`；log 拒絕原因 |
| R4 | WS 重連風暴 | `dashboard.js` 遇 4401／4403／401 停重連 |
| R5 | 誤傷 Bearer | 不用全域 filter；`isHeartbeatAuthorized` 不動；整合測試必跑 |
| R6 | OOM | `MAX_SESSIONS` |
| R7 | **安全回退** | **回滾舊 image 會恢復 deterministic cookie** — 僅緊急業務回退，且應計畫立刻再前滾或輪換密碼 |
| R8 | #257 路由變更 | session 邏輯獨立；合併時更新公開面測試與登出表單所在 HTML |

### 7.2 回滾

- 無 session 檔案格式遷移（Stage 1–2 純記憶體）。
- 回滾舊版：新 43 字元 Cookie 無效；舊 deterministic **重新有效（漏洞回歸）**。
- 分階段 PR 可只回退 Stage 2（CSRF）而保留 Stage 1（session）— 需有明確版本標籤。

### 7.3 驗收標準

1. 每次登入不同 256-bit session；伺服器不存明文 ID。
2. 登出後 HTTP／WS **立即**失效；其他 session 不受影響。
3. Cookie：`HttpOnly; SameSite=Lax; Path=/`；生產 `Secure`。
4. 跨站寫入／跨站 WS 被拒；**無 Origin 的瀏覽器寫入不被默認放行**。
5. 舊 32 hex Cookie 一律 401。
6. Bearer、peer、21 點、桌面端、訪客 2D／公開 API 行為不變（在 #257 後以新路由為準）。
7. 測試不依賴 env 預設密碼；log 無秘密。
8. `mvn -pl server test` 0 failures。

---

## 8. 分階段實作計畫、檔案清單

> **授權範圍（2026-11-13 審查）**：僅 **Stage 0** 可單獨開 PR 實作；Stage 1+ 需本文件經人工核准後另開 PR。**本 commit 僅新增本文件，不構成 Stage 1 授權。**

| 階段 | 內容 | Production 行為變化 |
|------|------|---------------------|
| **Stage 0** | `AuthConfig`、可注入 `AuthService`／`ServerApp`、`AdminTestServer`；移除 `password=secret` 與 `AdminAuthCookie` | **無** |
| **Stage 1** | `AdminSessionStore`、Cookie 旗標、login 輪換 ID、logout 撤銷、拒絕舊 Cookie、WS 綁定＋登出關閉、bounded 過期清理、常數時間密碼比對、log 規則 | 管理員須重登；登出有效 |
| **Stage 2** | `AdminOriginGuard`、CSRF token **或** Fetch Metadata+Origin、`POST /logout`、寫入路由 `isAdminWrite`、`dashboard.js` 401／440x | 跨站寫入 403 |
| **Stage 3（可選）** | session 持久化、`GET /logout` 移除、F9 另 issue | 視項而定 |

### 8.1 新增檔案（main）

- `server/src/main/java/com/example/server/auth/AuthConfig.java`
- `server/src/main/java/com/example/server/auth/AdminSessionStore.java`
- `server/src/main/java/com/example/server/auth/AdminOriginGuard.java`（或 `OriginCheck` 更名，與審查語意一致）

### 8.2 修改檔案（main）

- `AuthService.java` — 移除 `authCookieValue()`／建構時雜湊；`createSession`／`resolveSession`／`revoke`／`isAdminWrite`；**`isHeartbeatAuthorized` 不動**
- `ServerApp.java` — `loginPost`／`logout`（GET 過渡 + `POST`）、`setAuthCookie`／`clearAuthCookie`、`registerWebSocket`、cancel API、`dashboard`（及 #257 後 `/console` 若從此拆出）
- `DashboardBroadcaster.java` — sessionKey、`closeSession`、廣播驗證
- `PhotoRoutes.java`、`MarqueeRoutes.java`、`GraffitiRoutes.java`（delete）、`DevMachineRoutes.java`（list）— 寫入／敏感讀取改 `isAdminWrite`
- `server/src/main/resources/public/index.html`（及未來 console 頁）— 登出 POST 表單
- `server/src/main/resources/public/js/dashboard.js` — 401／4401／4403

### 8.3 測試

- 新增：`AdminTestServer`、`AdminSessionStoreTest`、`AuthConfigTest`、`AdminSessionIntegrationTest`、`AdminOriginGuardTest`
- 修改：`AuthServiceTest`、`PublicIslandAuthTest`、`AtlasRoutesTest`、`GraffitiRoutesTest`、`DevMachineRoutesTest`、`ServerAppBuildInfoTest`、`BlackjackServerIntegrationTest`、`PeerMessageReliabilityTest`
- 刪除：`AdminAuthCookie.java`

### 8.4 明確不修改（本案）

- `client/`、`shared/`、`atlas-src/`、`ChatRoutes`、`RelayRoutes`、`AtlasRoutes`（auth）、`IslandPresenceRoutes`、`LoginPageRenderer`（維持 POST 表單）、`IpResolver`（F9 另開）、`DevStatusTokenAuth`

### 8.5 環境變數（實作後更新 README，**本設計 PR 不改 README**）

| 變數 | 說明 |
|------|------|
| `ADMIN_PASSWORD` | 強密碼（正式必設） |
| `ADMIN_SESSION_TTL_HOURS` | 絕對 TTL |
| `ADMIN_SESSION_IDLE_MINUTES` | 閒置 TTL |
| `COOKIE_SECURE` | `auto`／`true`／`false` |
| `PUBLIC_BASE_URL` | 權威對外 URL（CSRF／Origin） |
| `ALLOWED_ORIGINS` | 額外允許 origin |
| `MAX_SESSIONS` | 可選 |

---

## 9. 部署前人工確認（延續 issue 清單）

1. 生產是否 Render 單實例？TLS 終止點？是否有自訂網域或 Cloudflare（影響 `PUBLIC_BASE_URL`／`ALLOWED_ORIGINS`）。
2. `render.yaml` free plan 與 disk 是否實際可用（Stage 3 持久化）。
3. 管理員是否接受部署／休眠後再登入。
4. Repo 外是否有監控依賴 `GET /logout` 或固定 `auth` Cookie。
5. Stage 1 上線是否同步輪換 `ADMIN_PASSWORD`。
6. F9（XFF 鎖定）是否另開 P1 issue。

---

## 10. 與 2026-11-13 審查留言對照

| 審查項 | 本文件處理 |
|--------|------------|
| Stage 0 可單獨做、不改 production 認證 | §6.3、§8 表格 |
| Stage 1 優先 session／旗標／舊 Cookie 拒絕 | §2、§4 |
| 不可無 Origin 放行 | §3.3 **阻擋項** |
| 不信任意 X-Forwarded-Host／Host | §3.2 `PUBLIC_BASE_URL` |
| WS 登出即關、過期 bounded cleanup | §2.3、§5.2 |
| 避免長期 `GET /logout` | §3.4 |
| 勿以 3D island 為未來前提；guest/admin 邊界 | §0、§1.3（2D／console） |
| 回滾非安全策略 | §0、§7.1 R7 |
| 僅授權 Stage 0 實作 | §8 首段 |

---

*文件版本：2026-11-21，分支 `ai/issue-27`，僅新增 `design/issue-27/cookie-session-security.md`，未變更任何 production／測試程式。*