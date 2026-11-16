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
const HEAD_NECK_21 = [
  'GV20','GV23','GV24+','GV26','CV24','CV23','CV22','BL2','BL1','LI20','GB14','EX-HN4','ST1','ST2','ST3','ST4','ST9','TE23','EX-HN5','GB1','SI18',
];
const MIDLINE_TRUNK = new Set(['GV16','GV15','GV14','CV17','CV14','CV8','GV4']);

try {
  const bundlePath = join(temporaryDir, 'entry.mjs');
  const bundle = await build({
    stdin: {
      contents: "export {createTeachingOverlay} from './app/teaching-overlay'; export {POINTS,TOOLS,initialSimulation,CURRICULUM_SETS,curriculumSet,isPracticeDeferred,practiceOpenCodes,DEFERRED_PRACTICE_CODES} from './app/teaching'; export {ANIME_POINTS,ANIME_SUPPORTED_CODES,animePointTarget} from './app/character-mode'; export {avatarPracticePoints,animeAvatar,DEFAULT_AVATAR_ID} from './app/avatars'; export {ANIME_OUTFITS} from './app/outfits';",
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
  const {createTeachingOverlay, POINTS, TOOLS, initialSimulation, CURRICULUM_SETS, curriculumSet, isPracticeDeferred, practiceOpenCodes, DEFERRED_PRACTICE_CODES, ANIME_POINTS, ANIME_SUPPORTED_CODES, animePointTarget, avatarPracticePoints, animeAvatar, DEFAULT_AVATAR_ID, ANIME_OUTFITS} = await import(pathToFileURL(bundlePath));
  const camera = new T.PerspectiveCamera(34, 1.6, 0.005, 100);
  camera.position.set(0, 1.4, 2.5);
  camera.lookAt(0, 1.35, 0);
  camera.updateMatrixWorld(true);
  const state = overrides => ({...initialSimulation, ...overrides});
  const scene = new T.Scene();
  const practicePoints = avatarPracticePoints(animeAvatar(DEFAULT_AVATAR_ID));
  const overlay = createTeachingOverlay(scene, {
    points: practicePoints,
    resolvePoint: (code, side) => animePointTarget(code, side),
  });
  const update = (overrides = {}) => overlay.update(state(overrides), camera, 1280, 800);

  await test('TOOLS 僅針與指壓', () => {
    assert.deepEqual(TOOLS.map(t => t.id), ['needle', 'finger']);
  });

  await test('頭頸 21＋軀幹 30 已烘；ST9／CV22 已重新開放', () => {
    const lesson = curriculumSet('head-neck-21');
    assert.ok(lesson);
    assert.equal(lesson.codes.length, 21);
    assert.equal(ANIME_POINTS.length, 51);
    assert.equal(ANIME_SUPPORTED_CODES.size, 51);
    assert.equal(DEFERRED_PRACTICE_CODES.size, 0);
    assert.equal(isPracticeDeferred('ST9'), false);
    assert.equal(isPracticeDeferred('CV22'), false);
    const open = practiceOpenCodes(lesson.codes);
    assert.equal(open.length, 21);
    assert.ok(open.includes('ST9'));
    assert.ok(open.includes('CV22'));
    assert.equal(practicePoints.length, 51);
    assert.equal(ANIME_OUTFITS.length, 2);
    assert.deepEqual(ANIME_OUTFITS.map(o => o.id), ['school', 'study-body']);
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
    assert.equal(initialSimulation.tool, 'needle');
    assert.equal(initialSimulation.pointCode, 'GV24+');
  });

  const animeJson = JSON.parse(await readFile(join(project, 'app/anime-points.json'), 'utf8'));
  await test('anime-points.json 含原 21 加本週 30', () => {
    assert.equal(animeJson.points.length, 51);
    const codes = new Set(animeJson.points.map(p => p.code));
    for (const code of HEAD_NECK_21) assert.ok(codes.has(code), `lost ${code}`);
    for (const code of TRUNK_30) assert.ok(codes.has(code), `missing ${code}`);
    for (const p of animeJson.points) {
      assert.ok(p.position?.length === 3);
      assert.ok(p.normal?.length === 3);
    }
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
