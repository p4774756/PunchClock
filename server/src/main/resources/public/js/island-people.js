import * as THREE from 'three';
import { P } from './island-kit.js';
import {
  NPC_STYLE_IDS,
  NPC_BUDGET,
  npcWomenAnim,
  npcSpawnPlan,
  idleFootY,
  cityWalkerStyles,
  miluPointsFromIdles
} from './island-people-data.js';
import { cityWalkerPaths } from './island-city-data.js';
import {
  createMiluView,
  createMiluState,
  miluStep,
  miluClipPath,
  screenAxes
} from './island-milu.js';

export {
  NPC_STYLE_IDS,
  NPC_BUDGET,
  npcSpawnPlan,
  cityWalkerStyles,
  miluPointsFromIdles,
  idleFootY
};

const sharedTexturesByStyle = new Map();

function startStyleTextureLoad(style, registry) {
  if (sharedTexturesByStyle.has(style)) return;
  const anim = npcWomenAnim(style);
  const anchor = createMiluView(THREE, { deferLoad: true, anim: anim });
  sharedTexturesByStyle.set(style, anchor.sharedTextures());
  if (registry) registry.textureAnchors.push(anchor);
  anchor.beginLoad();
}

function createNpcBillboard(style) {
  const anim = npcWomenAnim(style);
  const shared = sharedTexturesByStyle.get(style);
  const view = createMiluView(THREE, { shared: shared, anim: anim, deferLoad: !shared });
  if (!shared) {
    sharedTexturesByStyle.set(style, view.sharedTextures());
    view.beginLoad();
  }
  view.object.name = 'npc-' + style;
  return {
    view: view,
    anim: view.anim(),
    state: createMiluState(),
    dispose() {
      view.dispose();
    }
  };
}

export function npcClipPath(state, anim, seated) {
  if (seated) {
    const sit = anim.clips.idle.sit;
    if (sit && sit.length) {
      const frame = state && state.frame > 0 ? state.frame : 0;
      return sit[frame % sit.length] || sit[0];
    }
  }
  return miluClipPath(state, anim);
}

export function npcSeatedStep(state, dt, anim) {
  const sit = anim.clips.idle.sit;
  const src = state || createMiluState();
  const next = {
    mode: 'idle',
    facing: src.facing || 'front',
    frame: src.frame || 0,
    acc: src.acc || 0,
    blinkAcc: 0,
    blinking: false,
    waved: true
  };
  if (!sit || !sit.length) {
    return miluStep(src, dt, { anim: anim, moving: false, near: false, reduced: false });
  }
  const step = dt > 0 ? dt : 0;
  const interval = 1 / anim.fps.idle;
  next.acc += step;
  while (next.acc >= interval) {
    next.acc -= interval;
    next.frame = (next.frame + 1) % sit.length;
  }
  return next;
}

function sampleWalker(walker, along, t) {
  const path = walker.path;
  const count = path.length;
  if (walker.pingpong) {
    const span = Math.max(1, count - 1);
    const cycle = span * 2;
    const u = ((along % cycle) + cycle) % cycle;
    const forward = u <= span;
    const p = forward ? u : cycle - u;
    const index = Math.min(span - 1, Math.floor(p));
    const blend = p - index;
    return {
      from: path[index],
      to: path[index + 1],
      blend: blend,
      dir: forward ? 1 : -1,
      vx: 0,
      vz: 0
    };
  }
  const scaled = ((along % 1) + 1) % 1 * count;
  const index = Math.floor(scaled) % count;
  const from = path[index];
  const to = path[(index + 1) % count];
  const blend = scaled - Math.floor(scaled);
  const dx = to[0] - from[0];
  const dz = to[1] - from[1];
  return { from, to, blend, dir: walker.speed < 0 ? -1 : 1, vx: dx, vz: dz };
}

function waitForStyleTextures(styles) {
  const list = [];
  styles.forEach((style) => {
    list.push(new Promise((resolve) => {
      const map = sharedTexturesByStyle.get(style);
      if (!map) {
        resolve();
        return;
      }
      const poll = () => {
        const meta = map._miluLoad;
        if (map.size > 0) resolve();
        else if (meta && meta.failed) resolve();
        else setTimeout(poll, 40);
      };
      poll();
    }));
  });
  return Promise.all(list);
}

function collectStylesFromPlan(plan) {
  const styles = new Set();
  plan.idles.forEach((d) => styles.add(d.style));
  plan.walkers.forEach((d) => styles.add(d.style));
  plan.extras.forEach((d) => styles.add(d.style));
  cityWalkerStyles().forEach((s) => styles.add(s));
  return styles;
}

function fallbackMakePerson(THREE, kit, opts) {
  const { addMesh, geo } = kit;
  const group = new THREE.Group();
  const shirt = opts.shirt || 0xf4a0c0;
  addMesh(group, geo('box', [0.34, 0.7, 0.18]), shirt, 0, 0.35, 0);
  group.scale.setScalar(opts.scale || 1.05);
  return group;
}

function spawnIdleBillboard(scene, idles, def, registry) {
  const inst = createNpcBillboard(def.style);
  inst.view.object.position.set(def.x, idleFootY(def.seated), def.z);
  scene.add(inst.view.object);
  const row = {
    obj: inst.view.object,
    base: inst.view.object.position.y,
    phase: def.phase || 0,
    seated: def.seated,
    style: def.style,
    billboard: true,
    view: inst.view,
    anim: inst.anim,
    state: inst.state,
    npc: inst
  };
  idles.push(row);
  registry.disposers.push(inst);
  registry.idleRecords.push(row);
  return inst;
}

