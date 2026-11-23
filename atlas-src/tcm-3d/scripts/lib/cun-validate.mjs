import assert from 'node:assert/strict';
import {readFile, writeFile, mkdir, access} from 'node:fs/promises';
import {constants} from 'node:fs';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';

const scriptDir = dirname(fileURLToPath(import.meta.url));
const project = resolve(scriptDir, '../..');

const REQUIRED_LANDMARKS = [
  'navel', 'suprasternal_notch', 'left_nipple', 'right_nipple',
  'left_clavicle_mid', 'right_clavicle_mid', 'xiphoid', 'pubis_rim',
  'left_elbow_crease', 'right_elbow_crease', 'left_wrist_crease', 'right_wrist_crease',
  'left_knee', 'right_knee', 'left_medial_malleolus', 'right_medial_malleolus',
  'left_lateral_malleolus', 'right_lateral_malleolus',
  'anterior_hairline', 'posterior_hairline', 'vertex', 'yintang',
  'laryngeal_prominence', 'larynx_suprahyoid', 'mentolabial_groove', 'philtrum_upper_third',
  'pupil_left', 'infraorbital_rim', 'infraorbital_foramen', 'nose_tip', 'nasal_ala', 'mouth_corner',
  'left_masseter_prominence', 'left_supraclavicular_fossa', 'left_infraclavicular', 'sternum_nipple_mid',
];

/** 前髮際至百會 5 寸，百會至後髮際 7 寸。 */
const SCALP_FRONT_CUN = 5;

const SPINE_SUPERIOR_TO_INFERIOR = [
  'GV14', 'GV13', 'GV12', 'GV11', 'GV10', 'GV9', 'GV8', 'GV7', 'GV6', 'GV5', 'GV4', 'GV3',
];

const CHEST_SUPERIOR_TO_INFERIOR = ['CV22', 'CV21', 'CV20', 'CV19', 'CV18'];

const SPAN_LABEL = {
  headFront: '前髮際至印堂',
  headArc: '前髮際至後髮際',
  chest: '天突至劍突',
  upperAbdomen: '劍突至臍中',
  lowerAbdomen: '臍中至恥骨',
  lateral: '兩乳之間',
  forearm: '肘橫紋至腕橫紋',
  leg: '膝中至外踝尖',
};

const PHASE_EXCLUDED = ['GB26'];

function fmtPos(p) {
  return `[${p.map(v => Number(v).toFixed(4)).join(', ')}]`;
}

function quantize(value, digits, mode) {
  const f = 10 ** digits;
  const scaled = value * f;
  return (mode === 'round' ? Math.round(scaled) : Math.trunc(scaled)) / f;
}

/** 任一分量在 1–4 位小數被四捨五入或截斷，即視為同一穴座標的複本。各分量精度可以不同。 */
function isRoundedCopy(landmark, actual) {
  if (!Array.isArray(landmark) || landmark.length !== actual.length) return false;
  return landmark.every((value, i) => {
    if (Math.abs(value - actual[i]) < 1e-9) return true;
    return [1, 2, 3, 4].some(digits => (
      Math.abs(value - quantize(actual[i], digits, 'round')) < 1e-9
      || Math.abs(value - quantize(actual[i], digits, 'trunc')) < 1e-9
    ));
  });
}

function resolveLandmarks(raw, byCode) {
  const out = {};
  const pending = new Set(Object.keys(raw));
  for (let guard = 0; pending.size && guard < 8; guard += 1) {
    for (const key of [...pending]) {
      const entry = raw[key];
      if (Array.isArray(entry)) {
        out[key] = entry.slice();
        pending.delete(key);
      } else if (entry?.sourcePoint) {
        const point = byCode.get(entry.sourcePoint);
        assert.ok(point, `地標 ${key} 缺少來源穴 ${entry.sourcePoint}`);
        out[key] = point.position.slice();
        pending.delete(key);
      } else if (entry?.mirror) {
        if (!out[entry.mirror]) continue;
        const src = out[entry.mirror];
        out[key] = [-src[0], src[1], src[2]];
        pending.delete(key);
      } else {
        throw new Error(`地標 ${key} 無法解析`);
      }
    }
  }
  assert.equal(pending.size, 0, `地標無法解析：${[...pending].join(', ')}`);
  if (!out.midline) out.midline = [0, 0, 0];
  return out;
}

