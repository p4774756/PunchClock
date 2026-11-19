// 本島與城市島共用的地表／小物／光暈 helper（#146 → #144 沿用）。場景 mesh 見 island-decor-scene.js。
import { G, MAX_LAMP_POINT_LIGHTS, lampGroundHaloRadius, lampHaloOpacity } from './island-kit.js';

export { MAX_LAMP_POINT_LIGHTS };

export const DECOR_STONE = Object.freeze({
  edge: 0xe4d2b2,
  face: 0xf4e6cf,
  seam: 0xd8c4a4,
  accent: 0xead8bc
});

export const DECOR_GRASS = Object.freeze({
  top: 0xa8d06e,
  patch: 0x93c462,
  bright: 0xc5e098
});

export const DECOR_SAND = Object.freeze({
  top: 0xf0ddb4,
  grain: 0xe8cf9e
});

export const MAIN_PAVING_SPECS = Object.freeze([
  { x: 0, z: 3.05, w: 6.5, d: 4.35, rotY: 0 },
  { x: 0, z: 6.15, w: 1.9, d: 2.55, rotY: 0 },
  { x: -4.15, z: 3.55, w: 2.5, d: 1.7, rotY: 0 },
  { x: 4.35, z: 3.45, w: 2.3, d: 1.65, rotY: 0 },
  { x: -3.55, z: 0.55, w: 1.55, d: 2.7, rotY: 0 },
  { x: 3.7, z: 0.7, w: 1.7, d: 2.5, rotY: 0 },
  { x: 0, z: -4.7, w: 7.4, d: 1.2, rotY: 0 },
  { x: 5.15, z: 5.55, w: 2.6, d: 1.15, rotY: -0.55 }
]);

export const MAIN_EXTRA_BENCHES = Object.freeze([
  { x: -2.05, z: 2.35, rotY: 0.55 },
  { x: 3.25, z: 5.15, rotY: -0.35 }
]);

export const MAIN_EXTRA_FLOWER_BEDS = Object.freeze([
  { x: -1.15, z: 2.55, w: 1.05, d: 0.62 },
  { x: 2.4, z: 1.85, w: 0.95, d: 0.58 }
]);

/** 本島草地貼圖層（圓柱中心 y；頂面須低於石板 G+0.059）。 */
export const MAIN_ISLAND_GRASS_DECOR_CENTER_Y = G + 0.013;
export const MAIN_ISLAND_GRASS_DECOR_HEIGHT = 0.026;
export const MAIN_ISLAND_GRASS_PATCH_CENTER_Y = G + 0.014;
export const MAIN_PAVING_GROUP_Y = G + 0.034;
export const MAIN_PAVING_BRICK_HEIGHT = 0.05;

/** 神之島手搖 GLB 本地座標（model.position.y = G，不含 G）。正面朝 +Z。 */
export const SHOP_GLB_BASE = 0.08;
export const SHOP_GLB_DEPTH = 3.2;
export const SHOP_GLB_WALL_H = 1.92;
export const SHOP_GLB_ROOF_Y = SHOP_GLB_BASE + SHOP_GLB_WALL_H + 0.14;
export const SHOP_GLB_FRONT_Z = SHOP_GLB_DEPTH / 2;
export const SHOP_EAVES_BULB_Y = SHOP_GLB_BASE + SHOP_GLB_WALL_H - 0.5;
export const SHOP_EAVES_BULB_Z = SHOP_GLB_FRONT_Z + 0.12;
export const SHOP_EAVES_SIGN_Y = SHOP_GLB_BASE + SHOP_GLB_WALL_H - 0.48;
export const SHOP_EAVES_SIGN_Z = SHOP_GLB_FRONT_Z + 0.1;
export const SHOP_EAVES_LIGHT_SPAN = 4.35;

/** 石板磚格：決定性色差與縫線偏移。 */
export function pavingTileLayout(w, d, tileW, tileD, seed) {
  const tw = tileW == null ? 0.42 : tileW;
  const td = tileD == null ? 0.38 : tileD;
  const s = seed == null ? 0 : seed;
  const tiles = [];
  const cols = Math.max(1, Math.floor(w / tw));
  const rows = Math.max(1, Math.floor(d / td));
  const ox = -w / 2 + tw / 2;
  const oz = -d / 2 + td / 2;
  for (let row = 0; row < rows; row++) {
    const rowShift = (row % 2) * tw * 0.22;
    for (let col = 0; col < cols; col++) {
      const hash = ((col + 7) * 92821 + (row + 3) * 68917 + s * 1337) % 9973;
      const tone = hash % 5;
      const color = tone === 0 ? DECOR_STONE.seam : tone === 1 ? DECOR_STONE.accent : DECOR_STONE.face;
      tiles.push({
        x: ox + col * tw + rowShift,
        z: oz + row * td,
        color: color,
        rotY: (hash % 3) * 0.002
      });
    }
  }
  return tiles;
}

