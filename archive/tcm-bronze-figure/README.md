# 庭院金銅人（封存）

3D 群島時期，中醫推拿島庭院裡那尊寫實女性「人體針灸銅人」。全身拋光金銅、體表經絡刻線與穴位、紅白藍腰帶，站在刻有「人體針灸銅人」的三階金台座上。館門另有一尊較小的裝飾像，與庭院共用同一個 GLB。

這份封存只放在 `archive/`，**網站與測試都不會載入**。之後若要做人體研究室，可把模型再搬進 `public/models/`。

## 定稿來源

- 議題：[issue #221](https://github.com/p4774756/PunchClock/issues/221)
- 合併：[PR #225](https://github.com/p4774756/PunchClock/pull/225)（2026-11-20 11:11，Asia/Taipei）
- 定稿 commit：`2b912e1c61b8458e6bc8b31f1b8d84d48817da3c`
- 先前版本：[#200](https://github.com/p4774756/PunchClock/issues/200)／[PR #201](https://github.com/p4774756/PunchClock/pull/201) 用球和管子拼人體；#213／PR #214 未合併，由 #221 取代。
- 從網站移除：[PR #281](https://github.com/p4774756/PunchClock/pull/281)（#257，2026-11-21 08:05，Asia/Taipei），與整座 3D 群島一併刪除。

合併進去的是第 13 輪（r13）。那一輪只修三件事，其餘沿用 r8／r9：

- 經絡碎段：刪掉頭頸與上臂的短殘段，軀幹主線（任脈、胃經等）保持連續。
- 後中接縫：放寬縫合門檻並做局部平滑，去掉 r12 修補造成的凹脊。
- 頸背髮際改成淺 U 形。

前額／太陽穴髮際的梳齒當時沒修好，髮殼流程退回 r9。胸型、匾、台座、腰帶、臉、經絡主線都沒再改。

## 檔案

| 路徑 | 說明 |
| --- | --- |
| `model/tcm-bronze-figure.glb` | 定稿模型（約 1.5 MiB，含 PBR 材質、經絡刻線與穴位貼圖、髮髻、腰帶、台座） |
| `data/tcm-bronze-surface.json` | 59 穴（含左右）貼在體表的座標與法線、分區包圍盒 |
| `data/tcm-bronze-points.json` | 庭院互動用的穴位文案與編號 |
| `scripts/build_tcm_bronze_figure.py` | 以 MakeHuman 底模重建 GLB 的 Blender 腳本 |
| `scripts/prep_bronze_base_mesh.py` | 從 MakeHuman CC0 素材生成 `mh-female-base.glb` |
| `scripts/verify_tcm_bronze_glb.py` | 匯出後檢查頂點、經絡、材質 |
| `scripts/gen_plaque_texture.py` | 台座匾「人體針灸銅人」貼圖 |
| `scripts/gen-tcm-bronze-points.mjs` | 產生／更新 `tcm-bronze-points.json` |
| `scripts/bronze-base/` | MakeHuman CC0 底模、原始 target／權重，以及 `SOURCE.txt`（授權 CC0 1.0） |
| `reference-js/` | 當時在島上放置、載入材質與點穴的程式，僅供參考，不會被網站載入 |
| `preview/final-sheet.jpg` | 獨立審查最終對照表（Blender 棚燈） |
| `preview/final-front.jpg` | 從對照表裁出的全身正面 |

`preview/` 兩張圖是這次封存特別放進 repo 的定稿效果圖，方便以後對照，不必再翻審查目錄。

## 如何重新渲染或重建

需要本機的 Blender（當時用 `~/.local/bin/blender -b --factory-startup`）。在倉庫根目錄：

```bash
# 若要從頭重做底模（通常不必，mh-female-base.glb 已在 scripts/bronze-base/）
blender -b --factory-startup -P archive/tcm-bronze-figure/scripts/prep_bronze_base_mesh.py -- \
  archive/tcm-bronze-figure/scripts/bronze-base/mh-src \
  archive/tcm-bronze-figure/scripts/bronze-base/mh-female-base.glb

# 重建 GLB（腳本裡的路徑仍指向舊的 tools/blender 與 public/models，
# 執行前請把輸入／輸出改成這個封存目錄，或先把檔案暫時拷回原路徑）
blender -b --factory-startup -P archive/tcm-bronze-figure/scripts/build_tcm_bronze_figure.py
```

`build_tcm_bronze_figure.py` 與 `prep_bronze_base_mesh.py` 是依當時倉庫路徑寫的，封存後沒有改程式，重建時要自行改路徑或暫時放回 `tools/blender/` 與 `server/src/main/resources/public/models/`。

審查當時是用固定相機、低角度側光，輸出 head-front／head-34／head-side／head-back、chest、full-front、plaque。

## 以後在人體研究室裡怎麼用

1. 把 `model/tcm-bronze-figure.glb` 拷到網站可存取的位置，例如 `server/src/main/resources/public/models/tcm-bronze-figure.glb`。
2. 用 Three.js 的 `GLTFLoader` 載入。材質已是 PBR 金屬（暖金銅、metalness 約 0.9、roughness 約 0.25–0.35），場景需要環境貼圖（envMap）才會看得出拋光高光；不要把腰帶的布料材質當成青銅處理。
3. 若要點穴，讀 `data/tcm-bronze-surface.json` 的 `instances`（世界座標與法線）。文案在 `tcm-bronze-points.json`。
4. 模型面向：GLB 本身是 +Z 朝前、腳底 y=0、身高約 1.72 m。放進新場景時再調位置與旋轉即可。

授權：人體底模來自 [MakeHuman](https://github.com/makehumancommunity/makehuman) 系統素材，CC0 1.0，細節見 `scripts/bronze-base/SOURCE.txt`。