function distanceOf(span, landmarks) {
  const a = landmarks[span.from];
  const b = landmarks[span.to];
  assert.ok(a && b, `骨度跨度缺少地標 ${span.from} 或 ${span.to}`);
  if (span.measure === 'vertical') return Math.abs(a[1] - b[1]);
  if (span.measure === 'lateral') return Math.abs(a[0] - b[0]);
  return Math.hypot(a[0] - b[0], a[1] - b[1], a[2] - b[2]);
}

/**
 * 各骨度跨度各自保留寸長。軀幹直寸分胸、上腹、下腹，後寫入的一段不會蓋掉前一段。
 * @param {object[]} rules
 */
function cunScalars(landmarks, spans, boneSpans, rules) {
  const scalars = {};
  for (const span of spans) {
    if (span.measure === 'hairArc') continue;
    const cun = boneSpans[span.boneSpan];
    assert.equal(typeof cun, 'number', `boneSpans 缺少 ${span.boneSpan}`);
    const dist = distanceOf(span, landmarks);
    assert.ok(dist > 1e-6, `骨度跨度為零：${span.id}`);
    scalars[span.id] = dist / cun;
  }
  for (const span of spans) {
    if (span.measure !== 'hairArc') continue;
    const cun = boneSpans[span.boneSpan];
    const front = landmarks[span.from];
    const back = landmarks[span.to];
    const crown = landmarks.vertex;
    assert.ok(crown, '頭弧缺少頭頂地標 vertex');
    const baihui = rules.find(rule => rule.code === 'GV20');
    assert.equal(baihui?.scalpFrom, 'anterior_hairline');
    assert.equal(baihui?.scalpCun, SCALP_FRONT_CUN);
    const arc = Math.hypot(crown[0] - front[0], crown[1] - front[1], crown[2] - front[2])
      + Math.hypot(back[0] - crown[0], back[1] - crown[1], back[2] - crown[2]);
    assert.ok(arc > 1e-6, '前髮際至後髮際弧長為零');
    scalars.headArc = arc / cun;
  }
  for (const key of Object.keys(boneSpans)) {
    assert.ok(spans.some(span => span.boneSpan === key), `骨度 ${key} 沒有參與計算`);
  }
  assert.ok(scalars.upperAbdomen > 0 && scalars.lowerAbdomen > 0, '上下腹寸長需分別保留');
  assert.ok(scalars.chest > 0 && scalars.headFront > 0 && scalars.headArc > 0, '胸寸與頭寸需參與計算');
  assert.ok(scalars.forearm > 0 && scalars.leg > 0 && scalars.lateral > 0, '肢體與橫寸需參與計算');
  return scalars;
}

function lerp(a, b, t) {
  return [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t];
}

