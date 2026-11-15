// 日月星辰：太陽、月亮、星空、流星與月光倒影。
// 所有天空物件 fog: false、depthWrite: false，放在霧之外、ISLAND_CAMERA_FAR 之內。

import { ISLAND_CAMERA_FAR } from './island-interior.js';

export const STAR_COUNT = 600;
export const METEOR_INTERVAL_MIN = 8;
export const METEOR_INTERVAL_MAX = 20;
export const METEOR_DURATION_MIN = 0.6;
export const METEOR_DURATION_MAX = 1.0;
// 月光倒影 Y：水面約 -0.74，沙地頂約 -0.65，#71 浪花約 -0.693，倒影要在浪峰之上。
export const MOON_REFLECTION_Y = -0.69;
// 月亮仰角（弧度）：約 20°，探索模式抬頭時容易看到。
export const MOON_ELEVATION = 0.35;
// 月亮方位角（弧度）：從正 Z 軸順時針，放在探索模式容易看到的方向。
export const MOON_AZIMUTH = -0.5;

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
        elevation: 0.15 + rng() * 0.25
      };
    }
    return active;
  }

  function current() { return active; }

  return { step: step, current: current };
}

export function starTwinkle(phase, elapsed) {
  return 0.5 + 0.5 * Math.sin(elapsed * 1.2 + phase);
}

function createStarVertexShader() {
  return `
    attribute float size;
    attribute float brightness;
    varying float vBrightness;
    void main() {
      vBrightness = brightness;
      vec4 mvPosition = modelViewMatrix * vec4(position, 1.0);
      gl_PointSize = size * (1.0 / -mvPosition.z) * 300.0;
      gl_Position = projectionMatrix * mvPosition;
    }
  `;
}

function createStarFragmentShader() {
  return `
    uniform float opacity;
    varying float vBrightness;
    void main() {
      float dist = length(gl_PointCoord - vec2(0.5));
      if (dist > 0.5) discard;
      float alpha = smoothstep(0.5, 0.1, dist) * opacity * vBrightness;
      gl_FragColor = vec4(1.0, 1.0, 0.95, alpha);
    }
  `;
}

function createStars(THREE, count) {
  const positions = new Float32Array(count * 3);
  const phases = new Float32Array(count);
  const sizes = new Float32Array(count);
  const brightness = new Float32Array(count);
  const radius = ISLAND_CAMERA_FAR * 0.7;

  for (let i = 0; i < count; i++) {
    const theta = Math.random() * Math.PI * 2;
    // 分布在上半球，但更多集中在可見的仰角範圍（15°～75°）
    const phi = Math.acos(1 - Math.random() * 0.85);
    const r = radius;
    positions[i * 3] = r * Math.sin(phi) * Math.cos(theta);
    positions[i * 3 + 1] = r * Math.cos(phi);
    positions[i * 3 + 2] = r * Math.sin(phi) * Math.sin(theta);
    phases[i] = Math.random() * Math.PI * 2;
    sizes[i] = 2.5 + Math.random() * 3.5;
    brightness[i] = 1.0;
  }

  const geometry = new THREE.BufferGeometry();
  geometry.setAttribute('position', new THREE.BufferAttribute(positions, 3));
  geometry.setAttribute('size', new THREE.BufferAttribute(sizes, 1));
  geometry.setAttribute('brightness', new THREE.BufferAttribute(brightness, 1));

  const material = new THREE.ShaderMaterial({
    uniforms: {
      opacity: { value: 0 }
    },
    vertexShader: createStarVertexShader(),
    fragmentShader: createStarFragmentShader(),
    transparent: true,
    depthWrite: false,
    fog: false
  });

  const points = new THREE.Points(geometry, material);
  points.renderOrder = -1000;

  return {
    points: points,
    geometry: geometry,
    material: material,
    phases: phases,
    sizes: sizes,
    brightnessAttr: geometry.getAttribute('brightness')
  };
}

function createSoftDiscCanvas(inner, outer) {
  const size = 128;
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  const cx = size / 2;
  const grad = ctx.createRadialGradient(cx, cx, size * inner, cx, cx, size * outer);
  grad.addColorStop(0, 'rgba(255,255,255,1)');
  grad.addColorStop(0.45, 'rgba(255,255,255,0.9)');
  grad.addColorStop(0.75, 'rgba(255,255,255,0.12)');
  grad.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, size, size);
  return canvas;
}

