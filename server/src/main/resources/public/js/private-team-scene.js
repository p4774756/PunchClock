import * as THREE from './vendor/three.module.js';

const COL = {
  wood: 0xc9a66b,
  woodDk: 0xa07840,
  wall: 0xd8e4f4,
  wallDk: 0xb8c8dc,
  rug: 0x6a8fd4,
  rugEdge: 0xa8d0f2,
  desk: 0x8b6914,
  chair: 0x4a5568,
  screen: 0x1a2438,
  screenGlow: 0x5cb8ff,
  progress: 0x2e9a4a,
  hair: 0xf4a8c8,
  skin: 0xffe0d4,
  shirt: 0x8a96a8,
  plant: 0x3d8b4a,
  pot: 0x8b5a3c,
  mug: 0xf8fbff,
  sky: 0x9ec8f0,
  skyNight: 0x1a2848,
  building: 0x5a6a88,
  buildingLit: 0xf8dc6e,
  navy: 0x163a70
};

function box(w, h, d, color) {
  const g = new THREE.BoxGeometry(w, h, d);
  const m = new THREE.MeshLambertMaterial({ color });
  return new THREE.Mesh(g, m);
}

function cyl(rt, rb, h, seg, color) {
  const g = new THREE.CylinderGeometry(rt, rb, h, seg);
  const m = new THREE.MeshLambertMaterial({ color });
  return new THREE.Mesh(g, m);
}