function expectedFromRule(rule, landmarks, scalars, boneSpans, expectedByCode) {
  if (rule.midpointOfRules) {
    const a = expectedByCode.get(rule.midpointOfRules[0]);
    const b = expectedByCode.get(rule.midpointOfRules[1]);
    assert.ok(a && b, `${rule.code} 中點缺少已算規則`);
    return [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2];
  }
  if (rule.sameLevelAs) {
    const base = expectedByCode.get(rule.sameLevelAs);
    assert.ok(base, `${rule.code} 缺少同高規則 ${rule.sameLevelAs}`);
    const z = landmarks[rule.zFrom]?.[2];
    assert.equal(typeof z, 'number');
    return [rule.lateralAbsCun * scalars.lateral, base[1], z];
  }
  if (rule.at) {
    const lm = landmarks[rule.at];
    assert.ok(lm, `地標 ${rule.at}`);
    return lm.slice();
  }
  if (rule.midpointOf) {
    const a = landmarks[rule.midpointOf[0]];
    const b = landmarks[rule.midpointOf[1]];
    assert.ok(a && b, rule.code);
    return [(a[0] + b[0]) / 2, (a[1] + b[1]) / 2, (a[2] + b[2]) / 2];
  }
  if (rule.midpointYOf) {
    const a = landmarks[rule.midpointYOf[0]];
    const b = landmarks[rule.midpointYOf[1]];
    assert.ok(a && b, rule.code);
    const y = (a[1] + b[1]) / 2;
    const zLm = rule.zFrom ? landmarks[rule.zFrom] : null;
    assert.ok(zLm, `${rule.code} 缺少 zFrom 地標`);
    return [0, y, zLm[2]];
  }
  if (rule.scalpFrom) {
    const front = landmarks.anterior_hairline;
    const crown = landmarks.vertex;
    const back = landmarks.posterior_hairline;
    assert.ok(front && crown && back, `${rule.code} 缺少頭弧地標`);
    const total = boneSpans.hairline_front_to_back;
    const backCun = total - SCALP_FRONT_CUN;
    const cunFromFront = rule.scalpFrom === 'anterior_hairline'
      ? rule.scalpCun
      : total - rule.scalpCun;
    if (cunFromFront <= SCALP_FRONT_CUN) {
      return lerp(front, crown, cunFromFront / SCALP_FRONT_CUN);
    }
    return lerp(crown, back, (cunFromFront - SCALP_FRONT_CUN) / backCun);
  }
  if (rule.spinous) {
    const a = landmarks[rule.spinous.from];
    const b = landmarks[rule.spinous.to];
    assert.ok(a && b, rule.code);
    return lerp(a, b, rule.spinous.numer / rule.spinous.denom);
  }
  if (rule.atLevel) {
    const x = landmarks[rule.atLevel.xFrom];
    const y = landmarks[rule.atLevel.yFrom];
    const z = landmarks[rule.atLevel.zFrom];
    assert.ok(x && y && z, rule.code);
    return [x[0], y[1], z[2]];
  }

  const ref = landmarks[rule.ref];
  assert.ok(ref, `規則 ${rule.code} 缺少地標 ${rule.ref}`);
  const span = rule.span || 'upperAbdomen';
  const unit = scalars[span];
  assert.ok(unit > 0, `${rule.code} 寸長 ${span}`);
  const pos = ref.slice();
  if (rule.verticalCun != null) pos[1] += rule.verticalCun * unit;
  if (rule.lateralAbsCun != null) pos[0] = rule.lateralAbsCun * scalars.lateral;
  else if (rule.lateralCun != null) pos[0] += rule.lateralCun * scalars.lateral;
  if (rule.chestSurface) {
    const t = Math.min(1, Math.abs(rule.verticalCun ?? 0) / boneSpans.suprasternal_to_xiphoid);
    pos[2] = ref[2] + t * (landmarks.xiphoid[2] - ref[2]);
  }
  if (rule.along) {
    const to = landmarks[rule.along.to];
    assert.ok(to, rule.code);
    return lerp(ref, to, rule.along.fraction);
  }
  if (rule.midline) pos[0] = 0;
  return pos;
}

function computeExpected(rules, landmarks, scalars, boneSpans) {
  const expectedByCode = new Map();
  const pending = rules.slice();
  for (let guard = 0; pending.length && guard < 4; guard += 1) {
    for (let i = pending.length - 1; i >= 0; i -= 1) {
      const rule = pending[i];
      const needs = rule.midpointOfRules || (rule.sameLevelAs ? [rule.sameLevelAs] : null);
      if (needs && needs.some(code => !expectedByCode.has(code))) continue;
      expectedByCode.set(rule.code, expectedFromRule(rule, landmarks, scalars, boneSpans, expectedByCode));
      pending.splice(i, 1);
    }
  }
  assert.equal(pending.length, 0, `規則無法計算：${pending.map(rule => rule.code).join(', ')}`);
  return expectedByCode;
}

