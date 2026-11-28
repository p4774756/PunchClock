import * as THREE from './vendor/three.module.min.js';
import { lampGlowTexture, WALL_THICK } from './private-team-scene-room.js';
import { SECRETARY_SOFA_POSITION } from './private-team-scene-lounge.js';

const ACCENT_COLOR = 0xffd4a8;
const TRACK_COLOR = 0x3a3e44;
const TRACK_CUP = 0x1a1c20;

function addSpotWithPool(root, spec) {
  const spot = new THREE.SpotLight(
    spec.color || ACCENT_COLOR,
    spec.intensity,
    spec.distance || 5.2,
    spec.angle || Math.PI / 5.2,
    spec.penumbra != null ? spec.penumbra : 0.62,
    spec.decay != null ? spec.decay : 1.42
  );
  spot.name = spec.name;
  spot.castShadow = false;
  spot.position.set(spec.fromX, spec.fromY, spec.fromZ);
  const target = new THREE.Object3D();
  target.name = spec.name + 'Target';
  target.position.set(spec.toX, spec.toY, spec.toZ);
  root.add(target);
  spot.target = target;
  root.add(spot);

  let pool = null;
  let poolMat = null;
  if (spec.poolRadius > 0) {
    poolMat = new THREE.MeshBasicMaterial({
      map: lampGlowTexture(),
      color: spec.color || ACCENT_COLOR,
      transparent: true,
      opacity: spec.poolOpacity != null ? spec.poolOpacity : 0.36,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    });
    pool = new THREE.Mesh(new THREE.CircleGeometry(spec.poolRadius, 26), poolMat);
    pool.rotation.x = -Math.PI / 2;
    pool.position.set(spec.toX, 0.059, spec.toZ);
    pool.name = spec.name + 'LightPool';
    pool.renderOrder = 2;
    root.add(pool);
  }
  return { spot, target, pool, poolMat, baseIntensity: spec.intensity, basePoolOpacity: poolMat ? poolMat.opacity : 0 };
}

function addRecessedDownlight(parent, x, y, z, name) {
  const housing = new THREE.Mesh(
    new THREE.CylinderGeometry(0.055, 0.062, 0.028, 18),
    new THREE.MeshStandardMaterial({ color: TRACK_CUP, metalness: 0.42, roughness: 0.48 })
  );
  housing.position.set(x, y, z);
  housing.name = name;
  // 房間沒有天花板，嵌燈外殼在斜角／俯視會像漂浮的黑色圓盤：隱藏外觀，保留名稱與下方 SpotLight。
  housing.visible = false;
  parent.add(housing);
  const trim = new THREE.Mesh(
    new THREE.TorusGeometry(0.058, 0.004, 8, 20),
    new THREE.MeshStandardMaterial({ color: 0x5a5e64, metalness: 0.5, roughness: 0.35 })
  );
  trim.rotation.x = Math.PI / 2;
  trim.position.set(x, y + 0.012, z);
  trim.name = name + 'Trim';
  trim.visible = false;
  parent.add(trim);
  const emitter = new THREE.Mesh(
    new THREE.CircleGeometry(0.038, 14),
    new THREE.MeshBasicMaterial({
      color: 0xfff0d8,
      transparent: true,
      opacity: 0.72,
      depthWrite: false
    })
  );
  emitter.rotation.x = -Math.PI / 2;
  emitter.position.set(x, y - 0.01, z);
  emitter.name = name + 'Emitter';
  emitter.visible = false;
  parent.add(emitter);
}

const _aimDown = new THREE.Vector3(0, -1, 0);
const _aimDir = new THREE.Vector3();

/** 燈頭朝向：杯口（本地 -y）對準目標，並限制不要太水平，避免燈杯躺平。 */
export function trackHeadAimQuaternion(from, target) {
  _aimDir.subVectors(target, from);
  if (_aimDir.lengthSq() < 1e-8) _aimDir.set(0, -1, 0);
  _aimDir.normalize();
  if (_aimDir.y > -0.4) {
    _aimDir.y = -0.4;
    _aimDir.normalize();
  }
  return new THREE.Quaternion().setFromUnitVectors(_aimDown, _aimDir);
}

