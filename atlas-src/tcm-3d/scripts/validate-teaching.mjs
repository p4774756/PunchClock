import assert from 'node:assert/strict';
import {readFile, writeFile, mkdir, mkdtemp, rm} from 'node:fs/promises';
import {dirname, join, resolve} from 'node:path';
import {fileURLToPath, pathToFileURL} from 'node:url';
import {build} from 'esbuild';
import * as T from 'three';

// CPU geometry/interaction checks. This does not validate clinical point locations
// or replace a browser's rendering, input, and GPU shader checks.
const project = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const validationDir = join(project, '.validation');
let createdValidationDir = false;
try { await mkdir(validationDir); createdValidationDir = true; }
catch (error) { if (error.code !== 'EEXIST') throw error; }
const temporaryDir = await mkdtemp(join(validationDir, 'teaching-'));
const previousDocument = Object.getOwnPropertyDescriptor(globalThis, 'document');
let passed = 0, failed = 0;
const test = async (name, run) => {
  try { await run(); passed++; console.log(`通過：${name}`); }
  catch (error) { failed++; console.error(`失敗：${name}\n${error.message}`); }
};
const near = (actual, expected, tolerance = 1e-7) =>
  assert.ok(Math.abs(actual - expected) <= tolerance, `${actual} ≠ ${expected}（容差 ${tolerance}）`);
const finiteVector = values => assert.ok(values.every(Number.isFinite), '向量中出現非有限數值');
const effectivelyVisible = object => {
  for (let current = object; current; current = current.parent) if (!current.visible) return false;
  return true;
};

