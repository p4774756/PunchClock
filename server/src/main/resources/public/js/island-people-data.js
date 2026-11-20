// 行人 NPC 資料與預算（node:test 可載入，不依賴 WebGL）。
import { G, P } from './island-kit.js';
import { MILU_ANIM } from './island-milu.js';
import { islandAt } from './island-layout.js';

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

const SCOOTER_WALK_CLIPS = Object.freeze({
  left: Object.freeze(['walk/left_01.png', 'walk/left_02.png', 'walk/left_03.png']),
  right: Object.freeze(['walk/right_01.png', 'walk/right_02.png', 'walk/right_03.png']),
  front: Object.freeze(['walk/front_01.png', 'walk/front_02.png', 'walk/front_03.png']),
  back: Object.freeze(['walk/back_01.png', 'walk/back_02.png', 'walk/back_03.png'])
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

/** 城市機車騎乘看板（不計入 NPC_STYLE_IDS／七套貼圖預算） */
export function cityScooterAnim() {
  return Object.freeze({
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
    baseUrl: NPC_PET_BASE + 'scooter/',
    clips: Object.freeze({
      walk: SCOOTER_WALK_CLIPS,
      idle: Object.freeze({
        breath: Object.freeze(['walk/front_01.png']),
        blink: Object.freeze([]),
        backBreath: Object.freeze(['walk/back_01.png']),
        backBlink: Object.freeze([]),
        sit: Object.freeze([])
      })
    })
  });
}

export function cityScooterFramePaths() {
  const anim = cityScooterAnim();
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
  addList(walk.left);
  addList(walk.right);
  addList(walk.front);
  addList(walk.back);
  return out;
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
    { style: 'sport', path: MAIN_RING_WALK, speed: 0.045, pingpong: false, offset: 0.1, island: 'main' },
    { style: 'student', path: EAST_PIER_WALK, speed: 0.05, pingpong: true, offset: 0.2, island: 'main' }
  ]);
}

function sampleNpcWalkerPath(walker, along) {
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
      blend: blend
    };
  }
  const scaled = ((along % 1) + 1) % 1 * count;
  const index = Math.floor(scaled) % count;
  const from = path[index];
  const to = path[(index + 1) % count];
  const blend = scaled - Math.floor(scaled);
  return { from: from, to: to, blend: blend };
}

export function freezeNpcWalker(walker, t, reduced) {
  if (!walker || walker.talkHold) return;
  const along = reduced
    ? walker.offset
    : walker.offset + t * Math.abs(walker.speed);
  walker.talkHold = { along: along, greetUntil: 0 };
}

export function unfreezeNpcWalker(walker, t, reduced) {
  if (!walker || !walker.talkHold) return;
  walker.offset = reduced
    ? walker.talkHold.along
    : walker.talkHold.along - t * Math.abs(walker.speed);
  walker.talkHold = null;
  walker.talkGreetAt = 0;
}

export function npcWalkerSamplePosition(walker, t, reduced) {
  if (!walker || !walker.path || !walker.path.length) return { x: 0, z: 0 };
  const held = walker.talkHold;
  const along = held
    ? held.along
    : (reduced ? walker.offset : walker.offset + t * Math.abs(walker.speed));
  const scaled = walker.pingpong ? along * walker.path.length : along;
  const step = sampleNpcWalkerPath(walker, scaled);
  const x = step.from[0] + (step.to[0] - step.from[0]) * step.blend;
  const z = step.from[1] + (step.to[1] - step.from[1]) * step.blend;
  return { x: x, z: z };
}

