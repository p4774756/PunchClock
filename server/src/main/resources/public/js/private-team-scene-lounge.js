import * as THREE from './vendor/three.module.min.js';
import {
  box,
  cyl,
  loadRepeatTex,
  COL,
  roundedRectShape,
  extrudedRoundedPlate
} from './private-team-scene-room.js';
import { privateTeamSeededRand } from './private-team-scene-window.js';
import { isLoungeTigerStatus, isWorkingTigerStatus, parseSecretaryOutfitId } from './private-team-scene-secretary.js';

export const SECRETARY_TUB_POSITION = { x: 0.02, y: 0, z: 0.56 };
export const SECRETARY_SOFA_POSITION = { x: 1.38, y: 0, z: -0.3 };
export const SECRETARY_SHADOW_TUB = { x: 0.06, y: 0.072, z: -0.42 };
/** 沙發姿勢的腳下陰影：相對沙發錨點的「世界」偏移（不隨立繪縮放／偏移移動）。 */
export const SECRETARY_SHADOW_SOFA = { x: -0.03, y: 0.072, z: 0.16 };
export const SECRETARY_SOFA_ROTATION_Y = -0.42;
export const SECRETARY_BUBBLE_TUB = { x: 0.56, z: 0.72 };
export const SECRETARY_BUBBLE_SOFA = {
  x: SECRETARY_SOFA_POSITION.x + 0.12,
  z: SECRETARY_SOFA_POSITION.z + 0.5
};

/** 供單測推算沙發左緣與浴缸空隙（浴缸右緣約 x = 0.84） */
/** 世界座標下的沙發左右縮放（避免加寬後碰到浴缸或右牆） */
export const SOFA_WORLD_SCALE_X = 1.05;
export const SOFA_HALF_WIDTH_X = 0.36;
export const SOFA_ARM_RADIUS = 0.06;
/** 沙發 Z 方向縮放與前後半深（含前緣滾邊），供淨空檢查。 */
export const SOFA_WORLD_SCALE_Z = 1.12;
export const SOFA_HALF_DEPTH_Z = 0.27;

/**
 * 休憩區家具佔地（世界座標，XZ 平面）。浴缸數字對應 private-team-scene.js 的
 * TUB_CX／TUB_CZ／TUB_HW／TUB_HD 再加外緣滾邊與地板收邊；單測會對照原始碼避免漂移。
 */
export const LOUNGE_TUB_FOOTPRINT = { cx: 0, cz: 0.64, hw: 0.88, hd: 0.56 };
/** 地板外緣（FLOOR_HALF = 1.8）。家具要留在裡面。 */
export const LOUNGE_FLOOR_HALF = 1.8;
/** 地毯：比沙發略大，且左緣要留在浴缸右緣外面。 */
export const LOUNGE_RUG = { cx: 1.38, cz: -0.2, w: 0.84, d: 0.9 };
/** 沙發旁落地燈，相對沙發錨點。 */
export const LOUNGE_FLOOR_LAMP_OFFSET = { x: -0.52, z: -0.52 };
export const LOUNGE_FLOOR_LAMP_RADIUS = 0.17;
/** 龜背芋盆栽放在右後角（原本卡在浴缸與沙發之間的窄縫）。 */
export const LOUNGE_MONSTERA_POSITION = { x: 1.62, z: -1.38 };
export const LOUNGE_MONSTERA_RADIUS = 0.16;
/** 後窗胡桃木矮櫃（對應 addLoungeFurnishings 的 sbX／sbZ／sbHalfW）。 */
export const LOUNGE_SIDEBOARD_FOOTPRINT = { cx: 0.86, cz: -1.42, hw: 0.47, hd: 0.26 };

/** 沙發矩形佔地（隨沙發錨點）。 */
export function loungeSofaFootprint() {
  const hw = SOFA_HALF_WIDTH_X * SOFA_WORLD_SCALE_X;
  return {
    cx: SECRETARY_SOFA_POSITION.x,
    cz: SECRETARY_SOFA_POSITION.z,
    hw,
    hd: SOFA_HALF_DEPTH_Z
  };
}

/**
 * 秘書在浴缸與沙發之間移動時走的路線（世界座標）：從浴缸右緣外側起步，
 * 沿著前方的空地走到沙發座墊前。目前場景是直接換位置，不播放走路動畫；
 * 路線留作淨空基準，之後有走路動畫就照這幾點走。
 */