function deviationCun(actual, expected, scalars, rule) {
  const span = rule.span || (rule.scalpFrom || rule.atLevel ? 'headFront' : 'upperAbdomen');
  const vert = scalars[span] || scalars.upperAbdomen;
  const lat = scalars.lateral;
  const headLike = span === 'headFront' || span === 'headArc' || rule.scalpFrom || rule.atLevel;
  const ap = headLike ? scalars.headFront : lat;
  const dx = rule.midline ? 0 : Math.abs(actual[0] - expected[0]) / lat;
  const dy = Math.abs(actual[1] - expected[1]) / vert;
  const dz = Math.abs(actual[2] - expected[2]) / ap;
  return Math.hypot(dx, dy, dz);
}

function ruleSummary(rule) {
  if (rule.summary) return rule.summary;
  return rule.code;
}

function assertGeometry(spaceKey, landmarks, scalars, expected, boneSpans) {
  for (let i = 1; i < SPINE_SUPERIOR_TO_INFERIOR.length; i += 1) {
    const above = SPINE_SUPERIOR_TO_INFERIOR[i - 1];
    const below = SPINE_SUPERIOR_TO_INFERIOR[i];
    assert.ok(
      expected.get(above)[1] > expected.get(below)[1],
      `${spaceKey} ${above} 應高於 ${below}`,
    );
  }
  assert.ok(
    Math.abs(expected.get('GV8')[1] - expected.get('GV7')[1]) > 1e-4,
    `${spaceKey} 筋縮與中樞應在位置不可相同`,
  );
  for (let i = 1; i < CHEST_SUPERIOR_TO_INFERIOR.length; i += 1) {
    const above = CHEST_SUPERIOR_TO_INFERIOR[i - 1];
    const below = CHEST_SUPERIOR_TO_INFERIOR[i];
    assert.ok(expected.get(above)[1] > expected.get(below)[1], `${spaceKey} ${above} 應高於 ${below}`);
  }
  assert.ok(expected.get('CV21')[1] > expected.get('CV17')[1], `${spaceKey} 璇璣應高於膻中`);
  assert.ok(expected.get('CV20')[1] > expected.get('CV17')[1], `${spaceKey} 華蓋應高於膻中`);
  const xiphoid = landmarks.xiphoid;
  const cv16 = expected.get('CV16');
  assert.ok(cv16.every((v, i) => Math.abs(v - xiphoid[i]) < 1e-6), `${spaceKey} 中庭應在胸劍結合`);
  const gv20 = expected.get('GV20');
  const hair = landmarks.anterior_hairline;
  const crown = landmarks.vertex;
  assert.ok(
    Math.abs(gv20[1] - crown[1]) < 1e-6 && Math.abs(gv20[2] - crown[2]) < 1e-6,
    `${spaceKey} 百會應在前髮際沿頭弧 5 寸的頭頂`,
  );
  assert.ok(gv20[1] <= crown[1] + 1e-6, `${spaceKey} 百會應在位置不可高過頭頂`);
  assert.ok(expected.get('CV24')[1] > expected.get('CV23')[1], `${spaceKey} 承漿應高於廉泉`);
  assert.ok(expected.get('GV25')[1] > expected.get('GV26')[1], `${spaceKey} 素髎應高於水溝`);
  assert.ok(landmarks.nose_tip[1] > landmarks.philtrum_upper_third[1], `${spaceKey} 鼻尖應高於人中溝`);
  assert.ok(Math.abs(expected.get('ST1')[0] - landmarks.pupil_left[0]) < 1e-6, `${spaceKey} 承泣應在瞳孔直下`);
  assert.ok(Math.abs(expected.get('ST2')[0] - landmarks.pupil_left[0]) < 1e-6, `${spaceKey} 四白應在瞳孔直下`);
  assert.ok(expected.get('CV17')[1] > expected.get('CV16')[1], `${spaceKey} 膻中應高於中庭`);
  const nipMidY = (landmarks.left_nipple[1] + landmarks.right_nipple[1]) / 2;
  assert.ok(
    Math.abs(landmarks.sternum_nipple_mid[1] - nipMidY) < 1e-3,
    `${spaceKey} 膻中胸骨地標 y 應與兩乳頭連線等高`,
  );
  assert.ok(
    Math.abs(expected.get('ST12')[0] - expected.get('ST13')[0]) < 1e-5
      && Math.hypot(
        expected.get('ST12')[1] - expected.get('ST13')[1],
        expected.get('ST12')[2] - expected.get('ST13')[2],
      ) > 1e-4,
    `${spaceKey} 缺盆與氣戶應同乳中線但不可重合`,
  );
  assert.ok(expected.get('ST17')[1] > expected.get('ST18')[1], `${spaceKey} 乳中應高於乳根`);
  assert.ok(landmarks.xiphoid[1] < landmarks.left_nipple[1], `${spaceKey} 劍突應低於乳頭`);
  assert.ok(landmarks.larynx_suprahyoid[1] < landmarks.mentolabial_groove[1], `${spaceKey} 舌骨上緣應低於頦唇溝`);
  const verticalShortcut = Math.abs(hair[1] - landmarks.posterior_hairline[1]) / boneSpans.hairline_front_to_back;
  assert.ok(scalars.headArc > verticalShortcut, `${spaceKey} 前髮際至後髮際不可只取垂直差`);
  const lateral = scalars.lateral;
  assert.ok(Math.abs(expected.get('ST15')[0] - 4 * lateral) < 1e-6, `${spaceKey} 屋翳應旁開 4 寸`);
  assert.ok(Math.abs(expected.get('ST16')[0] - 4 * lateral) < 1e-6, `${spaceKey} 膺窗應旁開 4 寸`);
  assert.ok(Math.abs(expected.get('ST9')[0] - 1.5 * lateral) < 1e-6, `${spaceKey} 人迎應旁開 1.5 寸`);
  const navel = landmarks.navel;
  assert.ok(
    Math.abs(expected.get('ST19')[1] - (navel[1] + 6 * scalars.upperAbdomen)) < 1e-6,
    `${spaceKey} 不容應為臍上 6 寸`,
  );
  if (spaceKey === 'anime') {
    assert.ok(expected.get('GV24+')[2] < -0.05, '印堂應留在額前，不可落到頸後');
  } else {
    assert.ok(expected.get('GV24+')[2] > 0.05, '目錄印堂應在額前');
  }
}

