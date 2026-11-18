// 莫卡賭場 3D 室內：粉彩霓虹台式遊樂場（低多邊形、無 PointLight）。

import { interiorByBuilding } from './island-interior.js';

const CLAW_MSG = Object.freeze(['夾到一隻小熊！', '抓到粉紅兔兔！']);
const PINBALL_MSG = '彈珠嘣啦啦～好彩頭！';

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
  const clawPlush = [];
  const pinballs = [];
  let feedbackText = '';
  let feedbackUntil = 0;
  let clawPulse = 0;
  let pinPulse = 0;

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

  box(width + wallT * 2, 0.12, depth + wallT * 2, 0x3a2858, 0x5a4878, cx, ground + 0.06, cz);
  box(width, 0.05, depth, 0x2a2238, 0x424058, cx, ground + 0.1, cz);

  const tileCols = 10;
  const tileRows = 7;
  const tileW = width / tileCols;
  const tileD = depth / tileRows;
  const tileGeo = new THREE.BoxGeometry(tileW * 0.96, 0.025, tileD * 0.96);
  geometries.push(tileGeo);
  const tileCount = tileCols * tileRows;
  const tileMatA = mat(0xfff0f8, 0xd8c0d0, 0xff9ee8, 0.08);
  const tileMatB = mat(0xe8fff8, 0xc0d8d0, 0x7af8e8, 0.08);
  const tilesA = new THREE.InstancedMesh(tileGeo, tileMatA, Math.ceil(tileCount / 2));
  const tilesB = new THREE.InstancedMesh(tileGeo, tileMatB, Math.floor(tileCount / 2));
  const dummy = new THREE.Object3D();
  let tiA = 0;
  let tiB = 0;
  for (let row = 0; row < tileRows; row++) {
    for (let col = 0; col < tileCols; col++) {
      dummy.position.set(
        floor.minX + tileW * (col + 0.5),
        ground + 0.115,
        floor.minZ + tileD * (row + 0.5)
      );
      dummy.updateMatrix();
      if ((col + row) % 2) {
        tilesB.setMatrixAt(tiB, dummy.matrix);
        tiB += 1;
      } else {
        tilesA.setMatrixAt(tiA, dummy.matrix);
        tiA += 1;
      }
    }
  }
  tilesA.count = tiA;
  tilesB.count = tiB;
  tilesA.instanceMatrix.needsUpdate = true;
  tilesB.instanceMatrix.needsUpdate = true;
  group.add(tilesA);
  group.add(tilesB);

  box(wallT, wallH, depth + wallT, 0xfff0fa, 0xb8a0c8, floor.minX - wallT / 2, wallY, cz);
  box(wallT, wallH, depth + wallT, 0xfff0fa, 0xb8a0c8, floor.maxX + wallT / 2, wallY, cz);
  box(width + wallT * 2, wallH, wallT, 0xfff0fa, 0xb8a0c8, cx, wallY, floor.maxZ + wallT / 2);

  const doorW = 1.35;
  const leftW = (width - doorW) / 2;
  box(leftW, wallH, wallT, 0xfff0fa, 0xb8a0c8, floor.minX + leftW / 2, wallY, southZ);
  box(leftW, wallH, wallT, 0xfff0fa, 0xb8a0c8, floor.maxX - leftW / 2, wallY, southZ);

  const archY = ground + 1.75;
  box(0.22, 0.22, 0.22, 0xff7ad8, 0xff7ad8, -1.1, archY, southZ + 0.04, 0xff7ad8, 0.65);
  box(0.22, 0.22, 0.22, 0xff7ad8, 0xff7ad8, 1.1, archY, southZ + 0.04, 0xff7ad8, 0.65);
  box(doorW + 0.28, 0.14, 0.12, 0xff9ee8, 0xff9ee8, 0, archY + 0.08, southZ + 0.04, 0xff6ad8, 0.78);
  box(0.12, 1.55, 0.12, 0xffc8f0, 0xffc8f0, -1.1, ground + 0.92, southZ + 0.05, 0xff7ad8, 0.42);
  box(0.12, 1.55, 0.12, 0xffc8f0, 0xffc8f0, 1.1, ground + 0.92, southZ + 0.05, 0xff7ad8, 0.42);

  function buildClaw(cxLocal, czLocal, plushColor, glow) {
    box(0.78, 1.65, 0.62, 0xc8a8d8, 0x9880b0, cxLocal, ground + 0.92, czLocal);
    box(0.6, 0.95, 0.46, 0x1e1828, 0x141020, cxLocal, ground + 1.02, czLocal + 0.04);
    box(0.64, 0.72, 0.04, 0xc8e8ff, 0x98c0e8, cxLocal, ground + 1.08, czLocal + 0.28, 0x88d8ff, 0.18);
    box(0.68, 0.12, 0.52, 0xff9ee8, 0xff9ee8, cxLocal, ground + 1.58, czLocal - 0.02, glow, 0.72);
    box(0.08, 0.32, 0.08, 0x9ad8ff, 0x9ad8ff, cxLocal, ground + 1.38, czLocal + 0.12, 0x7ae8ff, 0.55);
    const plush = box(0.22, 0.22, 0.18, plushColor, plushColor, cxLocal, ground + 0.92, czLocal + 0.08, glow, 0.42);
    clawPlush.push(plush);
  }

  buildClaw(-2.48, 2.18, 0xffb878, 0xff9a68);
  buildClaw(-1.18, 2.18, 0xffa8d8, 0xff88c8);

  const pinCx = 2.72;
  const pinD = 0.42;
  for (let pi = 0; pi < 4; pi++) {
    const px = pinCx + pi * 0.48;
    box(0.42, 0.92, pinD, 0x7ad8e8, 0x5ab8c8, px, ground + 0.56, -0.28);
    box(0.38, 0.55, 0.08, 0x2a3848, 0x2a3848, px, ground + 0.92, -0.48, 0x9ae8ff, 0.45);
    const ballMat = mat(0xffe878, 0xffc858, 0xffe878, 0.2);
    const ballGeo = new THREE.SphereGeometry(0.045, 6, 5);
    geometries.push(ballGeo);
    const ball = new THREE.Mesh(ballGeo, ballMat);
    ball.position.set(px - 0.08, ground + 0.72, -0.22 + pi * 0.04);
    group.add(ball);
    pinballs.push(ball);
  }

  const tbl = room.blocks.find((b) => b.id === 'bj-table');
  const tx = (tbl.minX + tbl.maxX) / 2;
  const tz = (tbl.minZ + tbl.maxZ) / 2;
  box(tbl.maxX - tbl.minX, 0.08, tbl.maxZ - tbl.minZ, 0x3cb878, 0x2a9860, tx, ground + 0.74, tz, 0x5ae898, 0.22);
  box(tbl.maxX - tbl.minX + 0.08, 0.05, tbl.maxZ - tbl.minZ + 0.08, 0x5a7858, 0x4a6850, tx, ground + 0.68, tz);

  const stoolGeo = new THREE.CylinderGeometry(0.14, 0.16, 0.42, 6);
  geometries.push(stoolGeo);
  const stoolMat = mat(0xff9ad8, 0xd878b8, 0xff7ac8, 0.12);
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

  const cab = room.blocks.find((b) => b.id === 'prize-cab');
  const ccx = (cab.minX + cab.maxX) / 2;
  const ccz = (cab.minZ + cab.maxZ) / 2;
  box(cab.maxX - cab.minX, 1.35, cab.maxZ - cab.minZ, 0xe8d0f0, 0xb8a0c8, ccx, ground + 0.78, ccz);
  box(cab.maxX - cab.minX - 0.12, 0.88, 0.42, 0x221828, 0x181020, ccx, ground + 1.0, ccz + 0.08);
  box(cab.maxX - cab.minX - 0.08, 0.9, 0.06, 0xd8c8e8, 0xa8a0c0, ccx, ground + 0.92, ccz + (cab.maxZ - cab.minZ) / 2 - 0.02, 0xff9ee8, 0.35);

  const lanternGeo = new THREE.BoxGeometry(0.22, 0.28, 0.22);
  geometries.push(lanternGeo);
  const lanternMat = mat(0xffe8c8, 0xffc878, 0xffd888, 0.55);
  const lanternCount = 6;
  const lanterns = new THREE.InstancedMesh(lanternGeo, lanternMat, lanternCount);
  const lanternPos = [
    [-3.2, 2.65], [-1.2, 2.65], [0.8, 2.65], [2.5, 2.65],
    [-2.8, -0.6], [2.2, 1.8]
  ];
  for (let li = 0; li < lanternCount; li++) {
    dummy.position.set(lanternPos[li][0], ground + wallH - 0.38, lanternPos[li][1]);
    dummy.updateMatrix();
    lanterns.setMatrixAt(li, dummy.matrix);
  }
  lanterns.instanceMatrix.needsUpdate = true;
  group.add(lanterns);

  const plantGeo = new THREE.ConeGeometry(0.2, 0.38, 5);
  geometries.push(plantGeo);
  const plantMat = mat(0x68c878, 0x489858, 0x88e898, 0.08);
  const plantSpots = [
    [3.15, 2.35], [3.05, -1.35], [-3.35, 0.55], [-0.4, 2.85]
  ];
  for (let pi = 0; pi < plantSpots.length; pi++) {
    const px = plantSpots[pi][0];
    const pz = plantSpots[pi][1];
    box(0.26, 0.14, 0.26, 0xffb8c8, 0xd898a8, px, ground + 0.12, pz, 0xffa8c8, 0.12);
    const coneGeo = plantGeo;
    const plant = new THREE.Mesh(coneGeo, plantMat);
    plant.position.set(px, ground + 0.36, pz);
    group.add(plant);
  }

  box(0.55, 0.08, 0.14, 0xff88d8, 0xff88d8, -2.85, ground + 1.22, 0.42, 0xff6ad8, 0.65);

  function poke(kind, index) {
    if (kind === 'claw') {
      const idx = index === 1 ? 1 : 0;
      feedbackText = CLAW_MSG[idx];
      feedbackUntil = 2.2;
      clawPulse = reduced ? 0 : 1;
      if (!reduced && clawPlush[idx]) {
        clawPlush[idx].position.y += 0.12;
        window.setTimeout(() => {
          if (clawPlush[idx]) clawPlush[idx].position.y -= 0.12;
        }, 420);
      }
      return feedbackText;
    }
    if (kind === 'pinball') {
      feedbackText = PINBALL_MSG;
      feedbackUntil = 2;
      pinPulse = reduced ? 0 : 1;
      return feedbackText;
    }
    return '';
  }

  function tick(dt) {
    if (feedbackUntil > 0) feedbackUntil = Math.max(0, feedbackUntil - dt);
    if (feedbackUntil <= 0) feedbackText = '';
    if (reduced) return { message: feedbackText };
    if (clawPulse > 0) clawPulse = Math.max(0, clawPulse - dt * 1.8);
    if (pinPulse > 0) {
      pinPulse = Math.max(0, pinPulse - dt * 2.2);
      for (let i = 0; i < pinballs.length; i++) {
        pinballs[i].position.z = -0.22 + i * 0.04 + Math.sin(performance.now() * 0.02 + i) * 0.02 * pinPulse;
      }
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