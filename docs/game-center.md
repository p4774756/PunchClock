# 遊戲中心交接

給下一個 agent。這是既有「洋流」桌面程式的擴充，不是新專案。規則與畫面已經接上，**還沒在真的視窗裡點過**，也**還沒 commit**。

規格來源：`/Users/mac/Downloads/yangliu_game_pack/CURSOR_PROMPT.md`。同資料夾的 `concept_reference.png` 只是配色參考，沒有放進 repo，也不要裁進 UI。圖上的麻將、撲克、假數字與文案不要做。

## 不要動

- 首頁、排程任務、好友與傳檔、設定、日誌，以及現有角色陪伴（`AnimeCast`、`hero-sheet.png`）。
- `~/.punchclock/config.json`、`tasks.json` 的格式。點數在另一個檔。
- 不要把遊戲分頁插進 `JTabbedPane` 中間。舊的 `selectedTabIndex` 會開錯頁。遊戲是最後一頁，index **4**。
- 不要大改 `App.java`。目前只加了分頁和首頁「玩遊戲」。
- 不要加真實金錢、充值、提領、兌換、付費入口，也不要加依賴。
- 不要 commit / push，除非使用者明確要求。
- 終端機裡若還有 `suspend=y` 的除錯中 `com.example.App`，不要殺掉。那個行程不會載入新 class，要使用者自己重開。

## 現在的導覽

側欄順序（`AnimeShell.NAV`）：首頁、排程任務、好友與傳檔、**遊戲中心**、設定、日誌。

對應分頁（`NAV_TO_TAB`）：`-1, 0, 2, 4, 1, 3`。

| 側欄 | 分頁 index |
|------|------------|
| 首頁 | 不是分頁（`-1`） |
| 排程任務 | 0 |
| 好友與傳檔 | 2 |
| 遊戲中心 | 4 |
| 設定 | 1 |
| 日誌 | 3 |

首頁快捷操作多了「玩遊戲」，呼叫 `shell.openTab(4)`。原有「執行任務／開啟聊天／傳送檔案」還在。`HomeBoard.PLAY = 35`。

## 點數

`com.example.game.GameWallet`：三款遊戲共用。初始 10,000。同一時間只能有一局。

- `begin(game, bet)` 立刻扣點。下注 &lt; 1、超過餘額、上一局未結束，都不扣。
- `finish(returned, detail)` 只能一次。`returned` 含原本的下注；0 表示這注輸掉。
- `reset()` 在有進行中的一局時會丟 `GameRuleException`。畫面上的「重置點數」先走 `UiFonts.showConfirm`，同意才呼叫。
- 崩潰或關掉時若還有未完成的一局，下次 `GameStore.load()` 會把該注退回，並寫一筆「上次沒有完成，下注已退回」。

存檔：`~/.punchclock/games.json`（`GameStore`）。跟排程、連線設定分開。

## 三款規則

都在 `com.example.game`，UI 只負責動畫與按鈕。結果先算出來再播動畫。

**老虎機** `SlotMachine`。符號權重：櫻桃 40、檸檬 28、鈴鐺 16、星星 10、紅七 6。倍數含本金：三個紅七 ×20、星星 ×10、鈴鐺 ×6、檸檬 ×4、櫻桃 ×3；剛好兩個櫻桃 ×2；其他 0。捲軸約 70ms 一跳，第 8／14／22 跳停下。

**21 點** `Blackjack`。52 張。A 先當 11，爆了再改 1。J／Q／K = 10。莊家未滿 17 補牌，含軟 17 在內的 17 就停。沒有分牌、加倍、保險。Blackjack 退回 `bet * 5 / 2`（3:2，不足 1 點捨去）。雙方都 Blackjack 算平手。開局若已有 Blackjack 會直接結束，不能再補牌。

**輪盤** `Roulette`。單零 0–36。紅號寫在 `RED_NUMBERS`。可押紅、黑、單、雙、1–18、19–36，押中退回兩倍。0 全部沒中。沒有單號直注。