function createCelestialBody(THREE, color, size, glowColor) {
  const group = new THREE.Group();

  const bodyTex = new THREE.CanvasTexture(createSoftDiscCanvas(0.02, 0.38));
  bodyTex.colorSpace = THREE.SRGBColorSpace;
  const bodyMat = new THREE.SpriteMaterial({
    map: bodyTex,
    color: color,
    transparent: true,
    opacity: 1,
    fog: false,
    depthWrite: false
  });
  const body = new THREE.Sprite(bodyMat);
  body.scale.set(size, size, 1);
  group.add(body);

  const glowTex = new THREE.CanvasTexture(createSoftDiscCanvas(0.08, 0.5));
  glowTex.colorSpace = THREE.SRGBColorSpace;
  const glowMat = new THREE.SpriteMaterial({
    map: glowTex,
    color: glowColor,
    transparent: true,
    opacity: 0.45,
    fog: false,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const glow = new THREE.Sprite(glowMat);
  glow.scale.set(size * 2.4, size * 2.4, 1);
  group.add(glow);

  group.renderOrder = -1001;

  return {
    group: group,
    bodyMat: bodyMat,
    glowMat: glowMat,
    setOpacity(o) {
      bodyMat.opacity = o;
      glowMat.opacity = o * 0.45;
    },
    dispose() {
      bodyMat.dispose();
      glowMat.dispose();
      bodyTex.dispose();
      glowTex.dispose();
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

function createReflectionTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 64;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');

  // 中間亮、兩側與遠端淡出的柔和漸層
  const gradient = ctx.createLinearGradient(0, 0, 0, canvas.height);
  gradient.addColorStop(0, 'rgba(200, 220, 255, 0)');
  gradient.addColorStop(0.2, 'rgba(200, 220, 255, 0.6)');
  gradient.addColorStop(0.5, 'rgba(220, 235, 255, 0.9)');
  gradient.addColorStop(0.8, 'rgba(200, 220, 255, 0.5)');
  gradient.addColorStop(1, 'rgba(180, 200, 240, 0)');
  ctx.fillStyle = gradient;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  // 橫向淡出
  const horizGradient = ctx.createLinearGradient(0, 0, canvas.width, 0);
  horizGradient.addColorStop(0, 'rgba(0, 0, 0, 0)');
  horizGradient.addColorStop(0.3, 'rgba(255, 255, 255, 1)');
  horizGradient.addColorStop(0.7, 'rgba(255, 255, 255, 1)');
  horizGradient.addColorStop(1, 'rgba(0, 0, 0, 0)');
  ctx.globalCompositeOperation = 'destination-in';
  ctx.fillStyle = horizGradient;
  ctx.fillRect(0, 0, canvas.width, canvas.height);

  return canvas;
}

function createMoonReflection(THREE) {
  const canvas = createReflectionTexture();
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;

  const geometry = new THREE.PlaneGeometry(2.5, 32);
  const material = new THREE.MeshBasicMaterial({
    map: texture,
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
  return { mesh: mesh, geometry: geometry, material: material, texture: texture };
}

export function createIslandSky(THREE, opts) {
  const reduced = !!(opts && opts.reduced);
  const sunPos = opts && opts.sunPos ? opts.sunPos : { x: -9.5, y: 20.5, z: 11 };

  const group = new THREE.Group();
  const disposables = [];

  const dist = ISLAND_CAMERA_FAR * 0.65;
  const sunDir = new THREE.Vector3(sunPos.x, sunPos.y, sunPos.z).normalize();
  const sunBaseY = sunDir.y * dist;

  // 太陽：放在 sun.position 方向，抬頭可見
  const sun = createCelestialBody(THREE, 0xfffde0, 22, 0xfff5a0);
  sun.group.position.set(sunDir.x * dist, sunBaseY, sunDir.z * dist);
  group.add(sun.group);
  disposables.push(sun);

  // 月亮：低仰角（約 20°），探索模式抬頭時容易看到
  const moonDist = ISLAND_CAMERA_FAR * 0.6;
  const moonX = Math.sin(MOON_AZIMUTH) * Math.cos(MOON_ELEVATION) * moonDist;
  const moonPosY = Math.sin(MOON_ELEVATION) * moonDist;
  const moonZ = Math.cos(MOON_AZIMUTH) * Math.cos(MOON_ELEVATION) * moonDist;
  const moonHorizon = 5;
  const moonBaseY = moonPosY;
  const moon = createCelestialBody(THREE, 0xe8eef8, 15, 0xc8d4f0);
  moon.group.position.set(moonX, moonHorizon, moonZ);
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
      reflection.texture.dispose();
    }
  });

  const meteorScheduler = createMeteorScheduler({ reduced: reduced });

  // 預先建立的向量，避免每幀 new
  const meteorDir = new THREE.Vector3();

  let currentT = 0;

  // 月光倒影：錨在開闊海面，沿月亮方向朝島延伸，縮短避免壓到土層。
  const reflectionAnchorR = 40;
  const reflectionLen = 32;
  const moonHorizX = Math.sin(MOON_AZIMUTH);
  const moonHorizZ = Math.cos(MOON_AZIMUTH);
  const reflectionRotY = Math.atan2(-moonHorizX, -moonHorizZ);

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

  function updateStarsOpacity(t) {
    const baseOpacity = starOpacity(t);
    stars.material.uniforms.opacity.value = baseOpacity;
    stars.points.visible = baseOpacity > 0.01;
  }

  function updateStarsTwinkle(elapsed) {
    if (reduced) return;
    const baseOpacity = stars.material.uniforms.opacity.value;
    if (baseOpacity <= 0.01) return;

    const phases = stars.phases;
    const brightnessArr = stars.brightnessAttr.array;
    const count = phases.length;

    for (let i = 0; i < count; i++) {
      brightnessArr[i] = starTwinkle(phases[i], elapsed);
    }
    stars.brightnessAttr.needsUpdate = true;
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

    // 流星在抬頭可見的天空範圍生成（仰角 15°～45°）
    const r = ISLAND_CAMERA_FAR * 0.45;
    const angle = state.angle;
    const elev = state.elevation;
    const startX = r * Math.cos(angle) * Math.cos(elev * Math.PI / 2);
    const startY = r * Math.sin(elev * Math.PI / 2) + 40;
    const startZ = r * Math.sin(angle) * Math.cos(elev * Math.PI / 2);

    const speed = 100;
    const traveled = state.elapsed * speed;
    meteorDir.set(-Math.cos(angle) * 0.4, -0.75, -Math.sin(angle) * 0.4).normalize();

    const headX = startX + meteorDir.x * traveled;
    const headY = startY + meteorDir.y * traveled;
    const headZ = startZ + meteorDir.z * traveled;

    const tailLen = Math.min(traveled, 20);
    const tailX = headX - meteorDir.x * tailLen;
    const tailY = headY - meteorDir.y * tailLen;
    const tailZ = headZ - meteorDir.z * tailLen;

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
    const o = moonOpacity(t) * 0.5;
    reflection.material.opacity = o;
    reflection.mesh.visible = o > 0.01;

    // 設定倒影位置和方向（reduced 模式也要設定）
    let waveOffset = 0;
    if (!reduced && o > 0.01) {
      waveOffset = Math.sin(elapsed * 0.4) * 0.3 + Math.sin(elapsed * 0.7) * 0.15;
    }
    const alongX = -moonHorizX;
    const alongZ = -moonHorizZ;
    const alongLen = Math.hypot(alongX, alongZ) || 1;
    const anchorX = moonHorizX * reflectionAnchorR;
    const anchorZ = moonHorizZ * reflectionAnchorR;
    const inset = reflectionLen * 0.38;
    reflection.mesh.position.x = anchorX + (alongX / alongLen) * inset + waveOffset;
    reflection.mesh.position.z = anchorZ + (alongZ / alongLen) * inset;
    reflection.mesh.rotation.z = reflectionRotY;
  }

  // onMix 呼叫：只更新透明度與位置，不重算星星閃爍
  function update(t, elapsed, camera) {
    currentT = t;
    updateSun(t);
    updateMoon(t);
    updateStarsOpacity(t);
    updateReflection(t, elapsed);
  }

  // loop.register 呼叫：更新動畫（閃爍、流星、倒影波動）
  function tick(dt, elapsed) {
    updateMeteor(currentT, dt, elapsed);
    updateStarsTwinkle(elapsed);
    if (!reduced) {
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

  // 初始化位置（含 reduced 模式的倒影位置）
  updateReflection(0, 0);
  update(0, 0, null);

  return {
    group: group,
    update: update,
    tick: tick,
    setVisible: setVisible,
    dispose: dispose
  };
}
