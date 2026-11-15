// 日月星辰：太陽、月亮、星空、流星與月光倒影。
// 所有天空物件 fog: false、depthWrite: false，放在霧之外、ISLAND_CAMERA_FAR 之內。

import { ISLAND_CAMERA_FAR } from './island-interior.js';

export const STAR_COUNT = 600;
export const METEOR_INTERVAL_MIN = 8;
export const METEOR_INTERVAL_MAX = 20;
export const METEOR_DURATION_MIN = 0.6;
export const METEOR_DURATION_MAX = 1.0;
export const MOON_REFLECTION_Y = 0.15;

export function smoothstep(edge0, edge1, x) {
  const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
}

export function starOpacity(t) {
  return smoothstep(0.5, 1, t);
}

export function sunOpacity(t) {
  return 1 - smoothstep(0, 0.5, t);
}

export function moonOpacity(t) {
  return smoothstep(0.3, 0.8, t);
}

export function sunY(t, baseY) {
  return baseY - t * 15;
}

export function moonY(t, baseY, horizon) {
  return horizon + (baseY - horizon) * smoothstep(0.2, 1, t);
}

export function nextMeteorDelay(rng) {
  const fn = typeof rng === 'function' ? rng : Math.random;
  return METEOR_INTERVAL_MIN + fn() * (METEOR_INTERVAL_MAX - METEOR_INTERVAL_MIN);
}

export function meteorDuration(rng) {
  const fn = typeof rng === 'function' ? rng : Math.random;
  return METEOR_DURATION_MIN + fn() * (METEOR_DURATION_MAX - METEOR_DURATION_MIN);
}

export function createMeteorScheduler(opts) {
  const reduced = !!(opts && opts.reduced);
  const rng = opts && typeof opts.rng === 'function' ? opts.rng : Math.random;
  let countdown = nextMeteorDelay(rng);
  let active = null;

  function step(dt, t) {
    if (reduced || t < 0.7) {
      active = null;
      return null;
    }
    if (active) {
      active.elapsed += dt;
      if (active.elapsed >= active.duration) {
        active = null;
        countdown = nextMeteorDelay(rng);
      }
      return active;
    }
    countdown -= dt;
    if (countdown <= 0) {
      active = {
        elapsed: 0,
        duration: meteorDuration(rng),
        angle: rng() * Math.PI * 2,
        elevation: 0.4 + rng() * 0.35
      };
    }
    return active;
  }

  function current() { return active; }

  return { step: step, current: current };
}

export function starTwinkle(phase, elapsed) {
  return 0.6 + 0.4 * Math.sin(elapsed * 1.2 + phase);
}

function createStars(THREE, count) {
  const positions = new Float32Array(count * 3);
  const phases = new Float32Array(count);
  const sizes = new Float32Array(count);
  const radius = ISLAND_CAMERA_FAR * 0.7;

  for (let i = 0; i < count; i++) {
    const theta = Math.random() * Math.PI * 2;
    const phi = Math.acos(1 - Math.random() * 0.95);
    const r = radius;
    positions[i * 3] = r * Math.sin(phi) * Math.cos(theta);
    positions[i * 3 + 1] = r * Math.cos(phi);
    positions[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta);
    phases[i] = Math.random() * Math.PI * 2;
    sizes[i] = 1.2 + Math.random() * 1.8;
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));

  const material = new THREE.PointsMaterial({
    color: 0xffffff,
    size: 2,
    sizeAttenuation: false,
    transparent: true,
    opacity: 0,
    fog: false,
    depthWrite: false
  });

  const points = new THREE.Points(geometry, material);
  points.renderOrder = -1000;

  return { points: points, geometry: geometry, material: material, phases: phases, sizes: sizes };
}

