// 日月星辰：太陽、月亮、星空、流星與月光倒影。
export function smoothstep(edge0, edge1, x) {
  const t = Math.max(0, Math.min(1, (x - edge0) / (edge1 - edge0)));
  return t * t * (3 - 2 * t);
}

export function sunOpacity(t) {
  return 1 - smoothstep(0, 0.5, t);
}

export function moonOpacity(t) {
  return smoothstep(0.3, 0.7, t);
}

export function starsOpacity(t) {
  return smoothstep(0.5, 1, t);
}

export function nextMeteorDelay(rng) {
  return 8 + rng() * 12;
}

export function scheduleMeteor(state, elapsed, reduced, rng) {
  if (reduced) return Object.assign({}, state, { pending: false });
  if (state.active) return state;
  if (state.pending) {
    if (elapsed >= state.nextAt) {
      return Object.assign({}, state, {
        pending: false,
        active: true,
        startTime: elapsed,
        duration: 0.6 + rng() * 0.4
      });
    }
    return state;
  }
  return Object.assign({}, state, {
    pending: true,
    nextAt: elapsed + nextMeteorDelay(rng)
  });
}

export function stepMeteor(state, elapsed) {
  if (!state.active) return state;
  const progress = (elapsed - state.startTime) / state.duration;
  if (progress >= 1) {
    return Object.assign({}, state, { active: false, progress: 1 });
  }
  return Object.assign({}, state, { progress: progress });
}

export function createMeteorState() {
  return { pending: false, active: false, nextAt: 0, startTime: 0, duration: 0.8, progress: 0 };
}

const STAR_COUNT = 600;
const MOON_RADIUS = 380;
const MOON_THETA = Math.PI * 0.15;
const MOON_PHI = Math.PI * 0.38;
const SUN_RADIUS = 400;

export function moonPosition() {
  const y = Math.sin(MOON_PHI) * MOON_RADIUS;
  const r = Math.cos(MOON_PHI) * MOON_RADIUS;
  const x = Math.cos(MOON_THETA) * r;
  const z = Math.sin(MOON_THETA) * r;
  return { x: x, y: y, z: z };
}

export function sunDirection() {
  const dir = { x: -9.5, y: 20.5, z: 11 };
  const len = Math.hypot(dir.x, dir.y, dir.z);
  return { x: dir.x / len, y: dir.y / len, z: dir.z / len };
}

export function generateStars(count, rng) {
  const stars = [];
  for (let i = 0; i < count; i++) {
    const theta = rng() * Math.PI * 2;
    const phi = Math.acos(rng() * 0.85 + 0.15);
    const r = 450 + rng() * 50;
    const x = Math.sin(phi) * Math.cos(theta) * r;
    const y = Math.cos(phi) * r;
    const z = Math.sin(phi) * Math.sin(theta) * r;
    const size = 0.8 + rng() * 1.6;
    const phase = rng() * Math.PI * 2;
    const twinkleSpeed = 1.5 + rng() * 2;
    stars.push({ x, y, z, size, phase, twinkleSpeed });
  }
  return stars;
}

function seededRandom(seed) {
  let s = seed;
  return function () {
    s = (s * 1664525 + 1013904223) % 4294967296;
    return s / 4294967296;
  };
}

