// 建築發光材質設定。供 island-buildings.js 使用，也可供 Node.js 測試。

export const BUILDING_GLOW = Object.freeze({
  shop: Object.freeze({
    windows_glow: { color: 0xffb15a, amount: 0.95 },
    lantern_glow: { color: 0xff7a3a, amount: 1.1 }
  }),
  casino: Object.freeze({
    windows_glow: { color: 0xffc56a, amount: 1.05 },
    lantern_glow: { color: 0xffc56a, amount: 1.2 },
    neon_glow: { color: 0xff6ad8, amount: 1.15 }
  }),
  'tcm-site': Object.freeze({
    windows_glow: { color: 0xffb15a, amount: 0.9 },
    lantern_glow: { color: 0xff7a3a, amount: 1.15 }
  }),
  'computer-site': Object.freeze({
    windows_glow: { color: 0x7ec8ea, amount: 1.15 },
    neon_glow: { color: 0x3cf0ff, amount: 1.35 }
  })
});

export function glowForMaterial(buildingId, materialName) {
  const map = BUILDING_GLOW[buildingId];
  if (!map) return null;
  return map[materialName] || null;
}
