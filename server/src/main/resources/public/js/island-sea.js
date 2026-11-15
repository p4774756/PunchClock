import * as THREE from 'three';
import { ISLANDS } from './island-layout.js';
import { HEX } from './island-kit.js';

const WAVE_AMP = 0.032;
const WAVE_AMP_MAX = 0.038;
const WATER_Y = -0.74;
const FOAM_Y = WATER_Y + 0.012;

export function waveHeight(x, z, t, opts) {
  if (opts && opts.reduced) return 0;
  const w1 = Math.sin(x * 0.4 + t * 0.7) * 0.4;
  const w2 = Math.sin(z * 0.35 - t * 0.55 + 1.2) * 0.35;
  const w3 = Math.sin((x + z) * 0.28 + t * 0.42) * 0.25;
  const raw = (w1 + w2 + w3) * WAVE_AMP;
  const amp = opts && opts.amp != null ? opts.amp : WAVE_AMP;
  const cap = opts && opts.cap != null ? opts.cap : WAVE_AMP_MAX;
  return Math.max(-cap, Math.min(cap, raw * (amp / WAVE_AMP)));
}

export function foamRadius(visualR) {
  return visualR + 0.95 + 0.35;
}

export function foamCycle(t, phase, period) {
  const p = period || 5;
  const u = ((t + phase) % p) / p;
  const attack = 0.35;
  const hold = 0.15;
  const release = 0.5;
  let scale, opacity;
  if (u < attack) {
    const r = u / attack;
    scale = 0.7 + r * 0.3;
    opacity = 0.3 + r * 0.5;
  } else if (u < attack + hold) {
    scale = 1;
    opacity = 0.8;
  } else {
    const r = (u - attack - hold) / release;
    scale = 1 - r * 0.2;
    opacity = 0.8 - r * 0.8;
  }
  return { scale: scale, opacity: opacity };
}

export function foamCycleReduced() {
  return { scale: 0.85, opacity: 0.45 };
}

export function createIslandSea(THREE, opts) {
  const kit = opts.kit;
  const islands = opts.islands || ISLANDS;
  const reduced = !!opts.reduced;
  const water = opts.water;
  const sand = opts.sand;

  const group = new THREE.Group();
  let visible = true;
  let mix = 0;

  const dayWater = new THREE.Color(0x45c4c6);
  const nightWater = new THREE.Color(0x12325f);
  const dayFoam = new THREE.Color(0xffffff);
  const nightFoam = new THREE.Color(0x8090b8);

  const seaSize = 90;
  const seaSegs = 72;
  const seaGeo = new THREE.PlaneGeometry(seaSize, seaSize, seaSegs, seaSegs);
  const seaMat = new THREE.MeshLambertMaterial({
    color: dayWater.clone(),
    flatShading: true,
    transparent: false
  });
  seaMat.userData = { day: dayWater.clone(), night: nightWater.clone(), dayE: 0, nightE: 0 };
  if (kit) kit.addMaterial(seaMat);

  seaMat.onBeforeCompile = function (shader) {
    shader.uniforms.uTime = { value: 0 };
    shader.uniforms.uReduced = { value: reduced ? 1.0 : 0.0 };
    shader.vertexShader = 'uniform float uTime;\nuniform float uReduced;\n' + shader.vertexShader;
    shader.vertexShader = shader.vertexShader.replace(
      '#include <begin_vertex>',
      `#include <begin_vertex>
      if (uReduced < 0.5) {
        float w1 = sin(position.x * 0.4 + uTime * 0.7) * 0.4;
        float w2 = sin(position.y * 0.35 - uTime * 0.55 + 1.2) * 0.35;
        float w3 = sin((position.x + position.y) * 0.28 + uTime * 0.42) * 0.25;
        float wave = (w1 + w2 + w3) * ${WAVE_AMP.toFixed(4)};
        wave = clamp(wave, -${WAVE_AMP_MAX.toFixed(4)}, ${WAVE_AMP_MAX.toFixed(4)});
        transformed.z += wave;
      }`
    );
    seaMat.userData.shader = shader;
  };

  const seaMesh = new THREE.Mesh(seaGeo, seaMat);
  seaMesh.rotation.x = -Math.PI / 2;
  seaMesh.position.y = WATER_Y;
  seaMesh.receiveShadow = true;
  group.add(seaMesh);

  if (water) water.visible = true;

  const foamMeshes = [];
  const foamMats = [];
  const foamData = [];
  const foamGeo = new THREE.RingGeometry(1, 1.35, 6, 1, HEX);
  let period = 4.5;
  let phaseOffset = 0;
  islands.forEach((island, idx) => {
    const r = foamRadius(island.visualR) - 0.35;
    const mat = new THREE.MeshBasicMaterial({
      color: dayFoam.clone(),
      transparent: true,
      opacity: 0.5,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    foamMats.push(mat);
    const mesh = new THREE.Mesh(foamGeo, mat);
    mesh.rotation.x = -Math.PI / 2;
    mesh.rotation.z = HEX;
    mesh.position.set(island.x, FOAM_Y, island.z);
    mesh.scale.set(r, r, 1);
    group.add(mesh);
    foamMeshes.push(mesh);
    foamData.push({
      island: island,
      baseR: r,
      phase: phaseOffset,
      period: period + (idx % 3) * 0.4
    });
    phaseOffset += 1.7;
  });

  function setMix(t) {
    mix = t;
    seaMat.color.copy(dayWater).lerp(nightWater, t);
    foamMats.forEach((mat) => {
      mat.color.copy(dayFoam).lerp(nightFoam, t);
    });
  }

  function update(dt, elapsed) {
    if (!visible) return;
    const shader = seaMat.userData.shader;
    if (shader) shader.uniforms.uTime.value = elapsed;
    foamData.forEach((data, i) => {
      const cycle = reduced ? foamCycleReduced() : foamCycle(elapsed, data.phase, data.period);
      const mesh = foamMeshes[i];
      const r = data.baseR * cycle.scale;
      mesh.scale.set(r, r, 1);
      foamMats[i].opacity = cycle.opacity * (1 - mix * 0.35);
    });
  }

  function setVisible(v) {
    visible = !!v;
    group.visible = visible;
  }

  function dispose() {
    seaGeo.dispose();
    seaMat.dispose();
    foamGeo.dispose();
    foamMats.forEach((mat) => mat.dispose());
    if (kit) kit.removeMaterial(seaMat);
  }

  return {
    group: group,
    update: update,
    setMix: setMix,
    setVisible: setVisible,
    dispose: dispose
  };
}
