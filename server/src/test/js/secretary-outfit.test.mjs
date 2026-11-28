import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync, readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';
import { loadPublicModule } from './load-public-module.mjs';

const repoRoot = join(dirname(fileURLToPath(import.meta.url)), '../../../..');
const assetDir = join(repoRoot, 'server/src/main/resources/public/assets/private-team');
const publicRoot = join(repoRoot, 'server/src/main/resources/public');

const {
  SECRETARY_OUTFIT_STORAGE_KEY,
  parseSecretaryOutfitId,
  readSecretaryOutfitFromStorage,
  writeSecretaryOutfitToStorage,
  secretarySpritePackForOutfit,
  secretaryStandbyIdleUrlsForOutfit,
  secretaryCelebrateStepsForOutfit,
  secretaryAvatarUrlForOutfit,
  secretaryEquivalentSpriteUrl,
  SECRETARY_OUTFIT_CYCLE,
  SECRETARY_OUTFIT_LABELS,
  secretaryOutfitLabel,
  nextSecretaryOutfitId
} = await loadPublicModule('private-team-secretary-outfit.js');

const { secretarySpriteUrlForStatus } = await loadPublicModule('private-team-office.js');
const {
  SECRETARY_SPRITE_URL_TYPING,
  SECRETARY_SPRITE_URL_TYPING_B,
  secretaryTypingDisplayUrl,
  pickNextStandbyIdleUrl
} = await loadPublicModule('private-team-scene-secretary.js');

const STAGE_FILES = [
  ['secretary-stage-typing-a.webp', 624, 768],
  ['secretary-stage-typing-b.webp', 624, 768],
  ['secretary-stage-wave.webp', 624, 768],
  ['secretary-stage-yeah.webp', 624, 768],
  ['secretary-stage-idle-tea.webp', 624, 768],
  ['secretary-stage-read.webp', 624, 768],
  ['secretary-stage-phone.webp', 624, 768],
  ['secretary-stage-sleep.webp', 624, 768],
  ['secretary-stage-clap-a.webp', 624, 768],
  ['secretary-stage-clap-b.webp', 624, 768],
  ['secretary-stage-sofa.webp', 864, 1152],
  ['secretary-stage-avatar.webp', 192, 192]
];

test('服裝 id 解析與 localStorage 回退', () => {
  assert.equal(parseSecretaryOutfitId('default'), 'default');
  assert.equal(parseSecretaryOutfitId('stage'), 'stage');
  assert.equal(parseSecretaryOutfitId('STAGE'), 'default');
  assert.equal(parseSecretaryOutfitId(''), 'default');
  assert.equal(parseSecretaryOutfitId(null), 'default');

  const store = new Map();
  const storage = {
    getItem(k) {
      return store.has(k) ? store.get(k) : null;
    },
    setItem(k, v) {
      store.set(k, String(v));
    }
  };
  assert.equal(readSecretaryOutfitFromStorage(storage), 'default');
  writeSecretaryOutfitToStorage(storage, 'stage');
  assert.equal(store.get(SECRETARY_OUTFIT_STORAGE_KEY), 'stage');
  assert.equal(readSecretaryOutfitFromStorage(storage), 'stage');
  writeSecretaryOutfitToStorage(storage, 'oops');
  assert.equal(readSecretaryOutfitFromStorage(storage), 'default');
  assert.equal(store.get(SECRETARY_OUTFIT_STORAGE_KEY), 'default');
  store.set(SECRETARY_OUTFIT_STORAGE_KEY, 'STAGE');
  assert.equal(readSecretaryOutfitFromStorage(storage), 'default');
  assert.equal(store.get(SECRETARY_OUTFIT_STORAGE_KEY), 'default');
  store.set(SECRETARY_OUTFIT_STORAGE_KEY, 'unknown-outfit');
  assert.equal(readSecretaryOutfitFromStorage(storage), 'default');
  assert.equal(readSecretaryOutfitFromStorage({ getItem() { throw new Error('x'); } }), 'default');
  assert.equal(readSecretaryOutfitFromStorage(null), 'default');
});

test('default 與 stage 的 URL 對應（狀態、打字 B、待命輪替、慶祝）', () => {
  const def = secretarySpritePackForOutfit('default');
  const stage = secretarySpritePackForOutfit('stage');
  assert.equal(def.typing, SECRETARY_SPRITE_URL_TYPING);
  assert.match(stage.typing, /secretary-stage-typing-a\.webp$/);
  assert.equal(secretarySpriteUrlForStatus('工作中', 'stage'), stage.typing);
  assert.equal(secretarySpriteUrlForStatus('待命中', 'stage'), stage.sofa);
  assert.equal(secretarySpriteUrlForStatus('工作中'), def.typing);

  assert.equal(
    secretaryTypingDisplayUrl(stage.typing, true, false, stage),
    stage.typingB
  );
  const standby = secretaryStandbyIdleUrlsForOutfit('stage');
  assert.deepEqual(standby, [stage.idle, stage.read, stage.phone]);
  assert.equal(pickNextStandbyIdleUrl(stage.idle, 0, stage), stage.read);

  const steps = secretaryCelebrateStepsForOutfit('stage');
  assert.equal(steps[4].url, stage.yeah);
  assert.equal(steps[0].url, stage.clapA);
  assert.equal(secretaryAvatarUrlForOutfit('stage'), stage.avatar);

  assert.equal(
    secretaryEquivalentSpriteUrl(def.read, 'stage'),
    stage.read
  );
});