export function resolveWalkerIslandId(def, extra) {
  if (extra && extra.cityOnly) return 'city';
  const explicit = def && (def.islandId || def.island);
  if (explicit) return explicit;
  if (def && def.path && def.path.length) {
    const hit = islandAt(def.path[0][0], def.path[0][1], 0.15);
    if (hit && hit.id) return hit.id;
  }
  return 'main';
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
    { style: 'student', x: 1.0, z: 15.4, rotY: -0.4, walker: true, island: 'computer',
      path: [[0.35, 15.9], [1.45, 16.15], [2.15, 15.25]], speed: 0.042, pingpong: true },
    { style: 'fairy', x: -16.2, z: 0.35, rotY: 0.6, walker: false, island: 'tcm' },
    { style: 'fairy', x: -15.1, z: 1.55, rotY: -1.2, walker: true, island: 'tcm',
      path: [[-16.35, 0.25], [-14.85, 0.95], [-15.05, 1.75]], speed: 0.04, pingpong: true },
    { style: 'swimsuit', x: 21.35, z: 5.35, rotY: 0.3, walker: false, island: 'beach' },
    { style: 'swimsuit', x: 22.15, z: 6.25, rotY: -2.0, walker: true, island: 'beach',
      path: [[21.55, 6.05], [22.45, 5.85], [22.05, 6.35]], speed: 0.038, pingpong: true },
    { style: 'idol', x: 15.25, z: -5.45, rotY: 2.35, walker: false, island: 'casino' },
    { style: 'sport', x: 15.05, z: -7.85, rotY: -0.5, walker: true, island: 'casino',
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

/** 對話自動關閉距離（主角離路人） */
export const NPC_TALK_CLOSE_DIST = 3;

export const NPC_TALK_ACTION = 'npcTalk';

const NPC_NAMES = Object.freeze({
  summer: Object.freeze(['夏蘿', '柚子', '小葵', '晴晴']),
  student: Object.freeze(['書妍', '知予', '安安', '小語']),
  tech: Object.freeze(['程程', '艾琳', '小芸', 'Ruby']),
  idol: Object.freeze(['星奈', '玲音', '小響', 'Mika']),
  swimsuit: Object.freeze(['海音', '汐見', '小渚', '珊珊']),
  sport: Object.freeze(['小凜', '瑩瑩', '阿蓁', 'Joy']),
  fairy: Object.freeze(['綺夢', '紗羽', '若汐', '靈兒'])
});

const NPC_GENERAL_LINES = Object.freeze({
  summer: Object.freeze([
    '今天太陽好大，我已經在想等一下要喝什麼了！',
    '你也出來散步嗎？這種天氣不出門太可惜了。',
    '我的髮夾剛剛差點被風吹走，嚇死我了哈哈。',
    '走累了就找個陰涼的地方坐一下吧！',
    '我最喜歡夏天了，連影子看起來都很有精神。',
    '你的髮色好可愛，跟今天的天空很配耶。'
  ]),
  student: Object.freeze([
    '我在找寫生的角度，這裡的風景好適合畫下來。',
    '偷偷說，我其實是出來透氣的，作業還沒寫完……',
    '你知道嗎？島跟島之間有橋，用走的也能逛很遠喔。',
    '我的筆記本快寫滿了，今天看到的都想記下來。',
    '等考完試，我要把每座島都逛一遍！',
    '你看起來很會探險，有推薦的地方嗎？'
  ]),
  tech: Object.freeze([
    '剛把一個 bug 修掉，出來走走獎勵自己。',
    '測試全綠的那一刻，比喝咖啡還提神。',
    '我在等程式跑完，順便出來吹個風。',
    '記得打卡喔，不然下班會忘記的。',
    '耳機裡放 Lo-fi，配這片海剛剛好。',
    '寫程式卡住的時候，起來走一圈常常就想通了。'
  ]),
  idol: Object.freeze([
    '嗨嗨～今天也要元氣滿滿喔！',
    '我在練新歌，你要當我的第一位聽眾嗎？',
    '舞步練到一半，先出來喘口氣。',
    '被認出來了嗎？噓——我今天是私下出來散步的。',
    '唱歌之前要先笑，聲音才會亮起來喔。',
    '下次有表演，我一定留個好位子給你！'
  ]),
  swimsuit: Object.freeze([
    '海風鹹鹹的，好有度假的感覺！',
    '記得擦防曬喔，這裡的太陽很會偷偷曬人。',
    '我剛游完一圈，水好清涼。',
    '撿到一個好漂亮的貝殼——拍張照就好，還是放回去吧。',
    '坐船去別的島，看海的角度又不一樣喔。',
    '浪聲聽久了，整個人都放鬆下來了。'
  ]),
  sport: Object.freeze([
    '早安！我剛跑完一圈，精神超好！',
    '要不要一起做個伸展？手舉高，深呼吸～',
    '運動完喝水最重要，別等口渴才喝喔。',
    '我今天的目標是繞完整座島，還差一點！',
    '慢慢走也是運動，重點是每天都動一動。',
    '流汗的感覺好舒服，整個人都清醒了。'
  ]),
  fairy: Object.freeze([
    '風輕輕的，像有人在幫你順氣呢。',
    '深呼吸三次，肩膀會鬆一點喔。',
    '累的時候按一按合谷穴，會舒服很多。',
    '我喜歡安靜的地方，聽得見葉子的聲音。',
    '慢慢來就好，身體會告訴你什麼時候該休息。',
    '你今天氣色不錯，有好好睡覺吧？'
  ])
});

const NPC_ISLAND_LINES = Object.freeze({
  main: Object.freeze([
    '神之島的飲料店你去過了嗎？我每次都點不同口味。',
    '手搖要半糖少冰，這是我的堅持！',
    '從棧橋那邊可以搭船去別的島喔。',
    '廣場的施工進度牌，可以看到島上接下來要蓋什麼。',
    '塗鴉牆你看過了嗎？你也可以留下一筆喔。'
  ]),
  tcm: Object.freeze([
    '推拿館裡面有一間經絡研習室，銅人就在那裡。',
    '推拿館的師傅手法很好，肩頸卡卡可以去試試。',
    '你知道百會穴在哪嗎？就在頭頂正中間喔。',
    '經絡研習室可以看穴位，學起來很有趣。',
    '這座島飄著藥草香，聞了心情就很平靜。'
  ]),
  computer: Object.freeze([
    '研發樓裡的大家都在寫程式，好認真。',
    '上班記得打卡，下班也別忘了喔。',
    '加班前我會先出來走一圈，腦袋比較清楚。',
    '研發樓晚上燈亮起來，像一整排螢幕。',
    '這裡的人講話三句不離 bug，我已經習慣了。'
  ]),
  casino: Object.freeze([
    '賭場的霓虹燈晚上超漂亮，我常來拍照。',
    '裡面有 21 點，我只看不玩，湊熱鬧就好。',
    '進賭場記得量力而為，開心最重要。',
    '晚上這裡最熱鬧，音樂一下就想跳舞。',
    '門口的燈一閃一閃，好像在跟你打招呼。'
  ]),
  beach: Object.freeze([
    '沙灘島的海最藍了，一定要來踩踩水。',
    '浮潛記得找人一起，安全第一。',
    '貝殼很漂亮，拍照就好，留給下一個人。',
    '躺在沙灘上聽浪聲，差點睡著。',
    '太陽下山的時候，這裡的海會變成金色喔。'
  ]),
  city: Object.freeze([
    '城市島的 7-11 什麼都有，我很常來補貨。',
    '你有看到那台粉紅色的小車嗎？超可愛的！',
    '港灣那邊風很大，看船進出很療癒。',
    '這裡的街道一格一格的，走起來像在逛棋盤。',
    '城市島晚上招牌都亮起來，好熱鬧。'
  ])
});

const NPC_FAREWELLS = Object.freeze([
  '先這樣囉，路上小心！',
  '下次再聊，掰掰～',
  '我要繼續走走了，回頭見！',
  '祝你今天順順利利！'
]);

export const NPC_LINE_BANNED_FRAGMENTS = Object.freeze([
  '本單',
  '小地圖截圖',
  '仙氣是形容詞',
  '按完肩頰',
  '地板燙腳',
  '探索模式走路會不會計步'
]);

export function npcTalkProfile(style, index) {
  const id = NPC_STYLE_IDS.includes(style) ? style : 'summer';
  const pool = NPC_NAMES[id] || NPC_NAMES.summer;
  const idx = Number.isFinite(index) ? Math.abs(Math.floor(index)) : 0;
  return { name: pool[idx % pool.length] };
}

function pickOne(pool, rng) {
  if (!pool || !pool.length) return '';
  const r = typeof rng === 'function' ? rng() : Math.random();
  return pool[Math.floor(r * pool.length) % pool.length];
}

function shufflePick(pool, count, rng, skipKey) {
  const list = pool.slice();
  const out = [];
  while (list.length && out.length < count) {
    const r = typeof rng === 'function' ? rng() : Math.random();
    const idx = Math.floor(r * list.length);
    const line = list.splice(idx, 1)[0];
    if (skipKey && line === skipKey) {
      if (!list.length) break;
      continue;
    }
    if (line) out.push(line);
  }
  return out;
}

/**
 * 一次 2～3 句內容＋結尾；openingKey 用來避免相鄰兩次同開場。
 */
export function pickNpcLines(style, islandId, lastOpening, rng) {
  const id = NPC_STYLE_IDS.includes(style) ? style : 'summer';
  const general = (NPC_GENERAL_LINES[id] || []).slice();
  const island = (NPC_ISLAND_LINES[islandId] || []).slice();
  const rOpen = typeof rng === 'function' ? rng() : Math.random();
  const openingFromIsland = island.length > 0 && rOpen < 0.6;
  const openPool = openingFromIsland ? island : general;
  let opening = pickOne(openPool, rng);
  if (lastOpening && opening === lastOpening) {
    const alt = openPool.filter((line) => line !== lastOpening);
    opening = pickOne(alt.length ? alt : openPool, rng);
  }
  const extraCount = 1 + (typeof rng === 'function' && rng() > 0.5 ? 1 : 0);
  const restPool = general.filter((line) => line !== opening);
  const rest = shufflePick(restPool, extraCount, rng);
  const body = [opening].concat(rest);
  if (!body.length) body.push('今天天氣真好呀。');
  const farewell = pickOne(NPC_FAREWELLS, rng);
  return { lines: body.concat([farewell]), openingKey: opening };
}

export const WORLD_LINE_RATIO = 0.35;
export const WORLD_NEWS_RATIO = 0.1;

const WORLD_WEATHER_LINES = Object.freeze({
  rain: ['今天沒帶傘，好煩喔～', '毛毛雨還好，大雨就糟了。', '路上有點滑，小心腳步喔！'],
  storm: ['颱風天還出來逛，你膽子很大耶！', '風好大，帽子快飛走了啦！', '希望待會雨小一點～'],
  fog: ['霧好濃，小心別走丟了。', '遠處看不太清楚耶。', '慢慢走，注意安全！'],
  cloudy: ['雲好多，太陽躲起來了。', '今天天色暗暗的，還挺涼。', '好像要下雨了？'],
  clear: ['天氣真好，心情也跟著亮！', '太陽出來了，真舒服～', '這種天最適合散步！']
});

const WORLD_TEMP_HOT = Object.freeze(['好熱～來杯冰的吧！飲料店就在那邊', '熱到想躲陰影裡～', '記得補水喔！']);
const WORLD_TEMP_COLD = Object.freeze(['好冷，想喝熱的。', '風有點涼，多穿一件吧。', '手冰冰的，去室內暖暖！']);

const WORLD_TIME_LINES = Object.freeze({
  morning: ['早啊！今天也要加油喔～', '早上空氣真新鮮！'],
  noon: ['中午了，吃飯了沒？', '太陽正烈，找個陰涼處吧！'],
  evening: ['傍晚風好舒服～', '夕陽快下山了耶！'],
  night: ['這麼晚還沒睡呀？', '夜色好美，別太晚回家喔。']
});

const WORLD_FESTIVAL_LINES = Object.freeze({
  'new-year': ['新年快樂！紅包拿了嗎？', '恭喜發財，今年順順的！'],
  lantern: ['元宵快到了，想吃湯圓嗎？', '花燈亮起來一定很漂亮！'],
  'dragon-boat': ['端午要到了，粽子香好好聞～', '划龍舟好熱鬧，你有看過嗎？'],
  'mid-autumn': ['今晚月亮好圓～', '中秋就是要烤肉加月餅呀！'],
  'winter-solstice': ['冬至要吃湯圓喔！', '今天冬至，記得吃點暖的～'],
  christmas: ['聖誕快樂！有沒有收到禮物？', '街上好有節慶感喔～']
});

const WORLD_STYLE_WEATHER = Object.freeze({
  tech: { rain: '下雨天最適合在研發樓寫 code～', storm: '颱風天還在測試？你好認真！' },
  tcm: { rain: '雨天濕氣重，推拿出來暖暖身吧。', clear: '晴天空曠，最適合活動筋骨～' }
});

function timeBucket(hour) {
  const h = Number(hour);
  if (!Number.isFinite(h)) return 'noon';
  if (h >= 5 && h < 11) return 'morning';
  if (h >= 11 && h < 17) return 'noon';
  if (h >= 17 && h < 22) return 'evening';
  return 'night';
}

/** 當天節氣直接講；剛過 5 天內的節氣改成「剛過」口吻，再久就不提。 */
function solarTermMention(calendar, rng) {
  const cal = calendar || {};
  if (cal.solarTerm) {
    return rng() < 0.35 ? '今天是' + cal.solarTerm + '耶～' : '';
  }
  if (!cal.recentSolarTerm || !cal.recentSolarTermDate || !cal.date) return '';
  const days = Math.round((Date.parse(cal.date) - Date.parse(cal.recentSolarTermDate)) / 86400000);
  if (!Number.isFinite(days) || days < 0 || days > 5) return '';
  return rng() < 0.3 ? cal.recentSolarTerm + '剛過，天氣要換季囉～' : '';
}

function pickNewsLine(title, rng) {
  const safe = String(title || '').replace(/[<>&]/g, '');
  if (!safe) return '';
  return '欸，你有看到「' + safe + '」嗎？';
}

/** 純函式：依世界狀態挑一句情境台詞，無則回傳空字串。 */
export function pickContextualWorldLine(style, islandId, world, rng) {
  if (!world || typeof rng !== 'function') return '';
  const w = world.weather || {};
  const kind = String(w.kind || 'clear');
  const temp = Number(w.tempC);
  const id = NPC_STYLE_IDS.includes(style) ? style : 'summer';

  if (typeof rng === 'function' && rng() < WORLD_NEWS_RATIO && world.news && world.news.length) {
    const idx = Math.floor(rng() * world.news.length);
    const title = world.news[idx] && world.news[idx].title;
    const line = pickNewsLine(title, rng);
    if (line) return line;
  }

  if (world.festival && WORLD_FESTIVAL_LINES[world.festival]) {
    if (rng() < 0.45) return pickOne(WORLD_FESTIVAL_LINES[world.festival], rng);
  }
  const termLine = solarTermMention(world.calendar, rng);
  if (termLine) return termLine;

  if (Number.isFinite(temp)) {
    if (temp > 30 && islandId === 'main' && rng() < 0.55) return pickOne(WORLD_TEMP_HOT, rng);
    if (temp < 15 && rng() < 0.45) return pickOne(WORLD_TEMP_COLD, rng);
  }

  const custom = WORLD_STYLE_WEATHER[id] && WORLD_STYLE_WEATHER[id][kind];
  if (custom && rng() < 0.4) return custom;

  const tb = timeBucket(world.hour);
  if (rng() < 0.3) return pickOne(WORLD_TIME_LINES[tb], rng);

  const pool = WORLD_WEATHER_LINES[kind] || WORLD_WEATHER_LINES.clear;
  return pickOne(pool, rng);
}

export function allWorldContextLinesForTests() {
  const out = [];
  for (const key of Object.keys(WORLD_WEATHER_LINES)) out.push(...WORLD_WEATHER_LINES[key]);
  out.push(...WORLD_TEMP_HOT, ...WORLD_TEMP_COLD);
  for (const key of Object.keys(WORLD_TIME_LINES)) out.push(...WORLD_TIME_LINES[key]);
  for (const key of Object.keys(WORLD_FESTIVAL_LINES)) out.push(...WORLD_FESTIVAL_LINES[key]);
  for (const style of Object.keys(WORLD_STYLE_WEATHER)) {
    const row = WORLD_STYLE_WEATHER[style];
    for (const key of Object.keys(row)) out.push(row[key]);
  }
  return out;
}

export function pickNpcLinesWithWorld(style, islandId, lastOpening, rng, world) {
  const base = pickNpcLines(style, islandId, lastOpening, rng);
  if (!world || typeof rng !== 'function' || rng() > WORLD_LINE_RATIO) {
    return base;
  }
  const line = pickContextualWorldLine(style, islandId, world, rng);
  if (!line) return base;
  const lines = base.lines.slice();
  const insertAt = Math.min(Math.max(1, 1 + Math.floor(rng() * 2)), lines.length - 1);
  lines[insertAt] = line;
  return { lines: lines, openingKey: base.openingKey };
}

export function npcTalkLinePools(style, islandId) {
  const id = NPC_STYLE_IDS.includes(style) ? style : 'summer';
  return {
    general: (NPC_GENERAL_LINES[id] || []).slice(),
    island: (NPC_ISLAND_LINES[islandId] || []).slice()
  };
}

export function allNpcScriptLinesForTests() {
  const out = [];
  for (const style of NPC_STYLE_IDS) {
    const names = NPC_NAMES[style] || [];
    out.push(...names);
    out.push(...(NPC_GENERAL_LINES[style] || []));
  }
  for (const key of Object.keys(NPC_ISLAND_LINES)) {
    out.push(...(NPC_ISLAND_LINES[key] || []));
  }
  out.push(...NPC_FAREWELLS);
  return out;
}

export function nearestTalkableNpc(px, pz, npcs, radius) {
  const r = radius != null ? radius : MILU_ANIM.waveNpcRadius;
  let best = null;
  let bestD = r * r;
  if (!npcs || !npcs.length) return null;
  for (let i = 0; i < npcs.length; i++) {
    const npc = npcs[i];
    if (!npc || npc.talkable === false || npc.scooter) continue;
    const dx = px - npc.x;
    const dz = pz - npc.z;
    const d2 = dx * dx + dz * dz;
    if (d2 < bestD || (d2 === bestD && best === null)) {
      best = npc;
      bestD = d2;
    }
  }
  return best;
}

export function npcTalkShouldClose(px, pz, nx, nz) {
  return Math.hypot(px - nx, pz - nz) > NPC_TALK_CLOSE_DIST;
}

/** 戶外互動區命中時不顯示「聊天」。 */
export function npcTalkBlockedByZone(zone) {
  return !!(zone && (zone.id || zone.action));
}

/** 面板／載具等打開時不顯示「聊天」。 */
export function npcTalkPromptBlocked(ctx) {
  if (!ctx || !ctx.exploring || ctx.paused || ctx.indoor) return true;
  if (ctx.driving || ctx.sailing || ctx.steering) return true;
  if (ctx.uiOpen || ctx.lobbyOpen || ctx.chatOpen || ctx.talkOpen) return true;
  if (npcTalkBlockedByZone(ctx.zone)) return true;
  return false;
}

export function shouldOfferNpcTalkPrompt(ctx) {
  if (npcTalkPromptBlocked(ctx)) return null;
  const radius = ctx.radius != null ? ctx.radius : MILU_ANIM.waveNpcRadius;
  return nearestTalkableNpc(ctx.px, ctx.pz, ctx.npcs, radius);
}