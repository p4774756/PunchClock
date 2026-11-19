// node --test server/src/test/js/island-peers.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const Peers = await loadPublicModule('island-peers.js');

test('同儕共用貼圖池會載入且 ready', () => {
  const loads = [];
  const anchors = [];
  function createMiluView(THREE, opts) {
    const textures = opts.shared || new Map();
    const view = {
      object: { visible: false, position: { set() {} }, traverse() {} },
      sharedTextures: () => textures,
      beginLoad() {
        loads.push({ shared: !!opts.shared, deferLoad: !!opts.deferLoad });
        if (!opts.shared) {
          textures.set('walk/left_01.png', { dispose() {} });
        }
      },
      ready: () => textures.size > 0,
      failed: () => false,
      dispose() {},
      show() {},
      faceCamera() {},
      anim: () => opts.anim
    };
    if (!opts.shared) anchors.push(view);
    return view;
  }
  const doc = {
    createElement() {
      return {
        className: '',
        style: {},
        appendChild() {},
        querySelector() { return null; },
        textContent: '',
        dataset: {}
      };
    },
    getElementById(id) {
      if (id === 'islandLabels') return { appendChild() {} };
      if (id === 'islandPeerCount') return null;
      return null;
    }
  };
  const layer = Peers.createPeerLayer({
    THREE: {},
    scene: { add() {}, remove() {} },
    camera: {},
    projected: { set() {}, project() {} },
    P: 0,
    reduced: true,
    createMiluView: createMiluView,
    createMiluState: () => ({}),
    miluStep: (s) => s,
    miluClipPath: () => 'walk/left_01.png',
    screenAxes: () => ({ x: 1, z: 0 }),
    createMotion: () => ({ targets: [] }),
    pushTarget: () => {},
    sampleMotion: (_, __, ___, t) => t || { x: 0, z: 0, yaw: 0 },
    shouldSendPos: () => false,
    getMode: () => ({ exploring: true }),
    getPlayer: () => ({ x: 0, z: 0 }),
    getMiluVisible: () => true,
    getMiluPlaceholderVisible: () => false,
    getPlayerMeshVisible: () => false,
    currentScene: () => 'outdoor',
    presencePose: () => ({ x: 0, z: 0, yaw: 0, scene: 'outdoor', moving: false, avatar: 'milu' }),
    peerOffset: () => ({ x: 0, z: 0 }),
    getStageSize: () => ({ width: 800, height: 600 }),
    getChat: () => null,
    getPresence: () => null,
    document: doc,
    getCam: () => ({ theta: 0 })
  });
  layer.setWelcomed(true);
  layer.applyPeerList([{ id: 'a', name: '甲', x: 1, z: 2, yaw: 0, avatar: 'milu', moving: false }]);
  const row = layer.peers.get('a');
  assert.ok(row);
  assert.equal(row.view.ready(), true);
  assert.equal(anchors.length, 1);
  assert.equal(loads.length, 1);
  assert.equal(loads[0].shared, false);
  layer.applyPeerList([
    { id: 'a', name: '甲', x: 1, z: 2, yaw: 0, avatar: 'milu', moving: false },
    { id: 'b', name: '乙', x: 3, z: 4, yaw: 0, avatar: 'milu', moving: false }
  ]);
  assert.equal(anchors.length, 1);
  assert.equal(layer.peers.get('b').view.ready(), true);
});