test('舞台服靜態資源存在且 WebP 尺寸正確', () => {
  const py = process.env.PYTHON_BINARY?.trim() || 'python3';
  for (const [name, w, h] of STAGE_FILES) {
    const path = join(assetDir, name);
    assert.ok(existsSync(path), name);
    const head = readFileSync(path, { encoding: 'latin1', flag: 'r' }).slice(0, 12);
    assert.match(head, /^RIFF....WEBP/);
    assert.ok(statSync(path).size > 1000, name);
    const r = spawnSync(
      py,
      [
        '-c',
        'from PIL import Image; im=Image.open(' + JSON.stringify(path) + '); print(im.size[0], im.size[1])'
      ],
      { encoding: 'utf8', timeout: 30_000 }
    );
    assert.equal(r.status, 0, (r.stdout || '') + (r.stderr || ''));
    const [aw, ah] = String(r.stdout).trim().split(/\s+/).map(Number);
    assert.equal(aw, w, name);
    assert.equal(ah, h, name);
  }
});

test('首頁服裝控制與儲存鍵接線', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  // 單一切換按鈕，取代舊的下拉選單
  assert.match(js, /<button type="button" class="pt-hud-outfit pt-hud-sq" data-pt-secretary-outfit-btn/);
  assert.match(js, /data-pt-secretary-outfit-name/);
  assert.doesNotMatch(js, /data-pt-secretary-outfit-select/);
  assert.doesNotMatch(js, /<select/);
  assert.match(js, /服裝/);
  assert.match(js, /舞台服/);
  assert.match(js, /預設/);
  // 每按一次切到下一套，並走原本的儲存與場景更新
  assert.match(js, /nextSecretaryOutfitId\(secretaryOutfitId\)/);
  assert.match(js, /outfitBtn\.addEventListener\('click'/);
  assert.match(js, /writeSecretaryOutfitToStorage/);
  assert.match(js, /readSecretaryOutfitFromStorage/);
  assert.match(js, /data-pt-secretary-avatar/);
  assert.match(js, /setSecretaryOutfit/);
  // 讀屏：按鈕 aria-label 帶目前與下一套，切換後有 polite 播報
  assert.match(js, /setAttribute\('aria-label', '秘書服裝：'/);
  assert.match(js, /aria-live="polite"/);
  assert.match(js, /role="status"/);
  // 觸控面積 44px 以上、跟下載鈕同一套邊框樣式
  const block = css.slice(css.indexOf('.pt-hud-outfit {'), css.indexOf('.pt-hud-outfit:active'));
  assert.match(block, /min-height: (4[4-9]|[5-9][0-9])px/);
  assert.match(block, /min-width: (4[4-9]|[5-9][0-9])px/);
  assert.match(block, /border: 3px solid var\(--maple-navy/);
  assert.match(block, /background: var\(--maple-blue/);
  assert.match(css, /\.pt-hud-outfit:focus-visible/);
  assert.equal(SECRETARY_OUTFIT_STORAGE_KEY, 'punchclock.office.secretaryOutfit');
});

test('服裝按鈕每按一次的切換順序與標籤', () => {
  assert.deepEqual([...SECRETARY_OUTFIT_CYCLE], ['default', 'stage']);
  assert.deepEqual({ ...SECRETARY_OUTFIT_LABELS }, { default: '預設', stage: '舞台服' });
  assert.equal(nextSecretaryOutfitId('default'), 'stage');
  assert.equal(nextSecretaryOutfitId('stage'), 'default');
  // 無效或空值先視為預設，下一套就是舞台服
  for (const bad of [undefined, null, '', 'nope', 0, {}, ' STAGE ']) {
    assert.equal(nextSecretaryOutfitId(bad), 'stage', String(bad));
  }
  assert.equal(secretaryOutfitLabel('stage'), '舞台服');
  assert.equal(secretaryOutfitLabel('whatever'), '預設');
  // 連按會繞回來，且每個 id 都是既有服裝
  let cur = 'default';
  const seen = [];
  for (let i = 0; i < 5; i++) {
    cur = nextSecretaryOutfitId(cur);
    seen.push(cur);
    assert.equal(parseSecretaryOutfitId(cur), cur);
  }
  assert.deepEqual(seen, ['stage', 'default', 'stage', 'default', 'stage']);
});

test('服裝按鈕切換後寫入同一個儲存鍵，重讀得到同一套，壞值回到預設', () => {
  const store = new Map();
  const storage = {
    getItem: (k) => (store.has(k) ? store.get(k) : null),
    setItem: (k, v) => store.set(k, String(v))
  };
  let cur = readSecretaryOutfitFromStorage(storage);
  assert.equal(cur, 'default');
  cur = nextSecretaryOutfitId(cur);
  writeSecretaryOutfitToStorage(storage, cur);
  assert.equal(store.get('punchclock.office.secretaryOutfit'), 'stage');
  assert.equal(readSecretaryOutfitFromStorage(storage), 'stage');
  cur = nextSecretaryOutfitId(readSecretaryOutfitFromStorage(storage));
  writeSecretaryOutfitToStorage(storage, cur);
  assert.equal(readSecretaryOutfitFromStorage(storage), 'default');
  store.set('punchclock.office.secretaryOutfit', 'broken');
  assert.equal(readSecretaryOutfitFromStorage(storage), 'default');
  assert.equal(store.get('punchclock.office.secretaryOutfit'), 'default');
});

test('首頁進入口走版本化 JS 根路徑，相對模組才能一起換 URL', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  assert.match(html, /src="\{\{SERVER_JS_BASE\}\}\/office-page\.js"/);
  assert.match(html, /src="\{\{SERVER_JS_BASE\}\}\/device-fold\.js"/);
  assert.doesNotMatch(html, /src="\/js\/office-page\.js\?v=/);
  const scene = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.match(scene, /secretaryEquivalentSpriteUrl\(next, 'default'\)/);
  assert.match(scene, /\[px, py, pz, scale\]\.every\(Number\.isFinite\)/);
});
