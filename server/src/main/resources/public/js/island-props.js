import * as THREE from 'three';
import { BRIDGES, BOAT_STOPS, THEME_CIRCLES } from './island-layout.js';
import { G, P } from './island-kit.js';

export function buildIslandProps(ctx) {
  const { scene, kit, bobs, clouds, swimmers } = ctx;
  const { addMesh, addBox, geo, lampLight, addCup } = kit;
  function treeRound(x, z, scale, leaf) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.scale.setScalar(scale);
    addMesh(group, geo('cyl', [0.09, 0.13, 0.85, 6, 0]), 0xc4844a, 0, 0.42, 0, { shadow: false });
    addMesh(group, geo('sph', [0.58, 6, 5]), leaf, 0, 1.2, 0, { scale: [1.05, 0.82, 1.05], shadow: false });
    addMesh(group, geo('sph', [0.42, 6, 5]), leaf, 0.36, 1.0, 0.08, { scale: [1, 0.8, 1], shadow: false });
    addMesh(group, geo('sph', [0.36, 6, 5]), leaf, -0.32, 1.05, -0.06, { scale: [1, 0.75, 1], shadow: false });
    scene.add(group);
  }
  function treeCone(x, z, scale, leaf) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.scale.setScalar(scale);
    addMesh(group, geo('cyl', [0.08, 0.12, 0.7, 5, 0]), 0xa56b38, 0, 0.35, 0, { shadow: false });
    addMesh(group, geo('cone', [0.62, 0.7, 7]), leaf, 0, 0.95, 0, { shadow: false });
    addMesh(group, geo('cone', [0.46, 0.55, 7]), leaf, 0, 1.4, 0, { shadow: false });
    scene.add(group);
  }
  function treeLayer(x, z, scale, leaf) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.scale.setScalar(scale);
    addMesh(group, geo('cyl', [0.08, 0.11, 0.95, 5, 0]), 0xc4844a, 0, 0.48, 0, { shadow: false });
    addMesh(group, geo('sph', [0.7, 6, 5]), leaf, 0, 0.95, 0, { scale: [1, 0.42, 1], shadow: false });
    addMesh(group, geo('sph', [0.5, 6, 5]), leaf, 0, 1.28, 0, { scale: [1, 0.45, 1], shadow: false });
    addMesh(group, geo('sph', [0.32, 6, 5]), leaf, 0, 1.55, 0, { scale: [1, 0.5, 1], shadow: false });
    scene.add(group);
  }
  [
    [-6.25, -5.45, 'round', 1.22, 0x6fa84e],
    [-4.5, -6.25, 'cone', 1.05, 0x4f8a45],
    [5.7, -5.65, 'layer', 1.18, 0x8fbf62],
    [6.45, -3.15, 'round', 0.95, 0x6fa84e],
    [-6.55, -1.35, 'cone', 1.12, 0x5e9a48],
    [-6.35, 4.55, 'round', 0.98, 0x4f8a45],
    [6.25, 5.15, 'layer', 0.9, 0x6fa84e],
    [1.7, -6.35, 'cone', 0.82, 0x8fbf62],
    [-1.7, -6.45, 'round', 0.78, 0x6fa84e]
  ].forEach(([x, z, kind, scale, leaf]) => {
    if (kind === 'round') treeRound(x, z, scale, leaf);
    else if (kind === 'cone') treeCone(x, z, scale, leaf);
    else treeLayer(x, z, scale, leaf);
  });

  const bushGeo = geo('sph', [0.42, 6, 5]);
  function bush(x, z, s, color) {
    addMesh(scene, bushGeo, color, x, G + 0.2 * s, z, { scale: [s, s * 0.72, s], shadow: false });
  }
  [
    [-5.2, -3.2, 0.85, 0x7eae58],
    [-3.4, -5.5, 0.6, 0x8fbf62],
    [4.4, -5.6, 0.7, 0x6fa84e],
    [6.6, -4.4, 0.55, 0x8fbf62],
    [-5.6, 5.5, 0.5, 0x7eae58],
    [5.5, 5.6, 0.48, 0x6fa84e],
    [-6.5, 0.4, 0.62, 0x5e9a48],
    [2.6, -5.4, 0.45, 0x8fbf62]
  ].forEach(([x, z, s, color]) => bush(x, z, s, color));

  const flowerGeo = geo('sph', [0.16, 5, 4]);
  function bed(x, z, w, d) {
    addBox(scene, w, 0.16, d, 0xb8884e, x, G + 0.08, z);
    const colors = [0xf4a0c0, 0xf6d35a, 0xfffaf6, 0xf06a2a, 0xf4a0c0];
    for (let i = 0; i < 5; i++) {
      const fx = x + (i - 2) * w * 0.18;
      const fz = z + ((i % 2) - 0.5) * d * 0.28;
      addMesh(scene, flowerGeo, 0x5e9a48, fx, G + 0.2, fz, { scale: [0.7, 0.45, 0.7], shadow: false });
      addMesh(scene, flowerGeo, colors[i], fx, G + 0.32, fz, { scale: [0.85, 0.7, 0.85], shadow: false });
    }
  }
  bed(-1.85, 6.35, 1.15, 0.7);
  bed(1.85, 6.35, 1.15, 0.7);
  bed(0, -6.15, 2.4, 0.65);
  bed(-5.7, -5.9, 0.9, 0.55);
  bed(3.55, -3.65, 1.25, 0.7);

  function fence(x1, z1, x2, z2, h) {
    const dx = x2 - x1;
    const dz = z2 - z1;
    const len = Math.hypot(dx, dz);
    const ry = Math.atan2(-dz, dx);
    addBox(scene, len, h, 0.12, 0xfff7ef, (x1 + x2) / 2, G + h / 2, (z1 + z2) / 2, { rot: [0, ry, 0] });
    [0, 1].forEach((t) => {
      addBox(scene, 0.14, h + 0.12, 0.14, 0xf3e2cc, x1 + dx * t, G + (h + 0.12) / 2, z1 + dz * t);
    });
  }
  fence(-6.3, -6.55, 6.3, -6.55, 0.42);
  fence(-6.3, -6.55, -6.3, -3.6, 0.42);
  fence(6.3, -6.55, 6.3, -3.4, 0.42);
  fence(-6.5, 2.3, -6.5, 4.7, 0.36);

  function table(x, z) {
    addBox(scene, 0.78, 0.07, 0.78, 0xfff7ef, x, G + 0.56, z);
    [[-0.28, -0.28], [0.28, -0.28], [-0.28, 0.28], [0.28, 0.28]].forEach(([a, b]) => {
      addBox(scene, 0.06, 0.52, 0.06, 0xc4844a, x + a, G + 0.26, z + b);
    });
  }
  function chair(x, z, rotY) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.rotation.y = rotY;
    addBox(group, 0.36, 0.05, 0.36, 0xc4844a, 0, 0.4, 0);
    addBox(group, 0.36, 0.32, 0.05, 0xfff7ef, 0, 0.58, -0.16);
    [[-0.14, -0.14], [0.14, -0.14], [-0.14, 0.14], [0.14, 0.14]].forEach(([a, b]) => {
      addBox(group, 0.045, 0.38, 0.045, 0xa56b38, a, 0.19, b);
    });
    scene.add(group);
  }
  function umbrella(x, z, color, scale) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.scale.setScalar(scale || 1);
    addMesh(group, geo('cyl', [0.035, 0.04, 1.45, 5, 0]), 0xfff7ef, 0, 0.72, 0);
    addMesh(group, geo('cone', [0.92, 0.36, 8]), color, 0, 1.5, 0);
    addMesh(group, geo('sph', [0.07, 5, 4]), 0xfffaf6, 0, 1.7, 0, { shadow: false });
    scene.add(group);
  }
  function bench(x, z, rotY) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.rotation.y = rotY || 0;
    addBox(group, 1.05, 0.07, 0.34, 0xc4844a, 0, 0.4, 0);
    addBox(group, 1.05, 0.32, 0.06, 0xa56b38, 0, 0.58, -0.14);
    [-0.42, 0.42].forEach((xOff) => {
      addBox(group, 0.06, 0.4, 0.28, 0xa56b38, xOff, 0.2, 0);
    });
    scene.add(group);
  }

  table(-4.85, 3.55);
  umbrella(-4.85, 3.55, 0xf6d35a, 1);
  chair(-4.85, 4.15, Math.PI);
  chair(-4.85, 2.95, 0);
  addCup(scene, 0xf4a0c0, -5.05, G + 0.6, 3.45, 0.4);
  addCup(scene, 0xf6d35a, -4.6, G + 0.6, 3.68, 1.1);

  table(4.55, 3.5);
  umbrella(4.55, 3.5, 0xf06a2a, 0.92);
  chair(4.55, 4.08, Math.PI);
  chair(5.15, 3.5, -Math.PI / 2);
  addCup(scene, 0x7ec8ea, 4.4, G + 0.6, 3.4, 0.2);

  bench(2.15, 4.85, 0.2);

  function bin(x, z) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    addMesh(group, geo('cyl', [0.16, 0.18, 0.4, 6, 0]), 0x1f4d73, 0, 0.22, 0);
    addMesh(group, geo('cyl', [0.18, 0.18, 0.06, 6, 0]), 0xf3e2cc, 0, 0.44, 0);
    scene.add(group);
  }
  bin(2.55, 4.35);
  bin(-2.7, -2.55);
  addBox(scene, 0.38, 0.32, 0.38, 0xc4844a, -1.7, G + 0.16, -2.55);
  addBox(scene, 0.32, 0.28, 0.32, 0xa56b38, -1.35, G + 0.42, -2.45);

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
  [[-1.6, 5.55], [1.6, 5.55], [-5.45, 1.15], [5.45, 2.35], [1.35, 8.15], [-3.3, -4.7]].forEach(([x, z]) => {
    streetLamp(x, z);
  });
  lampLight(-1.6, G + 1.35, 5.55, 12);
  lampLight(1.6, G + 1.35, 5.55, 12);
  lampLight(5.45, G + 1.3, 2.35, 8);
  lampLight(1.35, G + 1.25, 8.15, 8, null, { realLight: true });

  function bicycle(x, z, rotY) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.rotation.y = rotY;
    const wheel = geo('cyl', [0.26, 0.26, 0.04, 8, 0]);
    const w1 = addMesh(group, wheel, 0xd5dde4, 0, 0.26, 0.4);
    w1.rotation.x = Math.PI / 2;
    const w2 = addMesh(group, wheel, 0xd5dde4, 0, 0.26, -0.4);
    w2.rotation.x = Math.PI / 2;
    addBox(group, 0.05, 0.05, 0.8, 0x1f4d73, 0, 0.36, 0);
    addBox(group, 0.05, 0.32, 0.05, 0x1f4d73, 0, 0.4, 0.4);
    addBox(group, 0.05, 0.28, 0.05, 0x1f4d73, 0, 0.46, -0.12);
    addBox(group, 0.12, 0.045, 0.16, 0xf06a2a, 0, 0.62, -0.12);
    addBox(group, 0.26, 0.04, 0.04, 0xd5dde4, 0, 0.58, 0.46);
    scene.add(group);
  }
  bicycle(-5.7, 5.15, 0.4);

  function spanDeck(x1, z1, x2, z2, width, color, y) {
    const dx = x2 - x1;
    const dz = z2 - z1;
    const len = Math.hypot(dx, dz) || 0.001;
    const ry = Math.atan2(-dz, dx);
    addBox(scene, len, 0.08, width, color, (x1 + x2) / 2, y, (z1 + z2) / 2, { rot: [0, ry, 0] });
    return { ry: ry, len: len, zx: Math.sin(ry), zz: Math.cos(ry) };
  }

  for (let i = 0; i < 4; i++) {
    addBox(scene, 1.5, 0.08, 0.4, i % 2 ? 0xc4844a : 0xa56b38, -0.1, P, 7.45 + i * 0.42);
  }
  [[-0.8, 7.35], [0.6, 7.35], [-0.8, 8.75], [0.6, 8.75]].forEach(([x, z]) => {
    addBox(scene, 0.1, 1.2, 0.1, 0xa56b38, x, P - 0.52, z);
  });
  spanDeck(-0.8, 7.4, -0.8, 8.7, 0.06, 0xfff7ef, P + 0.38);
  spanDeck(0.6, 7.4, 0.6, 8.7, 0.06, 0xfff7ef, P + 0.38);
  const buoy = addMesh(scene, new THREE.TorusGeometry(0.16, 0.04, 5, 10), 0xf06a2a, 0.6, P + 0.32, 8.15);
  buoy.rotation.y = 0.5;

  const berth = BOAT_STOPS.main.berth;
  const boat = new THREE.Group();
  addBox(boat, 1.2, 0.26, 0.48, 0xfff7ef, 0, 0.16, 0);
  addBox(boat, 0.7, 0.16, 0.32, 0xf06a2a, -0.05, 0.32, 0);
  addMesh(boat, geo('cone', [0.24, 0.36, 4]), 0xfff7ef, 0, 0.16, 0.55, { rot: [Math.PI / 2, 0, 0] });
  boat.position.set(berth.x, -0.62, berth.z);
  boat.rotation.y = berth.yaw;
  scene.add(boat);
  lampLight(0.55, 0.22, 0.62, 5, boat, { realLight: true });

  function bridge(x1, z1, x2, z2, width) {
    const deck = width || 1.15;
    const rail = deck / 2 - 0.06;
    const dx = x2 - x1;
    const dz = z2 - z1;
    const len = Math.hypot(dx, dz) || 0.001;
    const info = spanDeck(x1, z1, x2, z2, deck, 0xc4844a, P);
    [-1, 1].forEach((side) => {
      addBox(scene, len, 0.05, 0.05, 0xfff7ef,
        (x1 + x2) / 2 + info.zx * rail * side,
        P + 0.38,
        (z1 + z2) / 2 + info.zz * rail * side,
        { rot: [0, info.ry, 0] });
    });
    const posts = Math.max(2, Math.round(len / 1.15));
    for (let i = 0; i <= posts; i++) {
      const t = i / posts;
      [-1, 1].forEach((side) => {
        addBox(scene, 0.06, 0.46, 0.06, 0xf3e2cc,
          x1 + dx * t + info.zx * rail * side,
          P + 0.18,
          z1 + dz * t + info.zz * rail * side);
      });
    }
    [0.08, 0.92].forEach((t) => {
      addBox(scene, 0.12, 1.15, 0.12, 0xa56b38, x1 + dx * t, P - 0.5, z1 + dz * t);
    });
  }
  BRIDGES.forEach((span) => bridge(span.ax, span.az, span.bx, span.bz, span.halfWidth * 2));
  spanDeck(6.4, 1.95, 9.4, 1.95, 1.4, 0xc4844a, P);
  spanDeck(17.4, BOAT_STOPS.beach.land.z, 15.7, BOAT_STOPS.beach.land.z, 1.2, 0xc4844a, P);

  function railing(x1, z1, x2, z2) {
    const dx = x2 - x1;
    const dz = z2 - z1;
    const len = Math.hypot(dx, dz) || 0.001;
    const ry = Math.atan2(-dz, dx);
    const n = Math.max(2, Math.round(len / 0.85));
    for (let i = 0; i <= n; i++) {
      const t = i / n;
      addBox(scene, 0.06, 0.48, 0.06, 0xfff7ef, x1 + dx * t, G + 0.26, z1 + dz * t);
    }
    addBox(scene, len, 0.045, 0.045, 0xf3e2cc, (x1 + x2) / 2, G + 0.48, (z1 + z2) / 2, { rot: [0, ry, 0] });
  }
  railing(-4.4, 7.55, -1.15, 7.55);
  railing(1.05, 7.55, 4.3, 7.55);

  function hill(x, z, radius, height, color) {
    addMesh(scene, geo('cone', [1, 1, 6]), color, x, -0.9 + height / 2, z, {
      scale: [radius, height, radius],
      shadow: false,
      receive: false
    });
  }
  hill(-34, -26, 6.5, 2.6, 0xc9ddd4);
  hill(38, -16, 5.2, 2.2, 0xd7e6ee);
  hill(-26, 36, 4.6, 1.9, 0xd5e4c8);
  hill(18, 40, 4.2, 1.7, 0xd7e6ee);
  hill(42, 18, 3.6, 1.5, 0xc9ddd4);

  function palmAt(x, z) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    addMesh(group, geo('cyl', [0.07, 0.11, 1.55, 5, 0]), 0xc4844a, 0, 0.78, 0);
    for (let i = 0; i < 5; i++) {
      const a = (i / 5) * Math.PI * 2;
      const frond = addMesh(group, geo('cone', [0.07, 0.85, 4]), 0x6fa84e, Math.cos(a) * 0.22, 1.55, Math.sin(a) * 0.22);
      frond.rotation.z = Math.cos(a) * 0.9;
      frond.rotation.x = Math.sin(a) * 0.9;
    }
    scene.add(group);
  }
  function bambooAt(x, z) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    [-0.14, 0.02, 0.16].forEach((dx, i) => {
      addMesh(group, geo('cyl', [0.035, 0.045, 1.45 + i * 0.28, 5, 0]), 0x7eae58, dx, 0.75, (i - 1) * 0.07);
    });
    scene.add(group);
  }
  function dishAt(x, z) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    addBox(group, 0.5, 0.1, 0.5, 0x5d7384, 0, 0.05, 0);
    addBox(group, 0.09, 3.15, 0.09, 0xd5dde4, 0, 1.62, 0);
    const dish = addMesh(group, geo('cyl', [0.42, 0.1, 0.08, 8, 0]), 0xf3e2cc, 0.28, 2.95, 0);
    dish.rotation.z = -0.85;
    addMesh(group, geo('sph', [0.1, 6, 5]), 0x7ec8ea, 0, 3.35, 0, {
      night: 0xd7f4ff,
      glow: 0x7ec8ea,
      glowAmount: 1.35,
      shadow: false
    });
    addBox(group, 0.04, 0.03, 0.7, 0x3a312c, 0.16, 0.07, -0.42, { shadow: false });
    lampLight(0, 3.2, 0, 7, group);
    scene.add(group);
  }
  function figureAt(x, z) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    const gold = 0xd4a84b;
    addBox(group, 0.62, 0.12, 0.62, 0x6b3a2a, 0, 0.06, 0);
    addBox(group, 0.42, 0.1, 0.42, gold, 0, 0.16, 0);
    addBox(group, 0.11, 1.05, 0.11, gold, -0.1, 0.72, 0);
    addBox(group, 0.11, 1.05, 0.11, gold, 0.1, 0.72, 0);
    addBox(group, 0.4, 0.78, 0.2, gold, 0, 1.52, 0);
    addBox(group, 0.09, 0.62, 0.09, gold, -0.26, 1.58, 0);
    addBox(group, 0.09, 0.62, 0.09, gold, 0.26, 1.58, 0);
    addMesh(group, geo('sph', [0.2, 7, 6]), gold, 0, 2.12, 0);
    [[0, 2.28, 0.12], [0, 1.62, 0.14], [-0.1, 1.38, 0.12], [0.1, 1.38, 0.12]].forEach((point) => {
      addMesh(group, geo('sph', [0.03, 5, 4]), 0xfff1c4, point[0], point[1], point[2], {
        night: 0xffe7a8,
        glow: 0xffd27a,
        glowAmount: 0.85,
        shadow: false
      });
    });
    lampLight(0, 1.3, 0.15, 5, group);
    scene.add(group);
  }
  function herbAt(x, z) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    addBox(group, 0.62, 0.08, 0.42, 0x6b4a32, 0, 0.04, 0);
    addBox(group, 0.54, 0.06, 0.34, 0x4f8a45, 0, 0.1, 0);
    [[-0.14, 0.06], [0.04, -0.05], [0.15, 0.07]].forEach((spot, i) => {
      addMesh(group, geo('cone', [0.07, 0.2 + i * 0.03, 5]), i === 1 ? 0x7eae58 : 0x3f7a3a, spot[0], 0.22, spot[1]);
    });
    scene.add(group);
  }
  function rackAt(x, z) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    addBox(group, 0.38, 0.64, 0.26, 0x24384c, 0, 0.36, 0);
    addBox(group, 0.26, 0.07, 0.02, 0x7ec8ea, 0, 0.5, 0.14, {
      night: 0xd7f4ff,
      glow: 0x7ec8ea,
      glowAmount: 1.1,
      shadow: false
    });
    addBox(group, 0.26, 0.04, 0.02, 0xf06a2a, 0, 0.36, 0.14, {
      night: 0xffb15a,
      glow: 0xffb15a,
      glowAmount: 0.65,
      shadow: false
    });
    scene.add(group);
  }
  THEME_CIRCLES.forEach((prop) => {
    if (prop.id.indexOf('palm') === 0) palmAt(prop.x, prop.z);
    else if (prop.id === 'bamboo') bambooAt(prop.x, prop.z);
    else if (prop.id === 'dish') dishAt(prop.x, prop.z);
    else if (prop.id === 'figure') figureAt(prop.x, prop.z);
    else if (prop.id === 'herb') herbAt(prop.x, prop.z);
    else if (prop.id === 'rack') rackAt(prop.x, prop.z);
  });

  function cloud(x, y, z, scale, drift) {
    const group = new THREE.Group();
    [[0, 0, 0, 1], [0.85, 0.1, 0.05, 0.7], [-0.75, 0.04, -0.06, 0.58]].forEach(([px, py, pz, s]) => {
      const puff = addMesh(group, geo('sph', [1, 5, 4]), 0xfffaf8, px, py, pz, {
        scale: [s, s * 0.72, s],
        shadow: false,
        receive: false
      });
      puff.castShadow = false;
    });
    group.position.set(x, y, z);
    group.scale.setScalar(scale);
    group.userData.origin = x;
    group.userData.drift = drift;
    scene.add(group);
    clouds.push(group);
  }
  cloud(-20, 11.5, -24, 0.85, 0.12);
  cloud(6, 13, -30, 0.65, 0.1);
  cloud(26, 12, -16, 0.5, 0.14);
  cloud(-30, 14, 2, 0.55, 0.08);

  function addFish(radius, speed, phase, color, scale) {
    const group = new THREE.Group();
    addMesh(group, geo('sph', [0.28, 6, 5]), color, 0, 0, 0, { scale: [0.65, 0.48, 1.15], shadow: false, receive: false });
    addMesh(group, geo('cone', [0.14, 0.26, 4]), color, 0, 0, -0.36, { rot: [Math.PI / 2, 0, 0], shadow: false, receive: false });
    group.scale.setScalar(scale);
    scene.add(group);
    swimmers.push({ obj: group, radius: radius, speed: speed, phase: phase, y: -0.58 });
  }
  addFish(13.2, 0.18, 0.4, 0xf4a0c0, 0.7);
  addFish(14.1, 0.14, 2.2, 0xf6d35a, 0.55);

  return { boat: boat };
}

