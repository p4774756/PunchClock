import * as THREE from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { SkeletonUtils } from 'three/addons/utils/SkeletonUtils.js';
import { G, P } from './island-kit.js';
import {
  NPC_STYLE_IDS,
  NPC_GLB_URL,
  NPC_BUDGET,
  NPC_GLB_STATS,
  npcSpawnPlan,
  idleFootY,
  revealNpcInstanceRoot,
  cityWalkerStyles,
  miluPointsFromIdles
} from './island-people-data.js';
import { cityWalkerPaths } from './island-city-data.js';

export {
  NPC_STYLE_IDS,
  NPC_BUDGET,
  NPC_GLB_STATS,
  npcSpawnPlan,
  cityWalkerStyles,
  miluPointsFromIdles,
  idleFootY
};

const glbCache = new Map();
let npcLibrary = null;
let npcLoadPromise = null;

function loadNpcGlb() {
  if (npcLoadPromise) return npcLoadPromise;
  npcLoadPromise = new Promise((resolve, reject) => {
    const loader = new GLTFLoader();
    loader.load(
      NPC_GLB_URL,
      (gltf) => {
        npcLibrary = buildNpcLibrary(gltf);
        glbCache.set(NPC_GLB_URL, gltf);
        resolve(npcLibrary);
      },
      undefined,
      reject
    );
  });
  return npcLoadPromise;
}

function buildNpcLibrary(gltf) {
  const templates = {};
  const clipsByStyle = {};
  for (const style of NPC_STYLE_IDS) {
    clipsByStyle[style] = { walk: null, idle: null, sit: null };
  }
  (gltf.animations || []).forEach((clip) => {
    const name = clip.name || '';
    for (const style of NPC_STYLE_IDS) {
      if (name === style + '_walk') clipsByStyle[style].walk = clip;
      if (name === style + '_idle') clipsByStyle[style].idle = clip;
      if (name === style + '_sit' || name === style + '_sit_idle') {
        clipsByStyle[style].sit = clip;
      }
    }
  });
  gltf.scene.traverse((obj) => {
    if (!obj.name || !obj.name.startsWith('npc_')) return;
    const style = obj.name.slice(4);
    if (NPC_STYLE_IDS.indexOf(style) === -1) return;
    templates[style] = obj;
    obj.visible = false;
    obj.traverse((child) => {
      child.visible = false;
    });
  });
  return { templates, clipsByStyle, gltf };
}

function fallbackMakePerson(THREE, kit, opts) {
  const { addMesh, addBox, geo } = kit;
  const group = new THREE.Group();
  const shirt = opts.shirt || 0xf4a0c0;
  const pants = opts.pants || 0x3e4a5a;
  const hair = opts.hair || 0x3a312c;
  const headGeo = geo('sph', [0.15, 7, 6]);
  const hairGeo = geo('sph', [0.155, 6, 5]);
  const legGeo = geo('box', [0.11, 0.28, 0.12]);
  const shoeGeo = geo('box', [0.12, 0.06, 0.16]);
  const torsoGeo = geo('box', [0.34, 0.36, 0.18]);
  const armGeo = geo('box', [0.09, 0.28, 0.09]);
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
  group.scale.setScalar(opts.scale || 1.05);
  return group;
}

function tintNpcMaterials(root, variant) {
  if (!variant) return;
  const hue = variant === 1 ? 1.04 : 0.96;
  root.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return;
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    mats.forEach((m) => {
      if (m.color) m.color.offsetHSL(0, 0, (hue - 1) * 0.15);
    });
  });
}

function createNpcInstance(lib, style, opts, kit) {
  const template = lib && lib.templates[style];
  if (!template) return null;
  const root = SkeletonUtils.clone(template);
  revealNpcInstanceRoot(root);
  root.traverse((obj) => {
    if (obj.isMesh) {
      obj.castShadow = true;
      obj.receiveShadow = true;
      if (obj.material) {
        const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
        const cloned = mats.map((m) => {
          const c = m.clone();
          c.vertexColors = !!(m.vertexColors && !m.map);
          c.userData = Object.assign({}, m.userData);
          c.userData.day = c.color ? c.color.clone() : new THREE.Color(0xffffff);
          c.userData.night = c.color ? c.color.clone().multiplyScalar(0.72) : new THREE.Color(0x888888);
          c.userData.dayE = c.emissiveIntensity || 0;
          c.userData.nightE = c.emissiveIntensity ? c.emissiveIntensity * 0.85 : 0;
          if (kit && typeof kit.addMaterial === 'function') kit.addMaterial(c);
          return c;
        });
        obj.material = cloned.length === 1 ? cloned[0] : cloned;
      }
    }
  });
  tintNpcMaterials(root, opts && opts.variant);
  if (opts && opts.seated) root.scale.setScalar(0.92);

  const mixer = new THREE.AnimationMixer(root);
  const clips = lib.clipsByStyle[style] || {};
  const idleClip = (opts && opts.seated && clips.sit) ? clips.sit : clips.idle;
  const walkClip = clips.walk;
  const idleAction = idleClip ? mixer.clipAction(idleClip) : null;
  const walkAction = walkClip ? mixer.clipAction(walkClip) : null;
  if (idleAction) {
    idleAction.play();
  }

  return {
    obj: root,
    mixer,
    idleAction,
    walkAction,
    style,
    seated: !!(opts && opts.seated),
    dispose() {
      if (mixer) mixer.stopAllAction();
      root.traverse((child) => {
        if (child.isMesh) {
          if (child.geometry) child.geometry.dispose();
          const mats = Array.isArray(child.material) ? child.material : [child.material];
          mats.forEach((m) => { if (m) m.dispose(); });
        }
      });
    }
  };
}

