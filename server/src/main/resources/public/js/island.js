import * as THREE from 'three';

const ZONES = ['devices', 'system', 'photos'];
const POSES = {
  home: { look: [0.15, 0.95, 1.35], radius: 28.4, theta: 0.48, phi: 0.93 },
  devices: { look: [0.1, 0.9, 1.85], radius: 13.2, theta: 0.22, phi: 1.02 },
  system: { look: [-4.05, 1.0, -0.05], radius: 11.6, theta: -0.35, phi: 1.0 },
  photos: { look: [3.85, 1.05, 0.35], radius: 12.2, theta: 1.25, phi: 0.98 }
};

const G = 0.2;
const P = G + 0.055;
const HEX = Math.PI / 6;

const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const stage = document.getElementById('islandStage');
const canvas = document.getElementById('islandCanvas');
if (stage && canvas) boot();

function boot() {
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
  } catch (e) {
    renderer = null;
  }
  if (!renderer || !renderer.getContext()) {
    stage.classList.add('is-fallback');
    const list = document.getElementById('islandList');
    const listBtn = document.getElementById('islandListBtn');
    if (list) list.hidden = false;
    if (listBtn) listBtn.setAttribute('aria-expanded', 'true');
    return;
  }

  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.02;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

  const scene = new THREE.Scene();
  const skyDay = new THREE.Color(0xd8f7f8);
  const skyNight = new THREE.Color(0x1a3e86);
  scene.background = skyDay.clone();
  scene.fog = new THREE.Fog(skyDay.clone(), 78, 176);

  const camera = new THREE.PerspectiveCamera(25, 1, 0.4, 340);
  const clock = new THREE.Clock();

  const hemi = new THREE.HemisphereLight(0xfff8ef, 0xe7d8bc, 0.62);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xfff3d6, 1.42);
  sun.position.set(-9.5, 20.5, 11);
  sun.castShadow = true;
  sun.shadow.mapSize.set(2048, 2048);
  sun.shadow.camera.left = -16;
  sun.shadow.camera.right = 16;
  sun.shadow.camera.top = 16;
  sun.shadow.camera.bottom = -16;
  sun.shadow.camera.near = 2;
  sun.shadow.camera.far = 52;
  sun.shadow.bias = -0.00045;
  sun.shadow.normalBias = 0.032;
  sun.target.position.set(0, 0.4, 0);
  scene.add(sun);
  scene.add(sun.target);

  const hemiDay = new THREE.Color(0xfff8ef);
  const hemiNight = new THREE.Color(0x8eabd4);
  const groundDay = new THREE.Color(0xe7d8bc);
  const groundNight = new THREE.Color(0x243044);
  const sunDay = new THREE.Color(0xfff3d6);
  const sunNight = new THREE.Color(0xc5d4f2);

  const cache = new Map();
  const materialList = [];
  const geos = new Map();
  const anchors = {};
  const rings = {};
  const pickables = [];
  const bobs = [];
  const idles = [];
  const walkers = [];
  const clouds = [];
  const swimmers = [];
  const steams = [];
  const lampLights = [];
  let shaker = null;
  let modeMix = 0;
  let modeTarget = 0;

  const NIGHT = {
    0xa8d06e: 0x3d5d42,
    0x93c462: 0x355538,
    0xc5e098: 0x4d6a45,
    0x7eae58: 0x314e36,
    0x6fa84e: 0x2d4c34,
    0x8fbf62: 0x3a5a3c,
    0x4f8a45: 0x243e2c,
    0x5e9a48: 0x2a4632,
    0xf4e6cf: 0x6e6252,
    0xe4d2b2: 0x5c5346,
    0xfff7ef: 0xd4ccc2,
    0xf3e2cc: 0xc4b6a4,
    0xf06a2a: 0xe07a3a,
    0xd4531c: 0xc4622a,
    0x1f4d73: 0x17324e,
    0x45c4c6: 0x12325f,
    0xe7dcd0: 0xb7aea2,
    0xcfc3b2: 0x8e8578,
    0xd2a56c: 0x8a6844,
    0xb8884e: 0x6d4e32,
    0xc4844a: 0x8a5a32,
    0xa56b38: 0x6e4528,
    0xf0ddb4: 0x8d7b5c,
    0xfffaf6: 0xe6ddd4
  };

  function nightOf(hex) {
    if (Object.prototype.hasOwnProperty.call(NIGHT, hex)) return new THREE.Color(NIGHT[hex]);
    const c = new THREE.Color(hex);
    const hsl = { h: 0, s: 0, l: 0 };
    c.getHSL(hsl);
    const out = new THREE.Color();
    out.setHSL(hsl.h, Math.min(1, hsl.s * 0.78), 0.12 + hsl.l * 0.42);
    return out;
  }

  function mat(color, extra) {
    extra = extra || {};
    const key = extra.map ? '' : [
      color,
      extra.night != null ? extra.night : '',
      extra.glow || 0,
      extra.glowAmount == null ? '' : extra.glowAmount,
      extra.opacity == null ? 1 : extra.opacity,
      extra.side || 0
    ].join('|');
    if (key && cache.has(key)) return cache.get(key);
    const material = new THREE.MeshLambertMaterial({
      color: color,
      flatShading: true,
      map: extra.map || null,
      transparent: extra.opacity != null && extra.opacity < 1,
      opacity: extra.opacity == null ? 1 : extra.opacity,
      side: extra.side || THREE.FrontSide,
      emissive: new THREE.Color(extra.glow || 0x000000),
      emissiveIntensity: 0
    });
    material.userData.day = new THREE.Color(color);
    material.userData.night = new THREE.Color(extra.night != null ? extra.night : nightOf(color));
    material.userData.dayE = 0;
    material.userData.nightE = extra.glow ? (extra.glowAmount == null ? 0.85 : extra.glowAmount) : 0;
    materialList.push(material);
    if (key) cache.set(key, material);
    return material;
  }

  function geo(kind, args) {
    const key = kind + ':' + args.join(',');
    let geometry = geos.get(key);
    if (!geometry) {
      if (kind === 'box') geometry = new THREE.BoxGeometry(args[0], args[1], args[2]);
      else if (kind === 'cyl') geometry = new THREE.CylinderGeometry(args[0], args[1], args[2], args[3], 1, false, args[4] || 0);
      else if (kind === 'sph') geometry = new THREE.SphereGeometry(args[0], args[1], args[2]);
      else if (kind === 'cone') geometry = new THREE.ConeGeometry(args[0], args[1], args[2]);
      geos.set(key, geometry);
    }
    return geometry;
  }

  function addMesh(parent, geometry, color, x, y, z, extra) {
    const mesh = new THREE.Mesh(geometry, mat(color, extra));
    mesh.position.set(x, y, z);
    mesh.castShadow = !extra || extra.shadow !== false;
    mesh.receiveShadow = !extra || extra.receive !== false;
    if (extra && extra.scale) mesh.scale.set(extra.scale[0], extra.scale[1], extra.scale[2]);
    if (extra && extra.rot) mesh.rotation.set(extra.rot[0], extra.rot[1], extra.rot[2]);
    parent.add(mesh);
    return mesh;
  }

  function addBox(parent, w, h, d, color, x, y, z, extra) {
    return addMesh(parent, geo('box', [w, h, d]), color, x, y, z, extra);
  }

  function paint(w, h, draw) {
    const board = document.createElement('canvas');
    board.width = w;
    board.height = h;
    draw(board.getContext('2d'), w, h);
    const texture = new THREE.CanvasTexture(board);
    texture.colorSpace = THREE.SRGBColorSpace;
    texture.anisotropy = 8;
    return texture;
  }

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
    g.fillText('M', 108, h / 2 + 2);
    g.fillStyle = '#1f4d73';
    g.font = '700 88px ' + font;
    g.textAlign = 'left';
    g.fillText('莫卡手搖', 188, h / 2 + 2);
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

  function picture(parent, w, h, texture, x, y, z, rotY, glowAmount, tiltX) {
    const group = new THREE.Group();
    group.position.set(x, y, z);
    if (rotY) group.rotation.y = rotY;
    if (tiltX) group.rotation.x = tiltX;
    addBox(group, w, h, 0.08, 0xf06a2a, 0, 0, -0.02);
    const plane = new THREE.Mesh(
      new THREE.PlaneGeometry(w * 0.9, h * 0.82),
      mat(0xffffff, {
        map: texture,
        night: 0xfff0dc,
        glow: 0xffb15a,
        glowAmount: glowAmount == null ? 0.28 : glowAmount,
        side: THREE.DoubleSide
      })
    );
    plane.position.z = 0.04;
    plane.castShadow = false;
    plane.receiveShadow = false;
    group.add(plane);
    parent.add(group);
    return group;
  }

  const cupBody = geo('cyl', [0.13, 0.105, 0.36, 7, 0]);
  const cupLid = geo('cyl', [0.138, 0.138, 0.045, 7, 0]);
  const cupStraw = geo('cyl', [0.016, 0.016, 0.24, 4, 0]);

  function addCup(parent, color, x, y, z, phase) {
    const group = new THREE.Group();
    const body = addMesh(group, cupBody, color, 0, 0.18, 0);
    const lid = addMesh(group, cupLid, 0xfffaf6, 0, 0.38, 0);
    const straw = addMesh(group, cupStraw, 0x3a312c, 0.03, 0.52, 0);
    straw.rotation.z = 0.22;
    group.position.set(x, y, z);
    parent.add(group);
    if (phase != null) bobs.push({ obj: group, base: y, phase: phase, amp: 0.03 });
    return group;
  }

  function lampLight(x, y, z, power) {
    const light = new THREE.PointLight(0xffb066, 0, 8.5, 2);
    light.position.set(x, y, z);
    scene.add(light);
    lampLights.push({ light: light, power: power });
  }

  function applyMix(t) {
    materialList.forEach((material) => {
      material.color.copy(material.userData.day).lerp(material.userData.night, t);
      material.emissiveIntensity = material.userData.dayE + (material.userData.nightE - material.userData.dayE) * t;
    });
    scene.background.copy(skyDay).lerp(skyNight, t);
    scene.fog.color.copy(scene.background);
    scene.fog.near = 78 + (40 - 78) * t;
    scene.fog.far = 176 + (128 - 176) * t;
    hemi.color.copy(hemiDay).lerp(hemiNight, t);
    hemi.groundColor.copy(groundDay).lerp(groundNight, t);
    hemi.intensity = 0.62 + (0.38 - 0.62) * t;
    sun.color.copy(sunDay).lerp(sunNight, t);
    sun.intensity = 1.42 + (0.5 - 1.42) * t;
    renderer.toneMappingExposure = 1.02 + (0.94 - 1.02) * t;
    lampLights.forEach((item) => {
      item.light.intensity = item.power * t;
    });
  }

  const water = addMesh(scene, new THREE.CircleGeometry(96, 64), 0x45c4c6, 0, -0.74, 0, {
    night: 0x12325f,
    rot: [-Math.PI / 2, 0, 0],
    shadow: false
  });
  water.receiveShadow = true;

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

  const sand = addMesh(scene, geo('cyl', [1.7, 1.95, 0.1, 6, HEX]), 0xf0ddb4, -4.4, -0.7, 7.6, { shadow: false });
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
    lampLight(0, base + 1.35, front + 0.55, 16);
    lampLight(0.2, base + 1.25, -0.2, 9);
  }

  buildShop();

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

  function marker(name, x, y, z) {
    const anchor = new THREE.Object3D();
    anchor.position.set(x, y, z);
    scene.add(anchor);
    anchors[name] = anchor;
  }
  marker('seating', -4.85, G + 1.85, 3.55);
  marker('dock', -0.1, G + 1.15, 8.15);

  function treeRound(x, z, scale, leaf) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.scale.setScalar(scale);
    addMesh(group, geo('cyl', [0.09, 0.13, 0.85, 6, 0]), 0xc4844a, 0, 0.42, 0);
    addMesh(group, geo('sph', [0.58, 6, 5]), leaf, 0, 1.2, 0, { scale: [1.05, 0.82, 1.05] });
    addMesh(group, geo('sph', [0.42, 6, 5]), leaf, 0.36, 1.0, 0.08, { scale: [1, 0.8, 1] });
    addMesh(group, geo('sph', [0.36, 6, 5]), leaf, -0.32, 1.05, -0.06, { scale: [1, 0.75, 1] });
    scene.add(group);
  }
  function treeCone(x, z, scale, leaf) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.scale.setScalar(scale);
    addMesh(group, geo('cyl', [0.08, 0.12, 0.7, 5, 0]), 0xa56b38, 0, 0.35, 0);
    addMesh(group, geo('cone', [0.62, 0.7, 7]), leaf, 0, 0.95, 0);
    addMesh(group, geo('cone', [0.46, 0.55, 7]), leaf, 0, 1.4, 0);
    scene.add(group);
  }
  function treeLayer(x, z, scale, leaf) {
    const group = new THREE.Group();
    group.position.set(x, G, z);
    group.scale.setScalar(scale);
    addMesh(group, geo('cyl', [0.08, 0.11, 0.95, 5, 0]), 0xc4844a, 0, 0.48, 0);
    addMesh(group, geo('sph', [0.7, 6, 5]), leaf, 0, 0.95, 0, { scale: [1, 0.42, 1] });
    addMesh(group, geo('sph', [0.5, 6, 5]), leaf, 0, 1.28, 0, { scale: [1, 0.45, 1] });
    addMesh(group, geo('sph', [0.32, 6, 5]), leaf, 0, 1.55, 0, { scale: [1, 0.5, 1] });
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
    addMesh(scene, bushGeo, color, x, G + 0.2 * s, z, { scale: [s, s * 0.72, s] });
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
  lampLight(1.35, G + 1.25, 8.15, 8);

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

  const boat = new THREE.Group();
  addBox(boat, 1.2, 0.26, 0.48, 0xfff7ef, 0, 0.16, 0);
  addBox(boat, 0.7, 0.16, 0.32, 0xf06a2a, -0.05, 0.32, 0);
  addMesh(boat, geo('cone', [0.24, 0.36, 4]), 0xfff7ef, 0, 0.16, 0.55, { rot: [Math.PI / 2, 0, 0] });
  boat.position.set(1.65, -0.62, 8.7);
  boat.rotation.y = 0.4;
  scene.add(boat);
  bobs.push({ obj: boat, base: -0.62, phase: 0.6, amp: 0.035 });

  function bridge(x1, z1, x2, z2) {
    const dx = x2 - x1;
    const dz = z2 - z1;
    const len = Math.hypot(dx, dz) || 0.001;
    const info = spanDeck(x1, z1, x2, z2, 1.15, 0xc4844a, P);
    [-1, 1].forEach((side) => {
      addBox(scene, len, 0.05, 0.05, 0xfff7ef,
        (x1 + x2) / 2 + info.zx * 0.5 * side,
        P + 0.38,
        (z1 + z2) / 2 + info.zz * 0.5 * side,
        { rot: [0, info.ry, 0] });
    });
    const posts = Math.max(2, Math.round(len / 1.15));
    for (let i = 0; i <= posts; i++) {
      const t = i / posts;
      [-1, 1].forEach((side) => {
        addBox(scene, 0.06, 0.46, 0.06, 0xf3e2cc,
          x1 + dx * t + info.zx * 0.5 * side,
          P + 0.18,
          z1 + dz * t + info.zz * 0.5 * side);
      });
    }
    [0.08, 0.92].forEach((t) => {
      addBox(scene, 0.12, 1.15, 0.12, 0xa56b38, x1 + dx * t, P - 0.5, z1 + dz * t);
    });
  }
  bridge(6.7, 1.75, 9.35, 2.05);

  const islet = new THREE.Group();
  islet.position.set(10.9, 0, 2.3);
  layIsland(islet, 1.65);
  scene.add(islet);
  treeRound(10.55, 2.7, 0.68, 0x6fa84e);
  umbrella(11.35, 1.95, 0xf6d35a, 0.64);
  bench(11.2, 2.4, -0.8);
  bush(10.15, 1.75, 0.34, 0x8fbf62);
  rockAt(12.15, 2.85, 0.28, -0.72);
  rockAt(9.85, 1.45, 0.24, -0.75);

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

  const isletFar = new THREE.Group();
  isletFar.position.set(-15.5, 0, 7.2);
  layIsland(isletFar, 1.7);
  scene.add(isletFar);
  treeCone(-15.2, 7.35, 0.7, 0x6fa84e);
  const isletFar2 = new THREE.Group();
  isletFar2.position.set(18.5, 0, -13.5);
  layIsland(isletFar2, 1.45);
  scene.add(isletFar2);
  bush(18.4, -13.3, 0.7, 0x7eae58);

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

  const headGeo = geo('sph', [0.15, 7, 6]);
  const hairGeo = geo('sph', [0.155, 6, 5]);
  const legGeo = geo('box', [0.11, 0.28, 0.12]);
  const shoeGeo = geo('box', [0.12, 0.06, 0.16]);
  const torsoGeo = geo('box', [0.34, 0.36, 0.18]);
  const armGeo = geo('box', [0.09, 0.28, 0.09]);

  function makePerson(opts) {
    const group = new THREE.Group();
    const shirt = opts.shirt;
    const pants = opts.pants || 0x3e4a5a;
    const hair = opts.hair || 0x3a312c;
    if (opts.seated) {
      addBox(group, 0.12, 0.1, 0.28, pants, -0.08, 0.42, 0.14);
      addBox(group, 0.12, 0.1, 0.28, pants, 0.08, 0.42, 0.14);
      addMesh(group, legGeo, pants, -0.08, 0.18, 0.28);
      addMesh(group, legGeo, pants, 0.08, 0.18, 0.28);
      addMesh(group, shoeGeo, 0x2c2826, -0.08, 0.04, 0.32);
      addMesh(group, shoeGeo, 0x2c2826, 0.08, 0.04, 0.32);
      addMesh(group, torsoGeo, shirt, 0, 0.66, 0.02);
      addMesh(group, armGeo, shirt, -0.22, 0.6, 0.08);
      addMesh(group, armGeo, shirt, 0.22, 0.58, 0.12);
      addMesh(group, headGeo, 0xf3d0b0, 0, 0.98, 0.04);
      const hairMesh = addMesh(group, hairGeo, hair, 0, 1.05, 0);
      hairMesh.scale.set(1.02, 0.58, 1.05);
    } else {
      addMesh(group, legGeo, pants, -0.08, 0.2, 0);
      addMesh(group, legGeo, pants, 0.08, 0.2, 0);
      addMesh(group, shoeGeo, 0x2c2826, -0.08, 0.045, 0.03);
      addMesh(group, shoeGeo, 0x2c2826, 0.08, 0.045, 0.03);
      addMesh(group, torsoGeo, shirt, 0, 0.52, 0);
      addMesh(group, armGeo, shirt, -0.22, 0.5, 0);
      addMesh(group, armGeo, shirt, 0.22, 0.5, 0.02);
      addMesh(group, headGeo, 0xf3d0b0, 0, 0.86, 0);
      const hairMesh = addMesh(group, hairGeo, hair, 0, 0.94, -0.01);
      hairMesh.scale.set(1.02, 0.55, 1.05);
      if (opts.apron) addBox(group, 0.26, 0.28, 0.04, 0xf06a2a, 0, 0.46, 0.11);
      if (opts.hat) addMesh(group, geo('cyl', [0.17, 0.17, 0.08, 6, 0]), opts.hat, 0, 1.04, 0);
      if (opts.cup) addCup(group, opts.cup, 0.2, 0.46, 0.18, null);
    }
    group.scale.setScalar(opts.scale || 1.12);
    return group;
  }

  function placePerson(opts, x, z, rotY, idle) {
    const person = makePerson(opts);
    person.position.set(x, opts.seated ? G : P, z);
    person.rotation.y = rotY || 0;
    scene.add(person);
    if (idle) idles.push({ obj: person, base: person.position.y, phase: idle });
    return person;
  }

  placePerson({ shirt: 0xfff7ef, apron: true, hair: 0x3a312c }, 0.15, 0.42, 0, 0.2);
  placePerson({ shirt: 0xf4a0c0, hair: 0x6b4634 }, 0.05, 2.25, Math.PI, 0.8);
  placePerson({ shirt: 0x7ec8ea, hat: 0xf6d35a }, -0.05, 2.9, Math.PI, 1.4);
  placePerson({ shirt: 0x9fd9b4, hair: 0x3a312c }, 0.12, 3.55, Math.PI, 2.1);
  placePerson({ shirt: 0xf6d35a, seated: true, hair: 0x6b4634 }, -4.85, 4.15, Math.PI, 0.5);
  placePerson({ shirt: 0xf4a0c0, seated: true }, -4.85, 2.95, 0, 1.6);
  placePerson({ shirt: 0x7ec8ea, seated: true, hair: 0x3a312c }, 4.55, 4.08, Math.PI, 2.4);
  placePerson({ shirt: 0xf6d35a, hair: 0x6b4634, cup: 0xf4a0c0 }, -0.15, 8.45, 0.15, 0.9);
  placePerson({ shirt: 0x9fd9b4, seated: true, hair: 0x6b4634 }, 11.25, 2.55, -2.2, 1.1);

  function addWalker(shirt, path, speed, pingpong) {
    const person = makePerson({ shirt: shirt, hair: speed > 0 ? 0x3a312c : 0x6b4634 });
    scene.add(person);
    walkers.push({
      obj: person,
      path: path,
      speed: speed,
      pingpong: !!pingpong,
      offset: Math.random()
    });
  }
  addWalker(0xf08a3a, [
    [-5.6, 4.7], [-1.8, 6.35], [2.1, 6.4], [5.6, 4.3],
    [6.05, 1.0], [5.3, -3.3], [1.1, -5.15], [-3.5, -5.05],
    [-6.15, -1.6], [-6.3, 2.1]
  ], 0.045, false);
  addWalker(0x1f4d73, [[5.6, 1.15], [7.4, 1.6], [9.1, 1.95]], 0.05, true);

  const lookAt = new THREE.Vector3();
  const targetLook = new THREE.Vector3();
  let radius = POSES.home.radius;
  let theta = POSES.home.theta;
  let phi = POSES.home.phi;
  let targetRadius = radius;
  let targetTheta = theta;
  let targetPhi = phi;
  const pointer = new THREE.Vector2();
  const raycaster = new THREE.Raycaster();
  const projected = new THREE.Vector3();
  let running = false;
  let raf = 0;
  let activeZone = '';

  function applyPose(pose, instant) {
    targetLook.set(pose.look[0], pose.look[1], pose.look[2]);
    targetRadius = pose.radius;
    targetTheta = pose.theta;
    targetPhi = pose.phi;
    if (instant || reduced) {
      lookAt.copy(targetLook);
      radius = targetRadius;
      theta = targetTheta;
      phi = targetPhi;
      placeCamera();
    }
  }

  function placeCamera() {
    const aspect = camera.aspect || 1;
    let dist = radius * 1.07;
    if (aspect < 1.02) dist *= Math.min(1.6, 1.02 / Math.max(aspect, 0.5));
    const span = Math.sin(phi);
    camera.position.set(
      lookAt.x + dist * span * Math.sin(theta),
      lookAt.y + dist * Math.cos(phi),
      lookAt.z + dist * span * Math.cos(theta)
    );
    camera.lookAt(lookAt);
    camera.updateMatrixWorld(true);
  }

  function setActive(id) {
    activeZone = id || '';
    ZONES.forEach((zone) => {
      if (rings[zone]) rings[zone].visible = zone === activeZone;
    });
  }

  function focus(id) {
    const pose = POSES[id];
    if (!pose) return;
    applyPose(pose, false);
    setActive(id);
  }

  function reset() {
    applyPose(POSES.home, false);
    setActive('');
  }

  function setMode(next) {
    const night = next === 'night';
    modeTarget = night ? 1 : 0;
    if (reduced || document.hidden) {
      modeMix = modeTarget;
      applyMix(modeMix);
      renderer.render(scene, camera);
    }
    const dayBtn = document.getElementById('islandDay');
    const nightBtn = document.getElementById('islandNight');
    if (dayBtn) dayBtn.setAttribute('aria-pressed', night ? 'false' : 'true');
    if (nightBtn) nightBtn.setAttribute('aria-pressed', night ? 'true' : 'false');
    document.body.classList.toggle('is-night', night);
  }

  function resize() {
    const width = stage.clientWidth || window.innerWidth;
    const height = stage.clientHeight || window.innerHeight;
    camera.aspect = width / Math.max(height, 1);
    camera.fov = 25;
    camera.updateProjectionMatrix();
    renderer.setSize(width, height, false);
  }

  function placeLabels() {
    const width = stage.clientWidth || 1;
    const height = stage.clientHeight || 1;
    const far = targetRadius > 40;
    const nodes = [];
    document.querySelectorAll('[data-anchor]').forEach((el) => {
      const minor = el.hasAttribute('data-minor');
      if (minor && far) {
        el.style.visibility = 'hidden';
        return;
      }
      const anchor = anchors[el.getAttribute('data-anchor')];
      if (!anchor) return;
      anchor.getWorldPosition(projected);
      const dist = projected.distanceTo(camera.position);
      projected.project(camera);
      if (projected.z > 1 || projected.x < -1.08 || projected.x > 1.08 || projected.y < -1.08 || projected.y > 1.08) {
        el.style.visibility = 'hidden';
        return;
      }
      if (minor && dist > 32) {
        el.style.visibility = 'hidden';
        return;
      }
      nodes.push({
        el: el,
        x: (projected.x * 0.5 + 0.5) * width,
        y: (-projected.y * 0.5 + 0.5) * height,
        minor: minor,
        rank: Number(el.getAttribute('data-rank') || 3)
      });
    });
    nodes.sort((a, b) => a.rank - b.rank);
    const placed = [];
    nodes.forEach((node) => {
      const w = node.el.offsetWidth || 76;
      const h = node.el.offsetHeight || 28;
      let y = node.y;
      let hidden = false;
      for (let guard = 0; guard < 5; guard++) {
        const hit = placed.some((item) => {
          return Math.abs(item.x - node.x) < (item.w + w) * 0.48
            && Math.abs(item.y - y) < (item.h + h) * 0.5;
        });
        if (!hit) break;
        if (node.minor) {
          hidden = true;
          break;
        }
        y += h + 8;
      }
      if (hidden) {
        node.el.style.visibility = 'hidden';
        return;
      }
      node.el.style.visibility = 'visible';
      node.el.style.transform = 'translate(' + node.x + 'px, ' + y + 'px) translate(-50%, -120%)';
      placed.push({ x: node.x, y: y, w: w, h: h });
    });
  }

  function ndc(event) {
    const rect = canvas.getBoundingClientRect();
    pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
  }

  function hitZone(event) {
    ndc(event);
    raycaster.setFromCamera(pointer, camera);
    const hits = raycaster.intersectObjects(pickables, false);
    return hits.length ? hits[0].object.userData.zone : '';
  }

  let dragging = false;
  let moved = false;
  let lastX = 0;
  let lastY = 0;

  canvas.addEventListener('pointerdown', (event) => {
    dragging = true;
    moved = false;
    lastX = event.clientX;
    lastY = event.clientY;
    canvas.style.cursor = 'grabbing';
    if (canvas.setPointerCapture) canvas.setPointerCapture(event.pointerId);
  });
  canvas.addEventListener('pointermove', (event) => {
    if (!dragging) {
      canvas.style.cursor = hitZone(event) ? 'pointer' : 'grab';
      return;
    }
    const dx = event.clientX - lastX;
    const dy = event.clientY - lastY;
    if (dx * dx + dy * dy > 9) moved = true;
    lastX = event.clientX;
    lastY = event.clientY;
    targetTheta -= dx * 0.005;
    targetPhi = Math.min(1.15, Math.max(0.58, targetPhi - dy * 0.0035));
  });
  canvas.addEventListener('pointerup', (event) => {
    dragging = false;
    canvas.style.cursor = 'grab';
    if (!moved) {
      const zone = hitZone(event);
      if (zone && typeof window.switchDashTab === 'function') window.switchDashTab(zone);
    }
  });
  canvas.addEventListener('pointerleave', () => {
    if (!dragging) canvas.style.cursor = 'grab';
  });
  canvas.addEventListener('wheel', (event) => {
    event.preventDefault();
    targetRadius = Math.min(52, Math.max(11, targetRadius + event.deltaY * 0.01));
  }, { passive: false });

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

  function frame() {
    const k = reduced ? 1 : 0.08;
    lookAt.lerp(targetLook, k);
    radius += (targetRadius - radius) * k;
    let dTheta = targetTheta - theta;
    dTheta = Math.atan2(Math.sin(dTheta), Math.cos(dTheta));
    theta += dTheta * k;
    phi += (targetPhi - phi) * k;
    placeCamera();
    if (reduced) modeMix = modeTarget;
    else modeMix += (modeTarget - modeMix) * 0.06;
    applyMix(modeMix);
    const t = clock.getElapsedTime();
    walkers.forEach((walker) => {
      const along = reduced ? walker.offset : walker.offset + t * Math.abs(walker.speed);
      const step = sampleWalker(walker, walker.pingpong ? along * walker.path.length : along);
      const dx = step.to[0] - step.from[0];
      const dz = step.to[1] - step.from[1];
      walker.obj.position.set(
        step.from[0] + dx * step.blend,
        P,
        step.from[1] + dz * step.blend
      );
      if (dx || dz) walker.obj.rotation.y = Math.atan2(dx * step.dir, dz * step.dir);
    });
    if (!reduced) {
      bobs.forEach((bob) => {
        bob.obj.position.y = bob.base + Math.sin(t * 1.6 + bob.phase) * bob.amp;
      });
      idles.forEach((item) => {
        item.obj.position.y = item.base + Math.sin(t * 1.5 + item.phase) * 0.012;
      });
      steams.forEach((puff) => {
        puff.position.y = puff.userData.base + Math.sin(t * 1.7 + puff.userData.phase) * 0.08;
      });
      if (shaker) shaker.rotation.y = t * 1.6;
      clouds.forEach((item) => {
        item.position.x = item.userData.origin + Math.sin(t * item.userData.drift) * 1.4;
      });
      swimmers.forEach((fish) => {
        const angle = t * fish.speed + fish.phase;
        fish.obj.position.set(Math.cos(angle) * fish.radius, fish.y, Math.sin(angle) * fish.radius);
        fish.obj.rotation.y = Math.atan2(-Math.sin(angle), Math.cos(angle));
      });
    }
    renderer.render(scene, camera);
    placeLabels();
  }

  function start() {
    if (running) return;
    running = true;
    clock.getDelta();
    const loop = () => {
      if (!running) return;
      frame();
      raf = window.requestAnimationFrame(loop);
    };
    raf = window.requestAnimationFrame(loop);
  }

  function stop() {
    running = false;
    window.cancelAnimationFrame(raf);
  }

  window.addEventListener('resize', resize);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) stop();
    else start();
  });

  const list = document.getElementById('islandList');
  const listBtn = document.getElementById('islandListBtn');
  if (listBtn && list) {
    listBtn.addEventListener('click', () => {
      list.hidden = !list.hidden;
      listBtn.setAttribute('aria-expanded', list.hidden ? 'false' : 'true');
    });
  }
  const home = document.getElementById('islandHome');
  if (home) {
    home.addEventListener('click', () => {
      if (typeof window.switchDashTab === 'function') window.switchDashTab('');
      if (list) list.hidden = true;
      if (listBtn) listBtn.setAttribute('aria-expanded', 'false');
    });
  }
  const closeBtn = document.getElementById('islandSheetClose');
  if (closeBtn) {
    closeBtn.addEventListener('click', () => {
      if (typeof window.switchDashTab === 'function') window.switchDashTab('');
    });
  }
  const dayBtn = document.getElementById('islandDay');
  const nightBtn = document.getElementById('islandNight');
  if (dayBtn) dayBtn.addEventListener('click', () => setMode('day'));
  if (nightBtn) nightBtn.addEventListener('click', () => setMode('night'));
  window.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    const tagName = event.target && event.target.tagName;
    if (tagName === 'INPUT' || tagName === 'TEXTAREA') return;
    const sheet = document.getElementById('islandSheet');
    if (sheet && !sheet.hidden && typeof window.switchDashTab === 'function') {
      window.switchDashTab('');
    }
  });

  window.PunchClockIsland = { focus: focus, reset: reset, setMode: setMode };

  resize();
  applyMix(0);
  const open = document.querySelector('.dash-panel.is-active');
  if (open && !open.hasAttribute('hidden')) {
    const id = open.id.replace('panel-', '');
    applyPose(POSES[id] || POSES.home, true);
    setActive(POSES[id] ? id : '');
  } else {
    applyPose(POSES.home, true);
  }
  renderer.render(scene, camera);
  placeLabels();
  if (!document.hidden) start();
}
