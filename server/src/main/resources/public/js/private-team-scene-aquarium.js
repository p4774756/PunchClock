import * as THREE from './vendor/three.module.min.js';
import { FLOOR_SIZE, box, makeBathtubCausticsTexture } from './private-team-scene-room.js';

/** 盆緣與地板齊平（地板頂約 0.047），不突出地面 */
export const PRIVATE_TEAM_TUB_RIM_TOP_Y = 0.056;
/** 浴缸外殼下緣與水族箱底對齊 */
export const PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y = -3.1;
/** 水族箱深度（舊版 0.86，加深約 3.6 倍）。 */
export const PRIVATE_TEAM_AQUARIUM_DEPTH = 3.1;
/** 浴缸尚未建立水族箱時的淺坑底（約 0.4 深，仍與地板齊平）。 */
export const PRIVATE_TEAM_TUB_PIT_FLOOR_Y = -0.36;
export const PRIVATE_TEAM_AQUARIUM_TOP_Y = -0.038;
/** 鏡頭尚高但已大角度下拉時也建立水族箱 */
export const PRIVATE_TEAM_AQUARIUM_LAZY_CAMERA_Y = 1.85;
/** 約 18° 俯角即預建，避免底角黑屏 */
export const PRIVATE_TEAM_AQUARIUM_LAZY_PITCH_RAD = -0.32;
/** 驗收俯角（約 45°）用於單測對照 */
export const PRIVATE_TEAM_PITCH_ACCEPTANCE_DOWN_RAD = (45 * Math.PI) / 180;
export const PRIVATE_TEAM_BATH_WATER_SURFACE_OPACITY = 0.48;
export const PRIVATE_TEAM_BATH_WATER_PITCH_CLEAR_OPACITY = 0.22;
/** 下拉超過約 30° 起漸漸減淡水面，露出泳裝腿輪廓 */
export const PRIVATE_TEAM_BATH_WATER_PITCH_PEEK_START_RAD = 0.52;
export const PRIVATE_TEAM_BATH_WATER_PITCH_PEEK_END_RAD = 0.82;
export const PRIVATE_TEAM_TUB_FLOOR_AQUARIUM_OPACITY = 0.06;
export const PRIVATE_TEAM_AQUARIUM_UNDER_GLOW = 1.15;
export const PRIVATE_TEAM_AQUARIUM_GLASS_OPACITY = 0.38;

const SAND = 0xc4a882;
const GLASS = 0xb8d4e4;

export function shouldLazyBuildFloorAquarium(cameraY, pitchRadians) {
  const y = Number(cameraY);
  const p = Number(pitchRadians);
  if (!Number.isFinite(y) || !Number.isFinite(p)) return false;
  if (y < PRIVATE_TEAM_AQUARIUM_LAZY_CAMERA_Y) return true;
  if (p < PRIVATE_TEAM_AQUARIUM_LAZY_PITCH_RAD) return true;
  return false;
}

export function privateTeamBathWaterSurfaceOpacity(pitchRadians) {
  const down = Math.max(0, -Number(pitchRadians));
  if (!Number.isFinite(down)) return PRIVATE_TEAM_BATH_WATER_SURFACE_OPACITY;
  if (down <= PRIVATE_TEAM_BATH_WATER_PITCH_PEEK_START_RAD) {
    return PRIVATE_TEAM_BATH_WATER_SURFACE_OPACITY;
  }
  const span = PRIVATE_TEAM_BATH_WATER_PITCH_PEEK_END_RAD - PRIVATE_TEAM_BATH_WATER_PITCH_PEEK_START_RAD;
  const t = span > 1e-6 ? Math.min(1, (down - PRIVATE_TEAM_BATH_WATER_PITCH_PEEK_START_RAD) / span) : 1;
  return (
    PRIVATE_TEAM_BATH_WATER_SURFACE_OPACITY -
    t * (PRIVATE_TEAM_BATH_WATER_SURFACE_OPACITY - PRIVATE_TEAM_BATH_WATER_PITCH_CLEAR_OPACITY)
  );
}

