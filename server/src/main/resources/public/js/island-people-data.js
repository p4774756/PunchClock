// 行人 NPC 資料與預算（node:test 可載入，不依賴 WebGL）。
import { G, P } from './island-kit.js';
import { MILU_ANIM } from './island-milu.js';

export const NPC_STYLE_IDS = Object.freeze([
  'summer',
  'student',
  'tech',
  'idol',
  'swimsuit',
  'sport',
  'fairy'
]);

export const NPC_PET_BASE = '/pet/npc-women/';

export const NPC_BUDGET = Object.freeze({
  MAX_INSTANCES: 24,
  FRAME_PX: 400,
  MAX_STYLE_TEXTURE_SETS: 7
});

const WALK_CLIPS = Object.freeze({
  left: Object.freeze(['walk/left_01.png', 'walk/left_02.png', 'walk/left_03.png', 'walk/left_04.png']),
  right: Object.freeze(['walk/right_01.png', 'walk/right_02.png', 'walk/right_03.png', 'walk/right_04.png']),
  front: Object.freeze(['walk/front_01.png', 'walk/front_02.png', 'walk/front_03.png', 'walk/front_04.png']),
  back: Object.freeze(['walk/back_01.png', 'walk/back_02.png', 'walk/back_03.png', 'walk/back_04.png'])
});

const IDLE_CLIPS = Object.freeze({
  breath: Object.freeze([
    'idle/breath_01.png', 'idle/breath_02.png', 'idle/breath_03.png', 'idle/breath_04.png'
  ]),
  blink: Object.freeze(['idle/blink_01.png', 'idle/blink_02.png']),
  backBreath: Object.freeze([
    'idle/back_breath_01.png', 'idle/back_breath_02.png', 'idle/back_breath_03.png', 'idle/back_breath_04.png'
  ]),
  backBlink: Object.freeze(['idle/back_blink_01.png', 'idle/back_blink_02.png'])
});

const SIT_CLIP = Object.freeze(['sit/sit_01.png', 'sit/sit_02.png']);

const NPC_ANIM_BY_STYLE = Object.freeze(
  Object.fromEntries(
    NPC_STYLE_IDS.map((style) => {
      const hasSit = style === 'student' || style === 'idol' || style === 'sport';
      return [style, Object.freeze({
        footV: MILU_ANIM.footV,
        height: MILU_ANIM.height,
        shadowRadius: MILU_ANIM.shadowRadius,
        alphaTest: MILU_ANIM.alphaTest,
        faceDeadzone: MILU_ANIM.faceDeadzone,
        faceBucket: MILU_ANIM.faceBucket,
        faceHysteresis: MILU_ANIM.faceHysteresis,
        waveNpcRadius: MILU_ANIM.waveNpcRadius,
        waveZoneExtra: MILU_ANIM.waveZoneExtra,
        fps: MILU_ANIM.fps,
        blinkEvery: MILU_ANIM.blinkEvery,
        baseUrl: NPC_PET_BASE + style + '/',
        clips: Object.freeze({
          walk: WALK_CLIPS,
          idle: Object.freeze({
            breath: IDLE_CLIPS.breath,
            blink: IDLE_CLIPS.blink,
            backBreath: IDLE_CLIPS.backBreath,
            backBlink: IDLE_CLIPS.backBlink,
            sit: hasSit ? SIT_CLIP : Object.freeze([])
          })
        })
      })];
    })
  )
);

export function npcWomenAnim(style) {
  return NPC_ANIM_BY_STYLE[style] || NPC_ANIM_BY_STYLE.summer;
}

export function npcRequiredFramePaths(style) {
  const anim = npcWomenAnim(style);
  const out = [];
  const seen = Object.create(null);
  function addList(list) {
    if (!list) return;
    for (let i = 0; i < list.length; i++) {
      const rel = list[i];
      if (!rel || seen[rel]) continue;
      seen[rel] = true;
      out.push(rel);
    }
  }
  const walk = anim.clips.walk || {};
  const idle = anim.clips.idle || {};
  addList(walk.left);
  addList(walk.right);
  addList(idle.breath);
  addList(idle.blink);
  addList(walk.front);
  addList(walk.back);
  addList(idle.backBreath);
  addList(idle.backBlink);
  addList(idle.sit);
  return out;
}

export const MAIN_RING_WALK = Object.freeze([
  [-5.6, 4.7], [-1.8, 6.35], [2.1, 6.4], [5.6, 4.3],
  [6.05, 1.0], [5.3, -3.3], [1.1, -5.15], [-3.5, -5.05],
  [-6.15, -1.6], [-6.3, 2.1]
]);

export const EAST_PIER_WALK = Object.freeze([
  [5.6, 1.15], [7.4, 1.6], [9.1, 1.95]
]);

/** 主島環島 walker 與棧橋路徑 */
export function mainWalkerDefs() {
  return Object.freeze([
    { style: 'sport', path: MAIN_RING_WALK, speed: 0.045, pingpong: false, offset: 0.1 },
    { style: 'student', path: EAST_PIER_WALK, speed: 0.05, pingpong: true, offset: 0.2 }
  ]);
}

