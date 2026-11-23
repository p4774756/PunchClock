import assert from 'node:assert/strict';
import {readFile, writeFile, mkdir, mkdtemp, rm} from 'node:fs/promises';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {build} from 'esbuild';
import * as T from 'three';

/** Anime-assistant teaching checks (no FJ2810 / atlas.json). */
const project = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const validationDir = join(project, '.validation');
try { await mkdir(validationDir); } catch (error) { if (error.code !== 'EEXIST') throw error; }
const temporaryDir = await mkdtemp(join(validationDir, 'teaching-'));
let passed = 0, failed = 0;
const test = async (name, run) => {
  try { await run(); passed++; console.log(`通過：${name}`); }
  catch (error) { failed++; console.error(`失敗：${name}\n${error.message}`); }
};

const TRUNK_30 = [
  'TE21','SI19','GB2','ST6','TE17','GB21','ST12','GV16','GB20','GV15','BL10','GV14',
  'CV17','KI23','ST17','PC1','SP18','CV14','CV8','KI16','ST25','SP15','GB26',
  'BL14','BL43','SI11','SI9','GV4','BL23','BL52',
];
const BACK_SHU_14 = [
  'BL11','BL12','BL13','BL14','BL15','BL16','BL17','BL18','BL19','BL20','BL21','BL22','BL23','BL52',
];
const BACK_SHU_FIRST_LINE = BACK_SHU_14.filter(c => c !== 'BL52');
const HEAD_NECK_21 = [
  'GV20','GV23','GV24+','GV26','CV24','CV23','CV22','BL2','BL1','LI20','GB14','EX-HN4','ST1','ST2','ST3','ST4','ST9','TE23','EX-HN5','GB1','SI18',
];
const MIDLINE_TRUNK = new Set(['GV16','GV15','GV14','CV17','CV14','CV8','GV4']);