function labelTexture(text, w, h, opts) {
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = opts && opts.bg ? opts.bg : '#f8fbff';
  ctx.fillRect(0, 0, w, h);
  if (opts && opts.border) {
    ctx.strokeStyle = '#163a70';
    ctx.lineWidth = 6;
    ctx.strokeRect(4, 4, w - 8, h - 8);
  }
  ctx.fillStyle = opts && opts.color ? opts.color : '#163a70';
  ctx.font = 'bold ' + (opts && opts.fontSize ? opts.fontSize : 28) + 'px "Noto Sans TC", sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, w / 2, h / 2);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function screenTexture(isDay, pulse) {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 96;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#1a2438';
  ctx.fillRect(0, 0, 128, 96);
  const glow = isDay ? '#5cb8ff' : '#78c8ff';
  const a = 0.35 + pulse * 0.25;
  ctx.fillStyle = 'rgba(92, 184, 255, ' + a + ')';
  ctx.fillRect(12, 14, 104, 68);
  ctx.fillStyle = glow;
  ctx.fillRect(20, 24, 40, 8);
  ctx.fillRect(20, 38, 70, 6);
  ctx.fillRect(20, 50, 56, 6);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function progressTexture(pct) {
  const canvas = document.createElement('canvas');
  canvas.width = 200;
  canvas.height = 120;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#1a2438';
  ctx.fillRect(0, 0, 200, 120);
  ctx.fillStyle = '#8a96a8';
  ctx.font = 'bold 18px sans-serif';
  ctx.fillText('進度', 12, 28);
  ctx.fillStyle = '#2e4a3a';
  ctx.fillRect(12, 40, 176, 20);
  ctx.fillStyle = '#2e9a4a';
  ctx.fillRect(12, 40, Math.max(8, 176 * pct), 20);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function bubbleTexture(alpha) {
  const canvas = document.createElement('canvas');
  canvas.width = 96;
  canvas.height = 48;
  const ctx = canvas.getContext('2d');
  ctx.globalAlpha = alpha;
  ctx.fillStyle = '#f8fbff';
  ctx.strokeStyle = '#163a70';
  ctx.lineWidth = 4;
  const r = 10;
  const x = 8;
  const y = 6;
  const w = 80;
  const h = 30;
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.lineTo(x + w - r, y);
  ctx.quadraticCurveTo(x + w, y, x + w, y + r);
  ctx.lineTo(x + w, y + h - r);
  ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  ctx.lineTo(x + r, y + h);
  ctx.quadraticCurveTo(x, y + h, x, y + h - r);
  ctx.lineTo(x, y + r);
  ctx.quadraticCurveTo(x, y, x + r, y);
  ctx.closePath();
  ctx.fill();
  ctx.stroke();
  ctx.fillStyle = '#163a70';
  ctx.font = 'bold 22px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('…', 48, 20);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function buildSecretary() {
  const g = new THREE.Group();
  const body = box(0.42, 0.38, 0.32, COL.shirt);
  body.position.y = 0.52;
  g.add(body);

  const head = new THREE.Mesh(new THREE.SphereGeometry(0.2, 12, 10), new THREE.MeshLambertMaterial({ color: COL.skin }));
  head.position.set(0, 0.92, 0.02);
  g.add(head);

  const hair = box(0.44, 0.42, 0.36, COL.hair);
  hair.position.set(0, 0.9, -0.06);
  g.add(hair);

  const eyeL = new THREE.Mesh(new THREE.SphereGeometry(0.03, 6, 6), new THREE.MeshLambertMaterial({ color: COL.navy }));
  eyeL.position.set(-0.06, 0.94, 0.16);
  const eyeR = eyeL.clone();
  eyeR.position.x = 0.06;
  g.add(eyeL, eyeR);

  const smile = box(0.1, 0.02, 0.02, COL.navy);
  smile.position.set(0, 0.86, 0.17);
  g.add(smile);

  const armL = cyl(0.05, 0.05, 0.28, 8, COL.skin);
  armL.position.set(-0.3, 0.58, 0.1);
  armL.rotation.z = 0.35;
  const armR = cyl(0.05, 0.05, 0.28, 8, COL.skin);
  armR.position.set(0.3, 0.58, 0.1);
  armR.rotation.z = -0.35;
  g.add(armL, armR);

  return { group: g, armL, armR };
}

export function mountPrivateTeamScene(canvas, opts) {
  const win = (opts && opts.win) || globalThis;
  const doc = (opts && opts.doc) || globalThis.document;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false });
  renderer.setPixelRatio(Math.min(win.devicePixelRatio || 1, 2));
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x0a1628);

  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 80);
  camera.position.set(5.2, 4.4, 5.8);
  camera.lookAt(0.2, 0.7, 0);

  const amb = new THREE.AmbientLight(0xffffff, 0.65);
  const dir = new THREE.DirectionalLight(0xfff4e8, 0.85);
  dir.position.set(4, 8, 5);
  scene.add(amb, dir);

  const root = new THREE.Group();
  scene.add(root);

  const floor = new THREE.Group();
  for (let i = 0; i < 8; i++) {
    for (let j = 0; j < 6; j++) {
      const plank = box(0.48, 0.04, 0.18, (i + j) % 2 === 0 ? COL.wood : COL.woodDk);
      plank.position.set(-1.7 + i * 0.5, 0, -0.45 + j * 0.2);
      floor.add(plank);
    }
  }
  root.add(floor);

  const rug = box(2.4, 0.03, 1.6, COL.rug);
  rug.position.set(0.1, 0.04, 0.1);
  root.add(rug);
  const edges = [
    [1.22, 0.05, 0.02, 2.42, 0.06, 0.02],
    [-1.02, 0.05, 0.02, 2.42, 0.06, 0.02],
    [0.1, 0.05, 0.82, 0.02, 0.06, 1.62],
    [0.1, 0.05, -0.62, 0.02, 0.06, 1.62]
  ];
  edges.forEach((e) => {
    const edge = box(e[3], e[4], e[5], COL.rugEdge);
    edge.position.set(e[0], e[1], e[2]);
    root.add(edge);
  });

  const wall = box(5.2, 2.4, 0.12, COL.wall);
  wall.position.set(0, 1.2, -1.15);
  root.add(wall);

  const winGroup = new THREE.Group();
  const winFrame = box(2.2, 1.2, 0.08, COL.wallDk);
  winFrame.position.set(0.8, 1.45, -1.08);
  winGroup.add(winFrame);
  const glass = box(2.0, 1.0, 0.02, COL.sky);
  glass.position.set(0.8, 1.45, -1.02);
  glass.name = 'windowGlass';
  winGroup.add(glass);

  const skyline = new THREE.Group();
  skyline.name = 'skyline';
  const offsets = [-0.7, -0.35, 0, 0.35, 0.65, 0.95];
  const heights = [0.45, 0.7, 0.55, 0.85, 0.5, 0.65];
  offsets.forEach((ox, i) => {
    const b = box(0.22, heights[i], 0.08, COL.building);
    b.position.set(0.8 + ox, 1.0 + heights[i] / 2, -0.95);
    b.name = 'building';
    skyline.add(b);
  });
  winGroup.add(skyline);
  root.add(winGroup);

  const progMesh = new THREE.Mesh(
    new THREE.PlaneGeometry(1.0, 0.6),
    new THREE.MeshBasicMaterial({ map: progressTexture(0.62) })
  );
  progMesh.position.set(-1.5, 1.55, -1.04);
  root.add(progMesh);

  const desk = box(1.5, 0.08, 0.7, COL.desk);
  desk.position.set(0.05, 0.45, 0.15);
  root.add(desk);

  const monitorL = box(0.42, 0.32, 0.04, 0x2a3648);
  monitorL.position.set(-0.25, 0.78, 0.05);
  const monitorR = monitorL.clone();
  monitorR.position.x = 0.35;
  root.add(monitorL, monitorR);

  const scrL = new THREE.Mesh(
    new THREE.PlaneGeometry(0.36, 0.26),
    new THREE.MeshBasicMaterial({ map: screenTexture(true, 0.5) })
  );
  scrL.position.set(-0.25, 0.78, 0.08);
  const scrR = scrL.clone();
  scrR.position.x = 0.35;
  scrL.name = 'screenL';
  scrR.name = 'screenR';
  root.add(scrL, scrR);

  const mug = cyl(0.06, 0.05, 0.12, 10, COL.mug);
  mug.position.set(0.65, 0.55, 0.2);
  root.add(mug);

  const pot = cyl(0.1, 0.08, 0.12, 10, COL.pot);
  pot.position.set(-1.0, 0.52, 0.45);
  const plant = new THREE.Mesh(new THREE.ConeGeometry(0.18, 0.28, 6), new THREE.MeshLambertMaterial({ color: COL.plant }));
  plant.position.set(-1.0, 0.78, 0.45);
  root.add(pot, plant);

  const chairSeat = box(0.38, 0.06, 0.36, COL.chair);
  chairSeat.position.set(0.05, 0.32, 0.55);
  root.add(chairSeat);

  const sec = buildSecretary();
  sec.group.position.set(0.05, 0.28, 0.48);
  root.add(sec.group);

  const namePlate = new THREE.Mesh(
    new THREE.PlaneGeometry(0.55, 0.18),
    new THREE.MeshBasicMaterial({ map: labelTexture('私人秘書', 256, 80, { border: true, fontSize: 32 }) })
  );
  namePlate.position.set(0.05, 0.62, 0.42);
  namePlate.rotation.x = -0.15;
  root.add(namePlate);

  const bubbleMat = new THREE.MeshBasicMaterial({
    map: bubbleTexture(1),
    transparent: true,
    opacity: 1,
    depthWrite: false
  });
  const bubble = new THREE.Mesh(new THREE.PlaneGeometry(0.45, 0.22), bubbleMat);
  bubble.position.set(0.05, 1.22, 0.5);
  root.add(bubble);

  let dayMode = true;
  let running = true;
  let raf = 0;
  let t0 = performance.now();

  function resize() {
    const parent = canvas.parentElement;
    const w = parent ? parent.clientWidth : 1;
    const h = parent ? parent.clientHeight : 1;
    renderer.setSize(w, h, false);
    camera.aspect = w / Math.max(h, 1);
    camera.updateProjectionMatrix();
  }

  function setDayMode(next) {
    dayMode = !!next;
    scene.background.setHex(dayMode ? 0x0a1628 : 0x050a14);
    amb.intensity = dayMode ? 0.65 : 0.35;
      dir.intensity = dayMode ? 0.85 : 0.45;
    dir.color.setHex(dayMode ? 0xfff4e8 : 0xc8d8ff);
    const glassMesh = winGroup.getObjectByName('windowGlass');
    if (glassMesh && glassMesh.material) {
      glassMesh.material.color.setHex(dayMode ? COL.sky : COL.skyNight);
    }
    skyline.children.forEach((b, i) => {
      if (b.name === 'building') {
        b.material.color.setHex(dayMode ? COL.building : (i % 2 === 0 ? COL.buildingLit : COL.building));
      }
    });
  }

  function tick(now) {
    if (!running) return;
    raf = win.requestAnimationFrame(tick);
    const elapsed = (now - t0) / 1000;
    const type = Math.sin(elapsed * 6);
    sec.armL.rotation.x = type * 0.25;
    sec.armR.rotation.x = -type * 0.25;

    const bubblePhase = (Math.sin(elapsed * 2.2) + 1) / 2;
    bubbleMat.opacity = 0.45 + bubblePhase * 0.55;
    bubbleMat.map = bubbleTexture(bubbleMat.opacity);
    bubbleMat.needsUpdate = true;

    const pulse = (Math.sin(elapsed * 3) + 1) / 2;
    [scrL, scrR].forEach((s) => {
      s.material.map = screenTexture(dayMode, pulse);
      s.material.needsUpdate = true;
    });

    root.rotation.y = Math.sin(elapsed * 0.12) * 0.06;

    renderer.render(scene, camera);
  }

  resize();
  win.addEventListener('resize', resize);
  if (typeof ResizeObserver !== 'undefined' && canvas.parentElement) {
    const ro = new ResizeObserver(resize);
    ro.observe(canvas.parentElement);
  }
  raf = win.requestAnimationFrame(tick);

  return {
    setDayMode,
    resize,
    destroy() {
      running = false;
      win.cancelAnimationFrame(raf);
      win.removeEventListener('resize', resize);
      renderer.dispose();
    }
  };
}