/** 浴缸底與地板下同一池水：打通自底觀看的穿透 */
export function applyTubFloorAquariumPeek(tubFloorMesh) {
  if (!tubFloorMesh || !tubFloorMesh.material) return;
  tubFloorMesh.material.transparent = true;
  tubFloorMesh.material.opacity = PRIVATE_TEAM_TUB_FLOOR_AQUARIUM_OPACITY;
  tubFloorMesh.material.depthWrite = false;
}

/* ---------- 魚群（InstancedMesh：身體／尾鰭／背鰭各 1 個 draw call） ---------- */
export const PRIVATE_TEAM_FISH_SCHOOLS = [
  { count: 6, color: 0xff8a2a, size: 1, y: -0.95, cx: -0.3, cz: 0.1, ax: 1.0, az: 0.7, w: 0.42, spread: 0.2, phase: 0.4 },
  { count: 5, color: 0x3aa8ff, size: 0.9, y: -1.75, cx: 0.3, cz: 0.0, ax: 1.1, az: 0.8, w: 0.36, spread: 0.22, phase: 2.2 },
  { count: 4, color: 0xffd814, size: 0.75, y: -2.4, cx: 0.0, cz: 0.2, ax: 1.2, az: 0.6, w: 0.5, spread: 0.18, phase: 4.1 },
  { count: 1, color: 0xff4a3a, size: 1.7, y: -1.3, cx: 0.1, cz: -0.1, ax: 0.8, az: 0.9, w: 0.26, spread: 0, phase: 1.3 },
  { count: 1, color: 0xf2f2f8, size: 1.5, y: -2.05, cx: -0.2, cz: 0.1, ax: 0.9, az: 0.8, w: 0.3, spread: 0, phase: 5.4 }
];
const FISH_L = 0.12;
const FISH_H = 0.056;
const FISH_W = 0.034;
const _fm = new THREE.Matrix4();
const _fb = new THREE.Matrix4();
const _fq = new THREE.Quaternion();
const _fe = new THREE.Euler();
const _fp = new THREE.Vector3();
const _fs = new THREE.Vector3();
const _fc = new THREE.Color();

export function buildFish(group) {
  const fishGroup = new THREE.Group();
  fishGroup.name = 'aquariumFish';
  const total = PRIVATE_TEAM_FISH_SCHOOLS.reduce((n, s) => n + s.count, 0);
  const bodyGeo = new THREE.SphereGeometry(1, 10, 7);
  const tailGeo = new THREE.ConeGeometry(1, 1, 3, 1);
  tailGeo.rotateZ(-Math.PI / 2);
  tailGeo.translate(-0.5, 0, 0);
  const finGeo = new THREE.ConeGeometry(1, 1, 3, 1);
  finGeo.translate(0, 0.5, 0);
  const mat = () => new THREE.MeshBasicMaterial({ color: 0xffffff, side: THREE.DoubleSide, toneMapped: false });
  const body = new THREE.InstancedMesh(bodyGeo, mat(), total);
  const tail = new THREE.InstancedMesh(tailGeo, mat(), total);
  const fin = new THREE.InstancedMesh(finGeo, mat(), total);
  body.name = 'aquariumFishBody';
  tail.name = 'aquariumFishTail';
  fin.name = 'aquariumFishFin';
  const members = [];
  let idx = 0;
  for (let si = 0; si < PRIVATE_TEAM_FISH_SCHOOLS.length; si++) {
    const s = PRIVATE_TEAM_FISH_SCHOOLS[si];
    for (let k = 0; k < s.count; k++) {
      const r1 = ((si * 31 + k * 17) % 13) / 13 - 0.5;
      const r2 = ((si * 19 + k * 23) % 11) / 11 - 0.5;
      const r3 = ((si * 7 + k * 29) % 17) / 17 - 0.5;
      members.push({ si, off: [r1 * 2 * s.spread, r2 * s.spread, r3 * 2 * s.spread], phase: (k * 1.7 + si) % 6.28 });
      _fc.set(s.color);
      body.setColorAt(idx, _fc);
      _fc.multiplyScalar(0.9);
      tail.setColorAt(idx, _fc);
      fin.setColorAt(idx, _fc);
      idx++;
    }
  }
  [body, tail, fin].forEach((m) => {
    m.frustumCulled = false;
    m.instanceColor.needsUpdate = true;
    fishGroup.add(m);
  });
  group.add(fishGroup);
  return { group: fishGroup, body, tail, fin, members };
}

