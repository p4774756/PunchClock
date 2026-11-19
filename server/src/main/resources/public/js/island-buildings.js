import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { CASINO } from './island-explore.js';
import { wallSlots } from './island-graffiti.js';
import { BUILDINGS, ISLANDS } from './island-layout.js';
import { signCopy } from './island-roadmap.js';
import { G, P, nightHex } from './island-kit.js';
import { BUILDING_GLOW, glowForMaterial } from './island-buildings-data.js';
import { polishPedestalBase } from './island-decor.js';
export { BUILDING_GLOW, glowForMaterial };

const glbCache = new Map();
let sharedLoader = null;
function getLoader() {
  if (!sharedLoader) sharedLoader = new GLTFLoader();
  return sharedLoader;
}
function loadGLB(url) {
  if (glbCache.has(url)) return glbCache.get(url);
  const promise = new Promise((resolve, reject) => {
    getLoader().load(url, resolve, undefined, reject);
  });
  glbCache.set(url, promise);
  return promise;
}

export function buildIslandBuildings(ctx) {
  const { scene, kit, anchors, rings, pickables, steams, dayNight, reduced: motionReduced } = ctx;
  const { addMesh, addBox, geo, mat, paint, picture, addCup, lampLight, addMaterial } = kit;
  let shaker = null;
  const font = '"PingFang TC", "Heiti TC", "Microsoft JhengHei", sans-serif';
  const signMap = paint(640, 256, (g, w, h) => {
    g.fillStyle = '#fff7ef';
    g.fillRect(0, 0, w, h);
    g.fillStyle = '#f06a2a';
    g.fillRect(0, 0, 26, h);
    g.fillStyle = '#1f4d73';
    g.beginPath();
    g.arc(108, h / 2, 64, 0, Math.PI * 2);
    g.fill();
    g.fillStyle = '#fff7ef';
    g.font = '700 68px ' + font;
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.fillText('神', 108, h / 2 + 2);
    g.fillStyle = '#1f4d73';
    g.font = '700 72px ' + font;
    g.textAlign = 'left';
    g.fillText('神之島', 188, h / 2 + 2);
  });
  const menuMap = paint(384, 512, (g, w, h) => {
    g.fillStyle = '#fff7ef';
    g.fillRect(0, 0, w, h);
    g.fillStyle = '#f06a2a';
    g.fillRect(0, 0, w, 84);
    g.fillStyle = '#fff7ef';
    g.font = '700 52px ' + font;
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.fillText('菜單', w / 2, 44);
    ['#f4a0c0', '#f6d35a', '#7ec8ea', '#9fd9b4'].forEach((fill, i) => {
      const y = 138 + i * 86;
      g.fillStyle = fill;
      g.beginPath();
      g.arc(68, y, 28, 0, Math.PI * 2);
      g.fill();
      g.fillStyle = '#3f3434';
      g.fillRect(118, y - 12, 210, 16);
      g.fillStyle = '#d9cfc4';
      g.fillRect(118, y + 16, 150, 10);
    });
  });
  const casinoSignMap = paint(640, 220, (g, w, h) => {
    g.fillStyle = '#1a0c28';
    g.fillRect(0, 0, w, h);
    g.strokeStyle = '#ff6ad8';
    g.lineWidth = 8;
    g.strokeRect(10, 10, w - 20, h - 20);
    g.strokeStyle = '#d4a84b';
    g.lineWidth = 4;
    g.strokeRect(18, 18, w - 36, h - 36);
    g.font = '700 76px ' + font;
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.strokeStyle = '#ff4ec8';
    g.lineWidth = 5;
    g.strokeText('神之島賭場', w / 2, h / 2 - 24);
    g.fillStyle = '#ff9ee8';
    g.fillText('神之島賭場', w / 2, h / 2 - 24);
    g.font = '700 52px ' + font;
    g.strokeStyle = '#d4a84b';
    g.strokeText('777', w / 2, h / 2 + 52);
    g.fillStyle = '#ffe878';
    g.fillText('777', w / 2, h / 2 + 52);
  });
  const tcmSignMap = paint(512, 256, (g, w, h) => {
    g.fillStyle = '#3c1e14';
    g.fillRect(0, 0, w, h);
    g.fillStyle = '#d4a84b';
    g.fillRect(0, 0, w, 22);
    g.fillRect(0, h - 22, w, 22);
    g.font = '700 108px ' + font;
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.strokeStyle = '#1a0c08';
    g.lineWidth = 6;
    g.strokeText('推拿館', w / 2, h / 2 + 4);
    g.fillStyle = '#fff7ef';
    g.fillText('推拿館', w / 2, h / 2 + 4);
  });
  const labSignMap = paint(512, 256, (g, w, h) => {
    g.fillStyle = '#0e1a28';
    g.fillRect(0, 0, w, h);
    g.fillStyle = '#7ec8ea';
    g.fillRect(0, 0, w, 16);
    g.fillRect(0, h - 16, w, 16);
    g.font = '700 100px ' + font;
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.strokeStyle = '#051018';
    g.lineWidth = 5;
    g.strokeText('研發樓', w / 2, h / 2 + 4);
    g.fillStyle = '#ffffff';
    g.fillText('研發樓', w / 2, h / 2 + 4);
  });
  const signMaps = { shop: signMap, casino: casinoSignMap, 'tcm-site': tcmSignMap, 'computer-site': labSignMap };
  const buildingGroups = {};

  function loadBuildingGLB(buildingId, primitive, x, z, yaw) {
    const url = '/models/' + buildingId + '.glb';
    loadGLB(url).then((gltf) => {
      const model = gltf.scene;
      model.position.set(x, G, z);
      model.rotation.y = yaw || 0;
      model.traverse((obj) => {
        if (!obj.isMesh) return;
        const oldMat = obj.material;
        const matName = oldMat && oldMat.name;
        if (matName) obj.userData.glbMaterialName = matName;
        const glowInfo = glowForMaterial(buildingId, matName);
        let baseColor = 0xffffff;
        if (oldMat && oldMat.color) baseColor = oldMat.color.getHex();
        let extra = {};
        if (glowInfo) {
          const nightC = nightHex(baseColor);
          extra = {
            night: nightC != null ? nightC : baseColor,
            glow: glowInfo.color,
            glowAmount: glowInfo.amount
          };
        }
        if (obj.name === 'sign_face') {
          const signTex = signMaps[buildingId];
          if (signTex) {
            const labSignNight = buildingId === 'computer-site';
            obj.material = mat(0xffffff, {
              map: signTex,
              night: labSignNight ? 0xe8f8ff : 0xfff0dc,
              glow: labSignNight ? 0x7ec8ea : 0xffb15a,
              glowAmount: labSignNight ? 1.12 : 0.28,
              side: THREE.DoubleSide
            });
          } else {
            obj.material = mat(baseColor, extra);
          }
        } else {
          obj.material = mat(baseColor, extra);
        }
        if (oldMat && typeof oldMat.dispose === 'function') oldMat.dispose();
        obj.castShadow = true;
        obj.receiveShadow = true;
      });
      scene.add(model);
      primitive.visible = false;
      buildingGroups[buildingId] = model;
      if (buildingId === 'computer-site') {
        model.traverse((obj) => {
          if (obj.isMesh && obj.name === 'windmill') {
            obj.userData.slowSpin = !motionReduced;
          }
        });
      }
      if (dayNight && typeof dayNight.refresh === 'function') dayNight.refresh();
    }).catch((err) => {
      console.warn('[Island] GLB 載入失敗 (' + buildingId + ')，保留 primitive：', err);
    });
  }

  function buildShop() {
    const shop = new THREE.Group();
    shop.position.set(0, 0, -0.55);
    const W = 4.75;
    const D = 3.2;
    const H = 1.92;
    const T = 0.14;
    const front = D / 2;
    const back = -D / 2;
    const base = G + 0.08;
    const wallY = base + H / 2;
    addBox(shop, W + 0.36, 0.12, D + 0.36, 0xf3e2cc, 0, G + 0.04, 0);
    addBox(shop, W - 0.28, 0.08, D - 0.28, 0xc4844a, 0, G + 0.08, 0);
    addBox(shop, W, H, T, 0xfff7ef, 0, wallY, back + T / 2);
    addBox(shop, T, H, D, 0xfff7ef, -W / 2 + T / 2, wallY, 0);
    addBox(shop, T, H, D, 0xfff7ef, W / 2 - T / 2, wallY, 0);
    const pier = 1.1;
    const open = W - pier * 2;
    addBox(shop, pier, H, T, 0xfff7ef, -W / 2 + pier / 2, wallY, front - T / 2);
    addBox(shop, pier, H, T, 0xfff7ef, W / 2 - pier / 2, wallY, front - T / 2);
    addBox(shop, W, 0.46, T, 0xfff7ef, 0, base + H - 0.23, front - T / 2);
    addBox(shop, open + 0.08, 0.08, T + 0.04, 0xf06a2a, 0, base + H - 0.5, front);
    addBox(shop, 0.1, H, 0.1, 0xf3e2cc, -W / 2, wallY, front);
    addBox(shop, 0.1, H, 0.1, 0xf3e2cc, W / 2, wallY, front);
    addBox(shop, 0.1, H, 0.1, 0xf3e2cc, -W / 2, wallY, back);
    addBox(shop, 0.1, H, 0.1, 0xf3e2cc, W / 2, wallY, back);

    const roofY = base + H + 0.14;
    addBox(shop, W + 0.15, 0.08, D + 0.15, 0xfff7ef, 0, roofY - 0.16, 0);
    addBox(shop, W + 0.62, 0.26, D + 0.62, 0x1f4d73, 0, roofY, 0);
    addBox(shop, W + 0.78, 0.06, D + 0.78, 0x17385a, 0, roofY + 0.14, 0);

    picture(shop, 2.85, 1.05, signMap, 0, roofY + 0.42, front - 0.15, 0, 0.55, -0.62);

    const mascot = new THREE.Group();
    mascot.position.set(0.15, roofY + 0.28, -0.15);
    mascot.scale.setScalar(1.45);
    addMesh(mascot, geo('cyl', [0.2, 0.15, 0.46, 7, 0]), 0xf06a2a, 0, 0.23, 0);
    addMesh(mascot, geo('cyl', [0.22, 0.22, 0.07, 7, 0]), 0xfffaf6, 0, 0.48, 0);
    const straw = addMesh(mascot, geo('cyl', [0.025, 0.025, 0.32, 4, 0]), 0x3a312c, 0.04, 0.68, 0);
    straw.rotation.z = 0.2;
    shop.add(mascot);

    const awning = new THREE.Group();
    awning.position.set(0, base + H - 0.42, front - 0.02);
    awning.rotation.x = 0.38;
    for (let i = 0; i < 7; i++) {
      addBox(awning, 0.36, 0.045, 1.25, i % 2 ? 0xf06a2a : 0xfff7ef, -1.14 + i * 0.38, 0, 0.62);
    }
    shop.add(awning);
    [-1.2, 1.2].forEach((x) => {
      addBox(shop, 0.06, 1.28, 0.06, 0xfff7ef, x, G + 0.64, front + 0.95);
    });

    function windowUnit(x, y, z, w, h, rotY) {
      const group = new THREE.Group();
      group.position.set(x, y, z);
      group.rotation.y = rotY;
      addBox(group, w, h, 0.045, 0xb7e0ef, 0, 0, 0, {
        night: 0xffd7a4,
        glow: 0xffb15a,
        glowAmount: 0.95,
        shadow: false
      });
      const ft = 0.05;
      addBox(group, w + ft * 2, ft, 0.08, 0xfff7ef, 0, h / 2 + ft / 2, 0.02);
      addBox(group, w + ft * 2, ft, 0.08, 0xfff7ef, 0, -h / 2 - ft / 2, 0.02);
      addBox(group, ft, h, 0.08, 0xfff7ef, -w / 2 - ft / 2, 0, 0.02);
      addBox(group, ft, h, 0.08, 0xfff7ef, w / 2 + ft / 2, 0, 0.02);
      addBox(group, ft * 0.65, h, 0.07, 0xf3e2cc, 0, 0, 0.03);
      shop.add(group);
    }
    const winY = base + 1.15;
    windowUnit(W / 2 + 0.02, winY, -0.55, 0.62, 0.58, Math.PI / 2);
    windowUnit(W / 2 + 0.02, winY, 0.55, 0.62, 0.58, Math.PI / 2);
    windowUnit(-W / 2 - 0.02, winY, 0.15, 0.55, 0.5, -Math.PI / 2);
    windowUnit(0, winY + 0.15, back - 0.02, 0.7, 0.48, Math.PI);

    const door = new THREE.Group();
    door.position.set(-W / 2 - 0.04, base + 0.64, -0.85);
    door.rotation.y = -Math.PI / 2;
    addBox(door, 0.58, 1.28, 0.07, 0xf0d2b0, 0, 0, 0);
    addBox(door, 0.68, 0.06, 0.09, 0xfff7ef, 0, 0.66, 0.02);
    addBox(door, 0.05, 1.28, 0.09, 0xfff7ef, -0.31, 0, 0.02);
    addBox(door, 0.05, 1.28, 0.09, 0xfff7ef, 0.31, 0, 0.02);
    addBox(door, 0.32, 0.36, 0.03, 0xb7e0ef, 0, 0.28, 0.05, {
      night: 0xffd7a4,
      glow: 0xffb15a,
      glowAmount: 0.7,
      shadow: false
    });
    addBox(door, 0.05, 0.08, 0.05, 0xf06a2a, 0.18, 0, 0.06);
    shop.add(door);

    addBox(shop, 1.6, 0.78, 0.46, 0xf3e2cc, -0.7, base + 0.39, back + 0.55);
    addBox(shop, 1.6, 0.06, 0.5, 0xfffaf6, -0.7, base + 0.8, back + 0.55);
    addBox(shop, 0.55, 1.15, 0.5, 0x1f4d73, 1.55, base + 0.58, back + 0.5);
    addCup(shop, 0xf4a0c0, -1.15, base + 0.84, back + 0.55, null);
    addCup(shop, 0xf6d35a, -0.85, base + 0.84, back + 0.5, null);
    addCup(shop, 0x9fd9b4, -0.55, base + 0.84, back + 0.58, null);
    addBox(shop, 0.28, 0.22, 0.2, 0x3a312c, 0.15, base + 0.95, front - 0.35);

    scene.add(shop);
    const anchor = new THREE.Object3D();
    anchor.position.set(0.15, roofY + 1.15, -0.05);
    shop.add(anchor);
    anchors.shop = anchor;
    lampLight(0, base + 1.35, front + 0.55, 16, null, { realLight: true });
    lampLight(0.2, base + 1.25, -0.2, 9, null, { realLight: true });
    buildingGroups.shop = shop;
    loadBuildingGLB('shop', shop, 0, -0.55, 0);
  }

  buildShop();

  function buildCasino() {
    const casino = new THREE.Group();
    casino.position.set(CASINO.x, 0, CASINO.z);
    casino.rotation.y = CASINO.yaw || 0;
    const W = CASINO.w;
    const D = CASINO.d;
    const H = 2.15;
    const T = 0.14;
    const front = D / 2;
    const back = -D / 2;
    const base = G + 0.08;
    const wallY = base + H / 2;
    const doorW = 0.72;

    addBox(casino, 2.15, 0.04, 1.7, 0xe4d2b2, 0, G + 0.012, front + 0.85, { shadow: false });
    addBox(casino, 1.9, 0.05, 1.45, 0xf4e6cf, 0, G + 0.034, front + 0.85, { shadow: false });
    addBox(casino, 0.78, 0.025, 1.55, 0x9c2b3a, 0, G + 0.055, front + 0.95, { shadow: false });

    addBox(casino, W + 0.4, 0.12, D + 0.4, 0xd4a84b, 0, G + 0.04, 0);
    addBox(casino, W - 0.2, 0.08, D - 0.2, 0x3d2468, 0, G + 0.08, 0);
    addBox(casino, W, H, T, 0x5b3a8c, 0, wallY, back + T / 2);
    addBox(casino, T, H, D, 0x5b3a8c, -W / 2 + T / 2, wallY, 0);
    addBox(casino, T, H, D, 0x5b3a8c, W / 2 - T / 2, wallY, 0);

    const side = (W - doorW) / 2;
    addBox(casino, side, H, T, 0x5b3a8c, -W / 2 + side / 2, wallY, front - T / 2);
    addBox(casino, side, H, T, 0x5b3a8c, W / 2 - side / 2, wallY, front - T / 2);
    addBox(casino, doorW + 0.1, 0.42, T, 0x5b3a8c, 0, base + H - 0.21, front - T / 2);
    addBox(casino, doorW + 0.16, 0.08, T + 0.04, 0xd4a84b, 0, base + H - 0.48, front);

    addBox(casino, 0.1, H, 0.1, 0xd4a84b, -W / 2, wallY, front);
    addBox(casino, 0.1, H, 0.1, 0xd4a84b, W / 2, wallY, front);
    addBox(casino, 0.1, H, 0.1, 0xd4a84b, -W / 2, wallY, back);
    addBox(casino, 0.1, H, 0.1, 0xd4a84b, W / 2, wallY, back);

    const roofY = base + H + 0.12;
    addBox(casino, W + 0.2, 0.08, D + 0.2, 0x7a54b5, 0, roofY - 0.14, 0);
    addBox(casino, W + 0.55, 0.28, D + 0.55, 0x2a1848, 0, roofY, 0);
    addBox(casino, W + 0.7, 0.06, D + 0.7, 0xd4a84b, 0, roofY + 0.16, 0);
    addMesh(casino, geo('cone', [0.42, 0.55, 6]), 0xd4a84b, 0, roofY + 0.55, 0);
    addMesh(casino, geo('sph', [0.08, 5, 4]), 0xf0d078, 0, roofY + 0.86, 0, {
      night: 0xffe2b0,
      glow: 0xffc56a,
      glowAmount: 1.1,
      shadow: false
    });

    function casinoWindow(x, y, z, w, h, rotY) {
      const group = new THREE.Group();
      group.position.set(x, y, z);
      group.rotation.y = rotY || 0;
      addBox(group, w, h, 0.045, 0xf0d078, 0, 0, 0, {
        night: 0xffd7a4,
        glow: 0xffb15a,
        glowAmount: 1.05,
        shadow: false
      });
      addBox(group, w + 0.1, 0.05, 0.08, 0xd4a84b, 0, h / 2 + 0.03, 0.02);
      addBox(group, w + 0.1, 0.05, 0.08, 0xd4a84b, 0, -h / 2 - 0.03, 0.02);
      addBox(group, 0.05, h, 0.08, 0xd4a84b, -w / 2 - 0.03, 0, 0.02);
      addBox(group, 0.05, h, 0.08, 0xd4a84b, w / 2 + 0.03, 0, 0.02);
      casino.add(group);
    }
    const winY = base + 1.15;
    casinoWindow(W / 2 + 0.02, winY, -0.35, 0.55, 0.62, Math.PI / 2);
    casinoWindow(W / 2 + 0.02, winY, 0.4, 0.55, 0.62, Math.PI / 2);
    casinoWindow(-W / 2 - 0.02, winY, 0.05, 0.55, 0.58, -Math.PI / 2);
    casinoWindow(0, winY + 0.1, back - 0.02, 0.7, 0.5, Math.PI);

    const door = new THREE.Group();
    door.position.set(0, base + 0.72, front - 0.02);
    addBox(door, doorW, 1.44, 0.08, 0x3d2468, 0, 0, 0);
    addBox(door, doorW + 0.1, 0.06, 0.1, 0xd4a84b, 0, 0.74, 0.02);
    addBox(door, 0.05, 1.44, 0.1, 0xd4a84b, -doorW / 2, 0, 0.02);
    addBox(door, 0.05, 1.44, 0.1, 0xd4a84b, doorW / 2, 0, 0.02);
    addBox(door, 0.04, 1.4, 0.04, 0xf0d078, 0, 0, 0.03);
    addBox(door, 0.28, 0.34, 0.03, 0xfff0c8, -0.16, 0.22, 0.05, {
      night: 0xffd7a4,
      glow: 0xffb15a,
      glowAmount: 0.85,
      shadow: false
    });
    addBox(door, 0.28, 0.34, 0.03, 0xfff0c8, 0.16, 0.22, 0.05, {
      night: 0xffd7a4,
      glow: 0xffb15a,
      glowAmount: 0.85,
      shadow: false
    });
    addBox(door, 0.05, 0.08, 0.05, 0xd4a84b, 0.22, -0.05, 0.06);
    casino.add(door);

    addBox(casino, 0.9, 0.08, 0.55, 0xd4a84b, 0, base + 0.02, front + 0.35);
    [-0.55, 0.55].forEach((x) => {
      addBox(casino, 0.08, 1.35, 0.08, 0xd4a84b, x, G + 0.72, front + 0.55);
      addMesh(casino, geo('sph', [0.1, 5, 4]), 0xf0d078, x, G + 1.45, front + 0.55, {
        night: 0xffe2b0,
        glow: 0xffc56a,
        glowAmount: 1.2,
        shadow: false
      });
    });

    scene.add(casino);
    const anchor = new THREE.Object3D();
    anchor.position.set(0, roofY + 1.05, 0.1);
    casino.add(anchor);
    anchors.casino = anchor;
    lampLight(0, base + 1.4, front + 0.55, 14, casino);
    lampLight(0, roofY + 0.7, 0, 8, casino);
    lampLight(0, G + 1.15, front + 1.5, 11, casino);
    buildingGroups.casino = casino;
    loadBuildingGLB('casino', casino, CASINO.x, CASINO.z, CASINO.yaw);
  }

  buildCasino();

  function buildHall(building, theme) {
    const hall = new THREE.Group();
    hall.position.set(building.x, 0, building.z);
    hall.rotation.y = building.yaw || 0;
    const tcm = theme === 'tcm';
    const W = building.w;
    const D = building.d;
    const H = tcm ? 1.85 : 2.05;
    const T = 0.12;
    const front = D / 2;
    const back = -D / 2;
    const base = G + 0.08;
    const wallY = base + H / 2;
    const doorW = 0.62;
    const wall = tcm ? 0xf3e2cc : 0xd7e6ee;
    const trim = tcm ? 0x6b3a2a : 0x5d7384;
    const roof = tcm ? 0x8d3d3a : 0x24384c;
    const accent = tcm ? 0xd4a84b : 0x7ec8ea;
    const glow = tcm ? 0xffb15a : 0x7ec8ea;

    addBox(hall, 1.55, 0.04, 1.2, 0xe4d2b2, 0, G + 0.012, front + 0.7, { shadow: false });
    addBox(hall, 1.3, 0.05, 0.95, 0xf4e6cf, 0, G + 0.034, front + 0.7, { shadow: false });
    addBox(hall, W + 0.32, 0.12, D + 0.32, trim, 0, G + 0.04, 0);
    addBox(hall, W - 0.16, 0.08, D - 0.16, tcm ? 0xc4844a : 0x8aa0b0, 0, G + 0.08, 0);
    addBox(hall, W, H, T, wall, 0, wallY, back + T / 2);
    addBox(hall, T, H, D, wall, -W / 2 + T / 2, wallY, 0);
    addBox(hall, T, H, D, wall, W / 2 - T / 2, wallY, 0);
    const side = (W - doorW) / 2;
    addBox(hall, side, H, T, wall, -W / 2 + side / 2, wallY, front - T / 2);
    addBox(hall, side, H, T, wall, W / 2 - side / 2, wallY, front - T / 2);
    addBox(hall, doorW + 0.08, 0.36, T, wall, 0, base + H - 0.18, front - T / 2);
    [[-1, -1], [1, -1], [1, 1], [-1, 1]].forEach(([sx, sz]) => {
      addBox(hall, 0.1, H, 0.1, trim, sx * (W / 2), wallY, sz * (D / 2));
    });

    const roofY = base + H + 0.08;
    if (tcm) {
      addBox(hall, W + 0.12, 0.08, D + 0.12, 0xf3e2cc, 0, roofY - 0.14, 0);
      addBox(hall, W + 0.62, 0.2, D + 0.48, roof, 0, roofY, 0);
      addBox(hall, W + 0.16, 0.14, D + 0.1, 0x6e2e2c, 0, roofY + 0.2, 0);
      addBox(hall, W - 0.2, 0.1, D - 0.15, roof, 0, roofY + 0.32, 0);
      [[-1, -1], [1, -1], [1, 1], [-1, 1]].forEach(([sx, sz]) => {
        const horn = addMesh(hall, geo('cone', [0.09, 0.26, 5]), accent, sx * (W / 2 + 0.24), roofY + 0.08, sz * (D / 2 + 0.16));
        horn.rotation.z = sx * -0.7;
        horn.rotation.x = sz * 0.55;
      });
    } else {
      addBox(hall, W + 0.16, 0.1, D + 0.16, 0x9eb4c4, 0, roofY - 0.1, 0);
      addBox(hall, W + 0.4, 0.16, D + 0.34, roof, 0, roofY, 0);
      addBox(hall, W + 0.5, 0.05, D + 0.44, accent, 0, roofY + 0.11, 0, {
        night: 0xb7e4ff,
        glow: glow,
        glowAmount: 0.45,
        shadow: false
      });
      addBox(hall, 0.08, 0.62, 0.08, 0xd5dde4, 0.32, roofY + 0.42, -0.12);
      addMesh(hall, geo('sph', [0.09, 6, 5]), accent, 0.32, roofY + 0.8, -0.12, {
        night: 0xd7f4ff,
        glow: glow,
        glowAmount: 1.25,
        shadow: false
      });
    }

    picture(hall, tcm ? 1.55 : 1.42, 0.56, tcm ? tcmSignMap : labSignMap, 0, roofY + (tcm ? 0.58 : 0.42), front - 0.06, 0, tcm ? 0.72 : 0.88, tcm ? -0.22 : -0.16);

    function hallWindow(x, y, z, ww, hh, rotY) {
      const group = new THREE.Group();
      group.position.set(x, y, z);
      group.rotation.y = rotY || 0;
      addBox(group, ww, hh, 0.04, tcm ? 0xfff0c8 : 0xb7e4ff, 0, 0, 0, {
        night: tcm ? 0xffd7a4 : 0xd7f4ff,
        glow: glow,
        glowAmount: tcm ? 0.9 : 1.15,
        shadow: false
      });
      addBox(group, ww + 0.08, 0.045, 0.07, trim, 0, hh / 2 + 0.025, 0.02);
      addBox(group, ww + 0.08, 0.045, 0.07, trim, 0, -hh / 2 - 0.025, 0.02);
      addBox(group, 0.045, hh, 0.07, trim, -ww / 2 - 0.025, 0, 0.02);
      addBox(group, 0.045, hh, 0.07, trim, ww / 2 + 0.025, 0, 0.02);
      hall.add(group);
    }
    const winY = base + 1.02;
    hallWindow(W / 2 + 0.02, winY, -0.2, 0.46, 0.48, Math.PI / 2);
    hallWindow(-W / 2 - 0.02, winY, -0.2, 0.46, 0.48, -Math.PI / 2);
    if (tcm) hallWindow(0, winY + 0.08, back - 0.02, 0.52, 0.38, Math.PI);
    else hallWindow(0.7, winY, front + 0.02, 0.4, 0.34, 0);

    const door = new THREE.Group();
    door.position.set(0, base + 0.66, front - 0.02);
    addBox(door, doorW, 1.32, 0.07, tcm ? 0x8d3d3a : 0x1c3148, 0, 0, 0);
    addBox(door, doorW + 0.1, 0.05, 0.09, trim, 0, 0.68, 0.02);
    addBox(door, 0.045, 1.32, 0.09, accent, -doorW / 2, 0, 0.02);
    addBox(door, 0.045, 1.32, 0.09, accent, doorW / 2, 0, 0.02);
    addBox(door, 0.28, 0.32, 0.03, tcm ? 0xfff0c8 : 0xb7e4ff, 0, 0.22, 0.05, {
      night: tcm ? 0xffd7a4 : 0xd7f4ff,
      glow: glow,
      glowAmount: 0.85,
      shadow: false
    });
    addBox(door, 0.04, 0.07, 0.04, accent, 0.18, -0.02, 0.06);
    hall.add(door);

    if (tcm) {
      addBox(hall, 0.5, 0.62, 0.24, 0x6b3a2a, -0.45, base + 0.36, back + 0.32);
      addBox(hall, 0.5, 0.04, 0.26, 0xf3e2cc, -0.45, base + 0.68, back + 0.32);
      addMesh(hall, geo('cyl', [0.06, 0.07, 0.12, 6, 0]), 0x9fd9b4, -0.58, base + 0.78, back + 0.32);
      addMesh(hall, geo('cyl', [0.05, 0.06, 0.1, 6, 0]), 0xf4a0c0, -0.4, base + 0.76, back + 0.34);
      [-0.48, 0.48].forEach((x) => {
        addBox(hall, 0.045, 0.62, 0.045, trim, x, G + 0.36, front + 0.38);
        addMesh(hall, geo('sph', [0.08, 6, 5]), accent, x, G + 0.74, front + 0.38, {
          night: 0xffe2b0,
          glow: 0xffc56a,
          glowAmount: 1.15,
          shadow: false
        });
      });
      lampLight(0, base + 1.15, front + 0.4, 12, hall);
    } else {
      addBox(hall, 0.05, 0.03, 0.9, 0x3a312c, 0.62, G + 0.055, front + 0.48, { shadow: false });
      addBox(hall, 0.55, 0.03, 0.05, 0x3a312c, 0.32, G + 0.055, front + 0.12, { shadow: false });
      lampLight(0, base + 1.2, front + 0.35, 10, hall);
      lampLight(0.7, winY, front + 0.2, 7, hall);
    }

    scene.add(hall);
    const anchor = new THREE.Object3D();
    anchor.position.set(0, roofY + 1.05, 0.12);
    hall.add(anchor);
    anchors[building.id] = anchor;
    buildingGroups[building.id] = hall;
    loadBuildingGLB(building.id, hall, building.x, building.z, building.yaw);
  }

  BUILDINGS.forEach((building) => {
    if (building.id === 'tcm-site') buildHall(building, 'tcm');
    else if (building.id === 'computer-site') buildHall(building, 'computer');
  });

  function makeZone(id, x, z, ringR) {
    const group = new THREE.Group();
    group.position.set(x, 0, z);
    group.userData.zone = id;
    const anchor = new THREE.Object3D();
    anchor.position.set(0, G + 1.65, 0);
    group.add(anchor);
    anchors[id] = anchor;
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(ringR - 0.12, ringR, 28),
      new THREE.MeshBasicMaterial({ color: 0xf06a2a, side: THREE.DoubleSide })
    );
    ring.rotation.x = -Math.PI / 2;
    ring.position.y = P + 0.025;
    ring.visible = false;
    ring.userData.marker = true;
    ring.castShadow = false;
    ring.receiveShadow = false;
    group.add(ring);
    rings[id] = ring;
    scene.add(group);
    return group;
  }

  function tag(group) {
    const id = group.userData.zone;
    group.traverse((obj) => {
      if (obj.isMesh && !obj.userData.marker) {
        obj.userData.zone = id;
        pickables.push(obj);
      }
    });
  }

  const counter = makeZone('devices', 0, 1.42, 1.85);
  addBox(counter, 2.45, 0.62, 0.68, 0xf3e2cc, 0, G + 0.31, 0);
  addBox(counter, 2.55, 0.07, 0.76, 0xfffaf6, 0, G + 0.64, 0);
  addBox(counter, 2.55, 0.08, 0.06, 0xf06a2a, 0, G + 0.4, 0.34);
  addBox(counter, 0.32, 0.16, 0.24, 0x3a312c, 0.85, G + 0.76, 0.05);
  [[-0.78, 0xf4a0c0], [-0.28, 0xf6d35a], [0.22, 0x7ec8ea], [0.7, 0x9fd9b4]].forEach(([x, color], i) => {
    addCup(counter, color, x, G + 0.68, 0.02, i * 0.7);
  });
  addBox(counter, 1.35, 0.025, 0.55, 0xd4531c, 0, P + 0.02, 0.72, { shadow: false });
  [-0.55, 0.15].forEach((z, i) => {
    addBox(counter, 0.055, 0.7, 0.055, 0x1f4d73, -0.95, G + 0.35, z + 0.7);
  });
  addBox(counter, 0.03, 0.03, 1.15, 0xf06a2a, -0.95, G + 0.62, 1.15);
  const stand = new THREE.Group();
  stand.position.set(1.45, G, 0.85);
  stand.rotation.y = 0.42;
  addBox(stand, 0.06, 1.12, 0.06, 0xc4844a, -0.38, 0.56, 0.1);
  addBox(stand, 0.06, 1.12, 0.06, 0xc4844a, 0.38, 0.56, 0.1);
  addBox(stand, 0.06, 1.12, 0.06, 0xc4844a, -0.38, 0.56, -0.12);
  addBox(stand, 0.06, 1.12, 0.06, 0xc4844a, 0.38, 0.56, -0.12);
  counter.add(stand);
  picture(stand, 0.78, 1.02, menuMap, 0, 0.78, 0, 0, 0.16);
  anchors.devices.position.set(0, G + 1.45, 0);
  tag(counter);

  const machine = makeZone('system', -4.05, -0.15, 1.2);
  machine.rotation.y = 0.42;
  addBox(machine, 1.45, 0.1, 1.15, 0xf3e2cc, 0, G + 0.05, 0);
  addBox(machine, 1.15, 1.35, 0.9, 0xfff7ef, 0, G + 0.78, 0);
  addBox(machine, 1.28, 0.12, 1.02, 0x1f4d73, 0, G + 1.5, 0);
  addBox(machine, 0.72, 0.08, 0.86, 0xf06a2a, 0, G + 1.28, 0.04);
  addBox(machine, 0.62, 0.72, 0.04, 0xb7e0ef, 0, G + 0.82, 0.46, {
    night: 0xffd7a4,
    glow: 0xffb15a,
    glowAmount: 0.75,
    shadow: false
  });
  addBox(machine, 0.7, 0.06, 0.08, 0xfff7ef, 0, G + 1.2, 0.46);
  addBox(machine, 0.7, 0.06, 0.08, 0xfff7ef, 0, G + 0.44, 0.46);
  addBox(machine, 0.2, 0.1, 0.08, 0xf06a2a, 0.28, G + 0.95, 0.48);
  addMesh(machine, geo('cyl', [0.2, 0.16, 0.28, 7, 0]), 0x1f4d73, 0, G + 1.72, 0);
  shaker = addMesh(machine, geo('cyl', [0.16, 0.16, 0.42, 7, 0]), 0xf6d35a, 0.34, G + 1.82, 0.12);
  addBox(machine, 0.7, 0.08, 0.42, 0xf3e2cc, 0.72, G + 0.62, 0.15);
  addCup(machine, 0x9fd9b4, 0.55, G + 0.66, 0.2, null);
  addCup(machine, 0xf4a0c0, 0.88, G + 0.66, 0.12, 1.2);
  const puffGeo = geo('sph', [0.12, 5, 4]);
  for (let i = 0; i < 3; i++) {
    const puff = addMesh(machine, puffGeo, 0xfffaf6, -0.08 + i * 0.1, G + 2.05, 0.05, { shadow: false, receive: false });
    puff.userData.base = puff.position.y;
    puff.userData.phase = i * 0.8;
    steams.push(puff);
  }
  anchors.system.position.set(0, G + 2.25, 0);
  tag(machine);

  const board = makeZone('photos', 4.15, 0.2, 1.45);
  board.rotation.y = 0.62;
  addBox(board, 2.15, 0.1, 0.7, 0xf3e2cc, 0, G + 0.05, 0);
  addBox(board, 0.1, 1.7, 0.1, 0xc4844a, -1.05, G + 0.9, 0);
  addBox(board, 0.1, 1.7, 0.1, 0xc4844a, 1.05, G + 0.9, 0);
  addBox(board, 2.35, 1.55, 0.1, 0xfff7ef, 0, G + 1.05, 0);
  addBox(board, 2.45, 0.12, 0.22, 0xf06a2a, 0, G + 1.82, 0);
  addBox(board, 2.55, 0.1, 0.7, 0x1f4d73, 0, G + 1.95, -0.05);
  const shots = [
    [-0.62, 1.25, 0xf4a0c0, -0.06],
    [0, 1.32, 0xf6d35a, 0.05],
    [0.62, 1.22, 0x7ec8ea, -0.04],
    [-0.35, 0.72, 0x9fd9b4, 0.08],
    [0.38, 0.68, 0xf06a2a, -0.05]
  ];
  shots.forEach(([x, y, color, tilt]) => {
    addBox(board, 0.46, 0.38, 0.03, 0xfffaf6, x, G + y, 0.07);
    const photo = addBox(board, 0.36, 0.26, 0.02, color, x, G + y + 0.02, 0.09, { shadow: false });
    photo.rotation.z = tilt;
  });
  addBox(board, 0.7, 0.22, 0.02, 0xf6d35a, -0.45, G + 0.42, 0.08);
  addBox(board, 0.55, 0.2, 0.02, 0xfffaf6, 0.4, G + 0.4, 0.08);
  anchors.photos.position.set(0, G + 2.2, 0);
  tag(board);

  const graffitiBuilding = BUILDINGS.find((item) => item.id === 'graffiti-wall');
  let refreshGraffitiWall = function () {};
  if (graffitiBuilding) {
    const wall = makeZone(graffitiBuilding.id, graffitiBuilding.x, graffitiBuilding.z, 1.5);
    wall.rotation.y = graffitiBuilding.yaw || 0;
    const faceZ = graffitiBuilding.d / 2;
    polishPedestalBase(addBox, wall, graffitiBuilding.w, graffitiBuilding.d);
    addBox(wall, 0.15, 1.74, 0.17, 0xa56b38, -graffitiBuilding.w / 2, G + 0.95, 0);
    addBox(wall, 0.15, 1.74, 0.17, 0xa56b38, graffitiBuilding.w / 2, G + 0.95, 0);
    addBox(wall, graffitiBuilding.w + 0.08, 0.12, 0.18, 0xf06a2a, 0, G + 1.78, 0);
    const boardCanvas = document.createElement('canvas');
    boardCanvas.width = 1024;
    boardCanvas.height = 384;
    const boardTex = new THREE.CanvasTexture(boardCanvas);
    boardTex.colorSpace = THREE.SRGBColorSpace;
    boardTex.anisotropy = 8;
    const boardMat = new THREE.MeshBasicMaterial({ map: boardTex });
    const artW = graffitiBuilding.w * 0.9;
    const boardMesh = new THREE.Mesh(new THREE.PlaneGeometry(artW, artW * 384 / 1024), boardMat);
    boardMesh.position.set(0, G + 0.98, faceZ + 0.02);
    boardMesh.castShadow = false;
    boardMesh.receiveShadow = false;
    wall.add(boardMesh);
    // 靠東側、略低於牆頂，避開西側「候餐」。
    anchors[graffitiBuilding.id].position.set(graffitiBuilding.w * 0.42, G + 1.35, faceZ);
    lampLight(0, G + 1.7, faceZ + 0.8, 9, wall);
    tag(wall);

    function paintBrick(g, w, h) {
      g.fillStyle = '#e7b199';
      g.fillRect(0, 0, w, h);
      g.strokeStyle = 'rgba(120, 64, 42, 0.45)';
      g.lineWidth = 4;
      for (let y = 0; y < h; y += 48) {
        g.beginPath();
        g.moveTo(0, y);
        g.lineTo(w, y);
        g.stroke();
        const shift = (y / 48) % 2 ? 64 : 0;
        for (let x = shift; x < w; x += 128) {
          g.beginPath();
          g.moveTo(x, y);
          g.lineTo(x, Math.min(h, y + 48));
          g.stroke();
        }
      }
    }

    function drawWall(images) {
      const g = boardCanvas.getContext('2d');
      const w = boardCanvas.width;
      const h = boardCanvas.height;
      paintBrick(g, w, h);
      const cols = 4;
      const rows = 3;
      const cellW = w / cols;
      const cellH = h / rows;
      wallSlots(images, cols * rows).forEach((item, index) => {
        const img = item && images.lookup ? images.lookup[item.id] : null;
        if (!img) return;
        const col = index % cols;
        const row = Math.floor(index / cols);
        const pad = 10;
        g.drawImage(img, col * cellW + pad, row * cellH + pad, cellW - pad * 2, cellH - pad * 2);
      });
      boardTex.needsUpdate = true;
    }

    refreshGraffitiWall = function () {
      fetch('/api/graffiti?limit=12').then((res) => res.json()).then((body) => {
        const items = (body && body.items) || [];
        const lookup = Object.create(null);
        let pending = 0;
        items.forEach((item) => {
          if (!item || !item.id) return;
          pending += 1;
          const img = new Image();
          img.onload = () => {
            lookup[item.id] = img;
            pending -= 1;
            if (pending <= 0) {
              items.lookup = lookup;
              drawWall(items);
            }
          };
          img.onerror = () => {
            pending -= 1;
            if (pending <= 0) {
              items.lookup = lookup;
              drawWall(items);
            }
          };
          img.src = '/api/graffiti/' + encodeURIComponent(item.id) + '.png';
        });
        if (!pending) drawWall(items);
      }).catch(() => drawWall([]));
    };
    drawWall([]);
    refreshGraffitiWall();
  }

  let paintRoadmapSign = function () {};
  const roadmapBuilding = BUILDINGS.find((item) => item.id === 'construction-board');
  if (roadmapBuilding) {
    const sign = makeZone(roadmapBuilding.id, roadmapBuilding.x, roadmapBuilding.z, 1.15);
    sign.rotation.y = roadmapBuilding.yaw || 0;
    const faceZ = roadmapBuilding.d / 2;
    addBox(sign, 0.13, 1.44, 0.13, 0xa56b38, -roadmapBuilding.w / 2, G + 0.77, 0);
    addBox(sign, 0.13, 1.44, 0.13, 0xa56b38, roadmapBuilding.w / 2, G + 0.77, 0);
    polishPedestalBase(addBox, sign, roadmapBuilding.w, 0.18);
    const signCanvas = document.createElement('canvas');
    signCanvas.width = 640;
    signCanvas.height = 320;
    const signTex = new THREE.CanvasTexture(signCanvas);
    signTex.colorSpace = THREE.SRGBColorSpace;
    const signMesh = new THREE.Mesh(
      new THREE.PlaneGeometry(roadmapBuilding.w * 0.92, roadmapBuilding.w * 0.46),
      new THREE.MeshBasicMaterial({ map: signTex })
    );
    signMesh.position.set(0, G + 1.05, faceZ + 0.02);
    sign.add(signMesh);
    function drawRoadmapSign(copy) {
      const text = copy || signCopy(null);
      const g = signCanvas.getContext('2d');
      const w = signCanvas.width;
      const h = signCanvas.height;
      g.fillStyle = '#1a1a1a';
      g.fillRect(0, 0, w, h);
      g.fillStyle = '#f6d35a';
      const band = 22;
      for (let x = -h; x < w + h; x += band * 2) {
        g.beginPath();
        g.moveTo(x, 0);
        g.lineTo(x + band, 0);
        g.lineTo(x + band - h, h);
        g.lineTo(x - h, h);
        g.closePath();
        g.fill();
      }
      g.fillStyle = '#fff7ef';
      g.fillRect(26, 26, w - 52, h - 52);
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.fillStyle = '#1f2430';
      g.font = '700 52px ' + font;
      g.fillText(text.headline || '', w / 2, text.title ? h / 2 - 18 : h / 2);
      if (text.title) {
        g.font = '700 36px ' + font;
        g.fillStyle = '#c45a12';
        g.fillText(text.title, w / 2, h / 2 + 40);
      }
      signTex.needsUpdate = true;
    }
    drawRoadmapSign(signCopy(null));
    paintRoadmapSign = drawRoadmapSign;
    addMesh(sign, geo('cone', [0.14, 0.3, 6]), 0xf08a3a, -0.38, G + 0.18, faceZ + 0.22);
    addMesh(sign, geo('cone', [0.14, 0.3, 6]), 0xf6d35a, 0.38, G + 0.18, faceZ + 0.22);
    anchors[roadmapBuilding.id].position.set(0, G + 1.78, faceZ);
    lampLight(0, G + 1.4, faceZ + 0.55, 9, sign);
    tag(sign);
  }

  function marker(name, x, y, z) {
    const anchor = new THREE.Object3D();
    anchor.position.set(x, y, z);
    scene.add(anchor);
    anchors[name] = anchor;
  }
  marker('seating', -4.85, G + 1.85, 3.55);
  marker('dock', -0.1, G + 1.15, 8.15);
  ISLANDS.forEach((island) => {
    if (island.id === 'main') return;
    const yOff = (island.id === 'tcm' || island.id === 'computer') ? G + 3.65 : G + 2.6;
    marker('island-' + island.id, island.x, yOff, island.z);
  });
  function tickComputerSite(dt) {
    const model = buildingGroups['computer-site'];
    if (!model || motionReduced) return;
    const step = (dt || 0.016) * 0.22;
    model.traverse((obj) => {
      if (obj.userData && obj.userData.slowSpin) {
        obj.rotation.y += step;
      }
    });
  }

  return {
    get shaker() { return shaker; },
    getTcmSiteModel() { return buildingGroups['tcm-site'] || null; },
    refreshGraffitiWall: () => refreshGraffitiWall(),
    paintRoadmapSign: (copy) => paintRoadmapSign(copy),
    tickComputerSite: (dt) => tickComputerSite(dt)
  };
}
