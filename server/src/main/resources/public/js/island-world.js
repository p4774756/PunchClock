import * as THREE from 'three';
import { ISLANDS } from './island-layout.js';
import { G, HEX } from './island-kit.js';

export function buildIslandWorld(ctx) {
  const { scene, kit } = ctx;
  const { addMesh, addBox, geo } = kit;
  const water = addMesh(scene, new THREE.CircleGeometry(180, 72), 0x45c4c6, 0, -0.74, 0, {
    night: 0x12325f,
    rot: [-Math.PI / 2, 0, 0],
    shadow: false
  });
  water.receiveShadow = true;
  water.renderOrder = -1;

  function layIsland(parent, radius) {
    const grassH = radius > 5 ? 0.26 : 0.18;
    const soilH = radius > 5 ? 0.18 : 0.12;
    const deepH = radius > 5 ? 0.2 : 0.12;
    const rockH = radius > 5 ? 0.36 : 0.22;
    const y0 = G - grassH;
    addMesh(parent, geo('cyl', [radius, radius + 0.12, grassH, 6, HEX]), 0xa8d06e, 0, y0 + grassH / 2, 0);
    addMesh(parent, geo('cyl', [radius + 0.16, radius + 0.32, soilH, 6, HEX]), 0xd2a56c, 0, y0 - soilH / 2, 0);
    addMesh(parent, geo('cyl', [radius + 0.36, radius + 0.55, deepH, 6, HEX]), 0xb8884e, 0, y0 - soilH - deepH / 2, 0);
    addMesh(parent, geo('cyl', [radius + 0.6, radius + 0.95, rockH, 6, HEX]), 0xe7dcd0, 0, y0 - soilH - deepH - rockH / 2, 0);
  }

  layIsland(scene, 9.15);

  ISLANDS.forEach((island) => {
    if (island.id === 'main') return;
    const land = new THREE.Group();
    land.position.set(island.x, 0, island.z);
    layIsland(land, island.visualR);
    scene.add(land);
  });

  [
    [-5.4, -4.6, 2.3, 1.7, 0xc5e098],
    [5.3, -4.9, 2.0, 1.6, 0x93c462],
    [-6.1, 2.2, 1.5, 1.2, 0x7eae58],
    [0.4, -6.15, 1.7, 1.15, 0xc5e098],
    [6.1, 4.2, 1.35, 1.15, 0x93c462],
    [-2.2, 6.7, 1.2, 0.9, 0xc5e098]
  ].forEach(([x, z, rx, rz, color]) => {
    addMesh(scene, geo('cyl', [1, 1, 0.025, 6, HEX]), color, x, G + 0.012, z, {
      scale: [rx, 1, rz],
      shadow: false
    });
  });

  const rockGeo = geo('sph', [1, 5, 4]);
  function rockAt(x, z, s, sink) {
    addMesh(scene, rockGeo, s > 0.55 ? 0xe7dcd0 : 0xcfc3b2, x, (sink || -0.55) + s * 0.15, z, {
      scale: [s, s * 0.58, s * 0.86]
    });
  }
  [90, 150, 205, 250, 315].forEach((deg, i) => {
    const a = deg * Math.PI / 180;
    const r = 10.05;
    const x = Math.sin(a) * r;
    const z = Math.cos(a) * r;
    rockAt(x, z, 0.55 + (i % 3) * 0.16, -0.62);
    rockAt(x + 0.45, z - 0.2, 0.32 + (i % 2) * 0.1, -0.7);
  });
  [[-2.4, 8.7, 0.42], [3.6, 8.55, 0.34], [8.8, 3.4, 0.48], [-8.9, -2.2, 0.4], [7.2, -6.4, 0.36]].forEach(([x, z, s]) => {
    rockAt(x, z, s, -0.78);
  });

  const sand = addMesh(scene, geo('cyl', [1.7, 1.95, 0.1, 6, HEX]), 0xf0ddb4, -4.4, -0.66, 7.6, { shadow: false });
  sand.receiveShadow = true;

  function paving(x, z, w, d, rotY) {
    const extra = { shadow: false };
    if (rotY) extra.rot = [0, rotY, 0];
    addBox(scene, w + 0.16, 0.04, d + 0.16, 0xe4d2b2, x, G + 0.012, z, extra);
    addBox(scene, w, 0.05, d, 0xf4e6cf, x, G + 0.034, z, extra);
  }

  paving(0, 3.05, 6.5, 4.35);
  paving(0, 6.15, 1.9, 2.55);
  paving(-4.15, 3.55, 2.5, 1.7);
  paving(4.35, 3.45, 2.3, 1.65);
  paving(-3.55, 0.55, 1.55, 2.7);
  paving(3.7, 0.7, 1.7, 2.5);
  paving(0, -4.7, 7.4, 1.2);
  paving(5.15, 5.55, 2.6, 1.15, -0.55);

  return { water: water, sand: sand };
}
