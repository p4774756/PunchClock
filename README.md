# clickClick

圖形化多任務排程自動打卡桌面工具。

## 前置需求

- **JDK 11+**
- **Maven 3.6+**
- 本機已安裝 **Microsoft Edge** 或 **Google Chrome**（若選用本機瀏覽器模式）

## 建置與執行

```bash
# 編譯並執行測試
mvn test

# 打包 standalone JAR
mvn package

# 執行
java -jar target/clickClick-standalone.jar
```

打包產物：`target/clickClick-standalone.jar`（含所有依賴，可直接分發）。

## 使用方式

1. **設定打卡任務**
   - 輸入目標網址與按鈕 Selector（CSS selector 或 element ID）
   - 選擇日期、時間，可啟用 ±5 分鐘隨機浮動
   - 使用快捷模板快速建立「上班 09:00」「下班 18:00」任務
   - 勾選週一至週五後點「批量新增」一次建立整週排程

2. **管理任務**
   - 編輯、重新排定、立即執行、取消或刪除任務
   - 重啟程式後，未過期的排程任務會自動恢復

3. **雲端連線（選用）**
   - 啟用後定期向 [ping-pong-server](../ping-pong-server/) 回報狀態
   - 心跳 Token 需與伺服器 `HEARTBEAT_SECRET` 一致（本機預設 `clickclick-dev-secret`）
   - 可從 Web 控制台遠端取消全部或單一任務（HTTP 心跳取回指令，約 15 秒內生效）
   - 不支援遠端建立排程；請在本機操作

## 任務持久化

任務紀錄儲存於：

```
~/.clickClick/tasks.json
```

雲端連線設定（Server URL、Client ID、Token、是否啟用）儲存於：

```
~/.clickClick/config.json
```

程式關閉或重啟時自動讀寫。已過期但未執行的排程任務會標記為「取消」。

## 專案結構

```
src/main/java/com/example/
├── App.java                      # 主視窗
├── model/
│   ├── CheckInTask.java          # 任務資料模型
│   └── TaskStatus.java           # 任務狀態列舉
├── service/
│   ├── AutomationService.java    # Playwright 自動打卡
│   ├── SchedulerService.java     # 排程管理
│   ├── HeartbeatService.java     # 雲端心跳回報
│   ├── TaskPersistenceService.java # 本地 JSON 任務持久化
│   └── ConfigPersistenceService.java # 雲端設定持久化
└── ui/
    ├── PanelFactory.java         # UI 面板建構
    └── TaskEditDialog.java       # 任務編輯對話框
```

## 依賴

| 套件 | 用途 |
|------|------|
| Playwright 1.49 | 瀏覽器自動化 |
| LGoodDatePicker | 日期選擇器 UI |
| Gson | JSON 序列化 |
| JUnit 4 | 單元測試 |

## VS Code 除錯

工作區已設定 launch configuration，選擇 **App** 即可直接啟動主程式。
