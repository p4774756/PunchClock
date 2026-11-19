// 海空生物：node --test server/src/test/js/island-life.test.mjs
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
  'main',
  'resources',
  'public'
);

const life = await loadPublicModule('island-life.js');
const {
  LIFE_MAX_CREATURES,
  LIFE_SEA_Y,
  LIFE_SKY_BASE,
  validateLifePlacements,
  lifePlacementAnchors,
  lifePlacementAllowed,
  lifeInstanceBudget,
  lifeRareKindAt,
  lifeGlowStrength,
  lifeKindActive,
  lifeCreatureGlow,
  lifeUpdateTick,
  createIslandLife,
  sampleFishSchool,
  distanceToBoatRoutes,
  LIFE_GLB_KINDS,
  lifeModelPath,
  LIFE_CREATURE_KINDS,
  validateLifePanoramaGlbPlacements,
  LIFE_PANORAMA_CAMERA,
  LIFE_PANORAMA_VIEWPORT
} = life;

function mockThreeLite() {
  return {
    FrontSide: 0,
    Group: class {
      constructor() {
        this.children = [];
        this.position = { x: 0, y: 0, z: 0, set(x, y, z) { this.x = x; this.y = y; this.z = z; } };
        this.rotation = { x: 0, y: 0, z: 0, set(x, y, z) { this.x = x; this.y = y; this.z = z; } };
        this.visible = true;
        this.userData = {};
        this.scale = { setScalar() {} };
      }
      add(c) { this.children.push(c); }
      remove(c) {
        const i = this.children.indexOf(c);
        if (i >= 0) this.children.splice(i, 1);
      }
      traverse(fn) { fn(this); this.children.forEach(fn); }
    },
    MeshLambertMaterial: class {
      constructor(opts) {
        Object.assign(this, opts);
        this.userData = { dayE: 0, nightE: 0 };
        this.emissiveIntensity = 0;
      }
      dispose() {}
    },
    Mesh: class {
      constructor(geo, mat) {
        this.geometry = geo;
        this.material = mat;
        this.position = { set() {} };
        this.rotation = { set() {} };
        this.scale = { set() {}, setScalar() {} };
        this.children = [];
        this.userData = {};
        this.visible = true;
      }
      add(c) { this.children.push(c); }
    },
    SphereGeometry: class { dispose() {} },
    BoxGeometry: class { dispose() {} },
    CylinderGeometry: class { dispose() {} },
    ConeGeometry: class { dispose() {} },
    Color: class {
      constructor(v) { this.v = v; }
      clone() { return new this.constructor(this.v); }
      lerp() {}
    }
  };
}

describe('island-life 錨點', () => {
  test('全部錨點通過放置檢查', () => {
    const v = validateLifePlacements();
    assert.equal(v.ok, true, v.problems.join('; '));
  });

  test('錨點數量不超過上限', () => {
    assert.ok(lifePlacementAnchors().length <= LIFE_MAX_CREATURES);
  });

  test('每個錨點不在可行走區與航線上', () => {
    for (const a of lifePlacementAnchors()) {
      assert.ok(lifePlacementAllowed(a.x, a.z), `${a.kind} @ ${a.x},${a.z}`);
      assert.ok(distanceToBoatRoutes(a.x, a.z) >= 3.2);
    }
  });
});

describe('island-life reduced', () => {
  test('reduced 時預算縮小', () => {
    const b = lifeInstanceBudget(true, false);
    assert.equal(b.rare, 0);
    assert.ok(b.schools <= 1);
  });

  test('reduced 時稀有輪播停止', () => {
    assert.equal(lifeRareKindAt(100, true), null);
  });

  test('reduced 時夜間發光為 0', () => {
    assert.equal(lifeGlowStrength('jellyfish', 1, true), 0);
    assert.equal(lifeGlowStrength('phoenix', 1, true), 0);
  });

  test('reduced 時 update tick 不播動畫', () => {
    const tick = lifeUpdateTick(true, 0.016, 10);
    assert.equal(tick.animate, false);
    assert.equal(tick.dt, 0);
  });

  test('reduced 時神話生物不啟用', () => {
    for (const kind of ['kunpeng', 'kitsune', 'xuanwu', 'mermaid']) {
      assert.equal(lifeKindActive(kind, 1, true, 200, {}), false);
    }
  });
});

