# Anatomy data attribution

BodyParts3D, © The Database Center for Life Science licensed under CC Attribution 4.0 International.

- License: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html (updated 2025-02-27)
- Dataset: https://dbarchive.biosciencedbc.jp/en/bodyparts3d/download.html
- License terms: https://creativecommons.org/licenses/by/4.0/
- Source geometry: `isa_BP3D_4.0_obj_99.zip`, BodyParts3D 4.0.
- English names and relationships: IS-A and PART-OF concept, element, and inclusion tables from the same archive.
- Publication: Mitsuhashi et al. (2009), BodyParts3D: 3D structure database for anatomical concepts. https://doi.org/10.1093/nar/gkn613

Adaptations: axes and units converted from millimeters/Z-up to meters/Y-up; translated to rest at the stage; geometry simplified using meshoptimizer with 0.2% relative error limit per structure; normals quantized to signed 16-bit; packed into binary chunks; curated display system groupings and colors. The source contains 2,234 individual OBJ meshes; all remain represented. The combined hierarchy contains 3,432 named FMA concepts, which may reference multiple meshes. Original source identity is preserved in the manifest.

Source OBJ comments mention an older CC BY-SA 2.1 Japan license. The official current database license linked above supersedes that legacy text and explicitly permits redistribution and adaptation under CC BY 4.0.

BodyParts3D represents an adult male reference anatomy based on TARO MRI and anatomical illustration refinements. It is not a complete model of every possible human anatomical structure or variation. This interface is educational and is not a clinical tool.

## Historical assets (not included in the current release)

Earlier repository revisions included female reference anatomy: Kristen Browne and Heidi Schlehlein, Human Reference Atlas / HuBMAP, *3D Reference Organ Set for Female v1.5* (2023). CC BY 4.0. Geometry adapted for this viewer.

- Source DOI: https://doi.org/10.48539/HBM352.BTSQ.586
- Dataset: https://lod.humanatlas.io/ref-organ/united-female/v1.5
- Original GLB: https://cdn.humanatlas.io/digital-objects/ref-organ/united-female/v1.5/assets/3d-vh-f-united.glb
- License: https://creativecommons.org/licenses/by/4.0/

Adaptations: translated native meter/Y-up coordinates onto the stage, coincident vertices welded and source normals averaged, geometry simplified with a 0.2% per-structure relative error bound, and normals quantized. Colors and display systems are curated for this interface. All 888 source meshes are represented, with 1,073 source nodes available as selectable individual or compound concepts.

This is a reference assembly with whole-body surface and selected organs, including female reproductive anatomy. Its skeleton and muscle coverage is partial. It is not a complete model of every human structure or a single-person scan. Eight placenta/umbilical structures are classified under Pregnancy reference and hidden by default.

## 中醫教學臺的代碼適配

頭皮取穴展示隱藏獨立頭髮網格（FJ2813），露出原有頭皮及其穴位標記；原始模型資產不變。

本地中醫教學界面基於 ashemag / Human Atlas（MIT）改編。原始代碼版權聲明和許可全文見 [CODE-LICENSE.txt](CODE-LICENSE.txt)，代碼來源：https://github.com/ashemag/human-atlas 。

本項目新增中文界面、穴位工程覆蓋層、六種工具幾何、膚色/程序紋理、皮膚接觸反應示意及教學控制。上述視覺適配沒有完成醫學逐穴定位校核，也沒有改變人體數據的獨立 CC BY 4.0 許可。

2026-09-07：增加十四經穴目錄與分段體表經脈示意。種子座標來自本項目舊 PressPoints 銅人，經區域參考適配及幾何吸附；未經醫學逐穴審核。課堂體表移除男性外生殖器區域的原表面及獨立陰毛網格，以與邊界連接的平滑皮膚曲面替換，並重新計算浮點法線，原始模型資產保持不變；簡化區域不用於精細解剖或取穴。齦交僅收錄目錄，不放置外皮膚標記。

## 動漫研習室角色與換裝（CC0）

經絡研習室「動漫角色」視圖使用 VRoid Studio β 版樣本 **Sendagaya Shino**（千駄ヶ谷 しの）。pixiv Inc. 以 [CC0 1.0](https://creativecommons.org/publicdomain/zero/1.0/) 釋出，可自由改編與再發佈。

- 說明：https://vroid.pixiv.help/hc/en-us/articles/360012381793
- 打包來源（OpenGameArt 鏡像）：https://opengameart.org/content/vroid-studio-cc0-models
- 本倉庫檔案：`public/models/anime-assistant.glb`（由原 `.vrm` 以 glTF 二進位形式保存）
- 服裝登錄：`app/outfits.ts`（制服；露頸練習裝隱藏 Tops／Accessory 材質）
- 角色登錄：`app/avatars.ts`（原本角色）
- 頭頸 21 穴對應：`app/anime-points.json`（依解剖基準穴比例與臉部／頸部骨骼標誌吸附到角色皮膚；教學示意，非臨床取穴）

若要更換自備角色：將 glTF／GLB 放到 `public/models/anime-assistant.glb`（Y-up、公尺、站立於地面、臉朝 −Z、身高約 1.5–1.7 m；髮型勿遮耳周／下顎／頸前），再執行 `node scripts/bake-anime-points.mjs` 重生穴位，或手修 `app/anime-points.json`。

## 穴位座標的來源與權利

十四經穴的三維座標種子（`data/legacy-meridians.mjs`）由 JIC 在早前的 PressPoints 銅人項目中自行標定：在人體模型表面由人工目測點取，並非從任何第三方穴位數據集、商業素材或開源座標表複製而來。權利歸屬 © JIC contributors，隨本項目以 MIT 發佈。

`app/teaching-points.json` 中的最終位置由上述種子經區域參考適配與幾何吸附生成，每條記錄的 `positionProvenance` 字段說明該點參照了 BodyParts3D 的哪些骨性或肌性標誌。參照解剖模型定位這一行為不使 BodyParts3D 的 CC BY 4.0 許可擴展到座標本身；模型幾何文件的許可見本文件開頭。

穴位名稱與編號採用 GB/T 12346—2021（362 穴，含 GV24+ 印堂）。標準文本本身的權利歸屬發佈機構，本項目僅使用其名稱與編號體系。

全部座標標記 `reviewed: false`，是教學顯示用的工程值，未經執業醫師逐穴校核，不代表標準取穴精度。複用者請保留這一限制說明。
