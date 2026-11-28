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
  secretaryEquivalentSpriteUrl
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
  assert.equal(readSecretaryOutfitFromStorage({ getItem() { throw new Error('x'); } }), 'default');
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
  assert.match(js, /data-pt-secretary-outfit-select/);
  assert.match(js, /服裝/);
  assert.match(js, /舞台服/);
  assert.match(js, /預設/);
  assert.match(js, /writeSecretaryOutfitToStorage/);
  assert.match(js, /readSecretaryOutfitFromStorage/);
  assert.match(js, /data-pt-secretary-avatar/);
  assert.match(js, /setSecretaryOutfit/);
});