describe('island-life 日夜', () => {
  test('水母夜間發光標記', () => {
    assert.ok(lifeCreatureGlow('jellyfish'));
    assert.ok(lifeCreatureGlow('phoenix'));
    assert.equal(lifeCreatureGlow('fishSchool'), false);
  });

  test('水母白天不發光、夜間有強度', () => {
    assert.equal(lifeGlowStrength('jellyfish', 0.2, false), 0);
    assert.ok(lifeGlowStrength('jellyfish', 0.9, false) > 0.3);
  });

  test('夜晚鳳凰可見、白天不可', () => {
    assert.equal(lifeKindActive('phoenix', 0.3, false, 0, {}), false);
    assert.equal(lifeKindActive('phoenix', 0.85, false, 0, {}), true);
  });

  test('魚群與鳥群白天常駐', () => {
    assert.equal(lifeKindActive('fishSchool', 0.1, false, 0, {}), true);
    assert.equal(lifeKindActive('birds', 0.1, false, 0, {}), true);
  });
});

describe('island-life 稀有一輪一隻', () => {
  test('同一時刻只啟用一種稀有', () => {
    const active = new Set();
    for (let e = 0; e < 500; e += 12) {
      active.add(lifeRareKindAt(e, false));
    }
    assert.ok(active.size > 3, '稀有輪播應涵蓋多種');
    for (let e = 0; e < 200; e += 0.5) {
      let count = 0;
      const rare = lifeRareKindAt(e, false);
      for (const a of lifePlacementAnchors()) {
        if (a.kind === rare) count++;
      }
      assert.ok(count <= 2);
    }
  });

  test('forceAll 示範時全部可見', () => {
    assert.equal(lifeKindActive('kunpeng', 0, false, 0, { forceAll: true }), true);
  });

  test('forceRare 示範可鎖定單一傳說物', () => {
    assert.equal(lifeKindActive('xuanwu', 0, false, 0, { forceRare: 'xuanwu' }), true);
    assert.equal(lifeKindActive('kunpeng', 0, false, 0, { forceRare: 'xuanwu' }), false);
    assert.equal(lifeKindActive('whale', 0, false, 0, { forceRare: 'xuanwu' }), false);
    assert.equal(lifeKindActive('fishSchool', 0.1, false, 0, { forceRare: 'whale' }), true);
    assert.equal(lifeKindActive('phoenix', 0.9, false, 0, { forceRare: 'whale' }), false);
  });
});

describe('island-life 純函式', () => {
  test('魚群路徑循環', () => {
    const anchor = { x: -14.5, z: 20.5, radius: 2.6 };
    const p0 = sampleFishSchool(anchor, 0, false);
    const p1 = sampleFishSchool(anchor, 10, false);
    assert.notStrictEqual(p0.x, p1.x);
    assert.ok(Math.hypot(p0.x - anchor.x, p0.z - anchor.z) <= anchor.radius + 0.01);
  });

  test('海面高度常數合理', () => {
    assert.ok(LIFE_SEA_Y < LIFE_SKY_BASE);
    assert.ok(LIFE_SEA_Y < -0.5);
  });
});

describe('island-life 全景投影 (#226 r2)', () => {
  test('GLB 錨點在 1280×720 全景內且避開島標籤與 HUD', () => {
    const v = validateLifePanoramaGlbPlacements();
    assert.equal(v.ok, true, v.problems.join('; '));
  });

  test('投影函式使用指定相機參數', () => {
    assert.equal(LIFE_PANORAMA_CAMERA.eye.x, 58.1);
    assert.equal(LIFE_PANORAMA_VIEWPORT.width, 1280);
    assert.equal(LIFE_PANORAMA_CAMERA.fovDeg, 25);
  });
});

describe('island-life GLB 種類 (#226)', () => {
  test('每個 GLB 種類皆有對應模型檔', () => {
    assert.equal(LIFE_GLB_KINDS.length, 7);
    for (const kind of LIFE_GLB_KINDS) {
      const path = lifeModelPath(kind);
      assert.ok(path && path.startsWith('/models/life/'), kind + ' → ' + path);
      assert.ok(existsSync(join(publicRoot, path)), 'missing ' + path);
    }
    const removed = ['chiru', 'seaSerpent', 'qingniao', 'phoenix', 'wyvern'];
    for (const kind of removed) {
      assert.equal(lifeModelPath(kind), null, kind + ' 不應有 GLB');
    }
  });
});

describe('island-life createIslandLife', () => {
  test('建立與 dispose 不拋錯', () => {
    const THREE = mockThreeLite();
    const view = createIslandLife(THREE, { reduced: false, procedural: true });
    assert.ok(view.group);
    assert.ok(view.creatures.length > 0);
    view.update(0.016, 1);
    view.setMix(0.8);
    view.dispose();
    assert.equal(view.creatures.length, 0);
  });

  test('reduced 時 update 不拋錯', () => {
    const THREE = mockThreeLite();
    const view = createIslandLife(THREE, { reduced: true, procedural: true });
    for (let i = 0; i < 30; i++) {
      view.update(0.016, i * 0.016);
    }
    view.dispose();
  });
});