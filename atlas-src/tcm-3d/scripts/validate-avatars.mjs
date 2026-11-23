#!/usr/bin/env node
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
const root = join(dirname(fileURLToPath(import.meta.url)), '..');

const assistantDigest = readFileSync(join(root, 'app/anime-model-digest.ts'), 'utf8');
assert.match(assistantDigest, /ANIME_MODEL_DIGEST='bc1400d08d6b'/, 'assistant digest must stay unchanged');
assert.match(assistantDigest, /ANIME_MODEL_BYTES=5681108/, 'assistant byte size must stay unchanged');

const pointsJson = readFileSync(join(root, 'app/anime-points.json'), 'utf8');
const anime = JSON.parse(pointsJson);
assert.equal(anime.model, 'anime-assistant.glb');
assert.equal(anime.faceAxis, '-Z');
assert.ok(Array.isArray(anime.points), 'anime-points.points must be an array');
assert.ok(anime.points.length >= 83, `anime-points must not shrink below 83, got ${anime.points.length}`);
const codes = new Set();
for (const pt of anime.points) {
  assert.ok(pt && typeof pt.code === 'string' && pt.code, 'point missing code');
  assert.ok(typeof pt.name === 'string' && pt.name, `${pt.code} missing name`);
  assert.ok(Array.isArray(pt.position) && pt.position.length === 3 && pt.position.every((n) => Number.isFinite(n)), `${pt.code} position`);
  assert.ok(Array.isArray(pt.normal) && pt.normal.length === 3 && pt.normal.every((n) => Number.isFinite(n)), `${pt.code} normal`);
  assert.ok(!codes.has(pt.code), `duplicate code ${pt.code}`);
  codes.add(pt.code);
}
for (const code of ['LU5','PC3','HT3','LI11','TE10','SI8','LU9','PC7','HT7','LI5','TE4','SI5','LI15','TE14','SI10','SI9','SI11']) {
  assert.ok(codes.has(code), `required baked code missing: ${code}`);
}

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