export function updateFish(fish, t) {
  if (!fish) return;
  for (let i = 0; i < fish.members.length; i++) {
    const m = fish.members[i];
    const s = PRIVATE_TEAM_FISH_SCHOOLS[m.si];
    const a = t * s.w + s.phase;
    const b = t * s.w * 0.77 + s.phase * 1.3;
    const lx = s.cx + s.ax * Math.sin(a);
    const lz = 0.04 + s.cz + s.az * Math.sin(b);
    const vx = s.ax * s.w * Math.cos(a);
    const vz = s.az * s.w * 0.77 * Math.cos(b);
    const yaw = Math.atan2(-vz, vx) + Math.sin(t * 0.9 + m.phase) * 0.12;
    const breathe = 1 + 0.18 * Math.sin(t * 0.6 + m.phase);
    _fp.set(
      lx + m.off[0] * breathe,
      s.y + m.off[1] + Math.sin(t * 0.8 + m.phase) * 0.05,
      lz + m.off[2] * breathe
    );
    _fe.set(0, yaw, Math.sin(t * 1.3 + m.phase) * 0.05);
    _fq.setFromEuler(_fe);
    _fm.compose(_fp, _fq, _fs.set(1, 1, 1));
    const z = s.size;
    _fb.makeScale(FISH_L * z, FISH_H * z, FISH_W * z);
    fish.body.setMatrixAt(i, _fb.premultiply(_fm));
    const wag = Math.sin(t * 8 + m.phase * 3) * 0.5;
    _fe.set(0, wag, 0);
    _fq.setFromEuler(_fe);
    _fb.compose(_fp.set(-FISH_L * z * 0.85, 0, 0), _fq, _fs.set(FISH_L * z * 0.9, FISH_H * z * 1.3, FISH_W * z * 0.5));
    fish.tail.setMatrixAt(i, _fb.premultiply(_fm));
    _fb.makeScale(FISH_L * z * 0.55, FISH_H * z * 0.75, FISH_W * z * 0.35).setPosition(-FISH_L * z * 0.1, FISH_H * z * 0.8, 0);
    fish.fin.setMatrixAt(i, _fb.premultiply(_fm));
  }
  fish.body.instanceMatrix.needsUpdate = true;
  fish.tail.instanceMatrix.needsUpdate = true;
  fish.fin.instanceMatrix.needsUpdate = true;
}

/* ---------- 水草（錐形葉片，頂點隨時間輕輕擺動） ---------- */
export const PRIVATE_TEAM_SEAWEED_CLUMPS = [
  [-1.45, 0.9], [-1.2, -0.8], [-0.6, 1.15], [0.5, 1.2], [1.3, 0.9],
  [1.45, -0.3], [1.0, -1.1], [-0.1, -1.15], [-1.5, 0.1]
];

export function buildSeaweed(group, tankBottom) {
  const mats = [
    new THREE.MeshLambertMaterial({ color: 0x2e8a52, emissive: 0x145a34, emissiveIntensity: 0.4, side: THREE.DoubleSide }),
    new THREE.MeshLambertMaterial({ color: 0x4aa84a, emissive: 0x1c5a22, emissiveIntensity: 0.4, side: THREE.DoubleSide })
  ];
  const blades = [];
  for (let ci = 0; ci < PRIVATE_TEAM_SEAWEED_CLUMPS.length; ci++) {
    const [gx, gz] = PRIVATE_TEAM_SEAWEED_CLUMPS[ci];
    for (let bi = 0; bi < 3; bi++) {
      const h = 0.7 + (((ci * 37 + bi * 17) % 10) / 10) * 1.2;
      const w = 0.07 + (bi % 2) * 0.02;
      const geo = new THREE.PlaneGeometry(w, h, 1, 7);
      geo.translate(0, h / 2, 0);
      const pos = geo.attributes.position;
      for (let vi = 0; vi < pos.count; vi++) {
        pos.setX(vi, pos.getX(vi) * (1 - 0.6 * (pos.getY(vi) / h)));
      }
      const mesh = new THREE.Mesh(geo, mats[(ci + bi) % 2]);
      mesh.position.set(gx + (bi - 1) * 0.07, tankBottom + 0.02, 0.04 + gz + ((bi * 5) % 3 - 1) * 0.04);
      mesh.rotation.y = ci * 0.9 + bi * 1.7;
      mesh.name = 'aquariumSeagrass' + blades.length;
      mesh.userData.sway = {
        base: Float32Array.from(pos.array),
        h,
        phase: ci * 0.8 + bi * 1.9,
        amp: 0.1 + (bi % 3) * 0.03
      };
      group.add(mesh);
      blades.push(mesh);
    }
  }
  return blades;
}

