#!/usr/bin/env node
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const avatars = readFileSync(join(root, 'app/avatars.ts'), 'utf8');
const adult = readFileSync(join(root, 'app/adult-outfits.ts'), 'utf8');
const bridge = readFileSync(join(root, 'app/avatar-outfits.ts'), 'utf8');
const page = readFileSync(join(root, 'app/page.tsx'), 'utf8');
const scene = readFileSync(join(root, 'app/character-scene.tsx'), 'utf8');
const paint = readFileSync(join(root, 'app/adult-outfit-paint.ts'), 'utf8');

assert.match(avatars, /id:'adult'[\s\S]*hasOutfits:\s*true/, 'adult must offer outfits');
assert.match(adult, /shortName:'套裝'/, 'suit short label');
assert.match(adult, /shortName:'休閒'/, 'casual short label');
assert.match(adult, /shortName:'練習裝'/, 'practice short label');
assert.match(adult, /shortName:'薄紗'/, 'sheer short label');
assert.match(adult, /DEFAULT_ADULT_OUTFIT_ID:AdultOutfitId='suit'/, 'adult default suit');
assert.match(adult, /sheerOuter:\s*true/, 'sheer outer flag');
assert.doesNotMatch(adult, /emoji/u, 'no emoji in adult outfit registry');

assert.match(bridge, /outfitsForAvatar/, 'avatar outfit bridge required');
assert.match(bridge, /outfitBelongsToAvatar/, 'must not mix assistant/adult outfit ids');
assert.match(bridge, /DEFAULT_ADULT_OUTFIT_ID/, 'default per avatar');

assert.match(page, /adultOutfitId/, 'adult outfit state isolated from assistant');
assert.match(page, /setAdultOutfitId\(defaultOutfitForAvatar\('adult'\)\)/, 'selecting adult resets to suit');
assert.match(page, /outfit-bar/, 'outfit bar for picker');

assert.match(scene, /paintAdultSwimSkin/, 'adult swim paint in scene');
assert.match(scene, /applyAdultClothStyle/, 'adult cloth tint path');
assert.match(scene, /applyAdultSheerOuter/, 'sheer outer layer');
assert.match(scene, /sheerModel/, 'sheer alt rig');
assert.match(adult, /skipHairRestyle:\s*true/, 'adult practice keeps natural hair');

assert.match(paint, /hideAdultSportGarments/, 'sport practice hides lace garments');
assert.match(paint, /hideAdultSheerLaceGarments/, 'sheer must drop lace bottoms');
assert.match(paint, /makeAdultSheerNetMap/, 'sheer uses fine net map, not sport lace tint');
assert.match(paint, /transparentWithZWrite/, 'sheer MToon must disable opaque z-write');
assert.match(paint, /transparent=true/, 'sheer tops semi-transparent');
assert.match(scene, /hideAdultSportGarments/, 'scene applies sport garment hide');
assert.match(scene, /adultPaint==='sport'\)hideAdultSportGarments/, 'incremental sport build hides lace');
assert.doesNotMatch(
  paint,
  /tintCanvas\(map,SHEER_INNER,\{preserveAlpha:true,keep:\.035\}\)/,
  'sheer must not reuse green lace sport tint',
);

console.log('adult outfit registry checks passed.');