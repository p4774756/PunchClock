# 行人 NPC v2 看板圖（#188）

七種女性造型的 milu 規格 2D 看板幀。每張 400×400 RGBA，腳底對齊 canvas 下方 41px（footV 0.1025），站立角色內容高度約 320px。

核准的正面待機圖即 `idle/breath_01.png`。動作圖依 milu 相同檔名：

- `idle/breath_01.png` … `breath_04.png` — 正面呼吸
- `idle/blink_01.png`, `blink_02.png` — 正面眨眼
- `idle/back_breath_01.png` … `back_breath_04.png`
- `idle/back_blink_01.png`, `back_blink_02.png`
- `walk/{front,back,left,right}_01.png` … `_04.png`
- `sit/sit_01.png`, `sit_02.png` — 僅 student、idol、sport（島上座位）

造型目錄：`summer` `student` `fairy` `tech` `idol` `swimsuit` `sport`。

實作時把這些幀接到 milu 同一套 billboard（PlaneGeometry、只繞 Y 轉向鏡頭、8fps walk、面向分桶），拿掉 3D `npc-women.glb` 載入，保留 PR #189 的三處生成點修正。不要合併 PR #189。
