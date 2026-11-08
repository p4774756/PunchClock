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
  try { await run(); passed++; console.log(`通过：${name}`); }
  catch (error) { failed++; console.error(`失败：${name}\n${error.message}`); }
};
const near = (actual, expected, tolerance = 1e-7) =>
  assert.ok(Math.abs(actual - expected) <= tolerance, `${actual} ≠ ${expected}（容差 ${tolerance}）`);
const finiteVector = values => assert.ok(values.every(Number.isFinite), '向量中出现非有限数值');
const effectivelyVisible = object => {
  for (let current = object; current; current = current.parent) if (!current.visible) return false;
  return true;
};

try {
  const bundlePath = join(temporaryDir, 'entry.mjs');
  const bundle = await build({
    stdin: {
      contents: "export {createTeachingOverlay} from './app/teaching-overlay'; export {softenPelvicSurface} from './app/teaching-skin'; export {POINTS,TOOLS,pointTarget,initialSimulation} from './app/teaching'; export {PAIN_CASES,matchPainRegion} from './app/pain-relations';",
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
  const {createTeachingOverlay, softenPelvicSurface, POINTS, TOOLS, pointTarget, initialSimulation,PAIN_CASES,matchPainRegion} = await import(pathToFileURL(bundlePath));
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

  await test('穴位数据及左右镜像、中线单实例', () => {
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
    console.log(`  ${POINTS.length} 个穴名，${overlay.markers.length} 个体表实例。`);
  });

  await test('六种器具切换后仅选中器具可见', () => {
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

  await test('六工具拖动命中列表仅包含当前可见器具几何', () => {
    const mainPart = {needle: 'needle-shaft', moxa: 'moxa-paper', stone: 'polished-stone', finger: 'hand-palm', scraper: 'jade-scraper', cup: 'glass-cup-open-mouth'};
    for (const selected of TOOLS) {
      for (const elapsed of [0, 1.7]) {
        update({tool: selected.id, elapsed, effects: true});
        const currentInstrument = instruments.find(object => object.name === `instrument-${selected.id}`);
        const candidates = overlay.getDraggables(selected.id);
        assert.ok(candidates.length > 0, `${selected.id} 缺少可拖动几何`);
        assert.equal(new Set(candidates).size, candidates.length, '命中列表包含重复对象');
        assert.ok(candidates.some(object => object.name === mainPart[selected.id]), `${selected.id} 器具主体无法抓取`);
        for (const object of candidates) {
          assert.ok(object instanceof T.Mesh, '拖动列表包含非几何组对象');
          assert.ok(effectivelyVisible(object), `${object.name} 不可见却仍可命中`);
          let belongsToCurrent = false;
          for (let ancestor = object; ancestor; ancestor = ancestor.parent) if (ancestor === currentInstrument) belongsToCurrent = true;
          assert.ok(belongsToCurrent, `${object.name} 不属于当前工具`);
        }
        for (const other of TOOLS.filter(tool => tool.id !== selected.id)) {
          assert.deepEqual(overlay.getDraggables(other.id), [], `隐藏的 ${other.id} 仍参与命中`);
        }
        if (selected.id === 'cup' && elapsed === 0) {
          assert.ok(candidates.every(object => object.name !== 'contact-indicator'), '隐藏的杯口印痕仍可命中');
        }
      }
    }
  });

  await test('手动刮痧移到新位置后，旧痕迹保持原体表位置与朝向', () => {
    const trailScene = new T.Scene(), trailOverlay = createTeachingOverlay(trailScene);
    const trail = trailOverlay.root.getObjectByName('manual-scrape-trail');
    assert.ok(trail, '缺少手动刮痧轨迹层');
    assert.equal(trail.children.length, 128);
    const firstTarget = {position: [0.1, 1.1, 0.08], normal: [0, 0, 1]};
    const nextTarget = {position: [0.16, 1.06, 0.1], normal: [0, 1, 0]};
    const dragState = state({tool: 'scraper', pointCode: null, customTarget: firstTarget, dragging: true, running: true, elapsed: 1.2, effects: true});
    trailOverlay.update(dragState, 0, camera, 1280, 800);
    trailScene.updateMatrixWorld(true);
    const firstMark = trail.children.find(effectivelyVisible);
    assert.ok(firstMark, '首次刮动未记录色痕');
    const originalMatrix = [...firstMark.matrixWorld.elements];
    const expectedFirst = new T.Vector3().fromArray(firstTarget.position).addScaledVector(new T.Vector3().fromArray(firstTarget.normal), 0.001);
    near(firstMark.getWorldPosition(new T.Vector3()).distanceTo(expectedFirst), 0);
    trailOverlay.update({...dragState, customTarget: nextTarget, elapsed: 1.5}, 0, camera, 1280, 800);
    trailScene.updateMatrixWorld(true);
    assert.deepEqual([...firstMark.matrixWorld.elements], originalMatrix, '旧痕迹随新工具位置或朝向漂移');
    const visible = trail.children.filter(effectivelyVisible);
    assert.equal(visible.length, 2, '两个相隔体表位置未分别记录');
    const nextMark = visible.find(object => object !== firstMark);
    const expectedNext = new T.Vector3().fromArray(nextTarget.position).addScaledVector(new T.Vector3().fromArray(nextTarget.normal), 0.001);
    near(nextMark.getWorldPosition(new T.Vector3()).distanceTo(expectedNext), 0);
    near(new T.Vector3(0, 0, 1).applyQuaternion(nextMark.getWorldQuaternion(new T.Quaternion())).distanceTo(new T.Vector3().fromArray(nextTarget.normal)), 0);
  });

  await test('刮痧松开暂停保留轨迹，重播清空并从新位置重新记录', () => {
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
    assert.deepEqual(trail.children.filter(effectivelyVisible).map(mesh => ({id: mesh.uuid, matrix: [...mesh.matrixWorld.elements]})), before, '松开暂停时轨迹消失、增生或漂移');
    run({dragging: false, running: true, elapsed: 0, customTarget: target(0.12)});
    assert.equal(trail.children.filter(effectivelyVisible).length, 0, '重播未清空轨迹');
    run({dragging: true, running: true, elapsed: 0.1, customTarget: target(0.2)});
    trailScene.updateMatrixWorld(true);
    const fresh = trail.children.filter(effectivelyVisible);
    assert.equal(fresh.length, 1, '重播后恢复了旧轨迹');
    near(fresh[0].getWorldPosition(new T.Vector3()).distanceTo(new T.Vector3(0.2, 1, 0.101)), 0);
  });

  await test('长距离手动刮痧复用 128 个轨迹对象，暂停不继续写入', () => {
    const trailOverlay = createTeachingOverlay(new T.Scene());
    const trail = trailOverlay.root.getObjectByName('manual-scrape-trail');
    const before = trail.children.map(mesh => [mesh.uuid, mesh.geometry.uuid, mesh.material.uuid]);
    for (let i = 0; i < 150; i++) {
      trailOverlay.update(state({tool: 'scraper', pointCode: null, customTarget: {position: [i * 0.005, 1, 0.1], normal: [0, 0, 1]}, elapsed: 1 + i * 0.1, dragging: true, running: true}), 0, camera, 1280, 800);
    }
    assert.equal(trail.children.filter(effectivelyVisible).length, 128);
    assert.deepEqual(trail.children.map(mesh => [mesh.uuid, mesh.geometry.uuid, mesh.material.uuid]), before, '拖动过程中创建了新轨迹资源');
    const positions = trail.children.map(mesh => mesh.position.toArray());
    trailOverlay.update(state({tool: 'scraper', pointCode: null, customTarget: {position: [0.9, 1, 0.1], normal: [0, 0, 1]}, elapsed: 16, dragging: false, running: false}), 0, camera, 1280, 800);
    assert.deepEqual(trail.children.map(mesh => mesh.position.toArray()), positions, '非拖动状态仍记录轨迹');
  });

  await test('清空体表目标时立即清除旧刮痕，重新选点不恢复旧轨迹', () => {
    const trailOverlay = createTeachingOverlay(new T.Scene());
    const trail = trailOverlay.root.getObjectByName('manual-scrape-trail');
    const target = {position: [0.1, 1, 0.1], normal: [0, 0, 1]};
    const active = state({tool: 'scraper', pointCode: null, customTarget: target, elapsed: 1, dragging: true, running: true});
    trailOverlay.update(active, 0, camera, 1280, 800);
    assert.equal(trail.children.filter(effectivelyVisible).length, 1);
    const response = trailOverlay.update({...active, customTarget: null, elapsed: 0, dragging: false, running: false}, 0, camera, 1280, 800);
    assert.equal(response, null);
    assert.equal(trail.visible, false);
    assert.ok(trail.children.every(mesh => !mesh.visible), '无目标早返回跳过了轨迹清理');
    trailOverlay.update({...active, dragging: false, running: false}, 0, camera, 1280, 800);
    assert.equal(trail.visible, true);
    assert.equal(trail.children.filter(effectivelyVisible).length, 0, '重新选点时旧刮痕再次出现');
    trailOverlay.update(active, 0, camera, 1280, 800);
    assert.equal(trail.children.filter(effectivelyVisible).length, 1, '重新操作未从清空状态开始记录');
  });

  await test('暂停后固定 elapsed，矩阵和视觉状态保持不变', () => {
    for (const tool of TOOLS) {
      const paused = state({tool: tool.id, running: false, elapsed: 2.375, amplitude: 0.8});
      const beforeState = JSON.stringify(paused);
      overlay.update(paused, 0, camera, 1280, 800);
      const before = snapshot();
      for (let frame = 0; frame < 20; frame++) overlay.update(paused, 0, camera, 1280, 800);
      assert.equal(snapshot(), before, `${tool.id} 暂停时仍发生变化`);
      assert.equal(JSON.stringify(paused), beforeState, '更新函数修改了调用方的模拟状态');
    }
  });

  await test('松开 token 触发回弹衰减；普通暂停保持冻结（虚拟时钟）', () => {
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
        assert.notDeepEqual(heldMatrix, restMatrix, `${tool.id} 未产生可观察的按住状态`);
        for (const jump of [100, 1000, 10000]) {
          clockMs += jump;
          const paused = updateRelease({});
          near(paused.deform, held.deform); near(paused.activity, held.activity);
          assert.equal(paused.settling, false);
          assert.deepEqual(readMatrix(), heldMatrix, `${tool.id} 普通暂停被误当作松开`);
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
          assert.equal(released.settling, true, `${tool.id} 回弹完成前停止请求绘制`);
          assert.ok(released.activity > 0 && released.activity < previousActivity, `${tool.id} 活动未逐步衰减`);
          assert.ok(Math.abs(released.deform) <= previousDeform, `${tool.id} 松开时形变反而增大`);
          if (Math.abs(held.deform) > 0) assert.ok(Math.abs(released.deform) < previousDeform);
          const distance = matrixDistance(readMatrix());
          assert.ok(distance < previousDistance, `${tool.id} 器具未逐步回到静止基线`);
          previousActivity = released.activity; previousDeform = Math.abs(released.deform); previousDistance = distance;
        }
        clockMs = releaseStartMs + 900;
        const settled = updateRelease({release: 1});
        near(settled.activity, 0); near(settled.deform, 0);
        assert.equal(settled.settling, false);
        assert.deepEqual(readMatrix(), restMatrix, `${tool.id} 松开完成后未回到静止基线`);
        clockMs += 5000;
        const settledLater = updateRelease({release: 1});
        near(settledLater.activity, 0); near(settledLater.deform, 0);
        assert.equal(settledLater.settling, false);
        assert.deepEqual(readMatrix(), restMatrix, `${tool.id} 同一 release token 重复触发回弹`);
      }
    } finally {
      if (originalNow) Object.defineProperty(performanceObject, 'now', originalNow);
      else delete performanceObject.now;
    }
  });

  await test('播放期间器具会运动且复用原有几何与材质', () => {
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
      motion.updateMatrix(); assert.notDeepEqual([...motion.matrix.elements], first, `${tool.id} 播放时没有运动`);
      for (let frame = 0; frame < 12; frame++) update({tool: tool.id, running: true, elapsed: 1.3 + frame / 60, amplitude: 1});
      assert.deepEqual(resources(), before, `${tool.id} 播放时替换了几何或材质`);
      assert.deepEqual(instrument.position.toArray(), [0, 0, 0], `${tool.id} 改变了接触坐标系的原点`);
    }
  });

  await test('效果关闭后皮肤形变、扩散环和刮痕均关闭', () => {
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

  await test('效果关闭后火罐接触效果也关闭', () => {
    update({tool: 'cup', effects: false, elapsed: 2, amplitude: 1});
    const indicator = overlay.root.getObjectByName('contact-indicator');
    assert.ok(indicator, '未找到火罐接触效果对象');
    assert.ok(!effectivelyVisible(indicator) || indicator.material.opacity === 0, 'effects=false 时火罐 contact-indicator 仍然可见');
  });

  await test('拆解状态隐藏教学层、标签和皮肤效果', () => {
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

  await test('自选接触点优先、朝向正确，无选点时清空接触层', () => {
    const customTarget = {position: [0.1, 1.1, 0.09], normal: [0, 1, 0], meshId: 'test-surface'};
    const response = update({pointCode: 'ST36', customTarget});
    assert.equal(response.target, customTarget);
    assert.deepEqual(contact.position.toArray(), customTarget.position);
    const direction = new T.Vector3(0, 0, 1).applyQuaternion(contact.quaternion);
    near(direction.distanceTo(new T.Vector3(...customTarget.normal)), 0);
    assert.equal(update({pointCode: null, customTarget: null}), null);
    assert.equal(contact.visible, false); assert.equal(overlay.label.hidden, true);
  });

  const atlas = JSON.parse(await readFile(join(project, 'public/models/atlas.json'), 'utf8'));
  const skinPart = atlas.parts.find(part => part.id === 'FJ2810');
  assert.ok(skinPart, 'atlas 缺少 FJ2810 皮肤网格');
  const chunk = atlas.chunks[skinPart.chunk];
  const raw = await readFile(join(project, 'public', chunk.url));
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
  await test('十四经全量目录、筛选、经脉开关及口内例外', () => {
    assert.equal(POINTS.length,362);assert.equal(new Set(POINTS.map(p=>p.meridianId)).size,14);
    assert.equal(POINTS.filter(p=>p.code==='GV24+').length,1);
    assert.equal(anchored.getTarget(state({pointCode:'GV28'})),null);
    const lines=anchored.root.getObjectByName('meridian-paths');assert.equal(lines.children.length,26);
    for(const line of lines.children){const a=line.geometry.getAttribute('position');assert.ok(a.count>0);for(const n of a.array)assert.ok(Number.isFinite(n));for(let i=0;i<a.count;i+=2)assert.ok(new T.Vector3().fromBufferAttribute(a,i).distanceTo(new T.Vector3().fromBufferAttribute(a,i+1))<.025001);}
    anchored.update(state({meridianFilter:'LU'}),0,camera,1280,800);
    assert.equal(anchored.markers.filter(m=>m.visible).length,22);
    assert.equal(lines.children.filter(l=>l.visible).length,2);
    anchored.update(state({showMeridians:false}),0,camera,1280,800);assert.equal(lines.visible,false);
  });
  console.log(`实际皮肤射线吸附：${anchoredTargets.length}/${anchored.markers.length} 个返回网格元数据；${skinPart.vertexCount} 顶点，${skinPart.indexCount / 3} 三角面。`);
  if (missing.length) console.log(`未吸附清单（距真实网格最近距离，仅为几何诊断）：\n${JSON.stringify(missing, null, 2)}`);
  const mismatchedTargets = anchoredTargets.flatMap(({marker, target, pointCode, side}) => {
    const expected = new T.Vector3().fromArray(target.position).addScaledVector(new T.Vector3().fromArray(target.normal), 0.003);
    const difference = marker.position.distanceTo(expected);
    return difference > 1e-6 ? [{code: pointCode, side, markerPosition: marker.position.toArray(), targetPosition: target.position, differenceMm: +(difference * 1000).toFixed(3), nearestSurface: nearestSurface(pointTarget(pointCode, side))}] : [];
  });
  if (mismatchedTargets.length) console.log(`标记与所返回吸附位置不一致（检查是否跨侧回退）：\n${JSON.stringify(mismatchedTargets, null, 2)}`);
  console.log(`网格元数据与对应标记一致：${anchoredTargets.length - mismatchedTargets.length}/${anchored.markers.length}。`);

  await test('已吸附点位于真实皮肤三角面、法向朝外且坐标未越界', () => {
    assert.ok(anchoredTargets.length > 0, '无吸附成功实例');
    for (const {marker, target, pointCode, side} of anchoredTargets) {
      assert.equal(target.meshId, 'FJ2810');
      assert.ok(target.faceIndex >= 0 && target.faceIndex < indices.count / 3);
      finiteVector(target.position); finiteVector(target.normal);
      const point = new T.Vector3().fromArray(target.position), normal = new T.Vector3().fromArray(target.normal);
      near(normal.length(), 1, 1e-6);
      assert.ok(normal.dot(new T.Vector3().fromArray(pointTarget(pointCode, side).normal)) >= -1e-7);
      assert.ok(geometry.boundingBox.clone().expandByScalar(1e-6).containsPoint(point), `${pointCode}:${side} 超出皮肤包围盒`);
      readTriangle(target.faceIndex).closestPointToPoint(point, closest);
      assert.ok(closest.distanceTo(point) < 1e-5, `${pointCode}:${side} 不在所报告的三角面上`);
      assert.ok(marker.position.distanceTo(point.clone().addScaledVector(normal, 0.003)) < 1e-6, `${pointCode}:${side} 标记与吸附结果不一致，详见诊断清单`);
    }
  });

  await test('全部体表实例都有真实网格 faceIndex（失败项已列出）', () => {
    assert.equal(missing.length, 0, `未吸附：${missing.map(item => `${item.code}:${item.side}`).join(', ')}`);
  });

  await test('工具接近穴位时吸附并返回穴位身份，远离或反面不误选', () => {
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

  await test('中线负侧选择仍使用唯一中线吸附结果', () => {
    for (const point of POINTS.filter(point => point.midline)) {
      assert.deepEqual(anchored.getTarget(state({pointCode: point.code, side: -1})), anchored.getTarget(state({pointCode: point.code, side: 1})));
    }
  });

  await test('内侧腿部自选点聚焦后不被对侧肢体遮挡', () => {
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
    assert.ok(firstHit, '聚焦后的视线未命中所选皮肤');
    const occlusionGap = firstHit.point.distanceTo(targetPosition);
    // 12 mm is solely a visibility tolerance for this rendering regression.
    // It is not an assessment of acupuncture or medical point-location accuracy.
    assert.ok(occlusionGap <= 0.012, `视线先命中 face ${firstHit.faceIndex}，距所选表面 ${(occlusionGap * 1000).toFixed(3)} mm；疑似对侧肢体遮挡`);
    console.log(`  聚焦视线首个命中距所选表面 ${(occlusionGap * 1000).toFixed(3)} mm（仅遮挡检查）。`);
  });

  await test('吸附重复调用不累计偏移；聚焦请求只执行一次', () => {
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
  await test('疼痛关联只使用已收录且有来源的穴位，局部远端不重叠',()=>{
    for(const c of PAIN_CASES){assert.ok(c.source.startsWith('https://'));const codes=[...c.local,...c.distal];assert.equal(new Set(codes).size,codes.length);assert.ok(codes.length<=8);for(const code of codes)assert.ok(POINTS.some(p=>p.code===code));}
    assert.equal(matchPainRegion([0,1.05,.1]),null);assert.equal(matchPainRegion([.3,.8,0]),null);assert.equal(matchPainRegion([NaN,1.6,0]),null);assert.equal(matchPainRegion([.04,1.1,-.1]),'back');
  });
  await test('四组疼痛示例的左右八处标记均绑定实际皮肤三角面',()=>{
    for(const c of PAIN_CASES)for(const side of [1,-1]){const pain={topic:c.id,side,target:null,level:.5,showLinks:true};const target=anchored.getTarget(state({pointCode:null,customTarget:null,pain}));assert.ok(Number.isInteger(target.faceIndex));const p=new T.Vector3().fromArray(target.position);readTriangle(target.faceIndex).closestPointToPoint(p,closest);assert.ok(closest.distanceTo(p)<1e-5);assert.equal(Math.sign(target.position[0]),side);}
  });
  await test('选择远端穴位或拖动工具不移动原痛处、不自动降低模拟痛感',()=>{
    const pain={topic:'back',side:1,target:null,level:.7,showLinks:true};anchored.update(state({pain,pointCode:null}),0,camera,1280,720);const root=anchored.root.getObjectByName('pain-relationships'),marker=root.getObjectByName('pain-location'),before=marker.position.clone(),scale=marker.scale.clone(),opacity=marker.children[0].material.opacity;
    for(const tool of TOOLS){anchored.update(state({pain,tool:tool.id,pointCode:'BL40',elapsed:60,running:true,customTarget:{position:[.1,.4,.03],normal:[0,0,1]}}),0,camera,1280,720);near(marker.position.distanceTo(before),0);near(marker.scale.distanceTo(scale),0);near(marker.children[0].material.opacity,opacity);}
  });
  await test('关系线开关、未知区域、解剖模式和退出会清理关系展示',()=>{
    const pain={topic:'knee',side:-1,target:null,level:.5,showLinks:true};anchored.update(state({pain}),0,camera,1280,720);const root=anchored.root.getObjectByName('pain-relationships'),lines=root.children.filter(o=>o instanceof T.Line);assert.equal(lines.filter(l=>l.visible).length,7);
    anchored.update(state({pain:{...pain,showLinks:false}}),0,camera,1280,720);assert.ok(lines.every(l=>!l.visible));assert.equal(root.visible,true);
    anchored.update(state({pain:{...pain,topic:null,target:{position:[0,1,.1],normal:[0,0,1]}}}),0,camera,1280,720);assert.ok(lines.every(l=>!l.visible));
    anchored.update(state({pain,pickMode:'anatomy'}),0,camera,1280,720);assert.equal(root.visible,false);
    anchored.update(state({}),0,camera,1280,720);assert.equal(root.visible,false);
  });
  console.log(`教学验证完成：${passed} 项通过，${failed} 项失败。未进行临床精度或浏览器渲染认证。`);
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