export function swaySeaweed(blades, t) {
  if (!blades) return;
  for (let bi = 0; bi < blades.length; bi++) {
    const mesh = blades[bi];
    const { base, h, phase, amp } = mesh.userData.sway;
    const pos = mesh.geometry.attributes.position;
    for (let vi = 0; vi < pos.count; vi++) {
      const y = base[vi * 3 + 1];
      const k = (y / h) * (y / h);
      pos.setX(vi, base[vi * 3] + Math.sin(t * 1.3 + phase + y * 1.9) * amp * k);
      pos.setZ(vi, Math.cos(t * 0.9 + phase + y * 1.5) * amp * 0.5 * k);
    }
    pos.needsUpdate = true;
  }
}

export function buildFloorAquarium(root, waterY) {
  const group = new THREE.Group();
  group.name = 'floorAquarium';

  const innerW = FLOOR_SIZE - 0.22;
  const innerD = FLOOR_SIZE - 0.22;
  const tankBottom = PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y + 0.02;
  const tankH = PRIVATE_TEAM_AQUARIUM_TOP_Y - tankBottom;

  const sand = new THREE.Mesh(
    new THREE.PlaneGeometry(innerW - 0.08, innerD - 0.08),
    new THREE.MeshBasicMaterial({ color: SAND })
  );
  sand.rotation.x = -Math.PI / 2;
  sand.position.set(0, tankBottom + 0.012, 0.04);
  sand.name = 'aquariumSand';
  group.add(sand);

  const waterGradCanvas = document.createElement('canvas');
  waterGradCanvas.width = 4;
  waterGradCanvas.height = 128;
  const wCtx = waterGradCanvas.getContext('2d');
  const grad = wCtx.createLinearGradient(0, 0, 0, 128);
  grad.addColorStop(0, '#3a9aaa');
  grad.addColorStop(1, '#0e4a62');
  wCtx.fillStyle = grad;
  wCtx.fillRect(0, 0, 4, 128);
  const waterGradTex = new THREE.CanvasTexture(waterGradCanvas);
  waterGradTex.colorSpace = THREE.SRGBColorSpace;

  const wy = Number(waterY);
  const waterTop = Number.isFinite(wy) ? wy : 0.468;
  const waterFillH = waterTop - tankBottom;
  const waterVol = new THREE.Mesh(
    new THREE.BoxGeometry(innerW - 0.12, waterFillH, innerD - 0.12),
    new THREE.MeshLambertMaterial({
      color: 0x2a7a8a,
      map: waterGradTex,
      transparent: true,
      opacity: 0.42,
      depthWrite: false
    })
  );
  waterVol.position.set(0, tankBottom + waterFillH / 2, 0.04);
  waterVol.name = 'aquariumWaterVolume';
  group.add(waterVol);

  const glassMat = () =>
    new THREE.MeshLambertMaterial({
      color: GLASS,
      emissive: 0x3a7088,
      emissiveIntensity: 0.42,
      transparent: true,
      opacity: PRIVATE_TEAM_AQUARIUM_GLASS_OPACITY,
      depthWrite: false,
      side: THREE.DoubleSide
    });
  const wallThick = 0.018;
  const wallH = tankH + 0.04;
  const wallMidY = tankBottom + wallH / 2;
  const frontWall = box(innerW, wallH, wallThick, GLASS);
  frontWall.material = glassMat();
  frontWall.position.set(0, wallMidY, 0.04 + innerD / 2 - wallThick / 2);
  frontWall.name = 'aquariumGlassFront';
  frontWall.renderOrder = 3;
  group.add(frontWall);
  const backWall = frontWall.clone();
  backWall.position.z = 0.04 - innerD / 2 + wallThick / 2;
  backWall.name = 'aquariumGlassBack';
  group.add(backWall);
  const sideW = innerD - wallThick * 2;
  const leftWall = box(wallThick, wallH, sideW, GLASS);
  leftWall.material = glassMat();
  leftWall.position.set(-innerW / 2 + wallThick / 2, wallMidY, 0.04);
  leftWall.name = 'aquariumGlassLeft';
  group.add(leftWall);
  const rightWall = leftWall.clone();
  rightWall.position.x = innerW / 2 - wallThick / 2;
  rightWall.name = 'aquariumGlassRight';
  group.add(rightWall);

  const decorMat = new THREE.MeshLambertMaterial({
    color: 0x3d8a7a,
    emissive: 0x1a5048,
    emissiveIntensity: 0.35
  });
  const coralGeos = [];
  const coralOffsets = [
    [-0.95, -0.42, 0.78],
    [0.62, -0.55, -0.48],
    [-0.35, -0.62, -0.82],
    [1.05, -0.48, 0.35]
  ];
  for (let ci = 0; ci < coralOffsets.length; ci++) {
    const [cx, by, cz] = coralOffsets[ci];
    const cone = new THREE.Mesh(new THREE.ConeGeometry(0.06 + (ci % 2) * 0.03, 0.14 + ci * 0.02, 5), decorMat);
    cone.position.set(cx, tankBottom + 0.07 + by * 0.02, 0.04 + cz);
    cone.name = 'aquariumCoral' + ci;
    group.add(cone);
    coralGeos.push(cone);
  }
  const seagrass = buildSeaweed(group, tankBottom);

  const causticsTex = makeBathtubCausticsTexture();
  const causticsMat = new THREE.MeshBasicMaterial({
    map: causticsTex,
    color: 0x90d8ff,
    transparent: true,
    opacity: 0.36,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const causticsPlane = new THREE.Mesh(
    new THREE.PlaneGeometry(innerW - 0.2, innerD - 0.2),
    causticsMat
  );
  causticsPlane.rotation.x = -Math.PI / 2;
  causticsPlane.position.set(0, tankBottom + 0.018, 0.04);
  causticsPlane.name = 'aquariumCaustics';
  causticsPlane.renderOrder = 5;
  group.add(causticsPlane);

  const underHemi = new THREE.HemisphereLight(0xb8ecff, 0x2a4a58, 0.92);
  underHemi.position.set(0, tankBottom + 0.5, 0.04);
  underHemi.name = 'aquariumUnderHemi';
  group.add(underHemi);

  const aquaGlow = new THREE.PointLight(0x6ab8e0, 0.88, 5.5, 1.6);
  aquaGlow.position.set(0, tankBottom + 0.35, 0.04);
  aquaGlow.name = 'aquariumGlow';
  group.add(aquaGlow);

  const midGlow = new THREE.PointLight(0x58b0d8, 0.9, 4.2, 1.3);
  midGlow.position.set(0, -1.2, 0.04);
  midGlow.name = 'aquariumMidGlow';
  group.add(midGlow);

  const underGlow = new THREE.PointLight(0x48a0d8, PRIVATE_TEAM_AQUARIUM_UNDER_GLOW, 5.8, 1.4);
  underGlow.position.set(0, tankBottom + 0.08, 0.04);
  underGlow.name = 'aquariumUnderGlow';
  group.add(underGlow);

  waterVol.renderOrder = 2;
  sand.renderOrder = 1;

  const fish = buildFish(group);
  updateFish(fish, 0);

  root.add(group);
  return {
    group,
    causticsTex,
    causticsMat,
    fish,
    seagrass,
    aquaGlow,
    underGlow,
    underHemi,
    tankBottom
  };
}

export function tickFloorAquarium(state, elapsed, reducedMotion) {
  if (!state) return;
  if (!reducedMotion && state.causticsTex) {
    state.causticsTex.offset.x = (elapsed * 0.022) % 1;
    state.causticsTex.offset.y = (elapsed * 0.018) % 1;
    if (state.causticsMat) {
      state.causticsMat.opacity = 0.28 + ((Math.sin(elapsed * 1.1) + 1) / 2) * 0.14;
    }
  }
  if (reducedMotion) return;
  updateFish(state.fish, elapsed);
  swaySeaweed(state.seagrass, elapsed);
}

/**
 * 3D 泡澡腿會穿過桌板，並在下方玻璃水族箱露出鬼影腿（#907），預設關閉；
 * 精靈圖腰以下交給水下淡出處理。
 */
export const PRIVATE_TEAM_BATH_LEGS_VISIBLE = false;

/** 腿底停在水族箱沙面上方（沙在 tankBottom≈-3.08，腿在水中段懸空）。 */
export const PRIVATE_TEAM_BATH_LEG_BOTTOM_Y = -1.05;
/** 精靈圖有內容的底緣（腰臀／大腿裁切處）離圖片底邊的比例高度（占 SECRETARY_SPRITE_HEIGHT）。 */
export const PRIVATE_TEAM_BATH_HIP_FROM_SPRITE_BOTTOM = 0.1;
/** 兩條腿中心相對精靈中線的水平間距（精靈本地 x）。 */
export const PRIVATE_TEAM_BATH_LEG_SPREAD = 0.2;
const SKIN = 0xf3cdb8;

/** 腿從髖部往下的總長（世界 y 往下，下限避免鏡頭貼近時縮成一點）。 */
export function secretaryBathLegLength(hipY) {
  const y = Number(hipY);
  if (!Number.isFinite(y)) return 0.7;
  return Math.max(0.35, y - PRIVATE_TEAM_BATH_LEG_BOTTOM_Y);
}

/**
 * 泡澡時的 3D 腿：髖部釘在精靈圖底緣（隨 lookAt 轉動），腿垂直往下伸進浴缸水與地板下水族箱。
 * 每幀用 syncSecretaryBathLegs 對齊，任何鏡頭角度（含俯視）都不會與精靈圖脫節。
 */
export function addSecretaryBathLegs(parent) {
  if (parent.getObjectByName('secretaryBathLegs')) return;
  const group = new THREE.Group();
  group.name = 'secretaryBathLegs';
  const skinMat = new THREE.MeshLambertMaterial({
    color: SKIN,
    emissive: 0xa86c56,
    emissiveIntensity: 0.62
  });
  const pelvis = new THREE.Mesh(new THREE.BoxGeometry(0.78, 0.2, 0.05), skinMat);
  pelvis.name = 'bathLegPelvis';
  group.add(pelvis);
  function leg(side) {
    const thigh = new THREE.Mesh(new THREE.CylinderGeometry(0.1, 0.075, 1, 10), skinMat);
    thigh.name = 'bathLegThigh' + side;
    const shin = new THREE.Mesh(new THREE.CylinderGeometry(0.07, 0.05, 1, 10), skinMat);
    shin.name = 'bathLegShin' + side;
    // 圓潤的腳掌（拉長的橢球）＋膝蓋關節球，從側面看不再是灰色方塊。
    const foot = new THREE.Mesh(new THREE.SphereGeometry(0.06, 14, 10), skinMat);
    foot.scale.set(0.9, 0.5, 1.9);
    foot.name = 'bathLegFoot' + side;
    const knee = new THREE.Mesh(new THREE.SphereGeometry(0.078, 14, 10), skinMat);
    knee.name = 'bathLegKnee' + side;
    return [thigh, shin, foot, knee];
  }
  group.add(...leg('L'), ...leg('R'));
  for (let ci = 0; ci < group.children.length; ci++) {
    group.children[ci].renderOrder = 0;
    group.children[ci].frustumCulled = false;
  }
  parent.add(group);
}

const _hipLocal = new THREE.Vector3();
const _off = new THREE.Vector3();
const _a = new THREE.Vector3();
const _b = new THREE.Vector3();
const _dir = new THREE.Vector3();
const _up = new THREE.Vector3(0, 1, 0);

/** 膝蓋往浴缸前方（+z）彎出的距離，讓俯視時小腿不被正上方的鍵盤托盤擋住。 */
export const PRIVATE_TEAM_BATH_KNEE_FORWARD = 0.52;
const TUB_WORLD_Z_MAX = 0.98;
const TUB_WORLD_Z_MIN = 0.5;
const TUB_WORLD_X_MAX = 0.68;

function clamp(v, lo, hi) {
  return Math.max(lo, Math.min(hi, v));
}

/** 把單位高度圓柱（粗端在 +y）拉到 a→b，粗端落在 a。 */
function placeLimb(mesh, a, b) {
  if (!mesh) return;
  _dir.subVectors(a, b);
  const len = Math.max(0.001, _dir.length());
  mesh.position.addVectors(a, b).multiplyScalar(0.5);
  mesh.scale.set(1, len, 1);
  mesh.quaternion.setFromUnitVectors(_up, _dir.multiplyScalar(1 / len));
}

/**
 * 把腿的髖部對到精靈圖底緣中心：大腿由髖部往前彎到膝蓋，小腿再垂直插進水族箱。
 * 全部以秘書根節點（泡澡姿勢下未旋轉、未縮放）的本地座標擺放，group 本身固定在原點。
 * @param parent 秘書根節點
 * @param sprite 已 lookAt 鏡頭的精靈 Mesh
 * @param spriteHeight 精靈圖平面高度
 */
export function syncSecretaryBathLegs(parent, sprite, spriteHeight) {
  if (!parent || !sprite) return;
  const group = parent.getObjectByName('secretaryBathLegs');
  if (!group || !group.visible) return;
  parent.updateMatrixWorld(true);
  const hipLocalY = -spriteHeight / 2 + spriteHeight * PRIVATE_TEAM_BATH_HIP_FROM_SPRITE_BOTTOM;
  _hipLocal.set(-0.04, hipLocalY, 0);
  sprite.localToWorld(_hipLocal);
  parent.worldToLocal(_hipLocal);
  group.position.set(0, 0, 0);
  const px = parent.position.x;
  const pz = parent.position.z;
  const py = parent.position.y;
  // 根節點可能被縮放（泡澡比例 < 1）：世界偏移要除以縮放才是本地座標。
  const rs = parent.scale && parent.scale.y > 0 ? parent.scale.y : 1;

  const pelvis = group.getObjectByName('bathLegPelvis');
  if (pelvis) {
    pelvis.quaternion.copy(sprite.quaternion);
    // 骨盆板稍微塞進精靈圖後方：精靈內容蓋在上面，只在精靈邊緣鏤空處露出，補掉腰臀破洞。
    pelvis.position.set(0, 0.03, -0.04).applyQuaternion(sprite.quaternion).add(_hipLocal);
  }
  const sides = ['L', 'R'];
  for (let si = 0; si < sides.length; si++) {
    const side = sides[si];
    const sign = side === 'L' ? -1 : 1;
    // 沿精靈本地 x（隨鏡頭轉向）攤開，腿身仍在世界垂直方向。
    _off.set(sign * PRIVATE_TEAM_BATH_LEG_SPREAD, -0.04, 0).applyQuaternion(sprite.quaternion);
    _a.copy(_hipLocal).add(_off);
    const kneeWx = clamp(_a.x * rs + px, -TUB_WORLD_X_MAX, TUB_WORLD_X_MAX);
    const kneeWz = clamp(_a.z * rs + pz + PRIVATE_TEAM_BATH_KNEE_FORWARD, TUB_WORLD_Z_MIN, TUB_WORLD_Z_MAX);
    _b.set((kneeWx - px) / rs, _a.y - 0.08, (kneeWz - pz) / rs);
    placeLimb(group.getObjectByName('bathLegThigh' + side), _a, _b);
    _a.copy(_b);
    const kneeMesh = group.getObjectByName('bathLegKnee' + side);
    if (kneeMesh) kneeMesh.position.copy(_a);
    _b.set(_a.x, (PRIVATE_TEAM_BATH_LEG_BOTTOM_Y - py) / rs, _a.z);
    if (_a.y - _b.y < 0.25) _b.y = _a.y - 0.25;
    placeLimb(group.getObjectByName('bathLegShin' + side), _a, _b);
    const foot = group.getObjectByName('bathLegFoot' + side);
    if (foot) foot.position.set(_b.x, _b.y - 0.01, _b.z + 0.05);
  }
}
