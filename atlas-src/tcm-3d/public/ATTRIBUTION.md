# Attribution

## Anime assistant (current model) — CC0 1.0

經絡研習室使用 VRoid Studio β 版樣本 **Sendagaya Shino**（千駄ヶ谷 しの）。pixiv Inc. 以 [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) 釋出。

- 說明：https://vroid.pixiv.help/hc/en-us/articles/360012381793
- 本倉庫檔案：`public/models/anime-assistant.glb`
- 服裝：`app/outfits.ts`（制服；露頸隱藏 Tops／Accessory）
- 角色：`app/avatars.ts`
- 已烘穴位：`app/anime-points.json`（頭頸 21；教學示意）

## Viewer code — MIT

本地中醫教學界面基於 ashemag / Human Atlas（MIT）改編。原始代碼版權見 [CODE-LICENSE.txt](CODE-LICENSE.txt)，來源：https://github.com/ashemag/human-atlas 。

本項目新增中文界面、穴位覆蓋層、針／指壓工具與皮膚接觸示意。視覺適配未經醫學逐穴定位校核。

## Historical / catalog coordinates (no geometry in repo)

`app/teaching-points.json` 仍保留十四經穴名稱與**歷史解剖空間**座標，供之後每週加穴對位參考。這些座標曾吸附於舊版 BodyParts3D 成人男性體表；**倉庫已不再包含** `atlas.json`、`body-*.bin(.gz)` 或任何 BodyParts3D 幾何資產。

歷史資料來源（僅作目錄／對位說明，現行釋出不含其幾何）：

- BodyParts3D 4.0，© The Database Center for Life Science，CC BY 4.0  
  https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html

穴位名稱與編號採用 GB/T 12346—2021。工程座標未經執業醫師逐穴校核，不代表標準取穴精度。

## 肌肉分層解剖（選用模式）— CC BY-SA 4.0 衍生

「肌肉分層」模式使用自 **Z-Anatomy** 匯出並簡化後的骨骼與肌肉 GLB（不含內臟、血管、淋巴、內耳或腎臟網格）。原始 `Z-Anatomy.zip` 不隨倉庫釋出。

- Z-Anatomy：https://www.z-anatomy.com/
  https://github.com/Z-Anatomy/Models-of-human-anatomy
  授權：Creative Commons Attribution-ShareAlike 4.0 International（CC BY-SA 4.0）
  建議標註：`"Z-Anatomy - The libre 3D atlas of anatomy - CC-BY-SA 4.0"`
- 上游 **BodyParts3D**（The Database Center for Life Science）：官方現行 **CC BY 4.0**（2025-02-27 更新）
  https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html
  建議標註：`"BodyParts3D, © The Database Center for Life Science licensed under CC Attribution 4.0 International"`
  Z-Anatomy 文件亦提及舊式 `CC-BY-SA 2.1 Japan` 標示，畫面與本檔同時載明官方現行與 Z-Anatomy 要求文句。
- 衍生 GLB 目錄：`public/models/anatomy/`（gzip 壓縮供 lazy-load）
- 肌解文案：`data/muscle-catalog.json`（自撰繁中摘要，非整段複製外部百科）
- 穴位—肌肉對照：`data/acupoint-muscles.json`（人工整理，穴位座標仍為目錄示意）

僅在使用者切換至「肌肉分層」時才下載上述 GLB；預設少女穴位練習模式不載入。
