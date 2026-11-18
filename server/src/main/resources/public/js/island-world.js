import * as THREE from 'three';
import { ISLANDS } from './island-layout.js';
import { G, HEX } from './island-kit.js';
import { MAIN_PAVING_SPECS } from './island-decor.js';
import {
  layInstancedPaving,
  polishMainIslandGround,
  attachMainIslandExtras
} from './island-decor-scene.js';

export function buildIslandWorld(ctx) {
  const { scene, kit } = ctx;
  const { addMesh, geo } = kit;
  const water = addMesh(scene, new THREE.CircleGeometry(180, 72), 0x45c4c6, 0, -0.74, 0, {
    night: 0x12325f,
    rot: [-Math.PI / 2, 0, 0],
    shadow: false
  });
  water.receiveShadow = true;

  function layIsland(parent, radius, theme) {
    const grassH = radius > 5 ? 0.26 : 0.18;
    const soilH = radius > 5 ? 0.18 : 0.12;
    const deepH = radius > 5 ? 0.2 : 0.12;
    const rockH = radius > 5 ? 0.36 : 0.22;
    const y0 = G - grassH;
    const top = theme === 'beach' ? 0xf0ddb4 : theme === 'city' ? 0xc8ccd0 : 0xa8d06e;
    addMesh(parent, geo('cyl', [radius, radius + 0.12, grassH, 6, HEX]), top, 0, y0 + grassH / 2, 0);
    addMesh(parent, geo('cyl', [radius + 0.16, radius + 0.32, soilH, 6, HEX]), 0xd2a56c, 0, y0 - soilH / 2, 0);
    addMesh(parent, geo('cyl', [radius + 0.36, radius + 0.55, deepH, 6, HEX]), 0xb8884e, 0, y0 - soilH - deepH / 2, 0);
    addMesh(parent, geo('cyl', [radius + 0.6, radius + 0.95, rockH, 6, HEX]), 0xe7dcd0, 0, y0 - soilH - deepH - rockH / 2, 0);
  }

  layIsland(scene, 9.15);
  polishMainIslandGround(scene, kit);
  layInstancedPaving(scene, kit, MAIN_PAVING_SPECS);
  attachMainIslandExtras(scene, kit);

  ISLANDS.forEach((island) => {
    if (island.id === 'main') return;
    const land = new THREE.Group();
    land.position.set(island.x, 0, island.z);
    layIsland(land, island.visualR, island.theme);
    scene.add(land);
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

  const sand = addMesh(scene, geo('cyl', [1, 1, 0.001, 6, HEX]), 0xf0ddb4, -4.4, G, 7.6, {
    shadow: false,
    opacity: 0,
    receive: false
  });
  sand.visible = false;

  return { water, sand };
}