function createCelestialBody(THREE, color, size, glowColor) {
  const group = new THREE.Group();

  const bodyMat = new THREE.SpriteMaterial({
    color: color,
    transparent: true,
    opacity: 1,
    fog: false,
    depthWrite: false
  });
  const body = new THREE.Sprite(bodyMat);
  body.scale.set(size, size, 1);
  group.add(body);

  const glowMat = new THREE.SpriteMaterial({
    color: glowColor,
    transparent: true,
    opacity: 0.4,
    fog: false,
    depthWrite: false
  });
  const glow = new THREE.Sprite(glowMat);
  glow.scale.set(size * 2.5, size * 2.5, 1);
  group.add(glow);

  group.renderOrder = -1001;

  return {
    group: group,
    bodyMat: bodyMat,
    glowMat: glowMat,
    setOpacity(o) {
      bodyMat.opacity = o;
      glowMat.opacity = o * 0.4;
    },
    dispose() {
      bodyMat.dispose();
      glowMat.dispose();
    }
  };
}

function createMeteor(THREE) {
  const geometry = new THREE.BufferGeometry();
  const positions = new Float32Array([0, 0, 0, 0, 0, 0]);
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));

  const material = new THREE.LineBasicMaterial({
    color: 0xffffff,
    transparent: true,
    opacity: 0,
    fog: false,
    depthWrite: false
  });

  const line = new THREE.Line(geometry, material);
  line.renderOrder = -999;
  line.visible = false;

  return { line: line, geometry: geometry, material: material };
}