/** 主島靜態／傘桌閒置（x,z 與造型） */
export function mainIdleDefs() {
  return Object.freeze([
    { style: 'summer', x: 0.15, z: 0.42, rotY: 0, seated: false, phase: 0.2 },
    { style: 'summer', x: 0.05, z: 2.25, rotY: Math.PI, seated: false, phase: 0.8, variant: 1 },
    { style: 'idol', x: -0.05, z: 2.9, rotY: Math.PI, seated: false, phase: 1.4 },
    { style: 'summer', x: 0.12, z: 3.55, rotY: Math.PI, seated: false, phase: 2.1, variant: 1 },
    { style: 'sport', x: -5.15, z: 5.05, rotY: Math.PI * 0.85, seated: true, phase: 0.5 },
    { style: 'idol', x: -4.85, z: 2.95, rotY: 0, seated: true, phase: 1.6, variant: 1 },
    { style: 'student', x: 4.55, z: 4.08, rotY: Math.PI, seated: true, phase: 2.4 },
    { style: 'swimsuit', x: -0.15, z: 8.45, rotY: 0.15, seated: false, phase: 0.9 }
  ]);
}

/** 其他島與城市以外的主場景補點（總量 ≤ 24） */
export function islandExtraDefs() {
  return Object.freeze([
    { style: 'tech', x: 1.05, z: 15.25, rotY: -0.35, walker: false, island: 'computer' },
    { style: 'student', x: 1.0, z: 15.4, rotY: -0.4, walker: true,
      path: [[0.35, 15.9], [1.45, 16.15], [2.15, 15.25]], speed: 0.042, pingpong: true },
    { style: 'fairy', x: -16.2, z: 0.35, rotY: 0.6, walker: false, island: 'tcm' },
    { style: 'fairy', x: -15.1, z: 1.55, rotY: -1.2, walker: true,
      path: [[-16.35, 0.25], [-14.85, 0.95], [-15.05, 1.75]], speed: 0.04, pingpong: true },
    { style: 'swimsuit', x: 21.35, z: 5.35, rotY: 0.3, walker: false, island: 'beach' },
    { style: 'swimsuit', x: 22.15, z: 6.25, rotY: -2.0, walker: true,
      path: [[21.55, 6.05], [22.45, 5.85], [22.05, 6.35]], speed: 0.038, pingpong: true },
    { style: 'idol', x: 15.25, z: -5.45, rotY: 2.35, walker: false, island: 'casino' },
    { style: 'sport', x: 15.05, z: -7.85, rotY: -0.5, walker: true,
      path: [[14.55, -7.05], [15.05, -7.55], [14.75, -8.15]], speed: 0.044, pingpong: false }
  ]);
}

/** 城市島 6 名行人造型（路徑仍用 cityWalkerPaths） */
export function cityWalkerStyles() {
  return Object.freeze(['student', 'sport', 'idol', 'student', 'summer', 'tech']);
}

export function npcSpawnPlan(opts) {
  const reduced = !!(opts && opts.reduced);
  const lowTier = !!(opts && opts.lowTier);
  const idles = mainIdleDefs();
  const walkers = mainWalkerDefs().slice();
  const extras = islandExtraDefs();
  if (reduced || lowTier) {
    return {
      idles: idles.slice(0, 6),
      walkers: walkers.slice(0, 1),
      extras: extras.slice(0, 3),
      cityCount: 4
    };
  }
  return { idles, walkers, extras, cityCount: 6 };
}

export function countNpcInstances(plan) {
  return (
    plan.idles.length +
    plan.walkers.length +
    plan.extras.length +
    plan.cityCount
  );
}

export function idleFootY(seated) {
  return seated ? G : P;
}

export function samplePathPoints(path, stepsPerEdge = 6) {
  const out = [];
  if (!path || !path.length) return out;
  const closed = path.length > 2;
  const edges = closed ? path.length : path.length - 1;
  for (let i = 0; i < edges; i++) {
    const a = path[i];
    const b = path[(i + 1) % path.length];
    for (let s = 0; s < stepsPerEdge; s++) {
      const t = s / stepsPerEdge;
      out.push([a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t]);
    }
  }
  return out;
}

/** 路徑採樣是否落在可走區（需傳入 walkable(x,z,half)） */
export function pathWalkableSamples(path, walkable, half = 0.16) {
  const bad = [];
  const pts = samplePathPoints(path, 5);
  for (let i = 0; i < pts.length; i++) {
    const p = pts[i];
    if (!walkable(p[0], p[1], half)) bad.push(i);
  }
  return bad;
}

export function allNpcPathsForTests(plan) {
  const paths = [];
  plan.walkers.forEach((w) => paths.push(w.path));
  plan.extras.forEach((e) => {
    if (e.path) paths.push(e.path);
  });
  return paths;
}

export function miluPointsFromIdles(idleObjects) {
  return idleObjects.map((item) => ({
    x: item.obj.position.x,
    z: item.obj.position.z
  }));
}