function addTrackHead(parent, x, y, z, name, aimTarget) {
  const arm = new THREE.Mesh(
    new THREE.BoxGeometry(0.038, 0.022, 0.034),
    new THREE.MeshStandardMaterial({ color: TRACK_COLOR, metalness: 0.45, roughness: 0.42 })
  );
  arm.position.set(x, y - 0.02, z);
  arm.name = name + 'Arm';
  parent.add(arm);

  // 燈頭整組跟著杯體傾斜，透鏡貼在開口上，斜角才看得到發光面（舊版透鏡不跟著轉，看起來像關著的黑杯子）。
  const group = new THREE.Group();
  group.position.set(x, y - 0.028, z);
  if (aimTarget) {
    group.quaternion.copy(trackHeadAimQuaternion(group.position, aimTarget));
  } else {
    group.rotation.x = 0.72;
  }
  group.name = name + 'Group';
  parent.add(group);

  const cupMat = new THREE.MeshStandardMaterial({
    color: TRACK_CUP,
    metalness: 0.42,
    roughness: 0.38,
    emissive: new THREE.Color(0xffc070),
    emissiveIntensity: 0.55
  });
  const cup = new THREE.Mesh(new THREE.CylinderGeometry(0.034, 0.05, 0.064, 14), cupMat);
  cup.position.set(0, -0.048, 0);
  cup.name = name;
  group.add(cup);

  const lensMat = new THREE.MeshBasicMaterial({
    color: 0xfff3d8,
    transparent: true,
    opacity: 0.96,
    depthWrite: false
  });
  const lens = new THREE.Mesh(new THREE.CircleGeometry(0.032, 14), lensMat);
  lens.rotation.x = Math.PI / 2;
  lens.position.set(0, -0.082, 0);
  lens.name = name + 'Lens';
  lens.renderOrder = 3;
  group.add(lens);

  const bulb = new THREE.Mesh(
    new THREE.SphereGeometry(0.012, 8, 8),
    new THREE.MeshBasicMaterial({ color: 0xfff6e4, transparent: true, opacity: 0.92, depthWrite: false })
  );
  bulb.position.set(0, -0.074, 0);
  bulb.name = name + 'Bulb';
  group.add(bulb);

  const coneMat = new THREE.MeshBasicMaterial({
    map: lampGlowTexture(),
    color: 0xffd4a0,
    transparent: true,
    opacity: 0.2,
    depthWrite: false,
    blending: THREE.AdditiveBlending,
    side: THREE.DoubleSide
  });
  const cone = new THREE.Mesh(new THREE.ConeGeometry(0.11, 0.34, 16, 1, true), coneMat);
  cone.position.set(0, -0.25, 0);
  cone.name = name + 'Beam';
  cone.renderOrder = 2;
  group.add(cone);

  return { cupMat, lensMat, coneMat, emissiveBase: 0.55, lensBase: 0.96, coneBase: 0.2 };
}

/**
 * 美術館／精品住宿風天花板嵌燈、軌道射燈與重點光圈（無陰影）。
 * @returns {{ applyPeriod: (lt: object) => void }}
 */
