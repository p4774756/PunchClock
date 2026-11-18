import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const src = await readFile(join(root, 'app/character-scene.tsx'), 'utf8');
const pageSrc = await readFile(join(root, 'app/page.tsx'), 'utf8');

assert.doesNotMatch(pageSrc, /CharacterScene key=\{`\$\{DEFAULT_AVATAR_ID\}-\$\{outfitId\}`\}/, 'outfit switch must not remount CharacterScene');
assert.doesNotMatch(src, /\[avatarId,outfitId,onProgress,onError\]/, '3D scene must not rebuild on outfitId');
assert.match(src, /cloneSkinnedObject|SkeletonUtils\.js/, 'anime GLB parsed once with cloned practice rig');
assert.match(src, /isolatePracticeRig/, 'practice rig must deep-clone shared mesh assets');
assert.match(src, /clonePracticeMaterial/, 'practice rig must fork MToon materials with render parity');
assert.match(src, /syncMaterialRenderState/, 'cloned materials must copy transparent/depthWrite/side');
assert.match(src, /detachMToonUniforms/, 'MToon Color uniforms must not alias school rig');
assert.match(src, /isPracticeUniformMesh/, 'practice must detect uniform-only meshes');
assert.match(src, /isPracticeUniformMesh\(obj,outfit\)\)obj\.visible=false/, 'hidden uniform meshes must set visible=false');
assert.match(src, /next\.colorSpace=src\.colorSpace/, 'painted skin map must preserve source colorSpace');
assert.match(src, /assertUniformOutfitUntouched/, 'practice build must not mutate school mesh materials');
assert.match(src, /reanchor/, 'outfit switch must re-snap teaching markers');

assert.doesNotMatch(src, /BUN_BROWN|0x3b2a22/i, 'practice bun must not use brown常量');
assert.match(src, /findPrimaryHairMaterial/, 'bun should sample GLB hair MToon');
assert.match(src, /writeUvTriangleShoulderFix/, 'shoulder alpha-hole inpaint required');
assert.doesNotMatch(src, /SHOULDER_ARTIFACT|isShoulderArtifactPx/, 'shoulder fix must target alpha cutouts not #382830');
assert.match(src, /data\.data\[i\+3\]>=200\)continue[\s\S]*paintShoulderAlphaHole/, 'shoulder fix fills alpha<200 from nearby skin');
assert.match(src, /isHairMaterial/, 'hair-specific cel shading to preserve blue-violet');
assert.match(src, /syncHairBunMaterial/, 'bun materials must track hair MToon chroma');
assert.doesNotMatch(src, /requestIdleCallback/, 'must not prebuild practice outfit on idle');
assert.match(src, /practice-outfit-build-start/, 'practice outfit build must performance.mark start');
assert.match(src, /performance\.measure\(['"]practice-outfit-build/, 'practice outfit build must performance.measure');

console.log('practice outfit visual guards passed.');