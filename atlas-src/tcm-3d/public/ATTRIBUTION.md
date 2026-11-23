# Attribution

## 原本角色（Sendagaya Shino）— CC0 1.0

經絡研習室預設角色為 VRoid Studio β 版樣本 **Sendagaya Shino**（千駄ヶ谷 しの）。pixiv Inc. 以 [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) 釋出。

- 說明：https://vroid.pixiv.help/hc/en-us/articles/360012381793
- 本倉庫檔案：`public/models/anime-assistant.glb`
- 服裝：`app/outfits.ts`（制服；露頸隱藏 Tops／Accessory）
- 角色：`app/avatars.ts`（`assistant`）
- 已烘穴位：`app/anime-points.json`（頭頸 21；教學示意）

## 成年角色（AvatarSample_A）— VRoid 官方樣本（不是 CC0）

第二位研習室角色為 VRoid 官方樣本 **AvatarSample_A**。著作權未放棄，**不是 CC0**；使用與改作依 [VRoid 官方樣本利用條件](https://vroid.pixiv.help/hc/en-us/articles/4402394424089-VRoidPreset-A-Z)（可營利／非營利使用、改作、免費再發布；不可販售模型檔、不可標成 CC0、不可暗示 pixiv 官方背書）。

- 官方介紹：https://hub.vroid.com/en/characters/2843975675147313744/models/5644550979324015604
- 本倉庫檔案：`public/models/anime-adult.glb`（髮色改為純黑側分瀏海；其餘為研習室改作）
- 角色：`app/avatars.ts`（`adult`）
- 穴位：尚未烘上（`points: []`）

## Viewer code — MIT

本地中醫教學界面基於 ashemag / Human Atlas（MIT）改編。原始代碼版權見 [CODE-LICENSE.txt](CODE-LICENSE.txt)，來源：https://github.com/ashemag/human-atlas 。

本項目新增中文界面、穴位覆蓋層、針／指壓工具與皮膚接觸示意。視覺適配未經醫學逐穴定位校核。

## Historical / catalog coordinates (no geometry in repo)

`app/teaching-points.json` 仍保留十四經穴名稱與**歷史解剖空間**座標，供之後每週加穴對位參考。這些座標曾吸附於舊版 BodyParts3D 成人男性體表；**倉庫已不再包含** `atlas.json`、`body-*.bin(.gz)` 或任何 BodyParts3D 幾何資產。

歷史資料來源（僅作目錄／對位說明，現行釋出不含其幾何）：

- BodyParts3D 4.0，© The Database Center for Life Science，CC BY 4.0  
  https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html

穴位名稱與編號採用 GB/T 12346—2021。工程座標未經執業醫師逐穴校核，不代表標準取穴精度。

穴位—肌肉對照：`data/acupoint-muscles.json`（人工整理純文字，穴位座標仍為目錄示意；不依賴解剖幾何）。
