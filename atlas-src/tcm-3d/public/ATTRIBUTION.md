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
