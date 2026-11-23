import * as T from 'three';

export type StudyDecor = {
  group: T.Group;
  fog: T.Fog;
  update: (delta: number, reducedMotion: boolean) => void;
  dispose: () => void;
};

const tagChrome = (obj: T.Object3D) => {
  obj.userData.atlasSceneChrome = true;
  obj.raycast = () => {};
  return obj;
};

const chromeMesh = (
  geo: T.BufferGeometry,
  mat: T.Material,
  renderOrder = -4,
) => {
  const mesh = new T.Mesh(geo, mat);
  mesh.renderOrder = renderOrder;
  return tagChrome(mesh);
};

const desatBasic = (hex: string, opts?: {opacity?: number; map?: T.Texture}) =>
  new T.MeshBasicMaterial({
    color: new T.Color(hex),
    transparent: (opts?.opacity ?? 1) < 1,
    opacity: opts?.opacity ?? 1,
    depthWrite: (opts?.opacity ?? 1) >= 0.92,
    map: opts?.map,
  });

const canvasTexture = (w: number, h: number, draw: (ctx: CanvasRenderingContext2D) => void) => {
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d')!;
  draw(ctx);
  const tex = new T.CanvasTexture(canvas);
  tex.colorSpace = T.SRGBColorSpace;
  tex.needsUpdate = true;
  return tex;
};

