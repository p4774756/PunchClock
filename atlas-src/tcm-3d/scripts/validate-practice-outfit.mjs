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
assert.match(src, /MTOON_TEXTURE_UNIFORM_KEYS/, 'detach must not reset MToon texture uniforms to school');
assert.match(src, /forkMToonTextureUniforms/, 'practice MToon must fork texture uniforms after clone');
assert.match(src, /setMToonBodyMap/, 'painted skin must bind uniforms.map for MToon shader');
assert.match(src, /assertPracticeBodySkinMaps/, 'practice build must verify body map fork from school');
assert.match(src, /isPracticeUniformMesh/, 'practice must detect uniform-only meshes');
assert.match(src, /isPracticeUniformMesh\(obj,outfit\)\)obj\.visible=false/, 'hidden uniform meshes must set visible=false');
assert.match(src, /next\.colorSpace=src\.colorSpace/, 'painted skin map must preserve source colorSpace');
assert.match(src, /assertUniformOutfitUntouched/, 'practice build must not mutate school mesh materials');
assert.match(src, /applyCachedAnchors/, 'outfit switch must apply cached teaching markers');
assert.doesNotMatch(src, /teaching\.reanchor/, 'outfit switch must not repeat CPU skin raycast');
const overlay=await readFile(join(root,'app/teaching-overlay.ts'),'utf8');
assert.match(overlay, /buildSkinRaycastProxies|skin-raycast-proxy/, 'anchor must bake static skin proxies');

assert.doesNotMatch(src, /BUN_BROWN|0x3b2a22/i, 'practice bun must not use brown常量');
assert.match(src, /findPrimaryHairMaterial/, 'bun should sample GLB hair MToon');
assert.match(src, /writeUvTriangleShoulderFix/, 'shoulder alpha-hole inpaint required');
assert.doesNotMatch(src, /SHOULDER_ARTIFACT|isShoulderArtifactPx/, 'shoulder fix must target alpha cutouts not #382830');
assert.match(src, /data\.data\[i\+3\]>=200\)continue[\s\S]*paintShoulderAlphaHole/, 'shoulder fix fills alpha<200 from nearby skin');
assert.match(src, /isHairMaterial/, 'hair-specific cel shading to preserve blue-violet');
assert.match(src, /syncHairBunMaterial/, 'bun materials must track hair MToon chroma');
assert.match(src, /buildPracticeOutfitIncremental/, 'practice outfit must build incrementally');
assert.match(src, /startPostCardWarm|prioritizePracticeWarm/, 'practice outfit must warm on idle after load card');
assert.match(src, /uploadTexturesIncremental/, 'practice warm must upload outfit textures in slices');
assert.match(src, /warmShaderPlanGroup|warmShadersIncremental/, 'practice warm must shader-warm by program group');
const shaderWarm = await readFile(join(root, 'app/shader-warm.ts'), 'utf8');
assert.match(shaderWarm, /hideSceneChrome/, 'shader warm must hide scene chrome only');
assert.match(shaderWarm, /atlasSceneChrome/, 'scene chrome must be tagged on panorama/ground');
assert.match(shaderWarm, /groupMeshesByProgram|materialProgramKey/, 'shader warm must group by program cache key');
assert.doesNotMatch(shaderWarm, /stashSceneChrome/, 'must not hide all non-character scene children (lights)');
assert.match(src, /finishOutfitSwitchMeasure/, 'outfit switch measure must end after render');
assert.match(
  src,
  /renderer\.render\(scene,camera\);[\s\S]{0,120}finishOutfitSwitchMeasure\(\)/,
  'atlas:outfit-switch must measure after renderer.render',
);
assert.doesNotMatch(
  src.match(/const showOutfit=\([\s\S]*?\n  \};/m)?.[0] ?? '',
  /performance\.measure\('atlas:outfit-switch'/,
  'must not measure outfit switch inside showOutfit',
);
assert.match(pageSrc, /atlas:outfit-switch-start/, 'outfit switch measure must start on button click');
assert.match(src, /atlas:outfit-switch/, 'outfit switch must performance.measure atlas:outfit-switch');
assert.match(src, /atlas:model-visible/, 'first model frame must performance.measure atlas:model-visible');
assert.match(src, /practice-outfit-build-start/, 'practice outfit build must performance.mark start');
assert.match(src, /performance\.measure\(['"]practice-outfit-build/, 'practice outfit build must performance.measure');

console.log('practice outfit visual guards passed.');