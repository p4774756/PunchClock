// 莫卡賭場 3D 室內：拉斯維加斯霓虹廳（低多邊形、無 PointLight）。

import { interiorByBuilding } from './island-interior.js';

const SLOTS_MSG = '即將開獎，敬請期待！';
const ROULETTE_MSG = '輪盤轉運中，好運將至！';

export function createInterior(THREE, opts) {
  const room = interiorByBuilding('casino');
  const ground = (opts && opts.groundY != null) ? opts.groundY : 0.2;
  const reduced = !!(opts && opts.reducedMotion);
  const floor = room.floor;
  const width = floor.maxX - floor.minX;
  const depth = floor.maxZ - floor.minZ;
  const cx = (floor.minX + floor.maxX) / 2;
  const cz = (floor.minZ + floor.maxZ) / 2;
  const wallH = 2.25;
  const wallT = 0.1;
  const materials = [];
  const geometries = [];
  let feedbackText = '';
  let feedbackUntil = 0;
  let wheelSpin = 0;

  function mat(color, night, glow, emissiveI) {
    const material = new THREE.MeshLambertMaterial({
      color: color,
      flatShading: true,
      emissive: new THREE.Color(glow || 0x000000),
      emissiveIntensity: emissiveI || 0
    });
    material.userData.day = new THREE.Color(color);
    material.userData.night = new THREE.Color(night);
    material.userData.dayE = emissiveI || 0;
    material.userData.nightE = glow ? Math.max(emissiveI || 0, 0.92) : 0;
    materials.push(material);
    return material;
  }

  function box(w, h, d, color, night, x, y, z, glow, emissiveI) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow, emissiveI));
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }

  const group = new THREE.Group();
  const wallY = ground + wallH / 2;
  const southZ = floor.minZ - wallT / 2;

  box(width + wallT * 2, 0.12, depth + wallT * 2, 0x2a1848, 0x3a2858, cx, ground + 0.06, cz);
  box(width, 0.05, depth, 0x5c1838, 0x7a2848, cx, ground + 0.1, cz);

  const carpetGeo = new THREE.BoxGeometry(0.52, 0.022, 0.52);
  geometries.push(carpetGeo);
  const carpetMat = mat(0x8c2030, 0xa82838, 0xff6a78, 0.06);
  const carpetCols = 14;
  const carpetRows = 9;
  const carpetW = width / carpetCols;
  const carpetD = depth / carpetRows;
  const carpetCount = carpetCols * carpetRows;
  const carpets = new THREE.InstancedMesh(carpetGeo, carpetMat, carpetCount);
  const dummy = new THREE.Object3D();
  let ci = 0;
  for (let row = 0; row < carpetRows; row++) {
    for (let col = 0; col < carpetCols; col++) {
      dummy.position.set(
        floor.minX + carpetW * (col + 0.5),
        ground + 0.115,
        floor.minZ + carpetD * (row + 0.5)
      );
      dummy.updateMatrix();
      carpets.setMatrixAt(ci, dummy.matrix);
      ci += 1;
    }
  }
  carpets.instanceMatrix.needsUpdate = true;
  group.add(carpets);

  const wallPurple = mat(0x4a2878, 0x5a3888, 0x7a48a8, 0.08);
  function wallBox(w, h, d, x, y, z) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, wallPurple);
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }
  wallBox(wallT, wallH, depth + wallT, floor.minX - wallT / 2, wallY, cz);
  wallBox(wallT, wallH, depth + wallT, floor.maxX + wallT / 2, wallY, cz);
  wallBox(width + wallT * 2, wallH, wallT, cx, wallY, floor.maxZ + wallT / 2);

  const doorW = 1.35;
  const leftW = (width - doorW) / 2;
  wallBox(leftW, wallH, wallT, floor.minX + leftW / 2, wallY, southZ);
  wallBox(leftW, wallH, wallT, floor.maxX - leftW / 2, wallY, southZ);
  box(doorW + 0.08, 0.1, 0.12, 0xd4a84b, 0xd4a84b, 0, ground + 1.72, southZ + 0.04, 0xffc56a, 0.55);
  box(doorW + 0.22, 0.05, 0.06, 0xff6ad8, 0xff6ad8, 0, ground + 1.82, southZ + 0.05, 0xff4ec8, 0.78);

  box(width - 0.2, 0.08, 0.06, 0xd4a84b, 0xd4a84b, cx, ground + wallH - 0.12, cz, 0xffc56a, 0.35);
  box(width - 0.35, 0.05, 0.04, 0xff6ad8, 0xff6ad8, cx, ground + wallH - 0.22, cz, 0xff4ec8, 0.62);

  function buildSlot(cxLocal, czLocal) {
    box(0.58, 1.72, 0.52, 0x3d2468, 0x2a1848, cxLocal, ground + 0.98, czLocal);
    box(0.48, 0.82, 0.06, 0x141020, 0x141020, cxLocal, ground + 1.05, czLocal + 0.25);
    box(0.44, 0.38, 0.04, 0x221828, 0x181020, cxLocal, ground + 1.08, czLocal + 0.28);
    box(0.5, 0.14, 0.5, 0xd4a84b, 0xd4a84b, cxLocal, ground + 1.72, czLocal - 0.02, 0xffc56a, 0.55);
    box(0.42, 0.22, 0.06, 0xffe878, 0xffc858, cxLocal, ground + 1.15, czLocal + 0.29, 0xffe878, 0.85);
  }

  const slotZs = [0.15, 0.85, 1.55, 2.25];
  for (let si = 0; si < slotZs.length; si++) {
    buildSlot(-2.68, slotZs[si]);
  }

  const wheelGroup = new THREE.Group();
  wheelGroup.position.set(0, ground + 0.82, 2.02);
  group.add(wheelGroup);
  const wheelGeo = new THREE.CylinderGeometry(0.62, 0.62, 0.1, 12);
  geometries.push(wheelGeo);
  const wheelBase = new THREE.Mesh(wheelGeo, mat(0x3d2468, 0x2a1848, 0x5a3878, 0.12));
  wheelGroup.add(wheelBase);
  const wheelTopGeo = new THREE.CylinderGeometry(0.48, 0.48, 0.06, 12);
  geometries.push(wheelTopGeo);
  const wheelTop = new THREE.Mesh(wheelTopGeo, mat(0x2a9858, 0x1a7848, 0x5ae898, 0.22));
  wheelTop.position.y = 0.08;
  wheelGroup.add(wheelTop);
  for (let wi = 0; wi < 8; wi++) {
    const ang = (wi / 8) * Math.PI * 2;
    box(0.08, 0.06, 0.22, 0xff6ad8, 0xff6ad8,
      Math.cos(ang) * 0.38, ground + 0.92, 2.02 + Math.sin(ang) * 0.38, 0xff4ec8, 0.72);
  }
  box(1.35, 0.1, 0.95, 0x5a4878, 0x4a3868, 0, ground + 0.74, 2.02);

  const tbl = room.blocks.find((b) => b.id === 'bj-table');
  const tx = (tbl.minX + tbl.maxX) / 2;
  const tz = (tbl.minZ + tbl.maxZ) / 2;
  box(tbl.maxX - tbl.minX, 0.08, tbl.maxZ - tbl.minZ, 0x2a9860, 0x1a7848, tx, ground + 0.74, tz, 0x5ae898, 0.25);
  box(tbl.maxX - tbl.minX + 0.08, 0.05, tbl.maxZ - tbl.minZ + 0.08, 0x5a7858, 0x4a6850, tx, ground + 0.68, tz);

  const stoolGeo = new THREE.CylinderGeometry(0.14, 0.16, 0.42, 6);
  geometries.push(stoolGeo);
  const stoolMat = mat(0xd4a84b, 0xb48838, 0xffc56a, 0.15);
  const stoolCount = 4;
  const stools = new THREE.InstancedMesh(stoolGeo, stoolMat, stoolCount);
  const stoolPos = [
    [tx - 0.55, tz + 0.55],
    [tx + 0.55, tz + 0.55],
    [tx - 0.55, tz - 0.45],
    [tx + 0.55, tz - 0.45]
  ];
  for (let si = 0; si < stoolCount; si++) {
    dummy.position.set(stoolPos[si][0], ground + 0.28, stoolPos[si][1]);
    dummy.updateMatrix();
    stools.setMatrixAt(si, dummy.matrix);
  }
  stools.instanceMatrix.needsUpdate = true;
  group.add(stools);

  const chipGeo = new THREE.CylinderGeometry(0.14, 0.14, 0.05, 8);
  geometries.push(chipGeo);
  const chipMat = mat(0xffe878, 0xffc858, 0xffe878, 0.18);
  const chipCount = 12;
  const chips = new THREE.InstancedMesh(chipGeo, chipMat, chipCount);
  const chipSpots = [
    [0, 1.0, 0], [0.12, 1.02, 0.04], [-0.1, 0.98, -0.03],
    [tx - 0.35, 0.82, tz + 0.35], [tx - 0.22, 0.86, tz + 0.32], [tx - 0.42, 0.84, tz + 0.38],
    [-0.25, 0.88, 1.85], [0.18, 0.9, 1.9], [-0.05, 0.92, 1.78]
  ];
  for (let chi = 0; chi < chipSpots.length; chi++) {
    dummy.position.set(chipSpots[chi][0], ground + chipSpots[chi][1], chipSpots[chi][2]);
    dummy.updateMatrix();
    chips.setMatrixAt(chi, dummy.matrix);
  }
  chips.count = chipSpots.length;
  chips.instanceMatrix.needsUpdate = true;
  group.add(chips);

  const bulbGeo = new THREE.SphereGeometry(0.045, 5, 4);
  geometries.push(bulbGeo);
  const bulbMat = mat(0xffe878, 0xffc858, 0xffd888, 0.65);
  const bulbCount = 10;
  const bulbs = new THREE.InstancedMesh(bulbGeo, bulbMat, bulbCount);
  const bulbPos = [
    [-3.4, 2.05, -0.5], [-3.4, 2.05, 0.5], [-3.4, 2.05, 1.5], [-3.4, 2.05, 2.5],
    [2.8, 2.05, 0.5], [2.8, 2.05, 1.5], [2.8, 2.05, 2.4],
    [-0.8, 2.05, 2.85], [0.8, 2.05, 2.85], [0, 2.05, -1.0]
  ];
  for (let bi = 0; bi < bulbCount; bi++) {
    dummy.position.set(bulbPos[bi][0], ground + bulbPos[bi][1], bulbPos[bi][2]);
    dummy.updateMatrix();
    bulbs.setMatrixAt(bi, dummy.matrix);
  }
  bulbs.instanceMatrix.needsUpdate = true;
  group.add(bulbs);

  box(0.65, 0.1, 0.14, 0xff6ad8, 0xff6ad8, 0, ground + 1.22, -1.25, 0xff4ec8, 0.72);

  function poke(kind) {
    if (kind === 'slots') {
      feedbackText = SLOTS_MSG;
      feedbackUntil = 2.2;
      return feedbackText;
    }
    if (kind === 'roulette') {
      feedbackText = ROULETTE_MSG;
      feedbackUntil = 2.2;
      wheelSpin = reduced ? 0 : 1;
      return feedbackText;
    }
    return '';
  }

  function tick(dt) {
    if (feedbackUntil > 0) feedbackUntil = Math.max(0, feedbackUntil - dt);
    if (feedbackUntil <= 0) feedbackText = '';
    if (!reduced && wheelSpin > 0) {
      wheelSpin = Math.max(0, wheelSpin - dt * 0.65);
      wheelTop.rotation.y += dt * 2.4 * wheelSpin;
    }
    return { message: feedbackText };
  }

  return {
    group: group,
    materials: materials,
    poke: poke,
    tick: tick,
    dispose() {
      geometries.forEach((geometry) => geometry.dispose());
      materials.forEach((material) => {
        if (material.map) material.map.dispose();
        material.dispose();
      });
    }
  };
}