export function addPrivateTeamGalleryLighting(root, layout) {
  const wallH = layout.wallH;
  const wallY = layout.wallY;
  const leftX = layout.leftX;
  const backZ = layout.backZ;
  const tubCx = layout.tubCx;
  const tubCz = layout.tubCz;
  const rimTopY = layout.rimTopY;

  const ceilingY = wallH - 0.075;
  const fixtureRoot = new THREE.Group();
  fixtureRoot.name = 'galleryCeilingFixtures';
  root.add(fixtureRoot);

  const rail = new THREE.Mesh(
    new THREE.BoxGeometry(1.32, 0.016, 0.028),
    new THREE.MeshStandardMaterial({ color: TRACK_COLOR, metalness: 0.52, roughness: 0.36 })
  );
  rail.position.set(0.95, ceilingY, -0.38);
  rail.name = 'galleryCeilingTrackRail';
  fixtureRoot.add(rail);

  const trackHeadDefs = [
    { x: 0.42, name: 'galleryTrackHeadPoster' },
    { x: 0.78, name: 'galleryTrackHeadTub' },
    { x: 1.14, name: 'galleryTrackHeadSofa' },
    { x: 1.46, name: 'galleryTrackHeadVinyl' }
  ];
  addRecessedDownlight(fixtureRoot, -0.28, ceilingY, 0.08, 'galleryRecessedTub');
  addRecessedDownlight(fixtureRoot, 1.48, ceilingY, -0.72, 'galleryRecessedLounge');
  addRecessedDownlight(fixtureRoot, -0.72, ceilingY, 0.62, 'galleryRecessedDesk');

  const posterX = leftX + WALL_THICK + 0.05;
  const posterY = wallY + 0.05;
  const sofaX = SECRETARY_SOFA_POSITION.x;
  const sofaZ = SECRETARY_SOFA_POSITION.z;
  const vinylX = 0.86;
  const vinylZ = backZ + 0.38;

  // 四顆燈頭各自朝不同方向：左牆畫、浴缸地面、沙發、窗邊櫃。
  const headAims = [
    new THREE.Vector3(posterX, posterY, 0),
    new THREE.Vector3(tubCx, rimTopY - 0.05, tubCz),
    new THREE.Vector3(sofaX, 0.38, sofaZ),
    new THREE.Vector3(vinylX, 0.62, vinylZ)
  ];
  const heads = [];
  for (let i = 0; i < trackHeadDefs.length; i++) {
    heads.push(addTrackHead(fixtureRoot, trackHeadDefs[i].x, ceilingY, -0.38, trackHeadDefs[i].name, headAims[i]));
  }

  const fromY = ceilingY - 0.1;
  const spots = [
    addSpotWithPool(root, {
      name: 'galleryAccentSpotPoster',
      fromX: 0.42,
      fromY,
      fromZ: -0.38,
      toX: posterX,
      toY: posterY,
      toZ: 0,
      intensity: 2.15,
      distance: 4.2,
      angle: Math.PI / 6.8,
      penumbra: 0.58,
      poolRadius: 0 // 目標在牆上：不在地板打光圈，避免地面出現零散亮斑
    }),
    addSpotWithPool(root, {
      name: 'galleryAccentSpotBathtub',
      fromX: 0.78,
      fromY,
      fromZ: -0.38,
      toX: tubCx,
      toY: rimTopY - 0.05,
      toZ: tubCz,
      intensity: 2.55,
      distance: 4.8,
      angle: Math.PI / 5.4,
      penumbra: 0.55,
      poolRadius: 0.52
    }),
    addSpotWithPool(root, {
      name: 'galleryAccentSpotSofa',
      fromX: 1.14,
      fromY,
      fromZ: -0.38,
      toX: sofaX,
      toY: 0.38,
      toZ: sofaZ,
      intensity: 2.35,
      distance: 4.6,
      angle: Math.PI / 5.6,
      penumbra: 0.6,
      poolRadius: 0.48
    }),
    addSpotWithPool(root, {
      name: 'galleryAccentSpotVinyl',
      fromX: 1.46,
      fromY,
      fromZ: -0.38,
      toX: vinylX,
      toY: 0.62,
      toZ: vinylZ,
      intensity: 2.05,
      distance: 4.4,
      angle: Math.PI / 6.2,
      penumbra: 0.62,
      poolRadius: 0 // 目標在窗邊櫃上，同上
    })
  ];

  const sofaIndirect = new THREE.Mesh(
    new THREE.PlaneGeometry(1.05, 0.48),
    new THREE.MeshBasicMaterial({
      color: 0xffc078,
      transparent: true,
      opacity: 0.16,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
  );
  sofaIndirect.position.set(sofaX + 0.08, 1.08, backZ + 0.42);
  sofaIndirect.name = 'sofaBackIndirectWash';
  // 離後牆 0.4 的半空中平面，斜角會像右牆外的半透明色塊：隱藏。
  sofaIndirect.visible = false;
  root.add(sofaIndirect);

  let sofaIndirectBase = sofaIndirect.material.opacity;

  function applyPeriod(lt) {
    const mul = lt.galleryMul != null ? lt.galleryMul : 1;
    const poolMul = lt.galleryPoolMul != null ? lt.galleryPoolMul : mul;
    const indirectMul = lt.galleryIndirectMul != null ? lt.galleryIndirectMul : mul * 0.85;
    for (let i = 0; i < spots.length; i++) {
      const s = spots[i];
      s.spot.intensity = s.baseIntensity * mul;
      if (s.poolMat) {
        s.poolMat.opacity = s.basePoolOpacity * poolMul;
      }
    }
    for (let i = 0; i < heads.length; i++) {
      const h = heads[i];
      h.cupMat.emissiveIntensity = h.emissiveBase * mul;
      h.lensMat.opacity = h.lensBase * (0.28 + 0.72 * mul);
      h.coneMat.opacity = h.coneBase * mul;
    }
    sofaIndirect.material.opacity = sofaIndirectBase * indirectMul;
  }

  return { applyPeriod };
}