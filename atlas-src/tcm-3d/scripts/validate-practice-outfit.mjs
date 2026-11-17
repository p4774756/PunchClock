import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = await readFile(join(root, 'app/character-scene.tsx'), 'utf8');

assert.doesNotMatch(src, /BUN_BROWN|0x3b2a22/i, 'practice bun must not use brown常量');
assert.match(src, /findPrimaryHairMaterial/, 'bun should sample GLB hair MToon');
assert.match(src, /writeUvTriangleShoulderFix/, 'shoulder alpha-hole inpaint required');
assert.doesNotMatch(src, /SHOULDER_ARTIFACT|isShoulderArtifactPx/, 'shoulder fix must target alpha cutouts not #382830');
assert.match(src, /data\.data\[i\+3\]>=200\)continue[\s\S]*paintShoulderAlphaHole/, 'shoulder fix fills alpha<200 from nearby skin');
assert.match(src, /isHairMaterial/, 'hair-specific cel shading to preserve blue-violet');

console.log('practice outfit visual guards passed.');