export const SECRETARY_LOUNGE_ROUTE = [
  { name: 'tubSide', x: 1.12, z: 0.74 },
  { name: 'passage', x: 1.14, z: 0.42 },
  { name: 'sofaFront', x: 1.34, z: 0.16 }
];
/** 路線上的身體半徑（約 1 個人的肩寬一半）。 */
export const SECRETARY_ROUTE_BODY_RADIUS = 0.12;

/** 點到軸對齊矩形的最短距離（在矩形內為 0）。 */
export function distancePointToRect(px, pz, rect) {
  const dx = Math.max(Math.abs(px - rect.cx) - rect.hw, 0);
  const dz = Math.max(Math.abs(pz - rect.cz) - rect.hd, 0);
  return Math.hypot(dx, dz);
}

/** 兩個軸對齊矩形之間的空隙（重疊回傳 0）。 */
export function rectGap(a, b) {
  const dx = Math.max(Math.abs(a.cx - b.cx) - (a.hw + b.hw), 0);
  const dz = Math.max(Math.abs(a.cz - b.cz) - (a.hd + b.hd), 0);
  return Math.hypot(dx, dz);
}

/** 路線要避開的實體家具（地毯是貼地的，不算障礙）。 */
export function loungeObstacleFootprints() {
  const lamp = {
    cx: SECRETARY_SOFA_POSITION.x + LOUNGE_FLOOR_LAMP_OFFSET.x,
    cz: SECRETARY_SOFA_POSITION.z + LOUNGE_FLOOR_LAMP_OFFSET.z,
    hw: LOUNGE_FLOOR_LAMP_RADIUS,
    hd: LOUNGE_FLOOR_LAMP_RADIUS
  };
  const plant = {
    cx: LOUNGE_MONSTERA_POSITION.x,
    cz: LOUNGE_MONSTERA_POSITION.z,
    hw: LOUNGE_MONSTERA_RADIUS,
    hd: LOUNGE_MONSTERA_RADIUS
  };
  return {
    tub: LOUNGE_TUB_FOOTPRINT,
    sofa: loungeSofaFootprint(),
    lamp,
    plant,
    sideboard: LOUNGE_SIDEBOARD_FOOTPRINT
  };
}

/**
 * 舞台服沙發立繪是去背坐姿（沒有畫上沙發），需縮小並相對
 * SECRETARY_SOFA_POSITION（與 3D 皮沙發同一錨點）偏移，讓坐線對上座墊。
 * 只用相對偏移，之後沙發錨點若移動，舞台服會跟著走。
 */
export const SECRETARY_STAGE_SOFA_SCALE = 0.82;
export const SECRETARY_STAGE_SOFA_OFFSET = { x: 0.02, y: -0.16, z: 0.22 };
export const SECRETARY_STAGE_SOFA_BUBBLE_OFFSET = { x: 0.12, z: 0.22 };
export const SECRETARY_STAGE_SOFA_BUBBLE_Y = 1.16;

/**
 * 預設服沙發立繪也已去背（只留人物、不含畫上去的沙發），與舞台服一樣縮小後
 * 相對沙發錨點偏移，讓臀部坐在 3D 座墊上。
 */
export const SECRETARY_DEFAULT_SOFA_SCALE = 0.68;
export const SECRETARY_DEFAULT_SOFA_OFFSET = { x: 0.05, y: -0.1, z: 0.18 };