function createMoonReflection(THREE) {
  const geometry = new THREE.PlaneGeometry(3, 60);
  const material = new THREE.MeshBasicMaterial({
    color: 0xd4e8ff,
    transparent: true,
    opacity: 0,
    side: THREE.DoubleSide,
    fog: false,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const mesh = new THREE.Mesh(geometry, material);
  mesh.rotation.x = -Math.PI / 2;
  mesh.position.y = MOON_REFLECTION_Y;
  mesh.renderOrder = -998;
  mesh.visible = false;
  return { mesh: mesh, geometry: geometry, material: material };
}

export function createIslandSky(THREE, opts) {
  const reduced = !!(opts && opts.reduced);
  const sunPos = opts && opts.sunPos ? opts.sunPos : { x: -9.5, y: 20.5, z: 11 };

  const group = new THREE.Group();
  const disposables = [];

  const dist = ISLAND_CAMERA_FAR * 0.65;
  const sunDir = new THREE.Vector3(sunPos.x, sunPos.y, sunPos.z).normalize();
  const sunBaseY = sunDir.y * dist;

  const sun = createCelestialBody(THREE, 0xfffde0, 18, 0xfff5a0);
  sun.group.position.set(sunDir.x * dist, sunBaseY, sunDir.z * dist);
  group.add(sun.group);
  disposables.push(sun);

  const moonDir = new THREE.Vector3(0.4, 0.25, -0.88).normalize();
  const moonBaseY = moonDir.y * dist;
  const moonHorizon = -5;
  const moon = createCelestialBody(THREE, 0xe8eef8, 12, 0xc8d4f0);
  moon.group.position.set(moonDir.x * dist, moonHorizon, moonDir.z * dist);
  group.add(moon.group);
  disposables.push(moon);

  const stars = createStars(THREE, STAR_COUNT);
  group.add(stars.points);
  disposables.push({
    dispose() {
      stars.geometry.dispose();
      stars.material.dispose();
    }
  });

  const meteor = createMeteor(THREE);
  group.add(meteor.line);
  disposables.push({
    dispose() {
      meteor.geometry.dispose();
      meteor.material.dispose();
    }
  });

  const reflection = createMoonReflection(THREE);
  group.add(reflection.mesh);
  disposables.push({
    dispose() {
      reflection.geometry.dispose();
      reflection.material.dispose();
    }
  });

  const meteorScheduler = createMeteorScheduler({ reduced: reduced });

  let currentT = 0;
  let waveOffset = 0;

  function updateSun(t) {
    const o = sunOpacity(t);
    sun.setOpacity(o);
    sun.group.position.y = sunY(t, sunBaseY);
    sun.group.visible = o > 0.01;
  }

  function updateMoon(t) {
    const o = moonOpacity(t);
    moon.setOpacity(o);
    moon.group.position.y = moonY(t, moonBaseY, moonHorizon);
    moon.group.visible = o > 0.01;
  }

  function updateStars(t, elapsed) {
    const baseOpacity = starOpacity(t);
    stars.material.opacity = baseOpacity;
    stars.points.visible = baseOpacity > 0.01;

    if (!reduced && baseOpacity > 0) {
      const sizes = stars.sizes;
      const phases = stars.phases;
      const geom = stars.geometry;
      const posAttr = geom.getAttribute('position');
      const positions = posAttr.array;
      const count = phases.length;

      for (let i = 0; i < count; i++) {
        const twinkle = starTwinkle(phases[i], elapsed);
        const scale = sizes[i] * twinkle;
        const idx = i * 3;
        const len = Math.sqrt(
          positions[idx] * positions[idx] +
          positions[idx + 1] * positions[idx + 1] +
          positions[idx + 2] * positions[idx + 2]
        );
        if (len > 0) {
          const targetLen = ISLAND_CAMERA_FAR * 0.7 * (0.95 + 0.05 * scale / 3);
          const factor = targetLen / len;
          positions[idx] *= factor;
          positions[idx + 1] *= factor;
          positions[idx + 2] *= factor;
        }
      }
      posAttr.needsUpdate = true;
    }
  }

  function updateMeteor(t, dt, elapsed) {
    const state = meteorScheduler.step(dt, t);
    if (!state) {
      meteor.line.visible = false;
      meteor.material.opacity = 0;
      return;
    }

    const progress = state.elapsed / state.duration;
    const fadeIn = smoothstep(0, 0.2, progress);
    const fadeOut = 1 - smoothstep(0.7, 1, progress);
    meteor.material.opacity = fadeIn * fadeOut * 0.9;

    const r = ISLAND_CAMERA_FAR * 0.5;
    const angle = state.angle;
    const elev = state.elevation;
    const startX = r * Math.cos(angle) * Math.cos(elev * Math.PI / 2);
    const startY = r * Math.sin(elev * Math.PI / 2) * 0.7 + 50;
    const startZ = r * Math.sin(angle) * Math.cos(elev * Math.PI / 2);

    const speed = 80;
    const traveled = state.elapsed * speed;
    const dir = new THREE.Vector3(-Math.cos(angle) * 0.5, -0.7, -Math.sin(angle) * 0.5).normalize();

    const headX = startX + dir.x * traveled;
    const headY = startY + dir.y * traveled;
    const headZ = startZ + dir.z * traveled;

    const tailLen = Math.min(traveled, 25);
    const tailX = headX - dir.x * tailLen;
    const tailY = headY - dir.y * tailLen;
    const tailZ = headZ - dir.z * tailLen;

    const posAttr = meteor.geometry.getAttribute('position');
    posAttr.array[0] = tailX;
    posAttr.array[1] = tailY;
    posAttr.array[2] = tailZ;
    posAttr.array[3] = headX;
    posAttr.array[4] = headY;
    posAttr.array[5] = headZ;
    posAttr.needsUpdate = true;

    meteor.line.visible = meteor.material.opacity > 0.01;
  }

  function updateReflection(t, elapsed) {
    const o = moonOpacity(t) * 0.35;
    reflection.material.opacity = o;
    reflection.mesh.visible = o > 0.01;

    if (!reduced && o > 0.01) {
      waveOffset = Math.sin(elapsed * 0.4) * 0.4 + Math.sin(elapsed * 0.7) * 0.2;
      reflection.mesh.position.x = moonDir.x * 30 + waveOffset;
      reflection.mesh.position.z = moonDir.z * 30;
    }
  }

  function update(t, elapsed, camera) {
    currentT = t;
    updateSun(t);
    updateMoon(t);
    updateStars(t, elapsed);
    updateReflection(t, elapsed);
  }

  function tick(dt, elapsed) {
    updateMeteor(currentT, dt, elapsed);
    if (!reduced) {
      updateStars(currentT, elapsed);
      updateReflection(currentT, elapsed);
    }
  }

  function setVisible(visible) {
    group.visible = !!visible;
  }

  function dispose() {
    for (let i = 0; i < disposables.length; i++) {
      disposables[i].dispose();
    }
  }

  update(0, 0, null);

  return {
    group: group,
    update: update,
    tick: tick,
    setVisible: setVisible,
    dispose: dispose
  };
}