function spanReport(spaceLabel, scalars, boneSpans, spans) {
  const parts = spans.map(span => {
    const label = SPAN_LABEL[span.id] || span.id;
    return `${label} ${boneSpans[span.boneSpan]} 寸→${scalars[span.id].toFixed(4)}`;
  });
  return `${spaceLabel}寸長（世界座標／寸）：${parts.join('；')}`;
}

async function modelExists() {
  try {
    await access(join(project, 'public/models/anime-assistant.glb'), constants.R_OK);
    return true;
  } catch {
    return false;
  }
}

const SKIN_MAT = /Face_00_SKIN|Body_00_SKIN|FaceMouth/i;

async function loadAnimeSkinMeshes() {
  const {readFileSync} = await import('node:fs');
  const T = await import('three');
  const {GLTFLoader} = await import('three/examples/jsm/loaders/GLTFLoader.js');
  globalThis.self = globalThis;
  globalThis.URL = URL;
  globalThis.webkitURL = URL;
  if (!globalThis.document) {
    globalThis.document = {createElementNS: () => ({style: {}, setAttribute() {}, getContext() { return null; }})};
  }
  const buffer = readFileSync(join(project, 'public/models/anime-assistant.glb')).buffer;
  const gltf = await new Promise((res, rej) => new GLTFLoader().parse(buffer, '', res, rej));
  gltf.scene.updateMatrixWorld(true);
  const skins = [];
  gltf.scene.traverse(o => {
    if (!o.isMesh) return;
    const mats = Array.isArray(o.material) ? o.material : [o.material];
    if (mats.some(m => SKIN_MAT.test(m?.name || ''))) skins.push(o);
  });
  assert.ok(skins.length > 0, '缺少皮膚網格');
  const tri = new T.Triangle();
  const closest = new T.Vector3();
  const a = new T.Vector3();
  const b = new T.Vector3();
  const c = new T.Vector3();
  const distToSkin = (point) => {
    let best = Infinity;
    const probe = new T.Vector3(point[0], point[1], point[2]);
    for (const mesh of skins) {
      const pos = mesh.geometry.getAttribute('position');
      const index = mesh.geometry.index;
      if (!pos || !index) continue;
      for (let i = 0; i < index.count; i += 3) {
        a.fromBufferAttribute(pos, index.getX(i)).applyMatrix4(mesh.matrixWorld);
        b.fromBufferAttribute(pos, index.getX(i + 1)).applyMatrix4(mesh.matrixWorld);
        c.fromBufferAttribute(pos, index.getX(i + 2)).applyMatrix4(mesh.matrixWorld);
        tri.set(a, b, c);
        tri.closestPointToPoint(probe, closest);
        best = Math.min(best, probe.distanceTo(closest));
      }
    }
    return best;
  };
  return {distToSkin};
}