try {
  const bundlePath = join(temporaryDir, 'entry.mjs');
  const bundle = await build({
    stdin: {
      contents: "export {createTeachingOverlay} from './app/teaching-overlay'; export {softenPelvicSurface} from './app/teaching-skin'; export {POINTS,TOOLS,pointTarget,initialSimulation,CURRICULUM_SETS,curriculumSet} from './app/teaching'; export {PAIN_CASES,matchPainRegion,preferReviewedCodes,painRelationCodes} from './app/pain-relations';",
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
      return {className: '', hidden: false, style: {}, dataset: {}, textContent: '', setAttribute() {}, remove() {}};
    }},
  });
  const {createTeachingOverlay, softenPelvicSurface, POINTS, TOOLS, pointTarget, initialSimulation,CURRICULUM_SETS,curriculumSet,PAIN_CASES,matchPainRegion,preferReviewedCodes,painRelationCodes} = await import(pathToFileURL(bundlePath));
  const camera = new T.PerspectiveCamera(34, 1.6, 0.005, 100);
  camera.position.set(0, 0.85, 4);
  camera.lookAt(0, 0.85, 0);
  camera.updateMatrixWorld(true);
  const state = overrides => ({...initialSimulation, ...overrides});
  const scene = new T.Scene();
  const overlay = createTeachingOverlay(scene);
  const update = (overrides = {}, amount = 0) => overlay.update(state(overrides), amount, camera, 1280, 800);
  const instruments = [];
  overlay.root.traverse(object => { if (object.name.startsWith('instrument-') && object.name !== 'instrument-motion') instruments.push(object); });
  const contact = instruments[0].parent;
  const snapshot = () => {
    scene.updateMatrixWorld(true);
    const objects = [];
    overlay.root.traverse(object => objects.push({
      id: object.uuid, matrix: [...object.matrixWorld.elements], visible: object.visible,
      opacity: object.material?.opacity, emissiveIntensity: object.material?.emissiveIntensity,
      geometry: object.geometry?.uuid, material: object.material?.uuid,
    }));
    return JSON.stringify(objects);
  };

  await test('頭頸基準穴課堂集合完整且可篩選體表點', () => {
    const lesson = curriculumSet('head-neck-21');
    const practice = curriculumSet('common-10');
    assert.ok(lesson);
    assert.ok(practice);
    assert.equal(lesson.codes.length, 21);
    assert.equal(practice.codes.length, 10);
    assert.equal(lesson.status, 'reviewed');
    assert.equal(practice.status, 'pending');
    assert.equal(new Set(lesson.codes).size, 21);
    for (const code of lesson.codes) {
      const point = POINTS.find(p => p.code === code);
      assert.ok(point, `缺少基準穴 ${code}`);
      assert.match(point.positionProvenance, /課堂核對通過/);
      assert.equal(point.reviewed, true);
    }
    for (const code of practice.codes) {
      const point = POINTS.find(p => p.code === code);
      assert.ok(point, `缺少常用穴 ${code}`);
      assert.equal(point.reviewed, false);
    }
    assert.equal(CURRICULUM_SETS[0].id, 'head-neck-21');
    update({curriculumSet: 'head-neck-21', showPoints: true, meridianFilter: 'all'});
    const visible = overlay.markers.filter(marker => marker.visible);
    const expected = lesson.codes.map(code => POINTS.find(p => p.code === code)).filter(p => p && !p.surfaceUnavailable)
      .reduce((total, point) => total + (point.midline ? 1 : 2), 0);
    assert.equal(visible.length, expected);
    for (const marker of visible) assert.ok(lesson.codes.includes(marker.userData.point));
    update({curriculumSet: 'all', showPoints: true, meridianFilter: 'all'});
  });

  await test('穴位數據及左右鏡像、中線單實例', () => {
    assert.equal(new Set(POINTS.map(point => point.code)).size, POINTS.length);
    assert.equal(overlay.markers.length, POINTS.filter(p=>!p.surfaceUnavailable).reduce((total, point) => total + (point.midline ? 1 : 2), 0));
    for (const point of POINTS) {
      finiteVector(point.position); finiteVector(point.normal);
      assert.ok(new T.Vector3().fromArray(point.normal).length() > 0);
      const right = pointTarget(point.code, 1), left = pointTarget(point.code, -1);
      near(left.position[0], point.midline ? right.position[0] : -right.position[0]);
      near(left.position[1], right.position[1]); near(left.position[2], right.position[2]);
      near(left.normal[0], -right.normal[0]);
      const dots = overlay.markers.filter(dot => dot.userData.point === point.code);
      assert.equal(dots.length, point.surfaceUnavailable ? 0 : point.midline ? 1 : 2);
      if (point.midline) near(right.position[0], 0);
      for (const dot of dots) assert.deepEqual(dot.position.toArray(), pointTarget(point.code, dot.userData.side).position);
    }
    console.log(`  ${POINTS.length} 個穴名，${overlay.markers.length} 個體表實例。`);
  });

  await test('六種器具切換後僅選中器具可見', () => {
    assert.equal(TOOLS.length, 6); assert.equal(instruments.length, 6);
    for (const tool of TOOLS) {
      const response = update({tool: tool.id, elapsed: 1.7});
      assert.ok(response);
      const visible = instruments.filter(effectivelyVisible);
      assert.equal(visible.length, 1);
      assert.equal(visible[0].name, `instrument-${tool.id}`);
      const box = new T.Box3().setFromObject(visible[0]);
      finiteVector([...box.min.toArray(), ...box.max.toArray()]);
      assert.ok(!box.isEmpty());
    }
  });

  await test('六工具拖動命中列表僅包含當前可見器具幾何', () => {
    const mainPart = {needle: 'needle-shaft', moxa: 'moxa-paper', stone: 'polished-stone', finger: 'hand-palm', scraper: 'jade-scraper', cup: 'glass-cup-open-mouth'};
    for (const selected of TOOLS) {
      for (const elapsed of [0, 1.7]) {
        update({tool: selected.id, elapsed, effects: true});
        const currentInstrument = instruments.find(object => object.name === `instrument-${selected.id}`);
        const candidates = overlay.getDraggables(selected.id);
        assert.ok(candidates.length > 0, `${selected.id} 缺少可拖動幾何`);
        assert.equal(new Set(candidates).size, candidates.length, '命中列表包含重複對象');
        assert.ok(candidates.some(object => object.name === mainPart[selected.id]), `${selected.id} 器具主體無法抓取`);
        for (const object of candidates) {
          assert.ok(object instanceof T.Mesh, '拖動列表包含非幾何組對象');
          assert.ok(effectivelyVisible(object), `${object.name} 不可見卻仍可命中`);
          let belongsToCurrent = false;
          for (let ancestor = object; ancestor; ancestor = ancestor.parent) if (ancestor === currentInstrument) belongsToCurrent = true;
          assert.ok(belongsToCurrent, `${object.name} 不屬於當前工具`);
        }
        for (const other of TOOLS.filter(tool => tool.id !== selected.id)) {
          assert.deepEqual(overlay.getDraggables(other.id), [], `隱藏的 ${other.id} 仍參與命中`);
        }
        if (selected.id === 'cup' && elapsed === 0) {
          assert.ok(candidates.every(object => object.name !== 'contact-indicator'), '隱藏的杯口印痕仍可命中');
        }
      }
    }
  });

  await test('手動刮痧移到新位置後，舊痕跡保持原體表位置與朝向', () => {
    const trailScene = new T.Scene(), trailOverlay = createTeachingOverlay(trailScene);
    const trail = trailOverlay.root.getObjectByName('manual-scrape-trail');
    assert.ok(trail, '缺少手動刮痧軌跡層');
    assert.equal(trail.children.length, 128);
    const firstTarget = {position: [0.1, 1.1, 0.08], normal: [0, 0, 1]};
    const nextTarget = {position: [0.16, 1.06, 0.1], normal: [0, 1, 0]};
    const dragState = state({tool: 'scraper', pointCode: null, customTarget: firstTarget, dragging: true, running: true, elapsed: 1.2, effects: true});
    trailOverlay.update(dragState, 0, camera, 1280, 800);
    trailScene.updateMatrixWorld(true);
    const firstMark = trail.children.find(effectivelyVisible);
    assert.ok(firstMark, '首次刮動未記錄色痕');
    const originalMatrix = [...firstMark.matrixWorld.elements];
    const expectedFirst = new T.Vector3().fromArray(firstTarget.position).addScaledVector(new T.Vector3().fromArray(firstTarget.normal), 0.001);
    near(firstMark.getWorldPosition(new T.Vector3()).distanceTo(expectedFirst), 0);
    trailOverlay.update({...dragState, customTarget: nextTarget, elapsed: 1.5}, 0, camera, 1280, 800);
    trailScene.updateMatrixWorld(true);
    assert.deepEqual([...firstMark.matrixWorld.elements], originalMatrix, '舊痕跡隨新工具位置或朝向漂移');
    const visible = trail.children.filter(effectivelyVisible);
    assert.equal(visible.length, 2, '兩個相隔體表位置未分別記錄');
    const nextMark = visible.find(object => object !== firstMark);
    const expectedNext = new T.Vector3().fromArray(nextTarget.position).addScaledVector(new T.Vector3().fromArray(nextTarget.normal), 0.001);
    near(nextMark.getWorldPosition(new T.Vector3()).distanceTo(expectedNext), 0);
    near(new T.Vector3(0, 0, 1).applyQuaternion(nextMark.getWorldQuaternion(new T.Quaternion())).distanceTo(new T.Vector3().fromArray(nextTarget.normal)), 0);
  });

  await test('刮痧鬆開暫停保留軌跡，重播清空並從新位置重新記錄', () => {
    const trailScene = new T.Scene(), trailOverlay = createTeachingOverlay(trailScene);
    const trail = trailOverlay.root.getObjectByName('manual-scrape-trail');
    const target = x => ({position: [x, 1, 0.1], normal: [0, 0, 1]});
    const run = overrides => trailOverlay.update(state({tool: 'scraper', pointCode: null, customTarget: target(0.1), elapsed: 2, effects: true, ...overrides}), 0, camera, 1280, 800);
    run({dragging: true, running: true});
    run({dragging: true, running: true, customTarget: target(0.12)});
    trailScene.updateMatrixWorld(true);
    const before = trail.children.filter(effectivelyVisible).map(mesh => ({id: mesh.uuid, matrix: [...mesh.matrixWorld.elements]}));
    assert.equal(before.length, 2);
    for (let frame = 0; frame < 20; frame++) run({dragging: false, running: false, release: 1, customTarget: target(0.12)});
    trailScene.updateMatrixWorld(true);
    assert.deepEqual(trail.children.filter(effectivelyVisible).map(mesh => ({id: mesh.uuid, matrix: [...mesh.matrixWorld.elements]})), before, '鬆開暫停時軌跡消失、增生或漂移');
    run({dragging: false, running: true, elapsed: 0, customTarget: target(0.12)});
    assert.equal(trail.children.filter(effectivelyVisible).length, 0, '重播未清空軌跡');
    run({dragging: true, running: true, elapsed: 0.1, customTarget: target(0.2)});
    trailScene.updateMatrixWorld(true);
    const fresh = trail.children.filter(effectivelyVisible);
    assert.equal(fresh.length, 1, '重播後恢復了舊軌跡');
    near(fresh[0].getWorldPosition(new T.Vector3()).distanceTo(new T.Vector3(0.2, 1, 0.101)), 0);
  });

  await test('長距離手動刮痧複用 128 個軌跡對象，暫停不繼續寫入', () => {
    const trailOverlay = createTeachingOverlay(new T.Scene());
    const trail = trailOverlay.root.getObjectByName('manual-scrape-trail');
    const before = trail.children.map(mesh => [mesh.uuid, mesh.geometry.uuid, mesh.material.uuid]);
    for (let i = 0; i < 150; i++) {
      trailOverlay.update(state({tool: 'scraper', pointCode: null, customTarget: {position: [i * 0.005, 1, 0.1], normal: [0, 0, 1]}, elapsed: 1 + i * 0.1, dragging: true, running: true}), 0, camera, 1280, 800);
    }
    assert.equal(trail.children.filter(effectivelyVisible).length, 128);
    assert.deepEqual(trail.children.map(mesh => [mesh.uuid, mesh.geometry.uuid, mesh.material.uuid]), before, '拖動過程中創建了新軌跡資源');
    const positions = trail.children.map(mesh => mesh.position.toArray());
    trailOverlay.update(state({tool: 'scraper', pointCode: null, customTarget: {position: [0.9, 1, 0.1], normal: [0, 0, 1]}, elapsed: 16, dragging: false, running: false}), 0, camera, 1280, 800);
    assert.deepEqual(trail.children.map(mesh => mesh.position.toArray()), positions, '非拖動狀態仍記錄軌跡');
  });

  await test('清空體表目標時立即清除舊刮痕，重新選點不恢復舊軌跡', () => {
    const trailOverlay = createTeachingOverlay(new T.Scene());
    const trail = trailOverlay.root.getObjectByName('manual-scrape-trail');
    const target = {position: [0.1, 1, 0.1], normal: [0, 0, 1]};
    const active = state({tool: 'scraper', pointCode: null, customTarget: target, elapsed: 1, dragging: true, running: true});
    trailOverlay.update(active, 0, camera, 1280, 800);
    assert.equal(trail.children.filter(effectivelyVisible).length, 1);
    const response = trailOverlay.update({...active, customTarget: null, elapsed: 0, dragging: false, running: false}, 0, camera, 1280, 800);
    assert.equal(response, null);
    assert.equal(trail.visible, false);
    assert.ok(trail.children.every(mesh => !mesh.visible), '無目標早返回跳過了軌跡清理');
    trailOverlay.update({...active, dragging: false, running: false}, 0, camera, 1280, 800);
    assert.equal(trail.visible, true);
    assert.equal(trail.children.filter(effectivelyVisible).length, 0, '重新選點時舊刮痕再次出現');
    trailOverlay.update(active, 0, camera, 1280, 800);
    assert.equal(trail.children.filter(effectivelyVisible).length, 1, '重新操作未從清空狀態開始記錄');
  });

  await test('暫停後固定 elapsed，矩陣和視覺狀態保持不變', () => {
    for (const tool of TOOLS) {
      const paused = state({tool: tool.id, running: false, elapsed: 2.375, amplitude: 0.8});
      const beforeState = JSON.stringify(paused);
      overlay.update(paused, 0, camera, 1280, 800);
      const before = snapshot();
      for (let frame = 0; frame < 20; frame++) overlay.update(paused, 0, camera, 1280, 800);
      assert.equal(snapshot(), before, `${tool.id} 暫停時仍發生變化`);
      assert.equal(JSON.stringify(paused), beforeState, '更新函數修改了調用方的模擬狀態');
    }
  });

  await test('鬆開 token 觸發回彈衰減；普通暫停保持凍結（虛擬時鐘）', () => {
    const performanceObject = globalThis.performance;
    const originalNow = Object.getOwnPropertyDescriptor(performanceObject, 'now');
    let clockMs = 1000;
    Object.defineProperty(performanceObject, 'now', {configurable: true, value: () => clockMs});
    try {
      for (const tool of TOOLS) {
        const releaseOverlay = createTeachingOverlay(new T.Scene());
        const motion = releaseOverlay.root.getObjectByName(`instrument-${tool.id}`).getObjectByName('instrument-motion');
        const readMatrix = () => { motion.updateMatrix(); return [...motion.matrix.elements]; };
        const updateRelease = overrides => releaseOverlay.update(state({
          tool: tool.id, elapsed: 2.375, amplitude: 0.8,
          running: false, effects: true, release: 0, ...overrides,
        }), 0, camera, 1280, 800);
        // Same elapsed for rest, held pause, and release: only the release clock advances.
        const rest = updateRelease({amplitude: 0});
        const restMatrix = readMatrix();
        near(rest.deform, 0); near(rest.activity, 0);
        const held = updateRelease({});
        const heldMatrix = readMatrix();
        assert.notDeepEqual(heldMatrix, restMatrix, `${tool.id} 未產生可觀察的按住狀態`);
        for (const jump of [100, 1000, 10000]) {
          clockMs += jump;
          const paused = updateRelease({});
          near(paused.deform, held.deform); near(paused.activity, held.activity);
          assert.equal(paused.settling, false);
          assert.deepEqual(readMatrix(), heldMatrix, `${tool.id} 普通暫停被誤當作鬆開`);
        }
        const started = updateRelease({release: 1});
        const releaseStartMs = clockMs;
        assert.equal(started.settling, true);
        near(started.activity, held.activity); near(started.deform, held.deform);
        const matrixDistance = matrix => Math.hypot(...matrix.map((value, index) => value - restMatrix[index]));
        let previousActivity = started.activity;
        let previousDeform = Math.abs(started.deform);
        let previousDistance = matrixDistance(readMatrix());
        for (const offsetMs of [100, 300, 600, 790]) {
          clockMs = releaseStartMs + offsetMs;
          const released = updateRelease({release: 1});
          assert.equal(released.settling, true, `${tool.id} 回彈完成前停止請求繪製`);
          assert.ok(released.activity > 0 && released.activity < previousActivity, `${tool.id} 活動未逐步衰減`);
          assert.ok(Math.abs(released.deform) <= previousDeform, `${tool.id} 鬆開時形變反而增大`);
          if (Math.abs(held.deform) > 0) assert.ok(Math.abs(released.deform) < previousDeform);
          const distance = matrixDistance(readMatrix());
          assert.ok(distance < previousDistance, `${tool.id} 器具未逐步回到靜止基線`);
          previousActivity = released.activity; previousDeform = Math.abs(released.deform); previousDistance = distance;
        }
        clockMs = releaseStartMs + 900;
        const settled = updateRelease({release: 1});
        near(settled.activity, 0); near(settled.deform, 0);
        assert.equal(settled.settling, false);
        assert.deepEqual(readMatrix(), restMatrix, `${tool.id} 鬆開完成後未回到靜止基線`);
        clockMs += 5000;
        const settledLater = updateRelease({release: 1});
        near(settledLater.activity, 0); near(settledLater.deform, 0);
        assert.equal(settledLater.settling, false);
        assert.deepEqual(readMatrix(), restMatrix, `${tool.id} 同一 release token 重複觸發回彈`);
      }
    } finally {
      if (originalNow) Object.defineProperty(performanceObject, 'now', originalNow);
      else delete performanceObject.now;
    }
  });

  await test('播放期間器具會運動且複用原有幾何與材質', () => {
    const resources = () => {
      const result = [];
      for (const instrument of instruments) instrument.traverse(object => {
        if (object.geometry) result.push(`g:${object.geometry.uuid}`);
        for (const surface of object.material ? (Array.isArray(object.material) ? object.material : [object.material]) : []) result.push(`m:${surface.uuid}`);
      });
      return result.sort();
    };
    const before = resources();
    for (const tool of TOOLS) {
      const instrument = instruments.find(object => object.name === `instrument-${tool.id}`);
      const motion = instrument.getObjectByName('instrument-motion');
      assert.ok(motion);
      update({tool: tool.id, running: true, elapsed: 0.4, amplitude: 1});
      motion.updateMatrix(); const first = [...motion.matrix.elements];
      update({tool: tool.id, running: true, elapsed: 1.3, amplitude: 1});
      motion.updateMatrix(); assert.notDeepEqual([...motion.matrix.elements], first, `${tool.id} 播放時沒有運動`);
      for (let frame = 0; frame < 12; frame++) update({tool: tool.id, running: true, elapsed: 1.3 + frame / 60, amplitude: 1});
      assert.deepEqual(resources(), before, `${tool.id} 播放時替換了幾何或材質`);
      assert.deepEqual(instrument.position.toArray(), [0, 0, 0], `${tool.id} 改變了接觸座標系的原點`);
    }
  });

  await test('效果關閉後皮膚形變、擴散環和刮痕均關閉', () => {
    for (const tool of TOOLS) {
      const response = update({tool: tool.id, effects: false, elapsed: 2, amplitude: 1});
      assert.equal(response.deform, 0); assert.equal(response.activity, 0);
      for (const object of contact.children) {
        if (!object.isMesh) continue;
        const isPatch = object.geometry.type === 'CircleGeometry';
        const isStroke = object.geometry.type === 'PlaneGeometry';
        const isPulse = object.geometry.type === 'RingGeometry' && object.geometry.parameters.innerRadius >= 0.018;
        if (isPatch || isStroke || isPulse) near(object.material.opacity, 0);
      }
    }
  });

  await test('效果關閉後火罐接觸效果也關閉', () => {
    update({tool: 'cup', effects: false, elapsed: 2, amplitude: 1});
    const indicator = overlay.root.getObjectByName('contact-indicator');
    assert.ok(indicator, '未找到火罐接觸效果對象');
    assert.ok(!effectivelyVisible(indicator) || indicator.material.opacity === 0, 'effects=false 時火罐 contact-indicator 仍然可見');
  });

  await test('拆解狀態隱藏教學層、標籤和皮膚效果', () => {
    for (const amount of [0.002, 0.2, 0.8, 1]) {
      const response = update({tool: 'finger', elapsed: 2, amplitude: 1}, amount);
      assert.equal(overlay.root.visible, false);
      assert.equal(overlay.label.hidden, true);
      assert.ok(instruments.every(object => !effectivelyVisible(object)));
      assert.equal(response.deform, 0); assert.equal(response.activity, 0);
    }
    update({}, 0);
    assert.equal(overlay.root.visible, true);
  });

  await test('自選接觸點優先、朝向正確，無選點時清空接觸層', () => {
    const customTarget = {position: [0.1, 1.1, 0.09], normal: [0, 1, 0], meshId: 'test-surface'};
    const response = update({pointCode: 'ST36', customTarget});
    assert.equal(response.target, customTarget);
    assert.deepEqual(contact.position.toArray(), customTarget.position);
    const direction = new T.Vector3(0, 0, 1).applyQuaternion(contact.quaternion);
    near(direction.distanceTo(new T.Vector3(...customTarget.normal)), 0);
    assert.equal(update({pointCode: null, customTarget: null}), null);
    assert.equal(contact.visible, false); assert.equal(overlay.label.hidden, true);
  });

  const {loadChunkBuffer} = await import(pathToFileURL(join(project, 'scripts/load-chunk.mjs')));
  const atlas = JSON.parse(await readFile(join(project, 'public/models/atlas.json'), 'utf8'));
  const skinPart = atlas.parts.find(part => part.id === 'FJ2810');
  assert.ok(skinPart, 'atlas 缺少 FJ2810 皮膚網格');
  const chunk = atlas.chunks[skinPart.chunk];
  const raw = loadChunkBuffer(chunk, project);
  const buffer = raw.buffer.slice(raw.byteOffset, raw.byteOffset + raw.byteLength);
  const geometry = new T.BufferGeometry();
  geometry.setAttribute('position', new T.BufferAttribute(new Float32Array(buffer, skinPart.positions, skinPart.vertexCount * 3), 3));
  geometry.setIndex(new T.BufferAttribute(new Uint32Array(buffer, skinPart.indices, skinPart.indexCount), 1));
  softenPelvicSurface(geometry);geometry.computeBoundingBox(); geometry.computeBoundingSphere();
  // Match scene.tsx's CPU picking mesh (FrontSide, identity transform).
  const skin = new T.Mesh(geometry, new T.MeshBasicMaterial());
  skin.updateMatrixWorld(true);
  const anchoredScene = new T.Scene(), anchored = createTeachingOverlay(anchoredScene);
  anchored.anchor(skin);
  const positions = geometry.getAttribute('position'), indices = geometry.index;
  const triangle = new T.Triangle(), closest = new T.Vector3(), vertexA = new T.Vector3(), vertexB = new T.Vector3(), vertexC = new T.Vector3();
  const readTriangle = face => {
    vertexA.fromBufferAttribute(positions, indices.getX(face * 3));
    vertexB.fromBufferAttribute(positions, indices.getX(face * 3 + 1));
    vertexC.fromBufferAttribute(positions, indices.getX(face * 3 + 2));
    return triangle.set(vertexA, vertexB, vertexC);
  };
  const nearestSurface = seed => {
    const query = new T.Vector3().fromArray(seed.position);
    let distanceSquared = Infinity, faceIndex = -1;
    for (let face = 0; face < indices.count / 3; face++) {
      readTriangle(face).closestPointToPoint(query, closest);
      const distance = closest.distanceToSquared(query);
      if (distance < distanceSquared) { distanceSquared = distance; faceIndex = face; }
    }
    return {distanceMm: +(Math.sqrt(distanceSquared) * 1000).toFixed(3), faceIndex};
  };
  const missing = [];
  const anchoredTargets = [];
  for (const marker of anchored.markers) {
    const {point: pointCode, side} = marker.userData;
    const target = anchored.getTarget(state({pointCode, side}));
    if (!target?.meshId || !Number.isInteger(target.faceIndex)) {
      const seed = pointTarget(pointCode, side), normal = new T.Vector3().fromArray(seed.normal).normalize(), point = new T.Vector3().fromArray(seed.position);
      const ray = new T.Raycaster(point.clone().addScaledVector(normal, 0.18), normal.clone().negate(), 0, 0.36);
      missing.push({code: pointCode, side, nearestSurface: nearestSurface(seed), normalRayHitsMm: ray.intersectObject(skin, false).slice(0, 3).map(hit => +(hit.point.distanceTo(point) * 1000).toFixed(3))});
    } else anchoredTargets.push({marker, target, pointCode, side});
  }
  await test('十四經全量目錄、篩選、經脈開關及口內例外', () => {
    assert.equal(POINTS.length,364);assert.equal(new Set(POINTS.map(p=>p.meridianId)).size,15);
    assert.equal(POINTS.filter(p=>p.code==='GV24+').length,1);
    assert.equal(anchored.getTarget(state({pointCode:'GV28'})),null);
    const lines=anchored.root.getObjectByName('meridian-paths');assert.equal(lines.children.length,26);
    for(const line of lines.children){const a=line.geometry.getAttribute('position');assert.ok(a.count>0);for(const n of a.array)assert.ok(Number.isFinite(n));for(let i=0;i<a.count;i+=2)assert.ok(new T.Vector3().fromBufferAttribute(a,i).distanceTo(new T.Vector3().fromBufferAttribute(a,i+1))<.025001);}
    anchored.update(state({meridianFilter:'LU'}),0,camera,1280,800);
    assert.equal(anchored.markers.filter(m=>m.visible).length,22);
    assert.equal(lines.children.filter(l=>l.visible).length,2);
    anchored.update(state({showMeridians:false}),0,camera,1280,800);assert.equal(lines.visible,false);
  });
  console.log(`實際皮膚射線吸附：${anchoredTargets.length}/${anchored.markers.length} 個返回網格元數據；${skinPart.vertexCount} 頂點，${skinPart.indexCount / 3} 三角面。`);
  if (missing.length) console.log(`未吸附清單（距真實網格最近距離，僅為幾何診斷）：\n${JSON.stringify(missing, null, 2)}`);
  const mismatchedTargets = anchoredTargets.flatMap(({marker, target, pointCode, side}) => {
    const expected = new T.Vector3().fromArray(target.position).addScaledVector(new T.Vector3().fromArray(target.normal), 0.003);
    const difference = marker.position.distanceTo(expected);
    return difference > 1e-6 ? [{code: pointCode, side, markerPosition: marker.position.toArray(), targetPosition: target.position, differenceMm: +(difference * 1000).toFixed(3), nearestSurface: nearestSurface(pointTarget(pointCode, side))}] : [];
  });
  if (mismatchedTargets.length) console.log(`標記與所返回吸附位置不一致（檢查是否跨側回退）：\n${JSON.stringify(mismatchedTargets, null, 2)}`);
  console.log(`網格元數據與對應標記一致：${anchoredTargets.length - mismatchedTargets.length}/${anchored.markers.length}。`);

  await test('已吸附點位於真實皮膚三角面、法向朝外且座標未越界', () => {
    assert.ok(anchoredTargets.length > 0, '無吸附成功實例');
    for (const {marker, target, pointCode, side} of anchoredTargets) {
      assert.equal(target.meshId, 'FJ2810');
      assert.ok(target.faceIndex >= 0 && target.faceIndex < indices.count / 3);
      finiteVector(target.position); finiteVector(target.normal);
      const point = new T.Vector3().fromArray(target.position), normal = new T.Vector3().fromArray(target.normal);
      near(normal.length(), 1, 1e-6);
      assert.ok(normal.dot(new T.Vector3().fromArray(pointTarget(pointCode, side).normal)) >= -1e-7);
      assert.ok(geometry.boundingBox.clone().expandByScalar(1e-6).containsPoint(point), `${pointCode}:${side} 超出皮膚包圍盒`);
      readTriangle(target.faceIndex).closestPointToPoint(point, closest);
      assert.ok(closest.distanceTo(point) < 1e-5, `${pointCode}:${side} 不在所報告的三角面上`);
      assert.ok(marker.position.distanceTo(point.clone().addScaledVector(normal, 0.003)) < 1e-6, `${pointCode}:${side} 標記與吸附結果不一致，詳見診斷清單`);
    }
  });

  await test('全部體表實例都有真實網格 faceIndex（失敗項已列出）', () => {
    assert.equal(missing.length, 0, `未吸附：${missing.map(item => `${item.code}:${item.side}`).join(', ')}`);
  });

  await test('課堂篩選時工具只吸附基準穴', () => {
    const lesson = curriculumSet('head-neck-21');
    anchored.update(state({curriculumSet: 'head-neck-21', showPoints: true, meridianFilter: 'all'}), 0, camera, 1280, 800);
    const seed = pointTarget('GV20', 1);
    const snapped = anchored.snapTarget({position: seed.position, normal: seed.normal});
    assert.equal(snapped.pointCode, 'GV20');
    const outsider = pointTarget('ST36', 1);
    const kept = anchored.snapTarget({position: outsider.position, normal: outsider.normal});
    assert.equal(kept.pointCode, undefined);
    assert.ok(!lesson.codes.includes('ST36'));
    anchored.update(state({curriculumSet: 'all', showPoints: true, meridianFilter: 'all'}), 0, camera, 1280, 800);
  });

  await test('工具接近穴位時吸附並返回穴位身份，遠離或反面不誤選', () => {
    for (const {target, pointCode, side} of anchoredTargets) {
      const snapped = anchored.snapTarget({...target});
      assert.equal(snapped.pointCode, pointCode);
      assert.equal(snapped.pointSide, side);
      assert.deepEqual(snapped.position, target.position);
    }
    const sample = anchoredTargets.find(item => item.pointCode === 'ST36' && item.side === 1);
    assert.ok(sample);
    const position = new T.Vector3().fromArray(sample.target.position).addScaledVector(new T.Vector3().fromArray(sample.target.normal), .03);
    const distant = {...sample.target, position: position.toArray(), pointCode: undefined, pointSide: undefined};
    assert.equal(anchored.snapTarget(distant), distant);
    const reversed = {...sample.target, normal: new T.Vector3().fromArray(sample.target.normal).negate().toArray(), pointCode: undefined, pointSide: undefined};
    assert.equal(anchored.snapTarget(reversed), reversed);
  });

  await test('中線負側選擇仍使用唯一中線吸附結果', () => {
    for (const point of POINTS.filter(point => point.midline)) {
      assert.deepEqual(anchored.getTarget(state({pointCode: point.code, side: -1})), anchored.getTarget(state({pointCode: point.code, side: 1})));
    }
  });

  await test('內側腿部自選點聚焦後不被對側肢體遮擋', () => {
    const customTarget = {
      position: [-0.0084947466, 0.6785437577, -0.0242707046],
      normal: [0.9963364111, -0.0847959882, -0.0111083863],
      meshId: 'FJ2810', faceIndex: 21701,
    };
    const targetPosition = new T.Vector3().fromArray(customTarget.position);
    const focusCamera = new T.PerspectiveCamera(34, 1.6, 0.005, 100);
    focusCamera.position.set(1.4, 1.05, 3.6);
    focusCamera.lookAt(0, 0.85, 0);
    focusCamera.updateMatrixWorld(true);
    const controls = {
      target: new T.Vector3(0, 0.85, 0),
      update() { focusCamera.lookAt(this.target); focusCamera.updateMatrixWorld(true); },
    };
    const focusOverlay = createTeachingOverlay(new T.Scene());
    focusOverlay.anchor(skin);
    assert.equal(focusOverlay.focus(state({pointCode: null, customTarget, focus: 1}), focusCamera, controls, 0), true);
    near(controls.target.distanceTo(targetPosition), 0);
    finiteVector(focusCamera.position.toArray());
    const direction = targetPosition.clone().sub(focusCamera.position);
    const distance = direction.length();
    const viewRay = new T.Raycaster(focusCamera.position.clone(), direction.normalize(), 0, distance + 0.02);
    const firstHit = viewRay.intersectObject(skin, false)[0];
    assert.ok(firstHit, '聚焦後的視線未命中所選皮膚');
    const occlusionGap = firstHit.point.distanceTo(targetPosition);
    // 12 mm is solely a visibility tolerance for this rendering regression.
    // It is not an assessment of acupuncture or medical point-location accuracy.
    assert.ok(occlusionGap <= 0.012, `視線先命中 face ${firstHit.faceIndex}，距所選表面 ${(occlusionGap * 1000).toFixed(3)} mm；疑似對側肢體遮擋`);
    console.log(`  聚焦視線首個命中距所選表面 ${(occlusionGap * 1000).toFixed(3)} mm（僅遮擋檢查）。`);
  });

  await test('吸附重複調用不累計偏移；聚焦請求只執行一次', () => {
    const before = anchored.markers.map(marker => marker.position.toArray());
    anchored.anchor(skin);
    assert.deepEqual(anchored.markers.map(marker => marker.position.toArray()), before);
    const controls = {target: new T.Vector3(), update() {}};
    const focusState = state({focus: 1});
    assert.equal(anchored.focus(focusState, camera, controls, 0.5), false);
    assert.equal(anchored.focus(focusState, camera, controls, 0), true);
    assert.equal(anchored.focus(focusState, camera, controls, 0), false);
    assert.equal(anchored.focus({...focusState, focus: 2}, camera, controls, 0), true);
    finiteVector(camera.position.toArray());
  });
  await test('疼痛關聯只使用已收錄且有來源的穴位，局部遠端不重疊',()=>{
    for(const c of PAIN_CASES){assert.ok(c.source.startsWith('https://'));const codes=[...c.local,...c.distal];assert.equal(new Set(codes).size,codes.length);assert.ok(codes.length<=8);for(const code of codes)assert.ok(POINTS.some(p=>p.code===code));}
    assert.equal(matchPainRegion([0,1.05,.1]),null);assert.equal(matchPainRegion([.3,.8,0]),null);assert.equal(matchPainRegion([NaN,1.6,0]),null);assert.equal(matchPainRegion([.04,1.1,-.1]),'back');
  });
  await test('疼痛關聯有已校正穴時優先顯示已校正座標',()=>{
    const head=painRelationCodes(PAIN_CASES.find(c=>c.id==='head'));
    assert.deepEqual(head.local,['GV20','EX-HN5','GV24+']);
    assert.ok(head.local.every(code=>POINTS.find(p=>p.code===code).reviewed));
    assert.deepEqual(preferReviewedCodes(['LI4','GV20']),['GV20']);
    assert.deepEqual(preferReviewedCodes(['LI4','ST36']),['LI4','ST36']);
    const neck=painRelationCodes(PAIN_CASES.find(c=>c.id==='neck'));
    assert.deepEqual(neck.local,['ST9','CV23']);
    const knee=painRelationCodes(PAIN_CASES.find(c=>c.id==='knee'));
    assert.equal(knee.local.length+knee.distal.length,7);
  });
  await test('四組疼痛示例的左右八處標記均綁定實際皮膚三角面',()=>{
    for(const c of PAIN_CASES)for(const side of [1,-1]){const pain={topic:c.id,side,target:null,level:.5,showLinks:true};const target=anchored.getTarget(state({pointCode:null,customTarget:null,pain}));assert.ok(Number.isInteger(target.faceIndex));const p=new T.Vector3().fromArray(target.position);readTriangle(target.faceIndex).closestPointToPoint(p,closest);assert.ok(closest.distanceTo(p)<1e-5);assert.equal(Math.sign(target.position[0]),side);}
  });
  await test('選擇遠端穴位或拖動工具不移動原痛處、不自動降低模擬痛感',()=>{
    const pain={topic:'back',side:1,target:null,level:.7,showLinks:true};anchored.update(state({pain,pointCode:null}),0,camera,1280,720);const root=anchored.root.getObjectByName('pain-relationships'),marker=root.getObjectByName('pain-location'),before=marker.position.clone(),scale=marker.scale.clone(),opacity=marker.children[0].material.opacity;
    for(const tool of TOOLS){anchored.update(state({pain,tool:tool.id,pointCode:'BL40',elapsed:60,running:true,customTarget:{position:[.1,.4,.03],normal:[0,0,1]}}),0,camera,1280,720);near(marker.position.distanceTo(before),0);near(marker.scale.distanceTo(scale),0);near(marker.children[0].material.opacity,opacity);}
  });
  await test('關係線開關、未知區域、解剖模式和退出會清理關係展示',()=>{
    const pain={topic:'knee',side:-1,target:null,level:.5,showLinks:true};anchored.update(state({pain}),0,camera,1280,720);const root=anchored.root.getObjectByName('pain-relationships'),lines=root.children.filter(o=>o instanceof T.Line);assert.equal(lines.filter(l=>l.visible).length,7);
    anchored.update(state({pain:{...pain,showLinks:false}}),0,camera,1280,720);assert.ok(lines.every(l=>!l.visible));assert.equal(root.visible,true);
    anchored.update(state({pain:{...pain,topic:null,target:{position:[0,1,.1],normal:[0,0,1]}}}),0,camera,1280,720);assert.ok(lines.every(l=>!l.visible));
    anchored.update(state({pain,pickMode:'anatomy'}),0,camera,1280,720);assert.equal(root.visible,false);
    anchored.update(state({}),0,camera,1280,720);assert.equal(root.visible,false);
  });
  console.log(`教學驗證完成：${passed} 項通過，${failed} 項失敗。未進行臨床精度或瀏覽器渲染認證。`);
  if (failed) process.exitCode = 1;
} finally {
  if (previousDocument) Object.defineProperty(globalThis, 'document', previousDocument);
  else delete globalThis.document;
  await rm(temporaryDir, {recursive: true, force: true});
  if (createdValidationDir) {
    // Do not remove another process's validation files if it reused this directory.
    const {rmdir} = await import('node:fs/promises');
    try { await rmdir(validationDir); } catch (error) { if (error.code !== 'ENOTEMPTY' && error.code !== 'ENOENT') throw error; }
  }
}
