#!/usr/bin/env node
import assert from 'node:assert/strict';
import {createHash} from 'node:crypto';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const assistantDigest = readFileSync(join(root, 'app/anime-model-digest.ts'), 'utf8');
assert.match(assistantDigest, /ANIME_MODEL_DIGEST='bc1400d08d6b'/, 'assistant digest must stay unchanged');
assert.match(assistantDigest, /ANIME_MODEL_BYTES=5681108/, 'assistant byte size must stay unchanged');

const pointsJson = readFileSync(join(root, 'app/anime-points.json'), 'utf8');
const pointsHash = createHash('sha256').update(pointsJson).digest('hex');
assert.equal(
  pointsHash,
  '75b6d3b61008c672de0a92ff728493a1f80c4a845ab9ecabadf92b89de85ac5d',
  'anime-points.json must not change on this branch',
);
JSON.parse(pointsJson);

const page = readFileSync(join(root, 'app/page.tsx'), 'utf8');
assert.match(page, /ANIME_AVATARS/, 'page must render avatar list');
assert.match(page, /avatarId/, 'page must pass avatarId to CharacterScene');
assert.match(page, /DEFAULT_AVATAR_ID/, 'default avatar constant must remain');
assert.doesNotMatch(
  page,
  /CharacterScene key=\{`\$\{avatarId\}-\$\{outfitId\}`\}/,
  'outfit switch must not remount CharacterScene',
);
// Avatar switch used to remount via key={avatarId}, which disposed the WebGL
// renderer and forced a full reload. The cache keeps CharacterScene mounted
// and stashes / restores a rig keyed by avatarId instead.
const remountsOnAvatar = /<CharacterScene\b[^>]*key=\{avatarId\}/.test(page);
const sceneSrc = readFileSync(join(root, 'app/character-scene.tsx'), 'utf8');
const cacheSrc = readFileSync(join(root, 'app/avatar-scene-cache.ts'), 'utf8');
const cachedByAvatar =
  /takeAvatarRig\(\s*avatarId\s*\)/.test(sceneSrc) &&
  /stashAvatarRig\(\s*avatarId\s*,/.test(sceneSrc) &&
  /Map\s*<\s*AvatarId\s*,/.test(cacheSrc);
assert.ok(
  remountsOnAvatar || cachedByAvatar,
  'avatar switch must remount CharacterScene (key={avatarId}) or keep a per-avatar rig cache (stash/take by avatarId)',
);

const avatarsSrc = readFileSync(join(root, 'app/avatars.ts'), 'utf8');
assert.match(avatarsSrc, /id:'adult'/, 'adult avatar entry required');
assert.match(avatarsSrc, /id:'adult'[\s\S]*hasOutfits:\s*true/, 'adult must offer outfits');

assert.match(avatarsSrc, /DEFAULT_AVATAR_ID:AvatarId='assistant'/, 'default must be assistant');
assert.match(avatarsSrc, /points:ADULT_POINTS/, 'adult avatar uses baked ADULT_POINTS');

console.log('avatar registry checks passed.');