export function registerSceneMotion(loop, state) {
  const reduced = state.reduced;
  const walkers = state.walkers;
  const bobs = state.bobs;
  const idles = state.idles;
  const steams = state.steams;
  const clouds = state.clouds;
  const swimmers = state.swimmers;
  const shaker = state.shaker;
  const ground = state.P;

  function sampleWalker(walker, along) {
    const path = walker.path;
    const count = path.length;
    if (walker.pingpong) {
      const span = Math.max(1, count - 1);
      const cycle = span * 2;
      const u = ((along % cycle) + cycle) % cycle;
      const forward = u <= span;
      const p = forward ? u : cycle - u;
      const index = Math.min(span - 1, Math.floor(p));
      const blend = p - index;
      return {
        from: path[index],
        to: path[index + 1],
        blend: blend,
        dir: forward ? 1 : -1
      };
    }
    const scaled = ((along % 1) + 1) % 1 * count;
    const index = Math.floor(scaled) % count;
    return {
      from: path[index],
      to: path[(index + 1) % count],
      blend: scaled - Math.floor(scaled),
      dir: walker.speed < 0 ? -1 : 1
    };
  }

  loop.register(function updateWalkers(dt, t) {
    walkers.forEach((walker) => {
      const along = reduced ? walker.offset : walker.offset + t * Math.abs(walker.speed);
      const step = sampleWalker(walker, walker.pingpong ? along * walker.path.length : along);
      const dx = step.to[0] - step.from[0];
      const dz = step.to[1] - step.from[1];
      walker.obj.position.set(
        step.from[0] + dx * step.blend,
        ground,
        step.from[1] + dz * step.blend
      );
      if (dx || dz) walker.obj.rotation.y = Math.atan2(dx * step.dir, dz * step.dir);
    });
  });
  loop.register(function updateBobs(dt, t) {
    if (reduced) return;
    bobs.forEach((bob) => {
      bob.obj.position.y = bob.base + Math.sin(t * 1.6 + bob.phase) * bob.amp;
    });
  });
  loop.register(function updateIdles(dt, t) {
    if (reduced) return;
    idles.forEach((item) => {
      item.obj.position.y = item.base + Math.sin(t * 1.5 + item.phase) * 0.012;
    });
  });
  loop.register(function updateSteams(dt, t) {
    if (reduced) return;
    steams.forEach((puff) => {
      puff.position.y = puff.userData.base + Math.sin(t * 1.7 + puff.userData.phase) * 0.08;
    });
  });
  loop.register(function updateShaker(dt, t) {
    if (reduced) return;
    if (shaker) shaker.rotation.y = t * 1.6;
  });
  loop.register(function updateClouds(dt, t) {
    if (reduced) return;
    clouds.forEach((item) => {
      item.position.x = item.userData.origin + Math.sin(t * item.userData.drift) * 1.4;
    });
  });
  loop.register(function updateSwimmers(dt, t) {
    if (reduced) return;
    swimmers.forEach((fish) => {
      const angle = t * fish.speed + fish.phase;
      fish.obj.position.set(Math.cos(angle) * fish.radius, fish.y, Math.sin(angle) * fish.radius);
      fish.obj.rotation.y = Math.atan2(-Math.sin(angle), Math.cos(angle));
    });
  });
}
