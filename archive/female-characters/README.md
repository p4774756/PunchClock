# 3D 群島時期女性角色美術（封存）

這裡保存的是 3D 群島從網站拿掉之後、當時花很多時間打磨過的女性角色定稿。都放在 `archive/`，**網站不會載入，也不會取代現有角色**。

庭院金銅人另外放在 [`../tcm-bronze-figure/`](../tcm-bronze-figure/README.md)。

## 目錄

| 資料夾 | 是誰 | 定稿來源 | 從網站移除 |
| --- | --- | --- | --- |
| [`berry/`](berry/README.md) | 小莓：島嶼探索的第二位可操作角色（2D 看板精靈圖） | [issue #138](https://github.com/p4774756/PunchClock/issues/138)／[PR #148](https://github.com/p4774756/PunchClock/pull/148)，commit `36d32ca` | [PR #281](https://github.com/p4774756/PunchClock/pull/281)（#257） |
| [`npc-women/`](npc-women/README.md) | 島上女性行人：七種造型的 2D 看板，外加機車騎士；以及較早的七種 Q 版行人 GLB | 看板：[issue #188](https://github.com/p4774756/PunchClock/issues/188)／[PR #198](https://github.com/p4774756/PunchClock/pull/198)；GLB：[issue #181](https://github.com/p4774756/PunchClock/issues/181)／[PR #184](https://github.com/p4774756/PunchClock/pull/184) | 同上，PR #281 |

精靈圖與最後上線版本一致（取自 PR #281 刪除前的 main，`28474b75`）。素材分支 `assets/character2`、`assets/npc-women`、`assets/npc-women-sprites` 裡的同一批幀與上線檔完全相同，因此只存一份，另外補上分支裡獨有的概念圖、列隊圖與預覽圖。機車騎士的概念接觸圖來自已刪除的 `assets/city-scooter-riders` 分支（物件仍在本機歷史裡）。

只收定稿，沒有把中間輪次的半成品一併放進來。

## 沒有搬進來、仍在 main 上的角色

這些現在還在用，所以**沒有複製、也沒有移動**：

- 經絡研習室的穴位練習女模特（`atlas-src/tcm-3d/`，含 `anime-assistant` 模型）
- 桌面 App 的麋鹿／米鹿（`client/src/main/resources/pet/milu/`，以及 `assets/desktop-pet/milu-reference.png`）
- 首頁私人秘書立繪（`server/src/main/resources/public/assets/private-team/`）

## 以後怎麼用

看板精靈圖是 400×400 RGBA PNG，可當 2D billboard 或精靈表。行人 GLB 用 `GLTFLoader` 載入，當時有 walk／idle 動畫。細節見各資料夾的 README。
