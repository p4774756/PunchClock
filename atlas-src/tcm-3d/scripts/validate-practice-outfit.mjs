import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = await readFile(join(root, 'app/character-scene.tsx'), 'utf8');

assert.doesNotMatch(src, /BUN_BROWN|0x3b2a22/i, 'practice bun must not use brown常量');
assert.match(src, /findPrimaryHairMaterial/, 'bun should sample GLB hair MToon');
assert.match(src, /writeUvTriangleShoulderFix/, 'shoulder atlas artifact inpaint required');
assert.match(src, /isHairMaterial/, 'hair-specific cel shading to preserve blue-violet');

console.log('practice outfit visual guards passed.');