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

  await test('頭頸 21 已烘；可練習 19 穴（ST9／CV22 改至 #75）', () => {
    const lesson = curriculumSet('head-neck-21');
    assert.ok(lesson);
    assert.equal(lesson.codes.length, 21);
    assert.equal(ANIME_POINTS.length, 21);
    assert.equal(ANIME_SUPPORTED_CODES.size, 21);
    assert.deepEqual([...DEFERRED_PRACTICE_CODES].sort(), ['CV22', 'ST9']);
    assert.equal(isPracticeDeferred('ST9'), true);
    assert.equal(isPracticeDeferred('CV22'), true);
    assert.equal(isPracticeDeferred('GV24+'), false);
    const open = practiceOpenCodes(lesson.codes);
    assert.equal(open.length, 19);
    assert.ok(!open.includes('ST9'));
    assert.ok(!open.includes('CV22'));
    assert.equal(practicePoints.length, 19);
    assert.equal(ANIME_OUTFITS.length, 1);
    assert.equal(ANIME_OUTFITS[0].id, 'school');
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
    assert.equal(visibleCodes.size, 19);
    assert.ok(visible.length >= 19);
    for (const marker of visible) {
      assert.ok(lesson.codes.includes(marker.userData.point));
      assert.equal(isPracticeDeferred(marker.userData.point), false);
    }
    assert.equal(visibleCodes.has('ST9'), false);
    assert.equal(visibleCodes.has('CV22'), false);
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
  });

  await test('未烘穴位不在 ANIME_POINTS', () => {
    assert.equal(ANIME_SUPPORTED_CODES.has('LI4'), false);
    assert.equal(animePointTarget('LI4', 1), null);
    assert.ok(POINTS.find(p => p.code === 'LI4'), '目錄仍保留常用穴名稱供之後加穴');
  });

  await test('課堂集合順序不變', () => {
    assert.equal(CURRICULUM_SETS[0].id, 'head-neck-21');
    assert.equal(initialSimulation.tool, 'needle');
    assert.equal(initialSimulation.pointCode, 'GV24+');
  });

  const animeJson = JSON.parse(await readFile(join(project, 'app/anime-points.json'), 'utf8'));
  await test('anime-points.json 有 21 穴且無解剖 mesh', () => {
    assert.equal(animeJson.points.length, 21);
    for (const p of animeJson.points) {
      assert.ok(p.position?.length === 3);
      assert.ok(p.normal?.length === 3);
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