/** 與 lampGroundHaloRadius 相容、夜間略大的半徑。 */
export function decorLampHaloRadius(power) {
  return lampGroundHaloRadius(power) * 1.045;
}

/** 與 lampHaloOpacity 相容、夜間略暖亮的不透明度。 */
export function decorLampHaloOpacity(power, mix) {
  const base = lampHaloOpacity(power, mix);
  if (mix < 1e-6) return 0;
  return Math.min(0.96, base * (1 + mix * 0.08));
}

/** 屋簷串燈燈泡沿線分布（local X）。 */
export function stringLightBulbLayout(span, count) {
  const n = Math.max(2, Number(count) || 8);
  const half = span / 2;
  const out = [];
  for (let i = 0; i < n; i++) {
    const t = n === 1 ? 0.5 : i / (n - 1);
    out.push({ x: -half + span * t, phase: (i * 0.71) % (Math.PI * 2) });
  }
  return out;
}

/** 減少動態效果時串燈為靜態；否則極輕微呼吸（不閃爍）。 */
export function stringLightGlowScale(reduced, elapsed, phase) {
  if (reduced) return 1;
  const e = Number(elapsed) || 0;
  const p = Number(phase) || 0;
  return 0.94 + 0.06 * Math.sin(e * 0.55 + p);
}

export function stringLightEmissiveAtMix(mix, reduced, elapsed, phase) {
  const m = Math.max(0, Math.min(1, Number(mix) || 0));
  const dayE = 0.03;
  const nightE = 1.18;
  const base = dayE + (nightE - dayE) * m;
  return base * stringLightGlowScale(reduced, elapsed, phase);
}

/** 花圃小花位置（決定性）。 */
export function flowerBedStemLayout(w, d, count, seed) {
  const n = Math.max(1, Number(count) || 5);
  const s = seed == null ? 0 : seed;
  const stems = [];
  for (let i = 0; i < n; i++) {
    const hash = (i * 48271 + s * 31337) % 10007;
    const fx = ((hash % 1000) / 1000 - 0.5) * w * 0.82;
    const fz = (((hash / 1000) % 1000) / 1000 - 0.5) * d * 0.78;
    const petal = [0xf4a0c0, 0xf6d35a, 0xfffaf6, 0xf06a2a, 0x9fd9b4][hash % 5];
    stems.push({ x: fx, z: fz, petal: petal });
  }
  return stems;
}

/** 夜空背景色（深藍漸層頂色，mix 0～1）。 */
export function exploreSkyColorHex(mix) {
  const t = Math.max(0, Math.min(1, Number(mix) || 0));
  const day = { r: 0xd8, g: 0xf7, b: 0xf8 };
  const night = { r: 0x0c, g: 0x24, b: 0x58 };
  const r = Math.round(day.r + (night.r - day.r) * t);
  const g = Math.round(day.g + (night.g - day.g) * t);
  const b = Math.round(day.b + (night.b - day.b) * t);
  return (r << 16) | (g << 8) | b;
}

/** 星星場 opacity 加成（夜間更清晰）。 */
export function starFieldOpacityBoost(t) {
  const mix = Math.max(0, Math.min(1, Number(t) || 0));
  return 0.92 + mix * 0.08;
}

/** 海面月光反射條 opacity。 */
export function moonSeaReflectionOpacity(mix, reduced) {
  const m = Math.max(0, Math.min(1, Number(mix) || 0));
  if (m < 0.25) return 0;
  const base = m * 0.88;
  if (reduced) return base;
  return base;
}

/** 本島岸邊泡沫 alpha 加成。 */
export function mainIslandFoamAlpha(cycleAlpha, mix, reduced) {
  const m = Math.max(0, Math.min(1, Number(mix) || 0));
  const boost = 1 + m * 0.08;
  const nightDim = (1 - m * 0.4) * (1 - m * 0.28);
  if (reduced) return mainIslandFoamBaseOpacity();
  return cycleAlpha * nightDim * boost;
}

export function mainIslandFoamBaseOpacity() {
  return 0.42;
}

export function countPointLightsInRoot(root) {
  let n = 0;
  if (!root) return 0;
  const stack = [root];
  while (stack.length) {
    const obj = stack.pop();
    if (obj && obj.isPointLight) n += 1;
    const kids = obj && obj.children;
    if (kids) {
      for (let i = 0; i < kids.length; i++) stack.push(kids[i]);
    }
  }
  return n;
}

/** 塗鴉牆／施工牌座基與邊框（位置不變）。 */
export function polishPedestalBase(addBox, parent, w, d) {
  addBox(parent, w + 0.2, 0.18, d + 0.14, 0xc4844a, 0, G + 0.09, 0);
  addBox(parent, w + 0.06, 0.055, d + 0.08, 0xf3e2cc, 0, G + 0.205, 0, { shadow: false });
}