# 經絡研習室（tcm-3d）

中醫穴位課堂演示：以動漫助教角色展示已烘上的穴位，並用針／指壓做接觸演示。

**[體驗](https://tcm.jic.io/3d/) · [加入 JIC Discord](https://discord.gg/jic)**

## 能做什麼

- 開啟即進入少女角色（制服／露頸換裝），無解剖銅人切換。
- 點選已烘穴位（目前頭頸 21），播放或按住「針」「指壓」演示。
- 抓住工具沿皮膚拖動；穴位庫只列出已烘穴，其餘目錄保留在 `teaching-points.json` 供之後加穴。
- 深連結：`?set=head-neck-21`、`?code=GV24+` 等。

## 角色與許可

| 層次 | 來源 |
| --- | --- |
| 角色模型 | VRoid Studio β「Sendagaya Shino」，pixiv Inc.，CC0 1.0 → `public/models/anime-assistant.glb` |
| 查看器基礎 | [ashemag / Human Atlas](https://github.com/ashemag/human-atlas)（MIT）改編 |
| 穴位目錄 | GB/T 12346—2021；`teaching-points.json` 含歷史／目錄座標（舊銅人空間，僅供對位參考，模型已移除） |

完整署名見 [public/ATTRIBUTION.md](public/ATTRIBUTION.md)、[NOTICE](NOTICE)。

## 當前邊界

這是教學交互工程預覽。座標為教學示意，未經逐穴臨床校核。針／指壓動畫不對應真人力度或深度。尚未烘上的穴不顯示，之後依課堂每週加入。

## 本地運行

需要 Node.js 22.13+。

```sh
npm ci
npm run dev
```

打開 http://127.0.0.1:3016/ 。

```sh
npm run check
node scripts/validate-tool-drag.mjs
node scripts/validate-teaching.mjs
node scripts/validate-bake-merge.mjs
npm run build:punchclock
```

## 每週加穴

1. 在 `scripts/bake-anime-points.mjs` 的 `seeds` 補種子座標。
2. `node scripts/bake-anime-points.mjs --codes GV16,GB20,BL10`（合併，不清空）。
3. 目視確認，必要時手修 `app/anime-points.json`。
4. 穴庫自動只列已烘穴。

## 許可

- 應用代碼：MIT（保留 ashemag 原聲明）。
- 角色模型：CC0 1.0。
- 外部標準與依賴保留各自權利。
