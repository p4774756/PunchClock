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

/** 對話自動關閉距離（主角離路人） */
export const NPC_TALK_CLOSE_DIST = 3;

export const NPC_TALK_ACTION = 'npcTalk';

const NPC_NAMES = Object.freeze({
  summer: Object.freeze(['夏蘿', '柚子', '小葵', '晴夏']),
  student: Object.freeze(['書言', '知予', '靜夏', '予安的筆記']),
  tech: Object.freeze(['程夏', 'debug 妹', '小艾', 'Commit 娘']),
  idol: Object.freeze(['星奈', '小響', '玲音', '練歌的米露']),
  swimsuit: Object.freeze(['海音', '浪花', '汐見', '沙灘步調']),
  sport: Object.freeze(['小凛', '晨跑姐', '帕卡', '熱血瑩']),
  fairy: Object.freeze(['綺夢', '紗羽', '若汐', '靈犀'])
});

const NPC_GENERAL_LINES = Object.freeze({
  summer: Object.freeze([
    '今天陽光好舒服，散步最開心了！',
    '你也在找好喝的嗎？這附近選擇超多。',
    '風吹過來的時候，髮夾差點飛走哈哈。',
    '神之島的路很好走，適合慢慢逛。',
    '路過飲料店記得看今日限定喔。',
    '有看到小地圖嗎？迷路時很好用。'
  ]),
  student: Object.freeze([
    '咦，這個角度可以看到整條棧橋耶。',
    '我在用功…其實是在看雲的形狀啦。',
    '請問你知道研習室在哪一區嗎？',
    '筆記本寫滿了，準備去買新的。',
    '探索模式走路會不會計步啊？好奇。',
    '剛剛看見郵輪了，好想去船上看風景。'
  ]),
  tech: Object.freeze([
    '今天 build 都綠的話就很棒。',
    '我剛把一個 bug 關掉，心情超好。',
    '打卡別忘了，不過今天風景也滿療癒的。',
    '耳機裡是 Lo-fi，配散步剛剛好。',
    '研發島那邊的燈，晚上特別像 IDE。',
    '這裡 Wi-Fi 滿穩的，我在等 deploy。'
  ]),
  idol: Object.freeze([
    '嘿～要一起打個招呼嗎！',
    '練歌的時候看到路人會更開心耶。',
    '今天的步數可以當暖嗓運動吧？',
    '賭場那邊霓虹好酷，但不會亂跑進去。',
    '我把微笑練習藏在呼吸裡，秘密。',
    '你路過的時候，風都變得輕快了。'
  ]),
  swimsuit: Object.freeze([
    '海風鹹鹹的，超有度假感。',
    '記得防曬，太陽很會照人的喔。',
    '浮潛要選風小一點再出發比較穩。',
    '沙灘邊走步履會變慢，正常啦。',
    '船要開了嗎？我聽到碼頭廣播了。',
    '這裡水好藍，拍小地圖截圖都好看。'
  ]),
  sport: Object.freeze([
    '晨跑完精神超好，你也好早安！',
    '步伐別太快，地板燙腳就休息一下。',
    '我有做暖身，你要不要也甩甩手？',
    '一圈走完打卡，今天運動達成。',
    '樓梯別用跑的，膝蓋會抗議的。',
    '剛剛衝過飲料店，忍住了沒買全糖。'
  ]),
  fairy: Object.freeze([
    '氣流柔柔的，像經絡在慢慢走。',
    '你若靜下來，會聽見葉子翻身。',
    '穴位不是靠猜，是靠手感與耐心。',
    '仙氣是形容詞，做事還是要踏實。',
    '推拿館的銅人很帥，別害羞去看。',
    '走累了就深呼吸，三次就會輕一點。'
  ])
});

const NPC_ISLAND_LINES = Object.freeze({
  main: Object.freeze([
    '飲料店草莓季快結束了，要搶要快。',
    '手搖五分糖去冰，我心中的正解。',
    '沙灘傘下面居然有插座，太貼心。',
    '棧橋盡頭風大，帽子要壓好。',
    '神之島廣場常常有新人拍照。'
  ]),
  tcm: Object.freeze([
    '推拿館今天茶香很放鬆，進去看看？',
    '銅人身上的點，比地圖還精準。',
    '經絡研習室要脫鞋，別踩線。',
    '按完肩頰會熱熱的，是正常反應。',
    '研究室的門口有穴位海報，很實用。'
  ]),
  computer: Object.freeze([
    '研發樓冷氣很猛，外套要記得搭。',
    '寫程式的時候記得站起來走走。',
    '打卡鐘在門口，遲到會被自己看到。',
    '加班前我先在島上呼吸幾口。',
    '測試全綠再說八卦，規矩喔。'
  ]),
  casino: Object.freeze([
    '賭場霓虹亮起來，像晚上的慶典。',
    '21 點要穩住情緒，本單純看熱鬧。',
    '大廳音樂很有節奏，但我不亂下注。',
    '紅黑籌碼閃啊閃，逛逛就好。',
    '別在旋轉門前自拍，會卡住。'
  ]),
  beach: Object.freeze([
    '海面一望無際，心胸會變大。',
    '防曬要補，沙灘反射很狡猾。',
    '浮潛記得結伴，安全最重要。',
    '貝殼很美但別全撿走，給下一個人。',
    '浪花聲當白噪音，超助眠。'
  ]),
  city: Object.freeze([
    '7-11 的咖啡香會從街口飄出來。',
    '粉紅小車停在轉角，很搶鏡頭。',
    '港灣風把機車聲吹散一點，舒服。',
    '街區步行的人很多，慢慢走。',
    '公車站牌旁的花剛換季，好香。'
  ])
});

const NPC_FAREWELLS = Object.freeze([
  '先這樣啦，路上小心喔！',
  '下次再聊，拜拜～',
  '我去趕路啦，再見！',
  '祝你有好的一天，回頭見！'
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
  const general = (NPC_GENERAL_LINES[id] || []).concat(NPC_ISLAND_LINES[islandId] || []);
  const bodyCount = 2 + (typeof rng === 'function' && rng() > 0.5 ? 1 : 0);
  const body = shufflePick(general, bodyCount, rng, lastOpening);
  if (!body.length) body.push('今天天氣真好呀。');
  const openingKey = body[0];
  const farewell = pickOne(NPC_FAREWELLS, rng);
  return { lines: body.concat([farewell]), openingKey: openingKey };
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