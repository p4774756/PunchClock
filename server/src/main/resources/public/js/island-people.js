import * as THREE from 'three';
import { P } from './island-kit.js';
import {
  NPC_STYLE_IDS,
  NPC_BUDGET,
  npcWomenAnim,
  npcSpawnPlan,
  idleFootY,
  cityWalkerStyles,
  cityScooterAnim,
  miluPointsFromIdles,
  npcTalkProfile,
  resolveWalkerIslandId
} from './island-people-data.js';

export { freezeNpcWalker, unfreezeNpcWalker } from './island-people-data.js';
import { cityWalkerPaths, cityMotoPaths } from './island-city-data.js';
import { islandAt } from './island-layout.js';
import {
  createMiluView,
  createMiluState,
  miluStep,
  miluClipPath,
  screenAxes,
  resolveFacing
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
let cityScooterShared = null;
let cityScooterAnchor = null;
let talkSpawnSerial = 0;
let lastNpcMotionT = 0;

export function getNpcMotionTime() {
  return lastNpcMotionT;
}

export function npcGreetBobY(t0, now, reduced) {
  if (reduced || !Number.isFinite(t0) || !Number.isFinite(now)) return 0;
  const elapsed = (now - t0) / 1000;
  if (elapsed < 0 || elapsed > 0.4) return 0;
  return Math.abs(Math.sin(elapsed * Math.PI * 10)) * 0.04;
}

export function npcFacePlayer(state, anim, px, pz, nx, nz, camTheta, seated) {
  const axes = screenAxes(px - nx, pz - nz, camTheta);
  const src = state || createMiluState();
  const facing = resolveFacing(src.facing, axes.x, axes.z, anim, false);
  if (seated) {
    return Object.assign({}, src, { mode: 'idle', facing: facing, moving: false });
  }
  return Object.assign({}, src, { mode: 'idle', facing: facing, frame: 0, acc: 0 });
}

export function freezeNpcIdle(idle) {
  if (!idle) return;
  idle.talkHold = true;
}

export function unfreezeNpcIdle(idle) {
  if (!idle) return;
  idle.talkHold = false;
  idle.talkGreetAt = 0;
}

function talkMeta(style, islandId) {
  const idx = talkSpawnSerial++;
  return {
    talkKey: 'npc-' + idx,
    spawnIndex: idx,
    islandId: islandId || 'main',
    name: npcTalkProfile(style, idx).name,
    talkable: true,
    scooter: false
  };
}

export function collectTalkableNpcSnapshots(idles, walkers) {
  const out = [];
  if (idles) {
    for (let i = 0; i < idles.length; i++) {
      const row = idles[i];
      if (!row || row.talkable === false || !row.obj) continue;
      out.push({
        talkKey: row.talkKey,
        style: row.style,
        name: row.name,
        islandId: row.islandId || 'main',
        x: row.obj.position.x,
        z: row.obj.position.z,
        seated: !!row.seated,
        scooter: false,
        talkable: true,
        record: { kind: 'idle', row: row }
      });
    }
  }
  if (walkers) {
    for (let i = 0; i < walkers.length; i++) {
      const row = walkers[i];
      if (!row || row.talkable === false || row.scooter || !row.obj) continue;
      const isl = row.islandId;
      const fallback = islandAt(row.obj.position.x, row.obj.position.z, 0.15);
      out.push({
        talkKey: row.talkKey,
        style: row.style,
        name: row.name,
        islandId: isl || (fallback && fallback.id) || 'main',
        x: row.obj.position.x,
        z: row.obj.position.z,
        seated: false,
        scooter: !!row.scooter,
        talkable: true,
        record: { kind: 'walker', row: row }
      });
    }
  }
  return out;
}

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

function ensureCityScooterTextures(registry) {
  if (cityScooterShared) return cityScooterShared;
  const anim = cityScooterAnim();
  const anchor = createMiluView(THREE, { deferLoad: true, anim: anim });
  cityScooterShared = anchor.sharedTextures();
  cityScooterAnchor = anchor;
  if (registry) registry.textureAnchors.push(anchor);
  anchor.beginLoad();
  return cityScooterShared;
}

function waitForCityScooterTextures() {
  return new Promise((resolve) => {
    const map = cityScooterShared;
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
  });
}

function createCityScooterBillboard(registry) {
  ensureCityScooterTextures(registry);
  const anim = cityScooterAnim();
  const view = createMiluView(THREE, {
    shared: cityScooterShared,
    anim: anim,
    deferLoad: false
  });
  view.object.name = 'city-scooter';
  return {
    view: view,
    anim: view.anim(),
    state: createMiluState(),
    dispose() {
      view.dispose();
    }
  };
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
  const meta = talkMeta(def.style, def.islandId || 'main');
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
    npc: inst,
    talkKey: meta.talkKey,
    spawnIndex: meta.spawnIndex,
    islandId: meta.islandId,
    name: meta.name,
    talkable: meta.talkable,
    scooter: meta.scooter
  };
  idles.push(row);
  registry.disposers.push(inst);
  registry.idleRecords.push(row);
  return inst;
}

