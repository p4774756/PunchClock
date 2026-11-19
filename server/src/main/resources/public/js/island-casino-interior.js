// 神之島賭場 3D 室內：氣派拉斯維加斯大廳（低多邊形、無 PointLight）。

import { interiorByBuilding } from './island-interior.js';

const SLOTS_MSG = '即將開獎，敬請期待！';
const ROULETTE_MSG = '輪盤轉運中，好運將至！';
const HALL_NAME = '神之島賭場';
const WALL_H = 4.8;
const WALL_T = 0.14;

export function createInterior(THREE, opts) {
  const room = interiorByBuilding('casino');
  const ground = (opts && opts.groundY != null) ? opts.groundY : 0.2;
  const reduced = !!(opts && opts.reducedMotion);
  const floor = room.floor;
  const width = floor.maxX - floor.minX;
  const depth = floor.maxZ - floor.minZ;
  const cx = (floor.minX + floor.maxX) / 2;
  const cz = (floor.minZ + floor.maxZ) / 2;
  const materials = [];
  const geometries = [];
  let feedbackText = '';
  let feedbackUntil = 0;
  let wheelSpin = 0;

  function mat(color, night, glow, emissiveI, nightE) {
    const material = new THREE.MeshLambertMaterial({
      color: color,
      flatShading: true,
      emissive: new THREE.Color(glow || 0x000000),
      emissiveIntensity: emissiveI || 0
    });
    material.userData.day = new THREE.Color(color);
    material.userData.night = new THREE.Color(night);
    material.userData.dayE = emissiveI || 0;
    material.userData.nightE = glow
      ? (nightE != null ? nightE : Math.max(emissiveI || 0, 0.85))
      : 0;
    materials.push(material);
    return material;
  }

  const group = new THREE.Group();

  function box(w, h, d, color, night, x, y, z, glow, emissiveI, nightE) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow, emissiveI, nightE));
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }

  function cyl(rTop, rBot, h, segs, color, night, x, y, z, glow, emissiveI, nightE, rx, rz) {
    const geometry = new THREE.CylinderGeometry(rTop, rBot, h, segs);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow, emissiveI, nightE));
    mesh.position.set(x, y, z);
    if (rx) mesh.rotation.x = rx;
    if (rz) mesh.rotation.z = rz;
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }

  const wallY = ground + WALL_H / 2;
  const southZ = floor.minZ - WALL_T / 2;
  const dummy = new THREE.Object3D();

  // Floor + patterned field + red carpet runner
  box(width + WALL_T * 2, 0.14, depth + WALL_T * 2, 0x1a1028, 0x241838, cx, ground + 0.04, cz);
  box(width - 0.4, 0.05, depth - 0.4, 0x2a1438, 0x3a1c48, cx, ground + 0.12, cz);
  box(2.6, 0.04, depth - 1.1, 0x8c1428, 0xa81c38, 0, ground + 0.15, cz + 0.15, 0xff4a62, 0.08, 0.16);

  const tileGeo = new THREE.BoxGeometry(0.72, 0.02, 0.72);
  geometries.push(tileGeo);
  const tileMat = mat(0x5c1830, 0x7a2440, 0xff6a78, 0.04, 0.1);
  const tiles = [];
  for (let x = -7.2; x <= 7.2; x += 1.5) {
    for (let z = -1.2; z <= 13.2; z += 1.5) {
      if (Math.abs(x) < 1.4) continue;
      tiles.push([x, z]);
    }
  }
  const tileMesh = new THREE.InstancedMesh(tileGeo, tileMat, tiles.length);
  tiles.forEach((p, i) => {
    dummy.position.set(p[0], ground + 0.14, p[1]);
    dummy.updateMatrix();
    tileMesh.setMatrixAt(i, dummy.matrix);
  });
  tileMesh.instanceMatrix.needsUpdate = true;
  group.add(tileMesh);

  const wallPurple = mat(0x3a2068, 0x4a2c78, 0x6a38a0, 0.07, 0.16);
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
  wallBox(WALL_T, WALL_H, depth + WALL_T, floor.minX - WALL_T / 2, wallY, cz);
  wallBox(WALL_T, WALL_H, depth + WALL_T, floor.maxX + WALL_T / 2, wallY, cz);
  wallBox(width + WALL_T * 2, WALL_H, WALL_T, cx, wallY, floor.maxZ + WALL_T / 2);

  const doorW = 1.55;
  const leftW = (width - doorW) / 2;
  wallBox(leftW, WALL_H, WALL_T, floor.minX + leftW / 2, wallY, southZ);
  wallBox(leftW, WALL_H, WALL_T, floor.maxX - leftW / 2, wallY, southZ);
  // lintel + neon door frame
  box(doorW + 0.2, 0.16, 0.18, 0xd4a84b, 0xd4a84b, 0, ground + 2.15, southZ + 0.04, 0xffc56a, 0.55);
  box(doorW + 0.32, 0.06, 0.08, 0xff6ad8, 0xff6ad8, 0, ground + 2.28, southZ + 0.05, 0xff4ec8, 0.7, 0.85);

  // Gold cove lighting around the ceiling
  box(width - 0.3, 0.08, 0.08, 0xd4a84b, 0xd4a84b, cx, ground + WALL_H - 0.16, floor.maxZ - 0.18, 0xffc56a, 0.4);
  box(width - 0.3, 0.06, 0.05, 0xff6ad8, 0xff6ad8, cx, ground + WALL_H - 0.28, floor.maxZ - 0.22, 0xff4ec8, 0.55, 0.8);
  box(0.08, 0.08, depth - 0.3, 0xd4a84b, 0xd4a84b, floor.minX + 0.22, ground + WALL_H - 0.16, cz, 0xffc56a, 0.35);
  box(0.08, 0.08, depth - 0.3, 0xd4a84b, 0xd4a84b, floor.maxX - 0.22, ground + WALL_H - 0.16, cz, 0xffc56a, 0.35);

  // Columns along the carpet
  const colXs = [-2.15, 2.15];
  const colZs = [1.6, 5.0, 8.4, 11.6];
  colXs.forEach((x) => {
    colZs.forEach((z) => {
      cyl(0.28, 0.32, 4.15, 8, 0xc9a24a, 0xb88838, x, ground + 2.15, z, 0xffc56a, 0.12, 0.22);
      cyl(0.38, 0.38, 0.16, 8, 0xf0d48a, 0xd4b46a, x, ground + 4.2, z, 0xffe878, 0.35, 0.5);
      cyl(0.4, 0.4, 0.14, 8, 0xf0d48a, 0xd4b46a, x, ground + 0.2, z, 0xffe878, 0.2, 0.3);
    });
  });

  function chandelier(x, z, scale, tableGlow) {
    const s = scale || 1;
    const topY = ground + WALL_H - 0.05;
    box(0.06 * s, 0.55 * s, 0.06 * s, 0xd4a84b, 0xd4a84b, x, topY - 0.35 * s, z, 0xffc56a, 0.4);
    cyl(0.55 * s, 0.22 * s, 0.16 * s, 8, 0xf0d48a, 0xe0c06a, x, topY - 0.72 * s, z, 0xffe878, 0.45, 0.7);
    cyl(0.18 * s, 0.18 * s, 0.12 * s, 8, 0xffe878, 0xffd888, x, topY - 0.55 * s, z, 0xfff0b0, 0.85, 1);
    const crystalGeo = new THREE.SphereGeometry(0.07 * s, 6, 5);
    geometries.push(crystalGeo);
    const crystalMat = mat(0xfff4c8, 0xffe8a8, 0xfff0b0, 0.95, 1);
    const n = 8;
    const crystals = new THREE.InstancedMesh(crystalGeo, crystalMat, n);
    for (let i = 0; i < n; i++) {
      const ang = (i / n) * Math.PI * 2;
      dummy.position.set(x + Math.cos(ang) * 0.42 * s, topY - 0.88 * s, z + Math.sin(ang) * 0.42 * s);
      dummy.updateMatrix();
      crystals.setMatrixAt(i, dummy.matrix);
    }
    crystals.instanceMatrix.needsUpdate = true;
    group.add(crystals);
    // baked-looking wash under the fixture (not a PointLight)
    const glowH = tableGlow ? 0.045 : 0.03;
    const glowR = (tableGlow ? 1.7 : 1.15) * s;
    cyl(glowR, glowR, glowH, 10, 0xffe8b0, 0xffd890, x, ground + 0.18, z, 0xffe878, tableGlow ? 0.55 : 0.28, tableGlow ? 0.7 : 0.4);
  }
  chandelier(0, 2.3, 1.05, false);
  chandelier(0, 6.2, 1.15, false);
  chandelier(0, 10.2, 1.0, false);
  chandelier(4.5, 4.4, 1.2, true);

  function buildSlot(sx, sz) {
    box(0.72, 1.85, 0.62, 0x2c1848, 0x221038, sx, ground + 1.05, sz);
    box(0.58, 0.7, 0.06, 0x101018, 0x101018, sx, ground + 1.22, sz + 0.3);
    box(0.5, 0.28, 0.05, 0x221828, 0x181020, sx, ground + 1.22, sz + 0.33, 0x5ae898, 0.35, 0.55);
    box(0.42, 0.16, 0.04, 0xffe878, 0xffc858, sx, ground + 1.28, sz + 0.35, 0xffe878, 0.9, 1);
    box(0.64, 0.16, 0.58, 0xd4a84b, 0xd4a84b, sx, ground + 1.92, sz, 0xffc56a, 0.55, 0.75);
    box(0.5, 0.08, 0.5, 0xff6ad8, 0xff6ad8, sx, ground + 2.02, sz, 0xff4ec8, 0.7, 0.9);
    box(0.7, 0.08, 0.34, 0x3d2468, 0x2a1848, sx, ground + 0.22, sz + 0.22);
  }
  const slotZs = [0.55, 1.85, 3.15, 4.45, 5.75, 7.05, 8.35, 9.65, 10.95];
  const slotXs = [-6.95, -5.55];
  slotXs.forEach((sx) => slotZs.forEach((sz) => buildSlot(sx, sz)));

  // Roulette
  const wheelGroup = new THREE.Group();
  wheelGroup.position.set(0, ground + 0.92, 9.55);
  group.add(wheelGroup);
  box(2.5, 0.12, 2.2, 0x3d2468, 0x2a1848, 0, ground + 0.8, 9.55);
  box(2.35, 0.05, 2.05, 0xd4a84b, 0xd4a84b, 0, ground + 0.87, 9.55, 0xffc56a, 0.25, 0.4);
  const wheelGeo = new THREE.CylinderGeometry(0.78, 0.78, 0.1, 16);
  geometries.push(wheelGeo);
  const wheelBase = new THREE.Mesh(wheelGeo, mat(0x3d2468, 0x2a1848, 0x5a3878, 0.15, 0.28));
  wheelGroup.add(wheelBase);
  const wheelTopGeo = new THREE.CylinderGeometry(0.58, 0.58, 0.07, 16);
  geometries.push(wheelTopGeo);
  const wheelTop = new THREE.Mesh(wheelTopGeo, mat(0x2a9858, 0x1a7848, 0x5ae898, 0.28, 0.45));
  wheelTop.position.y = 0.08;
  wheelGroup.add(wheelTop);
  for (let wi = 0; wi < 12; wi++) {
    const ang = (wi / 12) * Math.PI * 2;
    const color = wi % 2 === 0 ? 0xff6ad8 : 0xd4a84b;
    box(0.08, 0.06, 0.22, color, color,
      Math.cos(ang) * 0.48, ground + 1.02, 9.55 + Math.sin(ang) * 0.48, color, 0.7, 0.85);
  }

  // 21-point table (east of the aisle, under a chandelier wash)
  const tbl = room.blocks.find((b) => b.id === 'bj-table');
  const tx = (tbl.minX + tbl.maxX) / 2;
  const tz = (tbl.minZ + tbl.maxZ) / 2;
  const tw = tbl.maxX - tbl.minX;
  const td = tbl.maxZ - tbl.minZ;
  box(tw + 0.16, 0.08, td + 0.16, 0xd4a84b, 0xd4a84b, tx, ground + 0.7, tz, 0xffc56a, 0.35, 0.5);
  box(tw, 0.1, td, 0x2a9860, 0x1a7848, tx, ground + 0.78, tz, 0x7aefb0, 0.42, 0.62);
  box(tw * 0.55, 0.03, 0.08, 0xffe878, 0xffc858, tx, ground + 0.85, tz, 0xffe878, 0.7, 0.9);

  const stoolGeo = new THREE.CylinderGeometry(0.16, 0.18, 0.48, 6);
  geometries.push(stoolGeo);
  const stoolMat = mat(0xd4a84b, 0xb48838, 0xffc56a, 0.18, 0.3);
  const stoolPos = [
    [tx - 0.85, tz + 0.45],
    [tx - 0.85, tz - 0.45],
    [tx + 0.15, tz + 0.7],
    [tx + 0.15, tz - 0.7],
    [tx + 0.85, tz]
  ];
  const stools = new THREE.InstancedMesh(stoolGeo, stoolMat, stoolPos.length);
  stoolPos.forEach((p, i) => {
    dummy.position.set(p[0], ground + 0.32, p[1]);
    dummy.updateMatrix();
    stools.setMatrixAt(i, dummy.matrix);
  });
  stools.instanceMatrix.needsUpdate = true;
  group.add(stools);

  const chipBar = room.blocks.find((b) => b.id === 'chip-bar');
  const bx = (chipBar.minX + chipBar.maxX) / 2;
  const bz = (chipBar.minZ + chipBar.maxZ) / 2;
  box(chipBar.maxX - chipBar.minX, 0.9, chipBar.maxZ - chipBar.minZ, 0x3d2468, 0x2a1848, bx, ground + 0.55, bz);
  box((chipBar.maxX - chipBar.minX) + 0.08, 0.08, (chipBar.maxZ - chipBar.minZ) + 0.08, 0xd4a84b, 0xd4a84b, bx, ground + 1.02, bz, 0xffc56a, 0.35, 0.5);

  const chipGeo = new THREE.CylinderGeometry(0.14, 0.14, 0.05, 8);
  geometries.push(chipGeo);
  const chipMat = mat(0xffe878, 0xffc858, 0xffe878, 0.22, 0.35);
  const chipSpots = [
    [tx - 0.35, 0.86, tz + 0.25], [tx - 0.22, 0.90, tz + 0.22], [tx - 0.42, 0.88, tz + 0.3],
    [bx - 0.35, 1.1, bz], [bx - 0.2, 1.14, bz + 0.12], [bx + 0.15, 1.1, bz - 0.1],
    [0.35, 0.95, 9.1], [-0.28, 0.97, 9.2]
  ];
  const chips = new THREE.InstancedMesh(chipGeo, chipMat, chipSpots.length);
  chipSpots.forEach((p, i) => {
    dummy.position.set(p[0], ground + p[1], p[2]);
    dummy.updateMatrix();
    chips.setMatrixAt(i, dummy.matrix);
  });
  chips.instanceMatrix.needsUpdate = true;
  group.add(chips);

  // Wall sconces
  const sconceZs = [1.2, 4.0, 7.0, 10.2, 12.8];
  sconceZs.forEach((z) => {
    [-8.28, 8.28].forEach((x) => {
      box(0.12, 0.28, 0.18, 0xd4a84b, 0xd4a84b, x, ground + 2.35, z, 0xffc56a, 0.4, 0.6);
      cyl(0.09, 0.09, 0.12, 6, 0xffe878, 0xffd888, x + (x > 0 ? -0.1 : 0.1), ground + 2.35, z, 0xfff0b0, 0.85, 1);
    });
  });

  // North-wall sign: 神之島賭場
  const signW = 5.6;
  const signH = 1.05;
  const signMat = mat(0x2a1048, 0xffffff, 0x000000, 0, 0);
  if (typeof document !== 'undefined' && document.createElement) {
    const canvas = document.createElement('canvas');
    canvas.width = 1024;
    canvas.height = 256;
    const ctx = canvas.getContext('2d');
    if (ctx) {
      ctx.fillStyle = '#2a1048';
      ctx.fillRect(0, 0, canvas.width, canvas.height);
      ctx.strokeStyle = '#d4a84b';
      ctx.lineWidth = 14;
      ctx.strokeRect(18, 18, canvas.width - 36, canvas.height - 36);
      ctx.fillStyle = '#ffd878';
      ctx.font = 'bold 120px "Noto Sans TC", "PingFang TC", sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(HALL_NAME, canvas.width / 2, canvas.height / 2 + 6);
      const map = new THREE.CanvasTexture(canvas);
      map.colorSpace = THREE.SRGBColorSpace || THREE.sRGBEncoding;
      signMat.map = map;
      signMat.userData.dayE = 0;
      signMat.userData.nightE = 0.18;
      signMat.userData.night = new THREE.Color(0xffffff);
      signMat.emissiveMap = map;
      signMat.emissive = new THREE.Color(0xffffff);
      signMat.emissiveIntensity = 0;
      signMat.needsUpdate = true;
    }
  }
  const signGeo = new THREE.BoxGeometry(signW, signH, 0.08);
  geometries.push(signGeo);
  const signMesh = new THREE.Mesh(signGeo, signMat);
  signMesh.position.set(0, ground + 3.55, floor.maxZ - 0.12);
  group.add(signMesh);
  box(signW + 0.2, 0.08, 0.1, 0xd4a84b, 0xd4a84b, 0, ground + 4.12, floor.maxZ - 0.12, 0xffc56a, 0.5, 0.7);
  box(signW + 0.2, 0.08, 0.1, 0xff6ad8, 0xff6ad8, 0, ground + 2.98, floor.maxZ - 0.12, 0xff4ec8, 0.6, 0.85);

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
      geometries.forEach((geometry) => geometry.dispose && geometry.dispose());
      materials.forEach((material) => {
        if (material.map && material.map.dispose) material.map.dispose();
        if (material.emissiveMap && material.emissiveMap.dispose && material.emissiveMap !== material.map) {
          material.emissiveMap.dispose();
        }
        if (material.dispose) material.dispose();
      });
    }
  };
}