async function animeModelBounds() {
  const {readFileSync} = await import('node:fs');
  const T = await import('three');
  const {GLTFLoader} = await import('three/examples/jsm/loaders/GLTFLoader.js');
  globalThis.self = globalThis;
  globalThis.URL = URL;
  globalThis.webkitURL = URL;
  if (!globalThis.document) {
    globalThis.document = {createElementNS: () => ({style: {}, setAttribute() {}, getContext() { return null; }})};
  }
  const buffer = readFileSync(join(project, 'public/models/anime-assistant.glb')).buffer;
  const gltf = await new Promise((res, rej) => new GLTFLoader().parse(buffer, '', res, rej));
  gltf.scene.updateMatrixWorld(true);
  const box = new T.Box3().setFromObject(gltf.scene);
  const upper = gltf.scene.getObjectByName('J_Bip_C_UpperChest');
  assert.ok(upper, '缺少上胸骨骨骼');
  const upperPos = new T.Vector3();
  upper.getWorldPosition(upperPos);
  return {maxY: box.max.y, upperChestY: upperPos.y};
}

/**
 * @param {{writeReport?: boolean, thresholdCun?: number}} [opts]
 */
export async function runCunValidation(opts = {}) {
  const config = JSON.parse(await readFile(join(project, 'data/cun-validation.json'), 'utf8'));
  const threshold = opts.thresholdCun ?? config.thresholdCun ?? 0.3;
  assert.equal(config.worsenSlackCun ?? 0, 0, '清單內偏差變大即失敗，不得留放寬');
  assert.equal(isRoundedCopy([0, 1.02, -0.1], [0, 1.02, -0.10554]), true);
  assert.equal(isRoundedCopy([0.055, 1.195, -0.12], [0.055, 1.195, -0.12613]), true);
  const animeJson = JSON.parse(await readFile(join(project, 'app/anime-points.json'), 'utf8'));
  const catalog = JSON.parse(await readFile(join(project, 'app/teaching-points.json'), 'utf8'));
  const catalogBy = new Map(catalog.map(p => [p.code, p]));
  const animeBy = new Map((animeJson.points || []).map(p => [p.code, p]));
  const hasModel = await modelExists();

  assert.equal(config.rules.length, 80, '第一期應為 80 條規則');
  for (const code of PHASE_EXCLUDED) {
    assert.ok(!config.rules.some(rule => rule.code === code), `${code} 不在第一期門檻`);
  }
  assert.equal(config.rules.find(rule => rule.code === 'GV20')?.scalpFrom, 'anterior_hairline');
  assert.equal(config.rules.find(rule => rule.code === 'GV20')?.scalpCun, 5);
  assert.equal(config.rules.find(rule => rule.code === 'GV24+')?.posterior, undefined);
  assert.equal(config.spaces.catalog.landmarks.navel?.sourcePoint, 'CV8');
  assert.equal(config.spaces.catalog.landmarks.suprasternal_notch?.sourcePoint, 'CV22');
  assert.equal(config.spaces.catalog.landmarks.left_nipple?.sourcePoint, 'ST17');
  assert.equal(config.spaces.anime.landmarks.navel?.sourcePoint, 'CV8');
  assert.equal(config.spaces.anime.landmarks.left_nipple?.sourcePoint, 'ST17');
  assert.equal(config.rules.find(rule => rule.code === 'GV25')?.at, 'nose_tip');
  assert.equal(config.rules.find(rule => rule.code === 'ST1')?.atLevel?.xFrom, 'pupil_left');
  assert.equal(config.rules.find(rule => rule.code === 'ST2')?.atLevel?.xFrom, 'pupil_left');
  assert.equal(config.rules.find(rule => rule.code === 'ST6')?.at, 'left_masseter_prominence');
  assert.equal(config.rules.find(rule => rule.code === 'CV17')?.midpointYOf?.[0], 'left_nipple');
  assert.equal(config.rules.find(rule => rule.code === 'CV17')?.zFrom, 'sternum_nipple_mid');
  assert.equal(config.rules.find(rule => rule.code === 'ST12')?.at, 'left_supraclavicular_fossa');
  assert.equal(config.rules.find(rule => rule.code === 'ST13')?.at, 'left_infraclavicular');
  assert.equal(config.spaces.anime.landmarks.baihui, undefined);
  assert.equal(config.spaces.catalog.landmarks.baihui, undefined);

  const spaces = {};
  for (const spaceKey of ['anime', 'catalog']) {
    const space = config.spaces[spaceKey];
    for (const name of REQUIRED_LANDMARKS) {
      assert.ok(space.landmarks[name], `${spaceKey} 缺少地標 ${name}`);
    }
    const byCode = spaceKey === 'catalog' ? catalogBy : animeBy;
    const landmarks = resolveLandmarks(space.landmarks, byCode);
    const scalars = cunScalars(landmarks, space.spans, config.boneSpans, config.rules);
    const expected = computeExpected(config.rules, landmarks, scalars, config.boneSpans);
    assertGeometry(spaceKey, landmarks, scalars, expected, config.boneSpans);
    spaces[spaceKey] = {landmarks, scalars, expected, spans: space.spans};
  }

  let landmarkSkinRows = [];
  if (hasModel) {
    const bounds = await animeModelBounds();
    const gv20 = spaces.anime.expected.get('GV20');
    assert.ok(
      gv20[1] <= bounds.maxY + 1e-4,
      `百會應在位置 y=${gv20[1].toFixed(4)} 不高出模型頭頂 ${bounds.maxY.toFixed(4)}`,
    );
    assert.ok(
      spaces.anime.landmarks.xiphoid[1] < bounds.upperChestY,
      `劍突應低於上胸骨（${spaces.anime.landmarks.xiphoid[1].toFixed(3)} < ${bounds.upperChestY.toFixed(3)}）`,
    );
    const {distToSkin} = await loadAnimeSkinMeshes();
    const rawAnime = config.spaces.anime.landmarks;
    const skinCheckNames = [
      'anterior_hairline', 'posterior_hairline', 'philtrum_upper_third', 'c7_spinous', 't9_spinous', 'l2_spinous',
      'pubis_rim', 'sternum_nipple_mid', 'left_masseter_prominence', 'left_supraclavicular_fossa', 'left_infraclavicular',
    ];
    for (const name of skinCheckNames) {
      const lm = spaces.anime.landmarks[name];
      assert.ok(lm, `少女地標 ${name} 缺失`);
      const mm = distToSkin(lm) * 1000;
      landmarkSkinRows.push({name, mm});
      assert.ok(mm < 5.01, `少女地標 ${name} 離皮膚 ${mm.toFixed(2)} mm（需 < 5 mm）`);
    }
  }

  const rows = [];
  const failures = [];

  for (const rule of config.rules) {
    const code = rule.code;
    const name = catalogBy.get(code)?.name ?? animeBy.get(code)?.name ?? code;
    const onAnime = animeBy.has(code);
    const source = onAnime ? '少女' : '目錄';
    if (onAnime && !hasModel) {
      failures.push(`${code} 已烘上身但缺少 anime-assistant.glb，無法比對少女座標`);
      continue;
    }
    const point = (onAnime ? animeBy : catalogBy).get(code);
    if (!point) {
      failures.push(`${code} 不在比對來源`);
      continue;
    }
    const spaceKey = onAnime ? 'anime' : 'catalog';
    const space = spaces[spaceKey];
    const rawAt = rule.at ? config.spaces[spaceKey].landmarks[rule.at] : null;
    let actual = point.position.slice();
    if (onAnime && !point.midline) actual = [Math.abs(actual[0]), actual[1], actual[2]];
    if (rawAt && !Array.isArray(rawAt) && rawAt.sourcePoint === code) {
      rows.push({
        code,
        name,
        source,
        rule: ruleSummary(rule),
        expected: actual.slice(),
        actual,
        deviationCun: 0,
        status: '地標來源',
        note: '本身即地標，不與自身座標比對',
      });
      continue;
    }

    const expected = space.expected.get(code).slice();
    if (rule.at && Array.isArray(config.spaces[spaceKey].landmarks[rule.at])
      && isRoundedCopy(space.landmarks[rule.at], actual)) {
      failures.push(`${code} 地標是該穴座標四捨五入後的複本`);
    }
    const dev = deviationCun(actual, expected, space.scalars, rule);
    const allow = config.knownExceedances?.find(entry => entry.code === code && entry.source === spaceKey);
    let status = 'OK';
    if (dev > threshold) {
      const cap = allow?.maxDeviationCun;
      if (allow && dev <= cap + 5e-7) status = '已知超標（允許）';
      else if (allow) {
        status = '惡化';
        failures.push(`${code} 偏差惡化：${dev.toFixed(3)} 寸 > 允許 ${Number(cap).toFixed(3)}（${allow.reason ?? ''}）`);
      } else {
        status = '超標';
        failures.push(`${code}（${name}）偏差 ${dev.toFixed(3)} 寸 > 門檻 ${threshold} [${source}]`);
      }
    }

    rows.push({
      code,
      name,
      source,
      rule: ruleSummary(rule),
      expected,
      actual,
      deviationCun: dev,
      status,
      note: onAnime ? '' : '僅目錄、未烘上身',
    });
  }

  rows.sort((a, b) => a.code.localeCompare(b.code, 'en'));
  const lines = [
    '# 骨度分寸穴位偏差報表',
    `門檻：${threshold} 寸；受檢 ${rows.length} 穴`,
    spanReport('少女', spaces.anime.scalars, config.boneSpans, spaces.anime.spans),
    spanReport('目錄', spaces.catalog.scalars, config.boneSpans, spaces.catalog.spans),
  ];
  if (landmarkSkinRows.length) {
    lines.push('', '## 少女地標離皮距離（mm）', '', '| 地標 | 距皮膚 mm |', '| --- | ---: |');
    for (const row of landmarkSkinRows) lines.push(`| ${row.name} | ${row.mm.toFixed(2)} |`);
  }
  lines.push(
    '',
    '| 代碼 | 名稱 | 來源 | 規則 | 應在位置 | 現有位置 | 偏差(寸) | 狀態 |',
    '| --- | --- | --- | --- | --- | --- | ---: | --- |',
  );
  for (const row of rows) {
    const note = row.note ? `；${row.note}` : '';
    lines.push(`| ${row.code} | ${row.name} | ${row.source} | ${row.rule} | ${fmtPos(row.expected)} | ${fmtPos(row.actual)} | ${row.deviationCun.toFixed(3)} | ${row.status}${note} |`);
  }
  const report = lines.join('\n');
  console.log(report);
  if (!report.includes('應在位置') || !report.includes('現有位置')) {
    failures.push('報表缺少應在位置或現有位置');
  }

  if (opts.writeReport !== false) {
    const outDir = join(project, '.validation');
    await mkdir(outDir, {recursive: true});
    await writeFile(join(outDir, 'cun-offset-report.md'), report, 'utf8');
  }

  return {passed: failures.length === 0, failures, rows, report, threshold};
}
