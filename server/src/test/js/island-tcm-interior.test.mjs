import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createInterior, todayExhibitModel, HALL_NAME, STUDY_LABEL, BRONZE_POINTS_URL } from '../../main/resources/public/js/island-tcm-interior.js';
import { pickTodayCode } from '../../main/resources/public/js/island-tcm-figure-logic.js';

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, '..', '..', 'main', 'resources', 'public/js/island-tcm-interior.js'), 'utf8');
const records = JSON.parse(readFileSync(join(here, '..', '..', 'main', 'resources', 'public/island/tcm-bronze-points.json'), 'utf8'));

test('推拿館室內源碼：無 PointLight，有醫館與經絡研習室文案', () => {
  assert.match(source, new RegExp(HALL_NAME));
  assert.match(source, /經絡研習室/);
  assert.match(source, /銅人在這/);
  assert.match(source, /tick/);
  assert.match(source, /今日一穴/);
  assert.match(source, /百子櫃|drawer/);
  assert.doesNotMatch(source, /new THREE\.PointLight/);
  assert.equal(STUDY_LABEL, '進入經絡研習室');
  assert.equal(BRONZE_POINTS_URL, '/island/tcm-bronze-points.json');
});

test('今日一穴室內展示與戶外同一選穴', () => {
  const now = Date.parse('2026-11-19T04:00:00Z');
  const outdoor = pickTodayCode(records, now);
  const model = todayExhibitModel(records, now);
  assert.equal(model.title, '今日一穴');
  assert.ok(model.subtitle.includes(outdoor));
  const record = records.find((row) => row.code === outdoor);
  assert.ok(record);
  assert.ok(model.subtitle.includes(record.name));
});

function stubTHREE() {
  const Color = class {
    constructor(v) { this.v = v; this.r = 1; }
    copy() { return this; }
  };
  class Material {
    constructor(opts) {
      Object.assign(this, opts);
      this.userData = {};
      this.emissive = opts && opts.emissive ? opts.emissive : new Color(0);
      this.emissiveIntensity = (opts && opts.emissiveIntensity) || 0;
    }
    dispose() {}
  }
  class Geometry { dispose() {} }
  class Mesh {
    constructor(geo, mat) { this.geometry = geo; this.material = mat; this.position = { set() {}, x: 0, y: 0, z: 0 }; this.rotation = { x: 0, y: 0, z: 0 }; this.castShadow = false; this.receiveShadow = false; }
  }
  class InstancedMesh extends Mesh {
    constructor(geo, mat, count) { super(geo, mat); this.count = count; this.instanceMatrix = { needsUpdate: false }; }
    setMatrixAt() {}
  }
  class Group {
    constructor() { this.children = []; this.position = { set() {}, x: 0, y: 0, z: 0 }; }
    add(obj) { this.children.push(obj); }
  }
  class Object3D { constructor() { this.position = { set() {} }; this.matrix = {}; } updateMatrix() {} }
  return {
    Color, Group, Object3D, Mesh, InstancedMesh,
    MeshLambertMaterial: Material,
    BoxGeometry: class extends Geometry {},
    CylinderGeometry: class extends Geometry {},
    SphereGeometry: class extends Geometry {},
    CanvasTexture: class { constructor() { this.dispose = () => {}; } },
    SRGBColorSpace: 'srgb',
    sRGBEncoding: 3001
  };
}

test('推拿館室內每份材質都有日夜色，dispose 不會炸掉', () => {
  const THREE = stubTHREE();
  const view = createInterior(THREE, { reducedMotion: true, groundY: 0.2, todayRecords: records, nowMs: Date.parse('2026-11-19T04:00:00Z') });
  assert.ok(view.group);
  assert.ok(view.materials.length > 8);
  for (const m of view.materials) {
    assert.ok(m.userData && m.userData.day && Number.isFinite(m.userData.day.r), 'material 要有 userData.day');
    assert.ok(m.userData.night, 'material 要有 userData.night');
  }
  view.dispose();
});