function spawnIdle(lib, scene, idles, def, kit) {
  const inst = createNpcInstance(lib, def.style, def, kit);
  if (!inst) return null;
  inst.obj.position.set(def.x, idleFootY(def.seated), def.z);
  inst.obj.rotation.y = def.rotY || 0;
  scene.add(inst.obj);
  idles.push({
    obj: inst.obj,
    base: inst.obj.position.y,
    phase: def.phase || 0,
    mixer: inst.mixer,
    idleAction: inst.idleAction,
    walkAction: inst.walkAction,
    seated: def.seated,
    npc: inst,
    style: def.style
  });
  return inst;
}

function spawnWalker(lib, scene, walkers, def, extra, kit) {
  const inst = createNpcInstance(lib, def.style, def, kit);
  if (!inst) return null;
  scene.add(inst.obj);
  walkers.push({
    obj: inst.obj,
    path: def.path,
    speed: def.speed,
    pingpong: !!def.pingpong,
    offset: def.offset != null ? def.offset : Math.random(),
    mixer: inst.mixer,
    idleAction: inst.idleAction,
    walkAction: inst.walkAction,
    style: def.style,
    npc: inst,
    cityOnly: !!(extra && extra.cityOnly)
  });
  return inst;
}

export function buildIslandPeople(ctx) {
  const { scene, kit, idles, walkers, reduced, qualityTier } = ctx;
  const lowTier = qualityTier === 'shadowOff' || qualityTier === 'shadowLow';
  const plan = npcSpawnPlan({ reduced, lowTier });
  const registry = {
    idleRecords: [],
    disposers: []
  };

  function makePerson(opts) {
    if (npcLibrary) {
      const inst = createNpcInstance(npcLibrary, 'summer', opts || {}, kit);
      if (inst) {
        registry.disposers.push(inst);
        return inst.obj;
      }
    }
    return fallbackMakePerson(THREE, kit, opts || {});
  }

  function spawnFromPlan(lib) {
    plan.idles.forEach((def) => {
      const inst = spawnIdle(lib, scene, idles, def, kit);
      if (inst) registry.idleRecords.push(idles[idles.length - 1]);
    });
    plan.walkers.forEach((def) => spawnWalker(lib, scene, walkers, def, null, kit));
    plan.extras.forEach((def) => {
      if (def.walker && def.path) {
        spawnWalker(lib, scene, walkers, {
          style: def.style,
          path: def.path,
          speed: def.speed,
          pingpong: def.pingpong,
          offset: Math.random() * 0.5
        }, null, kit);
      } else {
        spawnIdle(lib, scene, idles, {
          style: def.style,
          x: def.x,
          z: def.z,
          rotY: def.rotY,
          seated: false,
          phase: def.x * 0.1
        }, kit);
        registry.idleRecords.push(idles[idles.length - 1]);
      }
    });
  }

  function spawnCityWalkers() {
    const lib = npcLibrary;
    if (!lib) return;
    const paths = cityWalkerPaths();
    const styles = cityWalkerStyles();
    const count = plan.cityCount;
    for (let i = 0; i < count; i++) {
      spawnWalker(
        lib,
        scene,
        walkers,
        {
          style: styles[i % styles.length],
          path: paths[i % paths.length],
          speed: 0.038 + (i % 3) * 0.006,
          pingpong: i % 2 === 1,
          offset: i * 0.17
        },
        { cityOnly: true },
        kit
      );
    }
  }

  loadNpcGlb()
    .then((lib) => {
      npcLibrary = lib;
      spawnFromPlan(lib);
    })
    .catch(() => {});

  return {
    makePerson,
    miluStaticPoints() {
      return miluPointsFromIdles(registry.idleRecords.length ? registry.idleRecords : idles);
    },
    spawnCityWalkers,
    npcDisposeAll() {
      registry.disposers.forEach((d) => d.dispose());
      idles.forEach((item) => {
        if (item.npc && item.npc.dispose) item.npc.dispose();
      });
      walkers.forEach((w) => {
        if (w.npc && w.npc.dispose) w.npc.dispose();
      });
    },
    whenReady: loadNpcGlb(),
    plan,
    library: () => npcLibrary
  };
}