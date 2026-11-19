import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const L = await loadPublicModule('island-lab-interior.js');

function stubThree() {
  class Color {
    constructor(c) {
      this.c = c;
      this.r = 1;
      this.g = 1;
      this.b = 1;
    }
  }
  class Material {
    constructor(params = {}) {
      Object.assign(this, params);
      this.userData = {};
      this.emissiveIntensity = params.emissiveIntensity || 0;
    }
    dispose() {}
    clone() {
      const copy = new Material({
        map: this.map,
        emissive: this.emissive,
        emissiveMap: this.emissiveMap,
        emissiveIntensity: this.emissiveIntensity,
        flatShading: this.flatShading,
        transparent: this.transparent,
        color: this.color
      });
      copy.userData = {
        day: this.userData.day,
        night: this.userData.night,
        dayE: this.userData.dayE,
        nightE: this.userData.nightE
      };
      return copy;
    }
  }
  class Geometry { dispose() {} }
  class Object3D {
    constructor() {
      this.position = { set() {} };
      this.rotation = { x: 0, y: 0, z: 0 };
      this.children = [];
      this.matrix = {};
      this.castShadow = false;
      this.receiveShadow = false;
    }
    add(child) {
      this.children.push(child);
    }
    updateMatrix() {}
  }
  class Mesh extends Object3D {
    constructor(geometry, material) {
      super();
      this.geometry = geometry;
      this.material = material;
    }
  }
  class InstancedMesh extends Mesh {
    constructor(geometry, material, count) {
      super(geometry, material);
      this.count = count;
      this.instanceMatrix = { needsUpdate: false };
    }
    setMatrixAt() {}
  }
  class Group extends Object3D {}
  class CanvasTexture {
    constructor(canvas) {
      this.canvas = canvas;
      this.needsUpdate = false;
      this.generateMipmaps = false;
      this.minFilter = 0;
      this.magFilter = 0;
      this.anisotropy = 1;
    }
    dispose() {}
  }
  return {
    Color,
    MeshLambertMaterial: Material,
    BoxGeometry: Geometry,
    PlaneGeometry: Geometry,
    Mesh,
    Group,
    InstancedMesh,
    Object3D,
    CanvasTexture,
    LinearMipmapLinearFilter: 1,
    LinearFilter: 2
  };
}

function installDomStub() {
  const ctx = {
    fillStyle: '',
    font: '',
    textAlign: '',
    textBaseline: '',
    fillRect() {},
    fillText() {},
    measureText(text) {
      return { width: String(text || '').length * 8 };
    },
    createLinearGradient() {
      return { addColorStop() {} };
    }
  };
  globalThis.document = {
    createElement() {
      return { width: 0, height: 0, getContext() { return ctx; } };
    }
  };
}

test('研發樓室內每份材質都有日夜色，進房不會在 color.copy 炸掉', () => {
  installDomStub();
  const THREE = stubThree();
  const view = L.createInterior(THREE, { groundY: 0.2, reducedMotion: true });
  assert.ok(view && view.group && Array.isArray(view.materials));
  assert.ok(view.materials.length > 8, '應該組出大廳材質');
  for (let i = 0; i < view.materials.length; i++) {
    const material = view.materials[i];
    assert.ok(material.userData && material.userData.day && Number.isFinite(material.userData.day.r),
      'materials[' + i + '] 缺少 userData.day');
    assert.ok(material.userData.night && Number.isFinite(material.userData.night.r),
      'materials[' + i + '] 缺少 userData.night');
    assert.equal(typeof material.userData.dayE, 'number');
    assert.equal(typeof material.userData.nightE, 'number');
  }
  view.dispose();
});