const baguaMatTexture = () =>
  canvasTexture(512, 512, ctx => {
    const cx = 256;
    const cy = 256;
    ctx.fillStyle = '#e8dcc8';
    ctx.fillRect(0, 0, 512, 512);
    for (let i = 0; i < 8; i++) {
      const a0 = (i / 8) * Math.PI * 2;
      const a1 = ((i + 1) / 8) * Math.PI * 2;
      ctx.beginPath();
      ctx.moveTo(cx, cy);
      ctx.arc(cx, cy, 250, a0, a1);
      ctx.closePath();
      ctx.fillStyle = i % 2 ? '#e4d9c6' : '#ebe1cf';
      ctx.fill();
    }
  ctx.strokeStyle = 'rgba(168, 148, 108, 0.35)';
  ctx.lineWidth = 2;
  for (let r of [200, 168, 136]) {
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, Math.PI * 2);
    ctx.stroke();
  }
  const meander = (radius: number) => {
    ctx.save();
    ctx.strokeStyle = 'rgba(148, 128, 92, 0.28)';
    ctx.lineWidth = 3;
    ctx.beginPath();
    for (let a = 0; a <= Math.PI * 2 + 0.01; a += 0.08) {
      const wobble = Math.sin(a * 12) * 6;
      const x = cx + Math.cos(a) * (radius + wobble);
      const y = cy + Math.sin(a) * (radius + wobble);
      if (a === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.closePath();
    ctx.stroke();
    ctx.restore();
  };
  meander(212);
  const trigram = (angle: number, lines: number[]) => {
    const rad = 228;
    const ox = cx + Math.cos(angle) * rad;
    const oy = cy + Math.sin(angle) * rad;
    const along = Math.cos(angle + Math.PI / 2);
    const across = Math.sin(angle + Math.PI / 2);
    ctx.strokeStyle = 'rgba(120, 100, 72, 0.32)';
    ctx.lineWidth = 4;
    lines.forEach((kind, row) => {
      const t = row - 1;
      const px = ox + along * t * 14;
      const py = oy + across * t * 14;
      const len = 22;
      if (kind === 1) {
        ctx.beginPath();
        ctx.moveTo(px - along * len, py - across * len);
        ctx.lineTo(px + along * len, py + across * len);
        ctx.stroke();
      } else {
        for (const side of [-1, 1]) {
          ctx.beginPath();
          ctx.moveTo(px + along * side * 6 - across * len, py + across * side * 6 + along * len);
          ctx.lineTo(px + along * side * 6 + across * len, py + across * side * 6 - along * len);
          ctx.stroke();
        }
      }
    });
  };
  const patterns: number[][] = [
    [1, 1, 1],
    [0, 0, 0],
    [1, 0, 1],
    [0, 1, 0],
    [1, 1, 0],
    [0, 1, 1],
    [1, 0, 0],
    [0, 0, 1],
  ];
  patterns.forEach((p, i) => trigram(-Math.PI / 2 + (i / 8) * Math.PI * 2, p));
  ctx.strokeStyle = 'rgba(110, 140, 128, 0.12)';
  ctx.lineWidth = 1.2;
  const meridian = [
    [0, -0.35, 0, 0.32],
    [-0.28, 0.05, 0.3, 0.18],
    [0.26, -0.08, -0.32, 0.22],
  ];
  for (const [x0, y0, x1, y1] of meridian) {
    ctx.beginPath();
    ctx.moveTo(cx + x0 * 120, cy + y0 * 120);
    ctx.quadraticCurveTo(cx, cy, cx + x1 * 120, cy + y1 * 120);
    ctx.stroke();
  }
  for (let i = 0; i < 14; i++) {
    const t = i / 13;
    const x = cx + Math.sin(t * Math.PI * 2) * 48 * (1 - t * 0.2);
    const y = cy + Math.cos(t * Math.PI) * 38;
    ctx.fillStyle = 'rgba(118, 138, 122, 0.16)';
    ctx.beginPath();
    ctx.arc(x, y, 2.2, 0, Math.PI * 2);
    ctx.fill();
  }
  const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, 256);
  g.addColorStop(0, 'rgba(0,0,0,0.03)');
  g.addColorStop(0.65, 'rgba(0,0,0,0)');
  g.addColorStop(1, 'rgba(0,0,0,0.06)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 512, 512);
  });

const feetShadowTexture = () =>
  canvasTexture(256, 256, ctx => {
    const g = ctx.createRadialGradient(128, 128, 0, 128, 128, 128);
    g.addColorStop(0, 'rgba(0,0,0,0.07)');
    g.addColorStop(0.38, 'rgba(0,0,0,0.022)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(0, 0, 256, 256);
  });

const scrollTexture = (title: string) =>
  canvasTexture(128, 512, ctx => {
    ctx.fillStyle = '#f3ead8';
    ctx.fillRect(0, 0, 128, 512);
    ctx.strokeStyle = 'rgba(138, 118, 88, 0.45)';
    ctx.lineWidth = 4;
    ctx.strokeRect(10, 36, 108, 440);
    const barGrad = ctx.createLinearGradient(0, 0, 0, 512);
    barGrad.addColorStop(0, '#c9b896');
    barGrad.addColorStop(1, '#b9a888');
    ctx.fillStyle = barGrad;
    ctx.fillRect(4, 20, 120, 18);
    ctx.fillRect(4, 474, 120, 18);
    ctx.fillStyle = 'rgba(88, 72, 52, 0.78)';
    ctx.font = 'bold 56px "Songti SC", "STSong", "Noto Serif TC", serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    const chars = [...title];
    const startY = 180;
    const gap = 78;
    chars.forEach((ch, i) => {
      ctx.fillText(ch, 64, startY + i * gap);
    });
  });

const screenPanelTexture = () =>
  canvasTexture(256, 384, ctx => {
    const grad = ctx.createLinearGradient(0, 0, 0, 384);
    grad.addColorStop(0, '#eae4d8');
    grad.addColorStop(1, '#e0d8cc');
    ctx.fillStyle = grad;
    ctx.fillRect(0, 0, 256, 384);
    ctx.strokeStyle = 'rgba(130, 110, 82, 0.35)';
    ctx.lineWidth = 2;
    ctx.strokeRect(6, 6, 244, 372);
    const layers = [
      {y: 0.68, h: 0.22, c: 'rgba(148, 162, 152, 0.22)'},
      {y: 0.52, h: 0.18, c: 'rgba(132, 148, 138, 0.2)'},
      {y: 0.36, h: 0.16, c: 'rgba(118, 132, 124, 0.18)'},
    ];
    layers.forEach(L => {
      ctx.fillStyle = L.c;
      ctx.beginPath();
      ctx.moveTo(0, 384 * L.y);
      ctx.lineTo(256, 384 * (L.y - 0.06));
      ctx.lineTo(256, 384 * (L.y + L.h));
      ctx.lineTo(0, 384 * (L.y + L.h + 0.04));
      ctx.closePath();
      ctx.fill();
    });
    ctx.strokeStyle = 'rgba(92, 112, 102, 0.16)';
    ctx.lineWidth = 1;
    for (let i = 0; i < 5; i++) {
      ctx.beginPath();
      ctx.moveTo(32 + i * 44, 320);
      ctx.quadraticCurveTo(96, 280 - i * 8, 210 - i * 12, 250 + i * 4);
      ctx.stroke();
    }
  });

const buildMedicineCabinet = () => {
  const g = new T.Group();
  const frame = chromeMesh(
    new T.BoxGeometry(1.35, 1.65, 0.38),
    desatBasic('#c4b49c'),
  );
  frame.position.set(-4.2, 0.92, -5.4);
  frame.rotation.y = 0.42;
  g.add(frame);
  const rows = 7;
  const cols = 5;
  for (let r = 0; r < rows; r++) {
    for (let c = 0; c < cols; c++) {
      const drawer = chromeMesh(
        new T.BoxGeometry(0.22, 0.17, 0.06),
        desatBasic('#ddd2c0', {opacity: 0.96}),
      );
      drawer.position.set(
        -4.2 - 0.52 + c * 0.24,
        0.38 + r * 0.21,
        -5.18,
      );
      drawer.rotation.y = 0.42;
      g.add(drawer);
    }
  }
  return g;
};

const buildScreen = () => {
  const g = new T.Group();
  const tex = screenPanelTexture();
  const panels = 4;
  const radius = 2.1;
  const center = new T.Vector3(4.5, 0.95, -5.1);
  for (let i = 0; i < panels; i++) {
    const angle = -0.38 + (i / (panels - 1)) * 0.76;
    const panel = chromeMesh(
      new T.BoxGeometry(0.08, 1.45, 0.72),
      desatBasic('#ffffff', {map: tex}),
    );
    const ox = Math.sin(angle) * radius;
    const oz = Math.cos(angle) * radius - radius;
    panel.position.set(center.x + ox, center.y, center.z + oz);
    panel.rotation.y = -angle + 0.15;
    g.add(panel);
  }
  return g;
};

const buildScrolls = () => {
  const g = new T.Group();
  const scrollA = chromeMesh(
    new T.PlaneGeometry(0.28, 1.15),
    desatBasic('#ffffff', {map: scrollTexture('經絡')}),
  );
  scrollA.position.set(-1.05, 1.55, -6.35);
  g.add(scrollA);
  const scrollB = chromeMesh(
    new T.PlaneGeometry(0.28, 1.15),
    desatBasic('#ffffff', {map: scrollTexture('本草')}),
  );
  scrollB.position.set(0.95, 1.58, -6.38);
  g.add(scrollB);
  return g;
};

const buildStandingBronze = () => {
  const g = new T.Group();
  const bronze = '#a89478';
  const body = chromeMesh(new T.CylinderGeometry(0.055, 0.07, 0.22, 10), desatBasic(bronze));
  body.position.y = 0.14;
  g.add(body);
  const head = chromeMesh(new T.SphereGeometry(0.048, 10, 10), desatBasic('#b39d82'));
  head.position.y = 0.28;
  g.add(head);
  for (const side of [-1, 1] as const) {
    const arm = chromeMesh(new T.BoxGeometry(0.1, 0.02, 0.02), desatBasic(bronze));
    arm.position.set(side * 0.075, 0.2, 0);
    g.add(arm);
    const leg = chromeMesh(new T.CylinderGeometry(0.022, 0.026, 0.12, 8), desatBasic(bronze));
    leg.position.set(side * 0.032, 0.05, 0);
    g.add(leg);
  }
  for (let i = 0; i < 6; i++) {
    const dot = chromeMesh(new T.SphereGeometry(0.008, 6, 6), desatBasic('#8a7a64', {opacity: 0.85}));
    dot.position.set((Math.random() - 0.5) * 0.06, 0.08 + i * 0.03, 0.052);
    g.add(dot);
  }
  return g;
};

const buildDeskSet = () => {
  const g = new T.Group();
  g.position.set(2.35, 0, -4.6);
  g.rotation.y = -0.35;
  const top = chromeMesh(new T.BoxGeometry(0.62, 0.06, 0.38), desatBasic('#c9b8a0'));
  top.position.y = 0.36;
  g.add(top);
  for (const [x, z] of [
    [-0.24, -0.12],
    [0.24, -0.12],
    [-0.24, 0.12],
    [0.24, 0.12],
  ] as [number, number][]) {
    const leg = chromeMesh(new T.BoxGeometry(0.05, 0.34, 0.05), desatBasic('#b8a690'));
    leg.position.set(x, 0.17, z);
    g.add(leg);
  }
  const figure = buildStandingBronze();
  figure.position.set(0, 0.39, 0.02);
  figure.scale.setScalar(1.15);
  g.add(figure);
  return g;
};

const buildFloorMat = () => {
  const g = new T.Group();
  const map = baguaMatTexture();
  const mat = chromeMesh(
    new T.CircleGeometry(1.15, 64),
    desatBasic('#ffffff', {map, opacity: 0.96}),
  );
  mat.rotation.x = -Math.PI / 2;
  mat.position.y = 0.002;
  g.add(mat);
  const ring = chromeMesh(
    new T.RingGeometry(0.66, 0.72, 48),
    desatBasic('#cbb894', {opacity: 0.32}),
  );
  ring.rotation.x = -Math.PI / 2;
  ring.position.y = 0.003;
  g.add(ring);
  const shadow = chromeMesh(
    new T.CircleGeometry(3.5, 64),
    new T.MeshBasicMaterial({
      map: feetShadowTexture(),
      transparent: true,
      depthWrite: false,
    }),
  );
  shadow.rotation.x = -Math.PI / 2;
  shadow.position.y = -0.01;
  g.add(shadow);
  return g;
};

type Atmosphere = {
  points: T.Points;
  velocities: Float32Array;
  reducedHidden: boolean;
};

const buildAtmosphere = (): Atmosphere => {
  const count = 16;
  const positions = new Float32Array(count * 3);
  const velocities = new Float32Array(count * 3);
  const colors = new Float32Array(count * 3);
  for (let i = 0; i < count; i++) {
    positions[i * 3] = (Math.random() - 0.5) * 5;
    positions[i * 3 + 1] = 0.4 + Math.random() * 2.2;
    positions[i * 3 + 2] = (Math.random() - 0.5) * 5 - 1.5;
    velocities[i * 3] = (Math.random() - 0.5) * 0.08;
    velocities[i * 3 + 1] = -0.05 - Math.random() * 0.08;
    velocities[i * 3 + 2] = (Math.random() - 0.5) * 0.06;
    const petal = i % 3 === 0;
    const col = petal
      ? new T.Color('#e8c4c8')
      : i % 3 === 1
        ? new T.Color('#d8c878')
        : new T.Color('#c8a878');
    colors[i * 3] = col.r;
    colors[i * 3 + 1] = col.g;
    colors[i * 3 + 2] = col.b;
  }
  const geo = new T.BufferGeometry();
  geo.setAttribute('position', new T.BufferAttribute(positions, 3));
  geo.setAttribute('color', new T.BufferAttribute(colors, 3));
  const mat = new T.PointsMaterial({
    size: 0.055,
    vertexColors: true,
    transparent: true,
    opacity: 0.42,
    depthWrite: false,
    sizeAttenuation: true,
  });
  const points = tagChrome(new T.Points(geo, mat)) as T.Points;
  points.renderOrder = -5;
  return {points, velocities, reducedHidden: false};
};

export const buildStudyDecor = (): StudyDecor => {
  const group = new T.Group();
  group.name = 'studyDecor';
  group.add(buildFloorMat());
  group.add(buildMedicineCabinet());
  group.add(buildScreen());
  group.add(buildScrolls());
  group.add(buildDeskSet());
  const atmosphere = buildAtmosphere();
  group.add(atmosphere.points);
  const fog = new T.Fog('#f6ead7', 5.5, 13.5);

  const update = (delta: number, reducedMotion: boolean) => {
    if (reducedMotion) {
      if (!atmosphere.reducedHidden) {
        atmosphere.points.visible = false;
        atmosphere.reducedHidden = true;
      }
      return;
    }
    if (atmosphere.reducedHidden) {
      atmosphere.points.visible = true;
      atmosphere.reducedHidden = false;
    }
    const pos = atmosphere.points.geometry.getAttribute('position') as T.BufferAttribute;
    for (let i = 0; i < pos.count; i++) {
      let x = pos.getX(i) + atmosphere.velocities[i * 3] * delta;
      let y = pos.getY(i) + atmosphere.velocities[i * 3 + 1] * delta;
      let z = pos.getZ(i) + atmosphere.velocities[i * 3 + 2] * delta;
      if (y < 0.25) {
        y = 2.1 + Math.random() * 0.6;
        x = (Math.random() - 0.5) * 5;
        z = (Math.random() - 0.5) * 5 - 1.5;
      }
      pos.setXYZ(i, x, y, z);
    }
    pos.needsUpdate = true;
  };

  const dispose = () => {
    const textures = new Set<T.Texture>();
    group.traverse(obj => {
      if (!(obj instanceof T.Mesh) && !(obj instanceof T.Points)) return;
      obj.geometry.dispose();
      const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
      for (const m of mats) {
        const map = (m as T.MeshBasicMaterial).map;
        if (map) textures.add(map);
        m.dispose();
      }
    });
    textures.forEach(tex => tex.dispose());
  };

  return {group, fog, update, dispose};
};