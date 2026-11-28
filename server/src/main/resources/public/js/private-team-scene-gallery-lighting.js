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

function addTrackHead(parent, x, y, z, name, tiltX) {
  const arm = new THREE.Mesh(
    new THREE.BoxGeometry(0.038, 0.022, 0.034),
    new THREE.MeshStandardMaterial({ color: TRACK_COLOR, metalness: 0.45, roughness: 0.42 })
  );
  arm.position.set(x, y - 0.02, z);
  arm.name = name + 'Arm';
  parent.add(arm);
  const cup = new THREE.Mesh(
    new THREE.CylinderGeometry(0.034, 0.048, 0.062, 14),
    new THREE.MeshStandardMaterial({ color: TRACK_CUP, metalness: 0.48, roughness: 0.4 })
  );
  cup.position.set(x, y - 0.068, z);
  cup.rotation.x = tiltX != null ? tiltX : 0.72;
  cup.name = name;
  parent.add(cup);
  const lens = new THREE.Mesh(
    new THREE.CircleGeometry(0.03, 12),
    new THREE.MeshBasicMaterial({
      color: 0xffe8c8,
      transparent: true,
      opacity: 0.78,
      depthWrite: false
    })
  );
  lens.rotation.x = Math.PI / 2;
  lens.position.set(x, y - 0.095, z);
  lens.name = name + 'Lens';
  parent.add(lens);
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
  for (let i = 0; i < trackHeadDefs.length; i++) {
    addTrackHead(fixtureRoot, trackHeadDefs[i].x, ceilingY, -0.38, trackHeadDefs[i].name);
  }

  addRecessedDownlight(fixtureRoot, -0.28, ceilingY, 0.08, 'galleryRecessedTub');
  addRecessedDownlight(fixtureRoot, 1.48, ceilingY, -0.72, 'galleryRecessedLounge');
  addRecessedDownlight(fixtureRoot, -0.72, ceilingY, 0.62, 'galleryRecessedDesk');

  const posterX = leftX + WALL_THICK + 0.05;
  const posterY = wallY + 0.05;
  const sofaX = SECRETARY_SOFA_POSITION.x;
  const sofaZ = SECRETARY_SOFA_POSITION.z;
  const vinylX = 0.86;
  const vinylZ = backZ + 0.38;

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
      poolRadius: 0.42
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
      poolRadius: 0.38
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
    sofaIndirect.material.opacity = sofaIndirectBase * indirectMul;
  }

  return { applyPeriod };
}