角色在 `GameCompanion`，沿用 `/hud/anime/hero-sheet.png`。沒有透明去背圖，不要假裝有。情緒：閒置、開心、鼓勵、沒點到時安慰。台詞不能暗示必勝或叫人繼續下注（測試擋「必勝、穩贏、加碼、回本、再押、充值、提領」）。遊戲中心寬度 &lt; 880 時藏起角色，避免擋住按鈕。

配色：老虎機粉金、21 點墨綠桌、輪盤深紅金，外框仍是現有深紫。`NightTheme` 的膠囊按鈕在取得焦點時多畫一圈白線。

## 檔案

新增：

- `client/src/main/java/com/example/game/`：`GameWallet`、`GameStore`、`GameRecord`、`GameRuleException`、`SlotMachine`、`Blackjack`、`Roulette`
- `client/src/main/java/com/example/ui/game/`：`GameCenterPanel`、`SlotGamePanel`、`BlackjackGamePanel`、`RouletteGamePanel`、`GameCompanion`、`GameForms`、`GameHost`
- 測試：`client/src/test/java/com/example/game/` 四個、`ui/game/GameCompanionTest`、`ui/hud/AnimeShellNavTest`

改過、尚未 commit：

- `App.java`：最後一個分頁加上遊戲中心；首頁 `playGame()` → `openTab(4)`
- `AnimeShell.java`：側欄與 `NAV_TO_TAB`
- `HomeBoard.java`：第四顆「玩遊戲」
- `NightTheme.java`：按鈕焦點白框

裝置 ID 加寬到 280、並與大頭照靠左對齊，已經在 `2a288a7`，不要重做。

## 已跑過的測試

2026-10-26，JDK 11（`/Library/Java/JavaVirtualMachines/openjdk11-microsoft/Contents/Home`）。沙盒看不到這支 JDK 時，Maven 要在沙盒外跑。

```bash
export JAVA_HOME="/Library/Java/JavaVirtualMachines/openjdk11-microsoft/Contents/Home"
mvn -pl client -am test \
  -Dtest=GameWalletTest,SlotMachineTest,BlackjackTest,RouletteTest,GameStoreTest,AnimeShellNavTest,GameCompanionTest,ConfigPersistenceServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

34 項通過，含既有 `ConfigPersistenceServiceTest`（確認沒改到連線設定的讀檔）。

涵蓋：初始 10,000、無效下注不扣點、餘額不足、單局不能再開、重複 `finish` 不派彩、重置中途被拒、重置後回到 10,000、A 的點數、爆牌、Blackjack 3:2、雙方 Blackjack 平手、莊家補到 17、軟 17 不補、輪盤 0 全輸、紅黑單雙區間、重啟後餘額、未完成的一局會退回、壞掉的 json 不丟例外、側欄順序與舊分頁 index、首頁「玩遊戲」點得到、角色台詞沒有誘導下注。

**沒做：** 沒開視窗點過側欄、四顆快捷鍵、三款動畫、重置對話框、窄視窗藏角色。重置的「再問一次」只在 `GameCenterPanel` 裡，測試沒有把對話框點下去。

整包 `mvn -pl client -am test` 中途去抓 Playwright WebKit，`playwright.azureedge.net` 回 HTTP 400，行程已停掉。這不是遊戲中心的失敗。不要為了這個去改打卡瀏覽器下載。

## 建議下一步

1. 使用者重開桌面端（不要附在舊的 suspend 除錯上）。看側欄「遊戲中心」、首頁「玩遊戲」、三款能不能玩完一局、重置有沒有再問一次、視窗縮小時角色會不會擋住按鈕。
2. 輪盤指針有沒有停在開出的號碼上。演算法在 `RouletteGamePanel`：Java `fillArc` 的 0 度在三點鐘方向、90 度在十二點方向，指針畫在圓的正上方。若對不齊，只改角度，不要改 `Roulette.wins`。
3. 首頁四顆快捷鍵在很窄時會變擠。`HomeBoard.layout` 目前仍是一列四顆。
4. 使用者若要求，再 commit。不要把 `~/.punchclock/games.json` 放進 git。
5. README 還沒寫遊戲中心。使用者沒要求就不要改 README。
