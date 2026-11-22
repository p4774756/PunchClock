import * as THREE from './vendor/three.module.js';

export const SECRETARY_SPRITE_URL = '/assets/private-team/secretary.webp';

/** 立繪在場景中的高度（公尺比例）；寬度依紋理長寬比計算 */
const SECRETARY_SPRITE_HEIGHT = 1.42;

const COL = {
  wood: 0xc9a66b,
  woodDk: 0xa07840,
  wall: 0xd8e4f4,
  rug: 0x6a8fd4,
  rugEdge: 0xa8d0f2,
  desk: 0x8b6914,
  screen: 0x1a2438,
  progress: 0x2e9a4a,
  plant: 0x3d8b4a,
  pot: 0x8b5a3c,
  mug: 0xf8fbff,
  sky: 0x9ec8f0,
  skyNight: 0x1a2848,
  building: 0x4a5c7a,
  buildingLit: 0xf8dc6e,
  navy: 0x163a70,
  frame: 0x243044
};

function box(w, h, d, color, basic) {
  const g = new THREE.BoxGeometry(w, h, d);
  const m = basic
    ? new THREE.MeshBasicMaterial({ color })
    : new THREE.MeshLambertMaterial({ color });
  return new THREE.Mesh(g, m);
}

function cyl(rt, rb, h, seg, color) {
  const g = new THREE.CylinderGeometry(rt, rb, h, seg);
  const m = new THREE.MeshLambertMaterial({ color });
  return new THREE.Mesh(g, m);
}

function labelTexture(text, w, h) {
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#f8fbff';
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = '#163a70';
  ctx.lineWidth = 18;
  ctx.strokeRect(10, 10, w - 20, h - 20);
  ctx.fillStyle = '#163a70';
  ctx.font = 'bold 92px sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(text, w / 2, h / 2 + 4);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function makeScreenPainter() {
  const canvas = document.createElement('canvas');
  canvas.width = 160;
  canvas.height = 110;
  const ctx = canvas.getContext('2d');
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  const mat = new THREE.MeshBasicMaterial({ map: tex });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(0.56, 0.38), mat);
  mesh.position.z = 0.04;
  function draw(isDay, pulse) {
    ctx.fillStyle = '#121a2c';
    ctx.fillRect(0, 0, 160, 110);
    const glow = isDay ? '#7ec8ff' : '#b9e2ff';
    ctx.fillStyle = 'rgba(94, 186, 255, ' + (0.45 + pulse * 0.45) + ')';
    ctx.fillRect(12, 14, 136, 82);
    ctx.fillStyle = glow;
    const slide = (pulse * 28) | 0;
    ctx.fillRect(22, 26, 48 + slide, 10);
    ctx.fillRect(22, 46, 90, 8);
    ctx.fillRect(22, 64, 62 + (18 - slide / 2), 8);
    tex.needsUpdate = true;
  }
  return { mesh, draw };
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

function makeBubbleDrawer() {
  const canvas = document.createElement('canvas');
  canvas.width = 320;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  function draw(text) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#f8fbff';
    ctx.strokeStyle = '#163a70';
    ctx.lineWidth = 10;
    const x = 16;
    const y = 12;
    const w = 288;
    const h = 78;
    const r = 22;
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + 182, y + h);
    ctx.lineTo(x + 150, y + h + 26);
    ctx.lineTo(x + 140, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    const label = text == null || String(text).trim() === '' ? '…' : String(text);
    const size = label.length > 6 ? 46 : label.length > 4 ? 52 : 60;
    ctx.fillStyle = '#163a70';
    ctx.font = 'bold ' + size + 'px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, canvas.width / 2, 50);
    tex.needsUpdate = true;
  }
  draw('…');
  return { tex, draw };
}

function softShadowTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 64;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, 128, 64);
  const g = ctx.createRadialGradient(64, 32, 2, 64, 32, 60);
  g.addColorStop(0, 'rgba(10, 18, 36, 0.55)');
  g.addColorStop(0.55, 'rgba(10, 18, 36, 0.22)');
  g.addColorStop(1, 'rgba(10, 18, 36, 0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 128, 64);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function spriteAspectFromTexture(tex) {
  const img = tex && tex.image;
  if (img && img.width > 0 && img.height > 0) {
    return img.width / img.height;
  }
  return 526 / 768;
}

