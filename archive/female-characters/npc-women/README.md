# 女性行人 NPC

島上的 Q 版女性路人。最後在網站上用的是 milu 規格的 2D 看板精靈（七種造型，外加後來的機車騎士）。較早還做過七種造型的 3D GLB（walk／idle），後來改看板後仍留在當時的 main，一併封存。

## 定稿來源

- 2D 看板：[issue #188](https://github.com/p4774756/PunchClock/issues/188)／[PR #198](https://github.com/p4774756/PunchClock/pull/198)，commit `c205262`
- 3D GLB：[issue #181](https://github.com/p4774756/PunchClock/issues/181)／[PR #184](https://github.com/p4774756/PunchClock/pull/184)，commit `15d854c`（這是最後合併進 main 的 GLB；後續「分件貼圖 v2」的 PR #189 沒有合併）
- 從網站移除：[PR #281](https://github.com/p4774756/PunchClock/pull/281)（#257）
- 精靈圖取自刪除前的 main（`28474b75`），與 `assets/npc-women-sprites` 中七種造型的幀相同
- 預覽圖、列隊圖來自 `assets/npc-women`、`assets/npc-women-sprites`
- 機車騎士概念接觸圖與四向原圖來自已刪除分支 `assets/city-scooter-riders`（`b2100dc4`）

## 造型

| 目錄 | 說明 |
| --- | --- |
| `summer` | 夏日 |
| `student` | 學生（有坐姿幀） |
| `fairy` | 精靈 |
| `tech` | 科技 |
| `idol` | 偶像（有坐姿幀） |
| `swimsuit` | 泳裝 |
| `sport` | 運動（有坐姿幀） |
| `scooter` | 機車騎士（只有走路幀；分支預覽裡沒有這一型） |

看板幀為 400×400 RGBA，腳底對齊畫布下方約 41px。核准的正面待機是各造型的 `idle/breath_01.png`。`student`、`idol`、`sport` 另有 `sit/sit_01.png`、`sit_02.png`。完整檔名說明見 `SOURCE-sprites-README.md`（從素材分支原文抄來）。

## 檔案

- `model/npc-women.glb`：七種造型的 Q 版行人模型（約 694 KiB）
- `scripts/build_npc_women.py`：當時用來匯出 GLB 的 Blender 腳本（路徑仍是舊倉庫位置，重建時需自行調整）
- `sprites/<造型>/`：上線用的 2D 看板幀
- `preview/`：各造型正面預覽、與 milu 合影的列隊圖、`npc-women-lineup-v2.jpg`、機車接觸圖與四向原圖

之後若要再用看板，作法與小莓相同：PlaneGeometry billboard、只繞 Y 轉向、走路約 8fps。若要用 3D，用 `GLTFLoader` 載入 `model/npc-women.glb`。