function spawnWalkerBillboard(scene, walkers, def, extra, registry) {
  const inst = createNpcBillboard(def.style);
  scene.add(inst.view.object);
  walkers.push({
    obj: inst.view.object,
    path: def.path,
    speed: def.speed,
    pingpong: !!def.pingpong,
    offset: def.offset != null ? def.offset : Math.random(),
    style: def.style,
    billboard: true,
    view: inst.view,
    anim: inst.anim,
    state: inst.state,
    npc: inst,
    cityOnly: !!(extra && extra.cityOnly)
  });
  registry.disposers.push(inst);
  return inst;
}

export function registerNpcBillboardMotion(loop, ctx) {
  const reduced = ctx.reduced;
  const walkers = ctx.walkers;
  const idles = ctx.idles;
  const ground = ctx.P;
  const camera = ctx.camera;
  const camThetaFn = typeof ctx.camTheta === 'function' ? ctx.camTheta : () => ctx.camTheta || 0;

  loop.register(function updateNpcBillboards(dt, t) {
    if (!camera) return;
    walkers.forEach((walker) => {
      if (!walker.billboard) return;
      const along = reduced ? walker.offset : walker.offset + t * Math.abs(walker.speed);
      const step = sampleWalker(walker, walker.pingpong ? along * walker.path.length : along, t);
      const dx = step.to[0] - step.from[0];
      const dz = step.to[1] - step.from[1];
      const x = step.from[0] + dx * step.blend;
      const z = step.from[1] + dz * step.blend;
      walker.obj.position.set(x, ground, z);
      walker.view.faceCamera(camera, x, z);
      const moving = !reduced && Math.abs(walker.speed) > 0;
      let wx = dx * step.dir;
      let wz = dz * step.dir;
      if (!wx && !wz && step.vx) {
        wx = step.vx;
        wz = step.vz;
      }
      const axes = screenAxes(wx, wz, camThetaFn());
      walker.state = miluStep(walker.state, reduced ? 0 : dt, {
        moving: moving,
        screenX: axes.x,
        screenZ: axes.z,
        anim: walker.anim,
        near: false,
        reduced: reduced
      });
      walker.view.show(npcClipPath(walker.state, walker.anim, false));
    });
    idles.forEach((item) => {
      if (!item.billboard) return;
      const x = item.obj.position.x;
      const z = item.obj.position.z;
      item.view.faceCamera(camera, x, z);
      if (item.seated) {
        item.state = npcSeatedStep(item.state, reduced ? 0 : dt, item.anim);
      } else {
        item.state = miluStep(item.state, reduced ? 0 : dt, {
          moving: false,
          screenX: 0,
          screenZ: 0,
          anim: item.anim,
          near: false,
          reduced: reduced
        });
      }
      item.view.show(npcClipPath(item.state, item.anim, item.seated));
      if (reduced) return;
      const bob = item.seated ? 0.004 : 0.012;
      item.obj.position.y = item.base + Math.sin(t * 1.5 + item.phase) * bob;
    });
  });
}

export function buildIslandPeople(ctx) {
  const { scene, kit, idles, walkers, reduced, qualityTier } = ctx;
  const lowTier = qualityTier === 'shadowOff' || qualityTier === 'shadowLow';
  const plan = npcSpawnPlan({ reduced, lowTier });
  const registry = {
    idleRecords: [],
    disposers: [],
    textureAnchors: []
  };
  const stylesNeeded = collectStylesFromPlan(plan);
  stylesNeeded.forEach((style) => startStyleTextureLoad(style, registry));
  const whenReady = waitForStyleTextures(stylesNeeded);

  function makePerson(opts) {
    const inst = createNpcBillboard('summer');
    if (inst && inst.view) {
      registry.disposers.push(inst);
      return inst.view.object;
    }
    return fallbackMakePerson(THREE, kit, opts || {});
  }

  function spawnFromPlan() {
    plan.idles.forEach((def) => spawnIdleBillboard(scene, idles, def, registry));
    plan.walkers.forEach((def) => spawnWalkerBillboard(scene, walkers, def, null, registry));
    plan.extras.forEach((def) => {
      if (def.walker && def.path) {
        spawnWalkerBillboard(scene, walkers, {
          style: def.style,
          path: def.path,
          speed: def.speed,
          pingpong: def.pingpong,
          offset: Math.random() * 0.5
        }, null, registry);
      } else {
        spawnIdleBillboard(scene, idles, {
          style: def.style,
          x: def.x,
          z: def.z,
          rotY: def.rotY,
          seated: false,
          phase: def.x * 0.1
        }, registry);
      }
    });
  }

  whenReady.then(spawnFromPlan).catch(spawnFromPlan);

  function spawnCityWalkers() {
    const paths = cityWalkerPaths();
    const styles = cityWalkerStyles();
    const count = plan.cityCount;
    for (let i = 0; i < count; i++) {
      spawnWalkerBillboard(
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
        registry
      );
    }
  }

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
      registry.textureAnchors.forEach((a) => a.dispose());
      registry.textureAnchors.length = 0;
      sharedTexturesByStyle.clear();
    },
    whenReady: whenReady,
    plan,
    registerBillboardMotion: registerNpcBillboardMotion
  };
}