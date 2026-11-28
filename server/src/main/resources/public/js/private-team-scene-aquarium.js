import * as THREE from './vendor/three.module.min.js';
import { FLOOR_SIZE, box, makeBathtubCausticsTexture } from './private-team-scene-room.js';

/** 盆緣高出地板約 12cm */
export const PRIVATE_TEAM_TUB_RIM_TOP_Y = 0.125;
/** 浴缸外殼下緣與水族箱底對齊 */
export const PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y = -0.88;
export const PRIVATE_TEAM_AQUARIUM_DEPTH = 0.86;
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

const SWIMSUIT = 0xd85a82;
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
  const grassMat = new THREE.MeshLambertMaterial({
    color: 0x2e7a48,
    emissive: 0x144830,
    emissiveIntensity: 0.32
  });
  const grassBases = [
    [-0.55, 0.55],
    [0.42, 0.62],
    [-0.2, -0.35]
  ];
  const seagrass = [];
  for (let gi = 0; gi < grassBases.length; gi++) {
    const blade = new THREE.Mesh(new THREE.BoxGeometry(0.028, 0.2, 0.012), grassMat);
    const [gx, gz] = grassBases[gi];
    blade.position.set(gx, tankBottom + 0.1, 0.04 + gz);
    blade.rotation.z = (gi - 1) * 0.18;
    blade.name = 'aquariumSeagrass' + gi;
    group.add(blade);
    seagrass.push(blade);
  }

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

  const underGlow = new THREE.PointLight(0x48a0d8, PRIVATE_TEAM_AQUARIUM_UNDER_GLOW, 5.8, 1.4);
  underGlow.position.set(0, tankBottom + 0.08, 0.04);
  underGlow.name = 'aquariumUnderGlow';
  group.add(underGlow);

  waterVol.renderOrder = 2;
  sand.renderOrder = 1;

  const fishGroup = new THREE.Group();
  fishGroup.name = 'aquariumFish';
  const fishMat = new THREE.MeshBasicMaterial({ color: 0xf4c878 });
  const fishBodies = [];
  const fishSeeds = [
    { x: -0.45, z: 0.2, phase: 0.1 },
    { x: 0.35, z: -0.15, phase: 1.7 },
    { x: -0.15, z: 0.55, phase: 2.9 },
    { x: 0.7, z: 0.4, phase: 4.2 }
  ];
  for (let fi = 0; fi < fishSeeds.length; fi++) {
    const f = new THREE.Mesh(new THREE.BoxGeometry(0.055, 0.028, 0.1), fishMat);
  const tail = new THREE.Mesh(new THREE.BoxGeometry(0.022, 0.018, 0.04), fishMat);
    tail.position.set(-0.038, 0, 0);
    const g = new THREE.Group();
    g.add(f, tail);
    g.position.set(fishSeeds[fi].x, tankBottom + 0.28, 0.04 + fishSeeds[fi].z);
    g.name = 'aquariumFish' + fi;
    fishGroup.add(g);
    fishBodies.push({ group: g, seed: fishSeeds[fi], baseX: fishSeeds[fi].x, baseZ: fishSeeds[fi].z });
  }
  group.add(fishGroup);

  root.add(group);
  return {
    group,
    causticsTex,
    causticsMat,
    fishBodies,
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
  for (let fi = 0; fi < state.fishBodies.length; fi++) {
    const f = state.fishBodies[fi];
    const t = elapsed * 0.45 + f.seed.phase;
    f.group.position.x = f.baseX + Math.sin(t) * 0.28;
    f.group.position.z = 0.04 + f.baseZ + Math.cos(t * 0.85) * 0.22;
    f.group.rotation.y = Math.sin(t * 1.2) * 0.6;
  }
  for (let gi = 0; gi < state.seagrass.length; gi++) {
    state.seagrass[gi].rotation.x = Math.sin(elapsed * 1.4 + gi) * 0.12;
  }
}

/** 泡澡時 3D 泳裝腿部（從水面下方向可見輪廓）。 */
export function addSecretaryBathLegs(parent) {
  if (parent.getObjectByName('secretaryBathLegs')) return;
  const group = new THREE.Group();
  group.name = 'secretaryBathLegs';
  const suitMat = new THREE.MeshLambertMaterial({
    color: SWIMSUIT,
    emissive: 0x5a1830,
    emissiveIntensity: 0.62
  });
  function leg(side) {
    const sign = side === 'L' ? -1 : 1;
    const thigh = new THREE.Mesh(new THREE.BoxGeometry(0.12, 0.24, 0.14), suitMat);
    thigh.position.set(sign * 0.1, 0.18, 0.12);
    thigh.rotation.x = -0.42;
    thigh.rotation.z = sign * 0.08;
    thigh.name = 'bathLegThigh' + side;
    const shin = new THREE.Mesh(new THREE.BoxGeometry(0.09, 0.24, 0.1), suitMat);
    shin.position.set(sign * 0.12, -0.02, 0.28);
    shin.rotation.x = 0.65;
    shin.name = 'bathLegShin' + side;
    const foot = new THREE.Mesh(new THREE.BoxGeometry(0.07, 0.05, 0.14), suitMat);
    foot.position.set(sign * 0.1, -0.12, 0.42);
    foot.rotation.x = 0.25;
    foot.name = 'bathLegFoot' + side;
    return [thigh, shin, foot];
  }
  group.add(...leg('L'), ...leg('R'));
  group.position.set(0, 0.1, -0.02);
  group.renderOrder = 4;
  parent.add(group);
  for (let ci = 0; ci < group.children.length; ci++) {
    group.children[ci].renderOrder = 4;
  }
}