try {
  const bundlePath = join(temporaryDir, 'entry.mjs');
  const bundle = await build({
    stdin: {
      contents: "export {createTeachingOverlay} from './app/teaching-overlay'; export {POINTS,TOOLS,initialSimulation,CURRICULUM_SETS,curriculumSet,isPracticeDeferred,practiceOpenCodes,DEFERRED_PRACTICE_CODES,teachingPointMatchesQuery,libraryPointResults,ZHUYIN_PATTERN,POINT_BY_CODE} from './app/teaching'; export {ANIME_POINTS,ANIME_SUPPORTED_CODES,animePointTarget} from './app/character-mode'; export {avatarPracticePoints,animeAvatar,DEFAULT_AVATAR_ID} from './app/avatars'; export {ANIME_OUTFITS,isTorsoClothPeekPoint} from './app/outfits'; export {focusViewFromNormal} from './app/focus-view';",
      resolveDir: project, sourcefile: 'validate-teaching-entry.ts', loader: 'ts',
    },
    bundle: true, platform: 'node', format: 'esm', target: 'node22',
    external: ['three'], outfile: bundlePath, write: false, logLevel: 'silent',
  });
  await writeFile(bundlePath, bundle.outputFiles[0].contents);
  Object.defineProperty(globalThis, 'document', {
    configurable: true, writable: true,
    value: {createElement: tag => {
      assert.ok(['div','button'].includes(tag));
      return {className: '', hidden: false, style: {}, dataset: {}, textContent: '', setAttribute() {}, remove() {}, classList:{toggle(){}}};
    }},
  });
  const {createTeachingOverlay, POINTS, TOOLS, initialSimulation, CURRICULUM_SETS, curriculumSet, isPracticeDeferred, practiceOpenCodes, DEFERRED_PRACTICE_CODES, teachingPointMatchesQuery, libraryPointResults, ZHUYIN_PATTERN, POINT_BY_CODE, ANIME_POINTS, ANIME_SUPPORTED_CODES, animePointTarget, avatarPracticePoints, animeAvatar, DEFAULT_AVATAR_ID, ANIME_OUTFITS, isTorsoClothPeekPoint, focusViewFromNormal} = await import(pathToFileURL(bundlePath));
  const camera = new T.PerspectiveCamera(34, 1.6, 0.005, 100);
  camera.position.set(0, 1.4, 2.5);
  camera.lookAt(0, 1.35, 0);
  camera.updateMatrixWorld(true);
  const state = overrides => ({...initialSimulation, ...overrides});
  const scene = new T.Scene();
  const practicePoints = avatarPracticePoints(animeAvatar(DEFAULT_AVATAR_ID));
  const animeJson = JSON.parse(await readFile(join(project, 'app/anime-points.json'), 'utf8'));
  const overlay = createTeachingOverlay(scene, {
    points: practicePoints,
    resolvePoint: (code, side) => animePointTarget(code, side),
  });
  const update = (overrides = {}) => overlay.update(state(overrides), camera, 1280, 800);

  await test('TOOLS 僅針與指壓', () => {
    assert.deepEqual(TOOLS.map(t => t.id), ['needle', 'finger']);
  });

  await test('364 穴 zhuyin 非空且格式合法', () => {
    assert.equal(POINTS.length, 364);
    for (const point of POINTS) {
      assert.ok(point.zhuyin, `${point.code} 缺少 zhuyin`);
      assert.match(point.zhuyin, ZHUYIN_PATTERN, `${point.code} zhuyin 格式不合法：${point.zhuyin}`);
    }
  });

  const HOMOPHONE_ZHUYIN = {
    LI18: 'ㄈㄨˊ ㄊㄨˊ', ST32: 'ㄈㄨˊ ㄊㄨˋ', ST3: 'ㄐㄩˋ ㄌㄧㄠˊ', GB29: 'ㄐㄩ ㄌㄧㄠˊ',
    SI4: 'ㄨㄢˋ ㄍㄨˇ', GB12: 'ㄨㄢˊ ㄍㄨˇ', KI15: 'ㄓㄨㄥ ㄓㄨˋ', TE3: 'ㄓㄨㄥ ㄓㄨˇ',
    GB32: 'ㄓㄨㄥ ㄉㄨˊ', LR6: 'ㄓㄨㄥ ㄉㄨ', GV28: 'ㄧㄣˊ ㄐㄧㄠ', CV7: 'ㄧㄣ ㄐㄧㄠ',
    LR2: 'ㄒㄧㄥˊ ㄐㄧㄢ', GV1: 'ㄔㄤˊ ㄑㄧㄤˊ', LU11: 'ㄕㄠˋ ㄕㄤ', LI4: 'ㄏㄜˊ ㄍㄨˇ',
    PC3: 'ㄑㄩ ㄗㄜˊ', LI11: 'ㄑㄩ ㄔˊ', SP15: 'ㄉㄚˋ ㄏㄥˊ', LR1: 'ㄉㄚˋ ㄉㄨㄣ', CV14: 'ㄐㄩˋ ㄑㄩㄝˋ',
    BL2: 'ㄘㄨㄢˊ ㄓㄨˊ', BL4: 'ㄑㄩ ㄔㄚ', BL5: 'ㄨˇ ㄔㄨˋ',
    CV17: 'ㄉㄢˋ ㄓㄨㄥ', ST6: 'ㄐㄧㄚˊ ㄔㄜ', BL28: 'ㄆㄤˊ ㄍㄨㄤ ㄩˊ', LR5: 'ㄌㄧˇ ㄍㄡ', BL45: 'ㄧ ㄒㄧ',
    KI9: 'ㄓㄨˊ ㄅㄧㄣ', TE19: 'ㄌㄨˊ ㄒㄧˊ', LR14: 'ㄑㄧˊ ㄇㄣˊ', KI13: 'ㄑㄧˋ ㄒㄩㄝˋ',
    TE18: 'ㄑㄧˋ ㄇㄞˋ', LI7: 'ㄨㄣ ㄌㄧㄡˋ', KI7: 'ㄈㄨˋ ㄌㄧㄡˋ', SP10: 'ㄒㄩㄝˋ ㄏㄞˇ',
  };

  await test('同音不同字與多音字注音', () => {
  for (const [code, expected] of Object.entries(HOMOPHONE_ZHUYIN)) {
    const point = POINT_BY_CODE.get(code);
    assert.ok(point, code);
    assert.equal(point.zhuyin, expected, `${code} ${point.name}`);
  }
  });

  await test('注音搜尋（巨闕）', () => {
    const cv14 = POINT_BY_CODE.get('CV14');
    assert.ok(cv14);
    const hit = q => teachingPointMatchesQuery(cv14, q);
    assert.equal(hit('ㄐㄩˋ ㄑㄩㄝˋ'), true);
    assert.equal(hit('ㄐㄩㄑㄩㄝ'), true);
    assert.equal(hit('ㄐㄩ'), true);
    assert.equal(hit('巨闕'), true);
    assert.equal(hit('CV14'), true);
    assert.equal(hit('cv14'), true);
    assert.equal(hit('足三里'), false);
    assert.equal(hit('ST36'), false);
  });

  await test('穴位庫有輸入時搜完整目錄', () => {
    const codes = new Set(animeAvatar(DEFAULT_AVATAR_ID).points.map(p => p.code));
    const quiet = libraryPointResults('', 'all', 'all', 'head-neck-21', codes);
    assert.equal(quiet.length, 21);
    assert.equal(quiet.every(p => codes.has(p.code)), true);
    assert.equal(quiet.some(p => p.code === 'TE18'), false);
    const qiyi = libraryPointResults('ㄑㄧˋ', 'all', 'all', 'head-neck-21', codes).map(p => p.code);
    assert.ok(qiyi.includes('TE18'), 'ㄑㄧˋ 應含瘛脈 TE18');
    assert.ok(qiyi.includes('ST1'), 'ㄑㄧˋ 仍含已烘的承泣');
    const liou = libraryPointResults('ㄌㄧㄡˋ', 'all', 'all', 'head-neck-21', codes).map(p => p.code).sort();
    assert.deepEqual(liou, ['KI7', 'LI7']);
    const yixi = libraryPointResults('ㄧ ㄒㄧ', 'all', 'all', 'head-neck-21', codes).map(p => p.code);
    assert.ok(yixi.includes('BL45'), 'ㄧ ㄒㄧ 應含譩譆');
    for (const setId of ['trunk-30', 'back-shu-14', 'common-10']) {
      const again = libraryPointResults('ㄌㄧㄡˋ', 'all', 'all', setId, codes).map(p => p.code);
      assert.ok(again.includes('LI7') && again.includes('KI7'), setId);
      assert.ok(libraryPointResults('ㄑㄧˋ', 'all', 'all', setId, codes).some(p => p.code === 'TE18'), setId);
    }
  });

  await test('頭頸 21＋軀幹 30 已烘；ST9／CV22 已重新開放', () => {
    const lesson = curriculumSet('head-neck-21');
    assert.ok(lesson);
    assert.equal(lesson.codes.length, 21);
    assert.equal(ANIME_POINTS.length, 83);
    assert.equal(ANIME_SUPPORTED_CODES.size, 83);
    assert.equal(DEFERRED_PRACTICE_CODES.size, 0);
    assert.equal(isPracticeDeferred('ST9'), false);
    assert.equal(isPracticeDeferred('CV22'), false);
    const open = practiceOpenCodes(lesson.codes);
    assert.equal(open.length, 21);
    assert.ok(open.includes('ST9'));
    assert.ok(open.includes('CV22'));
    assert.equal(practicePoints.length, 83);
    assert.equal(ANIME_OUTFITS.length, 2);
    assert.deepEqual(ANIME_OUTFITS.map(o => o.id), ['school', 'study-body']);
    assert.equal(isTorsoClothPeekPoint('ST17'), true);
    assert.equal(isTorsoClothPeekPoint('BL23'), true);
    assert.equal(isTorsoClothPeekPoint('SI11'), true);
    assert.equal(isTorsoClothPeekPoint('SI12'), true);
    assert.equal(isTorsoClothPeekPoint('ST2'), false);
    assert.equal(isTorsoClothPeekPoint('GB21'), false);
    assert.equal(isTorsoClothPeekPoint(null), false);
    for (const code of lesson.codes) {
      assert.ok(ANIME_SUPPORTED_CODES.has(code), `缺少烘點 ${code}`);
      const target = animePointTarget(code, 1);
      assert.ok(target);
      assert.ok(target.position.every(Number.isFinite));
      assert.ok(target.normal.every(Number.isFinite));
      assert.doesNotMatch(JSON.stringify(target), /FJ2810/);
    }
    update({curriculumSet: 'head-neck-21', showPoints: true});
    const visible = overlay.markers.filter(marker => marker.visible);
    const visibleCodes = new Set(visible.map(marker => marker.userData.point));
    assert.ok(visibleCodes.size >= 21);
    assert.ok(visibleCodes.has('ST9'));
    assert.ok(visibleCodes.has('CV22'));
  });

  await test('頭胸腹背腰 30 已烘且中線／左右正確', () => {
    const lesson = curriculumSet('trunk-30');
    assert.ok(lesson);
    assert.equal(lesson.codes.length, 30);
    assert.deepEqual([...lesson.codes], TRUNK_30);
    for (const code of TRUNK_30) {
      assert.ok(ANIME_SUPPORTED_CODES.has(code), `缺少 ${code}`);
      const point = ANIME_POINTS.find(p => p.code === code);
      assert.ok(point);
      if (MIDLINE_TRUNK.has(code)) {
        assert.equal(point.midline, true);
        assert.equal(point.position[0], 0);
        const left = animePointTarget(code, 1);
        const right = animePointTarget(code, -1);
        assert.deepEqual(left.position, right.position);
      } else {
        assert.equal(point.midline, false);
        assert.ok(point.position[0] > 0, `${code} 應存解剖左側 +X`);
        const left = animePointTarget(code, 1);
        const right = animePointTarget(code, -1);
        assert.ok(left.position[0] < 0, `${code} 左側 runtime 應為 −X`);
        assert.ok(right.position[0] > 0, `${code} 右側 runtime 應為 +X`);
        assert.notEqual(left.position[0], right.position[0]);
      }
    }
  });

  await test('選中穴時針／指壓 contact 可見且非 FJ2810', () => {
    for (const tool of ['needle', 'finger']) {
      const response = update({tool, pointCode: 'GV24+', side: 1, running: true, elapsed: 0.8, amplitude: 0.6, effects: true});
      assert.ok(response);
      assert.ok(response.target.position.every(Number.isFinite));
      assert.notEqual(response.target.meshId, 'FJ2810');
      const instruments = [];
      overlay.root.traverse(object => { if (object.name === `instrument-${tool}`) instruments.push(object); });
      assert.equal(instruments.length, 1);
      assert.equal(instruments[0].visible, true);
      if (tool === 'finger') assert.ok(response.deform <= 0);
    }
    for (const code of ['ST9', 'CV22', 'CV17', 'GV4']) {
      const response = update({tool: 'needle', pointCode: code, side: 1, running: true, elapsed: 0.5, amplitude: 0.55, effects: true});
      assert.ok(response, code);
      assert.ok(response.target.position.every(Number.isFinite));
    }
  });

  await test('未烘穴位不在 ANIME_POINTS', () => {
    assert.equal(ANIME_SUPPORTED_CODES.has('LI4'), false);
    assert.equal(animePointTarget('LI4', 1), null);
    assert.ok(POINTS.find(p => p.code === 'LI4'), '目錄仍保留常用穴名稱供之後加穴');
  });

  await test('課堂集合順序不變', () => {
    assert.equal(CURRICULUM_SETS[0].id, 'head-neck-21');
    assert.equal(CURRICULUM_SETS[1].id, 'trunk-30');
    assert.equal(CURRICULUM_SETS[2].id, 'back-shu-14');
    assert.equal(CURRICULUM_SETS[4].id, 'arm-joints-15');
    assert.equal(initialSimulation.tool, 'needle');
    assert.equal(initialSimulation.pointCode, 'GV24+');
  });

  await test('背俞 14 已烘、口訣與第一側線排列', () => {
    const lesson = curriculumSet('back-shu-14');
    assert.ok(lesson);
    assert.equal(lesson.codes.length, 14);
    assert.deepEqual([...lesson.codes], BACK_SHU_14);
    assert.match(lesson.lesson, /大風肺厥心督膈/);
    assert.match(lesson.lesson, /第 8 胸椎不取穴/);
    for (const code of BACK_SHU_14) {
      assert.ok(ANIME_SUPPORTED_CODES.has(code), `缺少 ${code}`);
    }
    const byCode = new Map(animeJson.points.map(p => [p.code, p]));
    const xs = BACK_SHU_FIRST_LINE.map(c => byCode.get(c).position[0]);
    assert.ok(Math.max(...xs) - Math.min(...xs) < 0.0015, '第一側線旁開應共線');
    const ys = BACK_SHU_FIRST_LINE.map(c => byCode.get(c).position[1]);
    for (let i = 1; i < ys.length; i++) {
      assert.ok(ys[i - 1] > ys[i], `${BACK_SHU_FIRST_LINE[i - 1]} 應高於 ${BACK_SHU_FIRST_LINE[i]}`);
    }
    const stepAvg = (ys[0] - ys[ys.length - 1]) / (ys.length - 1);
    const t8Gap = ys[6] - ys[7];
    assert.ok(t8Gap > stepAvg * 1.35, `T7→T9（跳過 T8）間距應大於平均椎間距`);
    const bl23 = byCode.get('BL23');
    const bl52 = byCode.get('BL52');
    assert.ok(Math.abs(bl23.position[1] - bl52.position[1]) < 0.0015, '腎俞與志室應同高');
    assert.ok(byCode.get('GV14').position[1] > byCode.get('BL11').position[1], '大椎應高於大杼');
  });

  await test('anime-points.json 含頭頸 21、軀幹 30 與背俞 11', () => {
    assert.equal(animeJson.points.length, 83);
    const codes = new Set(animeJson.points.map(p => p.code));
    for (const code of HEAD_NECK_21) assert.ok(codes.has(code), `lost ${code}`);
    for (const code of TRUNK_30) assert.ok(codes.has(code), `missing ${code}`);
    for (const p of animeJson.points) {
      assert.ok(p.position?.length === 3);
      assert.ok(p.normal?.length === 3);
    }
  });

  await test('大椎 GV14 高於厥陰俞 BL14', () => {
    const gv14 = animeJson.points.find(p => p.code === 'GV14');
    const bl14 = animeJson.points.find(p => p.code === 'BL14');
    assert.ok(gv14 && bl14);
    assert.ok(gv14.position[1] > bl14.position[1], `GV14 y=${gv14.position[1]} 應高於 BL14 y=${bl14.position[1]}`);
  });

  await test('頸胸五穴：頦唇溝／喉結前緣／胸骨上窩／鎖骨上窩', async () => {
    const {readFileSync} = await import('node:fs');
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
    const boneY = name => {
      const o = gltf.scene.getObjectByName(name);
      assert.ok(o, `missing bone ${name}`);
      const v = new T.Vector3();
      o.getWorldPosition(v);
      return v.y;
    };
    const shoulderY = Math.max(boneY('J_Bip_L_Shoulder'), boneY('J_Bip_R_Shoulder'));
    const neckY = boneY('J_Bip_C_Neck');
    const upperChestY = boneY('J_Bip_C_UpperChest');
    const pos = code => animeJson.points.find(p => p.code === code).position;
    const cv24 = pos('CV24');
    const cv23 = pos('CV23');
    const st9 = pos('ST9');
    const cv22 = pos('CV22');
    const st12 = pos('ST12');
    assert.ok(cv24[1] > 1.4, `承漿 y=${cv24[1]} 應在頦唇溝`);
    assert.ok(cv24[1] > cv23[1] + 0.035, '承漿應明顯高於廉泉');
    assert.ok(cv23[1] > st9[1] + 0.005, '廉泉應略高於人迎');
    assert.ok(st9[1] > cv22[1] + 0.015, '人迎應高於天突');
    assert.ok(cv22[1] > shoulderY - 0.01, `天突 y=${cv22[1]} 應在鎖骨線上方（肩≈${shoulderY.toFixed(3)}）`);
    assert.ok(cv22[1] < neckY, `天突應低於頸骨（neck≈${neckY.toFixed(3)}）`);
    assert.ok(cv22[1] > upperChestY + 0.08, `天突不可落在鎖骨下上胸（上胸≈${upperChestY.toFixed(3)}）`);
    const st9n = animeJson.points.find(p => p.code === 'ST9').normal;
    const st12n = animeJson.points.find(p => p.code === 'ST12').normal;
    const skins = [];
    gltf.scene.traverse(o => {
      if (!o.isMesh) return;
      const mats = Array.isArray(o.material) ? o.material : [o.material];
      if (mats.some(m => /Face_00_SKIN|Body_00_SKIN|FaceMouth/i.test(m?.name || ''))) skins.push(o);
    });
    const ray = new T.Raycaster();
    const frontHit = (x, y) => {
      ray.set(new T.Vector3(x, y, -0.16), new T.Vector3(0, 0, 1));
      ray.far = 0.35;
      const hit = ray.intersectObjects(skins, false)[0];
      assert.ok(hit, `頸前射線未命中 x=${x} y=${y}`);
      const outward = hit.face.normal.clone().transformDirection(hit.object.matrixWorld);
      return {point: hit.point, normal: outward};
    };
    let anteriorBorder = 0;
    for (let x = 0.008; x <= 0.04; x += 0.001) {
      const hit = frontHit(x, st9[1]);
      if (hit.normal.z < -0.7 && Math.abs(hit.normal.x) < 0.5) anteriorBorder = x;
    }
    assert.ok(anteriorBorder > 0.012 && anteriorBorder < 0.02, `頸前緣 x≈${anteriorBorder.toFixed(3)} 應落在喉結旁`);
    assert.ok(st9[0] <= anteriorBorder + 0.001, `人迎 x=${st9[0]} 已過頸前緣（約 ${anteriorBorder.toFixed(3)}），偏到頸側`);
    assert.ok(st9[0] >= anteriorBorder - 0.004, `人迎 x=${st9[0]} 應靠近頸前緣（約 ${anteriorBorder.toFixed(3)}）`);
    assert.ok(st9[2] < -0.015, `人迎 z=${st9[2]} 應在頸前，不可偏到側極之後`);
    assert.ok(st9n[2] < -0.75 && st9n[0] < 0.45, `人迎法線應朝前（n=${st9n.join(',')}）`);
    let fossaY = null;
    for (let y = shoulderY - 0.02; y <= neckY; y += 0.002) {
      ray.set(new T.Vector3(st12[0], y, -0.16), new T.Vector3(0, 0, 1));
      ray.far = 0.35;
      const hit = ray.intersectObjects(skins, false)[0];
      if (!hit) break;
      const outward = hit.face.normal.clone().transformDirection(hit.object.matrixWorld);
      if (fossaY == null && outward.y > 0.85) fossaY = hit.point.y;
      else if (fossaY != null && outward.y < 0.5) break;
    }
    assert.ok(fossaY != null && fossaY > shoulderY, `鎖骨上窩底 y≈${fossaY} 應高於肩骨`);
    assert.ok(st12[1] >= fossaY - 0.002, `缺盆 y=${st12[1]} 仍在鎖骨前坡，窩底約 ${fossaY.toFixed(3)}`);
    assert.ok(st12[1] < fossaY + 0.012, `缺盆 y=${st12[1]} 高出鎖骨上窩底（約 ${fossaY.toFixed(3)}），不是窩裡`);
    assert.ok(st12[1] > cv22[1] - 0.02, `缺盆 y=${st12[1]} 不可明顯低於天突（${cv22[1]}）而落到胸壁`);
    assert.ok(st12[2] > -0.032, `缺盆 z=${st12[2]} 應退入鎖骨上窩，不可停在鎖骨前坡`);
    assert.ok(st12n[1] > 0.85, `缺盆法線應朝上（鎖骨上窩），ny=${st12n[1]}`);
    const snapSide = (position, normal) => {
      const n = normal.clone().normalize();
      const origin = position.clone().addScaledVector(n, 0.08);
      ray.set(origin, n.clone().negate());
      ray.far = 0.2;
      const hits = ray.intersectObjects(skins, false);
      const hit = hits.find(h => h.point.distanceTo(position) < 0.08) ?? hits[0];
      assert.ok(hit, '吸附未命中皮膚');
      const outward = hit.face.normal.clone().transformDirection(hit.object.matrixWorld);
      if (outward.dot(n) < 0) outward.negate();
      return {point: hit.point, normal: outward};
    };
    const sides = [];
    for (const side of [1, -1]) {
      const hit = snapSide(
        new T.Vector3(-st12[0] * side, st12[1], st12[2]),
        new T.Vector3(-st12n[0] * side, st12n[1], st12n[2]),
      );
      sides.push(hit);
      assert.ok(hit.normal.y > 0.85, `缺盆側 ${side} 吸附後法線未朝上 ny=${hit.normal.y.toFixed(3)}`);
      assert.ok(hit.point.y >= fossaY - 0.004, `缺盆側 ${side} y=${hit.point.y.toFixed(3)} 落到鎖骨上窩以下`);
      assert.ok(hit.point.z > -0.034, `缺盆側 ${side} z=${hit.point.z.toFixed(3)} 仍在鎖骨前坡`);
    }
    assert.ok(Math.abs(sides[0].point.y - sides[1].point.y) < 0.004, `缺盆左右高度差 ${Math.abs(sides[0].point.y - sides[1].point.y).toFixed(4)}，應對稱落在鎖骨上窩`);
  });

  await test('課堂目錄人迎／缺盆跟上角色頸前緣與鎖骨上窩', () => {
    const catalog = code => POINT_BY_CODE.get(code);
    const anime = code => animeJson.points.find(p => p.code === code);
    const cv23 = catalog('CV23').position;
    const cv22 = catalog('CV22').position;
    const st9 = catalog('ST9').position;
    const st12 = catalog('ST12').position;
    const a23 = anime('CV23').position;
    const a9 = anime('ST9').position;
    const a12 = anime('ST12').position;
    const cun = anime('ST17').position[0] / 4;
    assert.ok(Math.abs(st9[0] - a9[0]) < 0.001, `課堂人迎 x=${st9[0]} 應跟上角色頸前緣 ${a9[0]}`);
    assert.ok(Math.abs(st9[0] / cun - 1.5) < 0.35, `人迎旁開 ${(st9[0] / cun).toFixed(2)} 寸（頸前緣以乳中橫寸折算約 1.2–1.5 寸）`);
    assert.ok(Math.abs(st9[2] + a9[2]) < 0.001, `課堂人迎 z=${st9[2]} 應為頸前（角色 z=${a9[2]}）`);
    assert.ok(cv23[1] > st9[1], `課堂人迎 y=${st9[1]} 應略低於廉泉 ${cv23[1]}`);
    assert.ok(st9[1] > cv22[1], '課堂人迎應高於天突');
    assert.ok(Math.abs((cv23[1] - st9[1]) - (a23[1] - a9[1])) < 0.001, '課堂人迎與廉泉的高度差應與角色一致');
    assert.ok(Math.abs(st12[0] - a12[0]) < 0.001, '課堂缺盆 x 應與角色乳中線一致');
    assert.ok(Math.abs(st12[2] + a12[2]) < 0.001, `課堂缺盆 z=${st12[2]} 應為窩底（角色 z=${a12[2]}），不是鎖骨前坡`);
    // 目錄與角色的垂直原點不同；沿用上一輪已對齊的高度差，再加上這一輪窩底上移。
    const fossaLift = a12[1] - 1.318;
    assert.ok(Math.abs(st12[1] - (1.4676 + fossaLift)) < 0.001, '課堂缺盆高度應落在鎖骨上窩，而不是鎖骨前坡');
    const len = v => Math.hypot(v[0], v[1], v[2]);
    const n9 = catalog('ST9').normal;
    const n12 = catalog('ST12').normal;
    assert.ok(n9[2] / len(n9) > 0.9, '課堂人迎法線應朝前');
    assert.ok(n12[1] / len(n12) > 0.85, '課堂缺盆法線應朝上');
    for (const code of ['ST10', 'ST11', 'LI17', 'LI18', 'SI16']) {
      assert.ok(catalog(code), code);
      assert.equal(anime(code), undefined, `${code} 不應烘到角色`);
    }
  });

  await test('天宗 SI11 在肩胛區、較舊種子內側且偏後', () => {
    const si11 = animeJson.points.find(p => p.code === 'SI11');
    assert.ok(si11);
    assert.ok(si11.position[0] < 0.11, `SI11 x=${si11.position[0]} 應比舊的 0.11 更內側`);
    assert.ok(si11.position[1] > 1.24, `SI11 y=${si11.position[1]} 應略高於舊的 1.24`);
    assert.ok(si11.position[2] > 0.03, `SI11 z=${si11.position[2]} 應在背面`);
    assert.ok(si11.normal[2] > 0.5, `SI11 法線應朝後（正看背面）`);
  });

  await test('人迎／天突／廉泉聚焦距離明顯近於預設；天宗從背面', () => {
    const front = focusViewFromNormal('ST9', new T.Vector3(0.5, 0.5, -0.7).normalize(), 1);
    assert.ok(front.distance < 0.45, `ST9 distance=${front.distance} 應遠小於預設 .62`);
    assert.ok(front.viewDir.z < -0.8, '人迎應從前方看');
    const cv22 = focusViewFromNormal('CV22', new T.Vector3(0, 0, -1), 1);
    assert.ok(cv22.distance < 0.45);
    const si11 = focusViewFromNormal('SI11', new T.Vector3(0.3, -0.2, 0.9).normalize(), 1);
    assert.ok(si11.viewDir.z > 0.8, '天宗應從背面正看');
    assert.ok(si11.distance > 0.5 && si11.distance < 0.9);
  });

  await test('人迎錨定後朝上皮膚法線仍走近距，不可變百會 1.35', () => {
    // After overlay.anchor(), ST9 hit normal is ~[-0.06, 0.98, -0.17] (y≈0.984).
    const up = new T.Vector3(-0.06, 0.98, -0.17).normalize();
    assert.ok(up.y > 0.85, '測試法線必須能觸發舊的 normal.y>.85 分支');
    for (const code of ['ST9', 'CV22', 'CV23', 'ST12']) {
      const framed = focusViewFromNormal(code, up.clone(), 1);
      assert.ok(framed.distance < 0.45, `${code} distance=${framed.distance} 應 < 0.45（不可為 1.35）`);
      assert.notEqual(framed.distance, 1.35, `${code} 不可落入 GV20 遠距`);
      assert.ok(framed.viewDir.z < -0.8, `${code} 應從前方看，viewDir.z=${framed.viewDir.z}`);
    }
    const crown = focusViewFromNormal('GV20', up.clone(), 1);
    assert.equal(crown.distance, 1.35);
  });

  await test('每個烘點距離 Face／Body 皮膚 < 2 mm', async () => {
    const {readFileSync} = await import('node:fs');
    const {GLTFLoader} = await import('three/examples/jsm/loaders/GLTFLoader.js');
    globalThis.self = globalThis;
    globalThis.URL = URL;
    globalThis.webkitURL = URL;
    if (!globalThis.document) {
      globalThis.document = {createElementNS: () => ({style: {}, setAttribute() {}, getContext() { return null; }})};
    }
    const buffer = readFileSync(join(project, 'public/models/anime-assistant.glb')).buffer;
    const gltf = await new Promise((res, rej) => new GLTFLoader().parse(buffer, '', res, rej));
    const scene = gltf.scene;
    scene.updateMatrixWorld(true);
    const skins = [];
    scene.traverse(o => {
      if (!o.isMesh) return;
      const mats = Array.isArray(o.material) ? o.material : [o.material];
      if (mats.some(m => /Face_00_SKIN|Body_00_SKIN|FaceMouth/i.test(m?.name || ''))) skins.push(o);
    });
    assert.ok(skins.length > 0);
    const tri = new T.Triangle();
    const closest = new T.Vector3();
    const a = new T.Vector3(), b = new T.Vector3(), c = new T.Vector3();
    const distToSkin = (point) => {
      let best = Infinity;
      for (const mesh of skins) {
        const pos = mesh.geometry.getAttribute('position');
        const index = mesh.geometry.index;
        if (!pos || !index) continue;
        for (let i = 0; i < index.count; i += 3) {
          a.fromBufferAttribute(pos, index.getX(i)).applyMatrix4(mesh.matrixWorld);
          b.fromBufferAttribute(pos, index.getX(i + 1)).applyMatrix4(mesh.matrixWorld);
          c.fromBufferAttribute(pos, index.getX(i + 2)).applyMatrix4(mesh.matrixWorld);
          tri.set(a, b, c);
          tri.closestPointToPoint(point, closest);
          best = Math.min(best, point.distanceTo(closest));
        }
      }
      return best;
    };
    for (const p of animeJson.points) {
      // JSON +X = anatomical left; bake snapped on world −X then took abs — try both signs.
      const candidates = p.midline
        ? [new T.Vector3(0, p.position[1], p.position[2])]
        : [new T.Vector3(-p.position[0], p.position[1], p.position[2]), new T.Vector3(p.position[0], p.position[1], p.position[2])];
      let best = Infinity;
      for (const world of candidates) best = Math.min(best, distToSkin(world));
      assert.ok(best < 0.002, `${p.code} 距皮膚 ${(best * 1000).toFixed(2)} mm（需 < 2 mm）`);
    }
  });

  await test('同一直線／同一水平（種子）', async () => {
    const {validateCollinearSeeds} = await import('./lib/collinear-validate.mjs');
    const {passed, failures} = validateCollinearSeeds(animeJson);
    assert.ok(passed, failures.join('\n'));
  });

  await test('骨度分寸規則校驗（第一期）', async () => {
    const {runCunValidation} = await import('./lib/cun-validate.mjs');
    const {passed, failures} = await runCunValidation({writeReport: true});
    assert.ok(passed, failures.join('\n'));
  });

  overlay.dispose();
} finally {
  if (Object.getOwnPropertyDescriptor(globalThis, 'document')) delete globalThis.document;
  await rm(temporaryDir, {recursive: true, force: true});
}

if (failed) {
  console.error(`${failed} 項失敗，${passed} 項通過。`);
  process.exit(1);
}
console.log(`${passed} 項少女教學檢查通過。`);