function addMonitor(root, camera, x, z, scale) {
  const group = new THREE.Group();
  const s = scale == null ? 1 : scale;
  group.position.set(x, 0.86, z);
  group.scale.set(s, s, s);
  const bezel = box(0.64, 0.46, 0.05, 0x1a2438);
  const painter = makeScreenPainter();
  const stand = box(0.1, 0.28, 0.08, 0x2a3648);
  stand.position.y = -0.34;
  group.add(bezel, painter.mesh, stand);
  group.lookAt(camera.position.x, 1.15, camera.position.z);
  root.add(group);
  return painter;
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
  camera.position.set(4.8, 4.8, 6.2);
  camera.lookAt(0.15, 0.85, 0.05);

  const amb = new THREE.AmbientLight(0xffffff, 0.72);
  const dir = new THREE.DirectionalLight(0xfff4e8, 0.9);
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
  const edgeMat = new THREE.MeshBasicMaterial({ color: COL.rugEdge });
  [
    [2.4, 0.045, 0.07, 0.1, 0.075, 0.9],
    [2.4, 0.045, 0.07, 0.1, 0.075, -0.7],
    [0.07, 0.045, 1.6, -1.1, 0.075, 0.1],
    [0.07, 0.045, 1.6, 1.3, 0.075, 0.1]
  ].forEach((e) => {
    const edge = new THREE.Mesh(new THREE.BoxGeometry(e[0], e[1], e[2]), edgeMat);
    edge.position.set(e[3], e[4], e[5]);
    root.add(edge);
  });

  function wallPiece(w, h, x, y) {
    const m = box(w, h, 0.12, COL.wall);
    m.position.set(x, y, -1.15);
    root.add(m);
  }
  wallPiece(2.45, 2.4, -1.375, 1.2);
  wallPiece(0.75, 2.4, 2.225, 1.2);
  wallPiece(2.0, 0.95, 0.85, 0.475);
  wallPiece(2.0, 0.35, 0.85, 2.225);

  const skyMat = new THREE.MeshBasicMaterial({ color: COL.sky });
  const windowSky = new THREE.Mesh(new THREE.PlaneGeometry(2.15, 1.25), skyMat);
  windowSky.position.set(0.85, 1.5, -1.52);
  windowSky.name = 'windowSky';
  root.add(windowSky);

  const skyline = new THREE.Group();
  skyline.name = 'skyline';
  const buildingSpecs = [
    { x: 0.08, w: 0.26, h: 0.36 },
    { x: 0.40, w: 0.32, h: 0.7 },
    { x: 0.74, w: 0.2, h: 0.46 },
    { x: 1.08, w: 0.36, h: 0.86 },
    { x: 1.46, w: 0.24, h: 0.52 },
    { x: 1.74, w: 0.18, h: 0.32 }
  ];
  const buildingShades = [0x516484, 0x3e516e, 0x5a6d90, 0x445878, 0x4c6286, 0x3a4c68];
  buildingSpecs.forEach((spec, i) => {
    const b = box(spec.w, spec.h, 0.14, buildingShades[i]);
    b.position.set(spec.x, 1.02 + spec.h / 2, -1.36);
    b.name = 'building';
    skyline.add(b);
    const cols = spec.w > 0.28 ? 2 : 1;
    const rows = spec.h > 0.6 ? 3 : 2;
    for (let c = 0; c < cols; c++) {
      for (let r = 0; r < rows; r++) {
        const lit = box(0.05, 0.05, 0.02, 0xd5e2f2, true);
        const ox = (c - (cols - 1) / 2) * 0.1;
        const oy = (r - (rows - 1) / 2) * 0.14;
        lit.position.set(spec.x + ox, 1.02 + spec.h / 2 + oy, -1.28);
        lit.name = 'bwin';
        skyline.add(lit);
      }
    }
  });
  root.add(skyline);

  const glassMat = new THREE.MeshLambertMaterial({
    color: COL.sky,
    transparent: true,
    opacity: 0.22,
    depthWrite: false
  });
  const glass = new THREE.Mesh(new THREE.BoxGeometry(1.9, 1.02, 0.02), glassMat);
  glass.position.set(0.85, 1.5, -1.08);
  glass.name = 'windowGlass';
  root.add(glass);

  function frameBar(w, h, x, y) {
    const m = box(w, h, 0.08, COL.frame);
    m.position.set(x, y, -1.02);
    root.add(m);
  }
  frameBar(0.1, 1.18, -0.15, 1.5);
  frameBar(0.1, 1.18, 1.85, 1.5);
  frameBar(2.1, 0.1, 0.85, 2.05);
  frameBar(2.1, 0.1, 0.85, 0.95);
  frameBar(0.06, 1.0, 0.85, 1.5);
  frameBar(1.9, 0.06, 0.85, 1.5);

  const progMesh = new THREE.Mesh(
    new THREE.PlaneGeometry(1.0, 0.6),
    new THREE.MeshBasicMaterial({ map: progressTexture(0.62) })
  );
  progMesh.position.set(-1.55, 1.55, -1.04);
  root.add(progMesh);

  const desk = box(2.5, 0.08, 0.9, COL.desk);
  desk.position.set(0.1, 0.46, 0.18);
  root.add(desk);

  const monitorL = addMonitor(root, camera, -0.92, 0.2, 0.92);
  const monitorR = addMonitor(root, camera, 0.72, 0.22, 0.88);

  const mug = new THREE.Mesh(
    new THREE.CylinderGeometry(0.09, 0.075, 0.18, 12),
    new THREE.MeshBasicMaterial({ color: COL.mug })
  );
  mug.position.set(-0.42, 0.64, 0.46);
  const mugHandle = new THREE.Mesh(
    new THREE.TorusGeometry(0.05, 0.015, 6, 10),
    new THREE.MeshBasicMaterial({ color: COL.mug })
  );
  mugHandle.position.set(-0.5, 0.64, 0.46);
  mugHandle.rotation.y = Math.PI / 2;
  root.add(mug, mugHandle);

  const pot = cyl(0.1, 0.08, 0.12, 10, COL.pot);
  pot.position.set(-1.35, 0.12, 0.55);
  const plant = new THREE.Mesh(
    new THREE.ConeGeometry(0.18, 0.28, 6),
    new THREE.MeshLambertMaterial({ color: COL.plant })
  );
  plant.position.set(-1.35, 0.34, 0.55);
  root.add(pot, plant);

  const secretaryRoot = new THREE.Group();
  secretaryRoot.position.set(0.04, 0, 0.62);
  root.add(secretaryRoot);

  const shadowMat = new THREE.MeshBasicMaterial({
    map: softShadowTexture(),
    transparent: true,
    depthWrite: false
  });
  const shadow = new THREE.Mesh(new THREE.PlaneGeometry(1.16, 0.6), shadowMat);
  shadow.rotation.x = -Math.PI / 2;
  // 地毯盒中心 y=0.04、厚 0.03，頂面在 y=0.055。陰影要高於頂面，並落在椅輪與腳前的地毯上。
  shadow.position.set(0.08, 0.072, 0.22);
  shadow.renderOrder = 1;
  secretaryRoot.add(shadow);

  let secretarySprite = null;
  const spriteBaseY = SECRETARY_SPRITE_HEIGHT / 2 + 0.02;
  const loader = new THREE.TextureLoader();
  loader.load(
    SECRETARY_SPRITE_URL,
    (tex) => {
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.premultiplyAlpha = true;
      const aspect = spriteAspectFromTexture(tex);
      const spriteW = SECRETARY_SPRITE_HEIGHT * aspect;
      const mat = new THREE.MeshBasicMaterial({
        map: tex,
        transparent: true,
        alphaTest: 0.06,
        depthWrite: false,
        side: THREE.DoubleSide
      });
      secretarySprite = new THREE.Mesh(new THREE.PlaneGeometry(spriteW, SECRETARY_SPRITE_HEIGHT), mat);
      secretarySprite.position.set(0, spriteBaseY, 0);
      secretarySprite.renderOrder = 2;
      secretarySprite.name = 'secretarySprite';
      secretaryRoot.add(secretarySprite);
      if (clickables.indexOf(secretarySprite) === -1) {
        clickables.push(secretarySprite);
      }
    },
    undefined,
    () => {
      /* 紋理失敗時略過人物，其餘場景照常 */
    }
  );

  const namePlate = new THREE.Mesh(
    new THREE.PlaneGeometry(0.92, 0.32),
    new THREE.MeshBasicMaterial({
      map: labelTexture('私人秘書', 640, 220),
      side: THREE.DoubleSide
    })
  );
  namePlate.position.set(0.78, 1.9, 0.74);
  root.add(namePlate);
  const nameStand = box(0.06, 0.16, 0.06, COL.navy);
  nameStand.position.set(0.78, 1.8, 0.74);
  root.add(nameStand);

  const bubbleDrawer = makeBubbleDrawer();
  const bubbleMat = new THREE.MeshBasicMaterial({
    map: bubbleDrawer.tex,
    transparent: true,
    depthWrite: false
  });
  const bubble = new THREE.Mesh(new THREE.PlaneGeometry(0.82, 0.36), bubbleMat);
  bubble.position.set(0.38, 2.08, 0.72);
  bubble.renderOrder = 3;
  bubble.name = 'secretaryBubble';
  root.add(bubble);

  const clickables = [bubble];
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const onActivate = typeof (opts && opts.onActivate) === 'function' ? opts.onActivate : null;

  function pickSecretary(clientX, clientY) {
    const rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return false;
    pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(pointer, camera);
    const hits = raycaster.intersectObjects(clickables, false);
    return hits.length > 0;
  }

  function handlePointerDown(ev) {
    if (!onActivate) return;
    if (pickSecretary(ev.clientX, ev.clientY)) {
      ev.preventDefault();
      onActivate();
    }
  }

  canvas.addEventListener('pointerdown', handlePointerDown);

  let dayMode = true;
  let running = true;
  let reducedMotion = false;
  if (typeof win.matchMedia === 'function') {
    const mq = win.matchMedia('(prefers-reduced-motion: reduce)');
    reducedMotion = !!mq.matches;
    if (typeof mq.addEventListener === 'function') {
      mq.addEventListener('change', () => {
        reducedMotion = !!mq.matches;
      });
    } else if (typeof mq.addListener === 'function') {
      mq.addListener(() => {
        reducedMotion = !!mq.matches;
      });
    }
  }
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
    amb.intensity = dayMode ? 0.72 : 0.32;
    dir.intensity = dayMode ? 0.9 : 0.4;
    dir.color.setHex(dayMode ? 0xfff4e8 : 0xc8d8ff);
    glassMat.color.setHex(dayMode ? COL.sky : COL.skyNight);
    skyMat.color.setHex(dayMode ? COL.sky : COL.skyNight);
    skyline.children.forEach((b) => {
      if (b.name === 'bwin' && b.material) {
        b.material.color.setHex(dayMode ? 0xd5e2f2 : COL.buildingLit);
      }
    });
  }

  function tick(now) {
    if (!running) return;
    raf = win.requestAnimationFrame(tick);
    const elapsed = (now - t0) / 1000;
    const breath = Math.sin(elapsed * 1.65) * 0.014;
    const typeNudge = Math.sin(elapsed * 7.2) * 0.008;
    const lean = Math.sin(elapsed * 7.2 + 0.4) * 0.018;
    if (secretarySprite) {
      secretarySprite.position.y = spriteBaseY + breath + typeNudge;
      secretarySprite.rotation.z = lean;
      secretarySprite.lookAt(camera.position);
    }

    if (reducedMotion) {
      bubble.position.y = 2.02;
      bubbleMat.opacity = 0.92;
    } else {
      const bubblePhase = (Math.sin(elapsed * 2.2) + 1) / 2;
      bubble.position.y = 1.98 + bubblePhase * 0.07;
      bubbleMat.opacity = 0.82 + bubblePhase * 0.18;
    }

    const pulse = (Math.sin(elapsed * 3) + 1) / 2;
    monitorL.draw(dayMode, pulse);
    monitorR.draw(dayMode, pulse * 0.75 + 0.1);

    root.rotation.y = Math.sin(elapsed * 0.12) * 0.04;
    bubble.lookAt(camera.position);
    namePlate.lookAt(camera.position);

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
    setBubbleText(text) {
      bubbleDrawer.draw(text);
    },
    setReducedMotion(next) {
      reducedMotion = !!next;
    },
    destroy() {
      running = false;
      win.cancelAnimationFrame(raf);
      win.removeEventListener('resize', resize);
      canvas.removeEventListener('pointerdown', handlePointerDown);
      renderer.dispose();
    }
  };
}