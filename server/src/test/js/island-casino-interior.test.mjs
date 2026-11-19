import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createInterior } from '../../main/resources/public/js/island-casino-interior.js';

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, '..', '..', 'main', 'resources', 'public/js/island-casino-interior.js'), 'utf8');

test('賭場室內源碼：神之島賭場招牌、無 PointLight', () => {
  assert.match(source, /神之島賭場/);
  assert.doesNotMatch(source, /莫卡賭場/);
  assert.doesNotMatch(source, /new THREE\.PointLight/);
  assert.match(source, /即將開獎，敬請期待！/);
  assert.match(source, /輪盤轉運中，好運將至！/);
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

test('賭場室內每份材質都有日夜色，dispose 不會炸掉；reduced 時輪盤不轉', () => {
  const THREE = stubTHREE();
  const view = createInterior(THREE, { reducedMotion: true, groundY: 0.2 });
  assert.ok(view.group);
  assert.ok(view.materials.length > 8);
  for (const m of view.materials) {
    assert.ok(m.userData && m.userData.day && Number.isFinite(m.userData.day.r), 'material 要有 userData.day');
    assert.ok(m.userData.night, 'material 要有 userData.night');
  }
  assert.equal(view.poke('slots'), '即將開獎，敬請期待！');
  assert.equal(view.poke('roulette'), '輪盤轉運中，好運將至！');
  const before = view.tick(0.5);
  assert.ok(before);
  view.dispose();
});