export function createIslandSky(THREE, opts) {
  opts = opts || {};
  const reduced = !!(opts.reduced);
  const scene = opts.scene;
  const rng = opts.rng || seededRandom(42);
  const group = new THREE.Group();
  group.renderOrder = -100;

  const sunDir = sunDirection();
  const sunPos = { x: sunDir.x * SUN_RADIUS, y: sunDir.y * SUN_RADIUS, z: sunDir.z * SUN_RADIUS };

  const sunMat = new THREE.SpriteMaterial({
    color: 0xfff8e0,
    transparent: true,
    opacity: 1,
    fog: false,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const sun = new THREE.Sprite(sunMat);
  sun.scale.set(80, 80, 1);
  sun.position.set(sunPos.x, sunPos.y, sunPos.z);
  group.add(sun);

  const sunGlowMat = new THREE.SpriteMaterial({
    color: 0xffeebb,
    transparent: true,
    opacity: 0.35,
    fog: false,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const sunGlow = new THREE.Sprite(sunGlowMat);
  sunGlow.scale.set(160, 160, 1);
  sunGlow.position.copy(sun.position);
  group.add(sunGlow);

  const moonPos = moonPosition();
  const moonMat = new THREE.SpriteMaterial({
    color: 0xf8f4e8,
    transparent: true,
    opacity: 0,
    fog: false,
    depthWrite: false
  });
  const moon = new THREE.Sprite(moonMat);
  moon.scale.set(48, 48, 1);
  moon.position.set(moonPos.x, moonPos.y, moonPos.z);
  group.add(moon);

  const moonGlowMat = new THREE.SpriteMaterial({
    color: 0xd8e8ff,
    transparent: true,
    opacity: 0,
    fog: false,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const moonGlow = new THREE.Sprite(moonGlowMat);
  moonGlow.scale.set(120, 120, 1);
  moonGlow.position.copy(moon.position);
  group.add(moonGlow);

  const starsData = generateStars(STAR_COUNT, rng);
  const starPositions = new Float32Array(STAR_COUNT * 3);
  const starSizes = new Float32Array(STAR_COUNT);
  const starPhases = new Float32Array(STAR_COUNT);
  const starSpeeds = new Float32Array(STAR_COUNT);
  for (let i = 0; i < STAR_COUNT; i++) {
    const s = starsData[i];
    starPositions[i * 3] = s.x;
    starPositions[i * 3 + 1] = s.y;
    starPositions[i * 3 + 2] = s.z;
    starSizes[i] = s.size;
    starPhases[i] = s.phase;
    starSpeeds[i] = s.twinkleSpeed;
  }
  const starGeo = new THREE.BufferGeometry();
  starGeo.setAttribute('position', new THREE.BufferAttribute(starPositions, 3));
  starGeo.setAttribute('size', new THREE.BufferAttribute(starSizes, 1));
  const starMat = new THREE.PointsMaterial({
    color: 0xffffff,
    size: 2.5,
    sizeAttenuation: true,
    transparent: true,
    opacity: 0,
    fog: false,
    depthWrite: false
  });
  const stars = new THREE.Points(starGeo, starMat);
  group.add(stars);

  const meteorGeo = new THREE.BufferGeometry();
  const meteorPos = new Float32Array([0, 0, 0, 0, 0, 0]);
  meteorGeo.setAttribute('position', new THREE.BufferAttribute(meteorPos, 3));
  const meteorMat = new THREE.LineBasicMaterial({
    color: 0xffffff,
    transparent: true,
    opacity: 0,
    fog: false,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const meteorLine = new THREE.Line(meteorGeo, meteorMat);
  meteorLine.visible = false;
  group.add(meteorLine);

  let meteorState = createMeteorState();
  let meteorStart = { x: 0, y: 0, z: 0 };
  let meteorEnd = { x: 0, y: 0, z: 0 };

  const WATER_Y = -0.74;
  const WAVE_PEAK = 0.6;
  const reflectionY = WATER_Y + WAVE_PEAK + 0.15;
  const reflectionGeo = new THREE.PlaneGeometry(6, 80);
  const reflectionMat = new THREE.MeshBasicMaterial({
    color: 0xd8e8ff,
    transparent: true,
    opacity: 0,
    fog: false,
    depthWrite: false,
    side: THREE.DoubleSide,
    blending: THREE.AdditiveBlending
  });
  const reflection = new THREE.Mesh(reflectionGeo, reflectionMat);
  const moonDir = { x: moonPos.x, y: 0, z: moonPos.z };
  const moonDirLen = Math.hypot(moonDir.x, moonDir.z) || 1;
  moonDir.x /= moonDirLen;
  moonDir.z /= moonDirLen;
  reflection.position.set(moonDir.x * 40, reflectionY, moonDir.z * 40);
  reflection.rotation.x = -Math.PI / 2;
  reflection.rotation.z = -Math.atan2(moonDir.x, moonDir.z);
  group.add(reflection);

  let currentT = 0;
  let reflectionPhase = 0;

  function update(t, elapsed, camera) {
    currentT = t;

    const sOp = sunOpacity(t);
    sunMat.opacity = sOp;
    sunGlowMat.opacity = sOp * 0.35;

    const sunDip = smoothstep(0.3, 0.7, t);
    sun.position.y = sunPos.y - sunDip * 60;
    sunGlow.position.y = sun.position.y;

    const mOp = moonOpacity(t);
    moonMat.opacity = mOp;
    moonGlowMat.opacity = mOp * 0.4;

    const moonRise = smoothstep(0.2, 0.6, t);
    moon.position.y = moonPos.y - 40 + moonRise * 40;
    moonGlow.position.y = moon.position.y;

    const stOp = starsOpacity(t);
    starMat.opacity = stOp * 0.85;

    if (!reduced && stOp > 0) {
      const sizes = starGeo.attributes.size;
      for (let i = 0; i < STAR_COUNT; i++) {
        const baseSize = starSizes[i];
        const twinkle = 0.7 + 0.3 * Math.sin(elapsed * starSpeeds[i] + starPhases[i]);
        sizes.array[i] = baseSize * twinkle;
      }
      sizes.needsUpdate = true;
    }

    if (!reduced && t > 0.6) {
      meteorState = scheduleMeteor(meteorState, elapsed, reduced, rng);
      if (meteorState.active && meteorState.progress === 0) {
        const theta = rng() * Math.PI * 2;
        const phi = Math.PI * 0.15 + rng() * Math.PI * 0.25;
        const r = 420;
        meteorStart = {
          x: Math.sin(phi) * Math.cos(theta) * r,
          y: Math.cos(phi) * r,
          z: Math.sin(phi) * Math.sin(theta) * r
        };
        const dx = -0.4 + rng() * 0.2;
        const dy = -0.8 - rng() * 0.2;
        const dz = -0.2 + rng() * 0.4;
        const len = Math.hypot(dx, dy, dz);
        meteorEnd = {
          x: meteorStart.x + (dx / len) * 120,
          y: meteorStart.y + (dy / len) * 120,
          z: meteorStart.z + (dz / len) * 120
        };
      }
      meteorState = stepMeteor(meteorState, elapsed);
      if (meteorState.active) {
        meteorLine.visible = true;
        const p = meteorState.progress;
        const tailLen = 0.15;
        const headT = Math.min(1, p * 1.2);
        const tailT = Math.max(0, headT - tailLen);
        const headX = meteorStart.x + (meteorEnd.x - meteorStart.x) * headT;
        const headY = meteorStart.y + (meteorEnd.y - meteorStart.y) * headT;
        const headZ = meteorStart.z + (meteorEnd.z - meteorStart.z) * headT;
        const tailX = meteorStart.x + (meteorEnd.x - meteorStart.x) * tailT;
        const tailY = meteorStart.y + (meteorEnd.y - meteorStart.y) * tailT;
        const tailZ = meteorStart.z + (meteorEnd.z - meteorStart.z) * tailT;
        const pos = meteorGeo.attributes.position;
        pos.array[0] = tailX;
        pos.array[1] = tailY;
        pos.array[2] = tailZ;
        pos.array[3] = headX;
        pos.array[4] = headY;
        pos.array[5] = headZ;
        pos.needsUpdate = true;
        const fadeIn = smoothstep(0, 0.1, p);
        const fadeOut = 1 - smoothstep(0.7, 1, p);
        meteorMat.opacity = fadeIn * fadeOut * 0.9;
      } else {
        meteorLine.visible = false;
        meteorMat.opacity = 0;
      }
    } else {
      meteorLine.visible = false;
      meteorMat.opacity = 0;
      if (reduced) {
        meteorState = Object.assign({}, meteorState, { pending: false, active: false });
      }
    }

    const refOp = mOp * 0.28;
    if (!reduced) {
      reflectionPhase += elapsed * 0.001;
      const wave = 0.85 + 0.15 * Math.sin(reflectionPhase * 2.5);
      reflectionMat.opacity = refOp * wave;
    } else {
      reflectionMat.opacity = refOp;
    }
  }

  function setVisible(visible) {
    group.visible = !!visible;
  }

  function dispose() {
    sunMat.dispose();
    sunGlowMat.dispose();
    moonMat.dispose();
    moonGlowMat.dispose();
    starGeo.dispose();
    starMat.dispose();
    meteorGeo.dispose();
    meteorMat.dispose();
    reflectionGeo.dispose();
    reflectionMat.dispose();
    if (group.parent) group.parent.remove(group);
  }

  function getMeteorState() {
    return meteorState;
  }

  function getStarOpacity() {
    return starMat.opacity;
  }

  function getSunOpacity() {
    return sunMat.opacity;
  }

  function getMoonOpacity() {
    return moonMat.opacity;
  }

  return {
    group: group,
    update: update,
    setVisible: setVisible,
    dispose: dispose,
    getMeteorState: getMeteorState,
    getStarOpacity: getStarOpacity,
    getSunOpacity: getSunOpacity,
    getMoonOpacity: getMoonOpacity
  };
}
