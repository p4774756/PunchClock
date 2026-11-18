// 本島精緻化場景物件（瀏覽器／Three.js）；純函式見 island-decor.js。
import * as THREE from 'three';
import { G, HEX } from './island-kit.js';
import {
  DECOR_GRASS,
  DECOR_SAND,
  DECOR_STONE,
  MAIN_EXTRA_BENCHES,
  MAIN_EXTRA_FLOWER_BEDS,
  flowerBedStemLayout,
  pavingTileLayout,
  stringLightBulbLayout
} from './island-decor.js';

function mulberry32(seed) {
  let a = seed >>> 0;
  return function next() {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

function createTerrainNoiseMap(kind, size, seed) {
  const px = size == null ? 64 : size;
  const board = document.createElement('canvas');
  board.width = px;
  board.height = px;
  const ctx = board.getContext('2d');
  const rnd = mulberry32(seed == null ? 146 : seed);
  const imageData = ctx.createImageData(px, px);
  const data = imageData.data;
  for (let y = 0; y < px; y++) {
    for (let x = 0; x < px; x++) {
      const i = (y * px + x) * 4;
      const n = rnd();
      if (kind === 'grass') {
        data[i] = 140 + Math.floor(n * 35);
        data[i + 1] = 185 + Math.floor(n * 28);
        data[i + 2] = 95 + Math.floor(n * 22);
      } else if (kind === 'sand') {
        data[i] = 228 + Math.floor(n * 18);
        data[i + 1] = 205 + Math.floor(n * 22);
        data[i + 2] = 158 + Math.floor(n * 20);
      } else {
        data[i] = 228 + Math.floor(n * 20);
        data[i + 1] = 210 + Math.floor(n * 18);
        data[i + 2] = 178 + Math.floor(n * 24);
      }
      data[i + 3] = 255;
    }
  }
  ctx.putImageData(imageData, 0, 0);
  return board;
}

function terrainMapToTexture(canvas) {
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  texture.wrapS = THREE.RepeatWrapping;
  texture.wrapT = THREE.RepeatWrapping;
  texture.repeat.set(6, 6);
  texture.anisotropy = 4;
  return texture;
}

export function layInstancedPaving(scene, kit, specs) {
  const { geo, mat } = kit;
  const brickGeo = geo('box', [0.4, 0.05, 0.36]);
  const edgeGeo = geo('box', [1, 0.04, 1]);
  const dummy = new THREE.Object3D();
  specs.forEach((spec, specIndex) => {
    const tiles = pavingTileLayout(spec.w, spec.d, 0.4, 0.36, specIndex + 1);
    const byColor = new Map();
    tiles.forEach((tile) => {
      if (!byColor.has(tile.color)) byColor.set(tile.color, []);
      byColor.get(tile.color).push(tile);
    });
    const group = new THREE.Group();
    group.position.set(spec.x, G + 0.034, spec.z);
    if (spec.rotY) group.rotation.y = spec.rotY;
    byColor.forEach((list, color) => {
      const material = mat(color, { shadow: false });
      const mesh = new THREE.InstancedMesh(brickGeo, material, list.length);
      mesh.castShadow = false;
      mesh.receiveShadow = true;
      for (let i = 0; i < list.length; i++) {
        const tile = list[i];
        dummy.position.set(tile.x, 0, tile.z);
        dummy.rotation.set(0, tile.rotY || 0, 0);
        dummy.scale.set(1, 1, 1);
        dummy.updateMatrix();
        mesh.setMatrixAt(i, dummy.matrix);
      }
      mesh.instanceMatrix.needsUpdate = true;
      group.add(mesh);
    });
    const edgeMat = mat(DECOR_STONE.edge, { shadow: false });
    const edge = new THREE.Mesh(edgeGeo, edgeMat);
    edge.scale.set(spec.w + 0.16, 1, spec.d + 0.16);
    edge.position.y = -0.022;
    edge.castShadow = false;
    group.add(edge);
    scene.add(group);
  });
}

export function polishMainIslandGround(scene, kit) {
  const { geo, addMesh } = kit;
  const grassMap = terrainMapToTexture(createTerrainNoiseMap('grass', 64, 1461));
  const sandMap = terrainMapToTexture(createTerrainNoiseMap('sand', 64, 1462));
  sandMap.repeat.set(4, 4);

  addMesh(scene, geo('cyl', [9.15, 9.27, 0.026, 6, HEX]), DECOR_GRASS.top, 0, G + 0.201, 0, {
    map: grassMap,
    shadow: false,
    receive: true
  });

  addMesh(scene, geo('cyl', [1.7, 1.95, 0.102, 6, HEX]), DECOR_SAND.top, -4.4, G - 0.649, 7.6, {
    map: sandMap,
    shadow: false,
    receive: true
  });

  const patches = [
    [-5.4, -4.6, 2.3, 1.7, DECOR_GRASS.bright],
    [5.3, -4.9, 2.0, 1.6, DECOR_GRASS.patch],
    [-6.1, 2.2, 1.5, 1.2, DECOR_GRASS.patch],
    [0.4, -6.15, 1.7, 1.15, DECOR_GRASS.bright],
    [6.1, 4.2, 1.35, 1.15, DECOR_GRASS.patch],
    [-2.2, 6.7, 1.2, 0.9, DECOR_GRASS.bright]
  ];
  const patchGeo = geo('cyl', [1, 1, 0.028, 6, HEX]);
  patches.forEach(([x, z, rx, rz, color], idx) => {
    const patchMap = terrainMapToTexture(createTerrainNoiseMap('grass', 48, 2000 + idx));
    addMesh(scene, patchGeo, color, x, G + 0.014, z, {
      map: patchMap,
      scale: [rx, 1, rz],
      shadow: false
    });
  });
}

export function attachParkBench(scene, kit, x, z, rotY) {
  const { addBox } = kit;
  const group = new THREE.Group();
  group.position.set(x, G, z);
  group.rotation.y = rotY || 0;
  addBox(group, 1.05, 0.07, 0.34, 0xc4844a, 0, 0.4, 0);
  addBox(group, 1.05, 0.32, 0.06, 0xa56b38, 0, 0.58, -0.14);
  addBox(group, 0.06, 0.4, 0.28, 0xa56b38, -0.42, 0.2, 0);
  addBox(group, 0.06, 0.4, 0.28, 0xa56b38, 0.42, 0.2, 0);
  addBox(group, 1.08, 0.05, 0.36, 0xf3e2cc, 0, 0.08, 0);
  scene.add(group);
}

export function attachFlowerBedInstanced(scene, kit, cx, cz, w, d, seed) {
  const { geo, mat, addBox } = kit;
  const stems = flowerBedStemLayout(w, d, 6, seed);
  addBox(scene, w, 0.16, d, 0xb8884e, cx, G + 0.08, cz);
  const stemGeo = geo('sph', [0.16, 5, 4]);
  const stemMat = mat(0x5e9a48, { shadow: false });
  const stemMesh = new THREE.InstancedMesh(stemGeo, stemMat, stems.length);
  const petalGroups = new Map();
  stems.forEach((stem) => {
    if (!petalGroups.has(stem.petal)) petalGroups.set(stem.petal, []);
    petalGroups.get(stem.petal).push({ x: cx + stem.x, z: cz + stem.z });
  });
  const dummy = new THREE.Object3D();
  for (let i = 0; i < stems.length; i++) {
    const stem = stems[i];
    dummy.position.set(cx + stem.x, G + 0.2, cz + stem.z);
    dummy.scale.set(0.7, 0.45, 0.7);
    dummy.updateMatrix();
    stemMesh.setMatrixAt(i, dummy.matrix);
  }
  stemMesh.instanceMatrix.needsUpdate = true;
  scene.add(stemMesh);
  petalGroups.forEach((list, color) => {
    const petalMat = mat(color, { shadow: false });
    const petalMesh = new THREE.InstancedMesh(stemGeo, petalMat, list.length);
    for (let j = 0; j < list.length; j++) {
      const p = list[j];
      dummy.position.set(p.x, G + 0.32, p.z);
      dummy.scale.set(0.85, 0.7, 0.85);
      dummy.updateMatrix();
      petalMesh.setMatrixAt(j, dummy.matrix);
    }
    petalMesh.instanceMatrix.needsUpdate = true;
    scene.add(petalMesh);
  });
}

function paintSmallTradSign(kit, text, w, h) {
  return kit.paint(w || 256, h || 128, (g, bw, bh) => {
    g.fillStyle = '#fff7ef';
    g.fillRect(0, 0, bw, bh);
    g.strokeStyle = '#f06a2a';
    g.lineWidth = 6;
    g.strokeRect(8, 8, bw - 16, bh - 16);
    g.fillStyle = '#1f4d73';
    g.font = '700 42px "PingFang TC", "Heiti TC", "Microsoft JhengHei", sans-serif';
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.fillText(String(text || ''), bw / 2, bh / 2 + 2);
  });
}

export function attachShopEavesDecor(model, kit, opts) {
  opts = opts || {};
  const { geo, mat, lampLight } = kit;
  const reduced = !!opts.reduced;
  const roofY = opts.roofY == null ? 2.34 : opts.roofY;
  const frontZ = opts.frontZ == null ? 1.05 : opts.frontZ;
  const span = opts.span == null ? 4.35 : opts.span;

  const bulbs = stringLightBulbLayout(span, 14);
  const bulbGeo = geo('sph', [0.035, 6, 5]);
  const bulbMat = mat(0xfff6d4, {
    night: 0xffe2b0,
    glow: 0xffc56a,
    glowAmount: 1.02,
    shadow: false
  });
  bulbMat.userData.dayE = 0.05;
  bulbMat.userData.nightE = 1.02;
  const inst = new THREE.InstancedMesh(bulbGeo, bulbMat, bulbs.length);
  inst.castShadow = false;
  inst.userData.stringLight = true;
  const dummy = new THREE.Object3D();
  bulbs.forEach((b, i) => {
    dummy.position.set(b.x, roofY + 0.08, frontZ + 0.12);
    dummy.updateMatrix();
    inst.setMatrixAt(i, dummy.matrix);
  });
  inst.instanceMatrix.needsUpdate = true;
  model.add(inst);

  const wireGeo = geo('box', [span, 0.012, 0.012]);
  const wireMat = mat(0x3a312c, { shadow: false });
  const wire = new THREE.Mesh(wireGeo, wireMat);
  wire.position.set(0, roofY + 0.11, frontZ + 0.1);
  model.add(wire);

  lampLight(0, roofY + 0.05, frontZ + 0.35, 7, model, { localHalo: true });

  [
    { text: '今日特調', x: -1.55, z: frontZ + 0.22 },
    { text: '新鮮水果', x: 1.45, z: frontZ + 0.18 }
  ].forEach((spec) => {
    const tex = paintSmallTradSign(kit, spec.text, 220, 110);
    const plane = new THREE.Mesh(
      new THREE.PlaneGeometry(0.55, 0.28),
      mat(0xffffff, {
        map: tex,
        night: 0xfff0dc,
        glow: 0xffb15a,
        glowAmount: 0.35,
        side: THREE.DoubleSide,
        shadow: false
      })
    );
    plane.position.set(spec.x, roofY - 0.05, spec.z);
    plane.rotation.y = Math.PI;
    model.add(plane);
  });

  inst.userData.reduced = reduced;
  return inst;
}

export function attachMainIslandExtras(scene, kit) {
  MAIN_EXTRA_BENCHES.forEach((b) => attachParkBench(scene, kit, b.x, b.z, b.rotY));
  MAIN_EXTRA_FLOWER_BEDS.forEach((bed, i) => {
    attachFlowerBedInstanced(scene, kit, bed.x, bed.z, bed.w, bed.d, 1460 + i);
  });
}