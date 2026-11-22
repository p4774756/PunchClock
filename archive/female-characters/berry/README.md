# 小莓（berry）

3D 群島探索裡的第二位可操作角色，2D 看板精靈，動作規格對齊當時的 milu（待機呼吸／眨眼、四向走路、揮手）。

- 議題：[issue #138](https://github.com/p4774756/PunchClock/issues/138)
- 合併：[PR #148](https://github.com/p4774756/PunchClock/pull/148)，commit `36d32ca`
- 從網站移除：[PR #281](https://github.com/p4774756/PunchClock/pull/281)（#257）
- 精靈圖取自刪除前的 main（`28474b75`），與素材分支 `assets/character2` 的幀相同
- `berry-concept.jpg` 來自 `assets/character2`，是角色概念圖，當時沒有合併進網站

## 檔案

- `berry-concept.jpg`：概念圖
- `sprites/idle/`：正面與背面的呼吸、眨眼
- `sprites/walk/`：前、後、左、右各 4 幀
- `sprites/wave/`：揮手 4 幀

之後若要重新上場，把 `sprites/` 接到現有的 2D 看板邏輯即可（只繞 Y 轉向鏡頭、走路約 8fps、依面向分桶）。