function finiteNumber(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

/** 最保守的回退：錨點本身、不縮放。 */
function anchorOnlySofaPlacement() {
  const base = SECRETARY_SOFA_POSITION;
  return {
    position: {
      x: finiteNumber(base.x, 1.38),
      y: finiteNumber(base.y, 0),
      z: finiteNumber(base.z, -0.3)
    },
    scale: 1,
    bubble: {
      x: finiteNumber(SECRETARY_BUBBLE_SOFA.x, 1.5),
      z: finiteNumber(SECRETARY_BUBBLE_SOFA.z, 0.2),
      y: null
    }
  };
}

/** 依偏移與縮放算出擺位；任何數值非有限就回傳 null。 */
function sofaPlacementFromOffset(offset, rawScale) {
  const anchor = anchorOnlySofaPlacement().position;
  const scale = finiteNumber(rawScale, 0);
  if (!(scale > 0)) return null;
  const pos = {
    x: finiteNumber(anchor.x + Number(offset.x), NaN),
    y: finiteNumber(anchor.y + Number(offset.y), NaN),
    z: finiteNumber(anchor.z + Number(offset.z), NaN)
  };
  if (![pos.x, pos.y, pos.z].every(Number.isFinite)) return null;
  const bubble = {
    x: finiteNumber(anchor.x + Number(SECRETARY_STAGE_SOFA_BUBBLE_OFFSET.x), NaN),
    z: finiteNumber(anchor.z + Number(SECRETARY_STAGE_SOFA_BUBBLE_OFFSET.z), NaN),
    y: finiteNumber(SECRETARY_STAGE_SOFA_BUBBLE_Y, NaN)
  };
  if (![bubble.x, bubble.z, bubble.y].every(Number.isFinite)) return null;
  return { position: pos, scale, bubble };
}

/** 沙發擺位；無效服裝或非有限數字一律回退，避免立繪被移出畫面。 */
export function secretarySofaPlacementForOutfit(outfitId) {
  const isStage = parseSecretaryOutfitId(outfitId) === 'stage';
  const placed = isStage
    ? sofaPlacementFromOffset(SECRETARY_STAGE_SOFA_OFFSET, SECRETARY_STAGE_SOFA_SCALE)
    : sofaPlacementFromOffset(SECRETARY_DEFAULT_SOFA_OFFSET, SECRETARY_DEFAULT_SOFA_SCALE);
  return placed || anchorOnlySofaPlacement();
}

/** 把世界座標的陰影位置換成 secretaryRoot 的局部座標（含縮放與 Y 旋轉）。 */
export function secretarySofaShadowLocal(placement) {
  const base = anchorOnlySofaPlacement().position;
  const px = finiteNumber(placement && placement.position && placement.position.x, base.x);
  const py = finiteNumber(placement && placement.position && placement.position.y, base.y);
  const pz = finiteNumber(placement && placement.position && placement.position.z, base.z);
  const k = finiteNumber(placement && placement.scale, 1) > 0 ? Number(placement.scale) : 1;
  const dx = base.x + SECRETARY_SHADOW_SOFA.x - px;
  const dz = base.z + SECRETARY_SHADOW_SOFA.z - pz;
  const dy = base.y + SECRETARY_SHADOW_SOFA.y - py;
  const c = Math.cos(SECRETARY_SOFA_ROTATION_Y);
  const sn = Math.sin(SECRETARY_SOFA_ROTATION_Y);
  return {
    x: (dx * c - dz * sn) / k,
    y: dy / k,
    z: (dx * sn + dz * c) / k
  };
}

export function secretaryAtLoungePose(status, sofaSpriteOk) {
  void sofaSpriteOk;
  return isLoungeTigerStatus(status);
}

export function vinylShouldSpin(status, reducedMotion) {
  if (reducedMotion) return false;
  return isLoungeTigerStatus(status) && !isWorkingTigerStatus(status);
}

export { isLoungeTigerStatus, isWorkingTigerStatus };

export function makeMarbleFloorTexture(repeatX, repeatY) {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#3a3f48';
  ctx.fillRect(0, 0, 256, 256);
  const rand = privateTeamSeededRand(7541);
  for (let i = 0; i < 18; i++) {
    const gold = i % 4 === 0;
    ctx.strokeStyle = gold ? 'rgba(198, 178, 128, 0.32)' : 'rgba(205, 218, 235, 0.24)';
    ctx.lineWidth = 0.8 + rand() * 2.4;
    ctx.beginPath();
    let x = rand() * 256;
    let y = rand() * 256;
    ctx.moveTo(x, y);
    for (let j = 0; j < 7; j++) {
      x += (rand() - 0.5) * 72;
      y += (rand() - 0.5) * 72;
      ctx.lineTo(x, y);
    }
    ctx.stroke();
  }
  ctx.strokeStyle = 'rgba(32, 34, 38, 0.9)';
  ctx.lineWidth = 2.5;
  const tile = 128;
  for (let i = 1; i < 256 / tile; i++) {
    ctx.beginPath();
    ctx.moveTo(i * tile, 0);
    ctx.lineTo(i * tile, 256);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(0, i * tile);
    ctx.lineTo(256, i * tile);
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(repeatX, repeatY);
  tex.anisotropy = 4;
  return tex;
}

function makeLeatherGrainTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#d9c4b0';
  ctx.fillRect(0, 0, 128, 128);
  const rand = privateTeamSeededRand(8841);
  for (let i = 0; i < 42; i++) {
    ctx.strokeStyle = 'rgba(90, 58, 34, ' + (0.12 + rand() * 0.18) + ')';
    ctx.lineWidth = 0.4 + rand() * 0.8;
    ctx.beginPath();
    let x = rand() * 128;
    let y = rand() * 128;
    ctx.moveTo(x, y);
    for (let j = 0; j < 4; j++) {
      x += (rand() - 0.5) * 28;
      y += (rand() - 0.5) * 10;
      ctx.lineTo(x, y);
    }
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(2.4, 2.4);
  tex.anisotropy = 4;
  return tex;
}

function makeWalnutWoodTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#3d2a1e';
  ctx.fillRect(0, 0, 128, 128);
  const rand = privateTeamSeededRand(4417);
  for (let row = 0; row < 14; row++) {
    const y = row * 9 + rand() * 3;
    const shade = 48 + (row % 3) * 6 + rand() * 8;
    ctx.strokeStyle = 'rgb(' + (shade + 18) + ',' + shade + ',' + (shade - 8) + ')';
    ctx.lineWidth = 1.2 + rand() * 2.2;
    ctx.beginPath();
    ctx.moveTo(0, y);
    for (let x = 0; x < 128; x += 8) {
      ctx.lineTo(x, y + Math.sin(x * 0.09 + row) * 2.5 + (rand() - 0.5) * 2);
    }
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(1.6, 1.2);
  tex.anisotropy = 4;
  return tex;
}

function makeVinylGrooveTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#141414';
  ctx.fillRect(0, 0, 128, 128);
  const cx = 64;
  const cy = 64;
  for (let r = 58; r > 14; r -= 2) {
    ctx.strokeStyle = r % 4 === 0 ? '#0a0a0a' : '#1c1c1c';
    ctx.lineWidth = 0.6;
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

function makeRugColorTexture() {
  const size = 256;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#7c2b24';
  ctx.fillRect(0, 0, size, size);
  const rand = privateTeamSeededRand(6203);
  // 經緯織紋
  for (let y = 0; y < size; y += 3) {
    ctx.fillStyle = y % 6 === 0 ? 'rgba(40, 8, 6, 0.22)' : 'rgba(210, 120, 70, 0.08)';
    ctx.fillRect(0, y, size, 1);
  }
  for (let x = 0; x < size; x += 3) {
    ctx.fillStyle = x % 6 === 0 ? 'rgba(40, 8, 6, 0.16)' : 'rgba(210, 120, 70, 0.06)';
    ctx.fillRect(x, 0, 1, size);
  }
  // 外框條紋：鏽橘＋奶油
  ctx.strokeStyle = '#c4682f';
  ctx.lineWidth = 12;
  ctx.strokeRect(16, 16, size - 32, size - 32);
  ctx.strokeStyle = '#e2c79a';
  ctx.lineWidth = 3;
  ctx.strokeRect(30, 30, size - 60, size - 60);
  // 中央菱形花紋
  const c = size / 2;
  ctx.fillStyle = '#b5532b';
  ctx.beginPath();
  ctx.moveTo(c, c - 70);
  ctx.lineTo(c + 70, c);
  ctx.lineTo(c, c + 70);
  ctx.lineTo(c - 70, c);
  ctx.closePath();
  ctx.fill();
  ctx.strokeStyle = '#e2c79a';
  ctx.lineWidth = 3;
  ctx.stroke();
  ctx.fillStyle = '#4a1612';
  ctx.beginPath();
  ctx.moveTo(c, c - 34);
  ctx.lineTo(c + 34, c);
  ctx.lineTo(c, c + 34);
  ctx.lineTo(c - 34, c);
  ctx.closePath();
  ctx.fill();
  for (let i = 0; i < 90; i++) {
    ctx.fillStyle = 'rgba(30, 6, 4, ' + (0.05 + rand() * 0.08) + ')';
    ctx.fillRect(rand() * size, rand() * size, 2 + rand() * 4, 1);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.anisotropy = 4;
  return tex;
}

function leatherMaterial(baseColor, grainTex) {
  return new THREE.MeshStandardMaterial({
    color: baseColor,
    map: grainTex,
    metalness: 0.04,
    roughness: 0.38,
    emissive: 0x2a1408,
    emissiveIntensity: 0.55
  });
}

function sofaRollArm(radius, length, mat) {
  const geo = new THREE.CylinderGeometry(radius, radius * 1.04, length, 20);
  const arm = new THREE.Mesh(geo, mat);
  arm.rotation.x = Math.PI / 2;
  return arm;
}

function walnutMaterial(grainTex, color = 0xffffff) {
  return new THREE.MeshStandardMaterial({
    color,
    map: grainTex,
    metalness: 0.04,
    roughness: 0.62
  });
}

function extrudedPanel(hw, hh, depth, radius, mat) {
  const shape = roundedRectShape(hw, hh, radius);
  const geo = new THREE.ExtrudeGeometry(shape, {
    depth: depth,
    bevelEnabled: true,
    bevelThickness: 0.008,
    bevelSize: 0.008,
    bevelSegments: 2,
    curveSegments: 8
  });
  geo.center();
  return new THREE.Mesh(geo, mat);
}

function addMonstera(root, x, z) {
  const pot = cyl(0.1, 0.082, 0.12, 10, 0x5a4a38);
  pot.position.set(x, 0.06, z);
  pot.name = 'monsteraPot';
  root.add(pot);
  const leafMat = new THREE.MeshLambertMaterial({ color: 0x2d6b3c });
  const holes = [0, 1.2, 2.4, 3.6, 4.8];
  for (let i = 0; i < holes.length; i++) {
    const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.09, 6, 5), leafMat);
    const a = holes[i];
    leaf.position.set(x + Math.cos(a) * 0.05, 0.2 + (i % 2) * 0.12, z + Math.sin(a) * 0.05);
    leaf.scale.set(1.2, 0.65, 1.05);
    leaf.name = 'monsteraLeaf';
    root.add(leaf);
  }
}

/**
 * 右側休憩區、後窗胡桃木矮櫃與黑膠；回傳唱盤轉動件與音符（供 tick 更新）。
 */
export function addLoungeFurnishings(root, texLoader, layout) {
  const backZ = layout.backZ;
  const winBottom = layout.winBottom;

  const rugTex = loadRepeatTex(texLoader, 'rug-weave.webp', 1.1, 1.35);
  const rugMat = new THREE.MeshLambertMaterial({
    map: makeRugColorTexture(),
    bumpMap: rugTex,
    bumpScale: 1.2,
    color: 0xc9b3a0
  });
  const rug = new THREE.Mesh(new THREE.PlaneGeometry(LOUNGE_RUG.w, LOUNGE_RUG.d), rugMat);
  rug.rotation.x = -Math.PI / 2;
  rug.position.set(LOUNGE_RUG.cx, 0.062, LOUNGE_RUG.cz);
  rug.name = '編織地毯';
  rug.renderOrder = 1;
  root.add(rug);

  const leatherTex = makeLeatherGrainTexture();
  const leatherLight = leatherMaterial(0xb87a48, leatherTex);
  const leatherDark = leatherMaterial(0x93603a, leatherTex);
  const pipingMat = new THREE.MeshStandardMaterial({
    color: 0x3a2212,
    metalness: 0.02,
    roughness: 0.6
  });
  const woodLegMat = new THREE.MeshStandardMaterial({ color: 0x2c1b10, metalness: 0.1, roughness: 0.5 });

  const sofaSx = SECRETARY_SOFA_POSITION.x;
  const sofaSz = SECRETARY_SOFA_POSITION.z;
  const sofaGrp = new THREE.Group();
  sofaGrp.name = '焦糖棕皮沙發';
  sofaGrp.position.set(sofaSx, 0, sofaSz);
  // X 僅微放大以免碰到浴缸或右牆；Y/Z 加高加深，讓預設相機看出更大的沙發。
  sofaGrp.scale.set(SOFA_WORLD_SCALE_X, 1.12, SOFA_WORLD_SCALE_Z);

  // 座框（含底部暗縫，讓三塊坐墊之間看得出分隔）
  const sofaBase = extrudedRoundedPlate(0.3, 0.2, 0.14, 0.04, leatherDark);
  sofaBase.position.set(0, 0.09, 0.02);
  sofaGrp.add(sofaBase);
  const legXs = [-0.3, 0.3];
  const legZs = [-0.17, 0.19];
  for (let lx = 0; lx < legXs.length; lx++) {
    for (let lz = 0; lz < legZs.length; lz++) {
      const leg = cyl(0.022, 0.016, 0.09, 8, 0x2c1b10);
      leg.material = woodLegMat;
      leg.position.set(legXs[lx], 0.045, legZs[lz]);
      sofaGrp.add(leg);
    }
  }

  // 椅背框（比座面高、比扶手高）
  const backFrame = extrudedPanel(0.3, 0.2, 0.1, 0.04, leatherDark);
  backFrame.position.set(0, 0.4, -0.17);
  backFrame.name = 'sofaBackFrame';
  sofaGrp.add(backFrame);

  const cushionXs = [-0.188, 0, 0.188];
  for (let ci = 0; ci < cushionXs.length; ci++) {
    const cx = cushionXs[ci];
    // 座墊
    const cushion = extrudedRoundedPlate(0.088, 0.17, 0.075, 0.034, leatherLight);
    cushion.position.set(cx, 0.235, 0.05);
    cushion.name = 'sofaSeatCushion';
    sofaGrp.add(cushion);
    const seatPipeF = box(0.19, 0.008, 0.008, 0x3a2212);
    seatPipeF.material = pipingMat;
    seatPipeF.position.set(cx, 0.248, 0.226);
    sofaGrp.add(seatPipeF);
    // 靠墊（直立，微向後傾）
    const backCushion = extrudedPanel(0.088, 0.145, 0.07, 0.04, leatherLight);
    backCushion.position.set(cx, 0.465, -0.145);
    backCushion.rotation.x = -0.14;
    backCushion.name = 'sofaBackCushion';
    sofaGrp.add(backCushion);
    const backPipe = box(0.19, 0.012, 0.012, 0x3a2212);
    backPipe.material = pipingMat;
    backPipe.position.set(cx, 0.608, -0.155);
    backPipe.rotation.x = -0.14;
    sofaGrp.add(backPipe);
    // 釦飾（兩顆）
    for (let bi = 0; bi < 2; bi++) {
      const btn = new THREE.Mesh(new THREE.SphereGeometry(0.011, 8, 6), pipingMat);
      btn.position.set(cx + (bi === 0 ? -0.035 : 0.035), 0.5 - bi * 0.0, -0.045);
      sofaGrp.add(btn);
    }
  }

  // 兩側圓弧扶手
  const armRad = SOFA_ARM_RADIUS;
  const armLen = 0.42;
  const armXs = [-(SOFA_HALF_WIDTH_X - armRad), SOFA_HALF_WIDTH_X - armRad];
  for (let ai = 0; ai < armXs.length; ai++) {
    const armBody = box(armRad * 1.7, 0.22, armLen, 0x93603a);
    armBody.material = leatherDark;
    armBody.position.set(armXs[ai] + (ai === 0 ? -0.006 : 0.006), 0.22, 0.01);
    armBody.name = 'sofaArmBody';
    sofaGrp.add(armBody);
    const roll = sofaRollArm(armRad, armLen, leatherLight);
    roll.position.set(armXs[ai], 0.35 + armRad * 0.2, 0.01);
    sofaGrp.add(roll);
    const cap = new THREE.Mesh(new THREE.CircleGeometry(armRad * 0.9, 18), leatherDark);
    cap.position.set(armXs[ai], 0.35 + armRad * 0.2, 0.01 + armLen / 2 + 0.002);
    sofaGrp.add(cap);
  }
  root.add(sofaGrp);

  const sofaLamp = new THREE.Group();
  sofaLamp.name = 'sofaFloorLamp';
  sofaLamp.position.set(
    sofaSx + LOUNGE_FLOOR_LAMP_OFFSET.x,
    0,
    sofaSz + LOUNGE_FLOOR_LAMP_OFFSET.z
  );
  const sfBase = cyl(0.055, 0.062, 0.025, 10, COL.lampBase);
  sfBase.position.set(0, 0.012, 0);
  sofaLamp.add(sfBase);
  const sfPole = cyl(0.014, 0.014, 0.62, 8, COL.lampMetal);
  sfPole.position.set(0, 0.34, 0);
  sofaLamp.add(sfPole);
  const sfShade = cyl(0.14, 0.17, 0.11, 14, COL.lampShade);
  sfShade.position.set(0, 0.66, 0);
  sofaLamp.add(sfShade);
  const sfBulb = new THREE.PointLight(0xffc888, 0.62, 2.4, 2);
  sfBulb.position.set(0, 0.58, 0);
  sfBulb.name = 'sofaFloorLampBulb';
  sofaLamp.add(sfBulb);
  root.add(sofaLamp);

  addMonstera(root, LOUNGE_MONSTERA_POSITION.x, LOUNGE_MONSTERA_POSITION.z);

  const walnutTex = makeWalnutWoodTexture();
  const walnutBodyMat = walnutMaterial(walnutTex, 0xdddddd);
  const walnutTopMat = walnutMaterial(walnutTex, 0xeeeeee);

  const sbScale = 1.5;
  const sbTopY = 0.285;
  const sbBodyH = 0.14;
  const sbX = 0.86;
  const sbZ = backZ + 0.38;
  const sbHalfW = 0.31 * sbScale;

  const sb = new THREE.Group();
  sb.name = 'walnutSideboard';
  sb.position.set(sbX, 0, sbZ);
  const body = extrudedPanel(sbHalfW, sbBodyH, 0.3 * sbScale, 0.04, walnutBodyMat);
  body.rotation.x = -Math.PI / 2;
  body.position.set(0, sbBodyH, 0);
  sb.add(body);
  const top = extrudedRoundedPlate(0.32 * sbScale, 0.17 * sbScale, 0.022, 0.028, walnutTopMat);
  top.position.set(0, sbTopY, 0);
  sb.add(top);
  const doorLineMat = new THREE.MeshStandardMaterial({ color: 0x1e140e, metalness: 0.02, roughness: 0.8 });
  for (const dz of [-0.15, 0.15]) {
    const doorLine = box(0.002, 0.26, 0.38, 0x1e140e);
    doorLine.material = doorLineMat;
    doorLine.position.set(0, sbBodyH, dz);
    sb.add(doorLine);
  }
  const pullMat = new THREE.MeshStandardMaterial({ color: 0xc8b898, metalness: 0.35, roughness: 0.45 });
  for (const dx of [-0.34, 0.34]) {
    const pull = new THREE.Mesh(new THREE.CylinderGeometry(0.007, 0.007, 0.05, 10), pullMat);
    pull.rotation.z = Math.PI / 2;
    pull.position.set(dx, sbBodyH, 0.2);
    sb.add(pull);
  }
  const legMat = new THREE.MeshStandardMaterial({ color: 0x2a1e14, metalness: 0.05, roughness: 0.7 });
  for (const lx of [-0.4, 0.4]) {
    const leg = new THREE.Mesh(new THREE.CylinderGeometry(0.02, 0.022, 0.08, 12), legMat);
    leg.position.set(lx, 0.04, 0.18);
    sb.add(leg);
    const leg2 = leg.clone();
    leg2.position.set(lx, 0.04, -0.18);
    sb.add(leg2);
  }
  root.add(sb);

  const sleeveColors = [0x3a4a6a, 0x6a3a3a, 0x4a5a38, 0x5a4840];
  for (let ri = 0; ri < sleeveColors.length; ri++) {
    const sleeveMat = new THREE.MeshStandardMaterial({
      color: sleeveColors[ri],
      roughness: 0.78,
      metalness: 0.02
    });
    const sleeve = box(0.24, 0.24, 0.005, sleeveColors[ri]);
    sleeve.material = sleeveMat;
    sleeve.rotation.y = -0.35 + ri * 0.08;
    sleeve.position.set(
      sbX + 0.42 + ri * 0.018,
      sbTopY + 0.12 + ri * 0.002,
      sbZ + 0.12 + ri * 0.04
    );
    sleeve.name = 'vinylRecordSleeve';
    root.add(sleeve);
  }

  const turntable = new THREE.Group();
  turntable.name = 'vinylTurntable';
  const ttY = sbTopY + 0.012;
  turntable.position.set(sbX, ttY, sbZ);
  const baseMat = new THREE.MeshStandardMaterial({ color: 0x2a2420, metalness: 0.12, roughness: 0.55 });
  const ttBase = extrudedRoundedPlate(0.21, 0.165, 0.038, 0.028, baseMat);
  ttBase.position.set(0, 0.019, 0);
  turntable.add(ttBase);
  const vinylGrooveTex = makeVinylGrooveTexture();
  const platterR = 0.165;
  const platter = new THREE.Mesh(
    new THREE.CylinderGeometry(platterR, platterR, 0.014, 56),
    new THREE.MeshStandardMaterial({
      color: 0x111111,
      map: vinylGrooveTex,
      metalness: 0.06,
      roughness: 0.42
    })
  );
  platter.position.set(0, 0.055, 0);
  platter.name = 'vinylDisc';
  turntable.add(platter);
  const spindle = new THREE.Mesh(
    new THREE.CylinderGeometry(0.01, 0.012, 0.022, 12),
    new THREE.MeshStandardMaterial({ color: 0x888078, metalness: 0.55, roughness: 0.38 })
  );
  spindle.position.set(0, 0.072, 0);
  platter.add(spindle);
  const label = new THREE.Mesh(
    new THREE.CylinderGeometry(0.042, 0.042, 0.016, 28),
    new THREE.MeshStandardMaterial({ color: 0xc43c3c, roughness: 0.55, metalness: 0.02 })
  );
  label.position.set(0, 0.009, 0);
  platter.add(label);
  const armPivot = new THREE.Mesh(
    new THREE.CylinderGeometry(0.02, 0.022, 0.028, 14),
    new THREE.MeshStandardMaterial({ color: 0x5a544c, metalness: 0.4, roughness: 0.42 })
  );
  armPivot.position.set(0.15, 0.058, -0.09);
  turntable.add(armPivot);
  const armTube = new THREE.Mesh(
    new THREE.CylinderGeometry(0.008, 0.009, 0.18, 12),
    new THREE.MeshStandardMaterial({ color: 0x6a6058, metalness: 0.35, roughness: 0.48 })
  );
  armTube.rotation.x = 0.32;
  armTube.rotation.z = -0.52;
  armTube.position.set(0.158, 0.088, -0.03);
  turntable.add(armTube);
  const headshell = box(0.032, 0.018, 0.042, 0x4a443c);
  headshell.material = new THREE.MeshStandardMaterial({
    color: 0x4a443c,
    metalness: 0.25,
    roughness: 0.5
  });
  headshell.position.set(0.2, 0.078, 0.05);
  turntable.add(headshell);
  const stylus = box(0.006, 0.016, 0.008, 0x2a2824);
  stylus.material = new THREE.MeshStandardMaterial({
    color: 0x2a2824,
    metalness: 0.15,
    roughness: 0.6
  });
  stylus.position.set(0.214, 0.068, 0.058);
  turntable.add(stylus);
  const knobMat = new THREE.MeshStandardMaterial({ color: 0x8a8078, metalness: 0.2, roughness: 0.52 });
  for (const kx of [-0.14, 0.12]) {
    const knob = new THREE.Mesh(new THREE.CylinderGeometry(0.015, 0.015, 0.01, 14), knobMat);
    knob.position.set(kx, 0.044, 0.12);
    turntable.add(knob);
  }
  root.add(turntable);

  const lampGrp = new THREE.Group();
  lampGrp.name = 'loungeLamp';
  lampGrp.position.set(sbX + sbHalfW * 0.55, sbTopY + 0.02, sbZ);
  const lampBase = cyl(0.045, 0.05, 0.04, 8, COL.lampBase);
  lampBase.position.set(0, 0.02, 0);
  lampGrp.add(lampBase);
  const stem = cyl(0.012, 0.012, 0.14, 6, COL.lampMetal);
  stem.position.set(0, 0.1, 0);
  lampGrp.add(stem);
  const shade = cyl(0.09, 0.11, 0.08, 12, COL.lampShade);
  shade.position.set(0, 0.2, 0);
  lampGrp.add(shade);
  const bulb = new THREE.PointLight(0xffd8a8, 0.35, 0.9, 2);
  bulb.position.set(0, 0.16, 0);
  bulb.name = 'loungeLampBulb';
  lampGrp.add(bulb);
  root.add(lampGrp);

  const noteMat = new THREE.MeshBasicMaterial({
    color: 0xe8d8b0,
    transparent: true,
    opacity: 0.42,
    depthWrite: false
  });
  const notes = [];
  for (let ni = 0; ni < 3; ni++) {
    const note = new THREE.Mesh(new THREE.PlaneGeometry(0.035, 0.05), noteMat.clone());
    note.position.set(sbX + ni * 0.07, ttY + 0.14 + ni * 0.05, sbZ + 0.22);
    note.name = 'vinylNote';
    note.visible = false;
    root.add(note);
    notes.push(note);
  }

  return {
    vinylDisc: platter,
    vinylNotes: notes,
    sideboardMaxX: sbX + 0.32 * sbScale,
    handheldClearX: 0.51
  };
}

export function tickVinylSpin(platter, notes, status, reducedMotion, elapsed, dt) {
  const spin = vinylShouldSpin(status, reducedMotion);
  if (platter) {
    platter.rotation.y += spin ? dt * 1.15 : 0;
  }
  const showNotes = spin && !reducedMotion;
  if (notes) {
    for (let i = 0; i < notes.length; i++) {
      const n = notes[i];
      n.visible = showNotes;
      if (showNotes) {
        n.position.y = 0.4 + i * 0.05 + Math.sin(elapsed * 1.6 + i) * 0.025;
        n.material.opacity = 0.28 + ((Math.sin(elapsed * 2.2 + i) + 1) / 2) * 0.18;
      }
    }
  }
}