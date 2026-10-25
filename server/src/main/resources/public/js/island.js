import * as THREE from 'three';

const ZONES = ['devices', 'chat', 'files', 'system', 'photos'];
const POSES = {
  home: { look: [0, 0.55, 0], radius: 17.5, theta: 0.42, phi: 0.98 },
  devices: { look: [0, 0.7, 2.15], radius: 9.2, theta: 0.12, phi: 1.05 },
  chat: { look: [3.05, 0.85, 1.15], radius: 8.6, theta: 0.72, phi: 1.04 },
  files: { look: [3.35, 0.75, -1.7], radius: 8.8, theta: 0.95, phi: 1.02 },
  system: { look: [-3.35, 0.95, -0.85], radius: 8.8, theta: -0.55, phi: 1.02 },
  photos: { look: [-0.15, 1.05, -3.45], radius: 9.2, theta: 0.18, phi: 1.0 }
};

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
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0xcfe7ee);
  scene.fog = new THREE.Fog(0xcfe7ee, 24, 52);

  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 80);
  const clock = new THREE.Clock();
  const materials = new Map();
  const anchors = {};
  const rings = {};
  const pickables = [];
  const bobs = [];
  let shaker = null;

  function mat(color) {
    let material = materials.get(color);
    if (!material) {
      material = new THREE.MeshLambertMaterial({ color, flatShading: true });
      materials.set(color, material);
    }
    return material;
  }

  function addBox(parent, w, h, d, color, x, y, z) {
    const mesh = new THREE.Mesh(new THREE.BoxGeometry(w, h, d), mat(color));
    mesh.position.set(x, y, z);
    parent.add(mesh);
    return mesh;
  }

  const cupBody = new THREE.CylinderGeometry(0.15, 0.12, 0.4, 6);
  const cupLid = new THREE.CylinderGeometry(0.16, 0.16, 0.05, 6);
  const cupStraw = new THREE.CylinderGeometry(0.018, 0.018, 0.26, 4);

  function addCup(parent, color, x, y, z, phase) {
    const group = new THREE.Group();
    const body = new THREE.Mesh(cupBody, mat(color));
    body.position.y = 0.2;
    const lid = new THREE.Mesh(cupLid, mat(0xfffaf6));
    lid.position.y = 0.42;
    const straw = new THREE.Mesh(cupStraw, mat(0x3f3434));
    straw.position.set(0.03, 0.58, 0);
    straw.rotation.z = 0.18;
    group.add(body, lid, straw);
    group.position.set(x, y, z);
    parent.add(group);
    if (phase != null) bobs.push({ obj: group, base: y, phase });
    return group;
  }

  scene.add(new THREE.HemisphereLight(0xfff6e4, 0x7d9a72, 1.05));
  const sun = new THREE.DirectionalLight(0xfff1d0, 1.2);
  sun.position.set(7, 14, 6);
  scene.add(sun);

  const water = new THREE.Mesh(
    new THREE.CircleGeometry(48, 28),
    mat(0x7eb8c9)
  );
  water.rotation.x = -Math.PI / 2;
  water.position.y = -1.42;
  scene.add(water);

  const sand = new THREE.Mesh(new THREE.CylinderGeometry(8.5, 9.15, 1.45, 8), mat(0xc6a36a));
  sand.position.y = -0.72;
  scene.add(sand);
  const grass = new THREE.Mesh(new THREE.CylinderGeometry(8.05, 8.05, 0.26, 8), mat(0x8eae6a));
  grass.position.y = -0.04;
  scene.add(grass);

  function islet(x, z, radius) {
    const group = new THREE.Group();
    const base = new THREE.Mesh(new THREE.CylinderGeometry(radius, radius + 0.35, 0.7, 6), mat(0xc6a36a));
    base.position.y = -1.2;
    const top = new THREE.Mesh(new THREE.CylinderGeometry(radius * 0.82, radius * 0.82, 0.18, 6), mat(0x8eae6a));
    top.position.y = -0.8;
    group.add(base, top);
    group.position.set(x, 0, z);
    scene.add(group);
    return group;
  }

  function bush(parent, x, y, z, scale, color) {
    const mesh = new THREE.Mesh(new THREE.SphereGeometry(0.45, 6, 5), mat(color));
    mesh.position.set(x, y, z);
    mesh.scale.set(scale, scale * 0.85, scale);
    parent.add(mesh);
  }

  function umbrella(parent, x, y, z, color) {
    const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.045, 0.05, 1.35, 5), mat(0xf7f1ea));
    pole.position.set(x, y + 0.67, z);
    const top = new THREE.Mesh(new THREE.ConeGeometry(0.85, 0.36, 6), mat(color));
    top.position.set(x, y + 1.42, z);
    parent.add(pole, top);
  }

  bush(scene, -5.6, 0.28, 3.4, 1.15, 0x6f9658);
  bush(scene, 5.8, 0.24, 3.1, 0.9, 0x7ea362);
  bush(scene, -6.1, 0.22, -3.6, 1.05, 0x648a52);
  bush(scene, 6.2, 0.2, -3.2, 0.8, 0x87a864);
  umbrella(scene, -2.4, 0, 4.6, 0xf6d56b);

  const sideIsle = islet(12.2, 5.4, 1.7);
  umbrella(sideIsle, 0, -0.7, 0, 0xea5420);
  const farIsle = islet(-12.4, -6.2, 1.35);
  bush(farIsle, 0, -0.45, 0, 0.7, 0x6f9658);

  const rockGeo = new THREE.SphereGeometry(0.55, 5, 4);
  [[9.2, 2.2, 0.7], [-8.6, 4.4, 0.5], [4.8, -8.2, 0.85]].forEach(([x, z, s]) => {
    const rock = new THREE.Mesh(rockGeo, mat(0xcbb89a));
    rock.position.set(x, -1.2, z);
    rock.scale.set(s, s * 0.55, s);
    scene.add(rock);
  });

  const shop = new THREE.Group();
  addBox(shop, 3.5, 1.9, 2.55, 0xfff8f1, 0, 0.95, -0.35);
  addBox(shop, 3.85, 0.22, 2.95, 0x096597, 0, 1.98, -0.35);
  addBox(shop, 2.35, 0.1, 1.15, 0xea5420, 0, 1.42, 1.15);
  addBox(shop, 2.35, 0.1, 1.15, 0xfff8f1, 0, 1.28, 1.15);
  addBox(shop, 0.85, 1.15, 0.08, 0xf0d2b4, 0, 0.58, 0.94);
  addBox(shop, 0.7, 0.55, 0.06, 0xcfe7ee, -1.05, 1.15, 0.94);
  addBox(shop, 0.7, 0.55, 0.06, 0xcfe7ee, 1.05, 1.15, 0.94);
  scene.add(shop);
  const shopAnchor = new THREE.Object3D();
  shopAnchor.position.set(0, 2.45, -0.35);
  scene.add(shopAnchor);
  anchors.shop = shopAnchor;

  function makeZone(id, x, z) {
    const group = new THREE.Group();
    group.position.set(x, 0, z);
    group.userData.zone = id;
    const anchor = new THREE.Object3D();
    anchor.position.set(0, 1.85, 0);
    group.add(anchor);
    anchors[id] = anchor;
    const ring = new THREE.Mesh(
      new THREE.RingGeometry(1.05, 1.24, 6),
      new THREE.MeshBasicMaterial({ color: 0xea5420, side: THREE.DoubleSide })
    );
    ring.rotation.x = -Math.PI / 2;
    ring.position.y = 0.045;
    ring.visible = false;
    ring.userData.marker = true;
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

  const counter = makeZone('devices', 0, 2.55);
  addBox(counter, 2.5, 0.82, 0.72, 0xf6e2c4, 0, 0.41, 0);
  addBox(counter, 2.5, 0.06, 0.78, 0xfffaf6, 0, 0.85, 0);
  [[-0.85, 0xf48fb1], [-0.28, 0xf6d56b], [0.28, 0x8ecae6], [0.85, 0xb7d98a]].forEach(([x, color], i) => {
    addCup(counter, color, x, 0.88, 0.02, i * 0.8);
  });
  tag(counter);

  const speaker = makeZone('chat', 3.15, 1.25);
  const pole = new THREE.Mesh(new THREE.CylinderGeometry(0.06, 0.07, 1.15, 6), mat(0xf7f1ea));
  pole.position.y = 0.58;
  speaker.add(pole);
  addBox(speaker, 0.62, 0.42, 0.28, 0xea5420, 0, 1.22, 0);
  addBox(speaker, 0.22, 0.22, 0.06, 0xfff8f1, 0, 1.22, 0.16);
  tag(speaker);

  const rack = makeZone('files', 3.45, -1.85);
  addBox(rack, 1.35, 0.08, 0.55, 0xf6e2c4, 0, 0.35, 0);
  addBox(rack, 1.35, 0.08, 0.55, 0xf6e2c4, 0, 0.95, 0);
  addBox(rack, 0.08, 1.15, 0.08, 0xc6a36a, -0.6, 0.58, -0.2);
  addBox(rack, 0.08, 1.15, 0.08, 0xc6a36a, 0.6, 0.58, -0.2);
  addBox(rack, 0.08, 1.15, 0.08, 0xc6a36a, -0.6, 0.58, 0.2);
  addBox(rack, 0.08, 1.15, 0.08, 0xc6a36a, 0.6, 0.58, 0.2);
  addCup(rack, 0xf48fb1, -0.38, 0.39, 0, 0.4);
  addCup(rack, 0x8ecae6, 0.28, 0.39, 0, 1.2);
  addCup(rack, 0xf6d56b, -0.05, 0.99, 0, 2.1);
  tag(rack);

  const machine = makeZone('system', -3.45, -1.05);
  addBox(machine, 1.05, 1.45, 0.85, 0xfff8f1, 0, 0.72, 0);
  addBox(machine, 1.15, 0.12, 0.95, 0x096597, 0, 1.48, 0);
  addBox(machine, 0.28, 0.18, 0.16, 0xea5420, 0.22, 0.85, 0.44);
  shaker = new THREE.Mesh(new THREE.CylinderGeometry(0.22, 0.22, 0.55, 6), mat(0xf6d56b));
  shaker.position.set(-0.18, 1.85, 0);
  machine.add(shaker);
  addCup(machine, 0xb7d98a, 0.42, 0, 0.7, null);
  tag(machine);

  const board = makeZone('photos', -0.2, -3.7);
  addBox(board, 0.08, 1.35, 0.08, 0xc6a36a, -0.85, 0.68, 0);
  addBox(board, 0.08, 1.35, 0.08, 0xc6a36a, 0.85, 0.68, 0);
  addBox(board, 2.15, 1.45, 0.1, 0xfff8f1, 0, 1.15, 0);
  addBox(board, 0.42, 0.32, 0.04, 0xf48fb1, -0.55, 1.4, 0.08);
  addBox(board, 0.42, 0.32, 0.04, 0xf6d56b, 0, 1.4, 0.08);
  addBox(board, 0.42, 0.32, 0.04, 0x8ecae6, 0.55, 1.4, 0.08);
  addBox(board, 0.85, 0.28, 0.04, 0xb7d98a, -0.3, 0.9, 0.08);
  addBox(board, 0.7, 0.28, 0.04, 0xea5420, 0.45, 0.9, 0.08);
  tag(board);

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
    const span = Math.sin(phi);
    camera.position.set(
      lookAt.x + radius * span * Math.sin(theta),
      lookAt.y + radius * Math.cos(phi),
      lookAt.z + radius * span * Math.cos(theta)
    );
    camera.lookAt(lookAt);
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

  function resize() {
    const width = stage.clientWidth || window.innerWidth;
    const height = stage.clientHeight || window.innerHeight;
    camera.aspect = width / Math.max(height, 1);
    camera.updateProjectionMatrix();
    renderer.setSize(width, height, false);
  }

  function placeLabels() {
    const width = stage.clientWidth || 1;
    const height = stage.clientHeight || 1;
    document.querySelectorAll('[data-anchor]').forEach((el) => {
      const anchor = anchors[el.getAttribute('data-anchor')];
      if (!anchor) return;
      anchor.getWorldPosition(projected);
      projected.project(camera);
      if (projected.z > 1) {
        el.style.visibility = 'hidden';
        return;
      }
      const x = (projected.x * 0.5 + 0.5) * width;
      const y = (-projected.y * 0.5 + 0.5) * height;
      el.style.visibility = 'visible';
      el.style.transform = 'translate(' + x + 'px, ' + y + 'px) translate(-50%, -130%)';
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

  function hover(event) {
    const zone = hitZone(event);
    canvas.style.cursor = zone ? 'pointer' : 'grab';
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
      hover(event);
      return;
    }
    const dx = event.clientX - lastX;
    const dy = event.clientY - lastY;
    if (dx * dx + dy * dy > 9) moved = true;
    lastX = event.clientX;
    lastY = event.clientY;
    targetTheta -= dx * 0.005;
    targetPhi = Math.min(1.28, Math.max(0.42, targetPhi + dy * 0.004));
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
    targetRadius = Math.min(30, Math.max(7, targetRadius + event.deltaY * 0.012));
  }, { passive: false });

  function frame() {
    const k = reduced ? 1 : 0.08;
    lookAt.lerp(targetLook, k);
    radius += (targetRadius - radius) * k;
    theta += (targetTheta - theta) * k;
    phi += (targetPhi - phi) * k;
    placeCamera();
    if (!reduced) {
      const t = clock.getElapsedTime();
      bobs.forEach((bob) => {
        bob.obj.position.y = bob.base + Math.sin(t * 1.7 + bob.phase) * 0.035;
      });
      if (shaker) shaker.rotation.y = t * 1.5;
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
  window.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    const tagName = event.target && event.target.tagName;
    if (tagName === 'INPUT' || tagName === 'TEXTAREA') return;
    const sheet = document.getElementById('islandSheet');
    if (sheet && !sheet.hidden && typeof window.switchDashTab === 'function') {
      window.switchDashTab('');
    }
  });

  window.PunchClockIsland = { focus, reset };

  resize();
  const open = document.querySelector('.dash-panel.is-active');
  if (open && !open.hasAttribute('hidden')) {
    const id = open.id.replace('panel-', '');
    applyPose(POSES[id] || POSES.home, true);
    setActive(POSES[id] ? id : '');
  } else {
    applyPose(POSES.home, true);
  }
  placeLabels();
  renderer.render(scene, camera);
  if (!document.hidden) start();
}