function spawnWalkerBillboard(scene, walkers, def, extra, registry) {
  const inst = createNpcBillboard(def.style);
  scene.add(inst.view.object);
  const islandId = resolveWalkerIslandId(def, extra);
  const meta = talkMeta(def.style, islandId);
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
    cityOnly: !!(extra && extra.cityOnly),
    talkKey: meta.talkKey,
    spawnIndex: meta.spawnIndex,
    islandId: meta.islandId,
    name: meta.name,
    talkable: meta.talkable,
    scooter: meta.scooter
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
    lastNpcMotionT = t;
    const now = typeof performance !== 'undefined' ? performance.now() : t * 1000;
    walkers.forEach((walker) => {
      if (!walker.billboard) return;
      const held = walker.talkHold;
      const along = held
        ? held.along
        : (reduced ? walker.offset : walker.offset + t * Math.abs(walker.speed));
      const step = sampleWalker(walker, walker.pingpong ? along * walker.path.length : along, t);
      const dx = step.to[0] - step.from[0];
      const dz = step.to[1] - step.from[1];
      const x = step.from[0] + dx * step.blend;
      const z = step.from[1] + dz * step.blend;
      const greetY = npcGreetBobY(walker.talkGreetAt || 0, now, reduced);
      walker.obj.position.set(x, ground + greetY, z);
      walker.view.faceCamera(camera, x, z);
      const moving = !held && !reduced && Math.abs(walker.speed) > 0;
      let wx = dx * step.dir;
      let wz = dz * step.dir;
      if (!wx && !wz && step.vx) {
        wx = step.vx;
        wz = step.vz;
      }
      const axes = screenAxes(wx, wz, camThetaFn());
      walker.state = miluStep(walker.state, (held || reduced) ? 0 : dt, {
        moving: moving,
        screenX: held ? 0 : axes.x,
        screenZ: held ? 0 : axes.z,
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
        item.state = npcSeatedStep(item.state, (item.talkHold || reduced) ? 0 : dt, item.anim);
      } else {
        item.state = miluStep(item.state, (item.talkHold || reduced) ? 0 : dt, {
          moving: false,
          screenX: 0,
          screenZ: 0,
          anim: item.anim,
          near: false,
          reduced: reduced
        });
      }
      item.view.show(npcClipPath(item.state, item.anim, item.seated));
      const greetY = npcGreetBobY(item.talkGreetAt || 0, now, reduced);
      if (reduced && !greetY) {
        item.obj.position.y = item.base;
        return;
      }
      const bob = item.talkHold ? 0 : (item.seated ? 0.004 : 0.012);
      const idleBob = item.talkHold ? 0 : Math.sin(t * 1.5 + item.phase) * bob;
      item.obj.position.y = item.base + idleBob + greetY;
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
  ensureCityScooterTextures(registry);
  const whenReady = Promise.all([
    waitForStyleTextures(stylesNeeded),
    waitForCityScooterTextures()
  ]);

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
          offset: Math.random() * 0.5,
          island: def.island
        }, null, registry);
      } else {
        spawnIdleBillboard(scene, idles, {
          style: def.style,
          x: def.x,
          z: def.z,
          rotY: def.rotY,
          seated: false,
          phase: def.x * 0.1,
          islandId: def.island || 'main'
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

  function spawnCityScooterWalkers() {
    const motoPaths = cityMotoPaths();
    for (let m = 0; m < motoPaths.length; m++) {
      const inst = createCityScooterBillboard(registry);
      scene.add(inst.view.object);
      walkers.push({
        obj: inst.view.object,
        path: motoPaths[m],
        speed: 0.08,
        pingpong: true,
        offset: m * 0.3,
        cityOnly: true,
        billboard: true,
        view: inst.view,
        anim: inst.anim,
        state: inst.state,
        npc: inst,
        talkable: false,
        scooter: true
      });
      registry.disposers.push(inst);
    }
  }

  return {
    makePerson,
    miluStaticPoints() {
      return miluPointsFromIdles(registry.idleRecords.length ? registry.idleRecords : idles);
    },
    spawnCityWalkers,
    spawnCityScooterWalkers,
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
      cityScooterShared = null;
      cityScooterAnchor = null;
    },
    whenReady: whenReady,
    plan,
    registerBillboardMotion: registerNpcBillboardMotion,
    collectTalkableNpcs: () => collectTalkableNpcSnapshots(idles, walkers)
  };
}