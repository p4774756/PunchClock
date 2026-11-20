// 海面起伏與岸邊浪花。可測試的純函式匯出給 island-sea.test.mjs。
import { ISLANDS } from './island-layout.js';
import { HEX } from './island-kit.js';
import { mainIslandFoamAlpha, mainIslandFoamBaseOpacity } from './island-decor.js';

const SEA_Y = -0.74;
const WAVE_AMP = 0.02;
const WAVE_AMP_MAX = 0.035;
// 風浪：只在 waveMul > 1（雨／暴風）時疊上的高頻碎浪，clear 完全不變。
const CHOP_AMP = 0.075;
const FOAM_Y = SEA_Y + WAVE_AMP_MAX + 0.012;

function normMul(mul) {
  return mul == null || !Number.isFinite(Number(mul)) ? 1 : Math.max(0, Number(mul));
}

export function waveHeight(x, z, t, reduced, ampMul) {
  if (reduced) return 0;
  const mul = normMul(ampMul);
  const w1 = Math.sin(x * 0.32 + t * 0.45) * 0.55;
  const w2 = Math.sin(z * 0.28 - t * 0.38) * 0.65;
  const w3 = Math.sin((x + z) * 0.22 + t * 0.52) * 0.48;
  const chop = Math.sin(x * 1.05 + t * 2.1) * Math.sin(z * 0.87 - t * 1.7);
  return (w1 + w2 + w3) * WAVE_AMP * mul + chop * CHOP_AMP * Math.max(0, mul - 1);
}

export function effectiveWaveAmp(mul) {
  const m = normMul(mul);
  return WAVE_AMP * m * 1.68 + CHOP_AMP * Math.max(0, m - 1);
}

export function waveAmplitudeMax() {
  return WAVE_AMP_MAX;
}

export function foamY() {
  return FOAM_Y;
}

export function foamRadius(visualR) {
  return visualR + 0.95 + 0.65;
}

export function foamPhase(index) {
  return (index * 1.37) % (Math.PI * 2);
}

const FOAM_PERIOD = 5.0;
const FOAM_RANGE = 0.55;

export function foamCycle(t, phase, reduced, waveMul) {
  if (reduced) return { scale: 1.0, alpha: 0.38 };
  const mul = normMul(waveMul);
  const storm = Math.min(1.4, Math.max(0, mul - 1));
  const local = ((t * (1 + storm * 0.6) + phase) % FOAM_PERIOD) / FOAM_PERIOD;
  const push = Math.sin(local * Math.PI);
  const scale = 1.0 + push * (0.12 + storm * 0.07);
  const alpha = Math.min(0.62, 0.22 + push * 0.32 + storm * 0.12);
  return { scale, alpha };
}

