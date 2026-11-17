import * as THREE from 'three';
import { islandById, THEME_CIRCLES } from './island-layout.js';
import { G } from './island-kit.js';

/** 沙灘島道具：躺椅、傘、小燈、貝殼與小石。碰撞見 THEME_CIRCLES。 */
export function buildIslandBeach(ctx) {
  const { scene, kit } = ctx;
  const { addMesh, addBox, geo } = kit;
  const beach = islandById('beach');
  if (!beach) return;

  function umbrella(x, z, color, scale) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.scale.setScalar(scale || 1);
    addMesh(group, geo('cyl', [0.035, 0.04, 1.45, 5, 0]), 0xfff7ef, 0, 0.72, 0);
    addMesh(group, geo('cone', [0.92, 0.36, 8]), color, 0, 1.5, 0);
    addMesh(group, geo('sph', [0.07, 5, 4]), 0xfffaf6, 0, 1.7, 0, { shadow: false });
    scene.add(group);
  }

  function streetLamp(x, z) {
    addMesh(scene, geo('cyl', [0.04, 0.05, 1.35, 5, 0]), 0x1f4d73, x, G + 0.68, z);
    addBox(scene, 0.26, 0.08, 0.26, 0xf3e2cc, x, G + 1.38, z);
    addMesh(scene, geo('sph', [0.09, 6, 5]), 0xfff6d4, x, G + 1.28, z, {
      night: 0xffe2b0,
      glow: 0xffc56a,
      glowAmount: 1.15,
      shadow: false
    });
  }

  function lounger(x, z, rotY) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.rotation.y = rotY || 0;
    addBox(group, 0.52, 0.05, 1.15, 0xf3e2cc, 0, 0.22, 0);
    addBox(group, 0.52, 0.28, 0.06, 0xf06a2a, 0, 0.38, -0.48);
    addBox(group, 0.48, 0.04, 0.72, 0xf06a2a, 0, 0.28, 0.08);
    [[-0.2, -0.48], [0.2, -0.48], [-0.2, 0.48], [0.2, 0.48]].forEach(([ox, oz]) => {
      addBox(group, 0.05, 0.2, 0.05, 0xa56b38, ox, 0.1, oz);
    });
    scene.add(group);
  }

  const byId = {};
  for (let i = 0; i < THEME_CIRCLES.length; i++) {
    const prop = THEME_CIRCLES[i];
    byId[prop.id] = prop;
  }

  const L1 = byId['lounger-1'];
  const L2 = byId['lounger-2'];
  const L3 = byId['lounger-3'];
  const umb = byId['beach-umbrella'];
  const lamp = byId['beach-lamp'];

  if (L1) lounger(L1.x, L1.z, 0.55);
  if (L2) lounger(L2.x, L2.z, 0.35);
  if (L3) lounger(L3.x, L3.z, 0.75);
  if (umb) umbrella(umb.x, umb.z, 0xf6d35a, 0.95);
  if (lamp) streetLamp(lamp.x, lamp.z);

  // 貝殼／小石點綴（無碰撞）
  const shellGeo = geo('sph', [0.08, 5, 4]);
  [
    [1.8, -1.4, 0.9],
    [-0.6, -2.2, 0.75],
    [2.6, 1.1, 0.85],
    [-2.8, 0.4, 0.7],
    [0.9, -2.6, 0.8]
  ].forEach(([dx, dz, s], i) => {
    const color = i % 2 ? 0xfff7ef : 0xe7dcd0;
    addMesh(scene, shellGeo, color, beach.x + dx, G + 0.02, beach.z + dz, {
      scale: [s, s * 0.45, s * 0.8],
      shadow: false
    });
  });
  [
    [-1.8, 2.2, 0.28],
    [2.9, -0.5, 0.22],
    [-3.1, -0.8, 0.2]
  ].forEach(([dx, dz, s]) => {
    addMesh(scene, geo('sph', [1, 5, 4]), 0xcfc3b2, beach.x + dx, G + s * 0.12, beach.z + dz, {
      scale: [s, s * 0.55, s * 0.8],
      shadow: false
    });
  });
}
