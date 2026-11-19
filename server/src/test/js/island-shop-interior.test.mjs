import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createInterior, HALL_NAME } from '../../main/resources/public/js/island-shop-interior.js';

const here = dirname(fileURLToPath(import.meta.url));
const source = readFileSync(join(here, '..', '..', 'main', 'resources', 'public/js/island-shop-interior.js'), 'utf8');

test('飲料店室內源碼：神之島招牌、無莫卡、無 PointLight', () => {
  assert.match(source, /神之島/);
  assert.equal(HALL_NAME, '神之島');
  assert.doesNotMatch(source, /莫卡/);
  assert.doesNotMatch(source, /new THREE\.PointLight/);
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
  class Group {
    constructor() { this.children = []; this.position = { set() {}, x: 0, y: 0, z: 0 }; }
    add(obj) { this.children.push(obj); }
  }
  class Object3D { constructor() { this.position = { set() {} }; this.matrix = {}; } updateMatrix() {} }
  return {
    Color, Group, Object3D, Mesh,
    MeshLambertMaterial: Material,
    BoxGeometry: class extends Geometry {},
    CylinderGeometry: class extends Geometry {},
    SphereGeometry: class extends Geometry {},
    CanvasTexture: class { constructor() { this.dispose = () => {}; } },
    SRGBColorSpace: 'srgb',
    sRGBEncoding: 3001
  };
}

test('飲料店室內每份材質都有日夜色，dispose 不會炸掉', () => {
  const THREE = stubTHREE();
  const view = createInterior(THREE, { reducedMotion: true, groundY: 0.2 });
  assert.ok(view.group);
  assert.ok(view.materials.length > 8);
  assert.equal(view.lamp, undefined);
  for (const m of view.materials) {
    assert.ok(m.userData && m.userData.day && Number.isFinite(m.userData.day.r), 'material 要有 userData.day');
    assert.ok(m.userData.night, 'material 要有 userData.night');
  }
  view.dispose();
});