export function createIslandSea(THREE, opts) {
  const kit = opts.kit;
  const reduced = !!opts.reduced;

  const group = new THREE.Group();

  const SEG = 72;
  const SIZE = 90;
  const planeGeo = new THREE.PlaneGeometry(SIZE, SIZE, SEG, SEG);
  planeGeo.rotateX(-Math.PI / 2);

  const dayColor = new THREE.Color(0x45c4c6);
  const nightColor = new THREE.Color(0x12325f);

  const seaMat = new THREE.MeshLambertMaterial({
    color: dayColor.clone(),
    flatShading: true,
    side: THREE.FrontSide
  });
  seaMat.userData.day = dayColor.clone();
  seaMat.userData.night = nightColor.clone();
  seaMat.userData.dayE = 0;
  seaMat.userData.nightE = 0;

  let waveMul = 1;
  const uniforms = {
    uTime: { value: 0 },
    uReduced: { value: reduced ? 1.0 : 0.0 },
    uWaveMul: { value: 1.0 }
  };
  seaMat.onBeforeCompile = function (shader) {
    shader.uniforms.uTime = uniforms.uTime;
    shader.uniforms.uReduced = uniforms.uReduced;
    shader.uniforms.uWaveMul = uniforms.uWaveMul;
    shader.vertexShader = shader.vertexShader.replace(
      '#include <common>',
      `
      #include <common>
      uniform float uTime;
      uniform float uReduced;
      uniform float uWaveMul;
      `
    );
    shader.vertexShader = shader.vertexShader.replace(
      '#include <begin_vertex>',
      `
      #include <begin_vertex>
      if (uReduced < 0.5) {
        float w1 = sin(position.x * 0.32 + uTime * 0.45) * 0.55;
        float w2 = sin(position.z * 0.28 - uTime * 0.38) * 0.65;
        float w3 = sin((position.x + position.z) * 0.22 + uTime * 0.52) * 0.48;
        transformed.y += (w1 + w2 + w3) * ${WAVE_AMP.toFixed(4)} * uWaveMul;
        float storm = max(0.0, uWaveMul - 1.0);
        float chop = sin(position.x * 1.05 + uTime * 2.1) * sin(position.z * 0.87 - uTime * 1.7);
        float swell = sin((position.x - position.z) * 0.12 + uTime * 0.9) * 0.9;
        transformed.y += (chop + swell) * ${CHOP_AMP.toFixed(4)} * storm;
      }
      `
    );
  };

  const seaMesh = new THREE.Mesh(planeGeo, seaMat);
  seaMesh.position.y = SEA_Y;
  seaMesh.receiveShadow = false;
  group.add(seaMesh);

  if (kit) kit.addMaterial(seaMat);

  const foamMaterials = [];
  const foamMeshes = [];

  const foamDayColor = new THREE.Color(0xfffaf8);
  const foamNightColor = new THREE.Color(0x8090a8);

  ISLANDS.forEach((island, idx) => {
    const r = foamRadius(island.visualR);
    const innerR = island.visualR + 0.6;
    const shape = new THREE.Shape();
    for (let i = 0; i <= 6; i++) {
      const a = (i / 6) * Math.PI * 2 + HEX;
      const px = Math.cos(a) * r;
      const pz = Math.sin(a) * r;
      if (i === 0) shape.moveTo(px, pz);
      else shape.lineTo(px, pz);
    }
    const hole = new THREE.Path();
    for (let i = 0; i <= 6; i++) {
      const a = (i / 6) * Math.PI * 2 + HEX;
      const px = Math.cos(a) * innerR;
      const pz = Math.sin(a) * innerR;
      if (i === 0) hole.moveTo(px, pz);
      else hole.lineTo(px, pz);
    }
    shape.holes.push(hole);

    const foamGeo = new THREE.ShapeGeometry(shape, 1);
    foamGeo.rotateX(-Math.PI / 2);

    const foamMat = new THREE.MeshBasicMaterial({
      color: foamDayColor.clone(),
      transparent: true,
      opacity: 0.38,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    foamMat.userData.day = foamDayColor.clone();
    foamMat.userData.night = foamNightColor.clone();
    foamMat.userData.dayE = 0;
    foamMat.userData.nightE = 0;
    foamMat.userData.baseOpacity = island.id === 'main' ? mainIslandFoamBaseOpacity() : 0.38;

    const foam = new THREE.Mesh(foamGeo, foamMat);
    foam.position.set(island.x, FOAM_Y, island.z);
    foam.userData.phase = foamPhase(idx);
    foam.userData.baseScale = 1.0;
    group.add(foam);

    foamMaterials.push(foamMat);
    foamMeshes.push(foam);

    if (kit) kit.addMaterial(foamMat);
  });

  let mixValue = 0;

  function setMix(t) {
    mixValue = t;
    seaMat.color.copy(seaMat.userData.day).lerp(seaMat.userData.night, t);
    foamMaterials.forEach((mat) => {
      mat.color.copy(mat.userData.day).lerp(mat.userData.night, t);
    });
  }

  function setWaveMul(mul) {
    waveMul = mul == null || !Number.isFinite(Number(mul)) ? 1 : Math.max(0, Number(mul));
    uniforms.uWaveMul.value = waveMul;
  }

  function update(dt, elapsed) {
    uniforms.uTime.value = elapsed;
    if (reduced) return;
    foamMeshes.forEach((foam) => {
      const cycle = foamCycle(elapsed, foam.userData.phase, reduced, waveMul);
      foam.scale.setScalar(cycle.scale);
      const islandIdx = foamMeshes.indexOf(foam);
      const island = ISLANDS[islandIdx];
      if (island && island.id === 'main') {
        foam.material.opacity = mainIslandFoamAlpha(cycle.alpha, mixValue, reduced);
      } else {
        const nightDim = 1.0 - mixValue * 0.4;
        foam.material.opacity = cycle.alpha * nightDim * (1.0 - mixValue * 0.6);
      }
    });
  }

  function setVisible(visible) {
    group.visible = visible;
  }

  function dispose() {
    planeGeo.dispose();
    if (kit) kit.removeMaterial(seaMat);
    seaMat.dispose();
    foamMeshes.forEach((mesh) => {
      mesh.geometry.dispose();
      if (kit) kit.removeMaterial(mesh.material);
      mesh.material.dispose();
    });
  }

  return {
    group,
    update,
    setMix,
    setWaveMul,
    setVisible,
    dispose
  };
}
